/**************************************************************************
 *
 * Copyright (C) 2015 Red Hat Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 **************************************************************************/

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <stdio.h>
#include <signal.h>
#include <stdbool.h>
#include <unistd.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <sys/un.h>
#include <stddef.h>
#include <fcntl.h>
#include <getopt.h>
#include <string.h>

#include "util.h"
#include "util/list.h"
#include "util/u_math.h"
#include "util/u_memory.h"
#include "vtest.h"
#include "vtest_protocol.h"
#include "virglrenderer.h"
#include "vtest_server.h"
#include "virgl_util.h"
#ifdef HAVE_SYS_SELECT_H
#include <sys/select.h>
#endif

enum vtest_client_result {
   VTEST_CLIENT_DISCONNECTED = 1,
   VTEST_CLIENT_ERROR_INPUT_READ,
   VTEST_CLIENT_ERROR_CONTEXT_MISSING,
   VTEST_CLIENT_ERROR_CONTEXT_FAILED,
   VTEST_CLIENT_ERROR_COMMAND_ID,
   VTEST_CLIENT_ERROR_COMMAND_UNEXPECTED,
   VTEST_CLIENT_ERROR_COMMAND_DISPATCH,
};

struct vtest_client
{
   int in_fd;
   int out_fd;
   struct vtest_input input;

   struct list_head head;

   bool in_fd_ready;
   struct vtest_context *context;
   int context_poll_fd;
   bool context_need_poll;
};

struct vtest_server
{
   const char *socket_name;
   int socket;
   const char *read_file;

   const char *render_device;

   bool main_server;
   bool do_fork;
   bool loop;
   bool multi_clients;

   bool use_glx;
   bool use_egl_surfaceless;
   bool use_gles;

   bool venus;
   bool no_virgl;
   bool use_compat_profile;
   bool drm;

   int ctx_flags;

   struct list_head new_clients;
   struct list_head active_clients;
   struct list_head inactive_clients;
};

struct vtest_server server = {
   .socket_name = VTEST_DEFAULT_SOCKET_NAME,
   .socket = -1,

   .read_file = NULL,

   .render_device = 0,

   .main_server = true,
   .do_fork = true,
   .loop = true,
   .multi_clients = false,

   .ctx_flags = 0,
};

static void vtest_server_getenv(void);
static void vtest_server_parse_args(int argc, char **argv);
static void vtest_server_set_signal_child(void);
static void vtest_server_set_signal_segv(void);
static void vtest_server_open_read_file(void);
static void vtest_server_open_socket(void);
static void vtest_server_run(void);
static void vtest_server_close_socket(void);
static int vtest_client_dispatch_commands(struct vtest_client *client);


int vtest_main(int argc, char **argv)
{
#ifdef __AFL_LOOP
while (__AFL_LOOP(1000)) {
#endif

   vtest_server_getenv();
   vtest_server_parse_args(argc, argv);

   list_inithead(&server.new_clients);
   list_inithead(&server.active_clients);
   list_inithead(&server.inactive_clients);

   if (server.do_fork) {
      vtest_server_set_signal_child();
   } else {
      vtest_server_set_signal_segv();
   }

   vtest_server_run();

#ifdef __AFL_LOOP
   if (!server.main_server) {
      exit(0);
   }
}
#endif

   return 0;
}

#define OPT_NO_FORK 'f'
#define OPT_NO_LOOP_OR_FORK 'l'
#define OPT_MULTI_CLIENTS 'm'
#define OPT_USE_GLX 'x'
#define OPT_USE_EGL_SURFACELESS 's'
#define OPT_USE_GLES 'e'
#define OPT_RENDERNODE 'r'
#define OPT_VENUS 'v'
#define OPT_RENDER_SERVER 'n'
#define OPT_SOCKET_PATH 'p'
#define OPT_NO_VIRGL 'g'
#define OPT_COMPAT_PROFILE 'c'
#define OPT_DRM 'd'

