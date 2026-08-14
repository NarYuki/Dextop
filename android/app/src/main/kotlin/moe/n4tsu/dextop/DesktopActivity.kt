package moe.n4tsu.dextop

/**
 * Owns Dextop's control UI when it is opened inside the desktop display.
 *
 * Keeping this as a distinct component prevents One UI from moving the phone
 * MainActivity task between displays. MainActivity deliberately only records
 * itself as the phone orientation/task owner while it is on display 0.
 */
class DesktopActivity : MainActivity()
