#define _GNU_SOURCE
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <linux/futex.h>
#include <pthread.h>
#include <signal.h>
#include <stdarg.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ipc.h>
#include <sys/mman.h>
#include <sys/msg.h>
#include <sys/sem.h>
#include <sys/shm.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>

#include "deck_policy.h"

#define DIR_DEFAULT "/dev/shm/wnsysv"
#define SEM_MAGIC 0x574e5345u
#define MAX_OBJECTS 64

struct sem_header {
    uint32_t magic;
    uint32_t nsems;
    uint32_t key;
    uint32_t mode;
    int32_t creator_pid;
    _Atomic uint32_t otime;
    _Atomic uint32_t ctime;
    int32_t pad[9];
};
_Static_assert(sizeof(struct sem_header) == 64, "semaphore files written by older sessions keep their layout");

struct sem_map { int id; struct sem_header *hdr; size_t len; };
struct shm_map { void *addr; size_t len; int id; };

static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static struct sem_map g_sems[MAX_OBJECTS];
static struct shm_map g_shms[MAX_OBJECTS];
static char g_dir[PATH_MAX - NAME_MAX - 2];

static const char *object_dir(void) {
    if (!g_dir[0]) {
        const char *d = getenv("BL_SYSV_DIR");
        snprintf(g_dir, sizeof(g_dir), "%s", d && *d ? d : DIR_DEFAULT);
        mkdir(g_dir, 0700);
    }
    return g_dir;
}

static int object_id(int fd) {
    struct stat st;
    if (fstat(fd, &st) != 0) return -1;
    return (int)(st.st_ino & 0x7fffffff);
}

static int open_object(const char *kind, key_t key, int flags, int *created) {
    char path[PATH_MAX];
    int oflags = O_RDWR | O_CLOEXEC;
    *created = 0;
    if (key == IPC_PRIVATE) {
        static atomic_uint serial;
        snprintf(path, sizeof(path), "%s/%s.p%d.%u", object_dir(), kind, (int)getpid(),
                 atomic_fetch_add(&serial, 1));
        oflags |= O_CREAT | O_EXCL;
    } else {
        snprintf(path, sizeof(path), "%s/%s.k%08x", object_dir(), kind, (unsigned)key);
        if (flags & IPC_CREAT) oflags |= O_CREAT;
        if ((flags & (IPC_CREAT | IPC_EXCL)) == (IPC_CREAT | IPC_EXCL)) oflags |= O_EXCL;
    }
    int fd = open(path, oflags, (flags & 0777) ? (flags & 0777) : 0600);
    if (fd < 0) return -1;
    struct stat st;
    if (fstat(fd, &st) == 0 && st.st_size == 0) *created = 1;
    return fd;
}

static int open_by_id(const char *kind, int id) {
    DIR *d = opendir(object_dir());
    if (!d) return -1;
    struct dirent *e;
    int fd = -1;
    size_t klen = strlen(kind);
    while ((e = readdir(d))) {
        if (strncmp(e->d_name, kind, klen) || e->d_name[klen] != '.') continue;
        if ((int)(e->d_ino & 0x7fffffff) != id) continue;
        char path[PATH_MAX];
        snprintf(path, sizeof(path), "%s/%s", g_dir, e->d_name);
        fd = open(path, O_RDWR | O_CLOEXEC);
        break;
    }
    closedir(d);
    if (fd < 0) errno = EINVAL;
    return fd;
}

static int unlink_by_id(const char *kind, int id) {
    DIR *d = opendir(object_dir());
    if (!d) return -1;
    struct dirent *e;
    int rc = -1;
    size_t klen = strlen(kind);
    while ((e = readdir(d))) {
        if (strncmp(e->d_name, kind, klen) || e->d_name[klen] != '.') continue;
        if ((int)(e->d_ino & 0x7fffffff) != id) continue;
        char path[PATH_MAX];
        snprintf(path, sizeof(path), "%s/%s", g_dir, e->d_name);
        rc = unlink(path);
        break;
    }
    closedir(d);
    if (rc < 0) errno = EINVAL;
    return rc;
}

