part of '../m3e_toolbars.dart';

extension _M3EToolbarExitOffset on _M3EToolbarState {
  Offset _exitOffset(BuildContext context, double offset) {
    switch (_exitDirection) {
      case M3EToolbarExitDirection.top:
        return Offset(0, offset);
      case M3EToolbarExitDirection.bottom:
        return Offset(0, -offset);
      case M3EToolbarExitDirection.start:
        final isRtl = Directionality.of(context) == TextDirection.rtl;
        return Offset(isRtl ? -offset : offset, 0);
      case M3EToolbarExitDirection.end:
        final isRtl = Directionality.of(context) == TextDirection.rtl;
        return Offset(isRtl ? offset : -offset, 0);
    }
  }
}
