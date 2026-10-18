/* -*- c-set-style: "K&R"; c-basic-offset: 8 -*-
 *
 * This file is part of PRoot.
 *
 * Copyright (C) 2015 STMicroelectronics
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

#include <sched.h>      /* CLONE_*,  */
#include <sys/types.h>  /* pid_t, size_t, */
#include <stdlib.h>     /* NULL, */
#include <assert.h>     /* assert(3), */
#include <string.h>     /* bzero(3), */
#include <stdbool.h>    /* bool, true, false, */
#include <sys/queue.h>  /* LIST_*,  */
#include <talloc.h>     /* talloc_*, */
#include <signal.h>     /* kill(2), SIGKILL, */
#include <sys/ptrace.h> /* ptrace(2), PTRACE_*, */
#include <errno.h>      /* E*, */
#include <inttypes.h>   /* PRI*, */
#include <unistd.h>     /* getpid(2), */
#include <stdio.h>      /* fopen(3), fgets(3), snprintf(3), */
#include <dirent.h>     /* opendir(3), readdir(3), closedir(3), */
#include <limits.h>     /* INT_MAX, */

#include "tracee/tracee.h"
#include "tracee/reg.h"
#include "tracee/mem.h"
#include "path/binding.h"
#include "syscall/sysnum.h"
#include "tracee/event.h"
#include "ptrace/ptrace.h"
#include "ptrace/wait.h"
#include "extension/extension.h"
#include "cli/note.h"

#include "compat.h"

static Tracees tracees;

#define TRACEE_BUCKETS 1024
static Tracees buckets[TRACEE_BUCKETS];

static bool terminations_pending = false;

static Tracees *bucket_of(pid_t pid)
{
	return &buckets[(unsigned int) pid % TRACEE_BUCKETS];
}


/**
 * Remove @zombie from its parent's list of zombies.  Note: this is a
 * talloc destructor.
 */
static int remove_zombie(Tracee *zombie)
{
	LIST_REMOVE(zombie, link);
	return 0;
}

/**
 * Perform some specific treatments against @pointer according to its
 * type, before it gets unlinked from @tracee_->life_context.
 */
static void clean_life_span_object(const void *pointer, int depth UNUSED,
				int max_depth UNUSED, int is_ref UNUSED, void *tracee_)
{
	Binding *binding;
	Tracee *tracee;

	tracee = talloc_get_type_abort(tracee_, Tracee);

	/* So far, only bindings need a special treatment.  */
	binding = talloc_get_type(pointer, Binding);
	if (binding != NULL)
		remove_binding_from_all_lists(tracee, binding);
}

/**
 * Remove @tracee from the list of tracees and update all of its
 * children & ptracees, and its ptracer.  Note: this is a talloc
 * destructor.
 */
