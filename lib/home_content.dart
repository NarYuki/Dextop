part of 'main.dart';

extension _HomeContent on _HomeScreenState {
  Widget overview() {
    return CustomScrollView(
      key: ValueKey('overview'),
      slivers: [
        SliverAppBar.large(
          title: Text(currentLocalizations().appName),
          actions: [
            IconButton(onPressed: refresh, icon: Icon(Icons.refresh_rounded)),
            SizedBox(width: 8),
          ],
        ),
        SliverPadding(
          padding: EdgeInsets.fromLTRB(16, 0, 16, 32),
          sliver: SliverList.list(
            children: [
              heroPanel(),
              SizedBox(height: 16),
              if (!active &&
                  !autoActive &&
                  !stopping &&
                  recovery['recoverable'] == true) ...[
                recoveryPanel(),
                SizedBox(height: 16),
              ],
              workspacePanel(),
              SizedBox(height: 16),
              if (!loading &&
                  !(secureSettingsGranted &&
                      shizukuRunning &&
                      shizukuGranted)) ...[
                shizukuPanel(),
                SizedBox(height: 28),
              ],
              sectionTitle(currentLocalizations().display),
              SizedBox(height: 12),
              profileGrid(),
              SizedBox(height: 12),
              orientationControl(),
              if (error != null) ...[SizedBox(height: 16), errorPanel()],
            ],
          ),
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
    return Card(
      child: Padding(
        padding: EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                SizedBox.square(
                  dimension: 28,
                  child: Center(
                    child: Icon(
                      stopping
                          ? Icons.hourglass_top_rounded
                          : loading
                          ? Icons.hourglass_top_rounded
                          : needsAndroidRepair
                          ? Icons.warning_amber_rounded
                          : showRepairResult
                          ? Icons.check_circle_rounded
                          : hasExistingSession
                          ? Icons.history_rounded
                          : active || autoActive || ready
                          ? Icons.check_circle_rounded
                          : Icons.error_outline_rounded,
                      size: 24,
                      color: stopping
                          ? colors.onSurfaceVariant
                          : loading
                          ? colors.onSurfaceVariant
                          : needsAndroidRepair
                          ? colors.tertiary
                          : showRepairResult
                          ? colors.primary
                          : hasExistingSession
                          ? colors.onSurfaceVariant
                          : active || autoActive || ready
                          ? colors.primary
                          : colors.error,
                    ),
                  ),
                ),
                SizedBox(width: 10),
                Expanded(
                  child: Text(
                    stopping
                        ? currentLocalizations().uiDextopStopping
                        : loading
                        ? currentLocalizations().uiChecking
                        : autoPlus
                        ? currentLocalizations().uiRunningAutoPlus
                        : autoOnly
                        ? currentLocalizations().uiRunningAuto
                        : active
                        ? currentLocalizations().running
                        : needsAndroidRepair
                        ? currentLocalizations().uiAbnormalSessionWarning
                        : showRepairResult
                        ? currentLocalizations()
                              .uiTerminationProcessingCompletedSuccessfully
                        : hasExistingSession
                        ? currentLocalizations().uiThereIsAnExistingSession
                        : ready
                        ? currentLocalizations().uiDextopIsReady
                        : currentLocalizations().uiPreparationIsRequired,
                    softWrap: true,
                    overflow: TextOverflow.visible,
                    strutStyle: StrutStyle(
                      fontSize:
                          Theme.of(context).textTheme.titleLarge?.fontSize ??
                          22,
                      height: 1.15,
                      forceStrutHeight: true,
                    ),
                    style: Theme.of(
                      context,
                    ).textTheme.titleLarge?.copyWith(height: 1.15),
                  ),
                ),
              ],
            ),
            SizedBox(height: 20),
            if (autoOnly) ...[
              // Auto owns its independent virtual display. Starting another
              // phone-side session while it is active is intentionally
              // disabled until the two-session handoff is reintroduced.
              SizedBox(
                width: double.infinity,
                child: FilledButton.tonal(
                  onPressed: null,
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.phone_android_rounded, size: 20),
                      SizedBox(width: 8),
                      Text(currentLocalizations().uiStartPhoneDextop),
                    ],
                  ),
                ),
              ),
              SizedBox(height: 10),
            ],
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                style: needsAndroidRepair
                    ? FilledButton.styleFrom(
                        backgroundColor: Colors.amber.shade600,
                        foregroundColor: Colors.black,
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
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    stopping
                        ? SizedBox.square(
                            dimension: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : loading
                        ? SizedBox.square(
                            dimension: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : Icon(
                            needsAndroidRepair
                                ? Icons.build_rounded
                                : showRepairResult
                                ? Icons.restart_alt_rounded
                                : autoOnly
                                ? Icons.stop_rounded
                                : active
                                ? Icons.stop_rounded
                                : Icons.play_arrow_rounded,
                            size: 20,
                          ),
                    SizedBox(width: 8),
                    Text(
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
                      strutStyle: StrutStyle(
                        fontSize: 14,
                        height: 1,
                        forceStrutHeight: true,
                      ),
                      style: TextStyle(height: 1),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget recoveryPanel() {
    final width = recovery['width'] as int? ?? profile.width;
    final height = recovery['height'] as int? ?? profile.height;
    final density = recovery['density'] as int? ?? profile.density;
    return Card(
      child: Padding(
        padding: EdgeInsets.fromLTRB(16, 12, 16, 14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Icon(Icons.restore_rounded),
                SizedBox(width: 12),
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
                      SizedBox(height: 2),
                      Text(
                        '$width × $height / $density dpi',
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: SizedBox(
                    height: 40,
                    child: FilledButton.tonal(
                      onPressed: () async {
                        final recovered = DisplayProfile(
                          currentLocalizations().uiRecoverySession,
                          '$density dpi',
                          width,
                          height,
                          density,
                          Icons.restore_rounded,
                          id: 'recovery',
                        );
                        // Automatic resolution must be sampled again when a
                        // paused foldable session resumes; the active panel may
                        // have changed while Dextop was suspended.
                        final resumeProfile = profile.isDevice
                            ? profile
                            : recovered;
                        mutate(() => loading = true);
                        await bridge.start(
                          resumeProfile,
                          // Resolve automatic orientation again when resuming:
                          // the device may have rotated while suspended.
                          resolveHomePortrait(),
                          secure,
                          decorations: effectiveDecorations,
                          workspaceMagnificationPercent:
                              workspaceMagnificationPercent,
                        );
                        await Future<void>.delayed(Duration(milliseconds: 350));
                        await refresh();
                      },
                      child: Text(currentLocalizations().uiRestart),
                    ),
                  ),
                ),
                SizedBox(width: 8),
                Expanded(
                  child: SizedBox(
                    height: 40,
                    child: TextButton(
                      onPressed: () async {
                        await bridge.clearRecovery();
                        await refresh();
                      },
                      child: Text(currentLocalizations().uiDestruction),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget workspacePanel() {
    void openApps() => Navigator.of(context)
        .push(
          MaterialPageRoute<void>(
            builder: (_) => DextopFeaturesPage(
              isRunning: active,
              category: 'apps',
              ensureDesktopRunning: ensureDesktopRunning,
            ),
          ),
        )
        .then((_) => loadHomeWorkspaces());
    return Card(
      child: Column(
        children: [
          ListTile(
            leading: Icon(Icons.apps_rounded),
            title: Text(currentLocalizations().uiAppLauncher),
            subtitle: Text(currentLocalizations().uiOpenAppOnDesktop),
            trailing: Icon(Icons.chevron_right_rounded),
            onTap: openApps,
          ),
          Divider(height: 1),
          ListTile(
            leading: Icon(Icons.space_dashboard_rounded),
            title: Text(currentLocalizations().uiWorkSpace),
            subtitle: Text(currentLocalizations().uiOpenASavedAppConfiguration),
            trailing: AnimatedRotation(
              turns: workspaceExpanded ? .5 : 0,
              duration: Duration(milliseconds: 220),
              child: Icon(Icons.expand_more_rounded),
            ),
            onTap: () => mutate(() => workspaceExpanded = !workspaceExpanded),
          ),
          AnimatedSize(
            duration: Duration(milliseconds: 260),
            curve: Curves.easeInOutCubic,
            alignment: Alignment.topCenter,
            child: workspaceExpanded
                ? Column(
                    children: [
                      Divider(height: 1, indent: 16, endIndent: 16),
                      if (homeWorkspaces.isEmpty)
                        ListTile(
                          contentPadding: EdgeInsets.symmetric(horizontal: 16),
                          title: Text(
                            currentLocalizations().uiNoSavedWorkspaces,
                          ),
                        ),
                      if (homeAppsLoading)
                        Padding(
                          padding: EdgeInsets.fromLTRB(16, 4, 16, 8),
                          child: LinearProgressIndicator(
                            minHeight: 3,
                            borderRadius: BorderRadius.all(Radius.circular(3)),
                          ),
                        ),
                      ...homeWorkspaces.map(
                        (workspace) => ListTile(
                          contentPadding: EdgeInsets.only(
                            left: 16,
                            right: 16,
                            top: 4,
                            bottom: 4,
                          ),
                          title: Text('${workspace['name']}'),
                          subtitle: Padding(
                            padding: EdgeInsets.only(top: 8),
                            child: Wrap(
                              spacing: 6,
                              runSpacing: 6,
                              children: (workspace['apps'] as List)
                                  .cast<String>()
                                  .map(workspaceAppIcon)
                                  .toList(),
                            ),
                          ),
                          trailing: Icon(Icons.play_arrow_rounded),
                          onTap: () => launchHomeWorkspace(workspace),
                        ),
                      ),
                    ],
                  )
                : SizedBox.shrink(),
          ),
        ],
      ),
    );
  }

  Widget workspaceAppIcon(String packageName) {
    final bytes = homeApps[packageName]?['icon'];
    if (bytes is Uint8List) {
      return Tooltip(
        message: '${homeApps[packageName]?['label'] ?? packageName}',
        child: ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: Image.memory(bytes, width: 30, height: 30),
        ),
      );
    }
    if (homeAppsLoading) {
      return SizedBox.square(
        dimension: 28,
        child: Padding(
          padding: EdgeInsets.all(5),
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
      );
    }
    return Tooltip(
      message: packageName,
      child: Icon(Icons.android_rounded, size: 30),
    );
  }

  Widget shizukuPanel() {
    final colors = Theme.of(context).colorScheme;
    final embedded = privilegeProvider == 'embedded';
    final title = embedded
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
    return ListTile(
      contentPadding: EdgeInsets.symmetric(horizontal: 4),
      leading: Icon(
        embedded ? Icons.wifi_tethering_rounded : Icons.warning_amber_rounded,
        color: colors.error,
      ),
      title: Text(title),
      trailing: Icon(Icons.chevron_right_rounded),
      onTap: connect,
    );
  }
}
