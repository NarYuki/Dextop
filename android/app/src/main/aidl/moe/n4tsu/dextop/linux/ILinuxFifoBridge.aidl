package moe.n4tsu.dextop.linux;
import moe.n4tsu.dextop.linux.ILinuxFifoResult;
/** Blocking FIFO opens run outside Binder's thread pool. */
oneway interface ILinuxFifoBridge {
    void request(int operation, int flags, String id, ILinuxFifoResult result);
}
