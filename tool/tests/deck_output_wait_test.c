#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <vulkan/vulkan_core.h>

#define DECK_OUTPUT_SLOTS 3
#define POLLIN 1
#define POLLERR 8
struct pollfd { int fd; short events, revents; };
static int states[16], closed[16], imports, imported, import_failure, lock_depth, errors;
static int poll(struct pollfd *p, unsigned n, int timeout) {
    assert(n == 1 && timeout == 0);
    if (states[p->fd] < 0) { p->revents = POLLERR; return 1; }
    p->revents = states[p->fd] ? POLLIN : 0;
    return states[p->fd];
}
static int close(int fd) { closed[fd]++; return 0; }
static void pthread_mutex_lock(int *lock) { assert(!lock_depth); lock_depth++; }
static void pthread_mutex_unlock(int *lock) { assert(lock_depth == 1); lock_depth--; }
static void error(const char *message, VkResult result) { errors++; }
static VKAPI_ATTR VkResult VKAPI_CALL import_fd(VkDevice device, const VkImportSemaphoreFdInfoKHR *info) {
    assert(lock_depth == 0);
    assert(info->flags == VK_SEMAPHORE_IMPORT_TEMPORARY_BIT);
    assert(info->handleType == VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT);
    assert(info->semaphore == (VkSemaphore)(uintptr_t)7);
    imports++; imported = info->fd;
    return import_failure ? VK_ERROR_INVALID_EXTERNAL_HANDLE : VK_SUCCESS;
}
struct linux_gpu { VkDevice device; PFN_vkImportSemaphoreFdKHR ImportSemaphoreFdKHR; };
struct deck_output { int lock; bool busy[3]; int release_fds[3]; };
struct linux_gpu_window { struct linux_gpu *gpu; struct deck_output *deck; VkSemaphore acquired; };
#include "../../android/app/src/main/cpp/wayland/deck_slot_wait.inc"

int main(void) {
    struct linux_gpu gpu = {.ImportSemaphoreFdKHR = import_fd};
    struct deck_output d = {.release_fds = {3,4,5}};
    struct linux_gpu_window w = {.gpu = &gpu, .deck = &d, .acquired = (VkSemaphore)(uintptr_t)7};
    uint32_t index = 99; bool wait = true;
    states[4] = 1;
    assert(deck_output_take(&w,&index,&wait) == 1 && index == 1 && !wait);
    assert(closed[4] == 1 && !imports && d.release_fds[0] == 3 && d.release_fds[1] == -1 && d.busy[1]);
    d.busy[2] = true;
    assert(deck_output_take(&w,&index,&wait) == 1 && index == 0 && wait);
    assert(imports == 1 && imported == 3 && !closed[3] && d.release_fds[0] == -1 && d.busy[0]);
    assert(deck_output_take(&w,&index,&wait) == 0 && !wait && imports == 1);
    d.busy[2] = false; states[5] = -1;
    assert(deck_output_take(&w,&index,&wait) == 0 && !closed[5]);
    d.release_fds[2] = -1;
    assert(deck_output_take(&w,&index,&wait) == 1 && index == 2 && !wait && imports == 1);
    d.busy[0] = false; d.release_fds[0] = 6; import_failure = 1;
    assert(deck_output_take(&w,&index,&wait) == -1 && !wait);
    assert(imported == 6 && closed[6] == 1 && errors == 1 && !lock_depth);
    puts("DECK_SLOT_READY_PRIORITY_PENDING_GPU_WAIT_BUSY_EXCLUSION_FD_OWNERSHIP_OK");
    return 0;
}
