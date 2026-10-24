#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/futex.h>
#include <pthread.h>
#include <signal.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>

#ifndef SYS_futex_waitv
#define SYS_futex_waitv 449
#endif

#define FS_SLOTS 2048u
#define FS_WORDS (FS_SLOTS / 64u)
#define FS_BUCKETS 4096u
#define FS_KEYS 128u
#define FS_NOKEY 0xffffffffu
#define FS_SIZE32 2u
#define FS_PRIVATE 128u
#define FS_MAGIC 0x434e595346444444ull
#define FS_SWEEP_NS 1000000000ull
#define FS_POLL_NS 500000l
#define FS_NAP_MIN 10000000ull
#define FS_NAP_MAX 1000000000ull
#define FS_HEAD_SIZE 4096ul

struct fs_waitv {
  uint64_t val;
  uint64_t uaddr;
  uint32_t flags;
  uint32_t reserved;
};

struct fs_bucket {
  uint64_t bits[FS_WORDS];
};

struct fs_slot {
  uint32_t futex;
  uint32_t count;
  uint64_t owner;
  uint32_t keys[FS_KEYS];
};

struct fs_head {
  uint64_t magic;
  uint32_t sweeper;
  uint32_t pad;
  uint64_t sweep_at;
  uint64_t used[FS_WORDS];
};

#define FS_BUCKET_OFF FS_HEAD_SIZE
#define FS_SLOT_OFF (FS_BUCKET_OFF + (size_t)FS_BUCKETS * sizeof(struct fs_bucket))
#define FS_REGION (FS_SLOT_OFF + (size_t)FS_SLOTS * sizeof(struct fs_slot))

_Static_assert(sizeof(struct fs_head) <= FS_HEAD_SIZE, "fsync registry head");

struct fs_range {
  uintptr_t lo;
  uintptr_t hi;
  uint64_t off;
};

struct fs_map {
  uint32_t n;
  struct fs_range r[];
};

static int fs_state = -1;
static char *fs_reg;
static int fs_fd = -1;
static struct fs_map *fs_cur;
static uint32_t fs_pid;
static pthread_mutex_t fs_mx = PTHREAD_MUTEX_INITIALIZER;
static pthread_key_t fs_key;
static pthread_once_t fs_once = PTHREAD_ONCE_INIT;
static __thread int32_t fs_tslot = -1;
static __thread int fs_busy;
static __thread uint32_t fs_tid;
static __thread uint32_t fs_nap_key;
static __thread uint32_t fs_nap_n;
static __thread uint64_t fs_nap_ns;
static __thread sigset_t fs_fork_mask;

static long (*fs_real_syscall)(long, ...);
static int (*fs_real_shm_open)(const char *, int, mode_t);
static int (*fs_real_shm_unlink)(const char *);
static void *(*fs_real_mmap)(void *, size_t, int, int, int, off_t);
static void *(*fs_real_mmap64)(void *, size_t, int, int, int, off64_t);
static int (*fs_real_munmap)(void *, size_t);
__attribute__((visibility("hidden"))) int bl_noexec_retry(int prot, int flags, int fd);

static int fs_on(void) {
  if (fs_state < 0) {
    const char *e = getenv("BL_FSYNC");
    fs_state = e && e[0] == '1';
  }
  return fs_state;
}

static long fs_sys(long n, long a, long b, long c, long d, long e, long f) {
  if (!fs_real_syscall) fs_real_syscall = (long (*)(long, ...))dlsym(RTLD_NEXT, "syscall");
  return fs_real_syscall(n, a, b, c, d, e, f);
}

static void *fs_anon(size_t size) {
  if (!fs_real_mmap) fs_real_mmap = (void *(*)(void *, size_t, int, int, int, off_t))dlsym(RTLD_NEXT, "mmap");
  return fs_real_mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
}

static uint32_t fs_self(void) {
  if (!fs_tid) fs_tid = (uint32_t)fs_sys(SYS_gettid, 0, 0, 0, 0, 0, 0);
  return fs_tid;
}

