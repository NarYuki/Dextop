#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#ifdef DEXTOP_DENY_LINKS
int link(const char *a, const char *b) { errno = EPERM; return -1; }
int linkat(int a, const char *b, int c, const char *d, int f) { errno = EACCES; return -1; }
#elif defined(DEXTOP_PROC_TEST_STUBS)
int dextop_pad_open(const char *p, int f) { return -2; }
int dextop_serial_open(const char *p, int f) { return -2; }
int dextop_camera_open(const char *p, int f) { return -2; }
int dextop_usb_open(const char *p, int f) { return -1; }
int dextop_folder_open(int d, const char *p, int f, int m) { return -2; }
int dextop_folder_fopen(const char *p, const char *m, FILE **f) { return 0; }
void dextop_folder_forget(int fd) {}
#else
#define CHECK(x) do { if (!(x)) { fprintf(stderr, "FAIL line=%d errno=%d\n", __LINE__, errno); exit(1); } } while (0)
int main(void) {
    char dir[] = "/tmp/dextop-link-test-XXXXXX";
    CHECK(mkdtemp(dir));
    int d = open(dir, O_RDONLY|O_DIRECTORY); CHECK(d >= 0);
    CHECK(fchdir(d) == 0);
    int fd = open("source", O_WRONLY|O_CREAT|O_EXCL, 0600); CHECK(fd >= 0);
    CHECK(write(fd, "secret-test", 11) == 11); CHECK(close(fd) == 0);
    const char *opt = getenv("DEXTOP_HARDLINK_COPY"); int copy = opt && !strcmp(opt, "1");
    errno = 0; int r = link("source", "source.temp-1");
    if (!copy) {
        CHECK(r == -1 && errno == EPERM);
        CHECK(faccessat(d, "source.temp-1", F_OK, 0) == -1 && errno == ENOENT);
        /* The secure store's own fallback can create the first temporary name. */
        fd = open("source.temp-1", O_WRONLY|O_CREAT|O_EXCL, 0600); CHECK(fd >= 0); close(fd);
    } else {
        CHECK(r == 0);
        char buf[12] = {0}; fd = open("source.temp-1", O_RDONLY); CHECK(fd >= 0);
        CHECK(read(fd, buf, 11) == 11 && !strcmp(buf, "secret-test")); close(fd);
    }
    errno = 0; r = linkat(d, "source", d, "source.temp-2", 0);
    if (!copy) {
        CHECK(r == -1 && errno == EACCES);
        CHECK(faccessat(d, "source.temp-2", F_OK, 0) == -1 && errno == ENOENT);
    } else CHECK(r == 0);
    CHECK(unlinkat(d, "source.temp-1", 0) == 0);
    if (copy) CHECK(unlinkat(d, "source.temp-2", 0) == 0);
    CHECK(unlinkat(d, "source", 0) == 0);
    CHECK(fchdir(open("/", O_RDONLY|O_DIRECTORY)) == 0); close(d); CHECK(rmdir(dir) == 0);
    puts(copy ? "PACKAGE_COPY_OPT_IN_PASS" : "TRANSACTIONAL_STORE_FALLBACK_PASS");
    return 0;
}
#endif
