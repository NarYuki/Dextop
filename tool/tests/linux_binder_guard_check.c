/* Build with NDK aarch64-linux-android30-clang -Wall -Wextra -Werror
 * -Ithird_party/proot/src. No binder transactions are sent (zero-size buffers).
 * Run `check` for kernel inheritance tests, `check guest` inside PRoot. */
#define _GNU_SOURCE
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/syscall.h>
#include <sys/ptrace.h>
#include <sys/uio.h>
#include <sys/mman.h>
#include <sys/wait.h>
#include <unistd.h>
#include "syscall/binder-guard.h"

static uint32_t evaluate(struct seccomp_data *data)
{
    uint32_t a = 0;
    for (size_t pc = 0; pc < sizeof(dextop_binder_filter)/sizeof(*dextop_binder_filter); pc++) {
        const struct sock_filter *s = &dextop_binder_filter[pc];
        switch (s->code) {
        case BPF_LD | BPF_W | BPF_ABS: memcpy(&a, (char *)data + s->k, 4); break;
        case BPF_ALU | BPF_AND | BPF_K: a &= s->k; break;
        case BPF_JMP | BPF_JEQ | BPF_K: pc += a == s->k ? s->jt : s->jf; break;
        case BPF_RET | BPF_K: return s->k;
        default: abort();
        }
    }
    abort();
}

static int filter_matrix(void)
{
    const uint32_t arches[] = { AUDIT_ARCH_AARCH64, AUDIT_ARCH_ARM, AUDIT_ARCH_X86_64, 0 };
    const int numbers[] = { 29, 54, 0, 172, -1 };
    const uint64_t ips[] = { 0, 0xffff00000ULL, 0xffff00008ULL, UINT64_MAX };
    unsigned count = 0;
    for (unsigned ar = 0; ar < 4; ar++)
    for (unsigned n = 0; n < 5; n++)
    for (unsigned ip = 0; ip < 4; ip++)
    for (unsigned type = 0; type < 256; type++)
    for (unsigned variant = 0; variant < 4; variant++) {
        struct seccomp_data d = { .arch = arches[ar], .nr = numbers[n], .instruction_pointer = ips[ip] };
        d.args[1] = ((uint64_t)type << 8) | (variant ? 0xc0300001U : 0x40040007U);
        if (variant & 1) d.args[1] |= 0xabcdef1200000000ULL;
        if (variant & 2) d.args[1] ^= 0x00280000;
        uint32_t expected = ar >= 2 ? SECCOMP_RET_KILL_PROCESS :
            (numbers[n] == (ar == 0 ? 29 : 54) && type == 'b' ? SECCOMP_RET_ERRNO | EPERM : SECCOMP_RET_ALLOW);
        if (evaluate(&d) != expected) { fprintf(stderr, "matrix failed ar=%u n=%u type=%u\n", ar, n, type); return 1; }
        count++;
    }
    printf("PASS %u production BPF cases (ABIs, high bits, fast-path IPs)\n", count);
    return 0;
}

