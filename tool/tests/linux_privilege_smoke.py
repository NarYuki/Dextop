#!/usr/bin/env python3
"""Exercise privilege-related Linux commands in disposable files/processes.
Never changes Android mounts, firewall rules, users, modules or power state.
"""
import json
import os
from pathlib import Path
import shlex
import signal
import subprocess
import tempfile
import time

results=[]

def run(name, command, kind='normal', expected=None, timeout=12):
    started=time.monotonic()
    p=subprocess.Popen(['/bin/sh','-c',command],stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,start_new_session=True)
    try:
        output,_=p.communicate(timeout=timeout); rc=p.returncode
    except subprocess.TimeoutExpired:
        os.killpg(p.pid,signal.SIGKILL);output,_=p.communicate();rc='TIMEOUT'
    status='ok' if rc==0 else 'rejected'
    if rc=='TIMEOUT':status='hung'
    if rc==0 and expected is not None and expected not in output:status='wrong-result'
    row=dict(name=name,kind=kind,status=status,code=rc,seconds=round(time.monotonic()-started,3),output=output[-3500:])
    results.append(row);print(json.dumps(row),flush=True)
    return rc,output

with tempfile.TemporaryDirectory(prefix='dextop-privilege-check-') as temp:
    root=Path(temp); q=shlex.quote(temp)
    (root/'file').write_text('permission-probe\n')
    (root/'device-parent').mkdir()
    (root/'mountpoint').mkdir()
    (root/'identity.c').write_text('#include <stdio.h>\n#include <unistd.h>\nint main(void){printf("uid=%u euid=%u\\n",getuid(),geteuid());return 0;}\n')
    (root/'hello.cpp').write_text('#include <iostream>\n#include <thread>\nint main(){std::thread t([]{std::cout << "cxx-thread-ok\\n";});t.join();}\n')
    (root/'Makefile').write_text('all:\n\t$(CC) identity.c -o identity\n\t./identity\n')
    run('git init and commit',f'git init -q {q}/repo && git -C {q}/repo -c user.name=DextopProbe -c user.email=probe@localhost commit -q --allow-empty -m probe && git -C {q}/repo log -1 --format=%s',expected='probe')
    run('make and gcc executable',f'cd {q} && make',expected='uid=2000')
    run('g++ and pthread',f'g++ -pthread {q}/hello.cpp -o {q}/hello && {q}/hello',expected='cxx-thread-ok',timeout=30)
    run('zip roundtrip',f'cd {q} && zip -q archive.zip file && unzip -p archive.zip file',expected='permission-probe')
    run('xz roundtrip',f'xz -c {q}/file | xz -dc',expected='permission-probe')
    for name,command,expect in [
        ('sudo -n', 'sudo -n id -u','0'),
        ('sudo root','sudo -u root id -u','0'),
        ('sudo preserve environment',"DEXTOP_PRIV_PROBE=kept sudo -E printenv DEXTOP_PRIV_PROBE",'kept'),
        ('sudo working directory',f'sudo -D {q} pwd',temp),
        ('sudo environment assignment','sudo DEXTOP_PRIV_PROBE=assigned printenv DEXTOP_PRIV_PROBE','assigned'),
        ('sudo non-root user','sudo -u nobody id -u','65534'),
        ('sudo long user option','sudo --user=nobody id -u','65534'),
        ('sudo group','sudo -g nogroup id -g','65534'),
        ('sudo shell mode',"sudo -s -- printf shell-mode-ok",'shell-mode-ok'),
        ('sudo login mode',"sudo -i -- printf login-mode-ok",'login-mode-ok'),
        ('sudo real binary','/usr/bin/sudo -n id -u','0'),
        ('su root',"su -s /bin/sh -c 'id -u' root",'0'),
        ('sudo su root',"sudo su -s /bin/sh -c 'id -u' root",'0'),
        ('sudo su nobody',"sudo su -s /bin/sh -c 'id -u' nobody",'65534'),
    ]:run(name,command,kind='identity',expected=expect)
    run('sudo exit code',"sudo sh -c 'exit 23'",kind='expected-exit-23')
    run('sudo nesting','sudo sudo id -u',expected='0')
    run('chown and chgrp',f'''sudo sh -ec 'chown 1234:2345 {q}/file; test "$(stat -c %u:%g {q}/file)" = 1234:2345; chgrp 3456 {q}/file; test "$(stat -c %g {q}/file)" = 3456; echo ownership-ok' ''',expected='ownership-ok')
    run('chmod including setuid metadata',f'''sudo sh -ec 'chmod 4750 {q}/file; stat -c %a {q}/file; chmod 600 {q}/file' ''',expected='4750')
    run('install permissions',f'install -m 750 {q}/file {q}/installed-file && stat -c %a {q}/installed-file',expected='750')
    run('mkfifo',f'mkfifo {q}/pipe && stat -c %F {q}/pipe',expected='fifo')
    run('mknod emulated metadata',f"sudo sh -ec 'mknod {q}/device-parent/null c 1 3; stat -c %F {q}/device-parent/null'",kind='metadata-emulation',expected='character special file')
    run('device node actual kernel type',f'stat -c %F {q}/device-parent/null',kind='kernel-probe')
    run('fallocate',f'fallocate -l 4096 {q}/allocated && stat -c %s {q}/allocated',expected='4096')
    run('flock mutual exclusion',f"flock -n {q}/lock sh -c 'flock -n {q}/lock true; test $? = 1' ")
    run('user xattr',f'setfattr -n user.dextop_probe -v ok {q}/file && getfattr -n user.dextop_probe --only-values {q}/file',kind='filesystem-probe',expected='ok')
    run('file ACL',f'setfacl -m u:1234:r {q}/file && getfacl -n {q}/file',kind='filesystem-probe',expected='user:1234:r--')
    run('lsattr',f'lsattr {q}/file',kind='filesystem-probe')
    run('immutable flag',f"sudo sh -c 'chattr +i {q}/file; rc=$?; chattr -i {q}/file; exit $rc'",kind='kernel-probe')
    run('setcap and getcap',f"sudo sh -ec 'setcap cap_net_bind_service=ep {q}/installed-file; getcap {q}/installed-file'",kind='metadata-emulation',expected='cap_net_bind_service=ep')
    run('capability state','sudo capsh --print',kind='kernel-probe')
    run('setpriv inspect','sudo setpriv --dump',kind='kernel-probe')
    run('setpriv identity change',"sudo setpriv --reuid=65534 --regid=65534 --clear-groups id",kind='identity',expected='uid=65534')
    run('chroot current guest root','sudo chroot / /usr/bin/id',kind='kernel-probe')
    run('chroot isolated root',f'mkdir -p {q}/chroot/bin && gcc -static {q}/identity.c -o {q}/chroot/bin/identity && sudo chroot {q}/chroot /bin/identity',kind='kernel-probe',timeout=30)
    run('mount namespace and tmpfs',f'sudo unshare --mount --propagation private sh -ec "mount -t tmpfs tmpfs {q}/mountpoint; umount {q}/mountpoint"',kind='kernel-probe')
    run('user namespace','sudo unshare --user --map-root-user id',kind='kernel-probe')
    run('network namespace','sudo unshare --net ip link show',kind='kernel-probe')
    run('PID namespace','sudo unshare --pid --fork sh -c "echo pid=$$"',kind='kernel-probe')
    run('nsenter namespaces','sudo nsenter -t 1 -m true',kind='kernel-probe')
    run('umount own unmounted directory',f'sudo umount {q}/mountpoint',kind='expected-reject')
    run('mount fake CLI',f'sudo mount --fake --no-mtab -t tmpfs tmpfs {q}/mountpoint')
    run('sysctl read','sudo sysctl kernel.ostype kernel.osrelease',kind='kernel-probe')
    run('ip link read','sudo ip -brief link show',kind='kernel-probe')
    run('ip address read','sudo ip -brief address show',kind='kernel-probe')
    run('ip route read','sudo ip route show',kind='kernel-probe')
    run('iptables read','sudo iptables -w 1 -L -n',kind='kernel-probe')
    run('nftables read','sudo nft list tables',kind='kernel-probe')
    run('ping loopback','sudo ping -n -c 1 -W 2 127.0.0.1',kind='kernel-probe')
    run('dmesg read','sudo dmesg --level=emerg',kind='kernel-probe')
    run('lsmod read','sudo lsmod',kind='kernel-probe')
    run('modprobe dry run','sudo modprobe --dry-run --verbose loop',kind='kernel-probe')
    run('systemctl state','sudo systemctl is-system-running',kind='service-probe')
    run('journalctl read','sudo journalctl --no-pager -n 1',kind='service-probe')
    run('strace subprocess','sudo strace -o '+q+'/strace.log true',kind='kernel-probe')
    run('nice lower priority','sudo nice -n 5 true')
    run('nice higher priority','sudo nice -n -5 true',kind='kernel-probe')
    run('renice higher priority','sudo sh -c "renice -n -5 -p $$"',kind='kernel-probe')
    run('ionice idle','sudo ionice -c 3 true',kind='kernel-probe')
    run('chrt realtime','sudo chrt -r 1 true',kind='kernel-probe')
    cpu=min(os.sched_getaffinity(0))
    run('taskset affinity',f'sudo taskset -c {cpu} true')
    run('prlimit child resource limit',"sudo prlimit --nofile=128:128 -- sh -c 'ulimit -n'",expected='128')
    # Account operations use --prefix, not the actual container account database.
    etc=root/'accounts/etc';etc.mkdir(parents=True)
    for name,contents in [('passwd','root:x:0:0:root:/root:/bin/sh\n'),('group','root:x:0:\n'),('shadow','root:!:20000:0:99999:7:::\n'),('gshadow','root:!::\n'),('login.defs','UID_MIN 1000\nUID_MAX 60000\nGID_MIN 1000\nGID_MAX 60000\n')]:
        (etc/name).write_text(contents)
    (root/'accounts/home').mkdir()
    prefix=shlex.quote(str(root/'accounts'))
    run('group and user lifecycle (isolated)',f'''sudo sh -ec 'groupadd -P {prefix} -g 23456 dextopprobe;
        useradd -P {prefix} -M -u 23456 -g 23456 -s /bin/sh dextopprobe;
        grep dextopprobe {prefix}/etc/passwd;
        usermod -P {prefix} -c probechanged dextopprobe;
        userdel -P {prefix} dextopprobe; groupdel -P {prefix} dextopprobe;
        echo account-lifecycle-ok' ''',kind='account-probe',expected='account-lifecycle-ok')
    run('passwd isolated database',f'printf "root:!\\n" | sudo chpasswd -P {prefix} -e',kind='account-probe',timeout=25)
    # Test each account tool independently against a disposable prefix.
    for name,command in [
        ('groupadd', 'groupadd -g 23456 dextopprobe'),
        ('groupmod', 'groupmod -n renamedprobe dextopprobe'),
        ('groupdel', 'groupdel dextopprobe'),
        ('useradd', 'useradd -M -u 23457 -g 0 anotherprobe'),
        ('usermod', 'usermod -c changed dextopprobe'),
        ('userdel', 'userdel dextopprobe'),
        ('chage', 'chage -l dextopprobe'),
    ]:
        account=root/('account-'+name);(account/'etc').mkdir(parents=True)
        for file,content in [
            ('passwd','root:x:0:0:root:/root:/bin/sh\n'+'dextopprobe:x:23456:23456:Probe:/home/probe:/bin/sh\n'),
            ('group','root:x:0:\ndextopprobe:x:23456:\n'),
            ('shadow','root:!:20000:0:99999:7:::\ndextopprobe:!:20000:0:99999:7:::\n'),
            ('gshadow','root:!::\ndextopprobe:!::\n'),
            ('login.defs','UID_MIN 1000\nUID_MAX 60000\nGID_MIN 1000\nGID_MAX 60000\n')]:
            (account/'etc'/file).write_text(content)
        words=command.split(' ',1)
        run(name+' (isolated)', 'sudo '+words[0]+' -P '+shlex.quote(str(account))+' '+words[1],kind='account-probe',timeout=25)
    run('passwd status','sudo passwd -S root',kind='account-probe')
    run('gpasswd help','sudo gpasswd --help',kind='capability-discovery')
    run('runuser root',"sudo runuser -u root -- id -u",kind='identity',expected='0')
    run('runuser nobody',"sudo runuser -u nobody -- id -u",kind='identity',expected='65534')
    run('visudo syntax check','sudo visudo -c',kind='account-probe')
    run('sysctl write dry-run','sudo sysctl --dry-run -w kernel.hostname=dextop-probe',kind='kernel-probe')
    run('dpkg audit after tools installation','sudo dpkg --audit')
print(json.dumps({'summary':{key:sum(r['status']==key for r in results) for key in ['ok','rejected','hung','wrong-result']},'total':len(results)}),flush=True)
