import 'package:flutter/widgets.dart';

import '../../../foundations/foundations.dart';
import '../../checkbox/m3e_checkbox.dart';
import '../../icon_buttons/m3e_icon_buttons.dart';
import '../controllers/m3e_selection_controller.dart';
import '../styles/m3e_selection_theme.dart';
import 'm3e_selection_scope.dart';

/// Wraps an idle header widget and swaps to a contextual selection bar.
///
/// [idle] may be any widget (e.g. an app bar, or a column of header content).
/// Height is intrinsic — place this above the list body (see M3ESelection),
/// not in a scaffold app-bar slot, so arbitrary idle heights do not overflow.
class M3ESelectionAppBar extends StatefulWidget {
  /// Creates a selection app bar wrapper.
  const M3ESelectionAppBar({
    required this.idle,
    this.controller,
    this.itemCount,
    this.actions = const <Widget>[],
    this.selectAllLabel = 'Select all',
    this.showSelectAll = true,
    this.onClear,
    this.onAllSelected,
    this.theme,
    super.key,
  });

  /// Shown when nothing is selected. Any widget; height is intrinsic.
  final Widget idle;

  /// Selection controller. Optional under [M3ESelectionScope].
  final M3ESelectionController? controller;

  /// Item count for select-all. Optional under [M3ESelectionScope].
  final int? itemCount;

  /// Contextual action widgets (typically [M3EIconButton]s).
  final List<Widget> actions;

  /// Label beside the select-all checkbox.
  final String selectAllLabel;

  /// Whether to show the select-all row in selection mode.
  final bool showSelectAll;

  /// Called instead of [M3ESelectionController.clear] when close is pressed.
  final VoidCallback? onClear;

  /// Fired when select-all toggles (`true` = all selected).
  final ValueChanged<bool>? onAllSelected;

  /// Theme override.
  final M3ESelectionTheme? theme;

  /// Returns a copy with an injected controller and optional [itemCount].
  M3ESelectionAppBar copyWithWiring({
    M3ESelectionController? controller,
    int? itemCount,
    ValueChanged<bool>? onAllSelected,
  }) {
    return M3ESelectionAppBar(
      key: key,
      idle: idle,
      controller: controller ?? this.controller,
      itemCount: itemCount ?? this.itemCount,
      actions: actions,
      selectAllLabel: selectAllLabel,
      showSelectAll: showSelectAll,
      onClear: onClear,
      onAllSelected: onAllSelected ?? this.onAllSelected,
      theme: theme,
    );
  }

  @override
  State<M3ESelectionAppBar> createState() => _M3ESelectionAppBarState();
}

class _M3ESelectionAppBarState extends State<M3ESelectionAppBar> {
  M3ESelectionController? _listened;

  M3ESelectionController _resolveController(BuildContext context) {
    final M3ESelectionController? explicit = widget.controller;
    if (explicit != null) {
      return explicit;
    }
    final M3ESelectionScope? scope = M3ESelectionScope.maybeOf(context);
    assert(
      scope != null,
      'M3ESelectionAppBar requires a controller or an M3ESelectionScope ancestor.',
    );
    return scope!.controller;
  }

  int _resolveItemCount(BuildContext context) {
    if (widget.itemCount != null) {
      return widget.itemCount!;
    }
    final M3ESelectionScope? scope = M3ESelectionScope.maybeOf(context);
    assert(
      scope != null,
      'M3ESelectionAppBar requires itemCount or an M3ESelectionScope ancestor.',
    );
    return scope!.itemCount;
  }

  void _attach(M3ESelectionController controller) {
    if (_listened == controller) {
      return;
    }
    _listened?.removeListener(_onChanged);
    _listened = controller;
    _listened!.addListener(_onChanged);
  }

