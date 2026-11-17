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
Since the 16 KB page-size migration (Google Play, Android 15+), every ELF file
here is rebuilt from source by tool/build_16k_native_libs.sh with NDK r28 and
`-z max-page-size=16384`: PulseAudio 13.0 (pulseaudio-13.0.tar.xz, SHA-256
961b23ca1acfd28f2bc87414c27bb40e12436efcf2158d29721b1e89f3f28057) with
pulseaudio-13.0-android.patch (bionic build fixes only), libsndfile 1.0.31,
libltdl from libtool 2.4.7, and the DirectAudio sink in this directory.
libffi is the separate 16 KB build from third_party/libffi.
The earlier DD prebuilt host libraries (4 KB-aligned) are no longer shipped;
tool/stage_deck_audio.py and tool/experiments/deck_audio/build.ps1 describe that old path.
manifest.json records the exact staged files and SHA-256 values.
