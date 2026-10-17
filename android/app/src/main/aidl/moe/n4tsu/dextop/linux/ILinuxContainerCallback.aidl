package moe.n4tsu.dextop.linux;

/** Events from the shell-side Linux container service. */
oneway interface ILinuxContainerCallback {
    /** phase: download, verify, extract, configure, provision, done, error. percent is -1 when unknown. */
    void onProgress(String containerId, String phase, int percent, String message) = 1;
    /** A line of install or runtime log output. */
    void onLog(String containerId, String line) = 2;
    /** The container list or a container state changed; call listContainers() again. */
    void onContainersChanged() = 3;
    /**
     * An application window event as JSON: {"type": "map" | "configure" |
     * "title" | "unmap", "xid", "popup", ...}; see LinuxAppSession.
     */
    void onWindowEvent(String containerId, String json) = 4;
}
