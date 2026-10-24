import 'package:flutter/rendering.dart';
import 'package:flutter/widgets.dart';

import '../enums/m3e_toolbar_enums.dart';
import '../res/m3e_toolbar_tokens.dart';

double _lerpD(double a, double b, double t) => a + (b - a) * t;

/// Parent data for FAB + pill morph layouts.
class M3EToolbarFabLayoutParentData extends ContainerBoxParentData<RenderBox> {}

/// Horizontal FAB + toolbar morph (pill grows opposite the FAB).
class M3EToolbarHorizontalFabLayout extends MultiChildRenderObjectWidget {
  /// M3EToolbarHorizontalFabLayout.
  M3EToolbarHorizontalFabLayout({
    required this.progress,
    required this.fabPosition,
    required this.isRtl,
    required Widget toolbar,
    required Widget fab,
    super.key,
  }) : super(children: <Widget>[toolbar, fab]);

  /// 0 = collapsed (FAB only), 1 = expanded.
  final double progress;

  /// Horizontal FAB edge (`start` / `end`).
  final M3EToolbarFabPosition fabPosition;

  /// isRtl.
  final bool isRtl;

  @override
  RenderObject createRenderObject(BuildContext context) {
    return RenderM3EToolbarHorizontalFabLayout(
      progress: progress,
      fabPosition: fabPosition,
      isRtl: isRtl,
    );
  }

  @override
  void updateRenderObject(
    BuildContext context,
    covariant RenderM3EToolbarHorizontalFabLayout renderObject,
  ) {
    renderObject
      ..progress = progress
      ..fabPosition = fabPosition
      ..isRtl = isRtl;
  }
}

