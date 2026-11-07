# Sourced by Dextop's application launcher, desktop session and login shells.
[ ! -r /etc/dextop/theme.env ] || . /etc/dextop/theme.env
_dextop_theme_stamp="${HOME:-/root}/.config/dextop/toolkit-theme-v1"
_dextop_theme_applied=
[ ! -r "$_dextop_theme_stamp" ] || read -r _dextop_theme_applied <"$_dextop_theme_stamp"
if [ "$_dextop_theme_applied" != "${DEXTOP_THEME:-light}" ] && command -v python3 >/dev/null 2>&1 && [ -r /usr/local/libexec/dextop-toolkit-theme ]; then
    python3 /usr/local/libexec/dextop-toolkit-theme --mode "${DEXTOP_THEME:-light}" ||
        echo 'Dextop toolkit theme configuration failed' >&2
fi
unset _dextop_theme_stamp _dextop_theme_applied
if [ -z "${QT_QPA_PLATFORMTHEME:-}" ] && { command -v qt5ct >/dev/null 2>&1 || command -v qt6ct >/dev/null 2>&1; }; then
    # qt6ct also advertises the qt5ct key; one key serves both Qt generations.
    export QT_QPA_PLATFORMTHEME=qt5ct
fi
if [ -r "${HOME:-/root}/.config/dextop/gtk2-theme.rc" ] && [ -z "${GTK2_RC_FILES:-}" ]; then
    export GTK2_RC_FILES="/etc/gtk-2.0/gtkrc:${HOME:-/root}/.config/dextop/gtk2-theme.rc:${HOME:-/root}/.gtkrc-2.0"
fi
