/* POSIX write(2), linked against Darling's libSystem at execution time. */
extern long write(int fd, const void *buffer, unsigned long count);

int main(void) {
    static const char message[] = "Dextop macOS ARM64 hello\n";
    return write(1, message, sizeof(message) - 1) == sizeof(message) - 1 ? 0 : 1;
}
