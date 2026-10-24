part of 'm3e_carousel_wrapper.dart';

/// Registers the carousel viewport [RenderBox] without a [GlobalKey].
class _CarouselViewportAnchor extends SingleChildRenderObjectWidget {
  const _CarouselViewportAnchor({
    required this.onRegister,
    required this.onUnregister,
    required Widget child,
  }) : super(child: child);

  final void Function(RenderBox box) onRegister;
  final void Function(RenderBox box) onUnregister;

  @override
  RenderObject createRenderObject(BuildContext context) {
    return _RenderCarouselViewportAnchor(
      onRegister: onRegister,
      onUnregister: onUnregister,
    );
  }

  @override
  void updateRenderObject(
    BuildContext context,
    covariant _RenderCarouselViewportAnchor renderObject,
  ) {
    renderObject
      ..onRegister = onRegister
      ..onUnregister = onUnregister;
  }
}

class _RenderCarouselViewportAnchor extends RenderProxyBox {
  _RenderCarouselViewportAnchor({
    required this.onRegister,
    required this.onUnregister,
  });

  void Function(RenderBox box) onRegister;
  void Function(RenderBox box) onUnregister;

  void _registerIfReady() {
    if (hasSize && attached) {
      onRegister(this);
    }
  }

  @override
  void attach(PipelineOwner owner) {
    super.attach(owner);
    _registerIfReady();
  }

  @override
  void detach() {
    onUnregister(this);
    super.detach();
  }

  @override
  void performLayout() {
    super.performLayout();
    _registerIfReady();
  }
}

/// Registers its [RenderBox] for pulse measuring without a [GlobalKey].
class _CarouselItemAnchor extends SingleChildRenderObjectWidget {
  const _CarouselItemAnchor({
    required this.index,
    required this.onRegister,
    required this.onUnregister,
    required Widget child,
  }) : super(child: child);

  final int index;
  final void Function(int index, RenderBox box) onRegister;
  final void Function(int index, RenderBox box) onUnregister;

  @override
  RenderObject createRenderObject(BuildContext context) {
    return _RenderCarouselItemAnchor(
      index: index,
      onRegister: onRegister,
      onUnregister: onUnregister,
    );
  }

  @override
  void updateRenderObject(
    BuildContext context,
    covariant _RenderCarouselItemAnchor renderObject,
  ) {
    renderObject
      ..index = index
      ..onRegister = onRegister
      ..onUnregister = onUnregister;
  }
}

class _RenderCarouselItemAnchor extends RenderProxyBox {
  _RenderCarouselItemAnchor({
    required this._index,
    required this.onRegister,
    required this.onUnregister,
  });

  int _index;
  void Function(int index, RenderBox box) onRegister;
  void Function(int index, RenderBox box) onUnregister;

  int get index => _index;

  set index(int value) {
    if (_index == value) {
      return;
    }
    onUnregister(_index, this);
    _index = value;
    _registerIfReady();
  }

  void _registerIfReady() {
    if (hasSize && attached) {
      onRegister(_index, this);
    }
  }

  @override
  void attach(PipelineOwner owner) {
    super.attach(owner);
    _registerIfReady();
  }

  @override
  void detach() {
    onUnregister(_index, this);
    super.detach();
  }

  @override
  void performLayout() {
    super.performLayout();
    _registerIfReady();
  }
}
