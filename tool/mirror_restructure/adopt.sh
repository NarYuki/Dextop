#!/bin/bash
# adopt.sh Class field name...   -- adopt + fix, roll back on failure
S=$(dirname "$(readlink -f "$0")"); K=$S/../../android/app/src/main/kotlin/moe/n4tsu/dextop
cls=$1; field=$2; shift 2
cp $K/MirrorService.kt $S/bak.MirrorService.kt; cp $S/state.json $S/bak.state.json; cp $K/$cls.kt $S/bak.$cls.kt
cd $S && python3 extract.py adopt $cls "$@" || { cp $S/bak.MirrorService.kt $K/MirrorService.kt; cp $S/bak.state.json $S/state.json; cp $S/bak.$cls.kt $K/$cls.kt; exit 1; }
out=$(python3 extract.py fix $cls $field 2>&1); echo "$out" | tail -12
if echo "$out" | grep -q 'final compile rc 0 0'; then echo "OK $cls"; else echo "FAILED $cls, rolled back"; cp $K/$cls.kt $S/failed.$cls.kt; cp $S/bak.MirrorService.kt $K/MirrorService.kt; cp $S/bak.state.json $S/state.json; cp $S/bak.$cls.kt $K/$cls.kt; fi