static int remove_tracee(Tracee *tracee)
{
	Tracee *relative;
	Tracee *ptracer;
	int event;

	LIST_REMOVE(tracee, link);
	LIST_REMOVE(tracee, bucket_link);

	/* Clean objects that are linked to this tracee's life
	 * span.  */
	talloc_report_depth_cb(tracee->life_context, 0, 100, clean_life_span_object, tracee);

	if (tracee->parent != NULL)
		tracee->parent->nb_children--;

	if (tracee->nb_children != 0 || tracee->as_ptracer.nb_ptracees != 0)
	LIST_FOREACH(relative, &tracees, link) {
		/* Its children are now orphan.  */
		if (relative->parent == tracee) {
			relative->parent = NULL;
			tracee->nb_children--;
		}

		/* Its tracees are now free.  */
		if (relative->as_ptracee.ptracer == tracee) {
			/* Release the pending event, if any.  */
			relative->as_ptracee.ptracer = NULL;

			if (relative->as_ptracee.event4.proot.pending) {
				event = handle_tracee_event(relative,
							relative->as_ptracee.event4.proot.value);
				(void) restart_tracee(relative, event);
			}
			else if (relative->as_ptracee.event4.ptracer.pending) {
				event = relative->as_ptracee.event4.proot.value;
				(void) restart_tracee(relative, event);
			}

			bzero(&relative->as_ptracee, sizeof(relative->as_ptracee));
		}
	}

	/* Nothing else to do if it's not a ptracee.  */
	ptracer = tracee->as_ptracee.ptracer;
	if (ptracer == NULL)
		return 0;

	/* Zombify this ptracee until its ptracer is notified about
	 * its death.  */
	event = tracee->as_ptracee.event4.ptracer.value;
	if (tracee->as_ptracee.event4.ptracer.pending
	    && (WIFEXITED(event) || WIFSIGNALED(event))) {
		Tracee *zombie;

		zombie = new_dummy_tracee(ptracer);
		if (zombie != NULL) {
			LIST_INSERT_HEAD(&PTRACER.zombies, zombie, link);
			talloc_set_destructor(zombie, remove_zombie);

			zombie->parent = tracee->parent;
			zombie->clone = tracee->clone;
			zombie->pid = tracee->pid;

			detach_from_ptracer(tracee);
			attach_to_ptracer(zombie, ptracer);

			zombie->as_ptracee.event4.ptracer.pending = true;
			zombie->as_ptracee.event4.ptracer.value = event;
			zombie->as_ptracee.is_zombie = true;

			return 0;
		}
		/* Fallback to the common path.  */
	}

	detach_from_ptracer(tracee);

	/* Wake its ptracer if there's nothing else to wait for.  */
	if (PTRACER.nb_ptracees == 0 && PTRACER.wait_pid != 0) {
		/* Update the return value of ptracer's wait(2).  */
		poke_reg(ptracer, SYSARG_RESULT, -ECHILD);

		/* Don't forget to write its register cache back.  */
		(void) push_regs(ptracer);

		PTRACER.wait_pid = 0;
		(void) restart_tracee(ptracer, 0);
	}

	return 0;
}

/**
 * Allocate a new entry for a dummy tracee (no pid, no destructor, not
 * in the list of tracees, ...).  The new allocated memory is attached
 * to the given @context.  This function returns NULL if an error
 * occurred (ENOMEM), otherwise it returns the newly allocated
 * structure.
 */
Tracee *new_dummy_tracee(TALLOC_CTX *context)
{
	Tracee *tracee;

	tracee = talloc_zero(context, Tracee);
	if (tracee == NULL)
		return NULL;

	/* Allocate a memory collector.  */
	tracee->ctx = talloc_new(tracee);
	if (tracee->ctx == NULL)
		goto no_mem;

	/* By default new tracees have an empty file-system
	 * name-space and heap.  */
	tracee->fs = talloc_zero(tracee, FileSystemNameSpace);
	tracee->heap = talloc_zero(tracee, Heap);
	if (tracee->fs == NULL || tracee->heap == NULL)
		goto no_mem;

	return tracee;

no_mem:
	TALLOC_FREE(tracee);
	return NULL;
}

static uint64_t next_vpid = 1;

/**
 * Allocate a new entry for the tracee @pid, then set its destructor
 * and add it to the list of tracees.  This function returns NULL if
 * an error occurred (ENOMEM), otherwise it returns the newly
 * allocated structure.
 */
static Tracee *new_tracee(pid_t pid)
{
	Tracee *tracee;

	tracee = new_dummy_tracee(NULL);
	if (tracee == NULL)
		return NULL;

	talloc_set_destructor(tracee, remove_tracee);

	tracee->pid = pid;
	tracee->tgid = pid;
	tracee->vpid = next_vpid++;

	LIST_INSERT_HEAD(&tracees, tracee, link);
	LIST_INSERT_HEAD(bucket_of(pid), tracee, bucket_link);

	tracee->life_context = talloc_new(tracee);

	return tracee;
}

/**
 * Return the first [stopped?] tracee with the given
 * @pid (-1 for any) which has the given @ptracer, and which has a
 * pending event for its ptracer if @only_with_pevent is true.  See
 * wait(2) manual for the meaning of @wait_options.  This function
 * returns NULL if there's no such ptracee.
 */