static uint32_t fs_getpid(void) {
  if (!fs_pid) fs_pid = (uint32_t)getpid();
  return fs_pid;
}

static uint64_t fs_now(void) {
  struct timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);
  return (uint64_t)ts.tv_sec * 1000000000ull + (uint64_t)ts.tv_nsec;
}

static int fs_dead(uint32_t pid, uint32_t tid) {
  int saved = errno, dead;
  dead = pid && fs_sys(SYS_tgkill, pid, tid, 0, 0, 0, 0) == -1 && errno == ESRCH;
  errno = saved;
  return dead;
}

static uint64_t fs_owner(void) {
  return (uint64_t)fs_getpid() << 32 | fs_self();
}

static void fs_block(sigset_t *old) {
  sigset_t all;
  sigfillset(&all);
  pthread_sigmask(SIG_SETMASK, &all, old);
}

static inline struct fs_head *fs_head(void) {
  return (struct fs_head *)fs_reg;
}

static inline struct fs_bucket *fs_bucket(uint32_t key) {
  return (struct fs_bucket *)(fs_reg + FS_BUCKET_OFF) + ((key >> 4) & (FS_BUCKETS - 1));
}

static inline struct fs_slot *fs_slot(uint32_t s) {
  return (struct fs_slot *)(fs_reg + FS_SLOT_OFF) + s;
}

static int fs_key_of(uint64_t addr, uint32_t *key) {
  struct fs_map *m = __atomic_load_n(&fs_cur, __ATOMIC_ACQUIRE);
  uint32_t lo = 0, hi;
  if (!m) return 0;
  hi = m->n;
  while (lo < hi) {
    uint32_t mid = (lo + hi) / 2;
    if (addr < m->r[mid].lo) {
      hi = mid;
    } else if (addr >= m->r[mid].hi) {
      lo = mid + 1;
    } else {
      uint64_t k = m->r[mid].off + (addr - m->r[mid].lo);
      if (k >= FS_NOKEY) return 0;
      *key = (uint32_t)k;
      return 1;
    }
  }
  return 0;
}

static int fs_overlaps(uintptr_t lo, uintptr_t hi) {
  struct fs_map *m = __atomic_load_n(&fs_cur, __ATOMIC_ACQUIRE);
  if (!m) return 0;
  for (uint32_t i = 0; i < m->n; i++)
    if (m->r[i].lo < hi && m->r[i].hi > lo) return 1;
  return 0;
}

static void fs_rebuild_locked(uintptr_t lo, uintptr_t hi, const struct fs_range *add) {
  struct fs_map *old, *m;
  uint32_t n, k = 0;
  int added = 0;
  old = fs_cur;
  n = old ? old->n : 0;
  m = fs_anon(sizeof(*m) + (n + 1) * sizeof(struct fs_range));
  if (m == MAP_FAILED) return;
  for (uint32_t i = 0; i < n; i++) {
    const struct fs_range *r = &old->r[i];
    if (r->lo < hi && r->hi > lo) continue;
    if (add && !added && add->lo < r->lo) {
      m->r[k++] = *add;
      added = 1;
    }
    m->r[k++] = *r;
  }
  if (add && !added) m->r[k++] = *add;
  m->n = k;
  __atomic_store_n(&fs_cur, m, __ATOMIC_RELEASE);
}

static void fs_rebuild(uintptr_t lo, uintptr_t hi, const struct fs_range *add) {
  sigset_t old;
  fs_block(&old);
  pthread_mutex_lock(&fs_mx);
  fs_rebuild_locked(lo, hi, add);
  pthread_mutex_unlock(&fs_mx);
  pthread_sigmask(SIG_SETMASK, &old, NULL);
}

static void fs_track(void *p, size_t len, int flags, int fd, off_t off) {
  uintptr_t lo = (uintptr_t)p, hi = lo + len;
  int saved = errno;
  if (fs_fd >= 0 && fd == fs_fd && (flags & MAP_SHARED) && off >= 0) {
    struct fs_range r = {lo, hi, (uint64_t)off};
    fs_rebuild(lo, hi, &r);
  } else if (fs_overlaps(lo, hi)) {
    fs_rebuild(lo, hi, NULL);
  }
  errno = saved;
}

