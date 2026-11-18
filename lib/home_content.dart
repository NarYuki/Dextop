part of 'main.dart';

extension _HomeContent on _HomeScreenState {
  /// Home while only Linux is set up: an invitation to finish Dextop's setup.
  Widget linuxOnlyOverview() {
    final theme = Theme.of(context);
    return Center(
      key: const ValueKey('overview-linux-only'),
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(32),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 520),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(28),
                child: const Image(
                  image: AssetImage('assets/dextop_icon.png'),
                  width: 112,
                  height: 112,
                ),
              ),
              const SizedBox(height: 28),
              Text(
                localText(context, 'uiSetUpDextop'),
                textAlign: TextAlign.center,
                style: theme.textTheme.headlineMedium,
              ),
              const SizedBox(height: 12),
              Text(
                localText(
                  context,
                  'uiFinishTheSetupToRunAStandaloneAndroidDesktop',
                ),
                textAlign: TextAlign.center,
                style: theme.textTheme.titleMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                  fontWeight: FontWeight.w500,
                ),
              ),
              const SizedBox(height: 32),
              M3EButton.filled(
                size: M3EButtonSize.md,
                onPressed: widget.onStartSetup,
                icon: const Icon(M3EIcons.arrow_forward_rounded),
                label: Text(AppLocalizations.of(context).setupBegin),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget overview() {
    if (widget.linuxOnly) return linuxOnlyOverview();
    final wide = wideLayout;
    final session = <Widget>[
      heroPanel(),
      const SizedBox(height: 16),
      if (!active &&
          !autoActive &&
          !stopping &&
          recovery['recoverable'] == true) ...[
        recoveryPanel(),
        const SizedBox(height: 16),
      ],
      workspacePanel(),
      const SizedBox(height: 16),
      if (!loading &&
          !(secureSettingsGranted && shizukuRunning && shizukuGranted)) ...[
        shizukuPanel(),
        SizedBox(height: wide ? 16 : 24),
      ],
    ];
    final display = <Widget>[
      DextopSection(currentLocalizations().display),
      profileGrid(),
      const SizedBox(height: 12),
      orientationControl(),
      if (error != null) ...[const SizedBox(height: 16), errorPanel()],
    ];
    final actions = [
      M3EIconButton(
        variant: M3EIconButtonVariant.standard,
        onPressed: refresh,
        icon: const Icon(M3EIcons.refresh_rounded),
      ),
      const SizedBox(width: 8),
    ];
    return CustomScrollView(
      key: const ValueKey('overview'),
      slivers: [
        if (wide)
          M3EAppBar.sliver(
            pinned: true,
            variant: M3EAppBarVariant.small,
            titleText: currentLocalizations().appName,
            actions: actions,
          )
        else
          M3EAppBar.sliver(
            pinned: true,
            variant: M3EAppBarVariant.largeFlexible,
            titleText: currentLocalizations().appName,
            actions: actions,
          ),
        SliverPadding(
          padding: wide
              ? const EdgeInsets.fromLTRB(24, 12, 24, 32)
              : const EdgeInsets.fromLTRB(16, 4, 16, 32),
          sliver: wide
              ? SliverToBoxAdapter(
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        flex: 5,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: session,
                        ),
                      ),
                      const SizedBox(width: 24),
                      Expanded(
                        flex: 4,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            DextopSection(
                              currentLocalizations().display,
                              padding: const EdgeInsets.fromLTRB(8, 2, 8, 10),
                            ),
                            profileGrid(),
                            const SizedBox(height: 12),
                            orientationControl(),
                            if (error != null) ...[
                              const SizedBox(height: 16),
                              errorPanel(),
                            ],
                          ],
                        ),
                      ),
                    ],
                  ),
                )
              : SliverList.list(children: [...session, ...display]),
        ),
      ],
    );
  }

  Widget heroPanel() {
    final colors = Theme.of(context).colorScheme;
    final ready = secureSettingsGranted && shizukuRunning && shizukuGranted;
    final hasExistingSession =
        !active && !autoActive && !stopping && recovery['recoverable'] == true;
    final needsAndroidRepair =
        !active && !autoActive && androidRepair['required'] == true;
    final showRepairResult = !active && !autoActive && androidRepairCompleted;
    // autoActive is the validated native session owner state. Do not gate
    // the label on the separate Activity-connection flag: during an Auto
    // handoff that flag can briefly change while the overlay remains alive.
    final autoOnly = autoActive && !active;
    final autoPlus = autoActive && active;

    final statusTitle = stopping
        ? currentLocalizations().uiDextopStopping
        : loading
        ? currentLocalizations().uiChecking
        : autoPlus
        ? currentLocalizations().uiRunningAutoPlus.replaceFirst(
            'Auto+',
            autoMode.isEmpty ? 'Auto+' : 'Auto+/$autoMode',
          )
        : autoOnly
        ? currentLocalizations().uiRunningAuto.replaceFirst(
            'Auto',
            autoMode.isEmpty ? 'Auto' : 'Auto/$autoMode',
          )
        : active
        ? currentLocalizations().running
        : needsAndroidRepair
        ? currentLocalizations().uiAbnormalSessionWarning
        : showRepairResult
        ? currentLocalizations().uiTerminationProcessingCompletedSuccessfully
        : hasExistingSession
        ? currentLocalizations().uiThereIsAnExistingSession
        : ready
        ? currentLocalizations().uiDextopIsReady
        : currentLocalizations().uiPreparationIsRequired;

    final cardColor = active || autoActive
        ? colors.primaryContainer.withValues(alpha: 0.45)
        : needsAndroidRepair
        ? colors.tertiaryContainer.withValues(alpha: 0.55)
        : !ready && !loading && !hasExistingSession && !showRepairResult
        ? colors.errorContainer.withValues(alpha: 0.4)
        : colors.surfaceContainerLow;

    final statusIcon = stopping || loading
        ? M3EIcons.hourglass_top_rounded
        : needsAndroidRepair
        ? M3EIcons.warning_amber_rounded
        : showRepairResult
        ? M3EIcons.check_circle_rounded
        : hasExistingSession
        ? M3EIcons.history_rounded
        : active || autoActive || ready
        ? M3EIcons.check_circle_rounded
        : M3EIcons.error_outline_rounded;

    final statusIconColor = stopping || loading
        ? colors.onSurfaceVariant
        : needsAndroidRepair
        ? colors.tertiary
        : showRepairResult
        ? colors.primary
        : hasExistingSession
        ? colors.onSurfaceVariant
        : active || autoActive || ready
        ? colors.primary
        : colors.error;

    return M3ECard(
      variant: M3ECardVariant.filled,
      color: cardColor,
      borderRadius: BorderRadius.circular(26),
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              SizedBox.square(
                dimension: 28,
                child: Center(
                  child: Icon(statusIcon, size: 28, color: statusIconColor),
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Text(
                  statusTitle,
                  softWrap: true,
                  overflow: TextOverflow.visible,
                  style: Theme.of(context).textTheme.titleLarge
                      ?.copyWith(height: 1.18),
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),
          if (autoOnly) ...[
            M3EButton.tonal(
              size: M3EButtonSize.md,
              onPressed: null,
              enabled: false,
              icon: const Icon(M3EIcons.phone_android_rounded, size: 20),
              label: Text(currentLocalizations().uiStartPhoneDextop),
            ),
            const SizedBox(height: 10),
          ],
          M3EButton.filled(
            size: M3EButtonSize.md,
            decoration: needsAndroidRepair
                ? dextopButtonColors(
                    background: Colors.amber.shade600,
                    foreground: Colors.black,
                  )
                : active || autoOnly
                ? dextopButtonColors(
                    background: colors.error,
                    foreground: colors.onError,
                  )
                : null,
            onPressed: stopping
                ? null
                : needsAndroidRepair
                ? () async {
                    mutate(() => loading = true);
                    await bridge.repairAndroid();
                    if (!mounted) return;
                    mutate(() {
                      loading = false;
                      androidRepairCompleted = true;
                      androidRepair = {'required': false};
                      recovery = {'recoverable': false};
                    });
                  }
                : showRepairResult
                ? bridge.restartApp
                : autoOnly
                ? bridge.stopAuto
                : loading || stopping || hasExistingSession
                ? null
                : !active && !ready
                ? connect
                : toggleDisplay,
            icon: stopping || loading
                ? const SizedBox.square(
                    dimension: 24,
                    child: M3ELoadingIndicator(size: 24),
                  )
                : Icon(
                    needsAndroidRepair
                        ? M3EIcons.build_rounded
                        : showRepairResult
                        ? M3EIcons.restart_alt_rounded
                        : autoOnly || active
                        ? M3EIcons.stop_rounded
                        : M3EIcons.play_arrow_rounded,
                    size: 20,
                  ),
            label: Text(
              stopping
                  ? currentLocalizations().uiDextopStopping
                  : needsAndroidRepair
                  ? currentLocalizations().uiRestorePrivileges
                  : showRepairResult
                  ? currentLocalizations().uiRestartTheApp
                  : autoOnly
                  ? currentLocalizations().uiStopAndroidAuto
                  : active
                  ? currentLocalizations().stop
                  : currentLocalizations().uiStart,
            ),
          ),
        ],
      ),
    );
  }

  Widget recoveryPanel() {
    final width = recovery['width'] as int? ?? profile.width;
    final height = recovery['height'] as int? ?? profile.height;
    final density = recovery['density'] as int? ?? profile.density;
    final colors = Theme.of(context).colorScheme;
    return M3ECard(
      variant: M3ECardVariant.elevated,
      borderRadius: BorderRadius.circular(26),
      padding: const EdgeInsets.fromLTRB(18, 16, 18, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Icon(M3EIcons.restore_rounded, color: colors.primary),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      recovery['phase'] == 'running' ||
                              recovery['phase'] == 'paused'
                          ? currentLocalizations().uiDextopCanBeRestarted
                          : currentLocalizations()
                                .uiYouCanRestoreYourPreviousSession,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '$width × $height / $density dpi',
                      style: Theme.of(context).textTheme.bodyMedium
                          ?.copyWith(color: colors.onSurfaceVariant),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              Expanded(
                child: M3EButton.tonal(
                  size: M3EButtonSize.sm,
                  onPressed: () async {
                    final recovered = DisplayProfile(
                      currentLocalizations().uiRecoverySession,
                      '$density dpi',
                      width,
                      height,
                      density,
                      M3EIcons.restore_rounded,
                      id: 'recovery',
                    );
                    final resumeProfile = profile.isDevice
                        ? profile
                        : recovered;
                    mutate(() => loading = true);
                    await bridge.start(
                      resumeProfile,
                      resolveHomePortrait(),
                      secure,
                      decorations: effectiveDecorations,
                      workspaceMagnificationPercent:
                          workspaceMagnificationPercent,
                      magnifyWindowsWithDisplay: magnifyWindowsWithDisplay,
                    );
                    await Future<void>.delayed(
                      const Duration(milliseconds: 350),
                    );
                    await refresh();
                  },
                  child: Text(currentLocalizations().uiRestart),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: M3EButton.text(
                  size: M3EButtonSize.sm,
                  onPressed: () async {
                    await bridge.clearRecovery();
                    await refresh();
                  },
                  child: Text(currentLocalizations().uiDestruction),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget workspacePanel() {
    void openApps() => Navigator.of(context)
        .push(
          dextopRoute<void>(
            DextopFeaturesPage(
              isRunning: active,
              category: 'apps',
              ensureDesktopRunning: ensureDesktopRunning,
            ),
          ),
        )
        .then((_) => loadHomeWorkspaces());
    final colors = Theme.of(context).colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        DextopGroup(
          children: [
            M3EListItem(
              leading: const Icon(M3EIcons.apps_rounded),
              headline: currentLocalizations().uiAppLauncher,
              supportingText: currentLocalizations().uiOpenAppOnDesktop,
              trailing: const Icon(M3EIcons.chevron_right_rounded),
              onTap: openApps,
            ),
            M3EListItem(
              leading: const Icon(M3EIcons.space_dashboard_rounded),
              headline: currentLocalizations().uiWorkSpace,
              supportingText:
                  currentLocalizations().uiOpenASavedAppConfiguration,
              trailing: AnimatedRotation(
                turns: workspaceExpanded ? .5 : 0,
                duration: const Duration(milliseconds: 220),
                child: const Icon(M3EIcons.expand_more_rounded),
              ),
              onTap: () => mutate(() => workspaceExpanded = !workspaceExpanded),
            ),
          ],
        ),
        AnimatedSize(
          duration: const Duration(milliseconds: 260),
          curve: Curves.easeInOutCubic,
          alignment: Alignment.topCenter,
          child: workspaceExpanded
              ? Padding(
                  padding: const EdgeInsets.only(top: 6),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      if (homeAppsLoading)
                        const Padding(
                          padding: EdgeInsets.fromLTRB(4, 2, 4, 8),
                          child: M3EProgressIndicator.linearWavy(),
                        ),
                      if (homeWorkspaces.isEmpty)
                        DextopGroup(
                          color: colors.surfaceContainer,
                          children: [
                            M3EListItem(
                              headline:
                                  currentLocalizations().uiNoSavedWorkspaces,
                            ),
                          ],
                        )
                      else
                        DextopGroup(
                          color: colors.surfaceContainer,
                          children: [
                            for (final workspace in homeWorkspaces)
                              DextopWorkspaceRow(
                                name: '${workspace['name']}',
                                icons: [
                                  for (final pkg
                                      in (workspace['apps'] as List)
                                          .cast<String>())
                                    workspaceAppIcon(pkg),
                                ],
                                onTap: () => launchHomeWorkspace(workspace),
                              ),
                          ],
                        ),
                    ],
                  ),
                )
              : const SizedBox.shrink(),
        ),
      ],
    );
  }

  /// A workspace's apps as their icons, so they can be told apart at a glance.
  Widget workspaceAppIcon(String packageName) {
    final label = '${homeApps[packageName]?['label'] ?? packageName}';
    final bytes = homeApps[packageName]?['icon'];
    if (bytes is Uint8List) {
      return M3ETooltip(
        message: label,
        child: ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: Image.memory(bytes, width: 30, height: 30),
        ),
      );
    }
    if (homeAppsLoading) {
      return const SizedBox.square(
        dimension: 30,
        child: Padding(
          padding: EdgeInsets.all(5),
          child: M3ELoadingIndicator(),
        ),
      );
    }
    return M3ETooltip(
      message: label,
      child: const Icon(M3EIcons.android_rounded, size: 30),
    );
  }

  String get privilegeConnectionLabel {
    final embedded = privilegeProvider == 'embedded';
    return embedded
        ? AppLocalizations.of(context).setupEmbeddedPairAndStart
        : !shizukuInstalled
        ? currentLocalizations().uiInstallShizuku.replaceAll(
            'Shizuku',
            privilegeProviderName,
          )
        : !shizukuRunning
        ? currentLocalizations().uiCheckingShizukuConnection.replaceAll(
            'Shizuku',
            privilegeProviderName,
          )
        : currentLocalizations().uiAllowShizukuPermissions.replaceAll(
            'Shizuku',
            privilegeProviderName,
          );
  }

  Widget shizukuPanel() {
    final colors = Theme.of(context).colorScheme;
    final embedded = privilegeProvider == 'embedded';
    return DextopGroup(
      color: colors.errorContainer.withValues(alpha: 0.55),
      children: [
        M3EListItem(
          leading: Icon(
            embedded
                ? M3EIcons.wifi_tethering_rounded
                : M3EIcons.warning_amber_rounded,
            color: colors.error,
          ),
          headline: privilegeConnectionLabel,
          trailing: const Icon(M3EIcons.chevron_right_rounded),
          onTap: connect,
        ),
      ],
    );
  }
}
