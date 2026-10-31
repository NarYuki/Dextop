param([Parameter(Mandatory=$true)][string]$Zig)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$out = Join-Path $repo 'android/app/src/main/resources/moe/n4tsu/dextop/linux/gaming'
New-Item -ItemType Directory -Force $out | Out-Null
& $Zig cc -target aarch64-linux-gnu.2.31 -shared -fPIC -O2 -pthread -s `
    "$repo/android/app/src/main/cpp/linux_deck_nativewindow.c" -o "$out/libdextop_deck_nativewindow-aarch64.bin"
if ($LASTEXITCODE) { throw 'Failed to build Deck AHB allocation proxy' }
& $Zig cc -target aarch64-linux-gnu.2.31 -shared -fPIC '-Wl,-Bsymbolic' -O2 -pthread -s `
    "-I$repo/third_party/vulkan-headers/include" "$repo/android/app/src/main/cpp/linux_vulkan_xvfb.c" `
    -o "$out/libdextop_vk_xvfb-aarch64.bin"
if ($LASTEXITCODE) { throw 'Failed to build Xvfb Vulkan capability layer' }
$sources = Get-ChildItem (Join-Path $repo 'third_party/droiddeck-preload') -Filter '*.c' | ForEach-Object FullName
& $Zig cc -target aarch64-linux-gnu.2.31 -shared -fPIC -DDEXTOP_BINFMT -O2 -pthread -s `
    -o "$repo/android/app/src/main/jniLibs/arm64-v8a/libdextop_blsession.so" @sources `
    "$repo/android/app/src/main/cpp/linux_wine_memory.c" "$repo/android/app/src/main/cpp/linux_socket_markers.c" `
    "$repo/android/app/src/main/cpp/linux_credentials.c" "$repo/android/app/src/main/cpp/linux_semaphores.c" -ldl
if ($LASTEXITCODE) { throw 'Failed to build shared ARM64 session compatibility' }
foreach ($arch in @('x86_64', 'i386')) {
    $target = if ($arch -eq 'i386') { 'x86-linux-gnu.2.31' } else { 'x86_64-linux-gnu.2.31' }
    & $Zig cc -target $target -fPIC -pie -O2 -s -o "$out/runtime-check-$arch.bin" "$repo/tool/tests/linux_x86_runtime_check.c" -ldl
    if ($LASTEXITCODE) { throw "Failed to build $arch runtime check" }
    # Zig 0.13's i386 compiler-rt requires local resolution of its own symbols.
    [string[]]$link = @(if ($arch -eq 'i386') { '-Wl,-Bsymbolic' })
    & $Zig cc -target $target -shared -fPIC -O2 -pthread -s @link -o "$out/libblsession-$arch.bin" @sources -ldl
    if ($LASTEXITCODE) { throw "Failed to build $arch session compatibility" }
    & $Zig cc -target $target -shared -fPIC -O2 -s -o "$out/libdextop_unixsock-$arch.bin" `
        "$repo/android/app/src/main/cpp/linux_unixsock.c" "$repo/android/app/src/main/cpp/linux_socket_markers.c" -ldl
    if ($LASTEXITCODE) { throw "Failed to build $arch socket compatibility" }
}
& $Zig cc -target aarch64-linux-gnu.2.31 -shared -fPIC -DDEXTOP_BINFMT -O2 -s -o "$out/libdextop_gamesock-aarch64.bin" `
    "$repo/android/app/src/main/cpp/linux_unixsock.c" -ldl
if ($LASTEXITCODE) { throw 'Failed to build ARM64 Wine socket compatibility' }

foreach ($pair in @(@('aarch64', 'aarch64-linux-gnu.2.31'), @('x86_64', 'x86_64-linux-gnu.2.31'), @('i386', 'x86-linux-gnu.2.31'))) {
    & $Zig cc -target $pair[1] -shared -fPIC -O2 -pthread -s -o "$out/libdextop_padgrab-$($pair[0]).bin" `
        "$repo/android/app/src/main/cpp/linux_pad_grab.c" "$repo/android/app/src/main/cpp/linux_android_pads.c" "$repo/android/app/src/main/cpp/linux_android_usb.c" "$repo/android/app/src/main/cpp/linux_android_camera.c" -ldl
    if ($LASTEXITCODE) { throw "Failed to build $($pair[0]) controller grab" }
}

# /proc/stat, uptime and loadavg stand-ins, controllers, USB, cameras and picked folders for the Android execution mode.
& $Zig cc -target aarch64-linux-gnu.2.31 -shared -fPIC -O2 -pthread -s -o "$repo/android/app/src/main/jniLibs/arm64-v8a/libdextop_androidproc.so" `
    "$repo/android/app/src/main/cpp/linux_android_proc.c" "$repo/android/app/src/main/cpp/linux_android_folders.c" "$repo/android/app/src/main/cpp/linux_android_pads.c" "$repo/android/app/src/main/cpp/linux_android_usb.c" "$repo/android/app/src/main/cpp/linux_android_camera.c" -ldl
if ($LASTEXITCODE) { throw 'Failed to build Android-mode /proc stand-ins' }

& $Zig cc -target x86_64-windows-gnu -O2 -s -o "$out/dextop-wine-tray-x86_64.exe" `
    "$repo/android/app/src/main/cpp/linux_wine_tray_metadata.c" -luser32
if ($LASTEXITCODE) { throw 'Failed to build Wine tray metadata bridge' }

& $Zig cc -target aarch64-linux-gnu.2.31 -shared -fPIC -DDEXTOP_BINFMT -O2 -pthread -s `
    "$repo/third_party/droiddeck-fastpath/fastpath.c" -ldl -o "$out/libblfastpath-aarch64.bin"
if ($LASTEXITCODE) { throw 'Failed to build PRoot guest fast path' }
