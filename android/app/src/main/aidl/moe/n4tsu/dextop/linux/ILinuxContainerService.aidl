package moe.n4tsu.dextop.linux;

import android.view.Surface;
import moe.n4tsu.dextop.linux.ILinuxContainerCallback;

/**
 * Shizuku UserService running as the shell user. It owns every container
 * under /data/local/tmp/dextop/linux; the app never executes Linux code.
 */
interface ILinuxContainerService {
    void destroy() = 16777114;
    int protocolVersion() = 1;
    String probe() = 2;
    /** nativeLibraryDir of the installed APK, where libproot.so lives. */
    void initialize(String nativeLibraryDir, ILinuxContainerCallback callback) = 3;
    /** JSON array of containers: id, name, distro, state, bytes, created. */
    String listContainers() = 4;
    /** JSON array of installable distributions. */
    String catalog() = 5;
    /** Creates and installs a container in the background; returns its id. */
    String install(String name, String distroId) = 6;
    boolean cancelInstall(String containerId) = 7;
    boolean remove(String containerId) = 8;
    /** Re-runs package provisioning for an installed container. */
    boolean repair(String containerId) = 9;
    /** Runs a shell command inside the container (debugging); returns exit code and output as JSON. */
    String runCommand(String containerId, String command, int timeoutSeconds) = 10;

    /** Starts Xvfb and IceWM for the container at the given size; returns the X display number. */
    int startDesktop(String containerId, int width, int height, int dpi) = 11;
    void stopDesktop(String containerId) = 12;
    /** Draws the container's screen into [surface] (a Plasma v2 window) until detached. */
    boolean attachSurface(String containerId, in Surface surface) = 13;
    void detachSurface(String containerId) = 14;

    /** action: 0 move, 1 button press, 2 button release. Coordinates are X screen pixels. */
    oneway void pointer(String containerId, int action, int x, int y, int button) = 15;
    /** Linux evdev key code (KEY_*), as reported by Android's KeyEvent.getScanCode(). */
    oneway void key(String containerId, int evdevCode, boolean down) = 16;
    /** Text committed by the Android IME. */
    oneway void text(String containerId, String text) = 17;
    /** Wheel steps; positive dy scrolls down, positive dx scrolls right. */
    oneway void scroll(String containerId, int dx, int dy) = 18;
    /** 0: no desktop, 1: X is up and the window manager is starting, 2: ready. */
    int desktopState(String containerId) = 19;

    // Application windows. The input methods above (key, text, scroll) reach
    // them when containerId is "<id>#apps"; X windows are identified by xid.
    /** Desktop entries as JSON [{id, name, categories, icon (base64 PNG or "")}]. */
    String listApps(String containerId, String locale) = 20;
    /** Starts (or reuses) the application display sized to the Plasma display; replays map events. */
    int startApps(String containerId, int width, int height, int dpi) = 21;
    void launchApp(String containerId, String desktopId) = 22;
    void stopApps(String containerId) = 23;
    boolean attachWindow(String containerId, int xid, in Surface surface) = 24;
    void detachWindow(String containerId, int xid) = 25;
    /** Places the window at its Plasma content area, in display coordinates. */
    oneway void configureWindow(String containerId, int xid, int x, int y, int width, int height) = 26;
    oneway void activateWindow(String containerId, int xid) = 27;
    oneway void closeWindow(String containerId, int xid) = 28;
    /** Pointer input in window coordinates; action 0 move, 1 press, 2 release. */
    oneway void windowPointer(String containerId, int xid, int action, int x, int y, int button) = 29;
    String installWithOptions(String name, String distroId, String optionsJson) = 30;
    boolean setup(String containerId, String optionsJson) = 31;
    String runtimeLogs(String containerId) = 32;
    boolean resetSession(String containerId, boolean force) = 33;
    /** Global Linux settings as JSON: {"gpu": "virgl" | "software", "hardwareAvailable", "rendererRunning": [ids]}. */
    String linuxSettings() = 34;
    /** Merges [json] into the settings and returns the result like linuxSettings(). */
    String setLinuxSettings(String json) = 35;
}
