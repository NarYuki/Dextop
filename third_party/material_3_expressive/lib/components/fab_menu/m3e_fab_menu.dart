import 'dart:async';
import 'dart:ui' show lerpDouble;

import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';
import 'package:motor/motor.dart';

import '../../foundations/foundations.dart';
import '../buttons/utils/m3e_button_gradient_layer.dart';
import '../floating_action_buttons/components/m3e_fab_container_transform.dart';
import '../floating_action_buttons/m3e_floating_action_buttons.dart';
import 'controllers/m3e_fab_menu_controller.dart';
import 'enums/m3e_fab_menu_color_set.dart';
import 'enums/m3e_fab_menu_position.dart';
import 'models/m3e_fab_menu_item.dart';
import 'styles/m3e_fab_menu_theme.dart';

export 'controllers/m3e_fab_menu_controller.dart';
export 'enums/m3e_fab_menu_color_set.dart';
export 'enums/m3e_fab_menu_position.dart';
export 'models/m3e_fab_menu_item.dart';
export 'styles/m3e_fab_menu_theme.dart';

part 'components/m3e_fab_menu_trigger.dart';
part 'components/m3e_fab_menu_overlay.dart';
part 'components/m3e_fab_menu_items.dart';

/// A Material 3 Expressive FAB menu.
///
/// Opens from a FAB to show 2–6 related actions. The FAB morphs into a 56dp
/// circular close button while menu pills spring in from the trailing edge.
/// When viewport height is limited, items scroll behind the close button.
class M3EFabMenu extends StatefulWidget {
  /// Creates a FAB menu.
  const M3EFabMenu({
    required this.items,
    this.icon = const Icon(M3EIcons.add),
    this.closeIcon = const Icon(M3EIcons.close),
    this.expandIcon,
    this.collapseIcon,
    this.color = M3EFabColor.primary,
    this.size = M3EFabSize.medium,
    this.position = M3EFabMenuPosition.right,
    this.decoration,
    this.controller,
    super.key,
  }) : assert(
         items.length >= 2 && items.length <= 6,
         'A FAB menu needs 2–6 items.',
       );

  /// Menu actions (2–6).
  final List<M3EFabMenuItem> items;

  /// Closed-state FAB icon (alias for [expandIcon] when that is null).
  final Widget icon;

  /// Open-state FAB icon (alias for [collapseIcon] when that is null).
  final Widget closeIcon;

  /// Icon when the menu is closed. Defaults to [icon] (add).
  final Widget? expandIcon;

  /// Icon when the menu is open. Defaults to [closeIcon] (close).
  final Widget? collapseIcon;

  /// FAB / menu color style. Maps to a primary/secondary/tertiary set.
  final M3EFabColor color;

  /// Closed FAB size. Open close button is always 56dp.
  final M3EFabSize size;

  /// Which horizontal corner the open FAB morphs toward.
  final M3EFabMenuPosition position;

  /// Styling for the trigger FAB. Menu items use [M3EFabMenuTheme].
  final M3EFabDecoration? decoration;

  /// Optional controller for programmatic open / close.
  final M3EFabMenuController? controller;

  @override
  State<M3EFabMenu> createState() => _M3EFabMenuState();
}

