# Galaxy wget comparison, 2026-11-11

**Cause confirmed in the VPN UI:** Mullvad reports “デバイスが無効です”
(device invalid) and says the device was removed and requires login again.
It explicitly says proceeding to login releases its Internet block. That button
was not pressed: the user requires VPN preservation. Authentication and VPN
reconnection are required before an Internet download can be retested without
bypassing the VPN. No account credentials were accessed.

The user requires that the VPN remain enabled. No VPN setting, split-tunnel
exclusion, global DNS setting or Linux resolver setting was changed. No network
bypass is implemented. The current active VPN is `net.mullvad.mullvadvpn`;
`com.ct.client` is a split-tunnel exclusion, **not** the VPN owner.

Device: Galaxy SM-S942Z, serial RFGL22LEFGF, Android 17. Shell-mode rootfs:
`/data/local/tmp/dextop/linux/containers/c40c883b9055e/rootfs`.

## Previous and current PRoot

An isolated `git archive HEAD` (9485910) built the previous PRoot and loader with
the same NDK. The worktree was not reset. Previous and current binaries ran
against the same existing rootfs, binds and environment, with the experimental
fast path unset. The network publisher has no worktree diff against HEAD.

| Test | Result |
| --- | --- |
| Previous PRoot + current runtime preloads | VPN DNS timeout; wget exit 4 |
| Current PRoot + current runtime preloads | VPN DNS timeout; wget exit 4 |
| Previous PRoot + empty preload bindings | Same DNS failure |
| Current PRoot + empty preload bindings | Same DNS failure |

The published Android VPN DNS is `192.0.2.1` on `tun1` (network 103), with VPN
address `10.0.0.1/32`. Thus this resolver is not an accidental test fixture.
A temporary test-only LAN resolver bind answered DNS but TCP still timed out;
that bind was not persisted. Before the user selected VPN preservation, an
isolated Bionic test explicitly bound to Wi-Fi network 102 completed a public
TCP connection, while binding to VPN 103 timed out. This is diagnostic evidence,
not the chosen remedy.

## Independent Android controls with VPN retained

A Bionic executable without PRoot failed both Android's network-aware resolver
and an explicitly VPN-bound connection to `151.101.2.132:443`.

A disposable Android instrumentation APK with INTERNET and ACCESS_NETWORK_STATE
permissions then tested the same public Debian endpoint under a regular app UID,
10034, also included in the VPN UID range. It explicitly used the active Android
Network rather than selecting an underlying Wi-Fi network:

```
uid=10034 network=103 vpn=true
dns: UnknownHostException for deb.debian.org
vpn_tcp: SocketTimeoutException connecting to 151.101.2.132:443 from 10.0.0.1
https://deb.debian.org/debian/README: UnknownHostException
```

The diagnostic package `dev.dextop.vpnprobe` was uninstalled after this test.
The evidence excludes a failure specific to wget, PRoot, the new Landlock code,
the guest preload or just ADB shell UID permissions. These probes alone do not
establish the internal VPN failure; the subsequent Mullvad UI inspection gives
the concrete device-invalid/blocking state. Android reporting the VPN as
CONNECTED is not evidence that a public download can complete.

Local listening HTTP services tested on 8585/61209 reject CONNECT with 501;
37821 timed out. No usable VPN proxy was established, and no proxy setting was
persisted. The user's VPN-preservation requirement remains in force.

## Experimental feature UI

App information → Experimental features now contains the Landlock toggle.
The app-owned preference defaults off, survives reconnection, and is published
to the Linux service without starting a service solely to change the switch.
The known unstable Galaxy kernel still falls back to ordinary PRoot, with a
visible explanation. Saving ON is not represented as successful acceleration on
this kernel.

Validation: four widget tests pass (default/offline inspection, ON/OFF
persistence and synchronization, saved choice on rejected kernel, unavailable
native channel); targeted Flutter analysis reports no issues; GitHub debug
Kotlin compilation passes. The GitHub ARM64 release APK build passes, and its
signing certificate matches the installed Dextop release certificate. The APK
was installed with `adb install -r`; installation succeeded without clearing
data. On the Galaxy the switch initially displayed OFF, saved ON, remained ON
after closing/reopening the page, and was restored to its original OFF value.
The rejected-kernel explanation was visible. Mullvad remained the active VPN.
Logs and isolated comparison sources are in
`temp/wget-investigation/`.

## Resolved after user reconnected Mullvad (21:12–21:13 JST)

After the user reported reconnection, the Galaxy resolver automatically changed
from the blocked-state `192.0.2.1` to VPN DNS `10.64.0.1` and
`fc00:bbbb:bbbb:bb01::1`. Mullvad remained the active VPN, including shell UID
2000. No VPN bypass, DNS override or proxy was used for these tests.

| PRoot configuration | HTTPS wget result |
| --- | --- |
| Previous HEAD 9485910 | exit 0, HTTP 200, 1,198 bytes |
| Current installed release build | exit 0, HTTP 200, 1,198 bytes |
| Current build with PROOT_FASTPATH requested | exact-kernel fallback warning, then exit 0, HTTP 200, 1,198 bytes |

All three fetched `https://deb.debian.org/debian/README` with the normal runtime
preloads. SHA-256 was identical:
`a0c66a1fe046f008b6db9584d05347dd8ded2b78250851b61d3861c213ac361e`.
DNS succeeded in all three. One current-mode dig query first timed out on IPv4
and then obtained a valid answer from the VPN IPv6 resolver; wget itself
completed in 0.15 seconds. The guarded-request wget completed in 0.19 seconds.
This validates restored VPN downloads, not actual Landlock acceleration on the
known-rejected Galaxy kernel. Logs: `reconnected-{old,current,requested}.log`
in `temp/wget-investigation/`. The disposable device fixture was removed.
