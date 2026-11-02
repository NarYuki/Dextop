#!/bin/bash
# Android-mode battery: runs inside PRoot as an ordinary Android app UID (untrusted_app).
# "setup" (fake root, --link2symlink) installs the tools with apt; "session" (-i uid:uid,
# as Dextop's ordinary Linux sessions) checks basic use, development and debugging.
# Each line: PASS|FAIL|INFO <name>: <short detail>
set -u
cd /root
# A follow-up run with the production session preload, when that check script is present.
if [ "${1:-}" = session ] && [ -f /root/preload.sh ]; then bash /root/preload.sh; exit 0; fi
t() { # t <name> <command...>
  local name=$1; shift
  local out rc
  out=$(timeout ${T:-120} "$@" 2>&1); rc=$?
  out=$(printf '%s' "$out" | tr '\n' ' ' | cut -c1-220)
  if [ $rc -eq 0 ]; then echo "PASS $name: $out"; else echo "FAIL $name (rc=$rc): $out"; fi
}
info() { echo "INFO $1: $(eval "$2" 2>&1 | tr '\n' ' ' | cut -c1-220)"; }

if [ "${1:-session}" = setup ]; then
  export DEBIAN_FRONTEND=noninteractive
  T=600 t apt-update apt-get update
  T=1800 t apt-install apt-get install -y --no-install-recommends build-essential gdb strace python3 python3-venv \
    python3-pip git curl ca-certificates iproute2 procps psmisc netcat-openbsd lsof file sqlite3 nodejs make \
    iputils-ping util-linux
  t dpkg-audit dpkg --audit
  exit 0
fi

echo "== identity"
info id 'id'
info kernel-ids 'grep -E "^(Uid|Gid|Groups|NoNewPrivs|Seccomp|CapEff)" /proc/self/status'
info selinux 'cat /proc/self/attr/current'
info os 'grep PRETTY /etc/os-release'

echo "== basic files"
t file-write sh -c 'echo hi > f && cat f && mv f g && chmod 600 g && rm g'
t symlink sh -c 'ln -sf /etc/hostname s && cat s >/dev/null && rm s'
t hardlink sh -c 'echo a > h1 && ln h1 h2; rc=$?; rm -f h1 h2; exit $rc'
t mkfifo sh -c 'mkfifo p && rm p'
t chown-own sh -c 'touch o && chown $(id -u) o && rm o'
t xattr sh -c 'touch x && setfattr -n user.k -v 1 x 2>&1 || python3 -c "import os;os.setxattr(\"x\",\"user.k\",b\"1\")"; rc=$?; rm -f x; exit $rc'
t big-file sh -c 'dd if=/dev/zero of=big bs=1M count=64 status=none && rm big'
info df 'df -h / | tail -1'

echo "== processes and IPC"
t fork-exec sh -c 'for i in 1 2 3; do /bin/true; done'
t signals sh -c 'sleep 30 & p=$!; kill -STOP $p && kill -CONT $p && kill $p; wait $p; [ $? -ge 128 ]'
t ps sh -c 'ps aux | wc -l'
info ps-others 'ps -eo user | sort | uniq -c | tr -s " "'
t pty python3 -c 'import pty,os;pid,fd=pty.fork()
if pid==0: os.execv("/bin/echo",["echo","ok"])
print(os.read(fd,10))'
t unix-path-socket python3 -c 'import socket,os;s=socket.socket(socket.AF_UNIX);s.bind("/tmp/t.sock");s.listen();os.unlink("/tmp/t.sock")'
t unix-abstract python3 -c 'import socket;s=socket.socket(socket.AF_UNIX);s.bind("\0dextop-test");s.listen()'
t shm python3 -c 'from multiprocessing import shared_memory as m;s=m.SharedMemory(create=True,size=4096);s.close();s.unlink()'
t sysv-shm python3 -c 'import ctypes;l=ctypes.CDLL(None);i=l.shmget(0,4096,0o1600);print(i);assert i>=0;l.shmctl(i,0,0)'
t inotify python3 -c 'import ctypes;l=ctypes.CDLL(None);fd=l.inotify_init1(0);assert fd>=0;assert l.inotify_add_watch(fd,b"/tmp",2)>=0'
t eventfd-epoll python3 -c 'import os,select;fd=os.eventfd(0);e=select.epoll();e.register(fd);os.eventfd_write(fd,1);print(e.poll(1))'
t threads python3 -c 'import threading;t=[threading.Thread(target=lambda:None) for _ in range(64)];[x.start() for x in t];[x.join() for x in t]'