static void fs_unregister(uint32_t s, const uint32_t *keys, uint32_t n) {
  uint64_t bit = 1ull << (s & 63);
  for (uint32_t i = 0; i < n; i++) {
    struct fs_bucket *b;
    if (keys[i] == FS_NOKEY) continue;
    b = fs_bucket(keys[i]);
    __atomic_and_fetch(&b->bits[s >> 6], ~bit, __ATOMIC_SEQ_CST);
  }
}

static void fs_release(uint32_t s, uint64_t owner) {
  struct fs_slot *sl = fs_slot(s);
  uint32_t n, keys[FS_KEYS];
  if (!owner || !__atomic_compare_exchange_n(&sl->owner, &owner, 0, 0, __ATOMIC_ACQ_REL, __ATOMIC_RELAXED)) return;
  n = __atomic_load_n(&sl->count, __ATOMIC_RELAXED);
  if (n > FS_KEYS) n = FS_KEYS;
  for (uint32_t i = 0; i < n; i++) keys[i] = __atomic_load_n(&sl->keys[i], __ATOMIC_RELAXED);
  fs_unregister(s, keys, n);
  __atomic_store_n(&sl->count, 0, __ATOMIC_RELAXED);
  __atomic_and_fetch(&fs_head()->used[s >> 6], ~(1ull << (s & 63)), __ATOMIC_RELEASE);
}

static void fs_sweep(int force) {
  struct fs_head *h = fs_head();
  uint64_t now = fs_now();
  uint32_t me = fs_getpid(), cur = 0;
  if (!force && now < __atomic_load_n(&h->sweep_at, __ATOMIC_RELAXED)) return;
  if (!__atomic_compare_exchange_n(&h->sweeper, &cur, me, 0, __ATOMIC_ACQUIRE, __ATOMIC_RELAXED)) {
    if (!fs_dead(cur, cur) || !__atomic_compare_exchange_n(&h->sweeper, &cur, me, 0, __ATOMIC_ACQUIRE, __ATOMIC_RELAXED))
      return;
  }
  __atomic_store_n(&h->sweep_at, now + FS_SWEEP_NS, __ATOMIC_RELAXED);
  for (uint32_t w = 0; w < FS_WORDS; w++) {
    uint64_t u = __atomic_load_n(&h->used[w], __ATOMIC_ACQUIRE);
    while (u) {
      uint32_t s = w * 64 + (uint32_t)__builtin_ctzll(u);
      struct fs_slot *sl = fs_slot(s);
      u &= u - 1;
      uint64_t owner = __atomic_load_n(&sl->owner, __ATOMIC_ACQUIRE);
      if (fs_dead((uint32_t)(owner >> 32), (uint32_t)owner)) fs_release(s, owner);
    }
  }
  __atomic_store_n(&h->sweeper, 0, __ATOMIC_RELEASE);
}

static int32_t fs_alloc(void) {
  struct fs_head *h = fs_head();
  fs_sweep(0);
  for (int pass = 0; pass < 2; pass++) {
    for (uint32_t w = 0; w < FS_WORDS; w++) {
      uint64_t u = __atomic_load_n(&h->used[w], __ATOMIC_RELAXED);
      while (~u) {
        uint32_t b = (uint32_t)__builtin_ctzll(~u);
        if (__atomic_compare_exchange_n(&h->used[w], &u, u | (1ull << b), 0, __ATOMIC_ACQ_REL, __ATOMIC_RELAXED)) {
          uint32_t s = w * 64 + b;
          struct fs_slot *sl = fs_slot(s);
          __atomic_store_n(&sl->count, 0, __ATOMIC_RELAXED);
          __atomic_store_n(&sl->owner, fs_owner(), __ATOMIC_RELEASE);
          return (int32_t)s;
        }
      }
    }
    fs_sweep(1);
  }
  return -1;
}

