/*
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: MIT
 */

#include "threadpool.h"
#include "list.h"
#include "macros.h"

struct threadpool_worker {
   struct list_head node;
   struct threadpool *tp;
   cnd_t cnd;
   thrd_t thread;
   threadpool_work work;
   void *arg;
};

void
threadpool_init(struct threadpool *tp)
{
   list_inithead(&tp->idle_workers);
   list_inithead(&tp->busy_workers);
   mtx_init(&tp->lock, mtx_plain);
   cnd_init(&tp->cnd);
}

static void
exit_work(UNUSED void *job)
{}

static int
worker_main(void *arg)
{
   struct threadpool_worker *w = arg;

   mtx_lock(&w->tp->lock);

   do {
      if (!w->work)
         cnd_wait(&w->cnd, &w->tp->lock);

      if (w->work == exit_work)
         break;

      mtx_unlock(&w->tp->lock);
      w->work(w->arg);
      mtx_lock(&w->tp->lock);

      w->work = NULL;
      w->arg = NULL;

      /* Move ourself back to the idle list when done: */
      list_del(&w->node);
      list_add(&w->node, &w->tp->idle_workers);

      /* And tell the threadpool we are back to idle: */
      cnd_signal(&w->tp->cnd);

   } while (true);

   /* When exiting, remove ourselves from the idle list: */
   list_del(&w->node);

   /* And tell the threadpool we are exiting: */
   cnd_signal(&w->cnd);

   mtx_unlock(&w->tp->lock);

   return 0;
}

static void
spawn_worker(struct threadpool *tp)
{
   struct threadpool_worker *w = calloc(1, sizeof(*w));

   w->tp = tp;
   cnd_init(&w->cnd);
   list_add(&w->node, &tp->idle_workers);
   thrd_create(&w->thread, worker_main, w);
}

static struct threadpool_worker *
kick_work_locked(struct threadpool *tp, threadpool_work work, void *arg)
{
   struct threadpool_worker *w;

   assert(!list_is_empty(&tp->idle_workers));

   w = list_first_entry(&tp->idle_workers, struct threadpool_worker, node);

   assert(!w->work);
   w->work = work;
   w->arg  = arg;

   /* Move the worker to the busy list: */
   list_del(&w->node);
   list_add(&w->node, &tp->busy_workers);

   /* And then kick the worker: */
   cnd_signal(&w->cnd);

   return w;
}

void
threadpool_run(struct threadpool *tp, threadpool_work work, void *arg)
{
   mtx_lock(&tp->lock);

   /* If all workers are busy, spawn a new one: */
   if (list_is_empty(&tp->idle_workers))
      spawn_worker(tp);

   kick_work_locked(tp, work, arg);

   mtx_unlock(&tp->lock);
}

void
threadpool_fini(struct threadpool *tp)
{
   mtx_lock(&tp->lock);

   while (!list_is_empty(&tp->idle_workers) &&
          !list_is_empty(&tp->busy_workers)) {
      struct threadpool_worker *w;
      int ret;

      /* Wait for an idle worker: */
      if (list_is_empty(&tp->idle_workers))
         cnd_wait(&tp->cnd, &tp->lock);

      /* Kick exit job to notify the worker to shut down: */
      w = kick_work_locked(tp, exit_work, NULL);

      mtx_unlock(&tp->lock);

      /* Wait for the worker to exit: */
      thrd_join(w->thread, &ret);

      free(w);

      mtx_lock(&tp->lock);
   }

   mtx_unlock(&tp->lock);
}