/// Lays out a horizontal toolbar pill alongside a morphing FAB.
class RenderM3EToolbarHorizontalFabLayout extends RenderBox
    with
        ContainerRenderObjectMixin<RenderBox, M3EToolbarFabLayoutParentData>,
        RenderBoxContainerDefaultsMixin<
          RenderBox,
          M3EToolbarFabLayoutParentData
        > {
  /// Creates a horizontal FAB + toolbar layout render object.
  RenderM3EToolbarHorizontalFabLayout({
    required this._progress,
    required this._fabPosition,
    required this._isRtl,
  });

  double _progress;
  M3EToolbarFabPosition _fabPosition;
  bool _isRtl;

  /// Expand progress (0 = FAB only, 1 = full pill).
  double get progress => _progress;

  set progress(double value) {
    if (value == _progress) {
      return;
    }
    _progress = value;
    markNeedsLayout();
  }

  /// FAB edge on the horizontal axis.
  M3EToolbarFabPosition get fabPosition => _fabPosition;

  set fabPosition(M3EToolbarFabPosition value) {
    if (value == _fabPosition) {
      return;
    }
    _fabPosition = value;
    markNeedsLayout();
  }

  /// Whether layout mirrors for RTL.
  bool get isRtl => _isRtl;

  set isRtl(bool value) {
    if (value == _isRtl) {
      return;
    }
    _isRtl = value;
    markNeedsLayout();
  }

  @override
  void setupParentData(RenderBox child) {
    if (child.parentData is! M3EToolbarFabLayoutParentData) {
      child.parentData = M3EToolbarFabLayoutParentData();
    }
  }

  @override
  void performLayout() {
    final RenderBox? toolbarChild = firstChild;
    final RenderBox? fabChild = toolbarChild == null
        ? null
        : childAfter(toolbarChild);
    if (toolbarChild == null || fabChild == null) {
      size = constraints.smallest;
      return;
    }

    const double containerSize = M3EToolbarTokens.containerSize;
    const double gap = M3EToolbarTokens.toolbarToFabGap;
    const double fabBaseline = M3EToolbarTokens.fabBaseline;
    const double fabMedium = M3EToolbarTokens.fabMedium;
    const totalHeight = fabMedium;

    toolbarChild.layout(
      BoxConstraints(
        maxWidth: constraints.hasBoundedWidth
            ? constraints.maxWidth - fabBaseline - gap
            : double.infinity,
        minHeight: containerSize,
        maxHeight: containerSize,
      ),
      parentUsesSize: true,
    );
    final double naturalWidth = toolbarChild.size.width;

    final double fabSize = _lerpD(fabMedium, fabBaseline, _progress);
    final double totalWidth = naturalWidth + gap + fabBaseline;

    fabChild.layout(
      BoxConstraints.tight(Size(fabSize, fabSize)),
      parentUsesSize: true,
    );

    size = constraints.constrain(Size(totalWidth, totalHeight));

    final isEnd = _fabPosition == M3EToolbarFabPosition.end;
    final bool isEndEffective = _isRtl ? !isEnd : isEnd;

    final double clampedProgress = _progress.clamp(0.0, 1.2);
    final double toolbarWidth = naturalWidth * clampedProgress;

    final double fabX = isEndEffective ? (totalWidth - fabSize) : 0;
    final double fabY = (totalHeight - fabSize) / 2;

    final double toolbarX = isEndEffective
        ? (naturalWidth - toolbarWidth)
        : (totalWidth - naturalWidth);
    const double toolbarY = (totalHeight - containerSize) / 2;

    (toolbarChild.parentData! as M3EToolbarFabLayoutParentData).offset = Offset(
      toolbarX,
      toolbarY,
    );
    (fabChild.parentData! as M3EToolbarFabLayoutParentData).offset = Offset(
      fabX,
      fabY,
    );
  }

  @override
  void paint(PaintingContext context, Offset offset) {
    final RenderBox? toolbarChild = firstChild;
    if (toolbarChild != null) {
      final parentData =
          toolbarChild.parentData! as M3EToolbarFabLayoutParentData;
      final double clampedProgress = _progress.clamp(0.0, 1.0);
      final isEnd = _fabPosition == M3EToolbarFabPosition.end;
      final bool isEndEffective = _isRtl ? !isEnd : isEnd;
      final double naturalWidth = toolbarChild.size.width;
      final double opacityFactor = const Interval(
        0.5,
        1,
        curve: Curves.easeIn,
      ).transform(clampedProgress);

      context.pushOpacity(offset, (opacityFactor * 255).round(), (
        PaintingContext context,
        Offset offset,
      ) {
        if (clampedProgress >= 0.99) {
          context.paintChild(toolbarChild, offset + parentData.offset);
        } else {
          const double margin = 48;
          final Rect clipRect;
          if (isEndEffective) {
            final double left = naturalWidth * (1 - clampedProgress);
            clipRect = Rect.fromLTRB(
              left,
              -margin,
              naturalWidth + margin,
              toolbarChild.size.height + margin,
            );
          } else {
            final double right = naturalWidth * clampedProgress;
            clipRect = Rect.fromLTRB(
              -margin,
              -margin,
              right,
              toolbarChild.size.height + margin,
            );
          }
          context.pushClipRect(
            needsCompositing,
            offset + parentData.offset,
            clipRect,
            (PaintingContext context, Offset offset) {
              context.paintChild(toolbarChild, offset);
            },
          );
        }
      });
    }

    final RenderBox? fabChild = firstChild == null
        ? null
        : childAfter(firstChild!);
    if (fabChild != null) {
      final fabParentData =
          fabChild.parentData! as M3EToolbarFabLayoutParentData;
      context.paintChild(fabChild, fabParentData.offset + offset);
    }
  }

  @override
  bool hitTestChildren(BoxHitTestResult result, {required Offset position}) {
    final RenderBox? fabChild = firstChild == null
        ? null
        : childAfter(firstChild!);
    if (fabChild != null) {
      final fabParentData =
          fabChild.parentData! as M3EToolbarFabLayoutParentData;
      if (fabChild.hitTest(result, position: position - fabParentData.offset)) {
        return true;
      }
    }
    if (_progress > 0) {
      final RenderBox? toolbarChild = firstChild;
      if (toolbarChild != null) {
        final toolbarParentData =
            toolbarChild.parentData! as M3EToolbarFabLayoutParentData;
        if (toolbarChild.hitTest(
          result,
          position: position - toolbarParentData.offset,
        )) {
          return true;
        }
      }
    }
    return false;
  }
}