static Tracee *get_ptracee(const Tracee *ptracer, pid_t pid, bool only_stopped,
			bool only_with_pevent, word_t wait_options)
{
	Tracee *ptracee;

	/* Return zombies first.  */
	LIST_FOREACH(ptracee, &PTRACER.zombies, link) {
		/* Not the ptracee you're looking for?  */
		if (pid != ptracee->pid && pid != -1)
			continue;

		/* Not the expected kind of cloned process?  */
		if (!EXPECTED_WAIT_CLONE(wait_options, ptracee))
			continue;

		return ptracee;
	}

	LIST_FOREACH(ptracee, &tracees, link) {
		/* Discard tracees that don't have this ptracer.  */
		if (PTRACEE.ptracer != ptracer)
			continue;

		/* Not the ptracee you're looking for?  */
		if (pid != ptracee->pid && pid != -1)
			continue;

		/* Not the expected kind of cloned process?  */
		if (!EXPECTED_WAIT_CLONE(wait_options, ptracee))
			continue;

		/* No need to do more checks if its stopped state
		 * doesn't matter.  Be careful when using such
		 * maybe-running tracee.  */
		if (!only_stopped)
			return ptracee;

		/* Is this tracee in the stopped state?  */
		if (ptracee->running)
			continue;

		/* Has a pending event for its ptracer?  */
		if (PTRACEE.event4.ptracer.pending || !only_with_pevent)
			return ptracee;

		/* No need to go further if the specific tracee isn't
		 * in the expected state?  */
		if (pid == ptracee->pid)
			return NULL;
	}

	return NULL;
}

/**
 * Wrapper for get_ptracee(), this ensures only a stopped tracee is
 * returned (or NULL).
 */
Tracee *get_stopped_ptracee(const Tracee *ptracer, pid_t pid,
			bool only_with_pevent, word_t wait_options)
{
	return get_ptracee(ptracer, pid, true, only_with_pevent, wait_options);
}

/**
 * Wrapper for get_ptracee(), this ensures no running tracee is
 * returned.
 */
bool has_ptracees(const Tracee *ptracer, pid_t pid, word_t wait_options)
{
	return (get_ptracee(ptracer, pid, false, false, wait_options) != NULL);
}

/**
 * Return the entry related to the tracee @pid.  If no entry were
 * found, a new one is created if @create is true, otherwise NULL is
 * returned.
 */
Tracee *get_tracee(const Tracee *current_tracee, pid_t pid, bool create)
{
	Tracee *tracee;

	/* Don't reset the memory collector if the searched tracee is
	 * the current one: there's likely pointers to the
	 * sub-allocated data in the caller.  */
	if (current_tracee != NULL && current_tracee->pid == pid)
		return (Tracee *)current_tracee;

	LIST_FOREACH(tracee, bucket_of(pid), bucket_link) {
		if (tracee->pid == pid) {
			talloc_free_children(tracee->ctx);

			return tracee;
		}
	}

	return (create ? new_tracee(pid) : NULL);
}

void set_tracee_pid(Tracee *tracee, pid_t pid)
{
	tracee->pid = pid;
	tracee->tgid = pid;
	LIST_REMOVE(tracee, bucket_link);
	LIST_INSERT_HEAD(bucket_of(pid), tracee, bucket_link);
}

/**
 * Mark tracee as terminated and optionally take action.
 */
void terminate_tracee(Tracee *tracee)
{
	tracee->terminated = true;
	terminations_pending = true;

	/* Case where the terminated tracee is marked
	   to kill all tracees on exit.
	*/
	if (tracee->killall_on_exit) {
		VERBOSE(tracee, 1, "terminating all tracees on exit");
		kill_all_tracees();
	}
}

/**
 * Free all tracees marked as terminated.
 */
void free_terminated_tracees()
{
	Tracee *next;

	if (!terminations_pending)
		return;
	terminations_pending = false;

	/* Items can't be deleted when using LIST_FOREACH.  */
	next = tracees.lh_first;
	while (next != NULL) {
		Tracee *tracee = next;
		next = tracee->link.le_next;

		if (tracee->terminated)
			TALLOC_FREE(tracee);
	}
}

/* Read host process IDs, not PRoot's guest /proc view.  /proc/<tid>/status
 * works for non-leader threads too, even though /proc lists only leaders. */
