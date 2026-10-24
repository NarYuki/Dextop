import 'package:flutter/widgets.dart';

import '../enums/m3e_menu_anchor_position.dart';
import '../models/m3e_menu_node.dart';
import '../styles/m3e_menu_theme.dart';

/// Computed placement for an anchored menu popup.
@immutable
class M3EMenuPlacement {
  /// M3EMenuPlacement.
  const M3EMenuPlacement({
    required this.left,
    required this.width,
    required this.maxHeight,
    required this.scaleAlignment,
    required this.opensAbove,
    this.top,
    this.bottom,
  }) : assert(
         (top == null) != (bottom == null),
         'Exactly one of top or bottom must be set.',
       );

  /// left.

  final double left;

  /// Distance from the top of the screen when opening below the anchor.
  final double? top;

  /// Distance from the bottom of the screen when opening above the anchor.
  ///
  /// Pinning with [bottom] keeps the menu flush under the available space
  /// regardless of content height (unlike subtracting [maxHeight] from [top]).
  final double? bottom;

  /// width.

  final double width;

  /// maxHeight.
  final double maxHeight;

  /// scaleAlignment.
  final Alignment scaleAlignment;

  /// opensAbove.
  final bool opensAbove;
}

/// Collision-aware menu placement (Compose popup position provider).
abstract final class M3EMenuPlacer {
  const M3EMenuPlacer._();

  /// compute.
  static M3EMenuPlacement compute({
    required Size screenSize,
    required Rect anchorRect,
    required M3EMenuTheme theme,
    required M3EMenuAnchorPosition position,
    required TextDirection textDirection,
    required int approximateItemCount,
    double? preferredWidth,
  }) {
    final edge = theme.screenEdgePadding;
    final width = _resolveWidth(
      preferredWidth: preferredWidth,
      anchorRect: anchorRect,
      theme: theme,
    );
    final double itemExtent = theme.entryHeight + theme.itemGap;
    final double lower = theme.entryHeight * 2;
    final double upper = theme.maxHeight;
    final double approxHeight =
        (approximateItemCount * itemExtent + theme.verticalPadding * 2)
            .clamp(lower < upper ? lower : 0, upper)
            .toDouble();
    final spaceBelow = screenSize.height - anchorRect.bottom - edge;
    final spaceAbove = anchorRect.top - edge;
    final isRtl = textDirection == TextDirection.rtl;
    final isSide =
        position == M3EMenuAnchorPosition.end ||
        position == M3EMenuAnchorPosition.start;
    final opensAbove = _opensAbove(
      position: position,
      isSide: isSide,
      approxHeight: approxHeight,
      spaceAbove: spaceAbove,
      spaceBelow: spaceBelow,
    );
    final alignEnd = _alignEnd(position, isRtl);
    final horizontal = _resolveHorizontal(
      screenSize: screenSize,
      anchorRect: anchorRect,
      theme: theme,
      position: position,
      isSide: isSide,
      isRtl: isRtl,
      alignEnd: alignEnd,
      width: width,
      edge: edge,
    );
    final vertical = _resolveVertical(
      screenSize: screenSize,
      anchorRect: anchorRect,
      theme: theme,
      isSide: isSide,
      opensAbove: opensAbove,
      spaceAbove: spaceAbove,
      spaceBelow: spaceBelow,
      edge: edge,
      approxHeight: approxHeight,
    );
    return M3EMenuPlacement(
      left: horizontal.left,
      top: vertical.top,
      bottom: vertical.bottom,
      width: width,
      maxHeight: vertical.maxHeight,
      scaleAlignment: _scaleAlignment(
        isSide: isSide,
        opensTowardStart: horizontal.opensTowardStart,
        isClampedToLeft: horizontal.left <= edge + 0.5,
        alignEnd: alignEnd,
        opensAbove: opensAbove,
      ),
      opensAbove: opensAbove,
    );
  }

  static double _resolveWidth({
    required double? preferredWidth,
    required Rect anchorRect,
    required M3EMenuTheme theme,
  }) {
    return (preferredWidth ??
            (anchorRect.width + 176.0).clamp(theme.minWidth, theme.maxWidth))
        .clamp(theme.minWidth, theme.maxWidth);
  }

  static bool _opensAbove({
    required M3EMenuAnchorPosition position,
    required bool isSide,
    required double approxHeight,
    required double spaceAbove,
    required double spaceBelow,
  }) {
    if (isSide) {
      return false;
    }
    final preferAbove =
        position == M3EMenuAnchorPosition.topStart ||
        position == M3EMenuAnchorPosition.topEnd;
    if (preferAbove) {
      return spaceAbove >= approxHeight || spaceAbove > spaceBelow;
    }
    return spaceBelow < approxHeight && spaceAbove > spaceBelow;
  }

