part of 'm3e_dismissible_card_controller.dart';

/// Collapsing (post-dismiss fly-out) card rendering for
/// [M3EDismissibleCardBuildMixin].
extension M3EDismissibleCardCollapseRender<T extends StatefulWidget>
    on M3EDismissibleCardBuildMixin<T> {
  Widget _buildCollapsingCard(BuildContext context, int slotIndex) {
    final slot = _slots[slotIndex];
    final ctrl = slot.collapseCtrl!;
    final totalH = slot.capturedHeight + style.gap;
    final s = style;
    final swipingRight = slot.dismissedDirection == DismissDirection.startToEnd;
    final bgRadius = swipingRight
        ? s.backgroundBorderRadius
        : (s.secondaryBackgroundBorderRadius ?? s.backgroundBorderRadius);
    final cardRadius = s.selectedBorderRadius ?? s.outerRadius;

    return IgnorePointer(
      child: AnimatedBuilder(
        animation: ctrl,
        child: slot.frozenChild == null
            ? null
            : Stack(
                children: [
                  if (slot.dismissedDirection != null)
                    _buildCollapsingBackground(slot, s, bgRadius),
                  _buildCollapsingFlyingCard(context, slot, s, cardRadius),
                ],
              ),
        builder: (ctx, child) {
          final h = (totalH * (1.0 - ctrl.value)).clamp(0.0, totalH);
          return SizedBox(height: h, width: double.infinity, child: child);
        },
      ),
    );
  }

  Widget _buildCollapsingBackground(
    M3EDismissibleSlot slot,
    M3EDismissibleListStyle s,
    double bgRadius,
  ) {
    return ValueListenableBuilder<double>(
      valueListenable: slot.flyNotifier,
      builder: (_, flyOff, child) {
        final progress = flyOff.abs();
        final actionWidth = (progress - s.actionGap).clamp(0.0, progress);
        final swipingRight =
            slot.dismissedDirection == DismissDirection.startToEnd;
        if (actionWidth <= 0) {
          return const SizedBox.shrink();
        }
        final Widget? bg = swipingRight
            ? s.background
            : (s.secondaryBackground ?? s.background);
        if (bg == null) {
          return const SizedBox.shrink();
        }
        final double edgePad = s.actionEdgePadding;
        final double pillHeight = math.max(
          s.actionMinHeight,
          (slot.capturedHeight > 0 ? slot.capturedHeight : 56) -
              s.actionVerticalInset,
        );
        final double pillWidth = math.max(0, actionWidth - 2 * edgePad);
        return Positioned.fill(
          bottom: s.gap,
          child: Align(
            alignment: swipingRight
                ? Alignment.centerLeft
                : Alignment.centerRight,
            child: Padding(
              padding: EdgeInsets.symmetric(horizontal: edgePad),
              child: SizedBox(
                width: pillWidth,
                height: pillHeight,
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(pillHeight / 2),
                  child: bg,
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildCollapsingFlyingCard(
    BuildContext context,
    M3EDismissibleSlot slot,
    M3EDismissibleListStyle s,
    double cardRadius,
  ) {
    return Padding(
      padding: EdgeInsets.only(bottom: s.gap),
      child: OverflowBox(
        alignment: Alignment.topLeft,
        minWidth: slot.capturedWidth > 0 ? slot.capturedWidth : 0,
        maxWidth: slot.capturedWidth > 0
            ? slot.capturedWidth
            : MediaQuery.sizeOf(context).width,
        minHeight: 0,
        maxHeight: slot.capturedHeight,
        child: IgnorePointer(
          child: ValueListenableBuilder<double>(
            valueListenable: slot.flyNotifier,
            builder: (_, flyOff, child) =>
                Transform.translate(offset: Offset(flyOff, 0), child: child),
            child: Padding(
              padding: EdgeInsets.zero,
              child: M3ECard(
                variant: M3ECardVariant.filled,
                borderRadius: BorderRadius.circular(cardRadius),
                color:
                    s.color ??
                    M3ETheme.of(context).colorScheme.surfaceContainerHighest,
                border: s.border,
                padding: s.padding ?? const EdgeInsets.all(16),
                width: double.infinity,
                child: M3EListItemScope(child: slot.frozenChild!),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
