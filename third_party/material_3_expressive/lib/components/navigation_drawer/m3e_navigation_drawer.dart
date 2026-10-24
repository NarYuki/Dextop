import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';
import 'package:motor/motor.dart';

import '../../../foundations/foundations.dart';
import 'components/m3e_drawer_destination_button.dart';
import 'controllers/m3e_navigation_drawer_controller.dart';
import 'enums/m3e_navigation_drawer_enums.dart';
import 'models/m3e_navigation_destination.dart';
import 'models/m3e_navigation_drawer_section.dart';

export 'controllers/m3e_navigation_drawer_controller.dart';
export 'enums/m3e_navigation_drawer_enums.dart';
export 'models/m3e_navigation_destination.dart';
export 'models/m3e_navigation_drawer_section.dart';
export 'styles/m3e_navigation_drawer_theme.dart';

part 'components/m3e_navigation_drawer_build.dart';

/// A Material 3 Expressive navigation drawer.
///
/// A standard drawer stays in the layout. Set [dismissible] to slide it
/// closed from a menu control. A modal drawer covers the screen, starts
/// closed, and dismisses from a destination, the scrim, a drag toward the
/// start edge, or system back.
class M3ENavigationDrawer extends StatefulWidget {
  /// M3ENavigationDrawer.
  const M3ENavigationDrawer({
    required this.destinations,
    required this.selectedIndex,
    required this.onDestinationSelected,
    this.headline,
    this.sections = const <M3ENavigationDrawerSection>[],
    this.type = M3ENavigationDrawerType.standard,
    this.dismissible = false,
    this.controller,
    this.onDismissed,
    super.key,
  }) : assert(destinations.length >= 1, 'A drawer needs 1+ destinations.');

  /// Primary destinations. Indices start at 0.
  final List<M3ENavigationDestination> destinations;

  /// Groups after [destinations]. Each group is separated by one divider.
  final List<M3ENavigationDrawerSection> sections;

  /// selectedIndex.
  final int selectedIndex;

  /// onDestinationSelected.
  final ValueChanged<int> onDestinationSelected;

  /// headline.
  final String? headline;

  /// Standard stays in the layout. Modal overlays the screen.
  final M3ENavigationDrawerType type;

  /// When true, a standard drawer opens and closes only from [controller].
  ///
  /// Selecting a destination does not close it. Ignored for a modal drawer.
  final bool dismissible;

  /// Optional control for selection and open or close.
  final M3ENavigationDrawerController? controller;

  /// Called when a dismissible or modal drawer finishes a close request.
  final VoidCallback? onDismissed;

  @override
  State<M3ENavigationDrawer> createState() => _M3ENavigationDrawerState();
}

