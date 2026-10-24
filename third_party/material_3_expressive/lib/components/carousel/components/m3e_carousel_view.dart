import 'dart:math' as math;

import 'package:collection/collection.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/scheduler.dart';
import 'package:material_ui/material_ui.dart';

import '../../../foundations/foundations.dart';
import '../models/m3e_carousel_change_details.dart';
import '../styles/m3e_carousel_theme.dart';

part 'm3e_sliver_fixed_extent_carousel.dart';
part 'm3e_sliver_weighted_carousel.dart';
part 'm3e_sliver_weighted_carousel_layout.dart';
part 'm3e_carousel_scroll_physics.dart';
part 'm3e_carousel_metrics.dart';
part 'm3e_carousel_position.dart';
part 'm3e_carousel_controller.dart';

/// A Material Design carousel widget.
///
/// The [M3ECarouselView] presents a scrollable list of items, each of which can
/// dynamically change size based on the chosen layout.
class M3ECarouselView extends StatefulWidget {
  /// Creates a Material Design carousel.
  const M3ECarouselView({
    super.key,
    this.padding,
    this.backgroundColor,
    this.elevation,
    this.shape,
    this.itemClipBehavior,
    this.overlayColor,
    this.itemSnapping = false,
    this.shrinkExtent = 0.0,
    this.scaleItems = true,
    this.controller,
    this.scrollDirection = Axis.horizontal,
    this.reverse = false,
    this.onTap,
    this.haptic = M3EHapticFeedback.none,
    this.enableSplash = true,
    this.infinite = false,
    this.physics,
    required double this.itemExtent,
    required this.children,
    this.onIndexChanged,
    this.onChange,
    this.leadingInset = 0,
    this.restingExtents,
  }) : consumeMaxWeight = true,
       flexWeights = null,
       itemBuilder = null,
       itemCount = null;

  /// Creates a scrollable list where the size of each child widget is dynamically
  /// determined by the provided [flexWeights].
  const M3ECarouselView.weighted({
    super.key,
    this.padding,
    this.backgroundColor,
    this.elevation,
    this.shape,
    this.itemClipBehavior,
    this.overlayColor,
    this.itemSnapping = false,
    this.shrinkExtent = 0.0,
    this.scaleItems = true,
    this.controller,
    this.scrollDirection = Axis.horizontal,
    this.reverse = false,
    this.consumeMaxWeight = true,
    this.onTap,
    this.haptic = M3EHapticFeedback.none,
    this.enableSplash = true,
    this.infinite = false,
    this.physics,
    required List<int> this.flexWeights,
    required this.children,
    this.onIndexChanged,
    this.onChange,
    this.leadingInset = 0,
  }) : itemExtent = null,
       restingExtents = null,
       itemBuilder = null,
       itemCount = null;

  /// Creates a scrollable carousel with fixed-sized items created on demand.
  const M3ECarouselView.builder({
    super.key,
    this.padding,
    this.backgroundColor,
    this.elevation,
    this.shape,
    this.itemClipBehavior,
    this.overlayColor,
    this.itemSnapping = false,
    this.shrinkExtent = 0.0,
    this.scaleItems = true,
    this.controller,
    this.scrollDirection = Axis.horizontal,
    this.reverse = false,
    this.onTap,
    this.haptic = M3EHapticFeedback.none,
    this.enableSplash = true,
    required double this.itemExtent,
    required this.itemBuilder,
    this.itemCount,
    this.onIndexChanged,
    this.onChange,
    this.infinite = false,
    this.physics,
    this.leadingInset = 0,
  }) : consumeMaxWeight = true,
       flexWeights = null,
       restingExtents = null,
       children = const <Widget>[];

  /// Creates a scrollable carousel with weighted items created on demand.
  const M3ECarouselView.weightedBuilder({
    super.key,
    this.padding,
    this.backgroundColor,
    this.elevation,
    this.shape,
    this.itemClipBehavior,
    this.overlayColor,
    this.itemSnapping = false,
    this.shrinkExtent = 0.0,
    this.scaleItems = true,
    this.controller,
    this.scrollDirection = Axis.horizontal,
    this.reverse = false,
    this.consumeMaxWeight = true,
    this.onTap,
    this.haptic = M3EHapticFeedback.none,
    this.enableSplash = true,
    required List<int> this.flexWeights,
    required this.itemBuilder,
    this.itemCount,
    this.onIndexChanged,
    this.onChange,
    this.infinite = false,
    this.physics,
    this.leadingInset = 0,
  }) : itemExtent = null,
       restingExtents = null,
       children = const <Widget>[];

