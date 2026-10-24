#define _GNU_SOURCE
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

int main(int argc, char **argv) {
    struct sockaddr_un address = { .sun_family = AF_UNIX };
    strcpy(address.sun_path, "/tmp/dextop-global-socket-probe");
    if (argc > 1) {
        int fd = socket(AF_UNIX, SOCK_STREAM, 0);
        if (connect(fd, (struct sockaddr *)&address, sizeof(address))) return 10;
        return write(fd, "ping", 4) == 4 ? 0 : 11;
    }
    unsetenv("DEXTOP_SOCKET_MARKERS");
    unlink(address.sun_path);
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (bind(fd, (struct sockaddr *)&address, sizeof(address)) || listen(fd, 1)) return 2;
    struct stat st;
    if (lstat(address.sun_path, &st) || !S_ISFIFO(st.st_mode)) return 3;
    pid_t child = fork();
    if (!child) { clearenv(); execl(argv[0], argv[0], "client", NULL); _exit(12); }
    if (child < 0) return 4;
    int peer = accept(fd, NULL, NULL);
    char bytes[4];
    if (peer < 0 || read(peer, bytes, 4) != 4 || memcmp(bytes, "ping", 4)) return 5;
    int status;
    waitpid(child, &status, 0);
    close(peer); close(fd); unlink(address.sun_path);
    if (!WIFEXITED(status) || WEXITSTATUS(status)) return 6;
    puts("GLOBAL_SOCKET_DISCOVERY_AND_ENV_CLEARED_CLIENT_OK");
    return 0;
}
