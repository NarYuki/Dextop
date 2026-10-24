import 'dart:math' as math;
import 'dart:ui' show lerpDouble;

import 'package:flutter/rendering.dart'
    show
        BoxHitTestResult,
        FloatingHeaderSnapConfiguration,
        RenderProxyBox,
        RenderSliver,
        RenderSliverSingleBoxAdapter,
        ScrollDirection,
        SliverGeometry,
        TransformLayer;

import 'package:material_3_expressive/components/toolbars/m3e_toolbars.dart'
    show M3EToolbar;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3EToolbar;
import 'package:material_ui/material_ui.dart';

import '../../foundations/foundations.dart';
import '../search/controllers/m3e_search_controller.dart';
import '../search/enums/m3e_search_enums.dart';
import '../search/m3e_search_anchor.dart';
import '../tooltips/m3e_tooltips.dart';
import 'components/m3e_app_bar_semantics.dart';
import 'controllers/m3e_app_bar_controller.dart';
import 'enums/m3e_app_bar_enums.dart';
import 'styles/m3e_app_bar_theme.dart';

export 'controllers/m3e_app_bar_controller.dart';
export 'enums/m3e_app_bar_enums.dart';
export 'styles/m3e_app_bar_theme.dart';

part 'components/m3e_app_bar_docked.dart';
part 'components/m3e_app_bar_docked_parts.dart';
part 'components/m3e_app_bar_bottom.dart';
part 'components/m3e_app_bar_sliver.dart';
part 'components/m3e_app_bar_sliver_actions.dart';
part 'components/m3e_app_bar_motion.dart';
part 'components/m3e_app_bar_scroll.dart';
part 'components/m3e_app_bar_body.dart';
part 'components/m3e_app_bar_title.dart';

const Duration _kAppBarTravel = Duration(milliseconds: 250);

/// Which app bar layout an [M3EAppBar] renders.
enum _M3EAppBarKind { top, bottom, sliver }

/// Dock edge for single-sided system inset padding (toolbar-compatible).
enum _M3EAppBarDockEdge { top, bottom }

/// A Material 3 Expressive app bar with `top`, `bottom`, and `sliver` variants.
class M3EAppBar extends StatelessWidget implements PreferredSizeWidget {
  /// A fixed top app bar for use in `Scaffold.appBar`.
  ///
  /// When [safeArea] is true, only the top [M3ESafeArea] inset is applied
  /// outside the content band (same model as [M3EToolbar.docked]).
  const M3EAppBar.top({
    super.key,
    this.leading,
    this.title,
    this.titleText,
    this.subtitle,
    this.subtitleText,
    this.actions,
    this.centerTitle = false,
    this.backgroundColor,
    this.foregroundColor,
    this.elevation,
    this.shapeFamily = M3EAppBarShapeFamily.square,
    this.density = M3EAppBarDensity.regular,
    this.toolbarHeight,
    this.automaticallyImplyLeading = false,
    this.safeArea = true,
    this.clipBehavior = Clip.none,
    this.semanticLabel,
    this.controller,
    this.variant = M3EAppBarVariant.small,
    this.hideOnScroll = false,
    this.hideMode = M3EAppBarHideMode.none,
  }) : _kind = _M3EAppBarKind.top,
       _dockEdge = _M3EAppBarDockEdge.top,
       floatingActionButton = null,
       pinned = true,
       floating = false,
       snap = false;