static void vtest_server_parse_args(int argc, char **argv)
{
   int ret;

   static struct option long_options[] = {
      {"no-fork",             no_argument, NULL, OPT_NO_FORK},
      {"no-loop-or-fork",     no_argument, NULL, OPT_NO_LOOP_OR_FORK},
      {"multi-clients",       no_argument, NULL, OPT_MULTI_CLIENTS},
      {"use-glx",             no_argument, NULL, OPT_USE_GLX},
      {"use-egl-surfaceless", no_argument, NULL, OPT_USE_EGL_SURFACELESS},
      {"use-gles",            no_argument, NULL, OPT_USE_GLES},
      {"rendernode",          required_argument, NULL, OPT_RENDERNODE},
      {"venus",               no_argument, NULL, OPT_VENUS},
      {"socket-path",         required_argument, NULL, OPT_SOCKET_PATH},
      {"no-virgl",            no_argument, NULL, OPT_NO_VIRGL},
      {"compat",              no_argument, NULL, OPT_COMPAT_PROFILE},
      {"drm",                 no_argument, NULL, OPT_DRM},
      {0, 0, 0, 0}
   };

   /* getopt_long stores the option index here. */
   int option_index = 0;

   do {
      ret = getopt_long(argc, argv, "", long_options, &option_index);

      switch (ret) {
      case -1:
         break;
      case OPT_NO_FORK:
         server.do_fork = false;
         break;
      case OPT_NO_LOOP_OR_FORK:
         server.do_fork = false;
         server.loop = false;
         break;
      case OPT_MULTI_CLIENTS:
         printf("multi-clients enabled: clients must trust each other\n");
         server.multi_clients = true;
         break;
      case OPT_USE_GLX:
         server.use_glx = true;
         break;
      case OPT_USE_EGL_SURFACELESS:
         server.use_egl_surfaceless = true;
         break;
      case OPT_USE_GLES:
         server.use_gles = true;
         break;
      case OPT_RENDERNODE:
         server.render_device = optarg;
         break;
      case OPT_NO_VIRGL:
         server.no_virgl = true;
         break;
      case OPT_COMPAT_PROFILE:
         server.use_compat_profile = true;
         break;
#ifdef ENABLE_VENUS
      case OPT_VENUS:
         server.venus = true;
         break;
#endif
      case OPT_SOCKET_PATH:
         server.socket_name = optarg;
         break;
#ifdef ENABLE_DRM
      case OPT_DRM:
         server.drm = true;
         break;
#endif
      default:
         printf("Usage: %s [--no-fork] [--no-loop-or-fork] [--multi-clients] "
                "[--use-glx] [--use-egl-surfaceless] [--use-gles] [--no-virgl]"
                "[--rendernode <dev>] [--socket-path <path>] "
#ifdef ENABLE_VENUS
                " [--venus]"
#endif
#ifdef ENABLE_DRM
                " [--drm]"
#endif
                " [file]\n", argv[0]);
         exit(EXIT_FAILURE);
         break;
      }

   } while (ret >= 0);

   if (optind < argc) {
      server.read_file = argv[optind];
      server.loop = false;
      server.do_fork = false;
      server.multi_clients = false;
   }

   if (!server.no_virgl) {
      server.ctx_flags = VIRGL_RENDERER_USE_EGL;
      if (server.use_glx) {
         if (server.use_egl_surfaceless || server.use_gles) {
            fprintf(stderr, "Cannot use surfaceless or GLES with GLX.\n");
            exit(EXIT_FAILURE);
         }
         server.ctx_flags = VIRGL_RENDERER_USE_GLX;
      } else {
         if (server.use_egl_surfaceless)
            server.ctx_flags |= VIRGL_RENDERER_USE_SURFACELESS;
         if (server.use_gles)
            server.ctx_flags |= VIRGL_RENDERER_USE_GLES;
      }

      if (server.use_compat_profile) {
         if (server.use_gles) {
            fprintf(stderr, "Compatibility profile is not available with GLES.\n");
            exit(EXIT_FAILURE);
         }
         server.ctx_flags |= VIRGL_RENDERER_COMPAT_PROFILE;
      }
   } else {
      server.ctx_flags = VIRGL_RENDERER_NO_VIRGL;
   }

   if (server.venus) {
      server.ctx_flags |= VIRGL_RENDERER_VENUS;
      server.ctx_flags |= VIRGL_RENDERER_RENDER_SERVER;
   }
   if (server.drm) {
      server.ctx_flags |= VIRGL_RENDERER_DRM | VIRGL_RENDERER_ASYNC_FENCE_CB;
   }
}

