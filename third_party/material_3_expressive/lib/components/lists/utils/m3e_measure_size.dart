import 'package:flutter/widgets.dart';

/// Reports the rendered size of [child] to [onChange] after each layout.
class M3EMeasureSize extends StatefulWidget {
  /// child.
  final Widget child;

  /// Function.
  final void Function(Size) onChange;

  /// M3EMeasureSize.

  const M3EMeasureSize({
    super.key,
    required this.child,
    required this.onChange,
  });

  @override
  State<M3EMeasureSize> createState() => _M3EMeasureSizeState();
}

class _M3EMeasureSizeState extends State<M3EMeasureSize> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _notifySize());
  }

  @override
  void didUpdateWidget(covariant M3EMeasureSize oldWidget) {
    super.didUpdateWidget(oldWidget);
    WidgetsBinding.instance.addPostFrameCallback((_) => _notifySize());
  }

  void _notifySize() {
    if (mounted) {
      final box = context.findRenderObject() as RenderBox?;
      if (box != null && box.hasSize) {
        widget.onChange(box.size);
      }
    }
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
