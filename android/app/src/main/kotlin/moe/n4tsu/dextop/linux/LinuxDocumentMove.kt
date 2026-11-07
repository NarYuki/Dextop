package moe.n4tsu.dextop.linux

/** Provider operations are fallible; keep replaced data until the source is committed. */
internal object LinuxDocumentMove {
    fun <T : Any> replace(
        source: T,
        destination: T?,
        backup: (T) -> T?,
        move: ((T) -> T?)?,
        rename: ((T) -> T?)?,
        undoMove: (T) -> Unit,
        restore: (T) -> Unit,
        discard: (T) -> Unit,
    ): Int {
        val saved = destination?.let { backup(it) ?: return 5 /* EIO */ }
        var current = source
        var moved = false
        var committed = false
        try {
            if (move != null) {
                current = move(current) ?: return 18 /* EXDEV: allow caller's copy fallback */
                moved = true
            }
            if (rename != null) current = rename(current) ?: return 5
            committed = true
            // Failed cleanup leaves a recoverable backup, not a failed source move.
            if (saved != null) runCatching { discard(saved) }
            return 0
        } finally {
            if (!committed) {
                if (moved) runCatching { undoMove(current) }
                // If restoration fails, retain the backup under its temporary name.
                if (saved != null) runCatching { restore(saved) }
            }
        }
    }
}
