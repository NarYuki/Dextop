/*
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef THREADPOOL_H_
#define THREADPOOL_H_

#include "c11/threads.h"
#include "list.h"

struct threadpool_worker;

/**
 * A threadpool manages a list of workers, dispatching work to an idle
 * worker, or if necessary spawning a new worker.
 */
struct threadpool {
   struct list_head idle_workers;
   struct list_head busy_workers;
   mtx_t lock;
   cnd_t cnd;
};

typedef void (*threadpool_work)(void *job);

void threadpool_init(struct threadpool *tp);
void threadpool_run(struct threadpool *tp, threadpool_work work, void *arg);
void threadpool_fini(struct threadpool *tp);

#endif /*  THREADPOOL_H_ */
