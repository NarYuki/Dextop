#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/futex.h>
#include <linux/ioctl.h>
#include <pthread.h>
#include <signal.h>
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/random.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/timerfd.h>
#include <time.h>
#include <unistd.h>

struct ns_sem_args {
  uint32_t count;
  uint32_t max;
};

struct ns_mutex_args {
  uint32_t owner;
  uint32_t count;
};

struct ns_event_args {
  uint32_t manual;
  uint32_t signaled;
};

struct ns_wait_args {
  uint64_t timeout;
  uint64_t objs;
  uint32_t count;
  uint32_t index;
  uint32_t flags;
  uint32_t owner;
  uint32_t alert;
  uint32_t pad;
};

#define NS_CREATE_SEM _IOW('N', 0x80, struct ns_sem_args)
#define NS_SEM_RELEASE _IOWR('N', 0x81, uint32_t)
#define NS_WAIT_ANY _IOWR('N', 0x82, struct ns_wait_args)
#define NS_WAIT_ALL _IOWR('N', 0x83, struct ns_wait_args)
#define NS_CREATE_MUTEX _IOW('N', 0x84, struct ns_mutex_args)
#define NS_MUTEX_UNLOCK _IOWR('N', 0x85, struct ns_mutex_args)
#define NS_MUTEX_KILL _IOW('N', 0x86, uint32_t)
#define NS_CREATE_EVENT _IOW('N', 0x87, struct ns_event_args)
#define NS_EVENT_SET _IOR('N', 0x88, uint32_t)
#define NS_EVENT_RESET _IOR('N', 0x89, uint32_t)
#define NS_EVENT_PULSE _IOR('N', 0x8a, uint32_t)
#define NS_SEM_READ _IOR('N', 0x8b, struct ns_sem_args)
#define NS_MUTEX_READ _IOR('N', 0x8c, struct ns_mutex_args)
#define NS_EVENT_READ _IOR('N', 0x8d, struct ns_event_args)

#define NS_WAIT_REALTIME 1u
#define NS_MAX_WAIT 64u
#define NS_ENTS (NS_MAX_WAIT + 1u)
#define NS_SLOTS 65536u
#define NS_QS 16384u
#define NS_PROCS 256u
#define NS_FDS 65536
#define NS_DEVS 8
#define NS_VERSION 1u
#define NS_HEAD_MAGIC 0x31434e5953544e44ull
#define NS_DEV_MAGIC 0x4e54445600000000ull
#define NS_OBJ_MAGIC 0x27ull
#define NS_WAITERS 0x80000000u
#define NS_SWEEP_NS 2000000000ull
#define NS_DEAD 0xffffffffu

#ifndef CLOSE_RANGE_CLOEXEC
#define CLOSE_RANGE_CLOEXEC (1u << 2)
#endif

#define NS_E_VALID (1ull << 63)
#define NS_E_DEVICE (1ull << 62)

enum { NS_FREE, NS_SEM, NS_MUTEX, NS_EVENT };

struct ns_proc {
  uint32_t pid;
  uint32_t pad;
};

struct ns_head {
  uint64_t magic;
  uint32_t version;
  uint32_t tag;
  uint32_t all_lock;
  uint32_t proc_lock;
  uint64_t slot_free;
  uint64_t q_free;
  uint32_t slot_hwm;
  uint32_t q_hwm;
  uint64_t sweep_at;
  struct ns_proc procs[NS_PROCS];
};

struct ns_obj {
  uint32_t lock;
  uint32_t type;
  uint32_t gen;
  uint32_t all_hint;
  uint32_t a;
  uint32_t b;
  uint32_t ownerdead;
  uint32_t busy;
  uint32_t any_head;
  uint32_t any_tail;
  uint32_t all_head;
  uint32_t all_tail;
  uint32_t next_free;
  uint32_t pad[3];
  uint64_t holders[NS_PROCS / 64];
  uint64_t pad2[4];
};

struct ns_ent {
  uint32_t next;
  uint32_t prev;
  uint32_t obj;
  uint32_t index;
  uint32_t linked;
};

struct ns_q {
  int32_t signaled;
  uint32_t owner;
  uint32_t tid;
  uint32_t pid;
  uint32_t proc;
  uint32_t count;
  uint32_t total;
  uint32_t all;
  uint32_t ownerdead;
  uint32_t waiting;
  uint32_t inuse;
  uint32_t next_free;
  struct ns_ent ent[NS_ENTS];
};

#define NS_HEAD_SIZE 65536ul
#define NS_OBJ_OFF NS_HEAD_SIZE
#define NS_Q_OFF (NS_OBJ_OFF + (size_t)NS_SLOTS * sizeof(struct ns_obj))
#define NS_REGION (NS_Q_OFF + (size_t)NS_QS * sizeof(struct ns_q))

_Static_assert(sizeof(struct ns_head) <= NS_HEAD_SIZE, "ntsync head");
_Static_assert(sizeof(struct ns_obj) == 128, "ntsync object");

struct ns_dev {
  struct ns_head *h;
  uint64_t ino;
  uint32_t tag;
  int proc;
  uint32_t *counts;
  uint32_t refs;
};

struct ns_tls {
  struct ns_dev *d;
  uint32_t q;
  int waiting;
};

static struct ns_dev ns_devs[NS_DEVS];
static int ns_ndev;
static uint64_t *ns_fdt;
static pthread_mutex_t ns_mx = PTHREAD_MUTEX_INITIALIZER;
static pid_t ns_pid;
static int ns_state = -1;
static pthread_key_t ns_key;
static pthread_once_t ns_once = PTHREAD_ONCE_INIT;
static __thread uint32_t ns_tid;
static __thread struct ns_tls ns_tq;

static int (*ns_real_close)(int);

static int ns_on(void) {
  if (ns_state < 0) {
    const char *e = getenv("BL_SYNC");
    ns_state = e && e[0] == '1';
  }
  return ns_state;
}

static int ns_close_fd(int fd) {
  if (!ns_real_close) ns_real_close = (int (*)(int))dlsym(RTLD_NEXT, "close");
  return ns_real_close(fd);
}

static inline void ns_relax(void) {
#if defined(__aarch64__)
  __asm__ volatile("yield" ::: "memory");
#elif defined(__x86_64__) || defined(__i386__)
  __asm__ volatile("pause" ::: "memory");
#endif
}

static uint32_t ns_self(void) {
  if (!ns_tid) ns_tid = (uint32_t)syscall(SYS_gettid);
  return ns_tid;
}

static uint32_t ns_getpid(void) {
  if (!ns_pid) ns_pid = getpid();
  return (uint32_t)ns_pid;
}

static long ns_futex(void *w, int op, uint32_t val, const struct timespec *ts, uint32_t bits) {
  return syscall(SYS_futex, w, op, val, ts, NULL, bits);
}