/// Vertical FAB + toolbar morph (pill grows opposite the FAB).
class M3EToolbarVerticalFabLayout extends MultiChildRenderObjectWidget {
  /// M3EToolbarVerticalFabLayout.
  M3EToolbarVerticalFabLayout({
    required this.progress,
    required this.fabPosition,
    required Widget toolbar,
    required Widget fab,
    super.key,
  }) : super(children: <Widget>[toolbar, fab]);

  /// 0 = collapsed (FAB only), 1 = expanded.
  final double progress;

  /// Vertical FAB edge (`top` / `bottom`).
  final M3EToolbarFabPosition fabPosition;

  @override
  RenderObject createRenderObject(BuildContext context) {
    return RenderM3EToolbarVerticalFabLayout(
      progress: progress,
      fabPosition: fabPosition,
    );
  }

  @override
  void updateRenderObject(
    BuildContext context,
    covariant RenderM3EToolbarVerticalFabLayout renderObject,
  ) {
    renderObject
      ..progress = progress
      ..fabPosition = fabPosition;
  }
}

/// Lays out a vertical toolbar pill alongside a morphing FAB.
class RenderM3EToolbarVerticalFabLayout extends RenderBox
    with
        ContainerRenderObjectMixin<RenderBox, M3EToolbarFabLayoutParentData>,
        RenderBoxContainerDefaultsMixin<
          RenderBox,
          M3EToolbarFabLayoutParentData
        > {
  /// Creates a vertical FAB + toolbar layout render object.
  RenderM3EToolbarVerticalFabLayout({
    required this._progress,
    required this._fabPosition,
  });

  double _progress;
  M3EToolbarFabPosition _fabPosition;

  /// Expand progress (0 = FAB only, 1 = full pill).
  double get progress => _progress;

  set progress(double value) {
    if (value == _progress) {
      return;
    }
    _progress = value;
    markNeedsLayout();
  }

  /// FAB edge on the vertical layout.
  M3EToolbarFabPosition get fabPosition => _fabPosition;

  set fabPosition(M3EToolbarFabPosition value) {
    if (value == _fabPosition) {
      return;
    }
    _fabPosition = value;
    markNeedsLayout();
  }

  @override
  void setupParentData(RenderBox child) {
    if (child.parentData is! M3EToolbarFabLayoutParentData) {
      child.parentData = M3EToolbarFabLayoutParentData();
    }
  }

  @override
  void performLayout() {
    final RenderBox? toolbarChild = firstChild;
    final RenderBox? fabChild = toolbarChild == null
        ? null
        : childAfter(toolbarChild);
    if (toolbarChild == null || fabChild == null) {
      size = constraints.smallest;
      return;
    }

    const double containerSize = M3EToolbarTokens.containerSize;
    const double gap = M3EToolbarTokens.toolbarToFabGap;
    const double fabBaseline = M3EToolbarTokens.fabBaseline;
    const double fabMedium = M3EToolbarTokens.fabMedium;
    const totalWidth = fabMedium;

    toolbarChild.layout(
      BoxConstraints(
        minWidth: containerSize,
        maxWidth: containerSize,
        maxHeight: constraints.hasBoundedHeight
            ? constraints.maxHeight - fabBaseline - gap
            : double.infinity,
      ),
      parentUsesSize: true,
    );
    final double naturalHeight = toolbarChild.size.height;

    final double fabSize = _lerpD(fabMedium, fabBaseline, _progress);
    final double totalHeight = naturalHeight + gap + fabBaseline;

    fabChild.layout(
      BoxConstraints.tight(Size(fabSize, fabSize)),
      parentUsesSize: true,
    );

    size = constraints.constrain(Size(totalWidth, totalHeight));

    final isBottom = _fabPosition == M3EToolbarFabPosition.bottom;
    final double clampedProgress = _progress.clamp(0.0, 1.2);
    final double toolbarHeight = naturalHeight * clampedProgress;

    final double fabX = (totalWidth - fabSize) / 2;
    final double fabY = isBottom ? (totalHeight - fabSize) : 0;

    final double toolbarY = isBottom
        ? (naturalHeight - toolbarHeight)
        : (totalHeight - naturalHeight);
    const double toolbarX = (totalWidth - containerSize) / 2;

    (toolbarChild.parentData! as M3EToolbarFabLayoutParentData).offset = Offset(
      toolbarX,
      toolbarY,
    );
    (fabChild.parentData! as M3EToolbarFabLayoutParentData).offset = Offset(
      fabX,
      fabY,
    );
  }

  @override
  void paint(PaintingContext context, Offset offset) {
    final RenderBox? toolbarChild = firstChild;
    if (toolbarChild != null) {
      final parentData =
          toolbarChild.parentData! as M3EToolbarFabLayoutParentData;
      final double clampedProgress = _progress.clamp(0.0, 1.0);
      final isBottom = _fabPosition == M3EToolbarFabPosition.bottom;
      final double naturalHeight = toolbarChild.size.height;
      final double opacityFactor = const Interval(
        0.5,
        1,
        curve: Curves.easeIn,
      ).transform(clampedProgress);

      context.pushOpacity(offset, (opacityFactor * 255).round(), (
        PaintingContext context,
        Offset offset,
      ) {
        if (clampedProgress >= 0.99) {
          context.paintChild(toolbarChild, offset + parentData.offset);
        } else {
          const double margin = 48;
          final Rect clipRect;
          if (isBottom) {
            final double top = naturalHeight * (1 - clampedProgress);
            clipRect = Rect.fromLTRB(
              -margin,
              top,
              toolbarChild.size.width + margin,
              naturalHeight + margin,
            );
          } else {
            final double bottom = naturalHeight * clampedProgress;
            clipRect = Rect.fromLTRB(
              -margin,
              -margin,
              toolbarChild.size.width + margin,
              bottom,
            );
          }
          context.pushClipRect(
            needsCompositing,
            offset + parentData.offset,
            clipRect,
            (PaintingContext context, Offset offset) {
              context.paintChild(toolbarChild, offset);
            },
          );
        }
      });
    }

    final RenderBox? fabChild = firstChild == null
        ? null
        : childAfter(firstChild!);
    if (fabChild != null) {
      final fabParentData =
          fabChild.parentData! as M3EToolbarFabLayoutParentData;
      context.paintChild(fabChild, fabParentData.offset + offset);
    }
  }

  @override
  bool hitTestChildren(BoxHitTestResult result, {required Offset position}) {
    final RenderBox? fabChild = firstChild == null
        ? null
        : childAfter(firstChild!);
    if (fabChild != null) {
      final fabParentData =
          fabChild.parentData! as M3EToolbarFabLayoutParentData;
      if (fabChild.hitTest(result, position: position - fabParentData.offset)) {
        return true;
      }
    }
    if (_progress > 0) {
      final RenderBox? toolbarChild = firstChild;
      if (toolbarChild != null) {
        final toolbarParentData =
            toolbarChild.parentData! as M3EToolbarFabLayoutParentData;
        if (toolbarChild.hitTest(
          result,
          position: position - toolbarParentData.offset,
        )) {
          return true;
        }
      }
    }
    return false;
  }
}
