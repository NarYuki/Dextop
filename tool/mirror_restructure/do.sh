#!/bin/bash
# do.sh Class field regex [extract.py options...]   -- move + fix, roll back on failure
S=$(dirname "$(readlink -f "$0")"); K=$S/../../android/app/src/main/kotlin/moe/n4tsu/dextop
cls=$1; field=$2; re=$3; shift 3
cp $K/MirrorService.kt $S/bak.MirrorService.kt; cp $S/state.json $S/bak.state.json
cd $S && python3 extract.py move $cls $field "$re" --base DesktopSession --base OverlayMenu "$@" || exit 1
out=$(python3 extract.py fix $cls $field 2>&1); echo "$out" | tail -25
if echo "$out" | grep -q 'final compile rc 0 0'; then echo "OK $cls $(wc -l < $K/$cls.kt) lines; MirrorService $(wc -l < $K/MirrorService.kt)"; 
else echo "FAILED $cls -> kept for inspection as $S/failed.$cls.kt, rolled back"; cp $K/$cls.kt $S/failed.$cls.kt; cp $K/MirrorService.kt $S/failed.$cls.MirrorService.kt; rm -f $K/$cls.kt; cp $S/bak.MirrorService.kt $K/MirrorService.kt; cp $S/bak.state.json $S/state.json; fi