static int ns_dead(uint32_t id) {
  return id && kill((pid_t)id, 0) == -1 && errno == ESRCH;
}

static void ns_lock(uint32_t *w) {
  uint32_t me = ns_self(), want = me;
  for (unsigned spin = 0;; spin++) {
    uint32_t v = 0;
    if (__atomic_compare_exchange_n(w, &v, want, 0, __ATOMIC_ACQUIRE, __ATOMIC_RELAXED)) return;
    if (spin < 100) {
      ns_relax();
      continue;
    }
    if (!(v & NS_WAITERS)) {
      if (!__atomic_compare_exchange_n(w, &v, v | NS_WAITERS, 0, __ATOMIC_RELAXED, __ATOMIC_RELAXED)) continue;
      v |= NS_WAITERS;
    }
    want = me | NS_WAITERS;
    struct timespec ts = {0, 20000000};
    int saved = errno;
    if (ns_futex(w, FUTEX_WAIT, v, &ts, 0) == -1 && errno == ETIMEDOUT && ns_dead(v & ~NS_WAITERS)
        && __atomic_compare_exchange_n(w, &v, want, 0, __ATOMIC_ACQUIRE, __ATOMIC_RELAXED)) {
      errno = saved;
      return;
    }
    errno = saved;
  }
}

static void ns_unlock(uint32_t *w) {
  if (__atomic_exchange_n(w, 0, __ATOMIC_RELEASE) & NS_WAITERS) {
    int saved = errno;
    ns_futex(w, FUTEX_WAKE, 1, NULL, 0);
    errno = saved;
  }
}

static void ns_block(sigset_t *old) {
  sigset_t s;
  sigfillset(&s);
  sigdelset(&s, SIGSEGV);
  sigdelset(&s, SIGBUS);
  sigdelset(&s, SIGILL);
  sigdelset(&s, SIGFPE);
  sigdelset(&s, SIGTRAP);
  pthread_sigmask(SIG_BLOCK, &s, old);
}

static void ns_restore(const sigset_t *old) {
  pthread_sigmask(SIG_SETMASK, old, NULL);
}

static inline struct ns_obj *ns_obj(struct ns_head *h, uint32_t s) {
  return (struct ns_obj *)((char *)h + NS_OBJ_OFF) + s;
}

static inline struct ns_q *ns_q(struct ns_head *h, uint32_t i) {
  return (struct ns_q *)((char *)h + NS_Q_OFF) + i;
}

static inline uint32_t ns_node_id(uint32_t q, uint32_t i) {
  return q * NS_ENTS + i + 1;
}

static inline struct ns_ent *ns_node(struct ns_head *h, uint32_t id) {
  return &ns_q(h, (id - 1) / NS_ENTS)->ent[(id - 1) % NS_ENTS];
}

static inline struct ns_q *ns_node_q(struct ns_head *h, uint32_t id) {
  return ns_q(h, (id - 1) / NS_ENTS);
}

static void ns_link(struct ns_head *h, uint32_t *head, uint32_t *tail, uint32_t id) {
  struct ns_ent *e = ns_node(h, id);
  e->next = 0;
  e->prev = *tail;
  if (*tail) ns_node(h, *tail)->next = id;
  else *head = id;
  *tail = id;
  e->linked = 1;
}

static void ns_unlink(struct ns_head *h, uint32_t *head, uint32_t *tail, uint32_t id) {
  struct ns_ent *e = ns_node(h, id);
  if (!e->linked) return;
  if (e->prev) ns_node(h, e->prev)->next = e->next;
  else *head = e->next;
  if (e->next) ns_node(h, e->next)->prev = e->prev;
  else *tail = e->prev;
  e->next = e->prev = 0;
  e->linked = 0;
}

static int ns_no_holders(const struct ns_obj *o) {
  for (unsigned i = 0; i < NS_PROCS / 64; i++)
    if (o->holders[i]) return 0;
  return 1;
}

static void ns_push(uint64_t *head, uint32_t *next, uint32_t index) {
  uint64_t old = __atomic_load_n(head, __ATOMIC_ACQUIRE), nh;
  do {
    __atomic_store_n(next, (uint32_t)old, __ATOMIC_RELAXED);
    nh = (((old >> 32) + 1) << 32) | (index + 1);
  } while (!__atomic_compare_exchange_n(head, &old, nh, 1, __ATOMIC_RELEASE, __ATOMIC_ACQUIRE));
}

static int ns_pop(struct ns_head *h, uint64_t *head, int objs, uint32_t *out) {
  uint64_t old = __atomic_load_n(head, __ATOMIC_ACQUIRE);
  while ((uint32_t)old) {
    uint32_t i = (uint32_t)old - 1;
    uint32_t next = __atomic_load_n(objs ? &ns_obj(h, i)->next_free : &ns_q(h, i)->next_free, __ATOMIC_RELAXED);
    uint64_t nh = (((old >> 32) + 1) << 32) | next;
    if (__atomic_compare_exchange_n(head, &old, nh, 1, __ATOMIC_ACQUIRE, __ATOMIC_ACQUIRE)) {
      *out = i;
      return 1;
    }
  }
  return 0;
}

static int ns_grow(uint32_t *hwm, uint32_t limit, uint32_t *out) {
  uint32_t v = __atomic_load_n(hwm, __ATOMIC_RELAXED);
  while (v < limit) {
    if (__atomic_compare_exchange_n(hwm, &v, v + 1, 1, __ATOMIC_ACQ_REL, __ATOMIC_RELAXED)) {
      *out = v;
      return 1;
    }
  }
  return 0;
}

static void ns_maybe_free(struct ns_head *h, struct ns_obj *o) {
  if (o->type == NS_FREE || o->busy || !ns_no_holders(o)) return;
  o->type = NS_FREE;
  o->gen = (o->gen + 1) & 0xfffff;
  ns_push(&h->slot_free, &o->next_free, (uint32_t)(o - ns_obj(h, 0)));
}

static int ns_lock_obj(struct ns_head *h, struct ns_obj *o) {
  ns_lock(&o->lock);
  if (!o->all_hint) return 0;
  ns_unlock(&o->lock);
  ns_lock(&h->all_lock);
  ns_lock(&o->lock);
  return 1;
}

static void ns_unlock_obj(struct ns_head *h, struct ns_obj *o, int all) {
  ns_unlock(&o->lock);
  if (all) ns_unlock(&h->all_lock);
}

static int ns_claim(struct ns_q *q, int32_t index) {
  if (q->pid != ns_getpid()) {
    int saved = errno, dead = ns_dead(q->pid);
    errno = saved;
    if (dead) return 0;
  }
  int32_t want = -1;
  return __atomic_compare_exchange_n(&q->signaled, &want, index, 0, __ATOMIC_ACQ_REL, __ATOMIC_RELAXED);
}

static void ns_wake(struct ns_q *q) {
  int saved = errno;
  ns_futex(&q->signaled, FUTEX_WAKE, 1, NULL, 0);
  errno = saved;
}

