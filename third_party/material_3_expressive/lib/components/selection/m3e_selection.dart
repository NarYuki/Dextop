import 'package:material_ui/material_ui.dart';

import '../../foundations/foundations.dart';
import 'components/m3e_selection_app_bar.dart';
import 'components/m3e_selection_scope.dart';
import 'controllers/m3e_selection_controller.dart';

export 'components/m3e_selection_app_bar.dart';
export 'components/m3e_selection_flip.dart';
export 'components/m3e_selection_leading.dart';
export 'components/m3e_selection_scope.dart';
export 'controllers/m3e_selection_controller.dart';
export 'styles/m3e_selection_theme.dart';

/// Host widget that wires [M3ESelectionAppBar] and any list [body] to one
/// [M3ESelectionController].
///
/// Prefer wrapping the route with [PopScope] so system back clears selection
/// instead of popping:
///
/// ```dart
/// PopScope(
///   canPop: !controller.isSelectionMode,
///   onPopInvokedWithResult: (didPop, _) {
///     if (!didPop) controller.clear();
///   },
///   child: M3ESelection(
///     controller: controller,
///     itemCount: items.length,
///     appBar: M3ESelectionAppBar(...),
///     body: M3EList.scrollable(...),
///   ),
/// )
/// ```
class M3ESelection extends StatefulWidget {
  /// Creates a selection host.
  const M3ESelection({
    required this.appBar,
    required this.body,
    required this.itemCount,
    this.controller,
    this.scaffold = true,
    this.backgroundColor,
    this.selectedColor,
    this.resizeToAvoidBottomInset,
    super.key,
  });

  /// Idle / contextual app bar wrapper.
  final M3ESelectionAppBar appBar;

  /// Any list body (card list, dismissible list, etc.).
  final Widget body;

  /// Item count for select-all / [M3ESelectionScope].
  final int itemCount;

  /// Optional external controller. When null, one is owned internally.
  final M3ESelectionController? controller;

  /// When true (default), builds a scaffold whose body is header + list.
  /// The selection header is not placed in the scaffold app-bar slot so idle
  /// can be any intrinsic-height widget. Set false to embed under a host scaffold.
  final bool scaffold;

  /// Scaffold background color when [scaffold] is true.
  final Color? backgroundColor;

  /// Fill used for selected list items. Overrides the selection theme highlight.
  final Color? selectedColor;

  /// Forwarded to [Scaffold.resizeToAvoidBottomInset].
  final bool? resizeToAvoidBottomInset;

  @override
  State<M3ESelection> createState() => _M3ESelectionState();
}

class _M3ESelectionState extends State<M3ESelection> {
  M3ESelectionController? _owned;
  late M3ESelectionController _controller;

  @override
  void initState() {
    super.initState();
    _bindController();
  }

  @override
  void didUpdateWidget(M3ESelection oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      _bindController();
      setState(() {});
    }
  }

  void _bindController() {
    final M3ESelectionController? external = widget.controller;
    if (external != null) {
      if (_owned != null) {
        _owned!.dispose();
        _owned = null;
      }
      _controller = external;
    } else if (_owned == null) {
      _owned = M3ESelectionController();
      _controller = _owned!;
    } else {
      _controller = _owned!;
    }
  }

  @override
  void dispose() {
    _owned?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final M3ESelectionAppBar wiredBar = widget.appBar.copyWithWiring(
      controller: _controller,
      itemCount: widget.appBar.itemCount ?? widget.itemCount,
    );

    return ListenableBuilder(
      listenable: _controller,
      builder: (BuildContext context, Widget? _) {
        final Widget content = Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            wiredBar,
            Expanded(child: widget.body),
          ],
        );
        Widget hosted = M3ESelectionScope(
          controller: _controller,
          itemCount: widget.itemCount,
          child: widget.scaffold
              ? Scaffold(
                  backgroundColor: widget.backgroundColor,
                  resizeToAvoidBottomInset: widget.resizeToAvoidBottomInset,
                  body: content,
                )
              : content,
        );
        final Color? selectedColor = widget.selectedColor;
        if (selectedColor != null) {
          final M3EThemeData theme = M3ETheme.of(context);
          hosted = M3ETheme(
            data: theme.copyWith(
              selectionTheme: theme.selectionTheme.copyWith(
                highlightColor: selectedColor,
              ),
            ),
            child: hosted,
          );
        }
        return hosted;
      },
    );
  }
}
