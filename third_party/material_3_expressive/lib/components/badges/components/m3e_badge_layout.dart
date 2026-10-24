import 'package:flutter/rendering.dart';
import 'package:flutter/widgets.dart';

import '../enums/m3e_badge_alignment.dart';

/// Parent data for the badge layout.
class M3EBadgeLayoutParentData extends ContainerBoxParentData<RenderBox> {}

/// Anchors an indicator to the top edge of its content.
///
/// [offset] is the distance from the content's top-trailing (or top-leading)
/// corner to the badge's **bottom-leading** corner (Compose placement).
///
/// The reported size matches the content child only — the indicator may paint
/// outside without shifting or expanding the child (hosts like nav bars stay
/// aligned).
class M3EBadgeLayout extends MultiChildRenderObjectWidget {
  /// M3EBadgeLayout.
  M3EBadgeLayout({
    required this.alignment,
    required this.offset,
    required this.textDirection,
    required Widget content,
    required Widget indicator,
    super.key,
  }) : super(children: <Widget>[content, indicator]);

  /// Top-edge placement of the indicator.
  ///
  /// [M3EBadgeAlignment.topRight] is treated as trailing and
  /// [M3EBadgeAlignment.topLeft] as leading; in RTL those origins swap.
  final M3EBadgeAlignment alignment;

  /// Distance from the anchored corner to the badge bottom-leading (W×H).
  final Offset offset;

  /// Used to resolve leading/trailing for [alignment].
  final TextDirection textDirection;

  @override
  RenderObject createRenderObject(BuildContext context) {
    return RenderM3EBadgeLayout(
      alignment: alignment,
      offset: offset,
      textDirection: textDirection,
    );
  }

  @override
  void updateRenderObject(
    BuildContext context,
    covariant RenderM3EBadgeLayout renderObject,
  ) {
    renderObject
      ..alignment = alignment
      ..offset = offset
      ..textDirection = textDirection;
  }
}

/// Lays out badge content with its indicator anchored to the content box.
class RenderM3EBadgeLayout extends RenderBox
    with
        ContainerRenderObjectMixin<RenderBox, M3EBadgeLayoutParentData>,
        RenderBoxContainerDefaultsMixin<RenderBox, M3EBadgeLayoutParentData> {
  /// Creates a badge layout render object.
  RenderM3EBadgeLayout({
    required this._alignment,
    required this._offset,
    required this._textDirection,
  });

  M3EBadgeAlignment _alignment;
  Offset _offset;
  TextDirection _textDirection;

  /// Top-edge placement of the indicator.
  M3EBadgeAlignment get alignment => _alignment;

  set alignment(M3EBadgeAlignment value) {
    if (value == _alignment) {
      return;
    }
    _alignment = value;
    markNeedsLayout();
  }

  /// Distance from the anchored corner to the badge bottom-leading.
  Offset get offset => _offset;

  set offset(Offset value) {
    if (value == _offset) {
      return;
    }
    _offset = value;
    markNeedsLayout();
  }

  /// Text direction for leading/trailing resolution.
  TextDirection get textDirection => _textDirection;

  set textDirection(TextDirection value) {
    if (value == _textDirection) {
      return;
    }
    _textDirection = value;
    markNeedsLayout();
  }

  @override
  void setupParentData(RenderBox child) {
    if (child.parentData is! M3EBadgeLayoutParentData) {
      child.parentData = M3EBadgeLayoutParentData();
    }
  }

  @override
  void performLayout() {
    final RenderBox? content = firstChild;
    final RenderBox? indicator = content == null ? null : childAfter(content);
    if (content == null || indicator == null) {
      size = constraints.smallest;
      return;
    }

    content.layout(constraints, parentUsesSize: true);
    indicator.layout(const BoxConstraints(), parentUsesSize: true);

    // Size is content-only so hosts (nav, app bars) are not pushed/shifted.
    size = content.size;
    _parentDataOf(content).offset = Offset.zero;
    _parentDataOf(indicator).offset = _indicatorOrigin(
      content.size,
      indicator.size,
    );
  }

  /// Whether [alignment] resolves to the trailing edge in [textDirection].
  bool get _isTrailing {
    final rtl = _textDirection == TextDirection.rtl;
    return switch (_alignment) {
      M3EBadgeAlignment.topRight => !rtl,
      M3EBadgeAlignment.topLeft => rtl,
      M3EBadgeAlignment.topCenter => false,
    };
  }

  /// Whether [alignment] resolves to the leading edge in [textDirection].
  bool get _isLeading {
    final rtl = _textDirection == TextDirection.rtl;
    return switch (_alignment) {
      M3EBadgeAlignment.topLeft => !rtl,
      M3EBadgeAlignment.topRight => rtl,
      M3EBadgeAlignment.topCenter => false,
    };
  }

  Offset _indicatorOrigin(Size content, Size indicator) {
    final double top = _offset.dy - indicator.height;
    if (_alignment == M3EBadgeAlignment.topCenter) {
      return Offset((content.width - indicator.width) / 2, top);
    }
    if (_isTrailing) {
      // Bottom-leading of badge is offset.dx inward from the top-trailing corner.
      return Offset(content.width - _offset.dx, top);
    }
    if (_isLeading) {
      // Mirror: bottom-trailing of badge is offset.dx inward from top-leading.
      return Offset(_offset.dx - indicator.width, top);
    }
    return Offset((content.width - indicator.width) / 2, top);
  }

  M3EBadgeLayoutParentData _parentDataOf(RenderBox child) =>
      child.parentData! as M3EBadgeLayoutParentData;

  @override
  Size computeDryLayout(BoxConstraints constraints) {
    final RenderBox? content = firstChild;
    if (content == null) {
      return constraints.smallest;
    }
    return content.getDryLayout(constraints);
  }

  @override
  double computeMinIntrinsicWidth(double height) =>
      firstChild?.getMinIntrinsicWidth(height) ?? 0;

  @override
  double computeMaxIntrinsicWidth(double height) =>
      firstChild?.getMaxIntrinsicWidth(height) ?? 0;

  @override
  double computeMinIntrinsicHeight(double width) =>
      firstChild?.getMinIntrinsicHeight(width) ?? 0;

  @override
  double computeMaxIntrinsicHeight(double width) =>
      firstChild?.getMaxIntrinsicHeight(width) ?? 0;

  @override
  void paint(PaintingContext context, Offset offset) {
    defaultPaint(context, offset);
  }

  /// Allow hits on the overhanging indicator outside [size].
  @override
  bool hitTest(BoxHitTestResult result, {required Offset position}) {
    if (hitTestChildren(result, position: position) ||
        (size.contains(position) && hitTestSelf(position))) {
      result.add(BoxHitTestEntry(this, position));
      return true;
    }
    return false;
  }

  @override
  bool hitTestChildren(BoxHitTestResult result, {required Offset position}) {
    return defaultHitTestChildren(result, position: position);
  }
}