static int read_task_ids(pid_t pid, long *tgid, long *ppid, long *tracer)
{
	char path[64], line[256];
	FILE *file;
	int status;

	*tgid = *ppid = *tracer = -1;
	snprintf(path, sizeof(path), "/proc/%d/status", (int) pid);
	file = fopen(path, "re");
	if (file == NULL)
		return -errno;

	while (fgets(line, sizeof(line), file) != NULL) {
		if (strncmp(line, "Tgid:", 5) == 0)
			*tgid = strtol(line + 5, NULL, 10);
		else if (strncmp(line, "PPid:", 5) == 0)
			*ppid = strtol(line + 5, NULL, 10);
		else if (strncmp(line, "TracerPid:", 10) == 0)
			*tracer = strtol(line + 10, NULL, 10);
	}
	status = ferror(file) || *tgid <= 0 || *ppid < 0 || *tracer < 0
		? -EIO : 0;
	fclose(file);
	return status;
}

/* Recover only a delivered creation event whose GETEVENTMSG succeeded but
 * returned zero.  The creator is still stopped at that event, and its real
 * clone flags have already been read.  Require exactly one uninitialized,
 * PRoot-traced candidate of the right kind and relationship.  Never infer
 * clone flags from PPid, substitute a process for a thread, or choose by PID
 * order.  Include tracees whose initial SIGSTOP was handled before this event.
 * Missing events and ambiguous candidates need separate recovery logic. */
static int find_event_child(Tracee *parent, word_t clone_flags, unsigned long *result)
{
	char path[64];
	long parent_tgid, parent_ppid, tracer;
	const long self = (long) getpid();
	const bool thread = (clone_flags & CLONE_THREAD) != 0;
	struct dirent *entry;
	DIR *dir;
	unsigned long found = 0;
	int status;

	status = read_task_ids(parent->pid, &parent_tgid, &parent_ppid, &tracer);
	if (status < 0)
		return status;
	if (tracer != self)
		return -ESRCH;

	if (thread)
		snprintf(path, sizeof(path), "/proc/%ld/task", parent_tgid);
	else
		snprintf(path, sizeof(path), "/proc");
	dir = opendir(path);
	if (dir == NULL)
		return -errno;

	status = 0;
	for (;;) {
		long pid, tgid, ppid;
		char *end;
		Tracee *known;

		errno = 0;
		entry = readdir(dir);
		if (entry == NULL) {
			if (errno != 0)
				status = -errno;
			break;
		}
		if (entry->d_name[0] < '0' || entry->d_name[0] > '9')
			continue;
		errno = 0;
		pid = strtol(entry->d_name, &end, 10);
		if (errno != 0 || *end != '\0' || pid <= 0 || pid > INT_MAX ||
		    pid == parent->pid)
			continue;
		known = get_tracee(NULL, (pid_t) pid, false);
		if (known != NULL && (known->terminated || known->exe != NULL ||
		                     known->parent != NULL))
			continue;
		if (read_task_ids((pid_t) pid, &tgid, &ppid, &tracer) < 0 ||
		    tracer != self)
			continue;
		if (thread) {
			if (tgid != parent_tgid)
				continue;
		} else {
			long expected_ppid = (clone_flags & CLONE_PARENT) != 0
				? parent_ppid : parent_tgid;
			if (tgid != pid || ppid != expected_ppid)
				continue;
		}
		if (found != 0) {
			status = -EAGAIN;
			break;
		}
		found = (unsigned long) pid;
	}
	closedir(dir);
	if (status < 0)
		return status;
	if (found == 0)
		return -ESRCH;
	*result = found;
	return 0;
}

/**
 * Make new @parent's child inherit from it.  Depending on
 * @clone_flags, some information are copied or shared.  This function
 * returns -errno if an error occured, otherwise 0.
 */
static int new_child_setup(Tracee *parent, unsigned long pid, word_t clone_flags);