static long futex(void *addr, int op, int val, const struct timespec *timeout) {
    return syscall(SYS_futex, addr, op, val, timeout, NULL, 0);
}


static struct sem_map *sem_lookup(int id) {
    for (int i = 0; i < MAX_OBJECTS; i++)
        if (g_sems[i].hdr && g_sems[i].id == id) return &g_sems[i];
    int fd = open_by_id("sem", id);
    if (fd < 0) return NULL;
    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size < (off_t)sizeof(struct sem_header)) { close(fd); errno = EINVAL; return NULL; }
    void *m = mmap(NULL, (size_t)st.st_size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    close(fd);
    if (m == MAP_FAILED) return NULL;
    struct sem_header *h = m;
    if (h->magic != SEM_MAGIC) { munmap(m, (size_t)st.st_size); errno = EINVAL; return NULL; }
    for (int i = 0; i < MAX_OBJECTS; i++) {
        if (g_sems[i].hdr) continue;
        g_sems[i].id = id; g_sems[i].hdr = h; g_sems[i].len = (size_t)st.st_size;
        return &g_sems[i];
    }
    munmap(m, (size_t)st.st_size);
    errno = ENOSPC;
    return NULL;
}

static void sem_forget(int id) {
    for (int i = 0; i < MAX_OBJECTS; i++)
        if (g_sems[i].hdr && g_sems[i].id == id) { munmap(g_sems[i].hdr, g_sems[i].len); g_sems[i].hdr = NULL; }
}

static _Atomic int32_t *sem_words(struct sem_header *h) { return (_Atomic int32_t *)(h + 1); }

int semget(key_t key, int nsems, int semflg) {
    if (nsems < 0 || nsems > 250) { errno = EINVAL; return -1; }
    int created;
    int fd = open_object("sem", key, semflg, &created);
    if (fd < 0) return -1;
    if (created) {
        if (nsems == 0) { close(fd); errno = EINVAL; return -1; }
        size_t len = sizeof(struct sem_header) + (size_t)nsems * sizeof(int32_t);
        if (ftruncate(fd, (off_t)len) != 0) { close(fd); return -1; }
        struct sem_header h = {.magic = SEM_MAGIC, .nsems = (uint32_t)nsems, .key = (uint32_t)key,
                               .mode = (uint32_t)(semflg & 0777), .creator_pid = (int32_t)getpid(),
                               .ctime = (uint32_t)time(NULL)};
        if (pwrite(fd, &h, sizeof(h), 0) != (ssize_t)sizeof(h)) { close(fd); return -1; }
    } else {
        struct sem_header h;
        if (pread(fd, &h, sizeof(h), 0) != (ssize_t)sizeof(h) || h.magic != SEM_MAGIC) { close(fd); errno = EINVAL; return -1; }
        if (nsems > 0 && (uint32_t)nsems > h.nsems) { close(fd); errno = EINVAL; return -1; }
    }
    int id = object_id(fd);
    close(fd);
    return id;
}

static int sem_wait_op(_Atomic int32_t *word, short op, short flg, const struct timespec *timeout) {
    for (;;) {
        int32_t v = atomic_load(word);
        if (op == 0) {
            if (v == 0) return 0;
        } else if (v >= -op) {
            if (atomic_compare_exchange_weak(word, &v, v + op)) return 0;
            continue;
        }
        if (flg & IPC_NOWAIT) { errno = EAGAIN; return -1; }
        long r = futex(word, FUTEX_WAIT, v, timeout);
        if (r != 0 && errno == ETIMEDOUT) return -1;
        if (r != 0 && errno == EINTR) { errno = EINTR; return -1; }
    }
}