static void fs_thread_gone(void *p) {
  uint32_t s = (uint32_t)((uintptr_t)p - 1);
  if (fs_reg && s < FS_SLOTS && fs_tslot == (int32_t)s) {
    fs_tslot = -1;
    fs_busy = 0;
    fs_release(s, fs_owner());
  }
}

static void fs_key_init(void) {
  pthread_key_create(&fs_key, fs_thread_gone);
}

static void fs_fork_prepare(void) {
  fs_block(&fs_fork_mask);
  pthread_mutex_lock(&fs_mx);
}

static void fs_fork_parent(void) {
  pthread_mutex_unlock(&fs_mx);
  pthread_sigmask(SIG_SETMASK, &fs_fork_mask, NULL);
}

static void fs_child(void) {
  pthread_mutex_init(&fs_mx, NULL);
  pthread_sigmask(SIG_SETMASK, &fs_fork_mask, NULL);
  fs_pid = 0;
  fs_tid = 0;
  fs_busy = 0;
  if (fs_tslot >= 0) {
    fs_tslot = -1;
    pthread_setspecific(fs_key, NULL);
  }
}

__attribute__((constructor)) static void fs_init(void) {
  if (!fs_real_mmap) fs_real_mmap = (void *(*)(void *, size_t, int, int, int, off_t))dlsym(RTLD_NEXT, "mmap");
  if (!fs_real_mmap64) fs_real_mmap64 = (void *(*)(void *, size_t, int, int, int, off64_t))dlsym(RTLD_NEXT, "mmap64");
  if (!fs_real_munmap) fs_real_munmap = (int (*)(void *, size_t))dlsym(RTLD_NEXT, "munmap");
  if (fs_on()) pthread_atfork(fs_fork_prepare, fs_fork_parent, fs_child);
}

static int32_t fs_take(int *temp) {
  if (fs_busy) {
    *temp = 1;
    return fs_alloc();
  }
  *temp = 0;
  fs_busy = 1;
  __atomic_signal_fence(__ATOMIC_SEQ_CST);
  if (fs_tslot < 0) {
    int32_t s = fs_alloc();
    if (s < 0) {
      fs_busy = 0;
      return -1;
    }
    pthread_once(&fs_once, fs_key_init);
    pthread_setspecific(fs_key, (void *)(uintptr_t)(s + 1));
    fs_tslot = s;
  }
  return fs_tslot;
}

static void fs_give(int32_t s, int temp) {
  if (temp) {
    fs_release((uint32_t)s, fs_owner());
  } else {
    __atomic_signal_fence(__ATOMIC_SEQ_CST);
    fs_busy = 0;
  }
}

static int fs_changed(const struct fs_waitv *v, uint32_t n) {
  for (uint32_t i = 0; i < n; i++)
    if (__atomic_load_n((uint32_t *)(uintptr_t)v[i].uaddr, __ATOMIC_SEQ_CST) != (uint32_t)v[i].val) return (int)i;
  return -1;
}

static int fs_past(const struct timespec *ts, int clk) {
  struct timespec now;
  if (!ts) return 0;
  clock_gettime(clk, &now);
  return now.tv_sec > ts->tv_sec || (now.tv_sec == ts->tv_sec && now.tv_nsec >= ts->tv_nsec);
}

static uint64_t fs_clock_ns(int clk) {
  struct timespec now;
  clock_gettime(clk, &now);
  return (uint64_t)now.tv_sec * 1000000000ull + (uint64_t)now.tv_nsec;
}

static uint64_t fs_ts_ns(const struct timespec *ts) {
  return (uint64_t)ts->tv_sec * 1000000000ull + (uint64_t)ts->tv_nsec;
}

static long fs_poll(const struct fs_waitv *v, uint32_t n, const struct timespec *ts, int clk) {
  int i = fs_changed(v, n);
  if (i >= 0) {
    errno = EAGAIN;
    return -1;
  }
  for (;;) {
    struct timespec nap = {0, FS_POLL_NS};
    if (fs_past(ts, clk)) {
      errno = ETIMEDOUT;
      return -1;
    }
    if (nanosleep(&nap, NULL) == -1 && errno == EINTR) return -1;
    if ((i = fs_changed(v, n)) >= 0) return i;
  }
}

