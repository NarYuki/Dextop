package moe.n4tsu.dextop.input;

interface IPrivilegedInputCallback {
    oneway void onInputState(String category, String message);
    oneway void onThreeFingerGesture();
    oneway void onHaptic(boolean strong);
}
