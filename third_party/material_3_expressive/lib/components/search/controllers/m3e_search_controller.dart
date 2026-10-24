import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:material_3_expressive/components/search/m3e_search.dart'
    show M3ESearchAnchor;
import 'package:material_3_expressive/components/search/m3e_search_anchor.dart'
    show M3ESearchAnchor;
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3ESearchAnchor;

/// Handle implemented by [M3ESearchAnchor] for [M3ESearchController].
abstract class M3ESearchAnchorHandle {
  /// The viewIsOpen.
  bool get viewIsOpen;

  /// Whether focus on the anchor bar should avoid reopening the view.
  bool get suppressFocusOpen;

  /// openView.

  void openView();

  /// closeView.

  void closeView(String? selectedText);
}

/// Controls a search view opened by [M3ESearchAnchor].
class M3ESearchController extends TextEditingController {
  M3ESearchAnchorHandle? _anchor;

  /// Whether this controller is attached to a search anchor.
  bool get isAttached => _anchor != null;

  /// Whether the associated search view is currently open.
  bool get isOpen {
    assert(isAttached, 'M3ESearchController is not attached to an anchor.');
    return _anchor!.viewIsOpen;
  }

  /// Whether the anchor should ignore focus-driven [openView] calls.
  bool get suppressFocusOpen {
    assert(isAttached, 'M3ESearchController is not attached to an anchor.');
    return _anchor!.suppressFocusOpen;
  }

  /// Opens the search view associated with this controller.
  void openView() {
    assert(isAttached, 'M3ESearchController is not attached to an anchor.');
    _anchor!.openView();
  }

  /// Closes the search view, optionally setting [selectedText].
  void closeView(String? selectedText) {
    assert(isAttached, 'M3ESearchController is not attached to an anchor.');
    _anchor!.closeView(selectedText);
  }

  /// The currently attached search anchor handle, if any.
  M3ESearchAnchorHandle? get anchor => _anchor;

  /// Attaches this controller to a search anchor handle.
  set anchor(M3ESearchAnchorHandle handle) {
    _anchor = handle;
  }

  /// Detaches this controller from [anchor] when it is the current handle.
  void detach(M3ESearchAnchorHandle anchor) {
    if (_anchor == anchor) {
      _anchor = null;
    }
  }
}

/// Signature for building the search anchor child.
typedef M3ESearchAnchorChildBuilder = Widget Function(
  BuildContext context,
  M3ESearchController controller,
);

/// Signature for building search suggestions from the current query.
typedef M3ESearchSuggestionsBuilder = FutureOr<Iterable<Widget>> Function(
  BuildContext context,
  M3ESearchController controller,
);

/// Signature for laying out suggestion widgets in the search view.
typedef M3ESearchViewBuilder = Widget Function(Iterable<Widget> suggestions);

/// Signature for the screen reader text sent when results change.
typedef M3ESearchAnnouncementBuilder = String Function(int count);
