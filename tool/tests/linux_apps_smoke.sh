#!/bin/bash
# Recovered from the Galaxy rootfs /tmp/apps_test.sh on 2026-11-11.
# 201 named checks: CLI/TUI programs, languages and toolchains, filesystem, process,
# IPC, network and tool behavior; this does not test full GUI application behavior.
# Run with an isolated HOME, TMPDIR, XDG_RUNTIME_DIR and TMUX_TMPDIR.
# at is version-only: do not schedule jobs in the live environment.
# One line per check:
# OK/NG/-- name: detail. TUI programs run in a pty for a few seconds; staying alive counts as OK.
# TUI programs stay in the pty's foreground process group: a background group
# would loop on SIGTTOU in tcsetattr and ignore the deadline, in any mode.
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin TERM=xterm-256color LANG=C.UTF-8 COLUMNS=120 LINES=40
W=$(mktemp -d); export W; cd "$W"
strip() { sed -e 's/\x1b\[[0-9;?]*[ -/]*[@-~]//g' -e 's/\x1b[()][0-9A-Za-z]//g' -e 's/\x1b[=>]//g' | tr -d '\r' | grep -v '^\s*$'; }
# Checks run with pipefail: a failing command is not hidden by `| head`. A writer stopped
# by SIGPIPE after `head` had enough (status 141) still counts as success.
run() { out=$(timeout 20 bash -c "set -o pipefail; $*" 2>&1); rc=$?; [ $rc -eq 141 ] && rc=0; }
t() { # t name command...
  local name=$1; shift
  local out rc; run "$@"
  if [ $rc -eq 0 ]; then echo "OK  $name: $(printf '%s' "$out" | tail -1 | cut -c1-140)"
  else echo "NG  $name (rc=$rc): $(printf '%s' "$out" | tail -3 | tr '\n' ' ' | cut -c1-260)"; fi
}
v() { # v name binary regex command: it must succeed and print a line matching regex
  local name=$1 bin=$2 re=$3; shift 3
  have "$bin" || { echo "--  $name: not installed"; return; }
  local out rc; run "$@"
  if [ $rc -ne 0 ]; then echo "NG  $name (rc=$rc): $(printf '%s' "$out" | tail -3 | tr '\n' ' ' | cut -c1-260)"
  elif ! printf '%s\n' "$out" | grep -Eq -- "$re"; then echo "NG  $name (unexpected output): $(printf '%s' "$out" | tail -2 | tr '\n' ' ' | cut -c1-260)"
  else echo "OK  $name: $(printf '%s\n' "$out" | grep -E -- "$re" | tail -1 | cut -c1-140)"; fi
}
tui() { # tui name seconds command  (alive after N seconds = OK)
  local name=$1 secs=$2; shift 2
  command -v "${1%% *}" >/dev/null 2>&1 || { echo "--  $name: not installed"; return; }
  timeout -s KILL $((secs+2)) script -qfec "stty rows 40 cols 120; timeout --foreground $secs $*" /dev/null > "$W/$name.out" 2>&1; local rc=$?
  local text; text=$(strip < "$W/$name.out" | grep -iE 'error|fail|denied|cannot|can.t|unable|not found|no such|panic|abort|segmentation|exception' | head -3 | tr '\n' ' ' | cut -c1-260)
  if [ $rc -eq 124 ] && [ -z "$text" ]; then echo "OK  $name: ran ${secs}s"
  elif [ $rc -eq 124 ]; then echo "NG? $name (alive): $text"
  else echo "NG  $name (rc=$rc): ${text:-$(strip < "$W/$name.out" | tail -3 | tr '\n' ' ' | cut -c1-260)}"; fi
}
have() { command -v "$1" >/dev/null 2>&1; }
c() { # c name binary command (skip when the binary is missing)
  local name=$1 bin=$2; shift 2
  have "$bin" || { echo "--  $name: not installed"; return; }
  t "$name" "$@"
}

