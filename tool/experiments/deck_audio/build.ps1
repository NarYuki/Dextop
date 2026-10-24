param(
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$Java = $env:JAVA_HOME,
    [string]$NdkVersion = '28.2.13676358',
    [string]$BuildTools = '36.0.0',
    [string]$KotlinStdlib = ''
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path "$PSScriptRoot/../../..").Path
$work = "$repo/build/deck-directaudio"
$dd = "$repo/build/droiddeck-current"
$pa = "$work/source/pulseaudio-13.0"
$generated = "$work/generated"
$modules = "$work/modules"
$libs = "$dd/app/src/main/jniLibs/arm64-v8a"
if (!(Test-Path "$Sdk/platforms/android-36/android.jar")) { throw 'Set -Sdk to an SDK with platform 36.' }
if (!(Test-Path "$Java/bin/javac.exe")) { throw 'Set -Java to JDK 21.' }
function Invoke-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed: $LASTEXITCODE" }
}
Push-Location $repo
try {
    $revision = (& git -C $dd rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $revision -ne '8aa1fdaf708eb44ef7cff7e493bda946989af511') {
        throw 'The experiment requires the pinned DD checkout 8aa1fda.'
    }
    $archive = "$work/pulseaudio-v13.0.tar.gz"
    if ((Get-FileHash $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne
        'bd7980bc9bd44bd3b3363545dabd19476ebf4d6bb6d2527e7d179256394a6758') {
        throw 'PulseAudio source archive hash mismatch.'
    }
    New-Item -ItemType Directory -Force $generated,$modules,"$work/probe-classes","$work/probe-dex" | Out-Null
    Copy-Item "$dd/tools/aaudio-sink/config.h","$dd/tools/aaudio-sink/ltdl.h" $generated -Force
    New-Item -ItemType Directory -Force "$generated/pulse" | Out-Null
    $version = [IO.File]::ReadAllText("$pa/src/pulse/version.h.in").Replace('@PA_MAJOR@','13').Replace('@PA_MINOR@','0').Replace('@PA_API_VERSION@','12').Replace('@PA_PROTOCOL_VERSION@','33').Replace('@PA_VERSION@','13.0')
    [IO.File]::WriteAllText("$generated/pulse/version.h",$version)
    [IO.File]::WriteAllText("$generated/protocol-declarations.h", "struct pa_module;`nvoid pa__done(struct pa_module *);`n")
    $toolchain = "$Sdk/ndk/$NdkVersion/toolchains/llvm/prebuilt/windows-x86_64"
    $common = @('--target=aarch64-linux-android28','-shared','-fPIC','-O2','-DHAVE_CONFIG_H',
        "-ffile-prefix-map=$repo=/dextop", "-ffile-prefix-map=$($repo.Replace('\','/'))=/dextop",
        '-Wno-error=int-conversion',"-I$generated","-I$pa/src","-I$dd/tools/aaudio-sink",
        "-L$modules","-L$libs",'-l:libpulsecore-13.0.so','-l:libpulsecommon-13.0.so','-l:libpulse.so')
    # libprotocol-native.so must be the Android/Pulse13 binary extracted from pulseaudio.tzst.
    Invoke-Checked "$toolchain/bin/clang.exe" ($common + @('-DUSE_PROTOCOL_NATIVE','-DUSE_TCP_SOCKETS',
        '-include',"$generated/protocol-declarations.h","$pa/src/modules/module-protocol-stub.c",
        '-l:libprotocol-native.so','-o',"$modules/module-native-protocol-tcp.so"))
    Invoke-Checked "$toolchain/bin/clang.exe" ($common + @("-I$dd/tools/directaudio/sink",
        "$dd/tools/directaudio/sink/module-directaudio-native-sink.c",'-laaudio',
        '-o',"$modules/module-directaudio-native-sink.so"))
    foreach ($name in @('module-native-protocol-tcp.so','libprotocol-native.so','module-directaudio-native-sink.so')) {
        $dynamic = & "$toolchain/bin/llvm-readelf.exe" -d "$modules/$name"
        if ($LASTEXITCODE -ne 0 -or ($dynamic -match 'libc.so.6|libpulse.so.0|libpulsecore-17')) {
            throw "Non-Android or mismatched module: $name"
        }
    }
    $jar = "$Sdk/platforms/android-36/android.jar"
    $bt = "$Sdk/build-tools/$BuildTools"
    Invoke-Checked "$Java/bin/javac.exe" @('-source','17','-target','17','-classpath',$jar,
        '-d',"$work/probe-classes","$PSScriptRoot/AudioProbeReceiver.java","$PSScriptRoot/CarBrokerProbeReceiver.java")
    $env:JAVA_HOME = $Java
    $classes = @(Get-ChildItem "$work/probe-classes" -Filter '*.class' -Recurse | ForEach-Object FullName)
    $product = "$repo/build/app/tmp/kotlin-classes/githubRelease/moe/n4tsu/dextop/linux/LinuxDeckAudio.class"
    if (!(Test-Path $product)) { throw 'Compile :app:compileGithubReleaseKotlin before building the product-class probe.' }
    if (!$KotlinStdlib) {
        $KotlinStdlib = (Get-ChildItem "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.3.20" -Recurse -Filter 'kotlin-stdlib-2.3.20.jar' | Select-Object -First 1).FullName
    }
    Invoke-Checked "$bt/d8.bat" (@('--lib',$jar,'--min-api','30','--output',"$work/probe-dex",$product,$KotlinStdlib) + $classes)
    Invoke-Checked "$bt/aapt2.exe" @('link','-I',$jar,'--manifest',"$PSScriptRoot/AndroidManifest.xml",'-o',"$work/probe-manifest.apk")
    $zip = @'
import pathlib, sys, zipfile
work, libs = map(pathlib.Path, sys.argv[1:])
with zipfile.ZipFile(work/'probe-manifest.apk') as src, zipfile.ZipFile(work/'probe-unsigned.apk','w',zipfile.ZIP_STORED) as dst:
    for entry in src.infolist():
        dst.writestr(entry.filename, src.read(entry))
    dst.write(work/'probe-dex/classes.dex', 'classes.dex')
    for name in ['libpulseaudio.so','libpulsecore-13.0.so','libpulsecommon-13.0.so','libpulse.so','libltdl.so','libsndfile.so','libffi.so']:
        dst.write(libs/name,'lib/arm64-v8a/'+name)
    for name in ['module-native-protocol-tcp.so','libprotocol-native.so','module-directaudio-native-sink.so']:
        dst.write(work/'modules'/name,'assets/modules/'+name)
        dst.write(work/'modules'/name,'assets/deck-audio/modules/'+name)
'@
    Invoke-Checked 'python' @('-c',$zip,$work,$libs)
    Invoke-Checked "$bt/zipalign.exe" @('-f','-p','4',"$work/probe-unsigned.apk","$work/probe-aligned.apk")
    Invoke-Checked "$bt/apksigner.bat" @('sign','--ks',"$env:USERPROFILE/.android/debug.keystore",
        '--ks-pass','pass:android','--out',"$work/probe.apk","$work/probe-aligned.apk")
    Write-Output "Built isolation probe: $work/probe.apk"
} finally { Pop-Location }