  /// Empty space before the first item. It scrolls away and does not
  /// change item sizes.
  final double leadingInset;

  /// The amount of space to surround each carousel item with.
  ///
  /// Defaults to [EdgeInsets.all] of 4 pixels.
  final EdgeInsets? padding;

  /// The background color for each carousel item.
  ///
  /// Defaults to [ColorScheme.surface].
  final Color? backgroundColor;

  /// The z-coordinate of each carousel item.
  ///
  /// Defaults to 0.0.
  final double? elevation;

  /// The shape of each carousel item's [Material].
  ///
  /// Defines each item's [Material.shape].
  ///
  /// Defaults to a [RoundedRectangleBorder] with a circular corner radius
  /// of 28.0.
  final ShapeBorder? shape;

  /// The clip behavior for each carousel item.
  ///
  /// The item content will be clipped (or not) according to this option.
  /// Refer to the [Clip] enum for more details on the different clip options.
  ///
  /// Defaults to [Clip.antiAlias].
  final Clip? itemClipBehavior;

  /// The highlight color to indicate the carousel items are in pressed, hovered
  /// or focused states.
  ///
  /// The default values are:
  ///   * [WidgetState.pressed] - [ColorScheme.onSurface] with an opacity of 0.1
  ///   * [WidgetState.hovered] - [ColorScheme.onSurface] with an opacity of 0.08
  ///   * [WidgetState.focused] - [ColorScheme.onSurface] with an opacity of 0.1
  final WidgetStateProperty<Color?>? overlayColor;

  /// The minimum allowable extent (size) in the main axis for carousel items
  /// during scrolling transitions.
  ///
  /// As the carousel scrolls, the first visible item is pinned and gradually
  /// shrinks until it reaches this minimum extent before scrolling off-screen.
  /// Similarly, the last visible item enters the viewport at this minimum size
  /// and expands to its full [itemExtent].
  ///
  /// In cases where the remaining viewport space for the last visible item is
  /// larger than the defined [shrinkExtent], the [shrinkExtent] is dynamically
  /// adjusted to match this remaining space, ensuring a smooth size transition.
  ///
  /// Defaults to 0.0. Setting to 0.0 allows items to shrink/expand completely,
  /// transitioning between 0.0 and the full item size. In cases where the
  /// remaining viewport space for the last visible item is larger than the
  /// defined [shrinkExtent], the [shrinkExtent] is dynamically adjusted to match
  /// this remaining space, ensuring a smooth size transition.
  final double shrinkExtent;

  /// Whether fixed-extent items shrink while scrolling.
  ///
  /// When false, every item keeps [itemExtent] and slides.
  final bool scaleItems;

  /// Whether the carousel should keep scrolling to the next/previous items to
  /// maintain the original layout.
  ///
  /// Defaults to false.
  final bool itemSnapping;

  /// An object that can be used to control the position to which this scroll
  /// view is scrolled.
  final M3ECarouselController? controller;

  /// The [Axis] along which the scroll view's offset increases with each item.
  ///
  /// Defaults to [Axis.horizontal].
  final Axis scrollDirection;

  /// Whether the carousel list scrolls in the reading direction.
  ///
  /// Defaults to false.
  final bool reverse;

  /// Whether the collapsed items are allowed to expand to the max size.
  ///
  /// Defaults to true.
  final bool consumeMaxWeight;

  /// Called when one of the [children] is tapped.
  final ValueChanged<int>? onTap;

  /// Haptic intensity on item tap. Defaults to [M3EHapticFeedback.none].
  final M3EHapticFeedback haptic;

  /// Determines whether an [InkWell] will cover each Carousel item.
  ///
  /// Defaults to true.
  final bool enableSplash;

  /// The extent the children are forced to have in the main axis.
  ///
  /// This is required for [M3ECarouselView]. In [M3ECarouselView.weighted], this
  /// is null.
  final double? itemExtent;

  /// Resting size of each item. Items shrink from these sizes while scrolling.
  final List<double>? restingExtents;

  /// The scrollPhysics to apply to the carousel layout.
  ///
  /// Defaults to [NeverScrollableScrollPhysics] to allow scroll control only
  /// by horizontal swipe gesture.
  final ScrollPhysics? physics;