static int ns_signaled(const struct ns_obj *o, uint32_t owner) {
  switch (o->type) {
  case NS_SEM:
    return o->a != 0;
  case NS_MUTEX:
    if (o->b && o->b != owner) return 0;
    return o->a < UINT32_MAX;
  case NS_EVENT:
    return o->b != 0;
  }
  return 0;
}

static void ns_take(struct ns_obj *o, struct ns_q *q) {
  switch (o->type) {
  case NS_SEM:
    o->a--;
    break;
  case NS_MUTEX:
    if (o->ownerdead) q->ownerdead = 1;
    o->ownerdead = 0;
    o->a++;
    o->b = q->owner;
    break;
  case NS_EVENT:
    if (!o->a) o->b = 0;
    break;
  }
}

static void ns_wake_any(struct ns_head *h, struct ns_obj *o) {
  uint32_t id = o->any_head;
  while (id) {
    struct ns_ent *e = ns_node(h, id);
    struct ns_q *q = ns_node_q(h, id);
    uint32_t next = e->next;
    if (!ns_signaled(o, q->owner)) {
      if (o->type != NS_MUTEX || o->a == UINT32_MAX) return;
    } else if (ns_claim(q, (int32_t)e->index)) {
      ns_take(o, q);
      ns_wake(q);
    }
    id = next;
  }
}

static void ns_wake_all_q(struct ns_head *h, struct ns_q *q, struct ns_obj *locked) {
  uint32_t n = q->count;
  int ok = 1;
  for (uint32_t i = 0; i < n; i++) {
    struct ns_obj *o = ns_obj(h, q->ent[i].obj);
    if (o != locked) ns_lock(&o->lock);
  }
  for (uint32_t i = 0; i < n && ok; i++)
    ok = ns_signaled(ns_obj(h, q->ent[i].obj), q->owner);
  if (ok && ns_claim(q, 0)) {
    for (uint32_t i = 0; i < n; i++) ns_take(ns_obj(h, q->ent[i].obj), q);
    ns_wake(q);
  }
  for (uint32_t i = 0; i < n; i++) {
    struct ns_obj *o = ns_obj(h, q->ent[i].obj);
    if (o != locked) ns_unlock(&o->lock);
  }
}

static void ns_wake_all_obj(struct ns_head *h, struct ns_obj *o) {
  uint32_t id = o->all_head;
  while (id) {
    uint32_t next = ns_node(h, id)->next;
    ns_wake_all_q(h, ns_node_q(h, id), o);
    id = next;
  }
}

static void ns_drop_q(struct ns_head *h, uint32_t qi) {
  struct ns_q *q = ns_q(h, qi);
  if (q->waiting) {
    if (q->all) ns_lock(&h->all_lock);
    for (uint32_t i = 0; i < q->total; i++) {
      struct ns_ent *e = &q->ent[i];
      if (e->obj == UINT32_MAX) continue;
      struct ns_obj *o = ns_obj(h, e->obj);
      int all = 0;
      if (q->all && i < q->count) {
        ns_lock(&o->lock);
      } else {
        if (q->all) ns_lock(&o->lock);
        else all = ns_lock_obj(h, o);
      }
      if (e->linked) {
        if (q->all && i < q->count) {
          ns_unlink(h, &o->all_head, &o->all_tail, ns_node_id(qi, i));
          o->all_hint--;
        } else {
          ns_unlink(h, &o->any_head, &o->any_tail, ns_node_id(qi, i));
        }
      }
      o->busy--;
      e->obj = UINT32_MAX;
      ns_maybe_free(h, o);
      if (q->all) ns_unlock(&o->lock);
      else ns_unlock_obj(h, o, all);
    }
    if (q->all) ns_unlock(&h->all_lock);
    q->waiting = 0;
  }
}

static void ns_free_q(struct ns_head *h, uint32_t qi) {
  struct ns_q *q = ns_q(h, qi);
  ns_drop_q(h, qi);
  q->inuse = 0;
  ns_push(&h->q_free, &q->next_free, qi);
}

static void ns_sweep_proc(struct ns_head *h, uint32_t p) {
  uint32_t qn = __atomic_load_n(&h->q_hwm, __ATOMIC_ACQUIRE);
  for (uint32_t i = 0; i < qn; i++) {
    struct ns_q *q = ns_q(h, i);
    if (q->inuse && q->proc == p) ns_free_q(h, i);
  }
  uint32_t sn = __atomic_load_n(&h->slot_hwm, __ATOMIC_ACQUIRE);
  uint64_t bit = 1ull << (p % 64);
  for (uint32_t s = 0; s < sn; s++) {
    struct ns_obj *o = ns_obj(h, s);
    if (!(__atomic_load_n(&o->holders[p / 64], __ATOMIC_RELAXED) & bit)) continue;
    ns_lock(&o->lock);
    o->holders[p / 64] &= ~bit;
    ns_maybe_free(h, o);
    ns_unlock(&o->lock);
  }
  __atomic_store_n(&h->procs[p].pid, 0, __ATOMIC_RELEASE);
}

static void ns_sweep_dead(struct ns_head *h) {
  int saved = errno;
  ns_lock(&h->proc_lock);
  for (uint32_t i = 0; i < NS_PROCS; i++) {
    uint32_t pid = h->procs[i].pid;
    if (pid && pid != ns_getpid() && ns_dead(pid)) ns_sweep_proc(h, i);
  }
  ns_unlock(&h->proc_lock);
  errno = saved;
}

static void ns_maybe_sweep(struct ns_head *h) {
  struct timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);
  uint64_t now = (uint64_t)ts.tv_sec * 1000000000ull + (uint64_t)ts.tv_nsec;
  uint64_t last = __atomic_load_n(&h->sweep_at, __ATOMIC_RELAXED);
  if (now - last < NS_SWEEP_NS) return;
  if (!__atomic_compare_exchange_n(&h->sweep_at, &last, now, 0, __ATOMIC_RELAXED, __ATOMIC_RELAXED)) return;
  ns_sweep_dead(h);
}