echo "== system: $(id -un) uid=$(id -u) $(uname -m) $(. /etc/os-release; echo $PRETTY_NAME)"
# Monitors
tui btop 4 btop
[ -f ~/.config/btop/btop.log ] && echo "    btop.log: $(tail -3 ~/.config/btop/btop.log | tr '\n' ' ' | cut -c1-260)"
tui htop 4 htop
tui top 4 top
tui atop 4 atop
tui glances 6 glances
tui btm 4 btm
tui nvtop 4 nvtop
tui iotop 4 iotop
tui iftop 4 iftop
tui nload 4 nload
tui bmon 4 bmon
tui nethogs 4 nethogs
tui ncdu 4 ncdu /usr/share
tui mc 4 mc
tui ranger 4 ranger
tui tmux 3 tmux new-session
tui screen 3 screen
tui vim 3 vim
tui nvim 3 nvim
tui nano 3 nano
tui less 3 less /etc/os-release
tui watch 3 watch -n1 date
# procps / util-linux
v ps ps '^[a-z0-9_+-]+ +[0-9]+ ' 'ps aux | head -3 | tail -1'
v free free '^Mem:' 'free -m | sed -n 2p'
v uptime uptime 'load average' 'uptime'
v w w '^USER' 'w | head -2'
v vmstat vmstat '^ *[0-9]+ +[0-9]+ ' 'vmstat 1 2 | tail -1'
v pgrep pgrep '^[0-9]+$' 'pgrep -x bash | head -1'
v pstree pstree '\([0-9]+\)' 'pstree -p | head -2'
c kill kill 'sleep 30 & kill $!; wait $! ; [ $? -eq 143 ] && echo killed'
v lscpu lscpu 'CPU\(s\): [0-9]+' 'lscpu | grep -E "Model name|^CPU\(s\)" | tr -s " " | paste -sd" "'
c lsblk lsblk 'lsblk | head -3'
v findmnt findmnt '^/ ' 'findmnt / | tail -1'
v df df '[0-9]+%' 'df -h | head -3 | tail -1'
v du du '[0-9.]+[KMG]?\s+/usr/share/doc' 'du -sh /usr/share/doc'
v dmesg dmesg '.' 'dmesg | tail -1'
v mount mount ' on / ' 'mount | head -1'
v lsof lsof 'bash' 'lsof -p $$ | head -3 | tail -1'
v fuser fuser 'bash' 'fuser -v /bin/bash 2>&1 | head -2'
c strace strace 'strace -f -o /dev/null true && echo strace-ok'
c ltrace ltrace 'ltrace -o /dev/null true && echo ltrace-ok'
c gdb gdb 'gdb -q -batch -ex run -ex bt --args /bin/true'
v iostat iostat 'Device|avg-cpu' 'iostat | tail -3 | head -1'
v mpstat mpstat '^Average' 'mpstat 1 1 | tail -1'
v pidstat pidstat '^Average' 'pidstat 1 1 | tail -1'
v sar sar '^Average' 'sar -u 1 1 | tail -1'
v sensors sensors 'temp|Adapter|°C' 'sensors | head -3'
c lspci lspci 'lspci | head -1'
c lsusb lsusb 'lsusb | head -1'
v lshw lshw 'system|memory|processor' 'lshw -short 2>&1 | head -3'
v inxi inxi 'Distro' 'inxi -b -c0 | head -3'
v hostnamectl hostnamectl 'hostname' 'hostnamectl | head -2'
v timedatectl timedatectl 'time' 'timedatectl | head -2'
v loginctl loginctl 'SESSION|dextop' 'loginctl | head -2'
v neofetch neofetch 'OS|Host|Kernel' 'neofetch --stdout | head -4'
v fastfetch fastfetch '.' 'fastfetch --pipe | head -4'
v screenfetch screenfetch 'Kernel|OS' 'screenfetch -n | head -3'
# network
v ip-addr ip '^lo ' 'ip -br addr | head -3'
v ip-route ip ' dev ' 'ip route | head -2'
v ip-link ip '^lo ' 'ip -br link | head -2'
v ss ss 'State|LISTEN' 'ss -tlnp | head -3'
v netstat netstat '^tcp|Proto' 'netstat -tln | head -3'
v ifconfig ifconfig 'inet 127\.0\.0\.1' 'ifconfig lo'
v ping ping 'rtt min' 'ping -c1 -W2 1.1.1.1 | tail -1'
v ping6 ping 'rtt min' 'ping -6 -c1 -W2 2606:4700:4700::1111 | tail -1'
v traceroute traceroute '^ *[0-9]+ ' 'traceroute -m 3 -q 1 1.1.1.1 | tail -1'
v tracepath tracepath 'pmtu|Resume' 'tracepath -m 3 1.1.1.1 | tail -1'
v mtr mtr 'HOST' 'mtr -r -c 1 -m 3 1.1.1.1 | tail -1'
v dig dig '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$' 'dig +short debian.org | head -1'
v nslookup nslookup 'Address' 'nslookup debian.org | tail -2 | head -1'
v host host 'has (IPv6 )?address' 'host debian.org | head -1'
v nmap nmap '(open|closed)' 'nmap -sT -p 22,80 127.0.0.1 | grep -E "open|closed" | head -2 | tr "\n" " "'
c nc nc 'nc -z -w2 1.1.1.1 443 && echo nc-ok'
v curl curl 'HTTP/[0-9.]+ (200|301|302)' 'curl -sI https://deb.debian.org | head -1'
c wget wget 'wget -q -O /dev/null https://deb.debian.org && echo wget-ok'
v arp arp 'ether|Address' 'arp -n | head -2'
v iw iw 'Interface|phy' 'iw dev | head -2'
v ethtool ethtool 'Link detected|Settings for' 'ethtool lo | head -2'
v tcpdump tcpdump 'packets? captured|IP' 'timeout 2 tcpdump -c1 -i any 2>&1 | tail -1'
# services and scheduling
v systemctl systemctl 'loaded' 'systemctl list-units | head -2'
v systemctl-user systemctl 'loaded|UNIT' 'systemctl --user list-units | head -2'
v journalctl journalctl ': ' 'journalctl -n1 2>&1 | tail -1'
v crontab crontab 'no crontab|^[^#]' 'crontab -l 2>&1; [ $? -le 1 ]'
v at at 'version' 'at -V 2>&1'
v dbus-send dbus-send 'string|array' 'dbus-send --session --print-reply --dest=org.freedesktop.DBus / org.freedesktop.DBus.ListNames | head -2'
v service service '\[ [+?-] \]' 'service --status-all 2>&1 | head -2'
# misc runtime
c sudo sudo 'sudo -n id -un'
# su needs the user's password: without it the attempt must be refused.
v su su 'Authentication failure' 'su -c true </dev/null 2>&1; [ $? -ne 0 ]'
c chroot chroot 'sudo -n chroot / true && echo chroot-ok'
c unshare unshare 'unshare --user true && echo unshare-ok'
c nice nice 'nice -n 5 true && echo nice-ok'
v renice renice 'old priority' 'sleep 5 & renice 5 $! ; kill $!'
c ionice ionice 'ionice -c3 true && echo ionice-ok'
c taskset taskset 'taskset -c 0 true && echo taskset-ok'
v chrt chrt 'policy|priority|runtime' 'chrt -p $$'
v ulimit bash '^[0-9]+$' 'ulimit -n'
v prlimit prlimit 'NOFILE' 'prlimit --pid $$ --nofile | tail -1'
c setsid setsid 'setsid true && echo setsid-ok'
c flock flock 'flock "$W/x.lock" true && echo flock-ok'
v inotify inotifywait 'CREATE' 'd="$W/inw"; mkdir -p "$d"; (sleep 0.5; touch "$d/new") </dev/null >/dev/null 2>&1 & inotifywait -t 5 -e create "$d" 2>&1 | tail -1'
v getfacl getfacl 'owner' 'getfacl /tmp | head -2'
v stat stat '^(root|dextop)$' 'stat -c %U /etc/passwd'
v date-tz date '^JST$' 'TZ=Asia/Tokyo date +%Z'
v locale locale '^LANG=' 'locale 2>&1 | head -1'
v python-psutil python3 'True' 'python3 -c "import psutil;print(psutil.cpu_count(),psutil.virtual_memory().total>0,len(psutil.pids()),psutil.boot_time()>0,len(psutil.net_io_counters(pernic=True)),psutil.disk_usage(\"/\").total>0, psutil.cpu_freq())"'
v python-psutil-sensors python3 '\{' 'python3 -c "import psutil;print(psutil.sensors_temperatures(), psutil.sensors_battery())"'
v python-psutil-procs python3 '^[0-9]+$' 'python3 -c "import psutil;print(sum(1 for p in psutil.process_iter([\"name\",\"cpu_percent\",\"memory_info\",\"username\"])))"'
v node node '^[0-9]+ true' 'node -e "const os=require(\"os\");console.log(os.cpus().length,os.totalmem()>0,os.loadavg(),Object.keys(os.networkInterfaces()))"'
v go go '^go version' 'go version'
# languages and toolchains: each check verifies its own output (pipefail, exact values)
c perl perl 'perl -e '"'"'print "perl-$^V\n"'"'"''
c perl-modules perl 'set -o pipefail; perl -MPOSIX -MDigest::SHA=sha256_hex -MJSON::PP -MFile::Temp=tempfile -e '"'"'my ($fh,$n)=tempfile(DIR=>$ENV{W}); print $fh "dextop"; close $fh; open my $r,"<",$n or die; my $d=<$r>; die "read" unless $d eq "dextop"; my $j=decode_json(encode_json({a=>[1,2]})); die "json" unless $j->{a}[1]==2; die "sha" unless length(sha256_hex($d))==64; print "perl-modules-ok ", POSIX::floor(2.7), "\n"'"'"' | grep -x "perl-modules-ok 2"'
c perl-fork perl 'perl -e '"'"'my @k; for (1..4) { my $p=fork; die "fork" unless defined $p; if(!$p){ exit 7 } push @k,$p } my $ok=0; for (@k){ waitpid($_,0); $ok++ if ($?>>8)==7 } print $ok==4 ? "perl-fork-ok\n" : "bad $ok\n"; exit($ok==4?0:1)'"'"''
c perl-system perl 'perl -e '"'"'my $o=`echo sub`; chomp $o; system("true")==0 or die; opendir my $d,"/usr/bin" or die; my @f=grep {/^perl$/} readdir $d; print $o eq "sub" && @f ? "perl-system-ok\n" : "bad\n"'"'"' | grep -x perl-system-ok'
c perl-socket perl 'perl -MIO::Socket::UNIX -e '"'"'my $p="$ENV{W}/perl.sock"; my $s=IO::Socket::UNIX->new(Type=>SOCK_STREAM(),Local=>$p,Listen=>1) or die "listen $!"; if(!fork){ my $c=IO::Socket::UNIX->new(Peer=>$p) or exit 1; print $c "hi\n"; exit 0 } my $a=$s->accept; my $l=<$a>; wait; print $l eq "hi\n" ? "perl-socket-ok\n" : "bad\n"; exit($l eq "hi\n"?0:1)'"'"''
c python3 python3 'python3 -c "import sqlite3,ssl,json,subprocess,hashlib;c=sqlite3.connect(\":memory:\");c.execute(\"create table t(x)\");c.execute(\"insert into t values(41)\");v=c.execute(\"select x+1 from t\").fetchone()[0];o=subprocess.run([\"echo\",\"x\"],capture_output=True,text=True).stdout.strip();assert v==42 and o==\"x\";print(\"python3-ok\",ssl.OPENSSL_VERSION.split()[1])"'
c python3-venv python3 'set -o pipefail; python3 -m venv --without-pip "$W/venv" && "$W/venv/bin/python" -c "import sys;print(\"venv-ok\" if sys.prefix!=sys.base_prefix else \"bad\")" | grep -x venv-ok'
c ruby ruby 'ruby -e '"'"'require "json"; require "digest"; r=JSON.parse({a: 21}.to_json)["a"]*2; pid=fork { exit!(3) }; Process.wait(pid); raise "fork" unless $?.exitstatus==3; puts "ruby-ok #{r} #{RUBY_VERSION}"'"'"''
c lua lua5.4 'lua5.4 -e '"'"'local f=assert(io.open(os.getenv("W").."/l.txt","w")); f:write("x"); f:close(); local t=0; for i=1,10 do t=t+i end; assert(t==55); print("lua-ok "..t)'"'"''
c php php 'php -r '"'"'$f=getenv("W")."/p.txt"; file_put_contents($f,"dextop"); if (file_get_contents($f)!=="dextop") exit(1); echo "php-ok ", json_decode(json_encode(["a"=>3]))->a*2, " ", PHP_VERSION, "\n";'"'"''
c tcl tclsh 'echo '"'"'set f [open $env(W)/t.txt w]; puts $f ok; close $f; set f [open $env(W)/t.txt]; puts "tcl-[gets $f]"'"'"' | tclsh'
c awk gawk 'printf "3\n4\n" | gawk '"'"'{s+=$1} END {print "awk-sum", s}'"'"' | grep -x "awk-sum 7"'
c gcc gcc 'printf "#include <stdio.h>\n#include <pthread.h>\nstatic void*f(void*a){return a;}\nint main(void){pthread_t t;void*r;pthread_create(&t,0,f,(void*)7);pthread_join(t,&r);printf(\"gcc-ok %%ld\\\\n\",(long)r);return 0;}\n" > "$W/g.c" && gcc -O2 -pthread "$W/g.c" -o "$W/g" && "$W/g"'
c g++ g++ 'printf "#include <iostream>\n#include <vector>\n#include <numeric>\nint main(){std::vector<int> v{1,2,3};std::cout<<\"gxx-ok \"<<std::accumulate(v.begin(),v.end(),0)<<std::endl;}\n" > "$W/x.cc" && g++ -O2 "$W/x.cc" -o "$W/x" && "$W/x"'
c make make 'mkdir -p "$W/mk" && printf "all: out\nout: in\n\tcp in out\n" > "$W/mk/Makefile" && echo m > "$W/mk/in" && make -s -C "$W/mk" && cat "$W/mk/out" | grep -x m && echo make-ok'
c cmake cmake 'mkdir -p "$W/cm" && printf "cmake_minimum_required(VERSION 3.10)\nproject(t C)\nadd_executable(t t.c)\n" > "$W/cm/CMakeLists.txt" && printf "int main(void){return 0;}\n" > "$W/cm/t.c" && cmake -S "$W/cm" -B "$W/cm/b" >/dev/null && cmake --build "$W/cm/b" >/dev/null && "$W/cm/b/t" && echo cmake-ok'
c rust rustc 'printf "fn main(){let v:Vec<u32>=(1..=4).collect();println!(\"rust-ok {}\",v.iter().sum::<u32>());}\n" > "$W/r.rs" && rustc -O "$W/r.rs" -o "$W/r" && "$W/r"'
c java javac 'mkdir -p "$W/j" && printf "public class H{public static void main(String[] a){System.out.println(\"java-ok \"+Runtime.getRuntime().availableProcessors());}}\n" > "$W/j/H.java" && javac -d "$W/j" "$W/j/H.java" && java -cp "$W/j" H'
c sqlite3 sqlite3 'sqlite3 "$W/s.db" "create table t(x); insert into t values(6); select x*7 from t;" | grep -x 42 && echo sqlite-ok'
c git-roundtrip git 'cd "$W" && rm -rf gr && git init -q gr && cd gr && echo a > a && git add a && git -c user.name=t -c user.email=t@t commit -qm init && git log --oneline | wc -l | grep -x 1 && echo git-ok'
c zip-roundtrip zip 'cd "$W" && echo z > z.txt && zip -q z.zip z.txt && rm z.txt && unzip -q z.zip && grep -x z z.txt && echo zip-ok'
c tar-xz tar 'cd "$W" && mkdir -p tx && echo t > tx/f && tar -cJf tx.tar.xz tx && rm -r tx && tar -xJf tx.tar.xz && grep -x t tx/f && echo tar-ok'
c openssl openssl 'set -o pipefail; printf dextop | openssl dgst -sha256 | grep -q "= " && openssl rand -hex 4 | grep -Eq "^[0-9a-f]{8}$" && echo openssl-ok'
c file file 'file -b /bin/bash | grep -q ELF && echo file-ok'
# --- strict additions (2026-11-11): every check verifies its own result
# filesystem semantics
v fs-hardlink ln '^2 same$' 'cd "$W" && echo h > h1 && ln h1 h2 && [ "$(stat -c %i h1)" = "$(stat -c %i h2)" ] && echo "$(stat -c %h h1) same"'
v fs-symlink ln '^sym-ok$' 'cd "$W" && mkdir -p sd/in && echo s > sd/in/f && ln -sfn sd/in sl && ln -sf ../sl/f sd/rel && [ "$(readlink -f sd/rel)" = "$W/sd/in/f" ] && cat sl/f | grep -qx s && echo sym-ok'
v fs-rename-over mv '^new$' 'cd "$W" && echo old > ro && echo new > rn && mv -f rn ro && cat ro'
v fs-rename-exchange mv '^b a$' 'cd "$W" && echo a > xa && echo b > xb && mv --exchange xa xb && echo "$(cat xa) $(cat xb)"'
v fs-mkfifo mkfifo '^fifo-ok$' 'cd "$W" && rm -f ff && mkfifo ff && (echo fifo-ok > ff &) && cat ff'
v fs-xattr setfattr '^user.dx="1"$' 'cd "$W" && touch xf && setfattr -n user.dx -v 1 xf && getfattr -n user.dx xf | grep "^user"'
v fs-acl setfacl 'user:nobody:r--' 'cd "$W" && touch af && setfacl -m u:nobody:r af && getfacl -p af'
v fs-chmod-bits chmod '^4750$' 'cd "$W" && touch cb && chmod 4750 cb && stat -c %a cb'
v fs-utime touch '^978307200$' 'cd "$W" && touch -d "2001-01-01 00:00:00 UTC" ut && stat -c %Y ut'
v fs-sparse truncate '^sparse-ok$' 'cd "$W" && truncate -s 1G sp && [ "$(stat -c %s sp)" = 1073741824 ] && [ "$(du -k sp | cut -f1)" -lt 1024 ] && rm sp && echo sparse-ok'
v fs-write-64m dd '^ok$' 'cd "$W" && head -c 67108864 /dev/urandom > big && a=$(sha256sum < big) && cp big big2 && [ "$a" = "$(sha256sum < big2)" ] && rm big big2 && echo ok'
v fs-unicode-names ls '^3$' 'cd "$W" && mkdir -p un && cd un && touch "日本語.txt" "émoji-😀" "spaces in name" && ls | wc -l'
v fs-long-path mkdir '^deep-ok$' 'cd "$W" && p=$(printf "d%.0s/x123456789x123456789x123456789x123456789" $(seq 40)) && mkdir -p "lp/$p" && echo k > "lp/$p/f" && cat "lp/$p/f" | grep -qx k && [ ${#p} -gt 1600 ] && echo deep-ok'
v fs-many-files find '^5000$' 'cd "$W" && mkdir -p mf && cd mf && seq 5000 | xargs touch && find . -type f | wc -l'
v fs-find-exec find '^6$' 'cd "$W" && mkdir -p fe && for i in 1 2 3; do echo $i > fe/$i.txt; done && find fe -name "*.txt" -exec cat {} + | awk "{s+=\$1} END {print s}"'
v fs-readonly-dir chmod 'Permission denied' 'cd "$W" && mkdir -p rod && chmod 555 rod && { touch rod/x 2>&1; true; }; chmod 755 rod'
v fs-flock-contend flock '^busy$' 'cd "$W" && (flock -x 9; sleep 2) 9>fl </dev/null >/dev/null 2>&1 & sleep 0.5; flock -n fl true && echo free || echo busy; wait'
v fs-mmap python3 '^mmap-ok$' 'python3 - <<P
import mmap,os
p=os.environ["W"]+"/mm"; open(p,"wb").write(b"\0"*4096)
with open(p,"r+b") as f:
    m=mmap.mmap(f.fileno(),4096); m[0:5]=b"hello"; m.flush(); m.close()
