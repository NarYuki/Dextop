/* Own-source ABI probe. No SDK headers or Apple binary payload required. */
extern long write(int, const void *, unsigned long);
extern long read(int, void *, unsigned long);
extern long lseek(int, long, int);
extern int close(int);
extern int unlink(const char *);
extern int mkstemp(char *);
extern int pthread_create(void **, const void *, void *(*)(void *), void *);
extern int pthread_join(void *, void **);
extern int fork(void);
extern int waitpid(int, int *, int);
extern void _exit(int);

static void *worker(void *argument) { return argument; }

int main(void) {
    char path[] = "/tmp/dextop-macos-probe.XXXXXX";
    int fd = mkstemp(path);
    if (fd < 0) return 10;
    unlink(path);
    char message[] = "Dextop";
    char result[sizeof(message)];
    if (write(fd, message, sizeof(message)) != sizeof(message)) return 11;
    if (lseek(fd, 0, 0) != 0) return 12;
    if (read(fd, result, sizeof(result)) != sizeof(result)) return 13;
    for (unsigned i = 0; i < sizeof(result); ++i)
        if (result[i] != message[i]) return 14;
    if (close(fd)) return 15;
    write(1, "file: OK\n", 9);

    void *thread, *value = 0;
    if (pthread_create(&thread, 0, worker, message)) return 20;
    if (pthread_join(thread, &value) || value != message) return 21;
    write(1, "thread: OK\n", 11);

    int pid = fork();
    if (pid < 0) return 30;
    if (!pid) _exit(0);
    int status = -1;
    if (waitpid(pid, &status, 0) != pid || status != 0) return 31;
    write(1, "fork: OK\n", 9);
    return 0;
}