static int ns_attach(struct ns_dev *d) {
  if (d->proc >= 0) return d->proc;
  struct ns_head *h = d->h;
  uint32_t pid = ns_getpid();
  int slot = -1;
  if (!d->counts) {
    void *m = mmap(NULL, (size_t)NS_SLOTS * sizeof(uint32_t), PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (m == MAP_FAILED) return -1;
    d->counts = m;
  }
  int saved = errno;
  ns_lock(&h->proc_lock);
  for (uint32_t i = 0; i < NS_PROCS && slot < 0; i++)
    if (h->procs[i].pid == pid) {
      ns_sweep_proc(h, i);
      slot = (int)i;
    }
  for (uint32_t i = 0; i < NS_PROCS && slot < 0; i++)
    if (!h->procs[i].pid) slot = (int)i;
  for (uint32_t i = 0; i < NS_PROCS && slot < 0; i++)
    if (ns_dead(h->procs[i].pid)) {
      ns_sweep_proc(h, i);
      slot = (int)i;
    }
  if (slot >= 0) __atomic_store_n(&h->procs[slot].pid, pid, __ATOMIC_RELEASE);
  ns_unlock(&h->proc_lock);
  errno = saved;
  __atomic_store_n(&d->proc, slot, __ATOMIC_RELEASE);
  return slot;
}

static int ns_table(void) {
  if (ns_fdt) return 1;
  void *m = mmap(NULL, (size_t)NS_FDS * sizeof(uint64_t), PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
  if (m == MAP_FAILED) return 0;
  __atomic_store_n(&ns_fdt, (uint64_t *)m, __ATOMIC_RELEASE);
  return 1;
}

static struct ns_dev *ns_find(uint32_t tag) {
  for (int i = 0; i < ns_ndev; i++)
    if (ns_devs[i].h && ns_devs[i].tag == tag) return &ns_devs[i];
  return NULL;
}

static int ns_dev_get(struct ns_dev *d) {
  uint32_t r = __atomic_load_n(&d->refs, __ATOMIC_ACQUIRE);
  while (r && r != NS_DEAD)
    if (__atomic_compare_exchange_n(&d->refs, &r, r + 1, 1, __ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE)) return 1;
  return 0;
}

static void ns_release(struct ns_dev *d) {
  struct ns_head *h = d->h;
  __atomic_store_n(&d->tag, 0, __ATOMIC_RELEASE);
  if (d->proc >= 0) {
    ns_lock(&h->proc_lock);
    ns_sweep_proc(h, (uint32_t)d->proc);
    ns_unlock(&h->proc_lock);
  }
  if (d->counts) munmap(d->counts, (size_t)NS_SLOTS * sizeof(uint32_t));
  munmap(h, NS_REGION);
  d->counts = NULL;
  d->proc = -1;
  d->ino = 0;
  __atomic_store_n(&d->h, NULL, __ATOMIC_RELEASE);
}

static void ns_dev_put_locked(struct ns_dev *d) {
  if (__atomic_sub_fetch(&d->refs, 1, __ATOMIC_ACQ_REL)) return;
  uint32_t zero = 0;
  if (d->h && __atomic_compare_exchange_n(&d->refs, &zero, NS_DEAD, 0, __ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE)) ns_release(d);
}

static void ns_dev_put(struct ns_dev *d) {
  if (__atomic_sub_fetch(&d->refs, 1, __ATOMIC_ACQ_REL)) return;
  sigset_t old;
  int saved = errno;
  ns_block(&old);
  pthread_mutex_lock(&ns_mx);
  uint32_t zero = 0;
  if (d->h && __atomic_compare_exchange_n(&d->refs, &zero, NS_DEAD, 0, __ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE)) ns_release(d);
  pthread_mutex_unlock(&ns_mx);
  ns_restore(&old);
  errno = saved;
}

static struct ns_dev *ns_map(int fd, uint32_t tag, uint64_t ino) {
  struct ns_dev *d = ns_find(tag);
  if (d) return d->ino == ino ? d : NULL;
  for (int i = 0; i < ns_ndev && !d; i++)
    if (!ns_devs[i].h) d = &ns_devs[i];
  if (!d && ns_ndev == NS_DEVS) return NULL;
  struct ns_head *h = mmap(NULL, NS_REGION, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
  if (h == MAP_FAILED) return NULL;
  if (h->magic != NS_HEAD_MAGIC || h->version != NS_VERSION || h->tag != tag) {
    munmap(h, NS_REGION);
    return NULL;
  }
  if (!d) d = &ns_devs[ns_ndev];
  d->ino = ino;
  d->proc = -1;
  d->counts = NULL;
  d->refs = 0;
  __atomic_store_n(&d->h, h, __ATOMIC_RELEASE);
  __atomic_store_n(&d->tag, tag, __ATOMIC_RELEASE);
  if (d == &ns_devs[ns_ndev]) __atomic_store_n(&ns_ndev, ns_ndev + 1, __ATOMIC_RELEASE);
  return d;
}

static uint64_t ns_entry_dev(struct ns_dev *d) {
  return NS_E_VALID | NS_E_DEVICE | ((uint64_t)(d - ns_devs) << 56);
}

static uint64_t ns_entry_obj(struct ns_dev *d, uint32_t gen, uint32_t s) {
  return NS_E_VALID | ((uint64_t)(d - ns_devs) << 56) | ((uint64_t)gen << 20) | s;
}

static uint64_t ns_token(uint32_t tag, uint32_t gen, uint32_t s) {
  return (NS_OBJ_MAGIC << 56) | ((uint64_t)(tag & 0xffff) << 40) | ((uint64_t)(gen & 0xfffff) << 20) | s;
}

static void ns_forget_locked(int fd) {
  uint64_t e = __atomic_exchange_n(&ns_fdt[fd], 0, __ATOMIC_ACQ_REL);
  if (!e) return;
  struct ns_dev *d = &ns_devs[(e >> 56) & 7];
  if (!(e & NS_E_DEVICE)) {
    uint32_t s = (uint32_t)(e & 0xfffff);
    if (d->proc >= 0 && d->counts && d->counts[s] && !--d->counts[s]) {
      struct ns_obj *o = ns_obj(d->h, s);
      ns_lock(&o->lock);
      o->holders[d->proc / 64] &= ~(1ull << (d->proc % 64));
      ns_maybe_free(d->h, o);
      ns_unlock(&o->lock);
    }
  }
  ns_dev_put_locked(d);
}

static void ns_forget(int fd) {
  sigset_t old;
  int saved = errno;
  ns_block(&old);
  pthread_mutex_lock(&ns_mx);
  ns_forget_locked(fd);
  pthread_mutex_unlock(&ns_mx);
  ns_restore(&old);
  errno = saved;
}

static uint64_t ns_adopt(struct ns_dev *d, uint32_t tag, int fd, uint32_t gen, uint32_t s) {
  uint64_t e = 0;
  sigset_t old;
  ns_block(&old);
  pthread_mutex_lock(&ns_mx);
  if (!d->h || d->tag != tag || !ns_table() || ns_attach(d) < 0 || s >= NS_SLOTS) goto out;
  if ((e = __atomic_load_n(&ns_fdt[fd], __ATOMIC_ACQUIRE))) goto out;
  struct ns_obj *o = ns_obj(d->h, s);
  ns_lock(&o->lock);
  if (o->type != NS_FREE && o->gen == gen) {
    if (!d->counts[s]++) o->holders[d->proc / 64] |= 1ull << (d->proc % 64);
    e = ns_entry_obj(d, gen, s);
    d->refs++;
    __atomic_store_n(&ns_fdt[fd], e, __ATOMIC_RELEASE);
  }
  ns_unlock(&o->lock);
out:
  pthread_mutex_unlock(&ns_mx);
  ns_restore(&old);
  return e;
}

static uint64_t ns_note_device(int fd) {
  struct stat st;
  uint64_t e = 0;
  if (syscall(SYS_fstat, fd, &st) != 0 || !S_ISREG(st.st_mode)) return 0;
  if (((uint64_t)st.st_size & ~0xffffull) != NS_DEV_MAGIC) return 0;
  sigset_t old;
  ns_block(&old);
  pthread_mutex_lock(&ns_mx);
  struct ns_dev *d = ns_table() ? ns_map(fd, (uint32_t)st.st_size & 0xffff, (uint64_t)st.st_ino) : NULL;
  if (d) {
    e = __atomic_load_n(&ns_fdt[fd], __ATOMIC_ACQUIRE);
    if (!e) {
      e = ns_entry_dev(d);
      d->refs++;
      __atomic_store_n(&ns_fdt[fd], e, __ATOMIC_RELEASE);
    }
  }
  pthread_mutex_unlock(&ns_mx);
  ns_restore(&old);
  return e;
}

static uint64_t ns_lookup(int fd) {
  if (fd < 0 || fd >= NS_FDS) return 0;
  uint64_t *t = __atomic_load_n(&ns_fdt, __ATOMIC_ACQUIRE);
  if (t) {
    uint64_t e = __atomic_load_n(&t[fd], __ATOMIC_ACQUIRE);
    if (e) return e;
  }
  int saved = errno;
  struct itimerspec its;
  uint64_t e = 0;
  if (timerfd_gettime(fd, &its) == 0) {
    uint64_t tok = (uint64_t)its.it_interval.tv_sec * 1000000000ull + (uint64_t)its.it_interval.tv_nsec;
    if (its.it_value.tv_sec == 0 && its.it_value.tv_nsec == 0 && (tok >> 56) == NS_OBJ_MAGIC) {
      struct ns_dev *d = NULL;
      uint32_t tag = (uint32_t)(tok >> 40) & 0xffff;
      int n = __atomic_load_n(&ns_ndev, __ATOMIC_ACQUIRE);
      for (int i = 0; i < n && !d; i++)
        if (__atomic_load_n(&ns_devs[i].tag, __ATOMIC_ACQUIRE) == tag) d = &ns_devs[i];
      if (d) e = ns_adopt(d, tag, fd, (uint32_t)(tok >> 20) & 0xfffff, (uint32_t)tok & 0xfffff);
    }
  } else {
    e = ns_note_device(fd);
  }
  errno = saved;
  return e;
}

static int ns_ready(struct ns_dev *d) {
  if (__atomic_load_n(&ns_fdt, __ATOMIC_ACQUIRE) && __atomic_load_n(&d->proc, __ATOMIC_ACQUIRE) >= 0) return 1;
  pthread_mutex_lock(&ns_mx);
  int ok = ns_table() && ns_attach(d) >= 0;
  pthread_mutex_unlock(&ns_mx);
  return ok;
}

static void ns_thread_gone(void *p) {
  struct ns_tls *t = p;
  if (!t || !t->d || !t->q) return;
  sigset_t old;
  struct ns_dev *d = t->d;
  ns_block(&old);
  ns_free_q(d->h, t->q - 1);
  t->q = 0;
  t->waiting = 0;
  t->d = NULL;
  ns_restore(&old);
  ns_dev_put(d);
}

static void ns_key_init(void) {
  pthread_key_create(&ns_key, ns_thread_gone);
}

static struct ns_q *ns_thread_q(struct ns_dev *d, uint32_t *qi) {
  struct ns_head *h = d->h;
  if (ns_tq.d && ns_tq.q && ns_tq.waiting) {
    ns_drop_q(ns_tq.d->h, ns_tq.q - 1);
    ns_tq.waiting = 0;
  }
  if (ns_tq.d == d && ns_tq.q) {
    *qi = ns_tq.q - 1;
    return ns_q(h, *qi);
  }
  if (ns_tq.d && ns_tq.q) {
    struct ns_dev *old = ns_tq.d;
    ns_free_q(old->h, ns_tq.q - 1);
    ns_tq.q = 0;
    ns_tq.d = NULL;
    ns_dev_put(old);
  }
  pthread_once(&ns_once, ns_key_init);
  uint32_t i;
  if (!ns_pop(h, &h->q_free, 0, &i) && !ns_grow(&h->q_hwm, NS_QS, &i)) {
    ns_sweep_dead(h);
    if (!ns_pop(h, &h->q_free, 0, &i)) return NULL;
  }
  struct ns_q *q = ns_q(h, i);
  q->proc = (uint32_t)d->proc;
  q->waiting = 0;
  q->inuse = 1;
  __atomic_add_fetch(&d->refs, 1, __ATOMIC_ACQ_REL);
  ns_tq.d = d;
  ns_tq.q = i + 1;
  ns_tq.waiting = 0;
  pthread_setspecific(ns_key, &ns_tq);
  *qi = i;
  return q;
}

static int ns_create(struct ns_dev *d, int type, uint32_t a, uint32_t b) {
  struct ns_head *h = d->h;
  sigset_t old;
  int fd = -1, err = 0;
  uint32_t s = 0;
  ns_block(&old);
  pthread_mutex_lock(&ns_mx);
  if (!ns_table() || ns_attach(d) < 0) {
    err = ENOMEM;
    goto out;
  }
  if (!ns_pop(h, &h->slot_free, 1, &s) && !ns_grow(&h->slot_hwm, NS_SLOTS, &s)) {
    ns_sweep_dead(h);
    if (!ns_pop(h, &h->slot_free, 1, &s)) {
      err = ENOMEM;
      goto out;
    }
  }
  struct ns_obj *o = ns_obj(h, s);
  ns_lock(&o->lock);
  o->a = a;
  o->b = b;
  o->ownerdead = 0;
  o->all_hint = 0;
  o->any_head = o->any_tail = o->all_head = o->all_tail = 0;
  memset(o->holders, 0, sizeof(o->holders));
  o->holders[d->proc / 64] = 1ull << (d->proc % 64);
  o->type = (uint32_t)type;
  uint32_t gen = o->gen;
  ns_unlock(&o->lock);
  fd = timerfd_create(CLOCK_MONOTONIC, TFD_CLOEXEC);
  if (fd >= 0) {
    uint64_t tok = ns_token(d->tag, gen, s);
    struct itimerspec its = {{(time_t)(tok / 1000000000ull), (long)(tok % 1000000000ull)}, {0, 0}};
    if (fd >= NS_FDS || timerfd_settime(fd, 0, &its, NULL) != 0) {
      err = fd >= NS_FDS ? EMFILE : errno;
      ns_close_fd(fd);
      fd = -1;
    }
  } else {
    err = errno;
  }
  ns_lock(&o->lock);
  if (fd < 0) {
    o->holders[d->proc / 64] &= ~(1ull << (d->proc % 64));
    ns_maybe_free(h, o);
  } else {
    d->counts[s] = 1;
    d->refs++;
    __atomic_store_n(&ns_fdt[fd], ns_entry_obj(d, gen, s), __ATOMIC_RELEASE);
  }
  ns_unlock(&o->lock);
out:
  pthread_mutex_unlock(&ns_mx);
  ns_restore(&old);
  if (fd < 0) errno = err;
  return fd;
}

static int ns_expired(const struct ns_wait_args *args) {
  struct timespec now;
  if (clock_gettime((args->flags & NS_WAIT_REALTIME) ? CLOCK_REALTIME : CLOCK_MONOTONIC, &now)) return 0;
  return (uint64_t)now.tv_sec * 1000000000ull + (uint64_t)now.tv_nsec >= args->timeout;
}

static int ns_sleep(struct ns_q *q, const struct ns_wait_args *args) {
  struct timespec ts, *tsp = NULL;
  int op = FUTEX_WAIT_BITSET | ((args->flags & NS_WAIT_REALTIME) ? FUTEX_CLOCK_REALTIME : 0);
  if (args->timeout != UINT64_MAX) {
    ts.tv_sec = (time_t)(args->timeout / 1000000000ull);
    ts.tv_nsec = (long)(args->timeout % 1000000000ull);
    tsp = &ts;
  }
  while (__atomic_load_n(&q->signaled, __ATOMIC_ACQUIRE) == -1) {
    if (tsp && ns_expired(args)) return ETIMEDOUT;
    if (ns_futex(&q->signaled, op, (uint32_t)-1, tsp, FUTEX_BITSET_MATCH_ANY) == -1) {
      if (errno == ETIMEDOUT) return ETIMEDOUT;
      if (errno == EINTR) return EINTR;
    }
  }
  return 0;
}

static int ns_wait(struct ns_dev *d, struct ns_wait_args *ua, int all) {
  struct ns_head *h = d->h;
  struct ns_wait_args args;
  int fds[NS_ENTS];
  uint32_t slots[NS_ENTS], gens[NS_ENTS];
  memcpy(&args, ua, sizeof(args));
  if (args.pad || (args.flags & ~NS_WAIT_REALTIME) || !args.owner || args.count > NS_MAX_WAIT) return EINVAL;
  uint32_t total = args.count + (args.alert ? 1 : 0);
  if (args.count) memcpy(fds, (const void *)(uintptr_t)args.objs, args.count * sizeof(int));
  if (args.alert) fds[args.count] = (int)args.alert;
  for (uint32_t i = 0; i < total; i++) {
    uint64_t e = ns_lookup(fds[i]);
    if (!e || (e & NS_E_DEVICE) || &ns_devs[(e >> 56) & 7] != d) return EINVAL;
    slots[i] = (uint32_t)(e & 0xfffff);
    gens[i] = (uint32_t)(e >> 20) & 0xfffff;
    if (all && i < args.count)
      for (uint32_t j = 0; j < i; j++)
        if (slots[j] == slots[i]) return EINVAL;
  }
  sigset_t old;
  ns_block(&old);
  uint32_t qi;
  struct ns_q *q = ns_ready(d) ? ns_thread_q(d, &qi) : NULL;
  if (!q) {
    ns_restore(&old);
    return ENOMEM;
  }
  ns_maybe_sweep(h);
  __atomic_store_n(&q->signaled, -1, __ATOMIC_RELAXED);
  q->owner = args.owner;
  q->tid = ns_self();
  q->pid = ns_getpid();
  q->count = args.count;
  q->total = 0;
  q->all = (uint32_t)all;
  q->ownerdead = 0;
  for (uint32_t i = 0; i < total; i++) {
    q->ent[i].obj = UINT32_MAX;
    q->ent[i].index = i;
    q->ent[i].linked = 0;
    q->ent[i].next = q->ent[i].prev = 0;
  }
  q->total = total;
  q->waiting = 1;
  ns_tq.waiting = 1;
  int bad = 0;
  if (!all) {
    for (uint32_t i = 0; i < total && !bad; i++) {
      struct ns_obj *o = ns_obj(h, slots[i]);
      int a = ns_lock_obj(h, o);
      if (o->type == NS_FREE || o->gen != gens[i]) {
        bad = 1;
      } else {
        o->busy++;
        q->ent[i].obj = slots[i];
        ns_link(h, &o->any_head, &o->any_tail, ns_node_id(qi, i));
      }
      ns_unlock_obj(h, o, a);
    }
    for (uint32_t i = 0; i < total && !bad; i++) {
      if (__atomic_load_n(&q->signaled, __ATOMIC_ACQUIRE) != -1) break;
      struct ns_obj *o = ns_obj(h, slots[i]);
      int a = ns_lock_obj(h, o);
      ns_wake_any(h, o);
      ns_unlock_obj(h, o, a);
    }
  } else {
    ns_lock(&h->all_lock);
    for (uint32_t i = 0; i < total && !bad; i++) {
      struct ns_obj *o = ns_obj(h, slots[i]);
      ns_lock(&o->lock);
      if (o->type == NS_FREE || o->gen != gens[i]) {
        bad = 1;
      } else {
        o->busy++;
        q->ent[i].obj = slots[i];
        if (i < args.count) {
          o->all_hint++;
          ns_link(h, &o->all_head, &o->all_tail, ns_node_id(qi, i));
        } else {
          ns_link(h, &o->any_head, &o->any_tail, ns_node_id(qi, i));
        }
      }
      ns_unlock(&o->lock);
    }
    if (!bad) ns_wake_all_q(h, q, NULL);
    ns_unlock(&h->all_lock);
    if (!bad && args.alert && __atomic_load_n(&q->signaled, __ATOMIC_ACQUIRE) == -1) {
      struct ns_obj *o = ns_obj(h, slots[args.count]);
      int a = ns_lock_obj(h, o);
      ns_wake_any(h, o);
      ns_unlock_obj(h, o, a);
    }
  }
  int ret = 0;
  if (!bad && __atomic_load_n(&q->signaled, __ATOMIC_ACQUIRE) == -1) {
    sigset_t held;
    ns_restore(&old);
    ret = ns_sleep(q, &args);
    ns_block(&held);
  }
  ns_drop_q(h, qi);
  ns_tq.waiting = 0;
  int32_t got = __atomic_load_n(&q->signaled, __ATOMIC_ACQUIRE);
  int dead = (int)q->ownerdead;
  ns_restore(&old);
  if (bad) return EINVAL;
  if (got != -1) {
    ua->index = (uint32_t)got;
    return dead ? EOWNERDEAD : 0;
  }
  return ret ? ret : ETIMEDOUT;
}

static int ns_obj_ioctl(struct ns_dev *d, uint64_t e, unsigned long req, void *arg) {
  struct ns_head *h = d->h;
  uint32_t s = (uint32_t)(e & 0xfffff), gen = (uint32_t)(e >> 20) & 0xfffff;
  struct ns_obj *o = ns_obj(h, s);
  uint32_t type = __atomic_load_n(&o->type, __ATOMIC_ACQUIRE), u32 = 0, prev = 0;
  struct ns_mutex_args margs = {0, 0};
  int want, ret = 0;
  switch (req) {
  case NS_SEM_RELEASE:
    memcpy(&u32, arg, sizeof(u32));
    want = NS_SEM;
    break;
  case NS_SEM_READ:
    want = NS_SEM;
    break;
  case NS_MUTEX_UNLOCK:
    memcpy(&margs, arg, sizeof(margs));
    if (!margs.owner) return EINVAL;
    want = NS_MUTEX;
    break;
  case NS_MUTEX_KILL:
    memcpy(&u32, arg, sizeof(u32));
    if (!u32) return EINVAL;
    if (type == NS_MUTEX && __atomic_load_n(&o->b, __ATOMIC_ACQUIRE) != u32) return EPERM;
    want = NS_MUTEX;
    break;
  case NS_MUTEX_READ:
    want = NS_MUTEX;
    break;
  case NS_EVENT_SET:
  case NS_EVENT_RESET:
  case NS_EVENT_PULSE:
  case NS_EVENT_READ:
    want = NS_EVENT;
    break;
  default:
    return ENOTTY;
  }
  if (type != (uint32_t)want) return EINVAL;
  sigset_t old;
  ns_block(&old);
  int all = ns_lock_obj(h, o);
  if (o->type != (uint32_t)want || o->gen != gen) {
    ns_unlock_obj(h, o, all);
    ns_restore(&old);
    return EINVAL;
  }
  switch (req) {
  case NS_SEM_RELEASE: {
    prev = o->a;
    uint32_t sum = o->a + u32;
    if (sum < o->a || sum > o->b) {
      ret = EOVERFLOW;
      break;
    }
    o->a = sum;
    if (all) ns_wake_all_obj(h, o);
    ns_wake_any(h, o);
    break;
  }
  case NS_SEM_READ: {
    struct ns_sem_args r = {o->a, o->b};
    memcpy(arg, &r, sizeof(r));
    break;
  }
  case NS_MUTEX_UNLOCK:
    prev = o->a;
    if (o->b != margs.owner) {
      ret = EPERM;
      break;
    }
    if (!--o->a) o->b = 0;
    if (all) ns_wake_all_obj(h, o);
    ns_wake_any(h, o);
    break;
  case NS_MUTEX_KILL:
    if (o->b != u32) {
      ret = EPERM;
      break;
    }
    o->ownerdead = 1;
    o->b = 0;
    o->a = 0;
    if (all) ns_wake_all_obj(h, o);
    ns_wake_any(h, o);
    break;
  case NS_MUTEX_READ: {
    struct ns_mutex_args r = {o->b, o->a};
    memcpy(arg, &r, sizeof(r));
    if (o->ownerdead) ret = EOWNERDEAD;
    break;
  }
  case NS_EVENT_SET:
  case NS_EVENT_PULSE:
    prev = o->b;
    o->b = 1;
    if (all) ns_wake_all_obj(h, o);
    ns_wake_any(h, o);
    if (req == NS_EVENT_PULSE) o->b = 0;
    break;
  case NS_EVENT_RESET:
    prev = o->b;
    o->b = 0;
    break;
  case NS_EVENT_READ: {
    struct ns_event_args r = {o->a, o->b};
    memcpy(arg, &r, sizeof(r));
    break;
  }
  }
  ns_unlock_obj(h, o, all);
  ns_restore(&old);
  if (ret) return ret;
  if (req == NS_SEM_RELEASE || req == NS_EVENT_SET || req == NS_EVENT_RESET || req == NS_EVENT_PULSE)
    memcpy(arg, &prev, sizeof(prev));
  else if (req == NS_MUTEX_UNLOCK)
    memcpy((char *)arg + offsetof(struct ns_mutex_args, count), &prev, sizeof(prev));
  return 0;
}

static int ns_dev_ioctl(struct ns_dev *d, unsigned long req, void *arg, int *fd) {
  switch (req) {
  case NS_CREATE_SEM: {
    struct ns_sem_args a;
    memcpy(&a, arg, sizeof(a));
    if (a.count > a.max) return EINVAL;
    return (*fd = ns_create(d, NS_SEM, a.count, a.max)) < 0 ? errno : 0;
  }
  case NS_CREATE_MUTEX: {
    struct ns_mutex_args a;
    memcpy(&a, arg, sizeof(a));
    if (!a.owner != !a.count) return EINVAL;
    return (*fd = ns_create(d, NS_MUTEX, a.count, a.owner)) < 0 ? errno : 0;
  }
  case NS_CREATE_EVENT: {
    struct ns_event_args a;
    memcpy(&a, arg, sizeof(a));
    return (*fd = ns_create(d, NS_EVENT, a.manual, a.signaled)) < 0 ? errno : 0;
  }
  case NS_WAIT_ANY:
    return ns_wait(d, arg, 0);
  case NS_WAIT_ALL:
    return ns_wait(d, arg, 1);
  }
  return ENOTTY;
}

int bl_ntsync_ioctl(int fd, unsigned long req, void *arg, int *rc) __attribute__((visibility("hidden")));
int bl_ntsync_ioctl(int fd, unsigned long req, void *arg, int *rc) {
  if (_IOC_TYPE(req) != 'N' || _IOC_NR(req) < 0x80 || _IOC_NR(req) > 0x8d) return 0;
  if (!__atomic_load_n(&ns_ndev, __ATOMIC_ACQUIRE) && !ns_on()) return 0;
  uint64_t e = ns_lookup(fd);
  if (!e) return 0;
  struct ns_dev *d = &ns_devs[(e >> 56) & 7];
  if (!ns_dev_get(d)) return 0;
  int out = 0, err = (e & NS_E_DEVICE) ? ns_dev_ioctl(d, req, arg, &out) : ns_obj_ioctl(d, e, req, arg);
  ns_dev_put(d);
  if (err) {
    errno = err;
    *rc = -1;
  } else {
    *rc = out;
  }
  return 1;
}

int bl_ntsync_open(const char *path, int flags) __attribute__((visibility("hidden")));
int bl_ntsync_open(const char *path, int flags) {
  if (!path || strcmp(path, "/dev/ntsync") != 0 || !ns_on()) return -2;
  int fd = (int)syscall(SYS_openat, AT_FDCWD, path, flags, 0);
  if (fd >= 0) return fd;
  fd = memfd_create("droiddeck-ntsync", (flags & O_CLOEXEC) ? MFD_CLOEXEC : 0);
  if (fd < 0) return -1;
  uint32_t tag = 0;
  for (int tries = 0; tries < 64 && (!tag || ns_find(tag)); tries++) {
    uint16_t r = 0;
    if (getrandom(&r, sizeof(r), GRND_NONBLOCK) != sizeof(r)) r = (uint16_t)(ns_getpid() * 40503u + (uint32_t)tries * 9973u + (uint32_t)time(NULL));
    tag = r;
  }
  struct ns_head *h = MAP_FAILED;
  if (fd >= NS_FDS || ftruncate(fd, (off_t)(NS_DEV_MAGIC | tag)) != 0
      || (h = mmap(NULL, NS_REGION, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0)) == MAP_FAILED) {
    int err = fd >= NS_FDS ? EMFILE : errno;
    ns_close_fd(fd);
    errno = err;
    return -1;
  }
  h->version = NS_VERSION;
  h->tag = tag;
  __atomic_store_n(&h->magic, NS_HEAD_MAGIC, __ATOMIC_RELEASE);
  munmap(h, NS_REGION);
  if (!ns_note_device(fd)) {
    ns_close_fd(fd);
    errno = ENOMEM;
    return -1;
  }
  return fd;
}

static void ns_received(struct msghdr *msg) {
  for (struct cmsghdr *c = CMSG_FIRSTHDR(msg); c; c = CMSG_NXTHDR(msg, c)) {
    if (c->cmsg_level != SOL_SOCKET || c->cmsg_type != SCM_RIGHTS) continue;
    size_t n = (c->cmsg_len - CMSG_LEN(0)) / sizeof(int);
    int *fds = (int *)CMSG_DATA(c);
    for (size_t i = 0; i < n; i++) {
      int fd = fds[i];
      if (fd < 0 || fd >= NS_FDS) continue;
      if (ns_fdt && __atomic_load_n(&ns_fdt[fd], __ATOMIC_ACQUIRE)) ns_forget(fd);
      ns_note_device(fd);
    }
  }
}

int bl_udevmon_recvmsg(int fd, struct msghdr *msg, int flags, ssize_t (*real_recvmsg)(int, struct msghdr *, int),
                       ssize_t *result) __attribute__((visibility("hidden")));

ssize_t recvmsg(int fd, struct msghdr *msg, int flags) {
  static ssize_t (*real)(int, struct msghdr *, int);
  if (!real) real = (ssize_t (*)(int, struct msghdr *, int))dlsym(RTLD_NEXT, "recvmsg");
  ssize_t answered;
  if (msg && bl_udevmon_recvmsg(fd, msg, flags, real, &answered)) return answered;
  ssize_t r = real(fd, msg, flags);
  if (r >= 0 && msg && msg->msg_control && msg->msg_controllen && ns_on()) {
    int saved = errno;
    ns_received(msg);
    errno = saved;
  }
  return r;
}

void bl_fsync_fds_closed(unsigned int first, unsigned int last) __attribute__((visibility("hidden")));

int close(int fd) {
  if (fd >= 0) bl_fsync_fds_closed((unsigned int)fd, (unsigned int)fd);
  uint64_t *t = __atomic_load_n(&ns_fdt, __ATOMIC_ACQUIRE);
  if (t && fd >= 0 && fd < NS_FDS && __atomic_load_n(&t[fd], __ATOMIC_ACQUIRE)) ns_forget(fd);
  return ns_close_fd(fd);
}

int dup2(int oldfd, int newfd) {
  static int (*real)(int, int);
  if (!real) real = (int (*)(int, int))dlsym(RTLD_NEXT, "dup2");
  if (oldfd != newfd && newfd >= 0) bl_fsync_fds_closed((unsigned int)newfd, (unsigned int)newfd);
  uint64_t *t = __atomic_load_n(&ns_fdt, __ATOMIC_ACQUIRE);
  if (t && oldfd != newfd && newfd >= 0 && newfd < NS_FDS && __atomic_load_n(&t[newfd], __ATOMIC_ACQUIRE)) ns_forget(newfd);
  return real(oldfd, newfd);
}

int dup3(int oldfd, int newfd, int flags) {
  static int (*real)(int, int, int);
  if (!real) real = (int (*)(int, int, int))dlsym(RTLD_NEXT, "dup3");
  if (oldfd != newfd && newfd >= 0) bl_fsync_fds_closed((unsigned int)newfd, (unsigned int)newfd);
  uint64_t *t = __atomic_load_n(&ns_fdt, __ATOMIC_ACQUIRE);
  if (t && oldfd != newfd && newfd >= 0 && newfd < NS_FDS && __atomic_load_n(&t[newfd], __ATOMIC_ACQUIRE)) ns_forget(newfd);
  return real(oldfd, newfd, flags);
}

static void ns_forget_range(unsigned int first, unsigned int last) {
  uint64_t *t = __atomic_load_n(&ns_fdt, __ATOMIC_ACQUIRE);
  if (!t) return;
  for (unsigned int fd = first; fd <= last && fd < (unsigned int)NS_FDS; fd++)
    if (__atomic_load_n(&t[fd], __ATOMIC_ACQUIRE)) ns_forget((int)fd);
}

int close_range(unsigned int first, unsigned int last, int flags) {
  static int (*real)(unsigned int, unsigned int, int);
  if (!real) real = (int (*)(unsigned int, unsigned int, int))dlsym(RTLD_NEXT, "close_range");
  if (!(flags & CLOSE_RANGE_CLOEXEC)) {
    bl_fsync_fds_closed(first, last);
    ns_forget_range(first, last);
  }
  if (!real) {
    errno = ENOSYS;
    return -1;
  }
  return real(first, last, flags);
}

void closefrom(int lowfd) {
  static void (*real)(int);
  if (!real) real = (void (*)(int))dlsym(RTLD_NEXT, "closefrom");
  if (lowfd >= 0) {
    bl_fsync_fds_closed((unsigned int)lowfd, ~0u);
    ns_forget_range((unsigned int)lowfd, ~0u);
  }
  if (real) real(lowfd);
}

static void ns_fork_prepare(void) {
  pthread_mutex_lock(&ns_mx);
}

static void ns_fork_parent(void) {
  pthread_mutex_unlock(&ns_mx);
}

static void ns_fork_child(void) {
  pthread_mutex_init(&ns_mx, NULL);
  ns_pid = 0;
  ns_tid = 0;
  ns_fdt = NULL;
  memset(&ns_tq, 0, sizeof(ns_tq));
  for (int i = 0; i < ns_ndev; i++) {
    ns_devs[i].proc = -1;
    ns_devs[i].counts = NULL;
    ns_devs[i].refs = 0;
  }
}

__attribute__((constructor)) static void ns_init(void) {
  pthread_atfork(ns_fork_prepare, ns_fork_parent, ns_fork_child);
}