int semtimedop(int semid, struct sembuf *sops, size_t nsops, const struct timespec *timeout) {
    pthread_mutex_lock(&g_lock);
    struct sem_map *m = sem_lookup(semid);
    pthread_mutex_unlock(&g_lock);
    if (!m) return -1;
    struct sem_header *h = m->hdr;
    _Atomic int32_t *words = sem_words(h);
    for (size_t i = 0; i < nsops; i++) {
        if (sops[i].sem_num >= h->nsems) { errno = EFBIG; return -1; }
        _Atomic int32_t *w = &words[sops[i].sem_num];
        if (sops[i].sem_op > 0) {
            atomic_fetch_add(w, sops[i].sem_op);
            futex(w, FUTEX_WAKE, INT_MAX, NULL);
        } else if (sem_wait_op(w, sops[i].sem_op, sops[i].sem_flg, timeout) != 0) {
            return -1;
        }
        if (sops[i].sem_op < 0) futex(w, FUTEX_WAKE, INT_MAX, NULL);
    }
    atomic_store(&h->otime, (uint32_t)time(NULL));
    return 0;
}

int semop(int semid, struct sembuf *sops, size_t nsops) { return semtimedop(semid, sops, nsops, NULL); }

int semctl(int semid, int semnum, int cmd, ...) {
    union { int val; struct semid_ds *buf; unsigned short *array; } arg = {0};
    if (cmd == SETVAL || cmd == IPC_STAT || cmd == IPC_SET || cmd == GETALL || cmd == SETALL) {
        va_list ap;
        va_start(ap, cmd);
        arg.buf = va_arg(ap, struct semid_ds *);
        va_end(ap);
    }
    if (cmd == IPC_RMID) {
        pthread_mutex_lock(&g_lock);
        sem_forget(semid);
        int rc = unlink_by_id("sem", semid);
        pthread_mutex_unlock(&g_lock);
        return rc;
    }
    pthread_mutex_lock(&g_lock);
    struct sem_map *m = sem_lookup(semid);
    pthread_mutex_unlock(&g_lock);
    if (!m) return -1;
    struct sem_header *h = m->hdr;
    _Atomic int32_t *words = sem_words(h);
    switch (cmd) {
    case GETVAL:
        if (semnum < 0 || (uint32_t)semnum >= h->nsems) { errno = EINVAL; return -1; }
        return atomic_load(&words[semnum]);
    case SETVAL:
        if (semnum < 0 || (uint32_t)semnum >= h->nsems) { errno = EINVAL; return -1; }
        atomic_store(&words[semnum], (int32_t)(intptr_t)arg.buf);
        futex(&words[semnum], FUTEX_WAKE, INT_MAX, NULL);
        atomic_store(&h->ctime, (uint32_t)time(NULL));
        return 0;
    case GETALL:
        for (uint32_t i = 0; i < h->nsems; i++) arg.array[i] = (unsigned short)atomic_load(&words[i]);
        return 0;
    case SETALL:
        for (uint32_t i = 0; i < h->nsems; i++) {
            atomic_store(&words[i], arg.array[i]);
            futex(&words[i], FUTEX_WAKE, INT_MAX, NULL);
        }
        atomic_store(&h->ctime, (uint32_t)time(NULL));
        return 0;
    case GETPID: return h->creator_pid;
    case GETNCNT: case GETZCNT: return 0;
    case IPC_STAT:
        memset(arg.buf, 0, sizeof(*arg.buf));
        arg.buf->sem_perm.__key = (key_t)h->key;
        arg.buf->sem_perm.uid = arg.buf->sem_perm.cuid = getuid();
        arg.buf->sem_perm.gid = arg.buf->sem_perm.cgid = getgid();
        arg.buf->sem_perm.mode = (unsigned short)h->mode;
        arg.buf->sem_nsems = h->nsems;
        arg.buf->sem_otime = (time_t)atomic_load(&h->otime);
        arg.buf->sem_ctime = (time_t)atomic_load(&h->ctime);
        return 0;
    case IPC_SET: atomic_store(&h->ctime, (uint32_t)time(NULL)); return 0;
    default: errno = EINVAL; return -1;
    }
}