static void vtest_server_getenv(void)
{
   server.use_glx = getenv("VTEST_USE_GLX") != NULL;
   server.use_egl_surfaceless = getenv("VTEST_USE_EGL_SURFACELESS") != NULL;
   server.use_gles = getenv("VTEST_USE_GLES") != NULL;
   server.render_device = getenv("VTEST_RENDERNODE");
   server.use_compat_profile = getenv("VTEST_USE_COMPATIBILITY_PROFILE");
}

static void handler(int sig, siginfo_t *si, void *unused)
{
   (void)sig; (void)si, (void)unused;

   printf("SIGSEGV!\n");
   exit(EXIT_FAILURE);
}

static void vtest_server_set_signal_child(void)
{
   struct sigaction sa;
   int ret;

   memset(&sa, 0, sizeof(sa));
   sigemptyset(&sa.sa_mask);
   sa.sa_handler = SIG_IGN;
   sa.sa_flags = 0;

   ret = sigaction(SIGCHLD, &sa, NULL);
   if (ret == -1) {
      perror("Failed to set SIGCHLD");
      exit(1);
   }
}

static void vtest_server_set_signal_segv(void)
{
   struct sigaction sa;
   int ret;

   memset(&sa, 0, sizeof(sa));
   sigemptyset(&sa.sa_mask);
   sa.sa_flags = SA_SIGINFO;
   sa.sa_sigaction = handler;

   ret = sigaction(SIGSEGV, &sa, NULL);
   if (ret == -1) {
      perror("Failed to set SIGSEGV");
      exit(1);
   }
}

static int vtest_server_add_client(int in_fd, int out_fd)
{
   struct vtest_client *client;

   client = calloc(1, sizeof(*client));
   if (!client)
      return -1;

   client->in_fd = in_fd;
   client->out_fd = out_fd;

   client->input.data.fd = in_fd;
   client->input.read = vtest_block_read;

   client->context_poll_fd = -1;

   list_addtail(&client->head, &server.new_clients);

   return 0;
}

static void vtest_server_open_read_file(void)
{
   int in_fd;
   int out_fd;

   in_fd = open(server.read_file, O_RDONLY);
   if (in_fd == -1) {
      perror(NULL);
      exit(1);
   }

   out_fd = open("/dev/null", O_WRONLY);
   if (out_fd == -1) {
      perror(NULL);
      exit(1);
   }

   if (vtest_server_add_client(in_fd, out_fd)) {
      perror(NULL);
      exit(1);
   }
}

static void vtest_server_open_socket(void)
{
   struct sockaddr_un un;

   server.socket = socket(PF_UNIX, SOCK_STREAM, 0);
   if (server.socket < 0) {
      goto err;
   }

   memset(&un, 0, sizeof(un));
   un.sun_family = AF_UNIX;

   snprintf(un.sun_path, sizeof(un.sun_path), "%s", server.socket_name);
   if (un.sun_path[0] == '@') {
      /* Dextop: abstract socket; the length excludes the unused tail. */
      socklen_t length = offsetof(struct sockaddr_un, sun_path) + strlen(un.sun_path);
      un.sun_path[0] = '\0';
      if (bind(server.socket, (struct sockaddr *)&un, length) < 0) {
         goto err;
      }
      goto bound;
   }

   unlink(un.sun_path);

   if (bind(server.socket, (struct sockaddr *)&un, sizeof(un)) < 0) {
      goto err;
   }

bound:
   if (listen(server.socket, 1) < 0){
      goto err;
   }

   return;

err:
   perror("Failed to setup socket.");
   exit(1);
}

