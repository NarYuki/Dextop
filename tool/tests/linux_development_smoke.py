#!/usr/bin/env python3
"""Run inside a Dextop guest. All project files and caches live in a new /tmp directory."""
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import tempfile
import time

ROOT = Path(tempfile.mkdtemp(prefix="dextop-development-", dir="/tmp"))
RESULTS = []


def write(directory, name, text):
    path = directory / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    return path


def run(name, args, directory=None, timeout=90, env=None, requires=(), expected=None):
    directory = directory or ROOT / name
    directory.mkdir(parents=True, exist_ok=True)
    missing = [tool for tool in requires if not shutil.which(tool)]
    started = time.monotonic()
    result = dict(name=name, command=args, directory=str(directory))
    if missing:
        result.update(status="missing", missing=missing)
    else:
        try:
            child = subprocess.run(args, cwd=directory, env=dict(os.environ, **(env or {})),
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   encoding="utf-8", errors="replace", timeout=timeout)
            result.update(status="pass" if child.returncode == 0 else "fail", exit=child.returncode,
                          output=child.stdout[-5000:])
            if expected is not None and expected not in child.stdout:
                result["status"] = "fail"
                result["expected_output"] = expected
            if name == "strace" and child.returncode and any(word in child.stdout.lower() for word in ("ptrace", "operation not permitted")):
                result["status"] = "platform_limit"
        except subprocess.TimeoutExpired as error:
            output = error.stdout or b""
            if isinstance(output, bytes):
                output = output.decode("utf-8", errors="replace")
            result.update(status="timeout", output=output[-5000:])
        except Exception as error:
            result.update(status="fail", output=repr(error))
    result["seconds"] = round(time.monotonic() - started, 2)
    RESULTS.append(result)
    (ROOT / "results.json").write_text(json.dumps(RESULTS, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False), flush=True)
    return result


def shell(name, script, **kwargs):
    return run(name, ["bash", "-c", "set -euo pipefail\n" + script], **kwargs)