static int remove_shm(int id) {
    DIR *d = opendir(object_dir());
    if (!d) return -1;
    struct dirent *e;
    int rc = -1;
    while ((e = readdir(d))) {
        if (strncmp(e->d_name, "shm.", 4) || !strncmp(e->d_name, "shm.d.", 6)) continue;
        if ((int)(e->d_ino & 0x7fffffff) != id) continue;
        char from[PATH_MAX], to[PATH_MAX];
        snprintf(from, sizeof(from), "%s/%s", g_dir, e->d_name);
        snprintf(to, sizeof(to), "%s/shm.d.%s", g_dir, e->d_name + 4);
        rc = rename(from, to);
        break;
    }
    closedir(d);
    if (rc < 0) errno = EINVAL;
    return rc;
}

static void expire_removed_shm(void) {
    DIR *d = opendir(object_dir());
    if (!d) return;
    struct dirent *e;
    time_t now = time(NULL);
    while ((e = readdir(d))) {
        if (strncmp(e->d_name, "shm.d.", 6)) continue;
        char path[PATH_MAX];
        struct stat st;
        snprintf(path, sizeof(path), "%s/%s", g_dir, e->d_name);
        if (stat(path, &st) == 0 && now - st.st_mtime > 30) unlink(path);
    }
    closedir(d);
}

int shmget(key_t key, size_t size, int shmflg) {
    int created;
    expire_removed_shm();
    int fd = open_object("shm", key, shmflg, &created);
    if (fd < 0) return -1;
    if (created) {
        if (size == 0) { close(fd); errno = EINVAL; return -1; }
        if (ftruncate(fd, (off_t)size) != 0) { close(fd); return -1; }
    } else {
        struct stat st;
        if (fstat(fd, &st) != 0 || (size_t)st.st_size < size) { close(fd); errno = EINVAL; return -1; }
    }
    int id = object_id(fd);
    close(fd);
    return id;
}

void *shmat(int shmid, const void *shmaddr, int shmflg) {
    int fd = open_by_id("shm", shmid);
    if (fd < 0) return (void *)-1;
    struct stat st;
    if (fstat(fd, &st) != 0) { close(fd); return (void *)-1; }
    int prot = PROT_READ | ((shmflg & SHM_RDONLY) ? 0 : PROT_WRITE);
    void *m = mmap((void *)shmaddr, (size_t)st.st_size, prot, MAP_SHARED, fd, 0);
    close(fd);
    if (m == MAP_FAILED) return (void *)-1;
    pthread_mutex_lock(&g_lock);
    for (int i = 0; i < MAX_OBJECTS; i++) {
        if (g_shms[i].addr) continue;
        g_shms[i].addr = m; g_shms[i].len = (size_t)st.st_size; g_shms[i].id = shmid;
        break;
    }
    pthread_mutex_unlock(&g_lock);
    return m;
}

int shmdt(const void *shmaddr) {
    pthread_mutex_lock(&g_lock);
    for (int i = 0; i < MAX_OBJECTS; i++) {
        if (g_shms[i].addr != shmaddr) continue;
        munmap(g_shms[i].addr, g_shms[i].len);
        g_shms[i].addr = NULL;
        pthread_mutex_unlock(&g_lock);
        return 0;
    }
    pthread_mutex_unlock(&g_lock);
    errno = EINVAL;
    return -1;
}

