/**************************************************************************
 *
 * Copyright (C) 2016 Red Hat Inc.
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

#include "util.h"
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <sys/select.h>

int vtest_wait_for_fd_read(int fd)
{
   fd_set read_fds;

   int ret;
   FD_ZERO(&read_fds);
   FD_SET(fd, &read_fds);

   ret = select(fd + 1, &read_fds, NULL, NULL, NULL);
   if (ret < 0) {
      return ret;
   }

   if (FD_ISSET(fd, &read_fds)) {
      return 0;
   }

   return -1;
}

int __failed_call(const char* func, const char *called, int ret)
{
   fprintf(stderr, "%s called %s which failed (%d)\n", func, called, ret);
   return ret;
}

int __failure(const char* func, const char *reason, int ret)
{
   fprintf(stderr, "%s %s (%d)\n", func, reason, ret);
   return ret;
}
