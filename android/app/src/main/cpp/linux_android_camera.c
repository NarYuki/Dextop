/*
 * Android cameras as Video4Linux capture devices for Linux in Dextop.
 *
 * Linux applications (browsers, video calls, OBS, ffmpeg) use /dev/videoN through the V4L2
 * API; Android gives cameras only to apps through Camera2. Dextop records with Camera2 and
 * serves frames on the device socket of /tmp/dextop-devices.conf (see linux_android_usb.c):
 *   camera <video0> <Android camera id> <WxH,WxH,...> <label>
 * Opening /dev/videoN connects ("C <camera id>"); this library answers the V4L2 ioctls of a
 * YUYV memory-mapped streaming device. After VIDIOC_STREAMON it sends "S <w> <h>", and one
 * "N" per buffer the application queues; Dextop replies to each with a frame
 * (u32 length, little endian, then YUYV bytes). The socket is the application's descriptor,
 * so poll()/select() wake up exactly when a frame is ready to dequeue.
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/videodev2.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/sysmacros.h>
#include <sys/time.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>

#define CONFIG "/tmp/dextop-devices.conf"
#define MAX_FDS 4096
#define MAX_BUFFERS 8
#define MAX_SIZES 8

struct camera {
    char id[32];
    char label[64];
    int sizes[MAX_SIZES][2];
    int size_count;
    int width, height;
    int buffers;      /* allocated by VIDIOC_REQBUFS */
    int queued[MAX_BUFFERS];
    int queue_head, queue_count;
    unsigned char *memory[MAX_BUFFERS];
    uint32_t sequence;
    int streaming;
};

static struct camera *cameras[MAX_FDS];

static size_t frame_size(const struct camera *camera) { return (size_t)camera->width * camera->height * 2; }
static size_t buffer_span(const struct camera *camera) { return (frame_size(camera) + 4095) & ~(size_t)4095; }