static long fs_waitv(const struct fs_waitv *v, uint32_t n, uint32_t flags, const struct timespec *ts, int clk) {
  uint32_t keys[FS_KEYS];
  struct fs_slot *sl;
  struct timespec nap_ts;
  const struct timespec *until = ts;
  int temp, err = 0, op, napping = 0, wclk = clk, saved = errno;
  int32_t s;
  uint64_t bit, nap;
  if (!v || !n || n > FS_KEYS || flags || (ts && clk != CLOCK_MONOTONIC && clk != CLOCK_REALTIME)) {
    errno = EINVAL;
    return -1;
  }
  for (uint32_t i = 0; i < n; i++) {
    if ((v[i].flags & ~FS_PRIVATE) != FS_SIZE32 || v[i].reserved || (v[i].uaddr & 3)) {
      errno = EINVAL;
      return -1;
    }
    if ((v[i].flags & FS_PRIVATE) || !fs_key_of(v[i].uaddr, &keys[i])) keys[i] = FS_NOKEY;
  }
  if (!fs_reg || (s = fs_take(&temp)) < 0) return fs_poll(v, n, ts, clk);
  sl = fs_slot((uint32_t)s);
  bit = 1ull << (s & 63);
  for (uint32_t i = 0; i < n; i++) __atomic_store_n(&sl->keys[i], keys[i], __ATOMIC_RELAXED);
  __atomic_store_n(&sl->count, n, __ATOMIC_RELAXED);
  __atomic_store_n(&sl->futex, 0, __ATOMIC_RELAXED);
  for (uint32_t i = 0; i < n; i++) {
    struct fs_bucket *b;
    if (keys[i] == FS_NOKEY) continue;
    b = fs_bucket(keys[i]);
    __atomic_or_fetch(&b->bits[s >> 6], bit, __ATOMIC_SEQ_CST);
  }
  __atomic_thread_fence(__ATOMIC_SEQ_CST);
  if (fs_changed(v, n) >= 0) err = EAGAIN;
  nap = fs_nap_ns && fs_nap_n == n && fs_nap_key == keys[0] ? fs_nap_ns * 2 : FS_NAP_MIN;
  if (nap > FS_NAP_MAX) nap = FS_NAP_MAX;
  if (!ts || fs_ts_ns(ts) > fs_clock_ns(clk) + nap) {
    uint64_t at = fs_clock_ns(CLOCK_MONOTONIC) + nap;
    nap_ts.tv_sec = (time_t)(at / 1000000000ull);
    nap_ts.tv_nsec = (long)(at % 1000000000ull);
    until = &nap_ts;
    wclk = CLOCK_MONOTONIC;
    napping = 1;
  }
  op = FUTEX_WAIT_BITSET | (until && wclk == CLOCK_REALTIME ? FUTEX_CLOCK_REALTIME : 0);
  while (!err && !__atomic_load_n(&sl->futex, __ATOMIC_ACQUIRE)) {
    if (fs_sys(SYS_futex, (long)&sl->futex, op, 0, (long)until, 0, FUTEX_BITSET_MATCH_ANY) == -1 && errno != EAGAIN
        && !__atomic_load_n(&sl->futex, __ATOMIC_ACQUIRE))
      err = errno;
  }
  fs_unregister((uint32_t)s, keys, n);
  if (err && __atomic_exchange_n(&sl->futex, 1, __ATOMIC_ACQ_REL)) err = 0;
  fs_give(s, temp);
  if (err == ETIMEDOUT && napping) {
    fs_nap_key = keys[0];
    fs_nap_n = n;
    fs_nap_ns = nap;
    errno = EINTR;
    return -1;
  }
  if (err != EINTR) fs_nap_ns = 0;
  if (err) {
    errno = err;
    return -1;
  }
  errno = saved;
  s = fs_changed(v, n);
  return s < 0 ? 0 : s;
}