  /// A top app bar whose title is a read-only anchored [M3ESearchAnchor.bar].
  ///
  /// Tapping the bar opens the fullscreen (or docked) search view. Below the
  /// search bar theme max width, the bar fills the space between [leading] and
  /// [actions] while keeping the existing action gaps. Above that width it is
  /// capped and positioned with [centerTitle].
  factory M3EAppBar.search({
    Key? key,
    required M3ESearchController searchController,
    required M3ESearchSuggestionsBuilder suggestionsBuilder,
    Widget? leading,
    List<Widget>? actions,
    bool centerTitle = false,
    String? barHintText,
    Widget? barLeading,
    Iterable<Widget>? barTrailing,
    WidgetStateProperty<Color?>? barBackgroundColor,
    bool isFullScreen = true,
    Color? backgroundColor,
    Color? foregroundColor,
    double? elevation,
    M3EAppBarShapeFamily shapeFamily = M3EAppBarShapeFamily.square,
    M3EAppBarDensity density = M3EAppBarDensity.regular,
    double? toolbarHeight,
    bool automaticallyImplyLeading = false,
    bool safeArea = true,
    Clip clipBehavior = Clip.none,
    String? semanticLabel,
    M3EAppBarController? controller,
    M3EAppBarVariant variant = M3EAppBarVariant.small,
    bool hideOnScroll = false,
    M3EAppBarHideMode hideMode = M3EAppBarHideMode.none,
    bool wrapActions = false,
    ValueChanged<String>? onSubmitted,
    ValueChanged<String>? onChanged,
    VoidCallback? onClose,
    VoidCallback? onOpen,
    BoxConstraints? searchConstraints,
    AlignmentGeometry? barAlignment,
  }) {
    return M3EAppBar.top(
      key: key,
      leading: leading,
      actions: actions,
      centerTitle: centerTitle,
      backgroundColor: backgroundColor,
      foregroundColor: foregroundColor,
      elevation: elevation,
      shapeFamily: shapeFamily,
      density: density,
      toolbarHeight: toolbarHeight,
      automaticallyImplyLeading: automaticallyImplyLeading,
      safeArea: safeArea,
      clipBehavior: clipBehavior,
      semanticLabel: semanticLabel,
      controller: controller,
      variant: variant,
      hideOnScroll: hideOnScroll,
      hideMode: hideMode,
      title: _M3EAppBarSearchTitle(
        searchController: searchController,
        suggestionsBuilder: suggestionsBuilder,
        barHintText: barHintText,
        barLeading: barLeading,
        barTrailing: barTrailing,
        barBackgroundColor: barBackgroundColor,
        barAlignment:
            barAlignment ??
            (centerTitle ? Alignment.center : AlignmentDirectional.centerStart),
        wrapActions: wrapActions,
        isFullScreen: isFullScreen,
        onSubmitted: onSubmitted,
        onChanged: onChanged,
        onClose: onClose,
        onOpen: onOpen,
        searchConstraints: searchConstraints,
      ),
    );
  }

  /// A bottom app bar with actions and an optional floating action button.
  ///
  /// When [safeArea] is true, only the bottom [M3ESafeArea] inset is
  /// applied outside the content band.
  const M3EAppBar.bottom({
    super.key,
    this.actions = const <Widget>[],
    this.floatingActionButton,
    this.safeArea = true,
  }) : _kind = _M3EAppBarKind.bottom,
       _dockEdge = _M3EAppBarDockEdge.bottom,
       leading = null,
       title = null,
       titleText = null,
       subtitle = null,
       subtitleText = null,
       centerTitle = false,
       backgroundColor = null,
       foregroundColor = null,
       elevation = null,
       shapeFamily = M3EAppBarShapeFamily.square,
       density = M3EAppBarDensity.regular,
       toolbarHeight = null,
       automaticallyImplyLeading = true,
       clipBehavior = Clip.none,
       semanticLabel = null,
       pinned = true,
       floating = false,
       snap = false,
       hideOnScroll = false,
       hideMode = M3EAppBarHideMode.none,
       controller = null,
       variant = M3EAppBarVariant.medium;

  /// A scrolling sliver app bar for use in `CustomScrollView.slivers`.
  ///
  /// Flexible variants are pinned. They collapse to the small content height
  /// and stay there until the scroll offset returns to the top. [hideOnScroll]
  /// additionally moves that bar away while content scrolls forward and brings
  /// it back when the user scrolls back.
  const M3EAppBar.sliver({
    super.key,
    this.leading,
    this.title,
    this.titleText,
    this.subtitle,
    this.subtitleText,
    this.actions,
    this.centerTitle = false,
    this.backgroundColor,
    this.foregroundColor,
    this.pinned = true,
    this.floating = false,
    this.snap = false,
    this.hideOnScroll = false,
    this.hideMode = M3EAppBarHideMode.none,
    this.controller,
    this.shapeFamily = M3EAppBarShapeFamily.square,
    this.density = M3EAppBarDensity.regular,
    this.variant = M3EAppBarVariant.medium,
    this.semanticLabel,
  }) : _kind = _M3EAppBarKind.sliver,
       _dockEdge = _M3EAppBarDockEdge.top,
       elevation = null,
       toolbarHeight = null,
       automaticallyImplyLeading = true,
       safeArea = true,
       clipBehavior = Clip.none,
       floatingActionButton = null;

  final _M3EAppBarKind _kind;
  final _M3EAppBarDockEdge _dockEdge;

  /// leading.

  final Widget? leading;

  /// title.
  final Widget? title;

  /// titleText.
  final String? titleText;