  void _onChanged() {
    if (mounted) {
      setState(() {});
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _attach(_resolveController(context));
  }

  @override
  void didUpdateWidget(M3ESelectionAppBar oldWidget) {
    super.didUpdateWidget(oldWidget);
    _attach(_resolveController(context));
  }

  @override
  void dispose() {
    _listened?.removeListener(_onChanged);
    super.dispose();
  }

  M3ESelectionTheme _theme(BuildContext context) {
    return widget.theme ?? M3ETheme.of(context).selectionTheme;
  }

  void _clear(M3ESelectionController controller) {
    final VoidCallback? onClear = widget.onClear;
    if (onClear != null) {
      onClear();
    } else {
      controller.clear();
    }
  }

  void _toggleSelectAll(M3ESelectionController controller, int itemCount) {
    final bool? all = controller.allSelectedFor(itemCount);
    if (all ?? false) {
      controller.clear();
      widget.onAllSelected?.call(false);
    } else {
      controller.selectAll(itemCount);
      widget.onAllSelected?.call(true);
    }
  }

  /// Leading/trailing action slot width (M3EIconButton sm target).
  static const double _actionSlot = 48;

  Widget _buildContextualToolbar({
    required M3EThemeData theme,
    required Color foreground,
    required EdgeInsets appBarPad,
    required double titleGap,
    required M3ESelectionController controller,
  }) {
    // NavigationToolbar expands to max height — must be bounded (Column /
    // AnimatedSize pass infinite max height). Band matches the idle app bar;
    // overall header height still varies with select-all / idle content.
    return SizedBox(
      height: theme.appBarTheme.smallHeight,
      child: Padding(
        padding: appBarPad,
        child: NavigationToolbar(
          middleSpacing: titleGap,
          leading: M3EIconButton(
            variant: M3EIconButtonVariant.standard,
            icon: Icon(M3EIcons.close, color: foreground),
            onPressed: () => _clear(controller),
            tooltip: 'Clear selection',
            semanticLabel: 'Clear selection',
          ),
          centerMiddle: false,
          middle: Align(
            alignment: AlignmentDirectional.centerStart,
            child: Text(
              '${controller.selectedCount}',
              style: theme.typeScale.titleLarge.copyWith(color: foreground),
            ),
          ),
          trailing: Row(
            mainAxisSize: MainAxisSize.min,
            children: widget.actions,
          ),
        ),
      ),
    );
  }

  Widget? _buildSelectAllRow({
    required M3EThemeData theme,
    required M3ESelectionTheme selectionTheme,
    required Color foreground,
    required EdgeInsets appBarPad,
    required double titleGap,
    required M3ESelectionController controller,
    required int itemCount,
  }) {
    if (!widget.showSelectAll) {
      return null;
    }
    // Match toolbar: same contentPadding + 48dp leading slot (like close /
    // trailing icon buttons) and titleGap before the label.
    return Padding(
      padding: EdgeInsets.only(
        left: appBarPad.left,
        right: appBarPad.right,
        bottom: appBarPad.bottom,
      ),
      child: SizedBox(
        height: selectionTheme.selectAllHeight,
        child: Row(
          children: <Widget>[
            SizedBox(
              width: _actionSlot,
              child: Center(
                child: M3ECheckbox(
                  tristate: true,
                  value: controller.allSelectedFor(itemCount),
                  onChanged: (_) => _toggleSelectAll(controller, itemCount),
                  semanticLabel: widget.selectAllLabel,
                ),
              ),
            ),
            SizedBox(width: titleGap),
            Expanded(
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () => _toggleSelectAll(controller, itemCount),
                child: Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: Text(
                    widget.selectAllLabel,
                    style: theme.typeScale.bodyLarge.copyWith(
                      color: foreground,
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildContextual(BuildContext context) {
    final M3EThemeData theme = M3ETheme.of(context);
    final M3EColorScheme scheme = theme.colorScheme;
    final M3ESelectionTheme selectionTheme = _theme(context);
    final M3ESelectionController controller = _resolveController(context);
    final int itemCount = _resolveItemCount(context);
    final Color foreground = selectionTheme.contextualForeground(scheme);
    final EdgeInsets appBarPad = theme.appBarTheme.contentPadding.resolve(
      Directionality.of(context),
    );
    final double titleGap = theme.appBarTheme.titleGap;

    return ColoredBox(
      color: selectionTheme.contextualBackground(scheme),
      child: Padding(
        padding: EdgeInsets.only(top: M3ESafeArea.topOf(context)),
        child: IconTheme.merge(
          data: IconThemeData(color: foreground),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              _buildContextualToolbar(
                theme: theme,
                foreground: foreground,
                appBarPad: appBarPad,
                titleGap: titleGap,
                controller: controller,
              ),
              ?_buildSelectAllRow(
                theme: theme,
                selectionTheme: selectionTheme,
                foreground: foreground,
                appBarPad: appBarPad,
                titleGap: titleGap,
                controller: controller,
                itemCount: itemCount,
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final M3ESelectionController controller = _resolveController(context);
    _attach(controller);

    // AnimatedSize tracks the active child height. Switcher layout only sizes
    // to the current child; exiting children are overlayed so they do not
    // force a taller slot (avoids overflow when heights differ).
    return AnimatedSize(
      duration: const Duration(milliseconds: 220),
      reverseDuration: const Duration(milliseconds: 90),
      curve: const Cubic(0.2, 0, 0, 1),
      alignment: Alignment.topCenter,
      child: AnimatedSwitcher(
        duration: const Duration(milliseconds: 220),
        reverseDuration: const Duration(milliseconds: 90),
        switchInCurve: const Cubic(0.2, 0, 0, 1),
        switchOutCurve: const Cubic(0.4, 0, 1, 1),
        layoutBuilder: (Widget? currentChild, List<Widget> previousChildren) {
          return Stack(
            children: <Widget>[
              for (final Widget previous in previousChildren)
                Positioned(
                  top: 0,
                  left: 0,
                  right: 0,
                  child: IgnorePointer(child: previous),
                ),
              ?currentChild,
            ],
          );
        },
        transitionBuilder: (Widget child, Animation<double> animation) {
          final Animation<double> scale = Tween<double>(
            begin: 0.95,
            end: 1,
          ).animate(animation);
          return FadeTransition(
            opacity: animation,
            child: ScaleTransition(scale: scale, child: child),
          );
        },
        child: KeyedSubtree(
          key: ValueKey<bool>(controller.isSelectionMode),
          child: controller.isSelectionMode
              ? _buildContextual(context)
              : widget.idle,
        ),
      ),
    );
  }
}
