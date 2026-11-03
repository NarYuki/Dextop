#!/bin/sh
set -eu

runtime=${DEXTOP_DARLING_ROOT:-/opt/dextop/macos}
case "$runtime" in /*) ;; *) printf '%s\n' 'Darling runtime path must be absolute' >&2; exit 64;; esac
if [ "$(uname -m)" != aarch64 ]; then
    printf '%s\n' 'This experimental runtime requires Linux ARM64' >&2
    exit 69
fi

missing=0
for component in bin/darling bin/darlingserver libexec/darling/bin/mldr libexec/darling/usr/lib/dyld libexec/darling/sbin/launchd; do
    if [ ! -f "$runtime/$component" ]; then
        printf 'Missing Darling component: %s\n' "$component" >&2
        missing=1
    fi
done
if [ "$missing" -ne 0 ]; then exit 78; fi
for program in bin/darling bin/darlingserver libexec/darling/bin/mldr; do
    if [ ! -x "$runtime/$program" ]; then
        printf 'Darling component is not executable: %s\n' "$program" >&2
        exit 78
    fi
done
if [ "${1:-}" = --check ]; then
    printf '%s\n' 'Darling components present; application compatibility is not yet verified'
    exit 0
fi

case "${HOME:-}" in /*) ;; *) printf '%s\n' 'An absolute HOME is required' >&2; exit 64;; esac
export DARLING_NONROOT=1
export DEXTOP_DARLING_HOST_LIBRARY_PATH="${DEXTOP_DARLING_HOST_LIBRARY_PATH-${LD_LIBRARY_PATH-}}"
export DEXTOP_DARLING_ABSTRACT_RPC=${DEXTOP_DARLING_ABSTRACT_RPC:-1}
export DEXTOP_DARLING_ANON_EXEC=${DEXTOP_DARLING_ANON_EXEC:-1}
export DARLING_INSTALL_PREFIX="$runtime"
export DPREFIX="$HOME/.local/share/dextop/macos/default"
unset DARLING_PROFILE
if [ "$#" -eq 0 ]; then set -- shell; fi
exec "$runtime/bin/darling" "$@"