  static bool _alignEnd(M3EMenuAnchorPosition position, bool isRtl) {
    return switch (position) {
      M3EMenuAnchorPosition.bottomEnd ||
      M3EMenuAnchorPosition.topEnd ||
      M3EMenuAnchorPosition.end => !isRtl,
      M3EMenuAnchorPosition.bottomStart ||
      M3EMenuAnchorPosition.topStart ||
      M3EMenuAnchorPosition.start => isRtl,
    };
  }

  static ({double left, bool opensTowardStart}) _resolveHorizontal({
    required Size screenSize,
    required Rect anchorRect,
    required M3EMenuTheme theme,
    required M3EMenuAnchorPosition position,
    required bool isSide,
    required bool isRtl,
    required bool alignEnd,
    required double width,
    required double edge,
  }) {
    late double left;
    var opensTowardStart = false;
    if (isSide) {
      final side = _sideLeft(
        anchorRect: anchorRect,
        theme: theme,
        position: position,
        isRtl: isRtl,
        width: width,
        edge: edge,
        screenWidth: screenSize.width,
      );
      left = side.left;
      opensTowardStart = side.opensTowardStart;
    } else if (alignEnd) {
      left = anchorRect.right - width;
    } else {
      left = anchorRect.left;
    }
    left = left.clamp(edge, screenSize.width - width - edge);
    return (left: left, opensTowardStart: opensTowardStart);
  }

  static ({double left, bool opensTowardStart}) _sideLeft({
    required Rect anchorRect,
    required M3EMenuTheme theme,
    required M3EMenuAnchorPosition position,
    required bool isRtl,
    required double width,
    required double edge,
    required double screenWidth,
  }) {
    final openEnd = position == M3EMenuAnchorPosition.end;
    final preferRight = (openEnd && !isRtl) || (!openEnd && isRtl);
    if (preferRight) {
      var left = anchorRect.right + theme.anchorOffset;
      var opensTowardStart = false;
      if (left + width > screenWidth - edge) {
        left = anchorRect.left - width - theme.anchorOffset;
        opensTowardStart = true;
      }
      return (left: left, opensTowardStart: opensTowardStart);
    }
    var left = anchorRect.left - width - theme.anchorOffset;
    var opensTowardStart = true;
    if (left < edge) {
      left = anchorRect.right + theme.anchorOffset;
      opensTowardStart = false;
    }
    return (left: left, opensTowardStart: opensTowardStart);
  }

  static ({double? top, double? bottom, double maxHeight}) _resolveVertical({
    required Size screenSize,
    required Rect anchorRect,
    required M3EMenuTheme theme,
    required bool isSide,
    required bool opensAbove,
    required double spaceAbove,
    required double spaceBelow,
    required double edge,
    required double approxHeight,
  }) {
    final maxHeight =
        (isSide
                ? (screenSize.height - edge - edge)
                : (opensAbove ? spaceAbove : spaceBelow))
            .clamp(0.0, theme.maxHeight);
    if (isSide) {
      // Top-align with the triggering row. Do not use [maxHeight] as the
      // intrinsic height — it is only a scroll viewport cap; using it pushed
      // cascading menus to the top of the screen.
      final heightBudget = approxHeight.clamp(0.0, maxHeight);
      var top = anchorRect.top;
      if (top + heightBudget > screenSize.height - edge) {
        top = screenSize.height - edge - heightBudget;
      }
      top = top.clamp(edge, screenSize.height - edge);
      return (top: top, bottom: null, maxHeight: maxHeight);
    }
    if (opensAbove) {
      // Pin the menu's bottom edge just above the anchor so short menus sit
      // flush (same gap as opening below), instead of top = anchor - maxHeight.
      return (
        top: null,
        bottom: screenSize.height - anchorRect.top + theme.anchorOffset,
        maxHeight: maxHeight,
      );
    }
    return (
      top: anchorRect.bottom + theme.anchorOffset,
      bottom: null,
      maxHeight: maxHeight,
    );
  }

  static Alignment _scaleAlignment({
    required bool isSide,
    required bool opensTowardStart,
    required bool isClampedToLeft,
    required bool alignEnd,
    required bool opensAbove,
  }) {
    if (isSide) {
      return Alignment(opensTowardStart ? 1.0 : -1.0, -1);
    }
    final h = isClampedToLeft ? -1.0 : (alignEnd ? 1.0 : -1.0);
    return Alignment(h, opensAbove ? 1.0 : -1.0);
  }

  /// Rough visible row count for height estimation.
  static int approximateItemCount(List<M3EMenuNode> nodes) {
    var count = 0;
    for (final node in nodes) {
      switch (node) {
        case M3EMenuGroup(:final children, :final label):
          if (label != null) {
            count += 1;
          }
          count += approximateItemCount(children);
        case M3EMenuDivider():
          count += 1;
        case M3EMenuEntry() ||
            M3EMenuSelectable() ||
            M3EMenuToggleable() ||
            M3EMenuSubmenu() ||
            M3EMenuWidget():
          count += 1;
      }
    }
    return count == 0 ? 1 : count;
  }
}