  /// The weights that each visible child should occupy in the viewport.
  ///
  /// This is a required property in [M3ECarouselView.weighted]. This is null
  /// for default [M3ECarouselView]. The integers must be greater than 0.
  final List<int>? flexWeights;

  /// The child widgets for the carousel.
  final List<Widget> children;

  /// A callback invoked when the leading item changes.
  final ValueChanged<int>? onIndexChanged;

  /// Called when the leading or focal item index changes.
  final ValueChanged<M3ECarouselChangeDetails>? onChange;

  /// Called to build carousel item on demand.
  final NullableIndexedWidgetBuilder? itemBuilder;

  /// The number of items in the carousel.
  final int? itemCount;

  /// Whether the carousel should loop infinitely.
  ///
  /// Defaults to false.
  final bool infinite;

  @override
  State<M3ECarouselView> createState() => _CarouselViewState();
}

class _CarouselViewState extends State<M3ECarouselView> {
  double? _itemExtent;

  List<int>? get _flexWeights => widget.flexWeights;

  bool get _consumeMaxWeight => widget.consumeMaxWeight;
  M3ECarouselController? _internalController;

  M3ECarouselController get _controller =>
      widget.controller ?? _internalController!;
  late int _lastReportedLeadingItem;
  late int _lastReportedFocalItem;

  /// Cached from [didChangeDependencies] so item builds do not each register
  /// an [M3ETheme] dependency during sliver child updates.
  late M3ECarouselTheme _carouselTheme;
  late M3EColorScheme _scheme;

  @override
  void initState() {
    super.initState();
    _itemExtent = widget.itemExtent;
    if (widget.controller == null) {
      _internalController = M3ECarouselController();
    }
    _lastReportedLeadingItem = _getInitialLeadingItem();
    _lastReportedFocalItem = _focalIndexForLeading(_lastReportedLeadingItem);
    _controller
      .._carouselState = this
      ..addListener(_handleScroll);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final M3EThemeData theme = M3ETheme.of(context);
    _carouselTheme = theme.carouselTheme;
    _scheme = theme.colorScheme;
  }

