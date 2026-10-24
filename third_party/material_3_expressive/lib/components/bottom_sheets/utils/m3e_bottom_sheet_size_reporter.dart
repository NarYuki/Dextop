import 'package:flutter/rendering.dart';
import 'package:flutter/widgets.dart';

/// Reports its child's laid-out size after each change.
class M3EBottomSheetSizeReporter extends SingleChildRenderObjectWidget {
  /// M3EBottomSheetSizeReporter.
  const M3EBottomSheetSizeReporter({
    required this.onSize,
    required super.child,
    super.key,
  });

  /// Called after layout when the size changed.
  final ValueChanged<Size> onSize;

  @override
  RenderObject createRenderObject(BuildContext context) =>
      _RenderSizeReporter(onSize);

  @override
  void updateRenderObject(BuildContext context, RenderObject renderObject) {
    if (renderObject is _RenderSizeReporter) {
      renderObject.onSize = onSize;
    }
  }
}

class _RenderSizeReporter extends RenderProxyBox {
  _RenderSizeReporter(this.onSize);

  ValueChanged<Size> onSize;
  Size? _reported;

  @override
  void performLayout() {
    super.performLayout();
    if (size == _reported) {
      return;
    }
    _reported = size;
    final Size reported = size;
    WidgetsBinding.instance.addPostFrameCallback((_) => onSize(reported));
  }
}
