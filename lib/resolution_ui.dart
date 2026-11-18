part of 'main.dart';

extension _ResolutionUi on _HomeScreenState {
  Widget profileGrid() {
    return DextopGroup(
      children: [
        M3EListItem(
          leading: const Icon(M3EIcons.monitor_rounded),
          headline: profile.name,
          supportingText: profile.detail,
          trailing: const Icon(M3EIcons.expand_more_rounded),
          enabled: !active,
          onTap: active ? null : showResolutionSheet,
        ),
      ],
    );
  }

  Widget orientationControl() {
    final l = AppLocalizations.of(context);
    return DextopChoiceGroup<_HomeOrientationMode>(
      choices: [
        (
          _HomeOrientationMode.automatic,
          l.automaticResolution,
          M3EIcons.screen_rotation_rounded,
        ),
        (
          _HomeOrientationMode.landscape,
          l.landscape,
          M3EIcons.stay_current_landscape_rounded,
        ),
        (
          _HomeOrientationMode.portrait,
          l.portrait,
          M3EIcons.stay_current_portrait_rounded,
        ),
      ],
      selected: orientationMode,
      onSelected: active ? null : setHomeOrientation,
    );
  }

  Future<void> showResolutionSheet() async {
    await M3EBottomSheet.show<void>(
      context,
      initialValue: M3EBottomSheetValue.expanded,
      showDragHandle: true,
      builder: (sheetContext) {
        final colors = Theme.of(sheetContext).colorScheme;
        return StatefulBuilder(
          builder: (sheetContext, setSheetState) => Padding(
            padding: EdgeInsets.fromLTRB(
              20,
              0,
              20,
              MediaQuery.viewInsetsOf(sheetContext).bottom + 24,
            ),
            child: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    currentLocalizations().resolution,
                    style: Theme.of(sheetContext).textTheme.headlineMedium,
                  ),
                  const SizedBox(height: 14),
                  M3ECard(
                    variant: M3ECardVariant.filled,
                    color: colors.surfaceContainer,
                    borderRadius: BorderRadius.circular(24),
                    padding: const EdgeInsets.all(18),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Icon(
                              M3EIcons.zoom_in_rounded,
                              color: colors.primary,
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Text(
                                currentLocalizations().displayMagnification,
                                style: Theme.of(
                                  sheetContext,
                                ).textTheme.titleMedium,
                              ),
                            ),
                            DextopStatusPill(
                              label: '$workspaceMagnificationPercent%',
                              containerColor: colors.secondaryContainer,
                              contentColor: colors.onSecondaryContainer,
                            ),
                          ],
                        ),
                        const SizedBox(height: 6),
                        Text(
                          currentLocalizations()
                              .displayMagnificationDescription,
                          style: Theme.of(sheetContext).textTheme.bodySmall
                              ?.copyWith(color: colors.onSurfaceVariant),
                        ),
                        const SizedBox(height: 8),
                        M3ESlider(
                          value: workspaceMagnificationPercent.toDouble(),
                          min: 100,
                          max: 200,
                          divisions: 10,
                          label: '$workspaceMagnificationPercent%',
                          enabled: !active,
                          onChanged: active
                              ? null
                              : (value) {
                                  setSheetState(
                                    () => workspaceMagnificationPercent =
                                        value.round(),
                                  );
                                },
                          onChangeEnd: active
                              ? null
                              : (value) async {
                                  await setWorkspaceMagnification(
                                    value.round(),
                                  );
                                },
                        ),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 6),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                localText(context, 'ui100'),
                                style: Theme.of(sheetContext)
                                    .textTheme
                                    .labelSmall
                                    ?.copyWith(color: colors.onSurfaceVariant),
                              ),
                              Text(
                                localText(context, 'ui200'),
                                style: Theme.of(sheetContext)
                                    .textTheme
                                    .labelSmall
                                    ?.copyWith(color: colors.onSurfaceVariant),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 10),
                        DextopGroup(
                          color: colors.surfaceContainerLow,
                          children: [
                            DextopToggle(
                              icon: M3EIcons.web_asset_rounded,
                              title: currentLocalizations()
                                  .magnifyWindowsWithDisplay,
                              subtitle: currentLocalizations()
                                  .magnifyWindowsWithDisplayDescription,
                              value: magnifyWindowsWithDisplay,
                              enabled: !active,
                              onChanged: active
                                  ? null
                                  : (value) async {
                                      setSheetState(
                                        () => magnifyWindowsWithDisplay = value,
                                      );
                                      await setMagnifyWindowsWithDisplay(value);
                                    },
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 14),
                  DextopGroup(
                    color: colors.surfaceContainer,
                    children: [
                      for (final item in profiles)
                        M3EListItem(
                          headline: item.name,
                          supportingText: item.detail,
                          selected: profile.id == item.id,
                          leading: Icon(
                            profile.id == item.id
                                ? M3EIcons.check_circle_rounded
                                : M3EIcons.monitor_rounded,
                          ),
                          trailing: M3EIconButton(
                            variant: M3EIconButtonVariant.standard,
                            tooltip: currentLocalizations().uiEdit,
                            icon: const Icon(M3EIcons.edit_rounded),
                            onPressed: () async {
                              Navigator.pop(sheetContext);
                              await showResolutionEditor(item);
                            },
                          ),
                          onTap: () async {
                            mutate(() => profile = item);
                            await _saveProfiles();
                            if (!sheetContext.mounted) return;
                            Navigator.pop(sheetContext);
                          },
                        ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  M3EButton.filled(
                    size: M3EButtonSize.md,
                    icon: const Icon(M3EIcons.add_rounded),
                    onPressed: () async {
                      Navigator.pop(sheetContext);
                      await showResolutionEditor(null);
                    },
                    label: Text(currentLocalizations().customAdd),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  Future<void> showResolutionEditor(DisplayProfile? existing) async {
    final isDevice = existing?.isDevice ?? false;
    final width = TextEditingController(
      text: existing?.width.toString() ?? '1920',
    );
    final height = TextEditingController(
      text: existing?.height.toString() ?? '1080',
    );
    final density = TextEditingController(
      text: existing?.density.toString() ?? '240',
    );
    await M3EBottomSheet.show<void>(
      context,
      showDragHandle: true,
      builder: (sheetContext) => Padding(
        padding: EdgeInsets.fromLTRB(
          20,
          0,
          20,
          MediaQuery.viewInsetsOf(sheetContext).bottom + 24,
        ),
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                existing == null
                    ? currentLocalizations().customAdd
                    : currentLocalizations().editResolution,
                style: Theme.of(sheetContext).textTheme.headlineSmall,
              ),
              const SizedBox(height: 20),
              Row(
                children: [
                  Expanded(
                    child: numberField(
                      width,
                      currentLocalizations().width,
                      enabled: !isDevice,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: numberField(
                      height,
                      currentLocalizations().height,
                      enabled: !isDevice,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              numberField(density, 'DPI'),
              const SizedBox(height: 20),
              M3EButton.filled(
                size: M3EButtonSize.md,
                onPressed: () async {
                  final w = int.tryParse(width.text),
                      h = int.tryParse(height.text),
                      dpi = int.tryParse(density.text);
                  if (w == null ||
                      h == null ||
                      dpi == null ||
                      !inRange(w, 480, 7680) ||
                      !inRange(h, 480, 7680) ||
                      !inRange(dpi, 80, 640)) {
                    return;
                  }
                  final updated = DisplayProfile(
                    isDevice ? existing!.name : '$w × $h',
                    '$dpi dpi',
                    w,
                    h,
                    dpi,
                    isDevice
                        ? M3EIcons.smartphone_rounded
                        : M3EIcons.monitor_rounded,
                    id:
                        existing?.id ??
                        DateTime.now().microsecondsSinceEpoch.toString(),
                    isDevice: isDevice,
                  );
                  mutate(() {
                    if (existing == null) {
                      profiles.add(updated);
                    } else {
                      profiles[profiles.indexWhere(
                            (item) => item.id == existing.id,
                          )] =
                          updated;
                    }
                    if (existing == null || profile.id == existing.id) {
                      profile = updated;
                    }
                  });
                  await _saveProfiles();
                  if (sheetContext.mounted) Navigator.pop(sheetContext);
                },
                child: Text(
                  existing == null
                      ? currentLocalizations().add
                      : currentLocalizations().save,
                ),
              ),
              if (existing != null && !isDevice) ...[
                const SizedBox(height: 10),
                M3EButton.icon(
                  style: M3EButtonStyle.text,
                  icon: const Icon(M3EIcons.delete_outline_rounded),
                  label: Text(currentLocalizations().deleteResolution),
                  onPressed: () async {
                    mutate(() {
                      profiles.removeWhere((item) => item.id == existing.id);
                      if (profile.id == existing.id) profile = profiles.first;
                    });
                    await _saveProfiles();
                    if (sheetContext.mounted) Navigator.pop(sheetContext);
                  },
                ),
              ],
            ],
          ),
        ),
      ),
    );
    width.dispose();
    height.dispose();
    density.dispose();
  }

  Widget numberField(
    TextEditingController controller,
    String label, {
    bool enabled = true,
  }) {
    return M3ETextField(
      controller: controller,
      enabled: enabled,
      label: label,
      variant: M3ETextFieldVariant.outlined,
      keyboardType: const TextInputType.numberWithOptions(
        decimal: false,
        signed: false,
      ),
      textInputAction: TextInputAction.next,
    );
  }

  bool inRange(int value, int min, int max) => value >= min && value <= max;
}
