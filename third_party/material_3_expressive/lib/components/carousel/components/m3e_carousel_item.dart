import 'package:flutter/widgets.dart';

import '../models/m3e_carousel_scrim.dart';

/// One carousel item: image, optional text, and an optional transform.
///
/// [onTap] enables the item. A null [onTap] leaves it disabled.
/// When [applyDynamicTextContentTransform] is true, the text follows the
/// item's current size on every frame. Large slots show [title] and
/// [subtitle]. Medium slots hide [title]. Small slots show [prefixText] on
/// that same line.
class M3ECarouselItem {
  /// Creates a carousel item.
  const M3ECarouselItem({
    required this.image,
    this.title,
    this.subtitle,
    this.prefixText,
    this.applyDynamicTextContentTransform = true,
    this.onTap,
    this.transform,
    this.showScrim,
    this.aspectRatio = 1,
    this.semanticLabel,
  });

  /// Image or other visual, clipped to the item.
  final Widget image;

  /// Title, shown on large slots when dynamic text is on.
  final Widget? title;

  /// Supporting text under [title].
  final Widget? subtitle;

  /// Short text for a small slot. Shown on the subtitle line.
  final Widget? prefixText;

  /// Size-based text. The visible line follows the item's current size.
  ///
  /// When false, [title] and [subtitle] stay as given.
  final bool applyDynamicTextContentTransform;

  /// Item tap. Null disables the item.
  final VoidCallback? onTap;

  /// Full-screen destination opened after the tap pulse.
  final Widget? transform;

  /// Scrim between [image] and the text.
  final M3ECarouselScrim? showScrim;

  /// Width divided by height. Clamped by the carousel theme for multi-aspect.
  final double aspectRatio;

  /// Spoken name. Also used as the Show all row title.
  final String? semanticLabel;

  /// Whether the item accepts taps.
  bool get enabled => onTap != null;

  /// Text for the visible slot. Null when this item has no text to show.
  ///
  /// [slotMain] is the item's current main-axis size, so the line and its
  /// alignment update as the slot changes during a scroll.
  Widget? textOverlay({required double slotMain}) {
    final bool dynamicText = applyDynamicTextContentTransform;
    final bool small = slotMain <= 56;
    final bool large = slotMain >= 200;
    final Widget? shownTitle = !dynamicText || large ? title : null;
    final bool prefixLine = dynamicText && prefixText != null && small;
    final Widget? shownBody = prefixLine ? prefixText : subtitle;
    if (shownTitle == null && shownBody == null) {
      return null;
    }
    return Padding(
      padding: const EdgeInsets.all(12),
      child: Align(
        alignment: Alignment.bottomLeft,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            if (shownTitle != null) _oneLine(shownTitle),
            if (shownBody != null) _oneLine(shownBody),
          ],
        ),
      ),
    );
  }

  /// Keeps [child] on one line and scales it down when the slot is narrow.
  static Widget _oneLine(Widget child) {
    return FittedBox(
      fit: BoxFit.scaleDown,
      alignment: Alignment.centerLeft,
      child: child,
    );
  }
}