/* The camera listed as [video] ("video0"), and the device socket's name; 0 when found. */
static int lookup(const char *video, char *socket_name, size_t socket_size, struct camera *camera) {
    int fd = (int)syscall(SYS_openat, AT_FDCWD, CONFIG, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    char text[8192];
    ssize_t length = syscall(SYS_read, fd, text, sizeof(text) - 1);
    syscall(SYS_close, fd);
    if (length <= 0) return -1;
    text[length] = '\0';
    int found = -1;
    char *cursor = NULL;
    for (char *line = strtok_r(text, "\n", &cursor); line != NULL; line = strtok_r(NULL, "\n", &cursor)) {
        if (strncmp(line, "socket ", 7) == 0) {
            snprintf(socket_name, socket_size, "%s", line + 7);
            continue;
        }
        char name[16], id[32], sizes[256];
        int used = 0;
        if (strncmp(line, "camera ", 7) != 0 ||
            sscanf(line + 7, "%15s %31s %255s %n", name, id, sizes, &used) < 3 || strcmp(name, video) != 0) continue;
        if (camera) {
            snprintf(camera->id, sizeof(camera->id), "%s", id);
            snprintf(camera->label, sizeof(camera->label), "%s", used > 0 ? line + 7 + used : "Android camera");
            camera->size_count = 0;
            char *inner = NULL;
            for (char *size = strtok_r(sizes, ",", &inner); size && camera->size_count < MAX_SIZES; size = strtok_r(NULL, ",", &inner)) {
                int w, h;
                if (sscanf(size, "%dx%d", &w, &h) == 2 && w > 0 && h > 0 && w <= 4096 && h <= 4096) {
                    camera->sizes[camera->size_count][0] = w;
                    camera->sizes[camera->size_count][1] = h;
                    camera->size_count++;
                }
            }
        }
        found = 0;
    }
    return socket_name[0] && (!camera || camera->size_count > 0) ? found : -1;
}

static int send_line(int fd, const char *line) {
    size_t length = strlen(line);
    return syscall(SYS_sendto, fd, line, length, MSG_NOSIGNAL, NULL, 0) == (long)length ? 0 : -1;
}

/* /dev/videoN: a connected camera descriptor, -1 with errno, or -2 when [path] is no camera. */
int dextop_camera_open(const char *path, int flags) {
    if (path == NULL || strncmp(path, "/dev/video", 10) != 0) return -2;
    char socket_name[108] = "";
    struct camera *camera = calloc(1, sizeof(*camera));
    if (camera == NULL) return -2;
    if (lookup(path + 5, socket_name, sizeof(socket_name), camera) != 0) { free(camera); return -2; }
    int type = SOCK_STREAM | ((flags & O_CLOEXEC) ? SOCK_CLOEXEC : 0) | ((flags & O_NONBLOCK) ? SOCK_NONBLOCK : 0);
    int fd = (int)syscall(SYS_socket, AF_UNIX, type, 0);
    if (fd < 0 || fd >= MAX_FDS) { if (fd >= 0) syscall(SYS_close, fd); free(camera); errno = EBUSY; return -1; }
    struct sockaddr_un address = {.sun_family = AF_UNIX};
    size_t length = strlen(socket_name);
    memcpy(address.sun_path + 1, socket_name, length);
    char request[64];
    snprintf(request, sizeof(request), "C %s\n", camera->id);
    if (syscall(SYS_connect, fd, &address, (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + length)) != 0 ||
        send_line(fd, request) != 0) {
        syscall(SYS_close, fd);
        free(camera);
        errno = ENODEV;
        return -1;
    }
    camera->width = camera->sizes[0][0];
    camera->height = camera->sizes[0][1];
    cameras[fd] = camera;
    return fd;
}

static int supported(const struct camera *camera, int width, int height) {
    for (int i = 0; i < camera->size_count; i++) if (camera->sizes[i][0] == width && camera->sizes[i][1] == height) return 1;
    return 0;
}

static void fill_format(const struct camera *camera, struct v4l2_format *format) {
    memset(&format->fmt.pix, 0, sizeof(format->fmt.pix));
    format->fmt.pix.width = (uint32_t)camera->width;
    format->fmt.pix.height = (uint32_t)camera->height;
    format->fmt.pix.pixelformat = V4L2_PIX_FMT_YUYV;
    format->fmt.pix.field = V4L2_FIELD_NONE;
    format->fmt.pix.bytesperline = (uint32_t)camera->width * 2;
    format->fmt.pix.sizeimage = (uint32_t)frame_size(camera);
    format->fmt.pix.colorspace = V4L2_COLORSPACE_SRGB;
}

/* Reads exactly [size] bytes of the next frame, discarding what does not fit. */
static int receive_frame(int fd, unsigned char *target, size_t capacity, size_t *used) {
    unsigned char header[4];
    if (recv(fd, header, 4, MSG_WAITALL) != 4) return -1;
    size_t length = header[0] | (size_t)header[1] << 8 | (size_t)header[2] << 16 | (size_t)header[3] << 24;
    size_t copied = 0;
    while (copied < length) {
        unsigned char sink[4096];
        size_t want = length - copied;
        ssize_t n = copied < capacity
            ? recv(fd, target + copied, want < capacity - copied ? want : capacity - copied, MSG_WAITALL)
            : recv(fd, sink, want < sizeof(sink) ? want : sizeof(sink), MSG_WAITALL);
        if (n <= 0) return -1;
        copied += (size_t)n;
    }
    *used = length < capacity ? length : capacity;
    return 0;
}

static int camera_ioctl(int fd, struct camera *camera, unsigned long request, void *arg) {
    switch (request) {
        case VIDIOC_QUERYCAP: {
            struct v4l2_capability *cap = arg;
            memset(cap, 0, sizeof(*cap));
            snprintf((char *)cap->driver, sizeof(cap->driver), "dextop");
            snprintf((char *)cap->card, sizeof(cap->card), "%s", camera->label);
            snprintf((char *)cap->bus_info, sizeof(cap->bus_info), "platform:dextop-camera-%s", camera->id);
            cap->version = (6 << 16) | (1 << 8);
            cap->device_caps = V4L2_CAP_VIDEO_CAPTURE | V4L2_CAP_STREAMING;
            cap->capabilities = cap->device_caps | V4L2_CAP_DEVICE_CAPS;
            return 0;
        }
        case VIDIOC_ENUMINPUT: {
            struct v4l2_input *input = arg;
            if (input->index != 0) { errno = EINVAL; return -1; }
            uint32_t index = input->index;
            memset(input, 0, sizeof(*input));
            input->index = index;
            snprintf((char *)input->name, sizeof(input->name), "Camera");
            input->type = V4L2_INPUT_TYPE_CAMERA;
            return 0;
        }
        case VIDIOC_G_INPUT: *(int *)arg = 0; return 0;
        case VIDIOC_S_INPUT: if (*(int *)arg == 0) return 0; errno = EINVAL; return -1;
        case VIDIOC_ENUM_FMT: {
            struct v4l2_fmtdesc *desc = arg;
            if (desc->index != 0 || desc->type != V4L2_BUF_TYPE_VIDEO_CAPTURE) { errno = EINVAL; return -1; }
            uint32_t type = desc->type;
            memset(desc, 0, sizeof(*desc));
            desc->type = type;
            snprintf((char *)desc->description, sizeof(desc->description), "YUYV 4:2:2");
            desc->pixelformat = V4L2_PIX_FMT_YUYV;
            return 0;
        }
        case VIDIOC_ENUM_FRAMESIZES: {
            struct v4l2_frmsizeenum *size = arg;
            if (size->pixel_format != V4L2_PIX_FMT_YUYV || size->index >= (uint32_t)camera->size_count) { errno = EINVAL; return -1; }
            size->type = V4L2_FRMSIZE_TYPE_DISCRETE;
            size->discrete.width = (uint32_t)camera->sizes[size->index][0];
            size->discrete.height = (uint32_t)camera->sizes[size->index][1];
            return 0;
        }
        case VIDIOC_ENUM_FRAMEINTERVALS: {
            struct v4l2_frmivalenum *interval = arg;
            if (interval->index != 0 || interval->pixel_format != V4L2_PIX_FMT_YUYV ||
                !supported(camera, (int)interval->width, (int)interval->height)) { errno = EINVAL; return -1; }
            interval->type = V4L2_FRMIVAL_TYPE_DISCRETE;
            interval->discrete.numerator = 1;
            interval->discrete.denominator = 30;
            return 0;
        }
        case VIDIOC_G_FMT: {
            struct v4l2_format *format = arg;
            if (format->type != V4L2_BUF_TYPE_VIDEO_CAPTURE) { errno = EINVAL; return -1; }
            fill_format(camera, format);
            return 0;
        }
        case VIDIOC_TRY_FMT:
        case VIDIOC_S_FMT: {
            struct v4l2_format *format = arg;
            if (format->type != V4L2_BUF_TYPE_VIDEO_CAPTURE) { errno = EINVAL; return -1; }
            if (request == VIDIOC_S_FMT && camera->streaming) { errno = EBUSY; return -1; }
            /* The nearest listed size not larger than asked, else the smallest. */
            int best = -1;
            for (int i = 0; i < camera->size_count; i++) {
                if (camera->sizes[i][0] <= (int)format->fmt.pix.width && camera->sizes[i][1] <= (int)format->fmt.pix.height &&
                    (best < 0 || camera->sizes[i][0] * camera->sizes[i][1] > camera->sizes[best][0] * camera->sizes[best][1])) best = i;
            }
            if (best < 0) {
                best = 0;
                for (int i = 1; i < camera->size_count; i++)
                    if (camera->sizes[i][0] * camera->sizes[i][1] < camera->sizes[best][0] * camera->sizes[best][1]) best = i;
            }
            struct camera chosen = *camera;
            chosen.width = camera->sizes[best][0];
            chosen.height = camera->sizes[best][1];
            if (request == VIDIOC_S_FMT) { camera->width = chosen.width; camera->height = chosen.height; }
            fill_format(&chosen, format);
            return 0;
        }
        case VIDIOC_G_PARM:
        case VIDIOC_S_PARM: {
            struct v4l2_streamparm *parm = arg;
            if (parm->type != V4L2_BUF_TYPE_VIDEO_CAPTURE) { errno = EINVAL; return -1; }
            memset(&parm->parm.capture, 0, sizeof(parm->parm.capture));
            parm->parm.capture.capability = V4L2_CAP_TIMEPERFRAME;
            parm->parm.capture.timeperframe.numerator = 1;
            parm->parm.capture.timeperframe.denominator = 30;
            parm->parm.capture.readbuffers = 2;
            return 0;
        }
        case VIDIOC_REQBUFS: {
            struct v4l2_requestbuffers *buffers = arg;
            if (buffers->type != V4L2_BUF_TYPE_VIDEO_CAPTURE || buffers->memory != V4L2_MEMORY_MMAP) { errno = EINVAL; return -1; }
            if (camera->streaming) { errno = EBUSY; return -1; }
            uint32_t count = buffers->count > MAX_BUFFERS ? MAX_BUFFERS : buffers->count;
            if (count == 1) count = 2;
            camera->buffers = (int)count;
            camera->queue_count = camera->queue_head = 0;
            buffers->count = count;
            buffers->capabilities = V4L2_BUF_CAP_SUPPORTS_MMAP;
            return 0;
        }
        case VIDIOC_QUERYBUF:
        case VIDIOC_QBUF:
        case VIDIOC_DQBUF: {
            struct v4l2_buffer *buffer = arg;
            if (buffer->type != V4L2_BUF_TYPE_VIDEO_CAPTURE || buffer->memory != V4L2_MEMORY_MMAP) { errno = EINVAL; return -1; }
            if (request == VIDIOC_DQBUF) {
                if (!camera->streaming || camera->queue_count == 0) { errno = EINVAL; return -1; }
                int index = camera->queued[camera->queue_head];
                size_t used = 0;
                if (receive_frame(fd, camera->memory[index], frame_size(camera), &used) != 0) {
                    if (errno != EAGAIN && errno != EWOULDBLOCK) errno = EIO;
                    return -1;
                }
                camera->queue_head = (camera->queue_head + 1) % MAX_BUFFERS;
                camera->queue_count--;
                memset(buffer, 0, sizeof(*buffer));
                buffer->index = (uint32_t)index;
                buffer->type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
                buffer->memory = V4L2_MEMORY_MMAP;
                buffer->bytesused = (uint32_t)used;
                buffer->length = (uint32_t)frame_size(camera);
                buffer->m.offset = (uint32_t)(index * buffer_span(camera));
                buffer->field = V4L2_FIELD_NONE;
                buffer->flags = V4L2_BUF_FLAG_MAPPED | V4L2_BUF_FLAG_DONE | V4L2_BUF_FLAG_TIMESTAMP_MONOTONIC;
                struct timespec now;
                clock_gettime(CLOCK_MONOTONIC, &now);
                buffer->timestamp.tv_sec = now.tv_sec;
                buffer->timestamp.tv_usec = now.tv_nsec / 1000;
                buffer->sequence = camera->sequence++;
                return 0;
            }
            if (buffer->index >= (uint32_t)camera->buffers) { errno = EINVAL; return -1; }
            buffer->length = (uint32_t)frame_size(camera);
            buffer->m.offset = (uint32_t)(buffer->index * buffer_span(camera));
            buffer->field = V4L2_FIELD_NONE;
            buffer->flags = V4L2_BUF_FLAG_TIMESTAMP_MONOTONIC | (camera->memory[buffer->index] ? V4L2_BUF_FLAG_MAPPED : 0);
            if (request == VIDIOC_QBUF) {
                if (camera->queue_count >= MAX_BUFFERS || camera->memory[buffer->index] == NULL) { errno = EINVAL; return -1; }
                camera->queued[(camera->queue_head + camera->queue_count) % MAX_BUFFERS] = (int)buffer->index;
                camera->queue_count++;
                buffer->flags |= V4L2_BUF_FLAG_QUEUED;
                if (camera->streaming && send_line(fd, "N\n") != 0) { errno = EIO; return -1; }
            }
            return 0;
        }
        case VIDIOC_STREAMON: {
            if (camera->streaming) return 0;
            char line[64];
            snprintf(line, sizeof(line), "S %d %d\n", camera->width, camera->height);
            if (send_line(fd, line) != 0) { errno = EIO; return -1; }
            camera->streaming = 1;
            /* Buffers queued before streaming each ask for a frame now. */
            for (int i = 0; i < camera->queue_count; i++) send_line(fd, "N\n");
            return 0;
        }
        case VIDIOC_STREAMOFF: {
            /* Frames already asked for are still sent; closing the device drops them. */
            if (camera->streaming) send_line(fd, "E\n");
            camera->streaming = 0;
            camera->queue_count = camera->queue_head = 0;
            return 0;
        }
        case VIDIOC_G_PRIORITY: *(uint32_t *)arg = V4L2_PRIORITY_DEFAULT; return 0;
        case VIDIOC_S_PRIORITY: return 0;
        default:
            errno = request == VIDIOC_G_STD || request == VIDIOC_S_STD ? ENOTTY : EINVAL;
            return -1;
    }
}

/* V4L2 ioctls of a camera descriptor; 1 when [fd] is one. */
int dextop_camera_ioctl(int fd, unsigned long request, void *arg, int *result) {
    struct camera *camera = fd >= 0 && fd < MAX_FDS ? cameras[fd] : NULL;
    if (camera == NULL) return 0;
    *result = camera_ioctl(fd, camera, request, arg);
    return 1;
}

void dextop_camera_close(int fd) {
    struct camera *camera = fd >= 0 && fd < MAX_FDS ? cameras[fd] : NULL;
    if (camera == NULL) return;
    cameras[fd] = NULL;
    free(camera); /* mapped buffers stay valid until the application unmaps them */
}

/* A camera's buffers live in this process; mmap() of its descriptor hands out their memory. */
typedef void *(*mmap_fn)(void *, size_t, int, int, int, off_t);
void *dextop_camera_mmap(mmap_fn real, void *address, size_t length, int prot, int flags, int fd, off_t offset, int *handled) {
    struct camera *camera = fd >= 0 && fd < MAX_FDS ? cameras[fd] : NULL;
    *handled = camera != NULL;
    if (camera == NULL) return MAP_FAILED;
    size_t span = buffer_span(camera);
    size_t index = span ? (size_t)offset / span : MAX_BUFFERS;
    if (index >= (size_t)camera->buffers || length < frame_size(camera)) { errno = EINVAL; return MAP_FAILED; }
    void *memory = real(address, length, prot, (flags & ~MAP_SHARED) | MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (memory == MAP_FAILED) return MAP_FAILED;
    camera->memory[index] = memory;
    return memory;
}

/* /dev/videoN of a listed camera reports itself as a V4L2 character device (major 81). */
int dextop_camera_stat(const char *path, struct stat *st) {
    if (path == NULL || strncmp(path, "/dev/video", 10) != 0) return -1;
    char socket_name[108] = "";
    if (lookup(path + 5, socket_name, sizeof(socket_name), NULL) != 0) return -1;
    memset(st, 0, sizeof(*st));
    st->st_mode = S_IFCHR | 0660;
    st->st_rdev = makedev(81, atoi(path + 10));
    st->st_nlink = 1;
    return 0;
}