static void vtest_server_wait_clients(void)
{
   struct vtest_client *client;
   fd_set read_fds;
   int max_fd = -1;
   int ret;

   FD_ZERO(&read_fds);

   LIST_FOR_EACH_ENTRY(client, &server.active_clients, head) {
      FD_SET(client->in_fd, &read_fds);
      max_fd = MAX2(client->in_fd, max_fd);

      if (client->context_poll_fd >= 0) {
         FD_SET(client->context_poll_fd, &read_fds);
         max_fd = MAX2(client->context_poll_fd, max_fd);
      }
   }

   /* accept new clients when there is none or when multi_clients is set */
   if (server.socket >= 0 && (max_fd < 0 || server.multi_clients)) {
      FD_SET(server.socket, &read_fds);
      max_fd = MAX2(server.socket, max_fd);
   }

   if (max_fd < 0) {
      if (!list_is_empty(&server.new_clients)) {
         return;
      }

      fprintf(stderr, "server has no fd to wait\n");
      exit(1);
   }

   ret = select(max_fd + 1, &read_fds, NULL, NULL, NULL);
   if (ret < 0) {
      perror("Failed to select on socket!");
      exit(1);
   }

   LIST_FOR_EACH_ENTRY(client, &server.active_clients, head) {
      if (FD_ISSET(client->in_fd, &read_fds)) {
         client->in_fd_ready = true;
      }

      if (client->context_poll_fd >= 0) {
         if (FD_ISSET(client->context_poll_fd, &read_fds)) {
            client->context_need_poll = true;
         }
      } else if (client->context) {
         client->context_need_poll = true;
      }
   }

   if (server.socket >= 0 && FD_ISSET(server.socket, &read_fds)) {
      int new_fd = accept(server.socket, NULL, NULL);
      if (new_fd < 0) {
         perror("Failed to accept socket.");
         exit(1);
      }

      if (vtest_server_add_client(new_fd, new_fd)) {
         perror("Failed to add client.");
         exit(1);
      }
   }
}

static const char *vtest_client_result_string(enum vtest_client_result ret)
{
   switch (ret) {
#define CASE(e) case e: return #e;
   CASE(VTEST_CLIENT_DISCONNECTED)
   CASE(VTEST_CLIENT_ERROR_INPUT_READ)
   CASE(VTEST_CLIENT_ERROR_CONTEXT_MISSING)
   CASE(VTEST_CLIENT_ERROR_CONTEXT_FAILED)
   CASE(VTEST_CLIENT_ERROR_COMMAND_ID)
   CASE(VTEST_CLIENT_ERROR_COMMAND_UNEXPECTED)
   CASE(VTEST_CLIENT_ERROR_COMMAND_DISPATCH)
#undef CASE
   default: return "VTEST_CLIENT_ERROR_UNKNOWN";
   }
}

static void vtest_server_dispatch_clients(void)
{
   struct vtest_client *client, *tmp;

   LIST_FOR_EACH_ENTRY_SAFE(client, tmp, &server.active_clients, head) {
      int ret;

      if (client->context_need_poll) {
         vtest_poll_context(client->context);
         client->context_need_poll = false;
      }

      if (!client->in_fd_ready)
         continue;
      client->in_fd_ready = false;

      ret = vtest_client_dispatch_commands(client);
      if (ret) {
         fprintf(ret == VTEST_CLIENT_DISCONNECTED ? stdout : stderr, "client: %s\n",
                 vtest_client_result_string(ret));
         list_del(&client->head);
         list_addtail(&client->head, &server.inactive_clients);
      }
   }
}

static pid_t vtest_server_fork(void)
{
   pid_t pid = fork();

   if (pid == 0) {
      /* child */
      vtest_server_set_signal_segv();
      vtest_server_close_socket();
      server.main_server = false;
      server.do_fork = false;
      server.loop = false;
      server.multi_clients = false;
   }

   return pid;
}

static void vtest_server_fork_clients(void)
{
   struct vtest_client *client, *tmp;

   LIST_FOR_EACH_ENTRY_SAFE(client, tmp, &server.new_clients, head) {
      if (vtest_server_fork()) {
         /* parent: move new clients to the inactive list */
         list_del(&client->head);
         list_addtail(&client->head, &server.inactive_clients);
      } else {
         /* child: move the first new client to the active list */
         list_del(&client->head);
         list_addtail(&client->head, &server.active_clients);

         /* move the rest new clients to the inactive list */
         LIST_FOR_EACH_ENTRY_SAFE(client, tmp, &server.new_clients, head) {
            list_del(&client->head);
            list_addtail(&client->head, &server.inactive_clients);
         }
      }
   }
}

