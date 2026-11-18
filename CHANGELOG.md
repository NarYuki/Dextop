# Changelog

## [2.0.0-beta.3](https://github.com/NarYuki/Dextop/compare/v2.0.0-beta.2...v2.0.0-beta.3) (2026-11-18)


### New features

* **linux:** make the unprotected fast mode the default ([3368363](https://github.com/NarYuki/Dextop/commit/33683639e4f36ae0b66cc335df0da9abb8783c04))
* **linux:** make the unprotected fast mode the default ([5abda62](https://github.com/NarYuki/Dextop/commit/5abda6215974524a0c72be3bc91c5ca9049bffe5))


### Fixes

* **linux:** let Steam verify its webhelper connections in fast mode ([689c1e4](https://github.com/NarYuki/Dextop/commit/689c1e4ed387e7dbe57954aa048743fa1d5a03a2))
* **linux:** let Steam verify its webhelper connections in fast mode ([9116129](https://github.com/NarYuki/Dextop/commit/9116129ea68410e8c374d1f35bbf0f9790068f57))

## [2.0.0-beta.2](https://github.com/NarYuki/Dextop/compare/v2.0.0-beta.1...v2.0.0-beta.2) (2026-11-18)


### New features

* **ui:** move the app UI to Material 3 Expressive across settings, home, setup, display, keyboard, Samsung, Plasma, overlay and Linux screens ([40f9301](https://github.com/NarYuki/Dextop/commit/40f9301cd57f6d1a97f2c7c4f0763aac01f94e53))
* **ui:** slide between setup pages in the direction of travel and use M3E disabled colors for dropdowns and navigation items ([cefa62b](https://github.com/NarYuki/Dextop/commit/cefa62bb96d96129ff02321e92aa0d0d2b9e0dee))
* **linux:** add Linux containers in a new Linux tab: Debian, Ubuntu or Arch Linux ARM, with each install step shown ([d043bf1](https://github.com/NarYuki/Dextop/commit/d043bf1717d073856139e9bf9f74981f3e903d67))
* **linux:** show the Linux desktop (Xvfb + IceWM) in a Plasma window with mouse, keyboard, IME text and cursor tracking ([d043bf1](https://github.com/NarYuki/Dextop/commit/d043bf1717d073856139e9bf9f74981f3e903d67))
* **linux:** open each Linux app in its own window, with Dextop acting as the X window manager and menus shown as popups ([d043bf1](https://github.com/NarYuki/Dextop/commit/d043bf1717d073856139e9bf9f74981f3e903d67))
* **linux:** add a Wayland compositor for Linux apps with xdg-shell windows and popups, dma-buf/shm buffers, XKB keymaps, text-input-v3 and decorations ([4dc3ddf](https://github.com/NarYuki/Dextop/commit/4dc3ddf7af438561f3083d862d26fab1ffd96941))
* **linux:** add VirGL GPU acceleration and a setting to choose hardware (VirGL) or software (llvmpipe) rendering ([5e0cadc](https://github.com/NarYuki/Dextop/commit/5e0cadc1f4aab9ce7fe0ed2ebf5cea2f9dcee509))
* **linux:** present GPU windows through Mesa Turnip, with Turnip + Zink rendering in the container ([4dc3ddf](https://github.com/NarYuki/Dextop/commit/4dc3ddf7af438561f3083d862d26fab1ffd96941))
* **linux:** install DroidDeck's DD-Turnip for Adreno 6xx-8xx and Mesa 26 from Debian backports ([b263a2e](https://github.com/NarYuki/Dextop/commit/b263a2e3e400e3b2571e793329477ed754673d77))
* **linux:** test the GPU on each device and recommend it only when it is stable and clearly faster; three GPU crashes within ten minutes switch the session to software ([f2ccd8e](https://github.com/NarYuki/Dextop/commit/f2ccd8ecd44486f644c294fa12deac4d3057fedf))
* **linux:** add GPU windows (gamescope) so Turnip frames reach the compositor as dma-bufs ([94702eb](https://github.com/NarYuki/Dextop/commit/94702eb784e582dd570fd1e75e4d63068f6fe7a1))
* **linux:** install AppImages without FUSE, register them with their own name, icon and categories, and remove them on uninstall ([94702eb](https://github.com/NarYuki/Dextop/commit/94702eb784e582dd570fd1e75e4d63068f6fe7a1))
* **linux:** save stopped containers as snapshots, restore them as new containers, and export or import them through Android's file picker ([94702eb](https://github.com/NarYuki/Dextop/commit/94702eb784e582dd570fd1e75e4d63068f6fe7a1))
* **linux:** add a Linux Store with APT, deb-get and Flathub sources, cached catalogs, installation progress and Android notifications ([315d33b](https://github.com/NarYuki/Dextop/commit/315d33bc4ae97f029a85786ed212ea3b48db4bd1))
* **linux:** add a Linux terminal ([315d33b](https://github.com/NarYuki/Dextop/commit/315d33bc4ae97f029a85786ed212ea3b48db4bd1))
* **linux:** add Steam installation and DroidDeck gaming compatibility ([f50d6b6](https://github.com/NarYuki/Dextop/commit/f50d6b621b4cff6b7dbec96358a571770658d964))
* **linux:** give Linux access to the Android camera, folders, USB devices, gamepads and microphone ([5402d09](https://github.com/NarYuki/Dextop/commit/5402d091975cdae4f39943b59a3a0131f5a0a543))
* **linux:** add calendar, printing, systemd and toolkit theme integrations ([0e7755a](https://github.com/NarYuki/Dextop/commit/0e7755a4afe3fc47c50530518be80859e24f740c))
* **linux:** list the other execution mode's containers, grayed out with a prompt to switch ([0e7755a](https://github.com/NarYuki/Dextop/commit/0e7755a4afe3fc47c50530518be80859e24f740c))
* **linux:** add encrypted keyring setup and FIDO security key (HID) bridging ([4d25a58](https://github.com/NarYuki/Dextop/commit/4d25a58483d10659054953fa9f1f486d6813d142))
* **linux:** pin Linux apps to the taskbar, add desktop shortcuts, and integrate an experimental ~/Desktop folder ([04e1b0f](https://github.com/NarYuki/Dextop/commit/04e1b0f93f98919c0cefd984bade1c6ee338758f))
* **linux:** show container storage use by part and remove caches on request ([0fcc78a](https://github.com/NarYuki/Dextop/commit/0fcc78a736a21f6fb575e630d1b7dc67c74d782c))
* **linux:** install new Debian containers from signed prebuilt images, resuming interrupted downloads ([17deff4](https://github.com/NarYuki/Dextop/commit/17deff48f8497d930c75457af8ab7361543134ba))
* **linux:** use Linux without completing Dextop's own setup; Linux runs with ordinary app permissions by default and the Google Play build offers only this mode ([17deff4](https://github.com/NarYuki/Dextop/commit/17deff48f8497d930c75457af8ab7361543134ba))
* **linux:** ask for Store sources during first setup and hide the execution mode where only one exists ([3767eb2](https://github.com/NarYuki/Dextop/commit/3767eb279b202e8f9833dc17ac4faa8326d4a83d))
* **macos:** add experimental macOS compatibility: an isolated ARM64 runtime, a supervised bundle launcher, bundles in the Linux app list, and runtime status and settings ([3db9500](https://github.com/NarYuki/Dextop/commit/3db9500fe6cfba213a355c2c4943f09efcb4cec2), [caa4943](https://github.com/NarYuki/Dextop/commit/caa49434d0e1554c757fe1becf27864b7e3e9028), [98f984d](https://github.com/NarYuki/Dextop/commit/98f984decce3c4b74a486718bdf730bf593b8157), [adaabfa](https://github.com/NarYuki/Dextop/commit/adaabfaffbe34875cfc786b953cf93228f305255))
* **macos:** install the APK-approved macOS runtime from Linux settings ([9d87a68](https://github.com/NarYuki/Dextop/commit/9d87a685d86bf9df79b720988d1d7191c6cf5541))
* **book:** rebuild Book as its own shell with a live status bar, dock, centered launcher, grid desktop with Android widgets and shortcuts, control center, notifications and media panels ([04e1b0f](https://github.com/NarYuki/Dextop/commit/04e1b0f93f98919c0cefd984bade1c6ee338758f))
* **book:** make Book the default desktop ([4d25a58](https://github.com/NarYuki/Dextop/commit/4d25a58483d10659054953fa9f1f486d6813d142))
* **display:** support duplicate, extend and single-screen external display outputs ([5402d09](https://github.com/NarYuki/Dextop/commit/5402d091975cdae4f39943b59a3a0131f5a0a543))
* **display:** set external display density from physical DPI with Book's display scale ([04e1b0f](https://github.com/NarYuki/Dextop/commit/04e1b0f93f98919c0cefd984bade1c6ee338758f))
* **plasma:** add the Plasma v2 compositor mode, giving each app its own virtual display inside a Breeze window ([e639223](https://github.com/NarYuki/Dextop/commit/e63922373991f705c20e5f0ea7b25fa935c48dda))
* **plasma:** add Plasma v2 tray applets for network, audio and power, and make Plasma v2 a regular option ([e971c47](https://github.com/NarYuki/Dextop/commit/e971c4789dfb56bdd7f1f0f637c94c6760ac7a65))
* **launcher:** add a "Start Dextop" shortcut and a "Dextop Display" Quick Settings tile to toggle the virtual display ([167907e](https://github.com/NarYuki/Dextop/commit/167907e65d98b41d11e45e663e3826df0b6afe62))
* **auto:** add split placement for phone and Android Auto sessions ([e639223](https://github.com/NarYuki/Dextop/commit/e63922373991f705c20e5f0ea7b25fa935c48dda))
* **windows:** add a back button for Android apps, animated corners and frames, and split-aware corner rounding ([04e1b0f](https://github.com/NarYuki/Dextop/commit/04e1b0f93f98919c0cefd984bade1c6ee338758f))
* **l10n:** add inline Japanese and English text with generated translation tables, and expand localization ([5402d09](https://github.com/NarYuki/Dextop/commit/5402d091975cdae4f39943b59a3a0131f5a0a543))


### Fixes

* **setup:** turn accessibility on for the gesture and laptop demos on GitHub builds, and off again when the demo ends ([9593443](https://github.com/NarYuki/Dextop/commit/959344378892f6685c0fae83ff19d905b366acc9))
* **setup:** smooth the setup page transition with a shared-axis slide and fade, and load the tutorial images ahead of time ([dd3086d](https://github.com/NarYuki/Dextop/commit/dd3086d702521990cc309848597a6141199e8a5a))
* **ui:** show app icons in saved workspaces again on the home screen and in settings ([2babeab](https://github.com/NarYuki/Dextop/commit/2babeabfbc13c45c14e0da40e325385f28a501fd))
* **linux:** download FEX reliably while Launchpad's PPA host is busy: retry server errors and fall back to the librarian host ([4acc7b0](https://github.com/NarYuki/Dextop/commit/4acc7b01caedf24a28658f3d7bbdbca50f1a6949))
* **linux:** keep extended attributes working in the unprotected fast mode ([c9a5fca](https://github.com/NarYuki/Dextop/commit/c9a5fca8364bd39e2d8949e19eef72a0472b06a8))
* **update:** beta builds follow pre-releases automatically, and the pre-release switch on stable builds responds when tapped ([8aa3c14](https://github.com/NarYuki/Dextop/commit/8aa3c14d1175077d93e157dc1d66669d0c3a5e9f), [2b41e8c](https://github.com/NarYuki/Dextop/commit/2b41e8c8c63403e5bbd4c3546359ad4d26aeb45a))
* **linux:** run sessions with the shell user's real ids and a patched PRoot; glxgears runs about twice as fast and D-Bus authentication matches ([110dea3](https://github.com/NarYuki/Dextop/commit/110dea39d20a460d7123a1cda1ef71c7c60d627e))
* **linux:** wait for the session bus before starting Firefox and stop sessions left by an earlier service, so Firefox no longer reports "already running" ([110dea3](https://github.com/NarYuki/Dextop/commit/110dea39d20a460d7123a1cda1ef71c7c60d627e))
* **linux:** fix Linux on Samsung Galaxy (kernel 6.12): copy refused hard links, return ENOSYS for fchmodat2, bind /dev/shm, and forward keys that reach the overlay ([b263a2e](https://github.com/NarYuki/Dextop/commit/b263a2e3e400e3b2571e793329477ed754673d77))
* **linux:** fall back to abstract sockets where path sockets are refused, fixing Chrome's ProcessSingleton, dconf and the VirGL socket ([5e0cadc](https://github.com/NarYuki/Dextop/commit/5e0cadc1f4aab9ce7fe0ed2ebf5cea2f9dcee509))
* **linux:** enable MIT-SHM for Linux programs so the X server stops refusing shared memory ([f2ccd8e](https://github.com/NarYuki/Dextop/commit/f2ccd8ecd44486f644c294fa12deac4d3057fedf))
* **linux:** rebuild system information Android denies (network counters, battery, device model, swaps, hostname), so btop, fastfetch and psutil work ([8ad4aeb](https://github.com/NarYuki/Dextop/commit/8ad4aeba92818792beddd3f1fc278428c42cebdc))
* **linux:** fix Steam setup for container accounts and write setup logs to logcat ([df3d646](https://github.com/NarYuki/Dextop/commit/df3d6468b9054d33d81f4bf5d86abe591ae103de))
* **linux:** recover Steam setup when a pinned FEX or runtime download is removed by its publisher ([3767eb2](https://github.com/NarYuki/Dextop/commit/3767eb279b202e8f9833dc17ac4faa8326d4a83d))
* **linux:** confirm a free X display before using it, so apps start while another Dextop installation is running ([3767eb2](https://github.com/NarYuki/Dextop/commit/3767eb279b202e8f9833dc17ac4faa8326d4a83d))
* **linux:** stop keeping every downloaded package, and show the last lines of the setup log ([0fcc78a](https://github.com/NarYuki/Dextop/commit/0fcc78a736a21f6fb575e630d1b7dc67c74d782c))
* **linux:** show container errors as one line, with the full text in a selectable sheet ([80060fa](https://github.com/NarYuki/Dextop/commit/80060faece379d6bc35fa4796fc55ca3ff449fa7))
* **linux:** correct execution-mode and Turnip behaviour on unsupported GPUs ([17deff4](https://github.com/NarYuki/Dextop/commit/17deff48f8497d930c75457af8ab7361543134ba))
* **linux:** harden launcher authentication, Windows installer verification, fakeroot listeners and PulseAudio authentication ([315d33b](https://github.com/NarYuki/Dextop/commit/315d33bc4ae97f029a85786ed212ea3b48db4bd1))
* **linux:** improve graphics, input capture, window sizing and audio buffering ([315d33b](https://github.com/NarYuki/Dextop/commit/315d33bc4ae97f029a85786ed212ea3b48db4bd1))
* **linux:** restore Linux windows across activity recreation ([f50d6b6](https://github.com/NarYuki/Dextop/commit/f50d6b621b4cff6b7dbec96358a571770658d964))
* **linux:** fit floating Linux windows' Android tasks to their applications, sized from the parent's real frame ([0e7ae68](https://github.com/NarYuki/Dextop/commit/0e7ae68dd6eaa165135979ccbc200441f0cf486f), [4550cfc](https://github.com/NarYuki/Dextop/commit/4550cfc48a5b995a0bbc8d5e5d44d0824882e393), [a9f867c](https://github.com/NarYuki/Dextop/commit/a9f867c34a21bc7efe2171a5927140b6be1c1a61))
* **linux:** open the on-screen keyboard in Linux windows only from the pencil button ([80cb9a2](https://github.com/NarYuki/Dextop/commit/80cb9a2682bcd84e83dab2d4616545ab2e3ae8f3))
* **linux:** cache desktop entries, open folders in the file manager more reliably, and let orphaned taskbar pins be removed ([4d25a58](https://github.com/NarYuki/Dextop/commit/4d25a58483d10659054953fa9f1f486d6813d142))
* **book:** correct pointer capture, window reuse and startup phases, and dismiss the cover on the first frame ([4d25a58](https://github.com/NarYuki/Dextop/commit/4d25a58483d10659054953fa9f1f486d6813d142))
* **display:** show the cursor on mirrored displays and keep physical mice on the desktop display ([5402d09](https://github.com/NarYuki/Dextop/commit/5402d091975cdae4f39943b59a3a0131f5a0a543))
* **plasma:** keep the Xperia keyboard open and run Plasma v2 apps at 120 Hz ([d9ccd23](https://github.com/NarYuki/Dextop/commit/d9ccd23daab659f21ee6a6a14ba39c9a1e5ffc4e))
* **plasma:** fix Sony Xperia mirroring and Plasma v2 app windows, and support Android 15 on Xperia ([2392457](https://github.com/NarYuki/Dextop/commit/2392457d936b14b0bb20b882c083dabc1221f54c), [e971c47](https://github.com/NarYuki/Dextop/commit/e971c4789dfb56bdd7f1f0f637c94c6760ac7a65))
* **auto:** improve Car Companion pan and drag handling and Auto window overlap ([e639223](https://github.com/NarYuki/Dextop/commit/e63922373991f705c20e5f0ea7b25fa935c48dda), [080adff](https://github.com/NarYuki/Dextop/commit/080adff3caf1203bd7b29cfb85b6d41f59f16fe3))
* **auto:** add the Android 17 certificates so Car Companion's Play signatures are recognized ([17deff4](https://github.com/NarYuki/Dextop/commit/17deff48f8497d930c75457af8ab7361543134ba))
* **auto:** move the AAEnabler guidance into Android Auto setup ([8235e62](https://github.com/NarYuki/Dextop/commit/8235e62a6003e30c4deae54fcf1b8dca6c62b849))
* **desktop:** fix app launch on the virtual display and taps in touchpad mode ([75ce1e4](https://github.com/NarYuki/Dextop/commit/75ce1e445f8f6fa18a3f923c076f18cfa892373a))
* **desktop:** other apps can no longer start or stop the Dextop desktop ([17deff4](https://github.com/NarYuki/Dextop/commit/17deff48f8497d930c75457af8ab7361543134ba))
* **update:** stop the update notice repeating when using only Linux ([17deff4](https://github.com/NarYuki/Dextop/commit/17deff48f8497d930c75457af8ab7361543134ba))
* **macos:** wait for the macOS session to shut down before stopping Linux ([8e20e70](https://github.com/NarYuki/Dextop/commit/8e20e702dd4621fa623a641f0c4df97df15a3750))
* **build:** rebuild native libraries for 16 KB pages and patch PulseAudio for Android ([ef7e19a](https://github.com/NarYuki/Dextop/commit/ef7e19a16a892d67066f19be7fb0ae7a7c1bc98c))
* **android:** require Android 13 or later ([f50d6b6](https://github.com/NarYuki/Dextop/commit/f50d6b621b4cff6b7dbec96358a571770658d964))

## [2.0.0-beta.1](https://github.com/NarYuki/Dextop/compare/v1.7.0...v2.0.0-beta.1) (2026-11-17)


### New features

* **linux:** add an experimental KDE Plasma desktop session ([d5d3105](https://github.com/NarYuki/Dextop/commit/d5d31055bd09a78ef6a63a6bb741f7b09c14c3fa))
* **linux:** add Landlock fast mode with protected and unprotected modes ([abff3ea](https://github.com/NarYuki/Dextop/commit/abff3ea98cd5414abac88c856e14d08e0b72a2f2))
* **linux:** explain what sharing the whole storage gives Linux before turning it on ([65fb7f9](https://github.com/NarYuki/Dextop/commit/65fb7f95fece65c6f0a69baf704682f34dffa954))
* **linux:** keep container snapshots in the app's own storage ([95c1239](https://github.com/NarYuki/Dextop/commit/95c12395b2b0ae3629f59c4de55a508b374d6314))
* **linux:** optionally allow hard links in ordinary sessions ([c49c80d](https://github.com/NarYuki/Dextop/commit/c49c80d57c714ae6db997cc48e6110fa706096b3))
* **linux:** run integrations as systemd-compatible units and start them at boot ([3d2435d](https://github.com/NarYuki/Dextop/commit/3d2435dc995699efe43141d4972893fd8d86a768))
* **linux:** stand in for Android-denied /proc, netlink and syslog data ([e288890](https://github.com/NarYuki/Dextop/commit/e2888905eeeb68df371c04f3b8efbd79e54a6882))
* **settings:** reorganize Linux settings around devices and shared services ([10148d4](https://github.com/NarYuki/Dextop/commit/10148d40ec06573c4a82c3447f41b31cfd475283))
* **update:** offer pre-releases on request and order versions by semver ([0235cec](https://github.com/NarYuki/Dextop/commit/0235cec548f00114433d88dbf0d1300cacf539a0))


### Fixes

* **input:** give the laptop keyboard one owner and drop the slow fallback path ([86772ba](https://github.com/NarYuki/Dextop/commit/86772ba6037c18e6f63fe9fb897dbadb78e5701a))
* **linux:** ask for notifications for printing and accept full-width port digits ([8dc0c02](https://github.com/NarYuki/Dextop/commit/8dc0c0274c224b8551032bd7fd48d51b4f19cbdf))
* **linux:** fix integrations, SSH, USB storage and the desktop; rework the terminal ([94b5666](https://github.com/NarYuki/Dextop/commit/94b56661001deec56f4d82c8c3647b24e14ce1a4))
* **linux:** read Bluetooth file requests without trusting guest-controlled files ([9728d78](https://github.com/NarYuki/Dextop/commit/9728d78ce65f08e14f5873c96009ba81ae2648a3))

## 2.0.0

### 日本語

#### 新機能

- Debian 13 の Linux アプリを Android のウィンドウや Dextop のデスクトップで使えるようになりました。
- Linux の Store を追加しました。APT に加えて deb-get と Flathub を利用できます。
- Steam のインストールと、GPU ウィンドウ、AppImage、コンテナのスナップショットに対応しました。
- Dextop のセットアップを完了しなくても Linux だけを使えるようになりました。

#### 改善

- Linux 環境を署名済みの事前ビルドイメージから導入するようにしました。ダウンロードは中断しても続きから再開します。
- Linux は既定でアプリ権限のみで動作します。Google Play 版はこの方式のみです。
- 多言語対応を拡充しました。

#### 修正

- Android 17 以降で Car Companion の Play 署名を認識できない可能性がある問題を修正しました。
- 他のアプリから Dextop のデスクトップを開始・停止できないようにしました。
- Linux 専用の利用時に更新通知が繰り返し表示される問題を修正しました。

#### Car Companion 2.1.0

##### 改善

- 多言語対応を拡充しました。

### English

#### New

- Run Debian 13 applications as Android windows or on the Dextop desktop.
- Added a Linux Store with APT, plus optional deb-get and Flathub sources.
- Added Steam installation, GPU windows, AppImage support, and container snapshots.
- Linux can now be used without completing Dextop's own setup.

#### Improvements

- Linux environments now install from signed, prebuilt images, and interrupted downloads resume.
- Linux runs with ordinary app permissions by default. The Google Play build offers only this mode.
- Expanded localization.

#### Fixes

- Fixed Car Companion's Play signatures possibly not being recognized on Android 17 and later.
- Other apps can no longer start or stop the Dextop desktop.
- Fixed the update notice repeating when using only Linux.

#### Car Companion 2.1.0

##### Improvements

- Expanded localization.

## 1.7.0

### 日本語

#### 新機能

- Android Autoで走行中の使用にも対応しました。

#### 修正

- Play版でGitHub版の更新情報が表示される問題を修正しました。

#### Car Companion 2.0.0

##### 改善

- Android AutoのSurfaceサイズ変更時に、セッションを作り直さず同じ仮想ディスプレイをリサイズするようにしました。

##### 修正

- Car Companionの切断や異常終了後にセッションが残り、再接続できない問題を修正しました。

### English

#### New

- Added support for using Dextop with Android Auto while driving.

#### Fixes

- Fixed Play builds showing GitHub release update information.

#### Car Companion 2.0.0

##### Improvements

- Android Auto surface-size changes now resize the same virtual display without recreating the session.

##### Fixes

- Fixed Car Companion sessions remaining after a disconnect or abnormal termination and preventing reconnection.

## 1.6.9

### 日本語

#### 修正

- Android 16で履歴画面が小さな中央ウィンドウとして表示される問題を修正しました。
- 履歴画面が対象のデスクトップディスプレイに全画面で表示されるようにしました。

### English

#### Fixes

- Fixed an issue on Android 16 where Recent apps appeared in a small centered window.
- Recent apps now opens fullscreen on the target desktop display.

## 1.6.8

### 日本語

#### 新機能

- 表示倍率をウィンドウ、文字、操作部品にも適用できる設定を追加しました。自動解像度とカスタム解像度の両方に対応しています。
- カバーディスプレイセッションをFoldable端末向けの正式機能に変更し、デフォルトで有効にしました。

#### 修正

- 折りたたみ端末で表示倍率を設定してデスクトップを起動すると、表示が崩れる問題を修正しました。
- 最大付近の表示倍率でOverlay Displayを起動できない問題を修正しました。
- 画面の縦横切り替え後にメニューOverlayのボタンがリサイズされず、余白が生じる問題を修正しました。
- 折りたたみ端末で高い表示倍率を使用すると、Flexモードが繰り返し切り替わり画面が点滅する問題を修正しました。
- 通常モードでウィンドウへの表示倍率が解除され、元のサイズへ戻る問題を修正しました。

### English

#### New

- Added an option to apply display magnification to windows, text, and controls. Both automatic and custom resolutions are supported.
- Promoted cover-display sessions to a standard Foldable feature and enabled it by default.

#### Fixes

- Fixed an issue where the desktop display layout broke when launched with display magnification on foldable devices.
- Fixed Overlay Display failing to start near the maximum display magnification.
- Fixed menu overlay buttons retaining their previous size and leaving empty space after an orientation change.
- Fixed the screen flickering because Flex mode repeatedly toggled when using high display magnification on foldable devices.
- Fixed window magnification being reset to the original size in normal mode.

## 1.6.7

### 日本語

#### 新機能

- 折りたたみ端末のカバーディスプレイを、独立したuinputトラックパッドとして使用できるようにしました。

#### 改善

- 手動で開いたBlackBerryモードを永続的な手動選択として扱うようにしました。

#### 修正

- 折りたたみ端末でBlackBerryモードが表示直後に消える問題を修正しました。
- 自動姿勢判定が手動で選択したBlackBerryモードを上書きする問題を修正しました。
- カバーディスプレイの入力モード切り替え後に、背面ボタンやトラックパッドの状態が残る問題を修正しました。

### English

#### New

- Added support for using a foldable device's cover display as an independent uinput trackpad.

#### Improved

- Manually opened BlackBerry mode is now treated as a persistent manual selection.

#### Fixes

- Fixed an issue where BlackBerry mode disappeared immediately after being shown on foldable devices.
- Fixed automatic posture detection overriding a manually selected BlackBerry mode.
- Fixed rear-button or trackpad state remaining active after switching the cover-display input mode.

## 1.5.2 — Fold8 laptop-mode reliability

### Fixed

- Fixed Fold8 laptop mode failing to start when One UI temporarily reports an incorrect fold posture or hinge orientation.
- Added the hardware hinge angle as a safe Fold8 posture fallback while preserving the WindowManager posture path.
- Kept the embedded privileged Binder alive and able to republish after the app process is restarted.
- Added an in-app update confirmation shown once after a successful version upgrade.

### Changed

- Automatic laptop-mode detection is now disabled by default on new installations. Existing user choices are preserved.

## 1.4.2 — Pixel compatibility fallback and keyboard repeat input

### Improved

- Added long-press and continuous repeat input to the laptop-mode keyboard while preserving multi-key input and modifier combinations.
- Restored the localized Android Auto settings layout and aligned its sections with the rest of Dextop settings.

### Fixed

- Fixed affected Pixel firmware failing to start with `No compatible mirror backend` when `VirtualDisplayConfig.Builder#setDisplayIdToMirror` is unavailable.
- Added an automatic per-firmware Pixel/AOSP compatibility fallback that restores the legacy freeform flags and mirror backend order. The native Pixel path is probed again after each firmware update.
- Fixed held laptop keyboard keys remaining pressed when laptop mode closes or the keyboard layout is rebuilt.

## 1.4.1 — Google Cast support and display controls

### New

- Added Google Cast output with Compatibility and Low latency modes.
- Added an in-overlay Cast receiver picker with active scanning, connection status, receiver selection, and disconnection controls.
- Added a persistent 180-degree rotation control that keeps the desktop, overlay controls, touch coordinates, and gesture directions aligned in both portrait and landscape.
- Added an animated Android Auto setup guide to Dextop Car Companion.

### Improved

- Kept Android Auto displays awake while Dextop Car Companion is running.
- Removed the unfinished physical mouse and keyboard routing controls from the Dextop overlay.

### Fixed

- Fixed Pixel/AOSP devices on Android 16 or later opening desktop apps without platform window decorations by using the system-managed desktop path. Older Android versions retain the compatibility freeform path.

## 1.4.0 — Dextop Car Companion and Android Auto support

### New

- Added Dextop Car Companion for launching a dedicated Dextop desktop on supported parked Android Auto displays.
- Added an independently owned car-display session with head-unit-sized virtual displays and direct touch forwarding without starting a phone-side Dextop session.
- Added a dedicated in-car overlay with close, reconnect, stop, and workspace controls. Saved workspaces use the same names, icons, save, and launch behavior as Dextop on the phone.
- Added a compatibility display mode that exposes the Auto virtual display on the phone and an experimental direct-transfer mode that keeps it hidden.
- Added an Auto settings category with automatic phone-mirror orientation matching and the experimental hidden-display switch.
- Added recovery integration for unexpectedly disconnected Dextop Car Companion sessions.
- Added standard, wide, tall, and Subaru-style parked DHU test profiles with touch and parked-state sensors enabled.
- Added a true-black AMOLED laptop keyboard theme with near-white key legends and restrained accent colors.

### Improved

- Added explicit Running (Auto), stopping, and cleanup states so the home screen reflects the native session owner and prevents another start until teardown is complete.
- Kept phone and car display ownership isolated across display creation, topology activation, recovery, and teardown.
- Extended display topology handling to recognize Auto-owned displays while excluding displays that have already been removed.
- Improved the three-finger menu layout on foldable cover displays by recalculating the available viewport and enabling scrolling after display changes.
- Improved live resolution changes so VirtualDisplay output follows the measured destination Surface while retaining the requested logical desktop size.
- Improved DPI-only updates by changing only display density instead of rebuilding size, rotation, and desktop policy.
- Improved navigation restoration with repeated vendor-SystemUI recovery passes before the accessibility service is detached.
- Added focused window-launch diagnostics for vendor desktop environments while filtering package names, components, accounts, URIs, and private filesystem paths from shared reports.
- Serialized and coalesced window diagnostics so launching a workspace cannot create an unbounded number of diagnostic workers.
- Updated the release APK workflow to produce an arm64-only package while preserving the normal internal version code.

### Fixed

- Fixed DPI fields in the main resolution editor and quick menu rejecting Samsung IME, hardware-keyboard, and composing-text input.
- Fixed DPI-only changes unnecessarily rebuilding Samsung DeX and causing its taskbar process to disappear.
- Fixed custom-resolution changes retaining the previous crop, offset, black bars, or small centered image when switching to a full-surface resolution.
- Fixed removed overlay displays remaining in saved topology snapshots and appearing as an ever-growing list of phantom monitors.
- Fixed stale overlay display requests surviving session shutdown by clearing and verifying the request twice before reuse.
- Fixed Dextop being unable to start again after a completed stop because a second accessibility teardown left the stopping latch active.
- Fixed navigation gestures and Samsung's bottom gesture state remaining disabled after pause, interruption, or session shutdown.
- Fixed the software cursor remaining hidden after an unsupported mirror backend failed and the user returned to a working backend.
- Fixed system decorations being applied outside the existing desktop-HOME launch fallback path.
- Fixed Auto-only sessions being mistaken for broken phone sessions and incorrectly showing the Android repair flow.
- Fixed ending Dextop Car Companion stopping an unrelated phone-side Dextop session.
- Fixed configuration changes in Dextop Car Companion being treated as an unexpected Android Auto disconnect.
- Fixed car-session teardown leaving its virtual display or overlay behind after a normal exit.
- Fixed unsupported portrait DHU video modes by using supported 1080p streams with cropped portrait content regions.

## 1.3.9 — Safer virtual pointer lifecycle

### Improved

- Removed the virtual mouse DPI and pointer-acceleration controls. Pointer movement now uses a fixed raw scale, and values saved by older releases are cleared automatically.
- Standardized the Mouse settings page with the same compact section heading used by the other settings categories.
- Kept Dextop's virtual mouse and touchpad out of physical input-device routing so stopping Dextop cannot disable or re-route a separately connected mouse or touchpad.

### Fixed

- Fixed stale virtual-pointer preferences causing input to stop working until the app was reinstalled.
- Fixed virtual pointer teardown being mixed with physical-device association handling.
- Reverted incompatible secure mirror flags that could prevent a Dextop session from starting. The existing secure-display option remains available.

## 1.3.8 — Stronger laptop keyboard and trackpad feedback

### Improved

- Strengthened haptic feedback for laptop-mode keyboard keys, modifiers, FN/Meta/Menu actions, and trackpad taps, clicks, and long presses.
- Added a heavy-click vibration effect with an amplitude-controlled fallback for devices that do not expose the predefined effect.
- Added the virtual touchpad input profile and compact pointer/scroll settings, with localized labels and a software-cursor fallback.
- Improved virtual pointer lifecycle so switching between cursor and tap input does not leave stale devices or duplicate pointer events.

## 1.3.7 — Fixed “The desktop HOME activity could not be launched” startup failure

### Fixed

- Fixed regional Samsung carrier model identifiers beginning with `SC`, `SCG`, or `SCV` being resolved to a generic vendor profile instead of Samsung DeX.
- Added prefix-based model matching so carrier suffixes do not prevent Samsung profile detection.
- Added a Samsung HOME-launch fallback that recreates the virtual display with system decorations when One UI rejects HOME on the first attempt.
- Persisted the successful decoration workaround per firmware fingerprint and automatically retries the original configuration after a firmware update.

## 1.3.6

### Improved

- Added complete support for all Galaxy Z Fold series devices running One UI 8.
- Expanded foldable model recognition for worldwide Fold8 and Fold8 Ultra variants, including Japanese carrier models.
- Improved foldable profile selection by matching normalized model prefixes when regional suffixes are present.
- Improved laptop-mode detection compatibility across regional Samsung firmware variants.
- Added localized keyboard theme settings and transparency controls for keyboard backgrounds, keys, and trackpads.
- Improved keyboard theme export to include theme settings and associated images in a ZIP archive.
- Added configurable trackpad label visibility, enabled by default.
- Improved keyboard theme previews and laptop-mode overlay behavior.

### Fixed

- Fixed incorrect Meta key rendering on devices where the Android icon font fallback produced unexpected characters.
- Fixed keyboard background transparency not applying to the area between keys.
- Fixed keyboard theme settings being accessible while Dextop is running.
- Fixed unlocalized foldable setup and keyboard theme strings.
- Fixed laptop-mode keyboard and display state handling across foldable posture and display transitions.

## 1.3.5

### Improved

- Added Samsung DeX taskbar auto-hide control to Samsung desktop settings using the launcher-owned `taskbar_show_hide_on_hold_enabled` key.
- Included the Samsung DeX taskbar setting in the Samsung desktop settings backup and restore flow.
- Added runtime resolution of display-topology transaction IDs instead of relying on hard-coded Binder numbers, improving compatibility with OEM Android framework forks.
- Kept an otherwise usable VirtualDisplay session running when the optional topology API is unavailable or rejected, while recording the skipped operation in the session log.

### Fixed

- Separated the generic Android desktop taskbar setting from Samsung DeX. The Display setting now always controls only `desktop_windowing_force_hide_taskbar`, regardless of the device manufacturer.
- Fixed topology routing on newer/vendor Android builds where stale transaction IDs could invoke a protected display-mode operation and fail with `android.permission.RESTRICT_DISPLAY_MODES`.
- Fixed topology activation failures aborting Dextop mirroring instead of degrading gracefully to mirroring without topology routing.

## 1.3.4

### Improved

- Added a complete laptop keyboard theme system with Standard, Crimson, Cloud Pop, and user-created themes.
- Added full-screen localized theme management with scrollable previews, real keyboard-and-trackpad overlay previews, add/edit/delete actions, and per-theme ZIP import and export.
- Added customizable keyboard backgrounds, key, border, text, and trackpad colors, image opacity, background blur, and corner styling.
- Stabilized laptop-mode transitions and theme-preview lifecycle so preview surfaces are separated from the setup overlay and are cleaned up when leaving the editor.
- Extended the privacy-filtered Dextop session report with runtime display geometry, including the target display ID, surface and window dimensions, logical target size, density, rotation, and configuration orientation.
- Added input diagnostics for touch routing, coordinate conversion, active input mode, pointer count, and the result of each sampled `injectInputEvent` call.
- Added orientation lifecycle diagnostics covering requested rotation, applied display rotation locks, surface recreation, mirror reattachment, and rebuild completion or failure.
- Added display lifecycle snapshots when displays are added, removed, changed, resized, or disconnected so foldable and DeX transitions can be compared from one report.

### Fixed

- Fixed diagnostic reports omitting the runtime evidence needed to distinguish display-geometry mismatches from rejected input injection on vendor Android builds.

## 1.3.3

### Improved

- Shift now updates the keyboard legends to their correct symbols (`!@#$%^&*()_+{}|:"<>?`).
- Ctrl, Shift, and Alt modifiers latch on press, allowing modifier chords and multi-touch shortcuts such as Ctrl+C and Ctrl+V.
- Added smoother laptop-mode and keyboard-settings transitions with fade, slide, and scale animations.
- The desktop surface is now black-backed during laptop-mode layout changes so the Android screen cannot show through transparent resize areas.
- Stabilized hinge-angle detection with filtering and debounce to prevent posture-triggered flicker and repeated virtual-display resizing.

### Fixed

- Fixed keyboard-settings navigation returning abruptly without the laptop transition animation.
- Fixed unstable laptop-mode toggling caused by noisy real-device hinge sensor readings near posture thresholds.

## 1.3.2

### Improved

- Laptop mode now registers its on-screen keyboard as an external physical keyboard while the mode is active.
- Gboard and other IMEs now follow Android's physical-keyboard preference instead of being forcibly disabled or hidden.
- The temporary keyboard device is removed when laptop mode, Dextop, or a paused session ends.

### Fixed

- Fixed laptop-mode key input being treated only as synthetic key events, which caused virtual keyboards to appear unexpectedly in text fields.

## 1.3.1

### New

- Added persistent display arrangements so monitor positions are restored when the same external-display configuration reconnects.
- Added laptop mode for foldable devices, with a US keyboard and trackpad in the lower screen area.
- Added laptop keyboard themes, function keys, shortcut labels, and direct access to the Dextop overlay from the trackpad.
- Added optional hinge-angle detection for automatically entering and leaving laptop mode.

### Improved

- Kept manual laptop mode available from the overlay on supported foldable devices even when automatic detection is disabled.
- Updated the README and wiki with multi-display, high-refresh-rate, taskbar, and foldable laptop-mode features.
- Marked foldable main/cover-display switching as incomplete while device-specific panel transitions continue to be improved.

### Fixed

- Fixed hinge-angle detection silently enabling the laptop mode preference.
- Fixed manual laptop mode toggles overwriting the automatic detection preference.

## 1.3.0

### Multi-display support has arrived

- Added an intuitive display topology editor for arranging Dextop and external displays to match their physical layout.
- Added multi-display pointer routing and automatic topology activation when Dextop starts or resumes.
- Added live display hot-plug and topology monitoring. The editor reloads when displays are reconfigured and closes automatically when arrangement is no longer available.
- Added localized display identification, reset, cancel, and apply controls with a responsive desktop and mobile layout.

### New

- Added an option to keep supported built-in displays at 120 Hz while Dextop is running with an external monitor.
- Added automatic 120 Hz reapplication after an external display is connected, reconfigured, or disconnected.
- Added stable display settings for topology participation and automatic desktop taskbar hiding.

### Improved

- Reorganized display settings into Display and Convenience sections using the existing settings design.
- Made display topology available by default and automatically refresh it in the background at every Dextop start and resume.
- Improved the topology canvas so it scales with the available dialog size on phones, foldables, and large desktop windows.
- Removed Dextop's custom cursor and cursor rendering logic for physical mice while preserving touch-panel cursor mode and monitor routing.
- Improved localization for display topology, Samsung desktop settings, warnings, and feature descriptions.

### Fixed

- Fixed stale display topology state after external displays are connected, removed, folded, unfolded, or reconfigured.
- Fixed the display arrangement action remaining available when fewer than two configurable displays exist.
- Fixed built-in display refresh-rate settings being restored when 120 Hz should remain active after external-display disconnection.

## 1.1.2

### Improved

- Improved the three-finger edge swipe sensitivity by triggering from the leading finger instead of the centroid and lowering the required swipe distance, especially in portrait when swiping down from the top edge.

## 1.1.1

### Improved

- Device reports now include a privacy-filtered log from only the most recent Dextop session.
- Reduced noisy Android and Flutter debug logging while retaining device implementation, capability, backend, routing, failure, and restoration events.

### Fixed

- Fixed the device-report email recipient, subject, or body being omitted by some email applications.
- Fixed the updated gesture guide not appearing after upgrading from a version earlier than 1.1.0.

## 1.1.0

### New

- Added a VirtualDisplay-based display mirroring backend.
- Added a display setting for selecting Automatic, VirtualDisplay, WindowManager, or SurfaceControl mirroring.
- Added GitHub Release update checks at app startup and from App information.
- Added update indicators to the Settings navigation icon and App information entry.
- Added device-aware physical mouse and keyboard routing controls for supported external-display configurations.
- Added external-display hot-plug detection and automatic input-route restoration when a display is disconnected.
- Added localized device compatibility reports with automatically collected device details and email submission.
- Added Firebase Analytics screen and desktop-start event collection when Firebase is configured.

### Improved

- VirtualDisplay is now the default mirroring method and the first method attempted in Automatic compatibility mode.
- Improved physical mouse and touchpad cursor switching. The Dextop cursor is hidden when physical mouse movement is detected and restored when the touchscreen is tapped.
- Improved multi-touch forwarding by preserving pointer IDs, action indices, timing, history, pressure, and gesture data for smoother scrolling and reliable pinch zoom.
- App version labels now use the version installed in the APK instead of a fixed value.
- Improved update-check status reporting by distinguishing unchecked, checking, up-to-date, update available, and retrieval failure states.
- Added detailed update-check events to the Flutter debug log.
- Removed the default Flutter ripple and highlight effects from the orientation and theme segmented controls.
- Expanded the customizable overlay control row from three to five columns when input-routing controls are available.
- Updated the gesture demonstration to introduce the mouse and keyboard routing controls.
- Improved the overlay gesture flow and made multi-touch the production default.

### Fixed

- Fixed transparent or invalid app entries being captured when saving the current app arrangement from the overlay.
- Fixed rejected input injection events being incorrectly treated as successful.
- Fixed stale touch streams that could leave one-finger input unresponsive after closing the overlay.
- Fixed portrait overlay gestures so the three-finger swipe opens the panel from the top edge.