echo "== network"
t dns getent hosts deb.debian.org
t https curl -sS -o /dev/null -w "%{http_code}" https://deb.debian.org/
t ping ping -c1 -W2 1.1.1.1
t listen-8080 python3 -c 'import socket;s=socket.socket();s.bind(("0.0.0.0",8080));s.listen()'
t listen-80 python3 -c 'import socket;s=socket.socket();s.bind(("0.0.0.0",80));s.listen()'
t loopback sh -c '(nc -l 127.0.0.1 9099 >/tmp/nc.out &) ; sleep 1; echo hi | nc -q1 127.0.0.1 9099; sleep 1; grep -q hi /tmp/nc.out'
info ip 'ip -brief addr 2>&1 | head -5'
t netlink-route ip route

echo "== development"
cat > hello.c <<'C'
#include <stdio.h>
int main(void){puts("hello");return 0;}
C
t gcc-build-run sh -c 'gcc -O2 hello.c -o hello && ./hello'
t gcc-static sh -c 'gcc -static hello.c -o hello-static && ./hello-static'
cat > lib.c <<'C'
int answer(void){return 42;}
C
t shared-lib sh -c 'gcc -shared -fPIC lib.c -o libanswer.so && python3 -c "import ctypes;assert ctypes.CDLL(\"./libanswer.so\").answer()==42"'
t make sh -c 'printf "all:\n\techo made\n" > Makefile && make'
t python-venv sh -c 'python3 -m venv /tmp/venv && /tmp/venv/bin/python -c "import sys;print(sys.prefix)"'
t pip sh -c '/tmp/venv/bin/pip --version'
t git sh -c 'rm -rf /tmp/repo && git init -q /tmp/repo && cd /tmp/repo && echo a>a && git add a && git -c user.email=a@b -c user.name=t commit -qm m && git log --oneline | wc -l'
t sqlite sqlite3 /tmp/t.db 'create table t(x); insert into t values(1); select count(*) from t;'
t node-jit node -e 'let s=0;for(let i=0;i<1e7;i++)s+=i;console.log(s)'
t exec-new-file sh -c 'cp /bin/true /tmp/mytrue && /tmp/mytrue'
t mprotect-exec python3 -c 'import mmap,ctypes;m=mmap.mmap(-1,4096,prot=mmap.PROT_READ|mmap.PROT_WRITE|mmap.PROT_EXEC);print("rwx ok")'

echo "== debugging"
cat > crash.c <<'C'
#include <stdio.h>
int main(void){int *p=0;puts("crashing");return *p;}
C
gcc -g crash.c -o crash 2>/dev/null
t strace strace -f -o /tmp/st.txt /bin/true
t gdb-run gdb -q -batch -ex run -ex bt ./crash
t gdb-attach sh -c 'sleep 60 & p=$!; gdb -q -batch -p $p -ex "info proc" -ex detach; kill $p'
t proc-maps sh -c 'head -3 /proc/self/maps'
t core-dump sh -c 'ulimit -c unlimited; cat /proc/sys/kernel/core_pattern'
info perf 'cat /proc/sys/kernel/perf_event_paranoid'
t user-namespace unshare -r true
t dmesg dmesg
t lsof sh -c 'lsof -p $$ | head -3'
t ltrace-ptrace-traceme python3 -c 'import ctypes;l=ctypes.CDLL(None);print(l.ptrace(0,0,0,0))'

echo "== devices"
t gpu-kgsl python3 -c 'import os;os.close(os.open("/dev/kgsl-3d0",os.O_RDWR))'
t input-read python3 -c 'import os,glob;os.close(os.open(sorted(glob.glob("/dev/input/event*"))[0],os.O_RDONLY))'
t uinput python3 -c 'import os;os.close(os.open("/dev/uinput",os.O_WRONLY))'
t snd sh -c 'ls /dev/snd >/dev/null'
t binder python3 -c 'import os;os.close(os.open("/dev/binder",os.O_RDWR))'
t storage-sdcard ls /storage/emulated/0
echo "== Android process limits"
# Android 12+ kills an app's child processes beyond max_phantom_processes (32 here) and
# CPU-heavy ones in the background; shell-UID sessions are exempt.
for i in $(seq 1 45); do sleep 300 & done
sleep 90
alive=$(jobs -rp | wc -l)
[ "$alive" -eq 45 ] && echo "PASS phantom-45: $alive of 45 alive after 90 s" || echo "FAIL phantom-45: $alive of 45 alive after 90 s"
kill $(jobs -p) 2>/dev/null
echo "== done"