class _M3EFabMenuState extends State<M3EFabMenu>
    with TickerProviderStateMixin
    implements M3EFabMenuControllerClient {
  final LayerLink _link = LayerLink();
  final GlobalKey _fabKey = GlobalKey();
  final OverlayPortalController _portal = OverlayPortalController();
  final FocusNode _closeFocusNode = FocusNode(debugLabel: 'M3EFabMenuClose');

  late List<SingleMotionController> _itemCtrls;
  late List<bool> _itemVisible;
  late List<GlobalKey> _itemKeys;
  final GlobalKey _itemsStackKey = GlobalKey();

  final ValueNotifier<int?> _focusedItemIndex = ValueNotifier<int?>(null);
  late SingleMotionController _fabShapeCtrl;
  final List<Timer> _staggerTimers = <Timer>[];
  final FocusScopeNode _menuFocusScope = FocusScopeNode(
    debugLabel: 'M3EFabMenu',
    skipTraversal: true,
  );

  bool _open = false;
  M3EOverlayHistory? _overlayHistory;
  M3EFabContainerTransformHandle<dynamic>? _transformHandle;

  SpringMotion _springMotion(M3ESpring spring) =>
      const MaterialSpringMotion.expressiveSpatialDefault().copyWith(
        stiffness: spring.stiffness,
        damping: spring.damping,
      );

  SpringMotion get _expandMotion =>
      _springMotion(M3ETheme.of(context).fabMenuTheme.expandSpring);

  SpringMotion get _fabShapeMotion =>
      _springMotion(M3ETheme.of(context).fabMenuTheme.fabShapeSpring);

  Duration get _stagger => M3ETheme.of(context).fabMenuTheme.expandStagger;

  static const double _openWidthStart = 0.5;

  Widget get _resolvedExpandIcon => widget.expandIcon ?? widget.icon;

  Widget get _resolvedCollapseIcon => widget.collapseIcon ?? widget.closeIcon;

  bool get _isRight => widget.position == M3EFabMenuPosition.right;

  Alignment get _fabAlign => _isRight ? Alignment.topRight : Alignment.topLeft;

  Alignment get _menuItemAlign =>
      _isRight ? Alignment.centerRight : Alignment.centerLeft;

  M3EFabMenuColorSet get _colorSet => M3EFabMenuTheme.colorSetFor(widget.color);

  @override
  void initState() {
    super.initState();
    _itemCtrls = _createControllers(widget.items.length);
    _itemVisible = List<bool>.filled(widget.items.length, false);
    _itemKeys = List<GlobalKey>.generate(
      widget.items.length,
      (_) => GlobalKey(),
    );
    _fabShapeCtrl = SingleMotionController(
      motion: _springMotion(M3EFabMenuTheme.defaults.fabShapeSpring),
      vsync: this,
    );
    _menuFocusScope.traversalEdgeBehavior = TraversalEdgeBehavior.closedLoop;
    M3EFocusInteraction.instance.addListener(_onFocusInteractionChanged);
    widget.controller?.attachClient(this);
    widget.controller?.updateOpen(open: false);
  }

  void _onFocusInteractionChanged() {
    if (!M3EFocusInteraction.instance.ringsAllowed) {
      _focusedItemIndex.value = null;
    }
  }

  @override
  void didUpdateWidget(covariant M3EFabMenu oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller?.detachClient(this);
      widget.controller?.attachClient(this);
      widget.controller?.updateOpen(open: _open);
    }
    if (oldWidget.items.length != widget.items.length) {
      _disposeItemControllers();
      _itemCtrls = _createControllers(widget.items.length);
      _itemVisible = List<bool>.filled(widget.items.length, _open);
      _itemKeys = List<GlobalKey>.generate(
        widget.items.length,
        (_) => GlobalKey(),
      );
      _focusedItemIndex.value = null;
      if (_open) {
        for (final SingleMotionController c in _itemCtrls) {
          c.value = 1;
        }
      }
    }
  }

  void _holdOverlayHistory() {
    _overlayHistory ??= M3EOverlayHistory.register(context, onBack: _close);
  }

  void _releaseOverlayHistory() {
    final M3EOverlayHistory? history = _overlayHistory;
    _overlayHistory = null;
    history?.release();
  }

  @override
  void dispose() {
    _releaseOverlayHistory();
    widget.controller?.detachClient(this);
    M3EFocusInteraction.instance.removeListener(_onFocusInteractionChanged);
    _cancelStagger();
    _disposeItemControllers();
    _fabShapeCtrl.dispose();
    _menuFocusScope.dispose();
    _closeFocusNode.dispose();
    _focusedItemIndex.dispose();
    _transformHandle?.close();
    super.dispose();
  }

  List<SingleMotionController> _createControllers(int count) {
    final motion = _springMotion(M3EFabMenuTheme.defaults.expandSpring);
    return List<SingleMotionController>.generate(
      count,
      (_) => SingleMotionController(motion: motion, vsync: this),
    );
  }

  void _disposeItemControllers() {
    for (final SingleMotionController c in _itemCtrls) {
      c.dispose();
    }
  }

  void _cancelStagger() {
    for (final Timer t in _staggerTimers) {
      t.cancel();
    }
    _staggerTimers.clear();
  }

  @override
  void onOpenRequested() => _openMenu();

  @override
  void onCloseRequested() => _close();

  void _toggle() {
    if (_open) {
      _close();
    } else {
      _openMenu();
    }
  }

  void _scheduleItemReveal(int itemIndex, int delayMs) {
    _staggerTimers.add(
      Timer(Duration(milliseconds: delayMs), () {
        if (!mounted || !_open) {
          return;
        }
        setState(() => _itemVisible[itemIndex] = true);
        _itemCtrls[itemIndex]
          ..motion = _expandMotion
          ..value = 0
          ..animateTo(1);
        // After the last item mounts, focus the close button (spec a11y).
        if (itemIndex == 0) {
          _focusCloseAfterFrame();
        }
      }),
    );
  }

  void _focusCloseAfterFrame() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_open) {
        return;
      }
      _closeFocusNode.requestFocus();
    });
  }

  void _revealMenuItems() {
    _portal.show();
    // Cascade from the FAB upward: bottom item (nearest FAB) first.
    final int count = _itemCtrls.length;
    final int staggerMs = _stagger.inMilliseconds;
    for (var i = 0; i < count; i++) {
      final int fromFab = count - 1 - i;
      _scheduleItemReveal(i, fromFab * staggerMs);
    }
  }

  void _close() {
    if (!_open) {
      return;
    }
    _releaseOverlayHistory();
    _cancelStagger();
    // Instant hide — no reverse width morph (entry springs only).
    for (final SingleMotionController c in _itemCtrls) {
      c.value = 0;
    }
    _itemVisible = List<bool>.filled(_itemCtrls.length, false);
    _focusedItemIndex.value = null;
    _closeFocusNode.unfocus();
    _portal.hide();
    setState(() => _open = false);
    widget.controller?.updateOpen(open: false);
    _fabShapeCtrl
      ..motion = _fabShapeMotion
      ..animateTo(0);
  }

  void _openMenu() {
    if (_open) {
      return;
    }
    _cancelStagger();
    for (final SingleMotionController c in _itemCtrls) {
      c.value = 0;
    }
    _itemVisible = List<bool>.filled(_itemCtrls.length, false);
    _focusedItemIndex.value = null;
    setState(() => _open = true);
    _holdOverlayHistory();
    widget.controller?.updateOpen(open: true);
    _fabShapeCtrl
      ..motion = _fabShapeMotion
      ..animateTo(1);
    _revealMenuItems();
  }

  Future<void> _openItemTransform(M3EFabMenuItem item, int index) async {
    if (_transformHandle != null || item.openBuilder == null) {
      return;
    }
    final BuildContext? originContext = _itemKeys[index].currentContext;
    if (originContext == null || !originContext.mounted) {
      return;
    }
    final RenderObject? renderObject = originContext.findRenderObject();
    if (renderObject is! RenderBox || !renderObject.hasSize) {
      return;
    }
    final theme = M3ETheme.of(context);
    final fabMenuTheme = theme.fabMenuTheme;
    final Rect origin =
        renderObject.localToGlobal(Offset.zero) & renderObject.size;
    final handle = M3EFabContainerTransform.show(
      context: context,
      origin: origin,
      originRadius: fabMenuTheme.itemHeight / 2,
      originColor: fabMenuTheme.itemContainerColor(
        theme.colorScheme,
        _colorSet,
      ),
      openColor: item.openColor,
      scrimColor: item.transformScrimColor,
      builder: item.openBuilder!,
    );
    _transformHandle = handle;
    item.onPressed?.call();
    await handle.future;
    _transformHandle = null;
  }

  Future<void> _onItemTap(M3EFabMenuItem item, int index) async {
    if (item.openBuilder != null) {
      await _openItemTransform(item, index);
    } else {
      item.onPressed?.call();
    }
    if (mounted) {
      _close();
    }
  }

  @override
  Widget build(BuildContext context) => _M3EFabMenuTrigger(this).build(context);

  void _setItemFocused(int index, bool focused) {
    final bool show = focused && M3EFocusInteraction.instance.ringsAllowed;
    final int? next = show ? index : null;
    if (_focusedItemIndex.value == next) {
      return;
    }
    if (!show && _focusedItemIndex.value != index) {
      return;
    }
    _focusedItemIndex.value = next;
  }

  double _widthFactor(double t) =>
      _openWidthStart + (1.0 - _openWidthStart) * t;
}