int shmctl(int shmid, int cmd, struct shmid_ds *buf) {
    switch (cmd) {
    case IPC_RMID: {
        pthread_mutex_lock(&g_lock);
        int rc = remove_shm(shmid);
        pthread_mutex_unlock(&g_lock);
        return rc;
    }
    case IPC_STAT: {
        int fd = open_by_id("shm", shmid);
        if (fd < 0) return -1;
        struct stat st;
        int rc = fstat(fd, &st);
        close(fd);
        if (rc != 0) return -1;
        memset(buf, 0, sizeof(*buf));
        buf->shm_perm.uid = buf->shm_perm.cuid = getuid();
        buf->shm_perm.gid = buf->shm_perm.cgid = getgid();
        buf->shm_perm.mode = 0600;
        buf->shm_segsz = (size_t)st.st_size;
        buf->shm_nattch = 1;
        buf->shm_cpid = (pid_t)getpid();
        return 0;
    }
    case IPC_SET: case SHM_LOCK: case SHM_UNLOCK: return 0;
    default: errno = EINVAL; return -1;
    }
}




#define MSG_MAGIC 0x574e4d51u
#define MSG_SLOTS 32

struct msg_queue {
    uint32_t magic;
    _Atomic int32_t lock;
    _Atomic int32_t changed;
    uint64_t next_seq;
    struct { uint64_t seq; long mtype; size_t len; char data[2048]; } slot[MSG_SLOTS];
};

