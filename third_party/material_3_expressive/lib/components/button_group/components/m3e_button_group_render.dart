part of '../m3e_button_group.dart';

class _ButtonGroupRenderObjectWidget extends MultiChildRenderObjectWidget {
  const _ButtonGroupRenderObjectWidget({
    required this.direction,
    required this.spacing,
    required this.pressedIndex,
    required this.animValue,
    required this.expandedRatio,
    required super.children,
  });

  final Axis direction;
  final double spacing;
  final int? pressedIndex;
  final double animValue;
  final double expandedRatio;

  @override
  M3ERenderButtonGroup createRenderObject(BuildContext context) {
    return M3ERenderButtonGroup(
      direction: direction,
      spacing: spacing,
      pressedIndex: pressedIndex,
      animValue: animValue,
      expandedRatio: expandedRatio,
    );
  }

  @override
  void updateRenderObject(
    BuildContext context,
    M3ERenderButtonGroup renderObject,
  ) {
    renderObject
      ..direction = direction
      ..spacing = spacing
      ..pressedIndex = pressedIndex
      ..animValue = animValue
      ..expandedRatio = expandedRatio;
  }
}

/// M3ERenderButtonGroup.

class M3ERenderButtonGroup extends RenderBox
    with
        ContainerRenderObjectMixin<RenderBox, M3EButtonGroupParentData>,
        RenderBoxContainerDefaultsMixin<RenderBox, M3EButtonGroupParentData> {
  /// M3ERenderButtonGroup.
  M3ERenderButtonGroup({
    required this._direction,
    required this._spacing,
    required this._pressedIndex,
    required this._animValue,
    required this._expandedRatio,
  });

  Axis _direction;

  /// direction.
  Axis get direction => _direction;

  /// direction.
  set direction(Axis value) {
    if (_direction == value) {
      return;
    }
    _direction = value;
    markNeedsLayout();
  }

  double _spacing;

  /// spacing.
  double get spacing => _spacing;

  /// spacing.
  set spacing(double value) {
    if (_spacing == value) {
      return;
    }
    _spacing = value;
    markNeedsLayout();
  }

  int? _pressedIndex;

  /// pressedIndex.
  int? get pressedIndex => _pressedIndex;

  /// pressedIndex.
  set pressedIndex(int? value) {
    if (_pressedIndex == value) {
      return;
    }
    _pressedIndex = value;
    markNeedsLayout();
  }

  double _animValue;

  /// animValue.
  double get animValue => _animValue;

  /// animValue.
  set animValue(double value) {
    if (_animValue == value) {
      return;
    }
    _animValue = value;
    markNeedsLayout();
  }

  double _expandedRatio;

  /// expandedRatio.
  double get expandedRatio => _expandedRatio;

  /// expandedRatio.
  set expandedRatio(double value) {
    if (_expandedRatio == value) {
      return;
    }
    _expandedRatio = value;
    markNeedsLayout();
  }

  @override
  void setupParentData(RenderBox child) {
    if (child.parentData is! M3EButtonGroupParentData) {
      child.parentData = M3EButtonGroupParentData();
    }
  }

  @override
  double computeMinIntrinsicWidth(double height) =>
      computeMaxIntrinsicWidth(height);

  @override
  double computeMaxIntrinsicWidth(double height) {
    if (direction == Axis.vertical) {
      var max = 0.toDouble();
      RenderBox? child = firstChild;
      while (child != null) {
        max = math.max(max, child.computeMaxIntrinsicWidth(height));
        child = childAfter(child);
      }
      return max;
    } else {
      var total = 0.toDouble();
      var count = 0;
      RenderBox? child = firstChild;
      while (child != null) {
        total += child.computeMaxIntrinsicWidth(height);
        count++;
        child = childAfter(child);
      }
      if (count > 0) {
        total += spacing * (count - 1);
      }
      return total;
    }
  }

  @override
  double computeMinIntrinsicHeight(double width) =>
      computeMaxIntrinsicHeight(width);

  @override
  double computeMaxIntrinsicHeight(double width) {
    if (direction == Axis.horizontal) {
      var max = 0.toDouble();
      RenderBox? child = firstChild;
      while (child != null) {
        max = math.max(max, child.computeMaxIntrinsicHeight(width));
        child = childAfter(child);
      }
      return max;
    } else {
      var total = 0.toDouble();
      var count = 0;
      RenderBox? child = firstChild;
      while (child != null) {
        total += child.computeMaxIntrinsicHeight(width);
        count++;
        child = childAfter(child);
      }
      if (count > 0) {
        total += spacing * (count - 1);
      }
      return total;
    }
  }

  @override
  void performLayout() {
    final childCount = this.childCount;
    if (childCount == 0) {
      size = constraints.smallest;
      return;
    }

    final isHorizontal = direction == Axis.horizontal;
    final naturalSizes = _layoutNaturalMainSizes(isHorizontal: isHorizontal);
    final sizes = _applySquishSizes(naturalSizes, childCount: childCount);
    final children = _collectChildren();
    final maxCross = _layoutChildrenWithMainSizes(
      children,
      sizes,
      isHorizontal: isHorizontal,
    );

    final totalMain =
        sizes.fold<double>(0, (a, b) => a + b) +
        (childCount > 0 ? spacing * (childCount - 1) : 0);

    size = constraints.constrain(
      isHorizontal ? Size(totalMain, maxCross) : Size(maxCross, totalMain),
    );
    _positionChildren(
      children,
      sizes,
      maxCross: maxCross,
      isHorizontal: isHorizontal,
    );
  }

  List<double> _layoutNaturalMainSizes({required bool isHorizontal}) {
    final naturalSizes = <double>[];
    RenderBox? child = firstChild;
    while (child != null) {
      final dryConstraints = isHorizontal
          ? BoxConstraints(maxHeight: constraints.maxHeight)
          : BoxConstraints(maxWidth: constraints.maxWidth);
      child.layout(dryConstraints, parentUsesSize: true);
      naturalSizes.add(isHorizontal ? child.size.width : child.size.height);
      child = childAfter(child);
    }
    return naturalSizes;
  }

  List<double> _applySquishSizes(
    List<double> naturalSizes, {
    required int childCount,
  }) {
    final sizes = List<double>.of(naturalSizes);
    if (!_canApplySquish(childCount)) {
      return sizes;
    }

    final pIndex = pressedIndex!;
    final growth = naturalSizes[pIndex] * expandedRatio * animValue;
    sizes[pIndex] += growth;
    _shrinkNeighborsForSquish(
      sizes,
      pressedIndex: pIndex,
      childCount: childCount,
      growth: growth,
    );
    return sizes;
  }

  bool _canApplySquish(int childCount) {
    return pressedIndex != null &&
        pressedIndex! >= 0 &&
        pressedIndex! < childCount &&
        animValue > 0;
  }

  void _shrinkNeighborsForSquish(
    List<double> sizes, {
    required int pressedIndex,
    required int childCount,
    required double growth,
  }) {
    final isMiddle = pressedIndex > 0 && pressedIndex < childCount - 1;
    if (isMiddle) {
      sizes[pressedIndex - 1] = math.max(
        0,
        sizes[pressedIndex - 1] - growth / 2,
      );
      sizes[pressedIndex + 1] = math.max(
        0,
        sizes[pressedIndex + 1] - growth / 2,
      );
      return;
    }
    if (pressedIndex == 0 && childCount > 1) {
      sizes[1] = math.max(0, sizes[1] - growth);
      return;
    }
    if (pressedIndex == childCount - 1 && childCount > 1) {
      sizes[pressedIndex - 1] = math.max(0, sizes[pressedIndex - 1] - growth);
    }
  }

  List<RenderBox> _collectChildren() {
    final children = <RenderBox>[];
    RenderBox? child = firstChild;
    while (child != null) {
      children.add(child);
      child = childAfter(child);
    }
    return children;
  }

  double _layoutChildrenWithMainSizes(
    List<RenderBox> children,
    List<double> sizes, {
    required bool isHorizontal,
  }) {
    var maxCross = 0.toDouble();
    for (var i = 0; i < children.length; i++) {
      final c = children[i];
      final mainSize = sizes[i];
      final childConstraints = isHorizontal
          ? BoxConstraints.tightFor(width: mainSize)
                .copyWith(minHeight: 0, maxHeight: constraints.maxHeight)
          : BoxConstraints.tightFor(height: mainSize)
                .copyWith(minWidth: 0, maxWidth: constraints.maxWidth);
      c.layout(childConstraints, parentUsesSize: true);
      maxCross = math.max(
        maxCross,
        isHorizontal ? c.size.height : c.size.width,
      );
    }
    return maxCross;
  }

  void _positionChildren(
    List<RenderBox> children,
    List<double> sizes, {
    required double maxCross,
    required bool isHorizontal,
  }) {
    var currentMainOffset = 0.toDouble();
    for (var i = 0; i < children.length; i++) {
      final c = children[i];
      final childParentData = c.parentData! as M3EButtonGroupParentData;
      final alignment = childParentData.alignment ?? CrossAxisAlignment.center;
      final childCross = isHorizontal ? c.size.height : c.size.width;
      final freeSpace = maxCross - childCross;
      final crossOffset = switch (alignment) {
        CrossAxisAlignment.center => freeSpace / 2,
        CrossAxisAlignment.end => freeSpace,
        _ => 0.toDouble(),
      };

      childParentData.offset = isHorizontal
          ? Offset(currentMainOffset, crossOffset)
          : Offset(crossOffset, currentMainOffset);
      currentMainOffset += sizes[i] + spacing;
    }
  }

  @override
  void paint(PaintingContext context, Offset offset) {
    defaultPaint(context, offset);
  }

  @override
  bool hitTestChildren(BoxHitTestResult result, {required Offset position}) {
    return defaultHitTestChildren(result, position: position);
  }
}
