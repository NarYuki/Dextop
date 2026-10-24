"""Real-node Deck controller access regression; run in the Linux guest.

Usage: python3 deck_controller_read_access.py /path/to/libdextop_padgrab.so /dev/input/eventN
Choose Steam's virtual controller while Steam is running. No input is injected.
"""
import errno, fcntl, json, os, subprocess, sys

if sys.argv[1] == '--child':
    try:
        fd = os.open(sys.argv[2], os.O_RDWR | os.O_NONBLOCK | os.O_CLOEXEC)
        result = {'mode': fcntl.fcntl(fd, fcntl.F_GETFL) & os.O_ACCMODE}
        os.close(fd)
    except OSError as error:
        result = {'errno': error.errno}
    print(json.dumps(result))
else:
    library, node = sys.argv[1:]
    env = dict(os.environ)
    env['LD_PRELOAD'] = library + ':' + env.get('LD_PRELOAD', '')
    def probe(deck, path):
        child = dict(env)
        child.pop('DEXTOP_STEAM_INSIDE', None)
        if deck:
            child['DEXTOP_STEAM_INSIDE'] = '1'
        return json.loads(subprocess.check_output([sys.executable, __file__, '--child', path], env=child, text=True))
    outside = probe(False, node)
    assert outside.get('errno') in (errno.EACCES, errno.EPERM), outside
    assert probe(True, node) == {'mode': os.O_RDONLY}
    # A denied non-controller must retain its permission error in Deck Mode.
    assert probe(True, '/dev/input/event0').get('errno') in (errno.EACCES, errno.EPERM)
    print('PASS Deck controller read fallback; ordinary Linux and non-controller access unchanged')
