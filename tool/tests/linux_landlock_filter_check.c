/* Interpret the production BPF prologues offline: no syscalls bypass PRoot. */
#define _GNU_SOURCE
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "syscall/landlock-filter.h"
static uint32_t run(const struct sock_filter *prog, unsigned len, struct seccomp_data *data) {
  uint32_t a = 0;
  for (unsigned pc = 0; pc < len; pc++) {
    const struct sock_filter *f = &prog[pc];
    switch (f->code) {
      case BPF_LD | BPF_W | BPF_ABS: memcpy(&a, (char *)data + f->k, 4); break;
      case BPF_JMP | BPF_JEQ | BPF_K: pc += a == f->k ? f->jt : f->jf; break;
      case BPF_JMP | BPF_JGE | BPF_K: pc += a >= f->k ? f->jt : f->jf; break;
      case BPF_JMP | BPF_JSET | BPF_K: pc += (a & f->k) ? f->jt : f->jf; break;
      case BPF_RET | BPF_K: return f->k;
      default: return 0;
    }
    if (pc + 1 > len) return 0;   /* a jump past the end would be a broken program */
  }
  return SECCOMP_RET_TRACE;       /* fall through to PRoot's ordinary (tracing) filter */
}
/* Syscalls the broad (unprotected) prologue may exempt: Landlock-restricted file
 * operations and metadata-only lookups. Nothing else, ever. */
static int broad_allowed(int nr) {
  static const int ok[] = {56, 34, 35, 36, 37, 38, 276, 45, 79, 291, 48, 78, 43};
  for (unsigned i = 0; i < sizeof ok / sizeof *ok; i++) if (ok[i] == nr) return 1;
  return 0;
}
int main(void) {
  const uint64_t ips[] = {0xffff00000ULL - 1, 0xffff00000ULL, 0xffff00000ULL + 4095,
    0xffff00000ULL + 4096, 0xefff00020ULL, 0x10fff00020ULL, 0};
  const uint32_t archs[] = {0xc00000b7, 0x40000028, 0xc000003e, 0};
  const uint32_t flags[] = {O_RDONLY, O_WRONLY | O_TRUNC, O_CREAT | O_RDWR, O_PATH, O_PATH | O_CLOEXEC};
  const unsigned open_len = sizeof landlock_open_filter / sizeof *landlock_open_filter;
  const unsigned broad_len = sizeof landlock_broad_filter / sizeof *landlock_broad_filter;
  unsigned tests = 0;
  if (broad_len != LANDLOCK_BROAD_OUT) { printf("FAIL broad filter length %u\n", broad_len); return 3; }
  for (unsigned i = 0; i < sizeof ips / sizeof *ips; i++)
  for (unsigned j = 0; j < sizeof archs / sizeof *archs; j++)
  for (unsigned k = 0; k < sizeof flags / sizeof *flags; k++)
  for (int nr = 0; nr < 500; nr++) {
    struct seccomp_data d = {.arch = archs[j], .instruction_pointer = ips[i], .nr = nr};
    d.args[0] = 0x44584c4c; d.args[2] = flags[k];
    int address = j == 0 && (i == 1 || i == 2);
    uint32_t want = address && nr == 167 ? SECCOMP_RET_ERRNO | 117 :
      address && nr == 56 && !(flags[k] & O_PATH) ? SECCOMP_RET_ALLOW : SECCOMP_RET_TRACE;
    if (run(landlock_open_filter, open_len, &d) != want) { printf("FAIL protected ip=%llx arch=%x nr=%d flags=%x\n", (unsigned long long)ips[i], archs[j], nr, flags[k]); return 1; }
    want = address && nr == 167 ? SECCOMP_RET_ERRNO | 117 :
      address && broad_allowed(nr) ? SECCOMP_RET_ALLOW : SECCOMP_RET_TRACE;
    if (run(landlock_broad_filter, broad_len, &d) != want) { printf("FAIL broad ip=%llx arch=%x nr=%d flags=%x\n", (unsigned long long)ips[i], archs[j], nr, flags[k]); return 1; }
    tests += 2;
  }
  struct seccomp_data regular = {.arch = 0xc00000b7, .instruction_pointer = 0xffff00020ULL, .nr = 167};
  if (run(landlock_open_filter, open_len, &regular) != SECCOMP_RET_TRACE) return 2;
  if (run(landlock_broad_filter, broad_len, &regular) != SECCOMP_RET_TRACE) return 2;
  /* Escape-relevant syscalls from the trampoline must never be exempted by the broad prologue. */
  const int never[] = {203, 200, 242, 202, 221, 281, 117, 129, 131, 130, 167, 53, 54, 88, 27, 49, 50, 40, 39, 29, 425, 426};
  for (unsigned i = 0; i < sizeof never / sizeof *never; i++) {
    struct seccomp_data d = {.arch = 0xc00000b7, .instruction_pointer = 0xffff0001cULL, .nr = never[i]};
    if (run(landlock_broad_filter, broad_len, &d) == SECCOMP_RET_ALLOW) { printf("FAIL broad exempts %d\n", never[i]); return 4; }
  }
  printf("PASS %u filter cases (protected and broad), ordinary prctl fallthrough, %zu never-exempt syscalls\n",
    tests, sizeof never / sizeof *never);
  return 0;
}