int new_child(Tracee *parent, word_t clone_flags)
{
	bool flags_known;
	unsigned long pid;
	int status;

	/* If the tracee calls clone(2) with the CLONE_VFORK flag,
	 * PTRACE_EVENT_VFORK will be delivered instead [...];
	 * otherwise if the tracee calls clone(2) with the exit signal
	 * set to SIGCHLD, PTRACE_EVENT_FORK will be delivered [...]
	 *
	 * -- ptrace(2) man-page
	 *
	 * That means we have to check if it's actually a clone(2) in
	 * order to get the right flags.
	 */
	status = fetch_regs(parent);
	flags_known = (status >= 0);
	if (status >= 0 && get_sysnum(parent, CURRENT) == PR_clone)
		clone_flags = peek_reg(parent, CURRENT, SYSARG_1);
	else if (status >= 0 && get_sysnum(parent, CURRENT) == PR_clone3) {
		word_t args = peek_reg(parent, CURRENT, SYSARG_1);
		word_t flags = peek_uint64(parent, args);
		flags_known = false;
		if (errno == 0) {
			word_t exit_signal = peek_uint64(parent, args + 4 * sizeof(uint64_t));
			if (errno == 0) {
				clone_flags = (flags & ~(word_t) 0xFF) | (exit_signal & 0xFF);
				flags_known = true;
			}
		}
	}

	/* Get the pid of the parent's new child.  */
	status = ptrace(PTRACE_GETEVENTMSG, parent->pid, NULL, &pid);
	if (status < 0) {
		note(parent, WARNING, SYSTEM, "ptrace(GETEVENTMSG)");
		return status;
	}
	if (pid == 0) {
		static bool warned;

		status = flags_known ? find_event_child(parent, clone_flags, &pid) : -EIO;
		/* A thread creator can reach clone's exit while its child is held.
		 * vfork cannot: its creator waits for the child, so do not defer it. */
		if (status == -EAGAIN && (clone_flags & CLONE_THREAD) != 0 &&
		    (clone_flags & CLONE_VFORK) == 0) {
			parent->pending_clone_flags = clone_flags;
			parent->restart_how = PTRACE_SYSCALL;
			return 0;
		}
		if (status < 0) {
			note(parent, WARNING, USER,
			     "zero fork-event pid for task %d: cannot safely identify child (%s); leaving it uninitialized",
			     (int) parent->pid, strerror(-status));
			return status;
		}
		if (!warned) {
			warned = true;
			note(parent, WARNING, USER,
			     "kernel returned a zero fork-event pid; using procfs child discovery");
		}
		VERBOSE(parent, 1, "recovered child %lu for task %d from procfs",
			pid, (int) parent->pid);
	}

	return new_child_setup(parent, pid, clone_flags);
}

