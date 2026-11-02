/* Darwin's public AF_UNIX ABI, exercised through Darling's ELF bridge. */
extern int socket(int, int, int);
extern int bind(int, const void *, unsigned);
extern int listen(int, int);
extern int connect(int, const void *, unsigned);
extern int accept(int, void *, unsigned *);
extern int close(int);
extern int unlink(const char *);
extern int mkstemp(char *);
extern long write(int, const void *, unsigned long);
extern long read(int, void *, unsigned long);

int main(void) {
    struct {
        unsigned char length, family;
        char path[104];
    } address = {106, 1, "/tmp/dextop-mac-sock.XXXXXX"};
    int temporary = mkstemp(address.path);
    if (temporary < 0) return 40;
    close(temporary);
    unlink(address.path);
    int server = socket(1, 1, 0);
    if (server < 0) return 41;
    if (bind(server, &address, sizeof(address))) return 42;
    if (listen(server, 1)) return 43;
    int client = socket(1, 1, 0);
    if (client < 0) return 44;
    if (connect(client, &address, sizeof(address))) return 45;
    int peer = accept(server, 0, 0);
    if (peer < 0) return 46;
    char bytes[] = "RPC", received[sizeof(bytes)];
    if (write(client, bytes, sizeof(bytes)) != sizeof(bytes)) return 47;
    if (read(peer, received, sizeof(received)) != sizeof(received)) return 48;
    for (unsigned i = 0; i < sizeof(bytes); ++i)
        if (received[i] != bytes[i]) return 49;
    close(peer);
    close(client);
    close(server);
    unlink(address.path);
    write(1, "socket: OK\n", 11);
    return 0;
}
