"""Run inside Dextop Linux with an enabled FIDO key exposed as hidraw0.

Checks device metadata and ordinary descriptors without changing credentials.
"""
import os,stat,socket,tempfile,json,errno
checks=[]
def check(name,condition):
 assert condition,name
 checks.append(name)
fd=os.open('/dev/hidraw0',os.O_RDWR)
try:
 for label,info in [('stat',os.stat('/dev/hidraw0')),('lstat',os.lstat('/dev/hidraw0')),('fstat',os.fstat(fd))]:
  check(label+'_character',stat.S_ISCHR(info.st_mode));check(label+'_identity',os.major(info.st_rdev)==241 and os.minor(info.st_rdev)==0)
finally:os.close(fd)
try:os.fstat(fd);raise AssertionError('closed fd accepted')
except OSError as e:check('closed_fd_EBADF',e.errno==errno.EBADF)
with tempfile.TemporaryFile() as f:
 f.write(b'dextop');f.flush();check('regular_file_preserved',stat.S_ISREG(os.fstat(f.fileno()).st_mode) and os.fstat(f.fileno()).st_size==6)
a,b=socket.socketpair()
try:check('ordinary_socket_preserved',stat.S_ISSOCK(os.fstat(a.fileno()).st_mode))
finally:a.close();b.close()
r,w=os.pipe()
try:check('ordinary_pipe_preserved',stat.S_ISFIFO(os.fstat(r).st_mode))
finally:os.close(r);os.close(w)
try:os.stat('/tmp/dextop-intentionally-missing-9501');raise AssertionError('invented missing file')
except FileNotFoundError:checks.append('missing_path_ENOENT')
print(json.dumps(dict(passed=len(checks),checks=checks)))