static int new_child_setup(Tracee *parent, unsigned long pid, word_t clone_flags)
{
	int ptrace_options;
	Tracee *child;

	child = get_tracee(parent, (pid_t) pid, true);
	if (child == NULL) {
		note(parent, WARNING, SYSTEM, "running out of memory");
		return -ENOMEM;
	}

	/* Sanity checks.  */
	assert(child != NULL
	    && child->exe == NULL
	    && child->fs->cwd == NULL
	    && child->fs->bindings.pending == NULL
	    && child->fs->bindings.guest == NULL
	    && child->fs->bindings.host == NULL
	    && child->qemu == NULL
	    && child->glue == NULL
	    && child->parent == NULL
	    && child->as_ptracee.ptracer == NULL);

	child->verbose = parent->verbose;
	child->seccomp = parent->seccomp;
	child->sysexit_pending = parent->sysexit_pending;
#ifdef HAS_POKEDATA_WORKAROUND
	child->pokedata_workaround_stub_addr = parent->pokedata_workaround_stub_addr;
#endif
#ifdef ARCH_ARM64
	child->is_aarch32 = parent->is_aarch32;
#endif

	/* If CLONE_VM is set, the calling process and the child
	 * process run in the same memory space [...] any memory
	 * mapping or unmapping performed with mmap(2) or munmap(2) by
	 * the child or calling process also affects the other
	 * process.
	 *
	 * If CLONE_VM is not set, the child process runs in a
	 * separate copy of the memory space of the calling process at
	 * the time of clone().  Memory writes or file
	 * mappings/unmappings performed by one of the processes do
	 * not affect the other, as with fork(2).
	 *
	 * -- clone(2) man-page
	 */
	TALLOC_FREE(child->heap);
	child->heap = ((clone_flags & CLONE_VM) != 0)
		? talloc_reference(child, parent->heap)
		: talloc_memdup(child, parent->heap, sizeof(Heap));
	if (child->heap == NULL)
		return -ENOMEM;

	child->load_info = talloc_reference(child, parent->load_info);

	/* If CLONE_PARENT is set, then the parent of the new child
	 * (as returned by getppid(2)) will be the same as that of the
	 * calling process.
	 *
	 * If CLONE_PARENT is not set, then (as with fork(2)) the
	 * child's parent is the calling process.
	 *
	 * -- clone(2) man-page
	 */
	if ((clone_flags & CLONE_PARENT) != 0)
		child->parent = parent->parent;
	else
		child->parent = parent;
	if (child->parent != NULL)
		child->parent->nb_children++;

	/* Remember if this child belongs to the same thread group as
	 * its parent.  This is currently useful for ptrace emulation
	 * only but it deserves to be extended to support execve(2)
	 * specificity (ie. when a thread calls execve(2), its pid
	 * gets replaced by the pid of its thread group leader).  */
	child->clone = ((clone_flags & CLONE_THREAD) != 0);
	child->tgid = child->clone ? parent->tgid : child->pid;

	/* Depending on how the new process is created, it may be
	 * automatically traced by the parent's tracer.  */
	ptrace_options = ( clone_flags == 0			? PTRACE_O_TRACEFORK
			: (clone_flags & 0xFF) == SIGCHLD	? PTRACE_O_TRACEFORK
			: (clone_flags & CLONE_VFORK) != 0	? PTRACE_O_TRACEVFORK
			: 					  PTRACE_O_TRACECLONE);
	if (parent->as_ptracee.ptracer != NULL
	    && (   (ptrace_options & parent->as_ptracee.options) != 0
		|| (clone_flags & CLONE_PTRACE) != 0)) {
		attach_to_ptracer(child, parent->as_ptracee.ptracer);

		/* All these flags are inheritable, no matter why this
		 * child is being traced.  */
		child->as_ptracee.options |= (parent->as_ptracee.options
					      & ( PTRACE_O_TRACECLONE
						| PTRACE_O_TRACEEXEC
						| PTRACE_O_TRACEEXIT
						| PTRACE_O_TRACEFORK
						| PTRACE_O_TRACESYSGOOD
						| PTRACE_O_TRACEVFORK
						| PTRACE_O_TRACEVFORKDONE));
	}

	/* If CLONE_FS is set, the parent and the child process share
	 * the same file system information.  This includes the root
	 * of the file system, the current working directory, and the
	 * umask.  Any call to chroot(2), chdir(2), or umask(2)
	 * performed by the parent process or the child process also
	 * affects the other process.
	 *
	 * If CLONE_FS is not set, the child process works on a copy
	 * of the file system information of the parent process at the
	 * time of the clone() call.  Calls to chroot(2), chdir(2),
	 * umask(2) performed later by one of the processes do not
	 * affect the other process.
	 *
	 * -- clone(2) man-page
	 */
	TALLOC_FREE(child->fs);
	if ((clone_flags & CLONE_FS) != 0) {
		/* File-system name-space is shared.  */
		child->fs = talloc_reference(child, parent->fs);
	}
	else {
		/* File-system name-space is copied.  */
		child->fs = talloc_zero(child, FileSystemNameSpace);
		if (child->fs == NULL)
			return -ENOMEM;

		child->fs->cwd = talloc_strdup(child->fs, parent->fs->cwd);
		if (child->fs->cwd == NULL)
			return -ENOMEM;
		talloc_set_name_const(child->fs->cwd, "$cwd");

		/* Bindings are shared across file-system name-spaces since a
		 * "mount --bind" made by a process affects all other processes
		 * under Linux.  Actually they are copied when a sub
		 * reconfiguration occured (nested proot or chroot(2)).  */
		child->fs->bindings.guest = talloc_reference(child->fs, parent->fs->bindings.guest);
		child->fs->bindings.host  = talloc_reference(child->fs, parent->fs->bindings.host);
	}

	/* The path to the executable is unshared only once the child
	 * process does a call to execve(2).  */
	child->exe = talloc_reference(child, parent->exe);

	child->qemu = talloc_reference(child, parent->qemu);
	child->glue = talloc_reference(child, parent->glue);

	child->host_ldso_paths  = talloc_reference(child, parent->host_ldso_paths);
	child->guest_ldso_paths = talloc_reference(child, parent->guest_ldso_paths);

	child->tool_name = parent->tool_name;

	inherit_extensions(child, parent, clone_flags);

	/* Restart the child tracee if it was already alive but
	 * stopped until that moment.  */
	if (child->sigstop == SIGSTOP_PENDING) {
		bool keep_stopped = false;

		child->sigstop = SIGSTOP_ALLOWED;

		/* Notify its ptracer if it is ready to be traced.  */
		if (child->as_ptracee.ptracer != NULL) {
			/* Sanity check.  */
			assert(!child->as_ptracee.tracing_started);

#ifndef __W_STOPCODE
	#define __W_STOPCODE(sig) ((sig) << 8 | 0x7f)
#endif
			keep_stopped = handle_ptracee_event(child, __W_STOPCODE(SIGSTOP));

			/* Note that this event was already handled by
			 * PRoot since child->as_ptracee.ptracer was
			 * NULL up to now.  */
			child->as_ptracee.event4.proot.pending = false;
			child->as_ptracee.event4.proot.value   = 0;
		}

		if (!keep_stopped)
			(void) restart_tracee(child, 0);
	}

	VERBOSE(child, 1, "vpid %" PRIu64 ": pid %d", child->vpid, child->pid);

	return 0;
}