  @override
  void didUpdateWidget(covariant M3ECarouselView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.controller != oldWidget.controller) {
      oldWidget.controller?._detach(this);
      if (widget.controller != null) {
        _internalController?._detach(this);
        _internalController = null;
        widget.controller?._carouselState = this;
      } else {
        // widget.controller == null && oldWidget.controller != null
        assert(
          _internalController == null,
          'internal controller should be null when replacing with external',
        );
        _internalController = M3ECarouselController();
        _controller._carouselState = this;
      }
    }
    if (widget.flexWeights != oldWidget.flexWeights) {
      (_controller.position as _CarouselPosition).flexWeights = _flexWeights;
    }
    if (widget.itemExtent != oldWidget.itemExtent ||
        widget.restingExtents != oldWidget.restingExtents) {
      _itemExtent = widget.itemExtent;
      (_controller.position as _CarouselPosition)
        ..restingExtents = widget.restingExtents
        ..itemExtent = _itemExtent;
    }
    if (widget.consumeMaxWeight != oldWidget.consumeMaxWeight) {
      (_controller.position as _CarouselPosition).consumeMaxWeight =
          _consumeMaxWeight;
    }
  }

  @override
  void dispose() {
    _controller
      ..removeListener(_handleScroll)
      .._detach(this);
    _internalController?.dispose();
    super.dispose();
  }

  void _handleScroll() {
    if (widget.onIndexChanged == null && widget.onChange == null) {
      return;
    }

    final position = _controller.position as _CarouselPosition;
    // Round continuous scroll item (not truncate) so forward and reverse
    // swipes report the new leading/focal at the midpoint — truncation via
    // [leadingItem] delays next-item updates until nearly fully scrolled.
    final currentLeadingIndex = _reportedLeadingIndex(position);
    final currentFocalIndex = _focalIndexForLeading(currentLeadingIndex);
    final itemCount = widget.itemCount ?? widget.children.length;

    final leadingChanged = currentLeadingIndex != _lastReportedLeadingItem;
    final focalChanged = currentFocalIndex != _lastReportedFocalItem;
    if (!leadingChanged && !focalChanged) {
      return;
    }

    _lastReportedLeadingItem = currentLeadingIndex;
    _lastReportedFocalItem = currentFocalIndex;

    final details = M3ECarouselChangeDetails(
      leadingIndex: currentLeadingIndex,
      focalIndex: currentFocalIndex,
      itemCount: itemCount,
    );
    void notify() {
      if (!mounted) {
        return;
      }
      if (leadingChanged) {
        widget.onIndexChanged?.call(currentLeadingIndex);
      }
      widget.onChange?.call(details);
    }

    final SchedulerPhase phase = SchedulerBinding.instance.schedulerPhase;
    if (phase == SchedulerPhase.persistentCallbacks ||
        phase == SchedulerPhase.midFrameMicrotasks) {
      WidgetsBinding.instance.addPostFrameCallback((_) => notify());
      return;
    }
    notify();
  }

  /// Leading index for change callbacks; mirrors [ _CarouselPosition.leadingItem]
  /// but uses [num.round] so both swipe directions cross at the half-item mark.
  int _reportedLeadingIndex(_CarouselPosition position) {
    if (!position.hasPixels ||
        !position.hasViewportDimension ||
        position.viewportDimension <= 0) {
      return _lastReportedLeadingItem;
    }
    var leading = position
        .getItemFromPixels(position.pixels, position.viewportDimension)
        .round();
    if (position.consumeMaxWeight && position.flexWeights != null) {
      leading = math.max(
        leading - position.flexWeights!.indexOf(position.flexWeights!.max),
        0,
      );
    }
    if (position.infinite &&
        position.itemCount != null &&
        position.itemCount! > 0) {
      leading = leading % position.itemCount!;
    }
    return leading;
  }

  int _focalIndexForLeading(int leadingIndex) {
    final List<int>? weights = widget.flexWeights;
    if (weights == null || weights.isEmpty) {
      return leadingIndex;
    }
    final int maxWeight = weights.max;
    final int firstMaxWeightIndex = weights.indexOf(maxWeight);
    final int itemCount = widget.itemCount ?? widget.children.length;
    if (itemCount <= 0) {
      return leadingIndex;
    }
    return (leadingIndex + firstMaxWeightIndex).clamp(0, itemCount - 1);
  }

  // For weighted carousel, the initialItem means the index of the item to occupy the first maximum weight
  // in flexWeights. To get the initial leading item, it should be initialItem - index of the first max weight in flexWeights.
  // So it might be negative when initialItem value is small but the first max weight index is large. In that case,
  // the initial leading item should be 0.
  int _getInitialLeadingItem() {
    if (widget.flexWeights != null) {
      final int maxWeight = widget.flexWeights!.max;
      final int firstMaxWeightIndex = widget.flexWeights!.indexOf(maxWeight);
      return math.max(_controller.initialItem - firstMaxWeightIndex, 0);
    }
    return _controller.initialItem;
  }

  Widget _buildCarouselItem(int index) {
    // For infinite scrolling, wrap the index to the actual children range.
    var itemIndex = index;
    if (widget.infinite && widget.children.isNotEmpty) {
      itemIndex = index % widget.children.length;
    }
    // Resolve tokens once per State lifetime rebuild via [build], not here —
    // calling M3ETheme.of inside the item builder during a theme swap can
    // rebuild the weighted sliver mid-layout and drop the leading child.
    final M3ECarouselTheme carouselTheme = _carouselTheme;
    final M3EColorScheme scheme = _scheme;
    final EdgeInsets effectivePadding =
        widget.padding ?? carouselTheme.itemPadding as EdgeInsets;
    final Color effectiveBackgroundColor =
        widget.backgroundColor ?? carouselTheme.backgroundColor(scheme);
    final double effectiveElevation =
        widget.elevation ?? carouselTheme.elevation;
    final ShapeBorder effectiveShape = widget.shape ?? carouselTheme.shape;
    final Clip effectiveItemClipBehavior =
        widget.itemClipBehavior ?? carouselTheme.itemClipBehavior;
    final WidgetStateProperty<Color?> effectiveOverlayColor =
        widget.overlayColor ?? carouselTheme.overlayColor(scheme);

    Widget contents = widget.children[itemIndex];

    if (widget.enableSplash) {
      contents = Stack(
        fit: StackFit.expand,
        children: <Widget>[
          contents,
          Material(
            color: Colors.transparent,
            child: InkWell(
              onTap: widget.onTap == null
                  ? null
                  : () {
                      M3EHaptics.trigger(widget.haptic);
                      widget.onTap!(itemIndex);
                    },
              overlayColor: effectiveOverlayColor,
            ),
          ),
        ],
      );
    } else if (widget.onTap != null) {
      contents = GestureDetector(
        onTap: () {
          M3EHaptics.trigger(widget.haptic);
          widget.onTap!(index);
        },
        child: contents,
      );
    }

    return Padding(
      padding: effectivePadding,
      child: Material(
        clipBehavior: effectiveItemClipBehavior,
        color: effectiveBackgroundColor,
        elevation: effectiveElevation,
        shape: effectiveShape,
        child: contents,
      ),
    );
  }

  Widget _buildSliverCarousel(BuildContext context) {
    // Determine the child count and builder based on whether we're using lazy loading
    final int? childCount = widget.infinite
        ? null
        : widget.itemBuilder != null
        ? widget.itemCount
        : widget.children.length;

    NullableIndexedWidgetBuilder effectiveBuilder;
    if (widget.itemBuilder != null) {
      if (widget.infinite &&
          widget.itemCount != null &&
          widget.itemCount! > 0) {
        final int itemCount = widget.itemCount!;
        effectiveBuilder = (BuildContext context, int index) {
          return widget.itemBuilder!(context, index % itemCount);
        };
      } else {
        effectiveBuilder = widget.itemBuilder!;
      }
    } else {
      effectiveBuilder = (BuildContext context, int index) =>
          _buildCarouselItem(index);
    }

    final SliverChildDelegate delegate = SliverChildBuilderDelegate(
      effectiveBuilder,
      childCount: childCount,
    );

    if (_itemExtent != null) {
      return _SliverFixedExtentCarousel(
        itemExtent: _itemExtent!,
        minExtent: widget.shrinkExtent,
        scaleItems: widget.scaleItems,
        infinite: widget.infinite,
        restingExtents: widget.restingExtents,
        delegate: delegate,
      );
    }

    assert(
      _flexWeights != null && _flexWeights!.every((int weight) => weight > 0),
      'flexWeights is null or it contains non-positive integers',
    );
    return _SliverWeightedCarousel(
      consumeMaxWeight: _consumeMaxWeight,
      shrinkExtent: widget.shrinkExtent,
      weights: _flexWeights!,
      infinite: widget.infinite,
      delegate: delegate,
    );
  }

  /// Start padding so the first item rests after [M3ECarouselView.leadingInset].
  EdgeInsetsGeometry? _leadingScrollPadding() {
    if (widget.leadingInset <= 0) {
      return null;
    }
    final vertical = widget.scrollDirection == Axis.vertical;
    if (vertical) {
      return widget.reverse
          ? EdgeInsets.only(bottom: widget.leadingInset)
          : EdgeInsets.only(top: widget.leadingInset);
    }
    final startIsRight =
        (Directionality.of(context) == TextDirection.rtl) != widget.reverse;
    return startIsRight
        ? EdgeInsets.only(right: widget.leadingInset)
        : EdgeInsets.only(left: widget.leadingInset);
  }

  @override
  Widget build(BuildContext context) {
    final ScrollPhysics physics = widget.itemSnapping
        ? const M3ECarouselScrollPhysics()
        : ScrollConfiguration.of(context).getScrollPhysics(context);

    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        final double mainAxisExtent = switch (widget.scrollDirection) {
          Axis.horizontal => constraints.maxWidth,
          Axis.vertical => constraints.maxHeight,
        };

        final double leading = widget.scrollDirection == Axis.horizontal
            ? widget.leadingInset
            : 0;
        _itemExtent = widget.itemExtent == null
            ? null
            : clampDouble(
                widget.itemExtent!,
                0,
                math.max(0, mainAxisExtent - leading),
              );
        final Widget sliver = _buildSliverCarousel(context);
        final EdgeInsetsGeometry? leadingPadding = _leadingScrollPadding();
        return CustomScrollView(
          scrollDirection: widget.scrollDirection,
          reverse: widget.reverse,
          controller: _controller,
          physics: widget.physics ?? physics,
          clipBehavior: Clip.none,
          scrollCacheExtent: const ScrollCacheExtent.viewport(0),
          slivers: <Widget>[
            if (leadingPadding == null)
              sliver
            else
              SliverPadding(padding: leadingPadding, sliver: sliver),
          ],
        );
      },
    );
  }
}
