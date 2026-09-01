part of 'main.dart';

extension _ResolutionUi on _HomeScreenState {
  Widget profileGrid() {
    return Card(
      child: ListTile(
        contentPadding: EdgeInsets.symmetric(horizontal: 20, vertical: 10),
        leading: Icon(Icons.monitor_rounded),
        title: Text(profile.name),
        subtitle: Text(profile.detail),
        trailing: Icon(Icons.expand_more_rounded),
        onTap: active ? null : showResolutionSheet,
      ),
    );
  }

  Widget orientationControl() {
    final l = AppLocalizations.of(context);
    return independentSegmentSwitch<bool>(
      choices: [
        (false, l.landscape, Icons.stay_current_landscape_rounded),
        (true, l.portrait, Icons.stay_current_portrait_rounded),
      ],
      selected: portrait,
      onSelected: active ? null : setPortrait,
    );
  }

  Future<void> showResolutionSheet() async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (sheetContext) {
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
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    AppStrings.tr('resolution'),
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                  SizedBox(height: 12),
                  Card(
                    margin: EdgeInsets.zero,
                    child: Padding(
                      padding: EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Icon(Icons.zoom_in_rounded),
                              SizedBox(width: 12),
                              Expanded(
                                child: Text(
                                  AppStrings.tr('displayMagnification'),
                                  style: Theme.of(
                                    context,
                                  ).textTheme.titleMedium,
                                ),
                              ),
                              Text('$workspaceMagnificationPercent%'),
                            ],
                          ),
                          SizedBox(height: 6),
                          Text(
                            AppStrings.tr('displayMagnificationDescription'),
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                          SizedBox(height: 4),
                          Slider(
                            value: workspaceMagnificationPercent.toDouble(),
                            min: 100,
                            max: 200,
                            divisions: 5,
                            label: '$workspaceMagnificationPercent%',
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
                            padding: EdgeInsets.symmetric(horizontal: 8),
                            child: Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [Text('100%'), Text('200%')],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  SizedBox(height: 12),
                  ...profiles.map(
                    (item) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(item.name),
                      subtitle: Text(item.detail),
                      trailing: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (profile.id == item.id) Icon(Icons.check_rounded),
                          IconButton(
                            tooltip: AppStrings.tr('uiEdit'),
                            icon: Icon(Icons.edit_rounded),
                            onPressed: () async {
                              Navigator.pop(sheetContext);
                              await showResolutionEditor(item);
                            },
                          ),
                        ],
                      ),
                      onTap: () async {
                        mutate(() => profile = item);
                        await _saveProfiles();
                        if (!sheetContext.mounted) return;
                        Navigator.pop(sheetContext);
                      },
                    ),
                  ),
                  SizedBox(height: 16),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton.icon(
                      icon: Icon(Icons.add_rounded),
                      onPressed: () async {
                        Navigator.pop(sheetContext);
                        await showResolutionEditor(null);
                      },
                      label: Text(AppStrings.tr('customAdd')),
                    ),
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
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
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
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                existing == null
                    ? AppStrings.tr('customAdd')
                    : AppStrings.tr('editResolution'),
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              SizedBox(height: 20),
              Row(
                children: [
                  Expanded(
                    child: numberField(
                      width,
                      AppStrings.tr('width'),
                      enabled: !isDevice,
                    ),
                  ),
                  SizedBox(width: 12),
                  Expanded(
                    child: numberField(
                      height,
                      AppStrings.tr('height'),
                      enabled: !isDevice,
                    ),
                  ),
                ],
              ),
              SizedBox(height: 12),
              numberField(density, 'DPI'),
              SizedBox(height: 20),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
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
                          ? Icons.smartphone_rounded
                          : Icons.monitor_rounded,
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
                        ? AppStrings.tr('add')
                        : AppStrings.tr('save'),
                  ),
                ),
              ),
              if (existing != null && !isDevice) ...[
                SizedBox(height: 8),
                SizedBox(
                  width: double.infinity,
                  child: TextButton.icon(
                    icon: Icon(Icons.delete_outline_rounded),
                    label: Text(AppStrings.tr('deleteResolution')),
                    onPressed: () async {
                      mutate(() {
                        profiles.removeWhere((item) => item.id == existing.id);
                        if (profile.id == existing.id) profile = profiles.first;
                      });
                      await _saveProfiles();
                      if (sheetContext.mounted) Navigator.pop(sheetContext);
                    },
                  ),
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
    return TextField(
      controller: controller,
      enabled: enabled,
      // Do not filter the composing text produced by Samsung DeX/physical
      // keyboards while the field is being edited.  digitsOnly rejects some
      // IME composing updates and makes the field appear read-only (most
      // often for the DPI field).  Values are validated when Apply/Save is
      // pressed below, so accepting the edit buffer here is safe.
      keyboardType: TextInputType.numberWithOptions(
        decimal: false,
        signed: false,
      ),
      textInputAction: TextInputAction.next,
      autocorrect: false,
      enableSuggestions: false,
      enableInteractiveSelection: true,
      decoration: InputDecoration(
        labelText: label,
        border: OutlineInputBorder(),
      ),
    );
  }

  bool inRange(int value, int min, int max) => value >= min && value <= max;
}
