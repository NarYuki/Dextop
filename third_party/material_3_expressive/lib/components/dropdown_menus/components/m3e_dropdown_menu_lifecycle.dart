part of '../m3e_dropdown_menus.dart';

/// Lifecycle, async load, and controller sync for [_M3EDropdownMenuState].
extension _M3EDropdownMenuLifecycle<T> on _M3EDropdownMenuState<T> {
  void _initControllers() {
    // Theme may be unavailable; match [M3EDropdownMenuTheme] defaults.
    final open = widget.openMotion ?? M3EMotion.expressiveSpatialDefault;
    _expandCtrl = SingleMotionController(motion: open.toMotion(), vsync: this);
    _arrowCtrl = SingleMotionController(motion: open.toMotion(), vsync: this);
    _expandCtrl.addListener(_onExpandAnimationTick);

    if (widget.controller != null) {
      _controller = widget.controller!;
    } else {
      _controller = M3EDropdownController<T>();
      _ownController = true;
    }

    if (widget.items.isNotEmpty) {
      _controller.setItems(widget.items);
    }
    if (!_controller.initialized) {
      _controller.initialize();
    }

    _lastIsOpen = _controller.isOpen;
    _controller.addListener(_onControllerChanged);

    WidgetsBinding.instance.addPostFrameCallback((_) {
      _controller
        ..onSelectionChange = widget.onSelectionChanged
        ..onSearchChange = widget.onSearchChanged;
      _listenBackButton();
    });
  }

  void _initFocusAndLoading() {
    _focusNode = widget.focusNode ?? FocusNode();
    _loadingNotifier = ValueNotifier<bool>(false);
    _focusRingNotifier = ValueNotifier<bool>(false);
    _focusNode.addListener(_syncFieldFocusRing);
    FocusManager.instance.addHighlightModeListener(
      _onFocusHighlightModeChanged,
    );
    M3EFocusInteraction.instance.addListener(_syncFieldFocusRing);
    _listenable = Listenable.merge([
      _controller,
      _loadingNotifier,
      _focusRingNotifier,
    ]);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        _syncFieldFocusRing();
      }
    });
  }

  void _onFocusHighlightModeChanged(FocusHighlightMode mode) {
    _syncFieldFocusRing();
  }

  /// Keeps the field ring in sync with keyboard focus highlight state.
  void _syncFieldFocusRing() {
    if (!mounted) {
      return;
    }
    _focusRingNotifier.value = M3EFocusRing.shouldShow(_focusNode, context);
  }

  void _listenBackButton() {
    if (!widget.closeOnBackButton) {
      return;
    }
    WidgetsBinding.instance.addPostFrameCallback((_) {
      try {
        _registerBackButtonDispatcherCallback();
      } on Exception catch (e) {
        debugPrint('M3EDropdownMenu back-button error: $e');
      }
    });
  }

  void _registerBackButtonDispatcherCallback() {
    final rootBackDispatcher = Router.of(context).backButtonDispatcher;
    if (rootBackDispatcher != null) {
      rootBackDispatcher.createChildBackButtonDispatcher()
        ..addCallback(() {
          if (_controller.isOpen) {
            _close();
          }
          return Future.value(true);
        })
        ..takePriority();
    }
  }

  void _syncItemsFromWidget(M3EDropdownMenu<T> oldWidget) {
    if (widget.items != oldWidget.items && widget.future == null) {
      _controller.setItems(widget.items);
    }
  }

  void _syncControllerFromWidget(M3EDropdownMenu<T> oldWidget) {
    if (oldWidget.controller == widget.controller) {
      return;
    }

    _controller.removeListener(_onControllerChanged);
    if (_ownController) {
      _controller.dispose();
    }

    if (widget.controller != null) {
      _controller = widget.controller!;
      _ownController = false;
    } else {
      _controller = M3EDropdownController<T>();
      _ownController = true;
    }
    if (!_controller.initialized) {
      _controller.initialize();
    }
    if (widget.items.isNotEmpty) {
      _controller.setItems(widget.items);
    }
    _controller.addListener(_onControllerChanged);
    _controller
      ..onSelectionChange = widget.onSelectionChanged
      ..onSearchChange = widget.onSearchChanged;
  }

  void _syncFocusNodeFromWidget(M3EDropdownMenu<T> oldWidget) {
    if (oldWidget.focusNode == widget.focusNode) {
      return;
    }
    _focusNode.removeListener(_syncFieldFocusRing);
    if (oldWidget.focusNode == null) {
      _focusNode.dispose();
    }
    _focusNode = widget.focusNode ?? FocusNode();
    _focusNode.addListener(_syncFieldFocusRing);
    _syncFieldFocusRing();
  }

  void _syncMotionFromWidget(M3EDropdownMenu<T> oldWidget) {
    if (widget.openMotion == oldWidget.openMotion &&
        widget.closeMotion == oldWidget.closeMotion) {
      return;
    }
    _expandCtrl.motion = _resolvedOpenMotion.toMotion();
    _arrowCtrl.motion = _resolvedOpenMotion.toMotion();
  }

  Future<void> _loadAsync() async {
    _isLoading = true;
    _errorMessage = null;
    _loadingNotifier.value = true;
    try {
      final items = await widget.future!();
      if (mounted) {
        _controller.setItems(items);
      }
    } catch (e) {
      _errorMessage = e.toString();
    } finally {
      if (mounted) {
        _isLoading = false;
        _loadingNotifier.value = false;
      }
    }
  }

  void _onControllerChanged() {
    _formFieldKey.currentState?.didChange(_controller.selectedItems);

    if (_controller.isOpen && !_portalController.isShowing) {
      _open();
    } else if (!_controller.isOpen && _portalController.isShowing) {
      _close();
    }
  }

  void _onExpandAnimationTick() {
    if (!_controller.isOpen && _expandCtrl.value <= 0.01 && mounted) {
      if (_portalController.isShowing) {
        _portalController.hide();
      }
      _openingShowOnTop = null;
    }
  }

  Widget _buildFormField(FormFieldState<List<M3EDropdownItem<T>>?> formState) {
    return OverlayPortal(
      controller: _portalController,
      overlayChildBuilder: (_) => _buildOverlay(formState),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AnimatedSize(
            duration: const Duration(milliseconds: 200),
            curve: Curves.easeInOut,
            alignment: Alignment.topCenter,
            // Let the field's focus ring paint outside the animated bounds.
            clipBehavior: Clip.none,
            child: CompositedTransformTarget(
              link: _layerLink,
              child: ListenableBuilder(
                listenable: _listenable,
                builder: (_, _) {
                  return Semantics(
                    label: widget.fieldStyle.hintText ?? 'Dropdown field',
                    button: true,
                    enabled: widget.enabled,
                    child: _buildField(context, formState),
                  );
                },
              ),
            ),
          ),
          if (formState.hasError) _buildFormError(formState),
        ],
      ),
    );
  }

  Widget _buildFormError(FormFieldState<List<M3EDropdownItem<T>>?> formState) {
    return Padding(
      padding: const EdgeInsets.only(top: 6),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12),
        child: Builder(
          builder: (context) {
            final m3eTheme = M3ETheme.of(context);
            return Text(
              formState.errorText!,
              style:
                  (widget.fieldStyle.errorStyle ?? m3eTheme.typeScale.bodySmall)
                      .copyWith(
                        color:
                            widget.fieldStyle.errorStyle?.color ??
                            m3eTheme.colorScheme.error,
                      ),
            );
          },
        ),
      ),
    );
  }
}
