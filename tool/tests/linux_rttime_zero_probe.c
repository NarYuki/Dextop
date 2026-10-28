#define _GNU_SOURCE
#include <sched.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/resource.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

static void tick(int sig) { (void)sig; }

static void run(int zero) {
    struct rlimit limit = { zero ? 0 : RLIM_INFINITY, zero ? 0 : RLIM_INFINITY };
    if (setrlimit(RLIMIT_RTTIME, &limit)) _exit(10);
    printf("child zero=%d scheduling_policy=%d\n", zero, sched_getscheduler(0));
    fflush(stdout);
    signal(SIGUSR1, tick);
    timer_t timer;
    struct sigevent event = { .sigev_notify = SIGEV_SIGNAL, .sigev_signo = SIGUSR1 };
    if (timer_create(CLOCK_THREAD_CPUTIME_ID, &event, &timer)) _exit(11);
    struct itimerspec spec = { .it_interval = { 0, 1000000 }, .it_value = { 0, 1000000 } };
    if (timer_settime(timer, 0, &spec, NULL)) _exit(12);
    struct timespec begin, now;
    clock_gettime(CLOCK_MONOTONIC, &begin);
    do {
        for (volatile int i = 0; i < 100000; ++i) { }
        clock_gettime(CLOCK_MONOTONIC, &now);
    } while ((now.tv_sec - begin.tv_sec) * 1000000000LL + now.tv_nsec - begin.tv_nsec < 200000000LL);
    _exit(0);
}

int main(void) {
    for (int zero = 0; zero <= 1; ++zero) {
        pid_t pid = fork();
        if (pid < 0) return 1;
        if (!pid) run(zero);
        int status;
        if (waitpid(pid, &status, 0) < 0) return 2;
        printf("result zero=%d exit=%d signal=%d\n", zero,
               WIFEXITED(status) ? WEXITSTATUS(status) : -1,
               WIFSIGNALED(status) ? WTERMSIG(status) : 0);
        fflush(stdout);
    }
    return 0;
}