static int check_denied(void)
{
    uint64_t empty[6] = {0};
    const unsigned long commands[] = { 0xc0306201UL, 0xc0186201UL, 0x40046207UL,
        0x40046210UL, 0x6200UL, 0xffffffffc0306201UL };
    const char *paths[] = { "/dev/binder", "/proc/self/root/dev/binder", "/dev/hwbinder", "/dev/vndbinder", "/dev/null" };
    for (unsigned p = 0; p < sizeof(paths)/sizeof(*paths); p++) {
        int fd = open(paths[p], O_RDWR | O_CLOEXEC);
        if (fd < 0) { printf("SKIP open %s: %s\n", paths[p], strerror(errno)); continue; }
        for (unsigned c = 0; c < sizeof(commands)/sizeof(*commands); c++) {
            errno = 0;
            /* State-changing commands use an invalid fd: if a regression lets
             * one through, it still cannot change a real binder context. */
            long result = syscall(__NR_ioctl, c < 2 ? fd : -1, commands[c], empty);
            if (result != -1 || errno != EPERM) {
                fprintf(stderr, "FAIL %s request=%lx result=%ld errno=%d\n", paths[p], commands[c], result, errno);
                close(fd); return 1;
            }
        }
        close(fd);
        printf("PASS binder family rejected via %s\n", paths[p]);
    }
    errno = 0;
    if (syscall(__NR_ioctl, -1, 0x5401, empty) != -1 || errno != EBADF) return 2;
    puts("PASS unrelated ioctl reaches kernel");
#if defined(__aarch64__)
    /* Attack the address exempted by the optional unprotected fast path.
     * The mandatory guard must still reject an ioctl issued from that page. */
    void *page = mmap((void *)0xffff00000ULL, 4096, PROT_READ | PROT_WRITE,
        MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED_NOREPLACE, -1, 0);
    if (page == MAP_FAILED) { perror("test trampoline mapping"); return 3; }
    const uint32_t code[] = { 0xaa0003e8, 0xaa0103e0, 0xaa0203e1,
        0xaa0303e2, 0xaa0403e3, 0xaa0503e4, 0xaa0603e5, 0xd4000001, 0xd65f03c0 };
    memcpy(page, code, sizeof(code));
    __builtin___clear_cache(page, (char *)page + sizeof(code));
    if (mprotect(page, 4096, PROT_READ | PROT_EXEC)) return 4;
    long (*stub)(long, long, long, long, long, long, long) = page;
    if (stub(__NR_ioctl, -1, 0xc0306201UL, (long)empty, 0, 0, 0) != -EPERM) return 5;
    if (stub(__NR_ioctl, -1, 0x5401, (long)empty, 0, 0, 0) != -EBADF) return 6;
    munmap(page, 4096);
    puts("PASS ioctl from fast-path trampoline rejected");
#endif
    return 0;
}

static int inspect_tracer(pid_t pid)
{
    const char *names[] = { "mem", "maps", "environ", "root" };
    char path[128], byte;
    for (unsigned i = 0; i < sizeof(names)/sizeof(*names); i++) {
        snprintf(path, sizeof(path), "/proc/%d/%s", pid, names[i]);
        errno = 0;
        int fd = open(path, O_RDONLY);
        if (fd >= 0) { close(fd); fprintf(stderr, "FAIL accessible %s\n", path); return 1; }
        if (errno != EPERM && errno != EACCES) { perror(path); return 2; }
        printf("PASS external tracer %s denied\n", names[i]);
    }
    struct iovec local = { &byte, 1 }, remote = { (void *)1, 1 };
    errno = 0;
    if (process_vm_readv(pid, &local, 1, &remote, 1, 0) != -1 || errno != EPERM) return 3;
    errno = 0;
    if (process_vm_writev(pid, &local, 1, &remote, 1, 0) != -1 || errno != EPERM) return 4;
    errno = 0;
    /* SEIZE does not stop the target, so an unexpected success is recoverable. */
    if (ptrace(PTRACE_SEIZE, pid, NULL, NULL) != -1 || errno != EPERM) return 5;
    puts("PASS external process_vm_readv/writev and PTRACE_SEIZE denied");
    return 0;
}

int main(int argc, char **argv)
{
    setvbuf(stdout, NULL, _IONBF, 0);
    if (argc == 3 && !strcmp(argv[1], "inspect")) return inspect_tracer(atoi(argv[2]));
    if (argc == 2 && !strcmp(argv[1], "guest")) return check_denied();
    if (argc == 2 && !strcmp(argv[1], "exec")) return check_denied();
    if (filter_matrix()) return 1;
    int fd = open("/dev/binder", O_RDWR);
    uint64_t empty[6] = {0};
    if (fd < 0 || ioctl(fd, 0xc0306201UL, empty)) { perror("unfiltered empty binder control"); return 2; }
    puts("PASS unfiltered empty BINDER_WRITE_READ succeeds");
    if (dextop_install_binder_guard()) { perror("guard"); return 3; }
    errno = 0;
    if (ioctl(fd, 0xc0306201UL, empty) != -1 || errno != EPERM) return 4;
    close(fd);
    puts("PASS inherited/open-before-filter fd rejected");
    if (check_denied()) return 5;
    pid_t pid = fork();
    if (pid < 0) return 6;
    if (!pid) { execl(argv[0], argv[0], "exec", NULL); _exit(7); }
    int status;
    if (waitpid(pid, &status, 0) != pid || !WIFEXITED(status) || WEXITSTATUS(status)) return 8;
    puts("PASS fork + exec inherit mandatory guard");
    return 0;
}