static long fs_wake(uint32_t key, int n) {
  struct fs_bucket *b = fs_bucket(key);
  long woken = 0;
  __atomic_thread_fence(__ATOMIC_SEQ_CST);
  if (n <= 0) return 0;
  for (uint32_t w = 0; w < FS_WORDS; w++) {
    uint64_t bits = __atomic_load_n(&b->bits[w], __ATOMIC_SEQ_CST);
    while (bits) {
      uint32_t s = w * 64 + (uint32_t)__builtin_ctzll(bits), c;
      struct fs_slot *sl = fs_slot(s);
      bits &= bits - 1;
      c = __atomic_load_n(&sl->count, __ATOMIC_ACQUIRE);
      if (c > FS_KEYS) c = FS_KEYS;
      for (uint32_t i = 0; i < c; i++) {
        if (__atomic_load_n(&sl->keys[i], __ATOMIC_RELAXED) != key) continue;
        if (!__atomic_exchange_n(&sl->futex, 1, __ATOMIC_SEQ_CST)) {
          int saved = errno;
          fs_sys(SYS_futex, (long)&sl->futex, FUTEX_WAKE, 1, 0, 0, 0);
          errno = saved;
          if (++woken >= n) return woken;
        }
        break;
      }
    }
  }
  return woken;
}

void bl_fsync_fds_closed(unsigned int first, unsigned int last) __attribute__((visibility("hidden")));
void bl_fsync_fds_closed(unsigned int first, unsigned int last) {
  int fd = __atomic_load_n(&fs_fd, __ATOMIC_ACQUIRE);
  if (fd >= 0 && (unsigned int)fd >= first && (unsigned int)fd <= last) __atomic_store_n(&fs_fd, -1, __ATOMIC_RELEASE);
}

int bl_fsync_syscall(long number, const long *args, long *ret) __attribute__((visibility("hidden")));
int bl_fsync_syscall(long number, const long *args, long *ret) {
  if (number == SYS_futex_waitv) {
    if (!fs_on()) return 0;
    if (!args[0] && !args[1]) {
      errno = EINVAL;
      *ret = -1;
      return 1;
    }
    *ret = fs_waitv((const struct fs_waitv *)args[0], (uint32_t)args[1], (uint32_t)args[2],
                    (const struct timespec *)args[3], (int)args[4]);
    return 1;
  }
  if (number == SYS_futex && (int)args[1] == FUTEX_WAKE && fs_reg && __atomic_load_n(&fs_cur, __ATOMIC_RELAXED)) {
    uint32_t key;
    if (!fs_key_of((uint64_t)args[0], &key)) return 0;
    *ret = fs_wake(key, (int)args[2]);
    return 1;
  }
  return 0;
}

static int fs_fsync_name(const char *name) {
  size_t n;
  if (!name) return 0;
  if (*name == '/') name++;
  n = strlen(name);
  return n > 11 && !strncmp(name, "wine-", 5) && !strcmp(name + n - 6, "-fsync");
}

static void fs_attach(const char *name, int fd, int create) {
  char path[256];
  struct stat st;
  void *p;
  int rfd;
  if (fs_reg) {
    fs_fd = fd;
    return;
  }
  if (snprintf(path, sizeof(path), "%s-droiddeck", name) >= (int)sizeof(path)) return;
  if (create) fs_real_shm_unlink(path);
  rfd = fs_real_shm_open(path, O_RDWR | O_CREAT, 0644);
  if (rfd < 0) {
    fprintf(stderr, "droiddeck-fsync: cannot open %s: %s\n", path, strerror(errno));
    return;
  }
  if (fstat(rfd, &st) == 0 && (size_t)st.st_size < FS_REGION && ftruncate(rfd, (off_t)FS_REGION) != 0) {
    fprintf(stderr, "droiddeck-fsync: cannot size %s: %s\n", path, strerror(errno));
    close(rfd);
    return;
  }
  if (!fs_real_mmap) fs_real_mmap = (void *(*)(void *, size_t, int, int, int, off_t))dlsym(RTLD_NEXT, "mmap");
  p = fs_real_mmap(NULL, FS_REGION, PROT_READ | PROT_WRITE, MAP_SHARED, rfd, 0);
  close(rfd);
  if (p == MAP_FAILED) {
    fprintf(stderr, "droiddeck-fsync: cannot map %s: %s\n", path, strerror(errno));
    return;
  }
  fs_reg = p;
  fs_fd = fd;
  if (create) {
    __atomic_store_n(&fs_head()->magic, FS_MAGIC, __ATOMIC_RELEASE);
    fprintf(stderr, "droiddeck-fsync: up and running.\n");
  }
}

