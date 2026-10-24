part of '../m3e_sliders.dart';

class _TrackIconsOverlay extends StatelessWidget {
  const _TrackIconsOverlay({
    required this.icons,
    required this.fraction,
    required this.trackKind,
    required this.axis,
    required this.child,
  });

  final M3ESliderTrackIcons icons;
  final double fraction;
  final M3ESliderTrackKind trackKind;
  final Axis axis;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: <Widget>[
        child,
        Positioned.fill(
          child: LayoutBuilder(
            builder: (BuildContext context, BoxConstraints c) {
              final vertical = axis == Axis.vertical;
              final double extent = vertical ? c.maxHeight : c.maxWidth;
              final double icon = icons.size;
              final placed = <Widget>[];

              void place(Widget? w, double primary, {required bool active}) {
                if (w == null) {
                  return;
                }
                // Hide when the segment cannot fit the icon.
                final double activeLen = fraction * extent;
                final double inactiveLen = (1 - fraction) * extent;
                if (active && activeLen < icon + 8) {
                  return;
                }
                if (!active && inactiveLen < icon + 8) {
                  return;
                }
                placed.add(
                  Positioned(
                    left: vertical ? (c.maxWidth - icon) / 2 : primary,
                    top: vertical ? primary : (c.maxHeight - icon) / 2,
                    width: icon,
                    height: icon,
                    child: IconTheme.merge(
                      data: IconThemeData(size: icon),
                      child: w,
                    ),
                  ),
                );
              }

              if (trackKind == M3ESliderTrackKind.centered) {
                final double mid = extent / 2;
                place(icons.activeStart, mid - icon - 4, active: true);
                place(icons.activeEnd, fraction * extent + 4, active: true);
              } else {
                place(icons.activeStart, 4, active: true);
                place(
                  icons.activeEnd,
                  fraction * extent - icon - 4,
                  active: true,
                );
                place(
                  icons.inactiveStart,
                  fraction * extent + 4,
                  active: false,
                );
                place(icons.inactiveEnd, extent - icon - 4, active: false);
              }

              return Stack(children: placed);
            },
          ),
        ),
      ],
    );
  }
}