class _M3ENavigationDrawerState extends State<M3ENavigationDrawer>
    with SingleTickerProviderStateMixin {
  final List<FocusNode> _nodes = <FocusNode>[];
  late final SingleMotionController _reveal;
  M3ENavigationDrawerController? _bound;
  OverlayEntry? _modalEntry;
  bool _revealSeeded = false;
  bool _open = true;
  bool _shown = false;

  bool get _isModal => widget.type == M3ENavigationDrawerType.modal;

  bool get _canDismiss => _isModal || widget.dismissible;

  int get _destinationCount {
    var count = widget.destinations.length;
    for (final section in widget.sections) {
      count += section.destinations.length;
    }
    return count;
  }

  @override
  void initState() {
    super.initState();
    _open = !_canDismiss || (widget.controller?.isOpen ?? false);
    _shown = _isModal && _open;
    _reveal = SingleMotionController(
      motion: const MaterialSpringMotion.expressiveSpatialDefault(),
      vsync: this,
    );
    _reveal.value = _open ? 1 : 0;
    _revealSeeded = true;
    _reveal.addListener(_onReveal);
    _syncNodes();
    _bindController();
    if (_shown) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _syncOverlay());
    }
  }

  @override
  void didUpdateWidget(M3ENavigationDrawer oldWidget) {
    super.didUpdateWidget(oldWidget);
    _syncNodes();
    _bindController();
    if (oldWidget.type != widget.type ||
        oldWidget.dismissible != widget.dismissible) {
      if (!_canDismiss) {
        _open = true;
        _shown = false;
        _removeOverlay();
      } else if (_isModal && _open) {
        _shown = true;
      }
      _animateReveal();
      WidgetsBinding.instance.addPostFrameCallback((_) => _syncOverlay());
    }
  }

  @override
  void dispose() {
    _bound?.removeListener(_onController);
    _bound?.detach(_controllerSelect);
    _reveal
      ..removeListener(_onReveal)
      ..dispose();
    for (final node in _nodes) {
      node.dispose();
    }
    _removeOverlay();
    super.dispose();
  }

  void _syncNodes() {
    final count = _destinationCount;
    while (_nodes.length < count) {
      _nodes.add(FocusNode());
    }
    while (_nodes.length > count) {
      _nodes.removeLast().dispose();
    }
  }

  void _bindController() {
    final next = widget.controller;
    if (identical(_bound, next)) {
      _bound?.updateIndex(widget.selectedIndex);
      _bound?.updateIsOpen(isOpen: _open);
      return;
    }
    _bound?.removeListener(_onController);
    _bound?.detach(_controllerSelect);
    _bound = next;
    next?.attach(
      select: _controllerSelect,
      setOpen: _setOpen,
      index: widget.selectedIndex,
      isOpen: _open,
    );
    next?.addListener(_onController);
  }

  void _controllerSelect(int index) {
    widget.onDestinationSelected(index);
    if (_isModal) {
      _setOpen(false);
    }
  }

  void _onController() {
    if (!mounted || _bound == null) {
      return;
    }
    if (_bound!.index != widget.selectedIndex) {
      widget.onDestinationSelected(_bound!.index);
    }
    if (_bound!.isOpen != _open) {
      _setOpen(_bound!.isOpen);
    }
  }

  void _handleSelect(int index) {
    widget.onDestinationSelected(index);
    if (_isModal) {
      _setOpen(false);
    }
  }

  void _setOpen(bool value) {
    if (!_canDismiss) {
      _bound?.updateIsOpen(isOpen: true);
      return;
    }
    final changed = _open != value;
    if (!_shouldApplyOpen(changed: changed, value: value)) {
      return;
    }
    if (changed && !value) {
      widget.onDismissed?.call();
    }
    setState(() => _applyOpenValue(value));
    _bound?.updateIsOpen(isOpen: _open);
    _animateReveal();
    _syncOverlay();
    _focusFirstDestinationIfNeeded(changed: changed, value: value);
  }

  bool _shouldApplyOpen({required bool changed, required bool value}) {
    final target = value ? 1.0 : 0.0;
    return changed || (_reveal.value - target).abs() >= 0.01;
  }

  void _applyOpenValue(bool value) {
    _open = value;
    if (_isModal && value && !_shown) {
      _shown = true;
      _jumpReveal(0);
    }
  }

  void _focusFirstDestinationIfNeeded({
    required bool changed,
    required bool value,
  }) {
    if (!(changed && value && _isModal && _nodes.isNotEmpty)) {
      return;
    }
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        _nodes.first.requestFocus();
      }
    });
  }

  void _jumpReveal(double value) {
    _reveal.removeListener(_onReveal);
    _reveal.value = value;
    _reveal.addListener(_onReveal);
  }

  void _onReveal() {
    if (!mounted) {
      return;
    }
    _modalEntry?.markNeedsBuild();
    if (_isModal && _shown && !_open && _reveal.value <= 0.01) {
      _shown = false;
      _removeOverlay();
    }
    setState(() {});
  }

  void _animateReveal() {
    if (!mounted) {
      return;
    }
    final spring = M3ETheme.of(context).navigationDrawerTheme.revealSpring;
    _reveal.motion = const MaterialSpringMotion.expressiveSpatialDefault()
        .copyWith(stiffness: spring.stiffness, damping: spring.damping);
    final target = _open ? 1.0 : 0.0;
    if (!_revealSeeded) {
      _jumpReveal(target);
      _revealSeeded = true;
      return;
    }
    if ((_reveal.value - target).abs() < 0.01) {
      return;
    }
    _reveal.animateTo(target);
  }

  void _syncOverlay() {
    if (!mounted) {
      return;
    }
    if (_isModal && _shown) {
      if (_modalEntry == null) {
        _modalEntry = OverlayEntry(
          builder: (BuildContext context) => _buildOverlay(context),
        );
        Overlay.of(context, rootOverlay: true).insert(_modalEntry!);
      } else {
        _modalEntry!.markNeedsBuild();
      }
    } else {
      _removeOverlay();
    }
  }

  void _removeOverlay() {
    _modalEntry?.remove();
    _modalEntry = null;
  }

  void _move(int delta) {
    final current = _nodes.indexWhere((FocusNode node) => node.hasFocus);
    if (current < 0) {
      return;
    }
    final next = current + delta;
    if (next < 0 || next >= _nodes.length) {
      return;
    }
    _nodes[next].requestFocus();
  }

  void _onDragUpdate(DragUpdateDetails details) {
    if (!_isModal || !_shown) {
      return;
    }
    final width = M3ETheme.of(context).navigationDrawerTheme.width;
    if (width <= 0) {
      return;
    }
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final delta = details.primaryDelta ?? 0;
    final towardEnd = rtl ? -delta : delta;
    _reveal.value = (_reveal.value + towardEnd / width).clamp(0.0, 1.0);
  }

  void _onDragEnd(DragEndDetails details) {
    final theme = M3ETheme.of(context).navigationDrawerTheme;
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final velocity = details.primaryVelocity ?? 0;
    final towardEnd = rtl ? -velocity : velocity;
    final close =
        towardEnd < -700 ||
        (towardEnd <= 700 && _reveal.value < theme.dismissDragThreshold);
    _setOpen(!close);
  }

  void _assertIcons() {
    final all = <M3ENavigationDestination>[
      ...widget.destinations,
      for (final section in widget.sections) ...section.destinations,
    ];
    final anyIcon = all.any(
      (M3ENavigationDestination item) => item.icon != null,
    );
    final missing = all.any(
      (M3ENavigationDestination item) => item.icon == null,
    );
    assert(
      !(anyIcon && missing),
      'Use icons on every drawer destination, or on none.',
    );
    assert(
      widget.sections.every(
        (M3ENavigationDrawerSection section) => section.destinations.isNotEmpty,
      ),
      'A section needs 1+ destinations.',
    );
  }

  @override
  Widget build(BuildContext context) {
    _assertIcons();
    return M3EComponentTheme(
      builder: (BuildContext context) {
        if (_isModal) {
          return PopScope(
            canPop: !_shown,
            onPopInvokedWithResult: (bool didPop, Object? result) {
              if (!didPop) {
                _setOpen(false);
              }
            },
            child: const SizedBox.shrink(),
          );
        }
        return _buildStandard(context);
      },
    );
  }
}