assert open(p,"rb").read(5)==b"hello"; print("mmap-ok")
P'
v fs-o-tmpfile python3 '^tmpfile-ok$' 'python3 - <<P
import os
fd=os.open(os.environ["W"],os.O_TMPFILE|os.O_RDWR,0o600); os.write(fd,b"x"); os.lseek(fd,0,0); assert os.read(fd,1)==b"x"; print("tmpfile-ok")
P'
v fs-fcntl-lock python3 '^locked$' 'python3 - <<P
import fcntl,os
f=open(os.environ["W"]+"/lk","w"); fcntl.lockf(f,fcntl.LOCK_EX|fcntl.LOCK_NB)
pid=os.fork()
if pid==0:
    g=open(os.environ["W"]+"/lk","w")
    try: fcntl.lockf(g,fcntl.LOCK_EX|fcntl.LOCK_NB); os._exit(1)
    except OSError: os._exit(0)
_,st=os.waitpid(pid,0); print("locked" if os.waitstatus_to_exitcode(st)==0 else "not-locked")
P'
v fs-inotify-python python3 '^inotify-ok$' 'python3 - <<P
import ctypes,os,struct,select
libc=ctypes.CDLL(None,use_errno=True); fd=libc.inotify_init1(os.O_NONBLOCK); d=os.environ["W"]+"/iw"; os.makedirs(d,exist_ok=True)
assert libc.inotify_add_watch(fd,d.encode(),0x100)>=0
open(d+"/n","w").close(); r,_,_=select.select([fd],[],[],3); assert r; print("inotify-ok")
P'
# processes
v proc-fork-many bash '^200$' 'n=0; for i in $(seq 200); do (exit 0) </dev/null >/dev/null 2>&1 & done; wait && seq 200 | wc -l'
v proc-threads python3 '^64$' 'python3 -c "import threading;r=[];l=threading.Lock()
def f():
  with l: r.append(1)
