#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <linux/openat2.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/msg.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>

#define CHECK(x) do { if (!(x)) { fprintf(stderr, "FAIL line=%d errno=%d\n", __LINE__, errno); exit(1); } } while (0)
struct packet { long type; unsigned value; };
static int queue;
static void *blocked_receive(void *unused) {
    struct packet p;
    errno = 0;
    ssize_t result = msgrcv(queue, &p, sizeof(p.value), 9, 0);
    CHECK(result == -1 && errno == EIDRM);
    return NULL;
}

static void messages(int deck) {
    struct packet p = {.type = 1};
    queue = msgget(IPC_PRIVATE, 0600);
    CHECK(queue >= 0);
    for (unsigned i = 0; i < 2000; i++) {
        p.value = i;
        CHECK(msgsnd(queue, &p, sizeof(p.value), 0) == 0);
        p.value = ~i;
        CHECK(msgrcv(queue, &p, sizeof(p.value), 1, 0) == sizeof(p.value) && p.value == i);
    }
    pid_t child = fork();
    CHECK(child >= 0);
    if (!child) {
        p.type = 3; p.value = 0x12345678;
        _exit(msgsnd(queue, &p, sizeof(p.value), 0) == 0 ? 0 : 2);
    }
    CHECK(msgrcv(queue, &p, sizeof(p.value), 3, 0) == sizeof(p.value) && p.value == 0x12345678);
    int status;
    CHECK(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 0);
    if (deck) {
        pthread_t thread;
        CHECK(pthread_create(&thread, NULL, blocked_receive, NULL) == 0);
        usleep(100000);
        CHECK(msgctl(queue, IPC_RMID, NULL) == 0);
        CHECK(pthread_join(thread, NULL) == 0);
        errno = 0;
        CHECK(msgsnd(queue, &p, sizeof(p.value), IPC_NOWAIT) == -1);
    } else CHECK(msgctl(queue, IPC_RMID, NULL) == 0);
    for (int i = 0; i < 96; i++) {
        int id = msgget(IPC_PRIVATE, 0600);
        CHECK(id >= 0 && msgsnd(id, &p, sizeof(p.value), 0) == 0);
        CHECK(msgrcv(id, &p, sizeof(p.value), 0, 0) == sizeof(p.value));
        CHECK(msgctl(id, IPC_RMID, NULL) == 0);
    }
    puts(deck ? "DECK_QUEUE_REUSE_FORK_REMOVE_EVICT_OK" : "NORMAL_QUEUE_ROUNDTRIP_FORK_OK");
}

static void paths(int deck) {
    char dir[] = "/tmp/dextop-deck-openat-XXXXXX";
    CHECK(mkdtemp(dir));
    int root = open(dir, O_PATH | O_DIRECTORY | O_CLOEXEC);
    CHECK(root >= 0);
    int fd = openat(root, "payload", O_WRONLY | O_CREAT | O_EXCL, 0600);
    CHECK(fd >= 0 && write(fd, "test", 4) == 4); close(fd);
    CHECK(symlinkat("/payload", root, "absolute") == 0);
    struct open_how how = {.flags = O_RDONLY, .resolve = RESOLVE_IN_ROOT};
    if (deck) {
        fd = syscall(SYS_openat2, root, "/absolute", &how, sizeof(how));
        char text[4];
        CHECK(fd >= 0 && read(fd, text, sizeof(text)) == 4 && !memcmp(text, "test", 4)); close(fd);
        fd = syscall(SYS_openat2, root, "../../payload", &how, sizeof(how));
        CHECK(fd >= 0); close(fd);
        how.flags |= O_NOFOLLOW;
        errno = 0;
        CHECK(syscall(SYS_openat2, root, "absolute", &how, sizeof(how)) == -1 && errno == ELOOP);
        how.flags = O_RDONLY; how.resolve = RESOLVE_BENEATH;
        errno = 0;
        CHECK(syscall(SYS_openat2, root, "/payload", &how, sizeof(how)) == -1 && errno == EXDEV);
    }
    fd = syscall(SYS_openat, root, "payload", O_RDONLY, 0);
    CHECK(fd >= 0); close(fd);
    CHECK(unlinkat(root, "absolute", 0) == 0 && unlinkat(root, "payload", 0) == 0);
    close(root); CHECK(rmdir(dir) == 0);
    puts(deck ? "DECK_OPENAT2_IN_ROOT_SYMLINK_NOFOLLOW_OK" : "NORMAL_OPENAT_UNCHANGED_OK");
}

int main(void) {
    alarm(20);
    int deck = getenv("DEXTOP_STEAM_INSIDE") && !strcmp(getenv("DEXTOP_STEAM_INSIDE"), "1");
    messages(deck); paths(deck);
    puts("DECK_RUNTIME_PROBE_COMPLETE");
    return 0;
}
