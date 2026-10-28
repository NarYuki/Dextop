// Pseudo-terminals for the Linux terminal: the shell-side service starts a container shell on
// a new PTY and hands the master side to the app, which resizes it as its window changes.

#include <jni.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

#include <string>
#include <vector>

namespace {

std::vector<std::string> strings(JNIEnv *env, jobjectArray array) {
    std::vector<std::string> result;
    if (array == nullptr) return result;
    const jsize count = env->GetArrayLength(array);
    for (jsize i = 0; i < count; ++i) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(array, i));
        const char *chars = env->GetStringUTFChars(value, nullptr);
        result.emplace_back(chars);
        env->ReleaseStringUTFChars(value, chars);
        env->DeleteLocalRef(value);
    }
    return result;
}

std::vector<char *> pointers(std::vector<std::string> &values) {
    std::vector<char *> result;
    for (auto &value : values) result.push_back(value.data());
    result.push_back(nullptr);
    return result;
}

}  // namespace

/** Binary stdout pipe; no terminal processing, no TCP endpoint or extra PCM copy. */
extern "C" JNIEXPORT jint JNICALL
Java_moe_n4tsu_dextop_linux_LinuxPty_nativeSpawnPipe(
        JNIEnv *env, jclass, jobjectArray argv, jobjectArray envp, jintArray pidOut) {
    auto arguments = strings(env, argv);
    auto environment = strings(env, envp);
    if (arguments.empty()) return -EINVAL;
    auto args = pointers(arguments);
    auto vars = pointers(environment);
    int stream[2];
    if (pipe2(stream, O_CLOEXEC) != 0) return -errno;
    const pid_t pid = fork();
    if (pid < 0) { int error = errno; close(stream[0]); close(stream[1]); return -error; }
    if (pid == 0) {
        sigset_t none; sigemptyset(&none); sigprocmask(SIG_SETMASK, &none, nullptr);
        for (int signal = 1; signal < NSIG; ++signal) ::signal(signal, SIG_DFL);
        setsid();
        const int silent = open("/dev/null", O_RDWR);
        if (silent < 0 || dup2(silent, 0) < 0 || dup2(stream[1], 1) < 0 || dup2(silent, 2) < 0) _exit(126);
        if (DIR *fds = opendir("/proc/self/fd")) {
            int own = dirfd(fds);
            while (dirent *entry = readdir(fds)) {
                int fd = atoi(entry->d_name);
                if (fd > 2 && fd != own) close(fd);
            }
            closedir(fds);
        }
        chdir("/");
        execve(args[0], args.data(), vars.data());
        _exit(127);
    }
    close(stream[1]);
    jint value = pid; env->SetIntArrayRegion(pidOut, 0, 1, &value);
    return stream[0];
}

/** Starts argv on a new PTY of rows x cols; returns the master fd (or -errno) and the pid in pidOut[0]. */
extern "C" JNIEXPORT jint JNICALL
Java_moe_n4tsu_dextop_linux_LinuxPty_nativeSpawn(
        JNIEnv *env, jclass, jobjectArray argv, jobjectArray envp, jstring cwd,
        jint rows, jint cols, jintArray pidOut) {
    auto arguments = strings(env, argv);
    auto environment = strings(env, envp);
    if (arguments.empty()) return -EINVAL;
    const char *cwdChars = env->GetStringUTFChars(cwd, nullptr);
    std::string directory(cwdChars);
    env->ReleaseStringUTFChars(cwd, cwdChars);
    // Everything the child needs is prepared before fork: it must not allocate.
    auto argPointers = pointers(arguments);
    auto envPointers = pointers(environment);

    const int master = posix_openpt(O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (master < 0) return -errno;
    char name[64];
    if (grantpt(master) != 0 || unlockpt(master) != 0 || ptsname_r(master, name, sizeof name) != 0) {
        const int error = errno;
        close(master);
        return -error;
    }
    termios settings{};
    if (tcgetattr(master, &settings) == 0) {
        settings.c_iflag |= IUTF8;
        settings.c_iflag &= ~(IXON | IXOFF);
        tcsetattr(master, TCSANOW, &settings);
    }
    winsize size{static_cast<unsigned short>(rows), static_cast<unsigned short>(cols), 0, 0};
    ioctl(master, TIOCSWINSZ, &size);

    const pid_t pid = fork();
    if (pid < 0) {
        const int error = errno;
        close(master);
        return -error;
    }
    if (pid == 0) {
        // The JVM blocks and handles signals the shell expects at their defaults.
        sigset_t none;
        sigemptyset(&none);
        sigprocmask(SIG_SETMASK, &none, nullptr);
        for (int signal = 1; signal < NSIG; ++signal) ::signal(signal, SIG_DFL);
        close(master);
        setsid();
        const int slave = open(name, O_RDWR);
        if (slave < 0) _exit(126);
        ioctl(slave, TIOCSCTTY, 0);
        dup2(slave, 0);
        dup2(slave, 1);
        dup2(slave, 2);
        if (slave > 2) close(slave);
        if (DIR *fds = opendir("/proc/self/fd")) {
            const int own = dirfd(fds);
            while (dirent *entry = readdir(fds)) {
                const int fd = atoi(entry->d_name);
                if (fd > 2 && fd != own) close(fd);
            }
            closedir(fds);
        }
        if (chdir(directory.c_str()) != 0) chdir("/");
        execve(argPointers[0], argPointers.data(), envPointers.data());
        _exit(127);
    }
    jint value = pid;
    env->SetIntArrayRegion(pidOut, 0, 1, &value);
    return master;
}

/** Waits for pid to end; returns its exit code (128 + signal when killed). */
extern "C" JNIEXPORT jint JNICALL
Java_moe_n4tsu_dextop_linux_LinuxPty_nativeWaitFor(JNIEnv *, jclass, jint pid) {
    int status = 0;
    while (waitpid(pid, &status, 0) < 0) {
        if (errno != EINTR) return -1;
    }
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return 0;
}

/** Tells the terminal's programs its new size (they get SIGWINCH). */
extern "C" JNIEXPORT jboolean JNICALL
Java_moe_n4tsu_dextop_linux_LinuxPty_nativeResize(JNIEnv *, jclass, jint fd, jint rows, jint cols) {
    winsize size{static_cast<unsigned short>(rows), static_cast<unsigned short>(cols), 0, 0};
    return ioctl(fd, TIOCSWINSZ, &size) == 0;
}