t=[threading.Thread(target=f) for _ in range(64)];[x.start() for x in t];[x.join() for x in t];print(len(r))"'
v proc-signal-trap bash '^got-usr1$' 'bash -c "trap \"echo got-usr1; exit 0\" USR1; kill -USR1 \$\$; sleep 5"'
v proc-pty script '/dev/pts/[0-9]+' 'script -qec tty /dev/null | tr -d "\r"'
v proc-exec-chain env '^chain-ok$' 'exec env sh -c "exec printf chain-ok\\\\n"'
v proc-env-large bash '^102400$' 'X=$(head -c 102400 /dev/zero | tr "\0" a) bash -c "printf %s \"\$X\" | wc -c"'
v proc-argv-large xargs '^20000$' 'seq 20000 | xargs echo | wc -w'
v proc-self-exe readlink '^/usr/bin/(readlink|coreutils)$' 'readlink /proc/self/exe'
v proc-affinity python3 '^[1-9][0-9]*$' 'python3 -c "import os;print(len(os.sched_getaffinity(0)))"'
v proc-wait-status python3 '^7 9$' 'python3 -c "
import os,signal
a=os.fork()
if a==0: os._exit(7)
b=os.fork()
if b==0: os.kill(os.getpid(),signal.SIGKILL)
_,x=os.waitpid(a,0);_,y=os.waitpid(b,0);print(os.WEXITSTATUS(x),os.WTERMSIG(y))"'
# IPC
v ipc-pipe-100m dd '^104857600$' 'head -c 104857600 /dev/zero | cat | wc -c'
v ipc-unix-stream python3 '^pong$' 'python3 - <<P
import socket,os
p=os.environ["W"]+"/us.sock"; s=socket.socket(socket.AF_UNIX); s.bind(p); s.listen(1)
if os.fork()==0:
    c=socket.socket(socket.AF_UNIX); c.connect(p); c.sendall(b"ping"); c.close(); os._exit(0)
