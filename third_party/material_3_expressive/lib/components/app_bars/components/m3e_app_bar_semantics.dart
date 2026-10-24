import 'package:flutter/rendering.dart';
import 'package:material_ui/material_ui.dart';

/// M3ESliverSemantic.

class M3ESliverSemantic extends SingleChildRenderObjectWidget {
  /// M3ESliverSemantic.
  const M3ESliverSemantic({
    super.key,
    required this.label,
    required Widget child,
  }) : super(child: child);

  /// label.
  final String label;
  @override
  RenderObject createRenderObject(BuildContext context) =>
      M3ESliverSemanticRender(label);
  @override
  void updateRenderObject(
    BuildContext context,
    covariant M3ESliverSemanticRender renderObject,
  ) {
    renderObject.label = label;
  }
}

/// M3ESliverSemanticRender.

class M3ESliverSemanticRender extends RenderProxySliver {
  /// M3ESliverSemanticRender.
  M3ESliverSemanticRender(this._label);
  String _label;

  /// The label.
  String get label => _label;

  /// label.
  set label(String v) {
    if (v == _label) {
      return;
    }
    _label = v;
    markNeedsSemanticsUpdate();
  }

  @override
  void describeSemanticsConfiguration(SemanticsConfiguration config) {
    super.describeSemanticsConfiguration(config);
    config
      ..label = _label
      ..isSemanticBoundary = true;
  }
}