int shm_open(const char *name, int oflag, mode_t mode) {
  int fd, saved;
  if (!fs_real_shm_open) fs_real_shm_open = (int (*)(const char *, int, mode_t))dlsym(RTLD_NEXT, "shm_open");
  if (!fs_real_shm_unlink) fs_real_shm_unlink = (int (*)(const char *))dlsym(RTLD_NEXT, "shm_unlink");
  if (!fs_real_shm_open) {
    errno = ENOSYS;
    return -1;
  }
  fd = fs_real_shm_open(name, oflag, mode);
  if (fd >= 0 && fs_on() && fs_fsync_name(name) && fs_real_shm_unlink) {
    saved = errno;
    fs_attach(name, fd, oflag & O_CREAT);
    errno = saved;
  }
  return fd;
}

int shm_unlink(const char *name) {
  int r, saved;
  if (!fs_real_shm_unlink) fs_real_shm_unlink = (int (*)(const char *))dlsym(RTLD_NEXT, "shm_unlink");
  if (!fs_real_shm_unlink) {
    errno = ENOSYS;
    return -1;
  }
  r = fs_real_shm_unlink(name);
  if (fs_on() && fs_fsync_name(name)) {
    char path[256];
    saved = errno;
    if (snprintf(path, sizeof(path), "%s-droiddeck", name) < (int)sizeof(path)) fs_real_shm_unlink(path);
    errno = saved;
  }
  return r;
}

void *mmap(void *addr, size_t len, int prot, int flags, int fd, off_t off) {
  void *p;
  if (!fs_real_mmap) fs_real_mmap = (void *(*)(void *, size_t, int, int, int, off_t))dlsym(RTLD_NEXT, "mmap");
  p = fs_real_mmap(addr, len, prot, flags, fd, off);
  if (p == MAP_FAILED && bl_noexec_retry(prot, flags, fd)) p = fs_real_mmap(addr, len, prot & ~PROT_EXEC, flags, fd, off);
  if (p != MAP_FAILED && __builtin_expect(fs_fd >= 0, 0)) fs_track(p, len, flags, fd, off);
  return p;
}

void *mmap64(void *addr, size_t len, int prot, int flags, int fd, off64_t off) {
  void *p;
  if (!fs_real_mmap64) fs_real_mmap64 = (void *(*)(void *, size_t, int, int, int, off64_t))dlsym(RTLD_NEXT, "mmap64");
  p = fs_real_mmap64(addr, len, prot, flags, fd, off);
  if (p == MAP_FAILED && bl_noexec_retry(prot, flags, fd)) p = fs_real_mmap64(addr, len, prot & ~PROT_EXEC, flags, fd, off);
  if (p != MAP_FAILED && __builtin_expect(fs_fd >= 0, 0)) fs_track(p, len, flags, fd, off);
  return p;
}

int munmap(void *addr, size_t len) {
  uintptr_t lo = (uintptr_t)addr, hi = lo + len;
  sigset_t old;
  int r, saved;
  if (!fs_real_munmap) fs_real_munmap = (int (*)(void *, size_t))dlsym(RTLD_NEXT, "munmap");
  if (__builtin_expect(fs_fd < 0, 1) || !fs_overlaps(lo, hi)) return fs_real_munmap(addr, len);
  fs_block(&old);
  pthread_mutex_lock(&fs_mx);
  r = fs_real_munmap(addr, len);
  saved = errno;
  if (r == 0) fs_rebuild_locked(lo, hi, NULL);
  pthread_mutex_unlock(&fs_mx);
  pthread_sigmask(SIG_SETMASK, &old, NULL);
  errno = saved;
  return r;
}
