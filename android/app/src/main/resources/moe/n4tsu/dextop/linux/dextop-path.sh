# Dextop applications and login shells share the distribution's executable paths.
# Preserve user/vendor paths and their precedence. Never add '.' or an empty entry.
for dextop_path_entry in /usr/local/sbin /usr/local/bin /usr/sbin /usr/bin /sbin /bin /usr/local/games /usr/games "${HOME:-/root}/.local/bin" /opt/bin; do
    case ":${PATH-}:" in
        *":$dextop_path_entry:"*) ;;
        *) PATH="${PATH:+$PATH:}$dextop_path_entry" ;;
    esac
done
export PATH
unset dextop_path_entry
[ ! -r /etc/profile.d/dextop-toolkit.sh ] || . /etc/profile.d/dextop-toolkit.sh

[ ! -r /etc/dextop/printing.env ] || . /etc/dextop/printing.env