  /// Optional subtitle widget. Shown under the headline.
  final Widget? subtitle;

  /// Optional subtitle string. Ignored when [subtitle] is set.
  final String? subtitleText;

  /// actions.
  final List<Widget>? actions;

  /// centerTitle.
  final bool centerTitle;

  /// backgroundColor.
  final Color? backgroundColor;

  /// foregroundColor.
  final Color? foregroundColor;

  /// elevation.
  final double? elevation;

  /// shapeFamily.
  final M3EAppBarShapeFamily shapeFamily;

  /// density.
  final M3EAppBarDensity density;

  /// toolbarHeight.
  final double? toolbarHeight;

  /// automaticallyImplyLeading.
  final bool automaticallyImplyLeading;

  /// When true, applies [M3ESafeArea] padding on the docked edge only
  /// (top for [M3EAppBar.top]/[M3EAppBar.search], bottom for [M3EAppBar.bottom]).
  final bool safeArea;

  /// clipBehavior.
  final Clip clipBehavior;

  /// semanticLabel.
  final String? semanticLabel;

  /// floatingActionButton.

  // Bottom-only.
  final Widget? floatingActionButton;

  /// pinned.

  // Sliver-only.
  final bool pinned;

  /// floating.
  final bool floating;

  /// snap.
  final bool snap;

  /// When true, the bar slides away as content scrolls forward and returns
  /// when the user scrolls back. Same as [hideMode] [M3EAppBarHideMode.entire].
  final bool hideOnScroll;

  /// Which parts slide away on scroll. [hideOnScroll] selects
  /// [M3EAppBarHideMode.entire] when this is [M3EAppBarHideMode.none].
  final M3EAppBarHideMode hideMode;

  /// Optional controller for expand, collapse, show, and hide.
  final M3EAppBarController? controller;

  /// variant.
  final M3EAppBarVariant variant;

  @override
  Size get preferredSize {
    if (_kind == _M3EAppBarKind.bottom) {
      return Size.fromHeight(M3EAppBarTheme.defaults.bottomHeight);
    }
    final double? live = _m3eAppBarHeightOf(this);
    if (live != null) {
      return Size.fromHeight(math.max(0, live));
    }
    return Size.fromHeight(_fallbackContentHeight());
  }

  /// Content-band height before the first scroll frame.
  double _fallbackContentHeight() {
    if (toolbarHeight != null) {
      return toolbarHeight!;
    }
    final M3EAppBarMetrics metrics = M3EAppBarTheme.defaults.metrics(density);
    if (variant == M3EAppBarVariant.small) {
      return metrics.smallHeight;
    }
    return metrics.expandedHeight(
      variant,
      hasSubtitle: subtitle != null || subtitleText != null,
    );
  }

  /// [hideOnScroll] means the whole bar when [hideMode] is [M3EAppBarHideMode.none].
  M3EAppBarHideMode get _effectiveHideMode {
    if (hideMode != M3EAppBarHideMode.none) {
      return hideMode;
    }
    if (hideOnScroll) {
      return M3EAppBarHideMode.entire;
    }
    return M3EAppBarHideMode.none;
  }

  @override
  Widget build(BuildContext context) {
    return M3EComponentTheme(
      builder: (context) => switch (_kind) {
        _M3EAppBarKind.top => _M3EDockedAppBar(bar: this),
        _M3EAppBarKind.bottom => _M3EBottomAppBar(bar: this),
        _M3EAppBarKind.sliver => _M3ESliverAppBar(bar: this),
      },
    );
  }

  /// System inset for the docked edge only — same recipe as docked toolbars.
  EdgeInsets _edgeSafeAreaInset(BuildContext context) {
    if (!safeArea) {
      return EdgeInsets.zero;
    }
    final EdgeInsets mq = M3ESafeArea.paddingOf(context);
    return EdgeInsets.only(
      top: _dockEdge == _M3EAppBarDockEdge.top ? mq.top : 0,
      bottom: _dockEdge == _M3EAppBarDockEdge.bottom ? mq.bottom : 0,
    );
  }
}

/// Live content-band height for [M3EAppBar.preferredSize].
final Expando<double> _m3eAppBarHeights = Expando<double>();

double? _m3eAppBarHeightOf(M3EAppBar bar) => _m3eAppBarHeights[bar];

void _m3eAppBarWriteHeight(M3EAppBar bar, double height) {
  _m3eAppBarHeights[bar] = height;
}

void _m3eAppBarClearHeight(M3EAppBar bar) {
  _m3eAppBarHeights[bar] = null;
}
