part of 'm3e_adaptive_dialog.dart';

extension _M3EAdaptiveDialogMorph on _M3EAdaptiveDialogState {
  void _onVariant(M3EDialogVariant variant) {
    if (_variant == variant) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _measure());
      return;
    }
    final swap = _variant != null;
    _variant = variant;
    _pendingSwap = swap;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      widget.controller?.updateVariant(variant);
      if (!mounted) {
        return;
      }
      _pendingSwap = false;
      if (!swap || _lastRect == null) {
        _measure();
        _refresh();
        return;
      }
      _from = _lastRect;
      _measure();
      _to = _lastRect;
      final bool reduce = MediaQuery.maybeDisableAnimationsOf(context) ?? false;
      _morph!.value = reduce ? 1 : 0;
      if (!reduce) {
        _morph!.animateTo(1);
      }
      _refresh();
    });
  }

  void _measure() {
    if (!mounted) {
      return;
    }
    final RenderObject? host = context.findRenderObject();
    final RenderObject? box = _surfaceKey.currentContext?.findRenderObject();
    if (host is! RenderBox || box is! RenderBox || !box.hasSize) {
      return;
    }
    _lastRect = box.localToGlobal(Offset.zero, ancestor: host) & box.size;
  }

  Widget _morphed(Widget layout) {
    return AnimatedBuilder(
      animation: _morph!,
      child: layout,
      builder: (BuildContext context, Widget? child) {
        if (_pendingSwap) {
          return Opacity(opacity: 0, child: child);
        }
        final double p = m3eDialogSnap(_morph!.value);
        final Rect? from = _from;
        final Rect? to = _to;
        if (from == null || to == null || p >= 1 || to.isEmpty) {
          return child!;
        }
        final Rect current = Rect.lerp(from, to, p)!;
        final matrix = Matrix4.translationValues(current.left, current.top, 0)
          ..multiply(
            Matrix4.diagonal3Values(
              current.width / to.width,
              current.height / to.height,
              1,
            ),
          )
          ..multiply(Matrix4.translationValues(-to.left, -to.top, 0));
        return Opacity(
          opacity: p.clamp(0, 1),
          child: Transform(transform: matrix, child: child),
        );
      },
    );
  }
}