def main():
    print("SMOKE_ROOT=" + str(ROOT), flush=True)
    shell("coreutils", "printf '3\\n1\\n2\\n' > input; sort -n input | awk '{s+=$1} END {print s}' | grep -x 6; "
          "sed 's/1/one/' input | grep one; find . -name input -print0 | xargs -0 wc -l; "
          "sha256sum input; df -h .; du -sh .")
    shell("shells", "bash -c 'a=(one two); [[ ${#a[@]} == 2 ]]'; perl -e 'print qq(PERL_SHELL_OK\\n)'", requires=("perl",))
    shell("archives", "mkdir source; printf 'archive data\\n' > 'source/a file.txt'; "
          "tar -czf sample.tar.gz source; mkdir unpack; tar -xzf sample.tar.gz -C unpack; "
          "cmp 'source/a file.txt' 'unpack/source/a file.txt'; xz -k 'source/a file.txt'; "
          "xz -dc 'source/a file.txt.xz' | cmp - 'source/a file.txt'; "
          "zip -q sample.zip 'source/a file.txt'; unzip -q sample.zip -d zipout; "
          "cmp 'source/a file.txt' 'zipout/source/a file.txt'; echo ARCHIVE_OK", requires=("zip", "unzip"))
    shell("process_tools", "uptime; free -m; ps -eo pid,comm | head -15 || true; timeout 1 sleep 5 || test $? = 124")
    run("strace", ["strace", "-o", "trace.log", "true"], requires=("strace",))
    run("curl_tls", ["curl", "--fail", "--location", "--max-time", "25", "-I", "https://example.com"], requires=("curl",))
    shell("sqlite_jq", "sqlite3 db.sqlite 'pragma journal_mode=WAL; create table t(v); insert into t values(42); select v from t;' | grep -x 42; "
          "printf '{\"v\":42}' | jq -e '.v==42'", requires=("sqlite3", "jq"))
    shell("git", "git init -q source; printf 'hello\\n' > source/file; git -C source add file; "
          "git -C source -c user.name=DextopSmoke -c user.email=smoke@example.invalid commit -qm initial; "
          "git clone -q source clone; cmp source/file clone/file; git -C clone fsck --full; echo GIT_CLONE_OK", requires=("git",))
    shell("ssh_rsync", "ssh-keygen -q -t ed25519 -N '' -f testkey; ssh-keygen -lf testkey.pub; "
          "mkdir source target; printf data > source/file; rsync -a source/ target/; cmp source/file target/file; echo SSH_RSYNC_OK", requires=("ssh-keygen", "rsync"))

    native = ROOT / "native"
    native.mkdir()
    write(native, "hello.c", r'''#include <stdio.h>
#include <pthread.h>
#include <unistd.h>
#include <sys/wait.h>
#include <sys/mman.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <string.h>
static void *work(void *p) { *(int*)p = 42; return NULL; }
int main(void) {
 int answer=0; pthread_t thread;
 if(pthread_create(&thread,NULL,work,&answer)||pthread_join(thread,NULL)||answer!=42)return 1;
 int p[2]; if(pipe(p))return 2; pid_t pid=fork();
 if(pid==0){close(p[0]);write(p[1],"ok",2);_exit(0);} if(pid<0)return 3;
 close(p[1]); char text[3]={0};if(read(p[0],text,2)!=2||strcmp(text,"ok"))return 4;
 close(p[0]);int status;waitpid(pid,&status,0);if(status)return 5;
 int fd=open("mapped",O_CREAT|O_RDWR,0600);if(fd<0||ftruncate(fd,4096))return 6;
 char *m=mmap(NULL,4096,PROT_READ|PROT_WRITE,MAP_SHARED,fd,0);if(m==MAP_FAILED)return 7;
 strcpy(m,"mapped");if(msync(m,4096,MS_SYNC))return 8;munmap(m,4096);close(fd);
 void *lib=dlopen("libm.so.6",RTLD_NOW);if(!lib||!dlsym(lib,"cos"))return 9;dlclose(lib);
 puts("C_THREADS_FORK_MMAP_DLOPEN_OK");return 0;
}
''')
    write(native, "hello.cpp", r'''#include <filesystem>
#include <fstream>
#include <future>
#include <iostream>
#include <thread>
int main(){namespace fs=std::filesystem;fs::create_directory("cpp-data");
 std::ofstream("cpp-data/a") << "42";fs::rename("cpp-data/a","cpp-data/b");
 if(fs::file_size("cpp-data/b")!=2)return 1;
 auto f=std::async(std::launch::async,[]{return 42;});if(f.get()!=42)return 2;
 int x=0;{std::jthread t([&]{x=42;});}if(x!=42)return 3;
 std::cout<<"CPP20_FILESYSTEM_FUTURE_JTHREAD_OK\n";
}
''')
    shell("gcc_c", "gcc -Wall -Wextra -pthread hello.c -ldl -o c-test && ./c-test", directory=native, requires=("gcc",))
    shell("gpp_cpp20", "g++ -std=c++20 -Wall -Wextra -pthread hello.cpp -o cpp-test && ./cpp-test", directory=native, requires=("g++",))
    shell("clang_c", "clang -Wall -Wextra -pthread hello.c -ldl -o clang-test && ./clang-test", directory=native, requires=("clang",))
    write(native, "Makefile", "all:\n\t$(CC) -pthread hello.c -ldl -o make-test\n\t./make-test\n")
    run("make", ["make"], directory=native, requires=("make", "cc"))
    write(native, "CMakeLists.txt", "cmake_minimum_required(VERSION 3.15)\nproject(smoke C)\nfind_package(Threads REQUIRED)\nadd_executable(cmake-test hello.c)\ntarget_link_libraries(cmake-test PRIVATE Threads::Threads ${CMAKE_DL_LIBS})\n")
    shell("cmake_ninja", "cmake -S . -B build -G Ninja && cmake --build build && ./build/cmake-test", directory=native, requires=("cmake", "ninja", "cc"))

    py = ROOT / "python"
    py.mkdir()
    write(py, "stdlib.py", r'''import os, sqlite3, subprocess, sys, mmap, multiprocessing, socket, threading
from multiprocessing import shared_memory
from pathlib import Path
def child(q): q.put(42)
if __name__ == '__main__':
 Path('a').write_text('42'); os.replace('a','b'); assert Path('b').read_text()=='42'
 db=sqlite3.connect('db.sqlite');db.execute('pragma journal_mode=WAL');db.execute('create table t(v)');db.execute('insert into t values(42)');db.commit();assert db.execute('select v from t').fetchone()==(42,);db.close()
 assert subprocess.check_output([sys.executable,'-c','print(42)']).strip()==b'42'
 ctx=multiprocessing.get_context('spawn');q=ctx.Queue();p=ctx.Process(target=child,args=(q,));p.start();assert q.get(timeout=10)==42;p.join(10);assert p.exitcode==0
 mem=shared_memory.SharedMemory(create=True,size=16);mem.buf[:2]=b'ok';assert bytes(mem.buf[:2])==b'ok';mem.close();mem.unlink()
 a,b=socket.socketpair();a.sendall(b'ok');assert b.recv(2)==b'ok';a.close();b.close()
 print('PYTHON_SQLITE_SUBPROCESS_SPAWN_SHM_UNIX_SOCKET_OK')
''')
    run("python_stdlib", [sys.executable, "stdlib.py"], directory=py)
    run("python_venv", [sys.executable, "-m", "venv", str(py / "venv")], directory=py)
    vpython = py / "venv/bin/python"
    if vpython.is_file():
        run("pip_install", [str(vpython), "-m", "pip", "install", "--disable-pip-version-check", "--no-input", "--no-cache-dir", "requests", "numpy"], directory=py, timeout=180)
        run("python_requests_numpy", [str(vpython), "-c", "import requests,numpy as n; assert requests.get('https://example.com',timeout=20).status_code==200; a=n.array([[1.,2.],[3.,5.]]); assert n.allclose(a@n.linalg.inv(a),n.eye(2)); print('REQUESTS_NUMPY_LINALG_OK',n.__version__)"], directory=py)
    write(py, "native_test.c", r'''#include <Python.h>
static PyObject *answer(PyObject *s,PyObject *args){return PyLong_FromLong(42);}
static PyMethodDef methods[]={{"answer",answer,METH_NOARGS,"answer"},{NULL,NULL,0,NULL}};
static struct PyModuleDef module={PyModuleDef_HEAD_INIT,"native_test",NULL,-1,methods};
PyMODINIT_FUNC PyInit_native_test(void){return PyModule_Create(&module);}
''')
    shell("python_c_extension", "gcc -shared -fPIC $(python3-config --includes) native_test.c -o native_test$(python3-config --extension-suffix); python3 -c 'import native_test; assert native_test.answer()==42; print(\"PYTHON_C_EXTENSION_OK\")'", directory=py, requires=("gcc", "python3-config"))

    run("perl", ["perl", "-MJSON::PP", "-Mthreads", "-e", 'my $t=threads->create(sub {42}); die unless $t->join()==42; my $j=decode_json(q({"value":42})); die unless $j->{value}==42; print "PERL_THREADS_JSON_OK\\n";'], requires=("perl",))
    run("ruby", ["ruby", "-rjson", "-e", 'raise unless Thread.new {42}.value==42; raise unless JSON.parse(%q({"value":42}))["value"]==42; puts "RUBY_THREADS_JSON_OK"'], requires=("ruby",))
    node = ROOT / "node"
    node.mkdir()
    write(node, "test.js", "const assert=require('node:assert');const http=require('node:http');const {Worker}=require('node:worker_threads');const fs=require('node:fs');\n"
          "fs.writeFileSync('file','42');assert.equal(fs.readFileSync('file','utf8'),'42');\n"
          "const server=http.createServer((req,res)=>res.end('42'));server.listen(0,'127.0.0.1',()=>{http.get('http://127.0.0.1:'+server.address().port,res=>{let s='';res.on('data',c=>s+=c);res.on('end',()=>{assert.equal(s,'42');server.close();const w=new Worker(\"require('node:worker_threads').parentPort.postMessage(42)\",{eval:true});w.on('message',n=>{assert.equal(n,42);console.log('NODE_HTTP_WORKER_FS_OK')});});});});\n")
    run("node", ["node", "test.js"], directory=node, requires=("node",))
    write(node, "local/package.json", '{"name":"dextop-smoke-local","version":"1.0.0","main":"index.js"}')
    write(node, "local/index.js", "module.exports=42;\n")
    npm = shell("npm_local", "npm init -y >/dev/null; npm install --offline --ignore-scripts --no-audit --no-fund ./local; node -e 'if(require(\"dextop-smoke-local\")!==42)process.exit(1);console.log(\"NPM_LOCAL_PACKAGE_OK\")'", directory=node, requires=("npm",), env={"npm_config_cache": str(node / "npm-cache")})
    if npm["status"] == "fail" and "syscall link" in npm.get("output", ""):
        # Android denies hardlinks. Scope the installer's copy fallback to npm only.
        npm_env = {"npm_config_cache": str(node / "npm-cache-scoped"), "DEXTOP_HARDLINK_COPY": "1"}
        shell("npm_local_scoped_copy", "npm install --offline --ignore-scripts --no-audit --no-fund ./local; node -e 'if(require(\"dextop-smoke-local\")!==42)process.exit(1);console.log(\"NPM_LOCAL_SCOPED_COPY_OK\")'", directory=node, requires=("npm",), env=npm_env)
        shell("npm_registry_scoped_copy", "npm install --ignore-scripts --no-audit --no-fund is-number@7.0.0; node -e 'if(!require(\"is-number\")(42))process.exit(1);console.log(\"NPM_REGISTRY_INSTALL_OK\")'", directory=node, requires=("npm",), env=npm_env, timeout=120)

    go = ROOT / "go"
    go.mkdir()
    write(go, "main.go", 'package main\nimport("fmt";"net/http";"net/http/httptest";"io";"os")\nfunc main(){c:=make(chan int);go func(){c<-42}();if <-c!=42{panic("goroutine")};s:=httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter,r *http.Request){fmt.Fprint(w,"42")}));defer s.Close();r,e:=http.Get(s.URL);if e!=nil{panic(e)};b,e:=io.ReadAll(r.Body);r.Body.Close();if e!=nil||string(b)!="42"{panic("http")};if e=os.WriteFile("file",b,0600);e!=nil{panic(e)};fmt.Println("GO_GOROUTINE_HTTP_FS_OK")}\n')
    shell("go", "go build -o go-test main.go && ./go-test", directory=go, timeout=240,
          env={"GOTOOLCHAIN": "local", "GOCACHE": str(go / "cache"), "GOPATH": str(go / "gopath")}, requires=("go",))
    rust = ROOT / "rust"
    rust.mkdir()
    write(rust, "main.rs", 'use std::{thread,fs};fn main(){let t=thread::spawn(||42);assert_eq!(t.join().unwrap(),42);fs::write("file","42").unwrap();assert_eq!(fs::read_to_string("file").unwrap(),"42");println!("RUST_THREADS_FS_OK");}\n')
    shell("rust", "rustc main.rs -o rust-test && ./rust-test", directory=rust, requires=("rustc", "cc"))
    write(rust, "Cargo.toml", '[package]\nname="dextop-smoke"\nversion="0.1.0"\nedition="2021"\n')
    write(rust, "src/lib.rs", '#[test] fn answer(){assert_eq!(6*7,42);}\n')
    run("cargo", ["cargo", "test", "--offline"], directory=rust, requires=("cargo", "cc"), timeout=120,
        env={"CARGO_HOME": str(rust / "cargo-home")})
    java = ROOT / "java"
    java.mkdir()
    write(java, "Hello.java", 'import java.nio.file.*;import java.util.concurrent.*;public class Hello{public static void main(String[]a)throws Exception{Files.writeString(Path.of("file"),"42");if(!Files.readString(Path.of("file")).equals("42"))throw new Exception("nio");if(CompletableFuture.supplyAsync(()->42).get()!=42)throw new Exception("future");System.out.println("JAVA_NIO_FUTURE_OK");}}\n')
    shell("java", "javac Hello.java && java -Xmx256m Hello", directory=java, requires=("javac", "java"))
    shell("versions", "for x in gcc g++ clang cmake python3 perl ruby node npm rustc cargo java git sqlite3; do echo TOOL=$x; $x --version 2>&1 | head -2 || true; done; go version")
    counts = {status: sum(r["status"] == status for r in RESULTS) for status in sorted({r["status"] for r in RESULTS})}
    print("SMOKE_COUNTS=" + json.dumps(counts), flush=True)
    print("SMOKE_RESULTS=" + str(ROOT / "results.json"), flush=True)
    return int(any(r["status"] in ("fail", "timeout") for r in RESULTS))


if __name__ == "__main__":
    raise SystemExit(main())
