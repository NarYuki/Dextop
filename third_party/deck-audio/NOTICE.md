# Deck DirectAudio

Android PulseAudio 13 host and native DirectAudio sink, used only by Dextop's
Deck Mode process tree. Debian's PulseAudio and ordinary Linux audio stay in place.

Host libraries and libprotocol-native are from DroidDeck commit
8aa1fdaf708eb44ef7cff7e493bda946989af511:
https://github.com/Droid-Deck/DroidDeck/tree/8aa1fdaf708eb44ef7cff7e493bda946989af511

The adjacent DirectAudio sink source and protocol header are unmodified DD
sources, LGPL-2.1-or-later. The TCP protocol stub is compiled from PulseAudio
v13.0 (LGPL-2.1-or-later), rather than the incompatible glibc/Pulse17 module in
DD's asset archive. PulseAudio source archive SHA-256:
bd7980bc9bd44bd3b3363545dabd19476ebf4d6bb6d2527e7d179256394a6758.
Source: https://github.com/pulseaudio/pulseaudio/tree/v13.0

PulseAudio, libsndfile and libltdl retain their LGPL licensing; see COPYING and
PULSEAUDIO-LICENSE. libffi retains its MIT license in LIBFFI-LICENSE.
Build the native modules with tool/experiments/deck_audio/build.ps1, then stage
with python tool/stage_deck_audio.py. The prebuilt host libraries originate in
DD's pinned checkout; this script does not rebuild those libraries from source.
manifest.json records the exact staged files and SHA-256 values.