/* A clone event occurs before the creating thread returns from clone.  For an
 * ambiguous thread lookup, obtain the exact TID at that syscall's exit instead
 * of guessing from the order of /proc entries.  Called before normal syscall
 * exit handling so the registers and inherited tracee state are still intact. */
void finish_pending_child(Tracee *parent)
{
	word_t flags = parent->pending_clone_flags;
	word_t pid;
	long ptgid, ppid, tracer, ctgid;
	Tracee *known;
	int status;

	parent->pending_clone_flags = 0;
	status = fetch_regs(parent);
	if (status < 0)
		goto failed;
	pid = peek_reg(parent, CURRENT, SYSARG_RESULT);
	if (pid == 0 || pid > INT_MAX || pid == (word_t) parent->pid) {
		status = -ESRCH;
		goto failed;
	}
	/* Validate the host TID before setup.  This also rejects an unmappable
	 * PID from a different PID namespace instead of adopting an unrelated task. */
	status = read_task_ids(parent->pid, &ptgid, &ppid, &tracer);
	if (status < 0)
		goto failed;
	status = read_task_ids((pid_t) pid, &ctgid, &ppid, &tracer);
	if (status < 0)
		goto failed;
	known = get_tracee(NULL, (pid_t) pid, false);
	if (ctgid != ptgid || tracer != (long) getpid() ||
	    (known != NULL && (known->terminated || known->exe != NULL || known->parent != NULL))) {
		status = -ESRCH;
		goto failed;
	}
	status = new_child_setup(parent, (unsigned long) pid, flags);
	if (status < 0)
		goto failed;
	VERBOSE(parent, 1, "recovered thread %lu for task %d from clone return",
		(unsigned long) pid, (int) parent->pid);
	return;
failed:
	note(parent, WARNING, USER, "cannot recover thread at clone exit for task %d (error %d)",
		(int) parent->pid, status);
}

/**
 * Helper for swap_config().
 */
static void reparent_config(Tracee *new_parent, Tracee *old_parent)
{
	new_parent->verbose = old_parent->verbose;

#define REPARENT(field) do {							\
		talloc_reparent(old_parent, new_parent, old_parent->field);	\
		new_parent->field = old_parent->field;				\
	} while(0);

	REPARENT(fs);
	REPARENT(exe);
	REPARENT(qemu);
	REPARENT(glue);
	REPARENT(extensions);

#undef REPARENT
}

/**
 * Swap configuration (pointers and parentality) between @tracee1 and @tracee2.
 */
int swap_config(Tracee *tracee1, Tracee *tracee2)
{
	Tracee *tmp;

	tmp = talloc_zero(tracee1->ctx, Tracee);
	if (tmp == NULL)
		return -ENOMEM;

	reparent_config(tmp,     tracee1);
	reparent_config(tracee1, tracee2);
	reparent_config(tracee2, tmp);

	return 0;
}

/* Send the KILL signal to all tracees.  */
void kill_all_tracees()
{
	Tracee *tracee;

	LIST_FOREACH(tracee, &tracees, link)
		kill(tracee->pid, SIGKILL);
}

Tracees *get_tracees_list_head() {
	return &tracees;
}
