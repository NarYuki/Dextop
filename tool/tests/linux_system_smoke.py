#!/usr/bin/env python3
"""Read-only system/device survey for a Dextop Linux guest; emits JSON evidence."""
import ctypes
import json
import os
from pathlib import Path
import platform
import shutil
import socket
import subprocess
import tempfile
import time

results = []
def record(name, function):
    try:
        value = function()
        results.append(dict(name=name, status='pass', detail=value))
    except PermissionError as error:
        results.append(dict(name=name, status='restricted', detail=str(error)))
    except FileNotFoundError as error:
        results.append(dict(name=name, status='missing', detail=str(error)))
    except Exception as error:
        results.append(dict(name=name, status='error', detail=str(error)))

def read(path, binary=False):
    data = Path(path).read_bytes()
    return {'bytes':len(data), 'sample':repr(data[:400]) if binary else data[:1200].decode(errors='replace')}

def command(args):
    if not shutil.which(args[0]): raise FileNotFoundError(args[0])
    value = subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=12)
    if value.returncode:
        text = value.stdout[:2500]
        if 'Permission denied' in text or 'Operation not permitted' in text or 'not permitted' in text:
            raise PermissionError(text)
        raise RuntimeError(f'exit={value.returncode}: {text}')
    return value.stdout[:4000]

for path in ['/proc/version','/proc/uptime','/proc/cpuinfo','/proc/meminfo','/proc/stat','/proc/loadavg',
             '/proc/mounts','/proc/self/mountinfo','/proc/swaps','/proc/filesystems','/proc/modules',
             '/proc/interrupts','/proc/sys/kernel/hostname','/proc/sys/kernel/osrelease','/proc/sys/kernel/random/entropy_avail',
             '/proc/net/dev','/proc/net/route','/proc/net/tcp','/sys/fs/cgroup/cgroup.controllers',
             '/sys/firmware/devicetree/base/model','/sys/firmware/devicetree/base/compatible','/proc/device-tree/model']:
    record(path, lambda path=path: read(path, 'devicetree' in path or 'device-tree' in path))
for path in ['/sys/class','/sys/bus','/sys/devices','/sys/block','/dev','/dev/input','/dev/bus/usb','/sys/class/thermal']:
    record('list:'+path, lambda path=path: sorted(os.listdir(path))[:100])
for interface in sorted(Path('/sys/class/net').iterdir()):
    for name in ['rx_bytes','tx_bytes','rx_packets','tx_packets']:
        record(f'network:{interface.name}:{name}', lambda p=interface/'statistics'/name: int(p.read_text()))
for supply in sorted(Path('/sys/class/power_supply').iterdir()):
    record('power:'+supply.name, lambda p=supply: {v.name:v.read_text().strip() for v in p.iterdir() if v.is_file()})
for args in [['uname','-a'],['lscpu'],['free','-m'],['df','-h','/'],['findmnt','-n','/'],['lsblk'],
             ['ip','-brief','link'],['ip','route'],['ss','-lnt'],['lsusb'],['lspci'],['udevadm','info','--path=/sys/class/net/lo'],
             ['udevadm','settle','--timeout=2'],['dtc','-I','fs','-O','dts','/sys/firmware/devicetree/base'],
             ['dbus-run-session','--','dbus-send','--session','--print-reply','--dest=org.freedesktop.DBus','/org/freedesktop/DBus','org.freedesktop.DBus.ListNames']]:
    record('command:'+' '.join(args), lambda args=args: command(args))
def psutil_check():
    import psutil
    return dict(cpu=psutil.cpu_count(), memory=psutil.virtual_memory().total,
                processes=len(psutil.pids()), disk=psutil.disk_usage('/').total,
                network=list(psutil.net_io_counters(pernic=True)))
record('python:psutil', psutil_check)
def kernel_operations():
    import fcntl, mmap, select
    with tempfile.TemporaryDirectory(prefix='dextop-system-') as d:
        p=Path(d)/'lock'
        with p.open('w') as file:
            fcntl.flock(file,fcntl.LOCK_EX|fcntl.LOCK_NB)
        fd=os.eventfd(0,os.EFD_NONBLOCK)
        try:
            os.eventfd_write(fd,7)
            with select.epoll() as epoll:
                epoll.register(fd, select.EPOLLIN)
                assert epoll.poll(.2)
            assert os.eventfd_read(fd)==7
        finally: os.close(fd)
        with mmap.mmap(-1,4096) as memory: memory[:4]=b'test'; assert memory[:4]==b'test'
    return 'flock/eventfd/epoll/mmap passed'