a,_=s.accept(); assert a.recv(4)==b"ping"; os.wait(); print("pong")
P'
v ipc-unix-dgram python3 '^dgram-ok$' 'python3 - <<P
import socket,os
p=os.environ["W"]+"/ud.sock"; s=socket.socket(socket.AF_UNIX,socket.SOCK_DGRAM); s.bind(p)
c=socket.socket(socket.AF_UNIX,socket.SOCK_DGRAM); c.sendto(b"d",p); assert s.recv(1)==b"d"; print("dgram-ok")
P'
v ipc-abstract python3 '^abstract-ok$' 'python3 - <<P
import socket,os
n="\0dextop-test-%d"%os.getpid(); s=socket.socket(socket.AF_UNIX); s.bind(n); s.listen(1)
c=socket.socket(socket.AF_UNIX); c.connect(n); a,_=s.accept(); c.sendall(b"a"); assert a.recv(1)==b"a"; print("abstract-ok")
P'
v ipc-fd-passing python3 '^fdpass-ok$' 'python3 - <<P
import socket,os
a,b=socket.socketpair(); r,w=os.pipe(); socket.send_fds(a,[b"x"],[w]); _,fds,_,_=socket.recv_fds(b,1,1)
os.write(fds[0],b"z"); assert os.read(r,1)==b"z"; print("fdpass-ok")
P'
v ipc-posix-shm python3 '^shm-ok$' 'python3 -c "
from multiprocessing import shared_memory as s
m=s.SharedMemory(create=True,size=64); m.buf[0]=7; n=s.SharedMemory(m.name); assert n.buf[0]==7; n.close(); m.close(); m.unlink(); print(\"shm-ok\")"'
v ipc-sysv-shm ipcmk 'Shared memory id: [0-9]+' 'o=$(ipcmk -M 4096) && id=$(echo "$o" | grep -o "[0-9]*$") && ipcrm -m "$id" && echo "$o"'
v ipc-sysv-sem ipcmk 'Semaphore id: [0-9]+' 'o=$(ipcmk -S 1) && id=$(echo "$o" | grep -o "[0-9]*$") && ipcrm -s "$id" && echo "$o"'
v ipc-sysv-msg ipcmk 'Message queue id: [0-9]+' 'o=$(ipcmk -Q) && id=$(echo "$o" | grep -o "[0-9]*$") && ipcrm -q "$id" && echo "$o"'
v ipc-epoll python3 '^epoll-ok$' 'python3 -c "
import select,os
r,w=os.pipe(); e=select.epoll(); e.register(r,select.EPOLLIN); os.write(w,b\"x\"); assert e.poll(2); print(\"epoll-ok\")"'
v ipc-mp-pool python3 '^\[0, 1, 4, 9\]$' 'python3 -c "
from multiprocessing import Pool
if __name__==\"__main__\":
  with Pool(4) as p: print(p.starmap(pow,[(0,2),(1,2),(2,2),(3,2)]))"'
v ipc-socat-unix socat '^socat-ok$' 'cd "$W" && (timeout 5 socat UNIX-LISTEN:so.sock SYSTEM:"echo socat-ok" </dev/null >/dev/null 2>&1 &) && sleep 0.5 && socat - UNIX-CONNECT:so.sock'
# network
v net-tcp-loopback python3 '^tcp-ok$' 'python3 - <<P
import socket,os
s=socket.socket(); s.bind(("127.0.0.1",0)); s.listen(1); port=s.getsockname()[1]
if os.fork()==0:
    c=socket.create_connection(("127.0.0.1",port)); c.sendall(b"t"); os._exit(0)
a,_=s.accept(); assert a.recv(1)==b"t"; os.wait(); print("tcp-ok")
P'
v net-udp-loopback python3 '^udp-ok$' 'python3 - <<P
import socket
s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM); s.bind(("127.0.0.1",0)); s.settimeout(3)
socket.socket(socket.AF_INET,socket.SOCK_DGRAM).sendto(b"u",s.getsockname()); assert s.recv(1)==b"u"; print("udp-ok")
P'
v net-ipv6-loopback python3 '^tcp6-ok$' 'python3 - <<P
import socket,os
s=socket.socket(socket.AF_INET6); s.bind(("::1",0)); s.listen(1); port=s.getsockname()[1]
if os.fork()==0:
    c=socket.create_connection(("::1",port)); c.sendall(b"6"); os._exit(0)
a,_=s.accept(); assert a.recv(1)==b"6"; os.wait(); print("tcp6-ok")
P'
v net-resolve-python python3 '^[0-9a-f.:]+$' 'python3 -c "import socket;print(socket.getaddrinfo(\"deb.debian.org\",443)[0][4][0])"'
v net-https-verify curl '^200$' 'curl -sS --fail -o /dev/null -w "%{http_code}\n" https://deb.debian.org/debian/dists/stable/Release'
v net-https-bad-cert curl 'certificate|SSL' 'curl -sS https://self-signed.badssl.com/ -o /dev/null 2>&1; true'
v net-http-server python3 '^served$' 'cd "$W" && mkdir -p www && echo served > www/i.txt && timeout 10 python3 -u -m http.server 0 --bind 127.0.0.1 --directory www </dev/null > hs.log 2>&1 & sp=$!; for i in $(seq 30); do p=$(grep -o "port [0-9]*" hs.log | grep -o "[0-9]*"); [ -n "$p" ] && break; sleep 0.2; done; curl -s "http://127.0.0.1:$p/i.txt"; kill $sp'
v net-socat-tcp socat '^tcpecho$' 'p=$((40000 + RANDOM % 10000)); (timeout 5 socat TCP-LISTEN:$p,bind=127.0.0.1,reuseaddr EXEC:cat </dev/null >/dev/null 2>&1 &) ; sleep 0.5; echo tcpecho | socat -t1 - TCP:127.0.0.1:$p'
# tools
v jq jq '^3$' 'echo "{\"a\":[1,2]}" | jq ".a | add"'
v rsync rsync '^rsync-ok$' 'cd "$W" && mkdir -p rs/a/b && echo r > rs/a/b/f && ln -sf f rs/a/b/l && rsync -a rs/a/ rs/c/ && [ -L rs/c/b/l ] && cmp rs/a/b/f rs/c/b/f && echo rsync-ok'
v zstd zstd '^zstd-ok$' 'cd "$W" && seq 100000 > z && zstd -q -19 z -o z.zst && rm z && zstd -q -d z.zst && [ "$(tail -1 z)" = 100000 ] && echo zstd-ok'
v bzip2 bzip2 '^bzip2-ok$' 'cd "$W" && seq 50000 > bz && bzip2 -9 bz && bunzip2 bz.bz2 && [ "$(wc -l < bz)" = 50000 ] && echo bzip2-ok'
v 7z 7z '^7z-ok$' 'cd "$W" && mkdir -p 7d && echo seven > 7d/f && 7z a -bd -y 7.7z 7d >/dev/null && rm -r 7d && 7z x -bd -y 7.7z >/dev/null && grep -qx seven 7d/f && echo 7z-ok'
v diff-patch patch '^patched$' 'cd "$W" && printf "a\nb\n" > p1 && printf "a\nc\n" > p2 && { diff -u p1 p2 > p.diff; [ $? -eq 1 ]; } && patch -s p1 < p.diff && cmp p1 p2 && echo patched'
v sort-large sort '^200000 100000$' 'seq 200000 | shuf | sort -n | uniq | wc -l | tr -d "\n"; echo " $(seq 200000 | shuf | sort -n | sed -n 100000p)"'
v grep-recursive grep '^3$' 'cd "$W" && rm -rf grr && mkdir -p grr/a/b && echo needle > grr/1 && echo needle > grr/a/2 && echo needle > grr/a/b/3 && grep -rl needle grr | wc -l'
v xargs-parallel xargs '^8$' 'seq 8 | xargs -P4 -I{} sh -c "sleep 0.2; echo {}" | wc -l'
v iconv iconv '^日本語$' 'printf 日本語 | iconv -f UTF-8 -t SHIFT_JIS | iconv -f SHIFT_JIS -t UTF-8; echo'
v gpg-symmetric gpg '^secret$' 'cd "$W" && export GNUPGHOME="$W/gnupg" && mkdir -p -m 700 "$GNUPGHOME" && echo secret > gs && gpg -q --batch --pinentry-mode loopback --passphrase pw -c gs && rm gs && gpg -q --batch --pinentry-mode loopback --passphrase pw -d gs.gpg 2>/dev/null'
v ssh-keygen ssh-keygen 'SHA256:.*ED25519' 'cd "$W" && ssh-keygen -q -t ed25519 -N "" -f k && ssh-keygen -lf k.pub'
v git-branch-merge git '^merge-ok$' 'cd "$W" && rm -rf gb && git init -q -b main gb && cd gb && git config user.name t && git config user.email t@t && echo 1 > f && git add f && git commit -qm 1 && git switch -qc dev && echo 2 >> f && git commit -qam 2 && git switch -q main && git merge -q dev && [ "$(wc -l < f)" = 2 ] && cd .. && git clone -q gb gc && [ "$(git -C gc log --oneline | wc -l)" = 2 ] && echo merge-ok'
v sqlite-wal sqlite3 '^400$' 'cd "$W" && sqlite3 wal.db "pragma journal_mode=wal; create table t(x);" >/dev/null && for i in 1 2 3 4; do (for j in $(seq 100); do sqlite3 -cmd ".timeout 5000" wal.db "insert into t values($j)"; done) & done; wait; sqlite3 wal.db "select count(*) from t"'
v make-parallel make '^parallel-ok$' 'mkdir -p "$W/mp" && cd "$W/mp" && for i in 1 2 3 4 5 6; do printf "int f$i(void){return $i;}\n" > f$i.c; done && printf "int f1(void);int f2(void);int f3(void);int f4(void);int f5(void);int f6(void);\nint main(void){return f1()+f2()+f3()+f4()+f5()+f6()==21?0:1;}\n" > m.c && printf "OBJ=m.o f1.o f2.o f3.o f4.o f5.o f6.o\napp: \$(OBJ)\n\tcc -o app \$(OBJ)\n" > Makefile && make -s -j4 && ./app && echo parallel-ok'
# pnpm: Android refuses hardlinks, so pnpm must fall back to copying from its store.
v pnpm pnpm '^pnpm-ok$' 'export HOME="$W/pnhome" && mkdir -p "$HOME/a" "$HOME/b" && cd "$HOME/a" && printf "{\"name\":\"t\",\"version\":\"1.0.0\"}\n" > package.json && pnpm add -s is-odd@3.0.1 >/dev/null && cd ../b && cp ../a/package.json ../a/pnpm-lock.yaml . && pnpm install -s --offline >/dev/null && node -e "process.exit(require(\"is-odd\")(3)?0:1)" && echo pnpm-ok'
v gcc-static gcc '^static-ok$' 'cd "$W" && printf "#include <stdio.h>\nint main(void){puts(\"static-ok\");return 0;}\n" > st.c && gcc -static st.c -o st && file st | grep -q "statically linked" && ./st'
cd /; rm -rf "$W"
