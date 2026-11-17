#define _GNU_SOURCE
#include <errno.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <syslog.h>
#include <time.h>
#include <unistd.h>

static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static const char *log_ident;
static int log_option;
static int log_facility = LOG_USER;
static int log_mask = 0xff;
static int log_fd = -1;

static void disconnect(void) {
    if (log_fd >= 0) close(log_fd);
    log_fd = -1;
}

static int reconnect(void) {
    if (log_fd >= 0) return 0;
    log_fd = socket(AF_UNIX, SOCK_DGRAM | SOCK_CLOEXEC, 0);
    if (log_fd < 0) return -1;
    struct sockaddr_un address;
    memset(&address, 0, sizeof(address));
    address.sun_family = AF_UNIX;
    strcpy(address.sun_path, "/dev/log");
    if (connect(log_fd, (struct sockaddr *) &address, sizeof(address)) != 0) {
        disconnect();
        return -1;
    }
    return 0;
}

void openlog(const char *ident, int option, int facility) {
    pthread_mutex_lock(&lock);
    log_ident = ident;
    log_option = option;
    if (facility != 0) log_facility = facility & LOG_FACMASK;
    if (option & LOG_NDELAY) reconnect();
    pthread_mutex_unlock(&lock);
}

void closelog(void) {
    pthread_mutex_lock(&lock);
    disconnect();
    log_ident = NULL;
    pthread_mutex_unlock(&lock);
}

int setlogmask(int mask) {
    int previous = log_mask;
    if (mask != 0) log_mask = mask;
    return previous;
}

void vsyslog(int priority, const char *format, va_list arguments) {
    if (!(log_mask & LOG_MASK(LOG_PRI(priority)))) return;
    int saved = errno;
    char message[2048];
    char line[2304];
    errno = saved;
    vsnprintf(message, sizeof(message), format, arguments);
    if ((priority & LOG_FACMASK) == 0) priority |= log_facility;
    time_t now = time(NULL);
    struct tm local;
    localtime_r(&now, &local);
    char stamp[32];
    strftime(stamp, sizeof(stamp), "%b %e %H:%M:%S", &local);
    const char *ident = log_ident ? log_ident : program_invocation_short_name;
    int length = (log_option & LOG_PID)
        ? snprintf(line, sizeof(line), "<%d>%s %s[%d]: %s", priority, stamp, ident, (int) getpid(), message)
        : snprintf(line, sizeof(line), "<%d>%s %s: %s", priority, stamp, ident, message);
    if (length < 0) return;
    if (length >= (int) sizeof(line)) length = sizeof(line) - 1;
    pthread_mutex_lock(&lock);
    int sent = reconnect() == 0 && send(log_fd, line, (size_t) length, MSG_NOSIGNAL) >= 0;
    if (!sent) {
        disconnect();
        sent = reconnect() == 0 && send(log_fd, line, (size_t) length, MSG_NOSIGNAL) >= 0;
    }
    pthread_mutex_unlock(&lock);
    if ((!sent && (log_option & LOG_CONS)) || (log_option & LOG_PERROR)) {
        dprintf(STDERR_FILENO, "%s%s: %s\n", ident, "", message);
    }
    errno = saved;
}

void syslog(int priority, const char *format, ...) {
    va_list arguments;
    va_start(arguments, format);
    vsyslog(priority, format, arguments);
    va_end(arguments);
}

void __syslog_chk(int priority, int flag, const char *format, ...) {
    (void) flag;
    va_list arguments;
    va_start(arguments, format);
    vsyslog(priority, format, arguments);
    va_end(arguments);
}

void __vsyslog_chk(int priority, int flag, const char *format, va_list arguments) {
    (void) flag;
    vsyslog(priority, format, arguments);
}

#include <grp.h>
#include <dlfcn.h>

typedef int (*setgroups_fn)(size_t, const gid_t *);
typedef int (*initgroups_fn)(const char *, gid_t);

int setgroups(size_t count, const gid_t *list) {
    static setgroups_fn next;
    if (!next) next = (setgroups_fn) dlsym(RTLD_NEXT, "setgroups");
    int result = next(count, list);
    if (result != 0 && errno == EPERM && geteuid() != 0) return 0;
    return result;
}

int initgroups(const char *user, gid_t group) {
    static initgroups_fn next;
    if (!next) next = (initgroups_fn) dlsym(RTLD_NEXT, "initgroups");
    int result = next(user, group);
    if (result != 0 && errno == EPERM && geteuid() != 0) return 0;
    return result;
}