static struct msg_queue *msg_map(int id) {
    int fd = open_by_id("msg", id);
    if (fd < 0) return NULL;
    struct msg_queue *q = mmap(NULL, sizeof(*q), PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    close(fd);
    if (q == MAP_FAILED) return NULL;
    if (q->magic == MSG_MAGIC) return q;
    munmap(q, sizeof(*q));
    errno = EINVAL;
    return NULL;
}

struct msg_map { int id; int refs; struct msg_queue *q; };
static struct msg_map g_msgs[MAX_OBJECTS];

static int msg_removed(struct msg_queue *q) { return __atomic_load_n(&q->magic, __ATOMIC_ACQUIRE) != MSG_MAGIC; }

static struct msg_map *msg_get(int id) {
    struct msg_map *found = NULL, *spare = NULL, *idle = NULL;
    pthread_mutex_lock(&g_lock);
    for (int i = 0; i < MAX_OBJECTS; i++) {
        struct msg_map *e = &g_msgs[i];
        if (e->q && !e->refs && msg_removed(e->q)) {
            munmap(e->q, sizeof(*e->q));
            e->q = NULL;
        }
        if (!e->q) {
            if (!spare) spare = e;
        } else if (e->id == id && !msg_removed(e->q)) {
            found = e;
        } else if (!e->refs && !idle) {
            idle = e;
        }
    }
    if (!found && !spare && idle) {
        munmap(idle->q, sizeof(*idle->q));
        idle->q = NULL;
        spare = idle;
    }
    if (!found && spare && (spare->q = msg_map(id))) {
        spare->id = id;
        found = spare;
    } else if (!found && !spare) {
        errno = ENOSPC;
    }
    if (found) found->refs++;
    pthread_mutex_unlock(&g_lock);
    return found;
}

static void msg_put(struct msg_map *m) {
    pthread_mutex_lock(&g_lock);
    m->refs--;
    pthread_mutex_unlock(&g_lock);
}

static void msg_lock(struct msg_queue *q) {
    for (int32_t owner = 0; !atomic_compare_exchange_strong(&q->lock, &owner, getpid()); owner = 0) {
        if (kill(owner, 0) != 0 && errno == ESRCH) atomic_compare_exchange_strong(&q->lock, &owner, 0);
        else futex(&q->lock, FUTEX_WAIT, owner, &(struct timespec){0, 20000000});
    }
}

static void msg_unlock(struct msg_queue *q, int changed) {
    atomic_store(&q->lock, 0);
    futex(&q->lock, FUTEX_WAKE, 1, NULL);
    if (changed) {
        atomic_fetch_add(&q->changed, 1);
        futex(&q->changed, FUTEX_WAKE, INT_MAX, NULL);
    }
}

int msgget(key_t key, int msgflg) {
    int created;
    int fd = open_object("msg", key, msgflg, &created);
    if (fd < 0) return -1;
    uint32_t magic = MSG_MAGIC;
    if (created && (ftruncate(fd, sizeof(struct msg_queue)) != 0 || pwrite(fd, &magic, sizeof(magic), 0) != sizeof(magic))) {
        close(fd);
        return -1;
    }
    int id = object_id(fd);
    close(fd);
    return id;
}

static int deck_msgsnd(int msqid, const void *msgp, size_t msgsz, int msgflg) {
    struct msg_map *m = msg_get(msqid);
    if (!m) return -1;
    struct msg_queue *q = m->q;
    if (msgsz > sizeof(q->slot[0].data) || *(const long *)msgp < 1) { msg_put(m); errno = EINVAL; return -1; }
    for (;;) {
        msg_lock(q);
        int32_t seen = atomic_load(&q->changed);
        if (msg_removed(q)) {
            msg_unlock(q, 0);
            msg_put(m);
            errno = EIDRM;
            return -1;
        }
        for (int i = 0; i < MSG_SLOTS; i++) {
            if (q->slot[i].seq) continue;
            q->slot[i].mtype = *(const long *)msgp;
            q->slot[i].len = msgsz;
            memcpy(q->slot[i].data, (const long *)msgp + 1, msgsz);
            q->slot[i].seq = ++q->next_seq;
            msg_unlock(q, 1);
            msg_put(m);
            return 0;
        }
        msg_unlock(q, 0);
        if ((msgflg & IPC_NOWAIT) || (futex(&q->changed, FUTEX_WAIT, seen, NULL) != 0 && errno == EINTR)) {
            msg_put(m);
            if (msgflg & IPC_NOWAIT) errno = EAGAIN;
            return -1;
        }
    }
}

static ssize_t deck_msgrcv(int msqid, void *msgp, size_t msgsz, long msgtyp, int msgflg) {
    if (msgtyp < 0) { errno = EINVAL; return -1; }
    struct msg_map *m = msg_get(msqid);
    if (!m) return -1;
    struct msg_queue *q = m->q;
    for (;;) {
        msg_lock(q);
        int32_t seen = atomic_load(&q->changed);
        if (msg_removed(q)) {
            msg_unlock(q, 0);
            msg_put(m);
            errno = EIDRM;
            return -1;
        }
        int best = -1;
        for (int i = 0; i < MSG_SLOTS; i++)
            if (q->slot[i].seq && (!msgtyp || q->slot[i].mtype == msgtyp) && (best < 0 || q->slot[i].seq < q->slot[best].seq))
                best = i;
        if (best >= 0) {
            size_t n = q->slot[best].len < msgsz ? q->slot[best].len : msgsz;
            *(long *)msgp = q->slot[best].mtype;
            memcpy((long *)msgp + 1, q->slot[best].data, n);
            q->slot[best].seq = 0;
            msg_unlock(q, 1);
            msg_put(m);
            return (ssize_t)n;
        }
        msg_unlock(q, 0);
        if ((msgflg & IPC_NOWAIT) || (futex(&q->changed, FUTEX_WAIT, seen, NULL) != 0 && errno == EINTR)) {
            msg_put(m);
            if (msgflg & IPC_NOWAIT) errno = ENOMSG;
            return -1;
        }
    }
}

static int deck_msgctl(int msqid, int cmd, struct msqid_ds *buf) {
    if (cmd != IPC_RMID) { errno = EINVAL; return -1; }
    struct msg_queue *q = msg_map(msqid);
    if (!q) return -1;
    pthread_mutex_lock(&g_lock);
    int rc = unlink_by_id("msg", msqid);
    pthread_mutex_unlock(&g_lock);
    int saved = errno;
    if (rc == 0) {
        __atomic_store_n(&q->magic, 0, __ATOMIC_SEQ_CST);
        atomic_fetch_add(&q->changed, 1);
        futex(&q->changed, FUTEX_WAKE, INT_MAX, NULL);
    }
    munmap(q, sizeof(*q));
    errno = saved;
    return rc;
}

int msgsnd(int msqid, const void *msgp, size_t msgsz, int msgflg) {
    if (dextop_deck_runtime()) return deck_msgsnd(msqid, msgp, msgsz, msgflg);
    struct msg_queue *q = msg_map(msqid);
    if (!q) return -1;
    if (msgsz > sizeof(q->slot[0].data) || *(const long *)msgp < 1) { munmap(q, sizeof(*q)); errno = EINVAL; return -1; }
    for (;;) {
        msg_lock(q);
        int32_t seen = atomic_load(&q->changed);
        for (int i = 0; i < MSG_SLOTS; i++) {
            if (q->slot[i].seq) continue;
            q->slot[i].mtype = *(const long *)msgp;
            q->slot[i].len = msgsz;
            memcpy(q->slot[i].data, (const long *)msgp + 1, msgsz);
            q->slot[i].seq = ++q->next_seq;
            msg_unlock(q, 1);
            munmap(q, sizeof(*q));
            return 0;
        }
        msg_unlock(q, 0);
        if ((msgflg & IPC_NOWAIT) || (futex(&q->changed, FUTEX_WAIT, seen, NULL) != 0 && errno == EINTR)) {
            munmap(q, sizeof(*q));
            if (msgflg & IPC_NOWAIT) errno = EAGAIN;
            return -1;
        }
    }
}

ssize_t msgrcv(int msqid, void *msgp, size_t msgsz, long msgtyp, int msgflg) {
    if (dextop_deck_runtime()) return deck_msgrcv(msqid, msgp, msgsz, msgtyp, msgflg);
    if (msgtyp < 0) { errno = EINVAL; return -1; }
    struct msg_queue *q = msg_map(msqid);
    if (!q) return -1;
    for (;;) {
        msg_lock(q);
        int32_t seen = atomic_load(&q->changed);
        int best = -1;
        for (int i = 0; i < MSG_SLOTS; i++)
            if (q->slot[i].seq && (!msgtyp || q->slot[i].mtype == msgtyp) && (best < 0 || q->slot[i].seq < q->slot[best].seq))
                best = i;
        if (best >= 0) {
            size_t n = q->slot[best].len < msgsz ? q->slot[best].len : msgsz;
            *(long *)msgp = q->slot[best].mtype;
            memcpy((long *)msgp + 1, q->slot[best].data, n);
            q->slot[best].seq = 0;
            msg_unlock(q, 1);
            munmap(q, sizeof(*q));
            return (ssize_t)n;
        }
        msg_unlock(q, 0);
        if ((msgflg & IPC_NOWAIT) || (futex(&q->changed, FUTEX_WAIT, seen, NULL) != 0 && errno == EINTR)) {
            munmap(q, sizeof(*q));
            if (msgflg & IPC_NOWAIT) errno = ENOMSG;
            return -1;
        }
    }
}

int msgctl(int msqid, int cmd, struct msqid_ds *buf) {
    if (dextop_deck_runtime()) return deck_msgctl(msqid, cmd, buf);
    if (cmd != IPC_RMID) { errno = EINVAL; return -1; }
    pthread_mutex_lock(&g_lock);
    int rc = unlink_by_id("msg", msqid);
    pthread_mutex_unlock(&g_lock);
    return rc;
}

static void sysv_lock_before_fork(void) { pthread_mutex_lock(&g_lock); }
static void sysv_unlock_after_fork(void) { pthread_mutex_unlock(&g_lock); }
static void sysv_reset_after_fork(void) {
    pthread_mutex_init(&g_lock, NULL);
    for (int i = 0; i < MAX_OBJECTS; i++) g_msgs[i].refs = 0;
}

__attribute__((constructor)) static void install_sysv_fork_handlers(void) {
  pthread_atfork(sysv_lock_before_fork, sysv_unlock_after_fork, sysv_reset_after_fork);
}
