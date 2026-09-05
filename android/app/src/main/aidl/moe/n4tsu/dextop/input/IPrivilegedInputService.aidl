package moe.n4tsu.dextop.input;

import moe.n4tsu.dextop.input.IPrivilegedInputCallback;

interface IPrivilegedInputService {
    void destroy() = 16777114;
    int protocolVersion() = 1;
    String probe() = 2;
    void configure(in int[] config) = 3;
    boolean start(IPrivilegedInputCallback callback) = 4;
    void setOutputReady(boolean ready) = 5;
    void inject(in int[] events) = 6;
    void setKeyboardVisible(boolean visible) = 7;
    void stop(String reason) = 8;
    boolean injectKeyboard(int keyCode, int action, int metaState, int repeatCount) = 9;
    void setGamepadVisible(boolean visible) = 10;
    boolean injectGamepad(int code, int value) = 11;
}