static void vtest_server_activate_clients(void)
{
   struct vtest_client *client, *tmp;

   /* move new clients to the active list */
   LIST_FOR_EACH_ENTRY_SAFE(client, tmp, &server.new_clients, head) {
      list_addtail(&client->head, &server.active_clients);
   }
   list_inithead(&server.new_clients);
}

static void vtest_server_inactivate_clients(void)
{
   struct vtest_client *client, *tmp;

   /* move active clients to the inactive list */
   LIST_FOR_EACH_ENTRY_SAFE(client, tmp, &server.active_clients, head) {
      list_addtail(&client->head, &server.inactive_clients);
   }
   list_inithead(&server.active_clients);
}

static void vtest_server_tidy_clients(void)
{
   struct vtest_client *client, *tmp;

   LIST_FOR_EACH_ENTRY_SAFE(client, tmp, &server.inactive_clients, head) {
      if (client->context) {
         vtest_destroy_context(client->context);
      }

      if (client->in_fd >= 0) {
         close(client->in_fd);
      }

      if (client->out_fd >= 0 && client->out_fd != client->in_fd) {
         close(client->out_fd);
      }

      free(client);
   }

   list_inithead(&server.inactive_clients);
}

static void vtest_server_run(void)
{
   bool run = true;

   if (server.read_file) {
      vtest_server_open_read_file();
   } else {
      vtest_server_open_socket();
   }

   while (run) {
      const bool was_empty = list_is_empty(&server.active_clients);
      bool is_empty;

      vtest_server_wait_clients();
      vtest_server_dispatch_clients();

      if (server.do_fork) {
         vtest_server_fork_clients();
      } else {
         vtest_server_activate_clients();
      }

      /* init renderer after the first active client is added */
      is_empty = list_is_empty(&server.active_clients);
      if (was_empty && !is_empty) {
         int ret = vtest_init_renderer(server.multi_clients,
                                       server.ctx_flags,
                                       server.render_device);
         if (ret) {
            vtest_server_inactivate_clients();
            run = false;
         }
      }

      vtest_server_tidy_clients();

      /* clean up renderer after the last active client is removed */
      if (!was_empty && is_empty) {
         vtest_cleanup_renderer();
         if (!server.loop) {
            run = false;
         }
      }
   }

   vtest_server_close_socket();
}