record('kernel:userspace-primitives', kernel_operations)
def fd_and_shared_memory():
    import array, fcntl, mmap
    from multiprocessing import shared_memory
    fd=os.memfd_create('dextop-system', os.MFD_ALLOW_SEALING)
    try:
        os.write(fd,b'shared-data')
        fcntl.fcntl(fd,fcntl.F_ADD_SEALS,fcntl.F_SEAL_SHRINK)
        left,right=socket.socketpair()
        try:
            left.sendmsg([b'x'],[(socket.SOL_SOCKET,socket.SCM_RIGHTS,array.array('i',[fd]))])
            _,ancillary,_,_=right.recvmsg(1,socket.CMSG_SPACE(4))
            received=array.array('i');received.frombytes(ancillary[0][2][:4]);copy=received[0]
            try:
                os.lseek(copy,0,os.SEEK_SET);assert os.read(copy,11)==b'shared-data'
            finally:os.close(copy)
        finally:left.close();right.close()
    finally:os.close(fd)
    memory=shared_memory.SharedMemory(create=True,size=64)
    try:
        memory.buf[:4]=b'test'
        other=shared_memory.SharedMemory(name=memory.name)
        try:assert bytes(other.buf[:4])==b'test'
        finally:other.close()
    finally:memory.close();memory.unlink()
    return 'memfd, seals, SCM_RIGHTS, POSIX shared memory passed'
record('kernel:fd-sharing',fd_and_shared_memory)
def inotify():
    libc=ctypes.CDLL(None,use_errno=True)
    fd=libc.inotify_init1(os.O_NONBLOCK|os.O_CLOEXEC)
    if fd<0:raise OSError(ctypes.get_errno(),'inotify_init1')
    try:
        with tempfile.TemporaryDirectory() as directory:
            if libc.inotify_add_watch(fd,os.fsencode(directory),0x100)<0:raise OSError(ctypes.get_errno(),'inotify_add_watch')
            Path(directory,'event').write_text('created')
            import select
            assert select.select([fd],[],[],2)[0]
            assert b'event' in os.read(fd,4096)
    finally:os.close(fd)
    return 'file creation event received'
record('kernel:inotify',inotify)
def dtc_roundtrip():
    with tempfile.TemporaryDirectory() as directory:
        root=Path(directory);source=root/'test.dts';binary=root/'test.dtb'
        source.write_text('/dts-v1/; / { model = "Dextop test fixture"; compatible = "dextop,test"; };')
        subprocess.run(['dtc','-I','dts','-O','dtb','-o',str(binary),str(source)],check=True,capture_output=True)
        assert binary.read_bytes()[:4]==bytes.fromhex('d00dfeed')
        text=subprocess.check_output(['dtc','-I','dtb','-O','dts',str(binary)],text=True)
        assert 'Dextop test fixture' in text
    return 'DTS/DTB conversion works; this is a synthetic fixture, not the phone hardware tree'
record('device-tree:compiler-roundtrip',dtc_roundtrip)
for args in [['unshare','--user','--map-root-user','true'],['unshare','--mount','--propagation','unchanged','true']]:
    record('namespace:'+' '.join(args),lambda args=args:command(args))
for path in ['/sys/class/drm','/sys/bus/usb/devices','/sys/bus/pci/devices','/dev/dri','/sys/fs/cgroup']:
    record('list:'+path,lambda path=path:sorted(os.listdir(path))[:40])
record('/proc/self/status',lambda:read('/proc/self/status'))
record('/proc/cgroups',lambda:read('/proc/cgroups'))
output=Path('/tmp/dextop-system-survey-'+str(os.getpid())+'.json')
output.write_text(json.dumps(dict(platform=platform.platform(), uid=os.getuid(), results=results),ensure_ascii=False,indent=2))
for row in results: print(row['status'].upper(), row['name'], str(row['detail'])[:120].replace('\n',' '))
print('RESULTS='+str(output))