static const struct vtest_command {
   const char *name;
   int (*dispatch)(uint32_t);
   bool init_context;
} vtest_commands[] = {
#define HANDLER(N, n, init_context) \
   [VCMD_##N] = { #N, vtest_##n, init_context }

   HANDLER(GET_CAPS,                send_caps,             false),
   HANDLER(RESOURCE_CREATE,         create_resource,       true ),
   HANDLER(RESOURCE_UNREF,          resource_unref,        true ),
   HANDLER(TRANSFER_GET,            transfer_get,          true ),
   HANDLER(TRANSFER_PUT,            transfer_put,          true ),
   HANDLER(SUBMIT_CMD,              submit_cmd,            true ),
   HANDLER(RESOURCE_BUSY_WAIT,      resource_busy_wait,    false),
   /* VCMD_CREATE_RENDERER is a special case */
   HANDLER(GET_CAPS2,               send_caps2,            false),
   HANDLER(PING_PROTOCOL_VERSION,   ping_protocol_version, false),
   HANDLER(PROTOCOL_VERSION,        protocol_version,      false),

   /* since protocol version 2 */
   HANDLER(RESOURCE_CREATE2,        create_resource2,      true ),
   HANDLER(TRANSFER_GET2,           transfer_get2,         true ),
   HANDLER(TRANSFER_PUT2,           transfer_put2,         true ),
   /* since protocol version 3 */
   HANDLER(GET_PARAM,               get_param,             false),
   HANDLER(GET_CAPSET,              get_capset,            false),
   HANDLER(CONTEXT_INIT,            context_init,          false),
   HANDLER(RESOURCE_CREATE_BLOB,    resource_create_blob,  true ),
   HANDLER(SYNC_CREATE,             sync_create,           true ),
   HANDLER(SYNC_UNREF,              sync_unref,            true ),
   HANDLER(SYNC_READ,               sync_read,             true ),
   HANDLER(SYNC_WRITE,              sync_write,            true ),
   HANDLER(SYNC_WAIT,               sync_wait,             true ),
   HANDLER(SUBMIT_CMD2,             submit_cmd2,           true ),

   /* since protocol version 4 */
#ifdef ENABLE_DRM
   HANDLER(DRM_SYNC_CREATE,             drm_sync_create,           true   ),
   HANDLER(DRM_SYNC_DESTROY,            drm_sync_destroy,          true   ),
   HANDLER(DRM_SYNC_HANDLE_TO_FD,       drm_sync_handle_to_fd,     true   ),
   HANDLER(DRM_SYNC_FD_TO_HANDLE,       drm_sync_fd_to_handle,     true   ),
   HANDLER(DRM_SYNC_IMPORT_SYNC_FILE,   drm_sync_import_sync_file, true   ),
   HANDLER(DRM_SYNC_EXPORT_SYNC_FILE,   drm_sync_export_sync_file, true   ),
   HANDLER(DRM_SYNC_WAIT,               drm_sync_wait,             true   ),
   HANDLER(DRM_SYNC_RESET,              drm_sync_reset,            true   ),
   HANDLER(DRM_SYNC_SIGNAL,             drm_sync_signal,           true   ),
   HANDLER(DRM_SYNC_TIMELINE_SIGNAL,    drm_sync_timeline_signal,  true   ),
   HANDLER(DRM_SYNC_TIMELINE_WAIT,      drm_sync_timeline_wait,    true   ),
   HANDLER(DRM_SYNC_QUERY,              drm_sync_query,            true   ),
   HANDLER(DRM_SYNC_TRANSFER,           drm_sync_transfer,         true   ),
#endif /* ENABLE_DRM */
   HANDLER(RESOURCE_EXPORT_FD,          resource_export_fd,        true   ),
};

static int vtest_client_dispatch_commands(struct vtest_client *client)
{
   TRACE_FUNC();
   const struct vtest_command *cmd;
   int ret;
   uint32_t header[VTEST_HDR_SIZE];

   ret = client->input.read(&client->input, &header, sizeof(header));
   if (!ret)
      return VTEST_CLIENT_DISCONNECTED;
   else if (ret < 0 || (size_t)ret < sizeof(header))
      return VTEST_CLIENT_ERROR_INPUT_READ;

   if (!client->context) {
      /* The first command MUST be VCMD_CREATE_RENDERER */
      if (header[1] != VCMD_CREATE_RENDERER) {
         return VTEST_CLIENT_ERROR_CONTEXT_MISSING;
      }

      ret = vtest_create_context(&client->input, client->out_fd,
                                 header[0], &client->context);
      if (ret < 0) {
         return VTEST_CLIENT_ERROR_CONTEXT_FAILED;
      }
      printf("%s: client context created.\n", __func__);
      vtest_poll_resource_busy_wait();

      return 0;
   }

   vtest_poll_resource_busy_wait();
   if (header[1] <= 0 || header[1] >= ARRAY_SIZE(vtest_commands)) {
      return VTEST_CLIENT_ERROR_COMMAND_ID;
   }

   cmd = &vtest_commands[header[1]];
   if (cmd->dispatch == NULL) {
      return VTEST_CLIENT_ERROR_COMMAND_UNEXPECTED;
   }

   /* we should consider per-context dispatch table to get rid of if's */
   if (cmd->init_context) {
      ret = vtest_lazy_init_context(client->context);
      if (ret) {
         return VTEST_CLIENT_ERROR_CONTEXT_FAILED;
      }
      client->context_poll_fd = vtest_get_context_poll_fd(client->context);
   }

   vtest_set_current_context(client->context);

   void *trace_scope = TRACE_SCOPE_BEGIN(cmd->name);
   ret = cmd->dispatch(header[0]);
   TRACE_SCOPE_END(trace_scope);

   if (ret < 0) {
      return VTEST_CLIENT_ERROR_COMMAND_DISPATCH;
   }

   return 0;
}

static void vtest_server_close_socket(void)
{
   if (server.socket != -1) {
      close(server.socket);
      server.socket = -1;
   }
}
