import 'package:firebase_analytics/firebase_analytics.dart';
import 'package:firebase_core/firebase_core.dart';

abstract final class AppAnalytics {
  static FirebaseAnalytics? _analytics;

  static Future<void> initialize() async {
    try {
      await Firebase.initializeApp();
      _analytics = FirebaseAnalytics.instance;
      await _analytics?.setAnalyticsCollectionEnabled(true);
    } catch (_) {}
  }

  static Future<void> screen(String name) async {
    await _analytics?.logEvent(
      name: 'screen_view',
      parameters: {
        'firebase_screen': name,
        'firebase_screen_class': 'DextopScreen',
      },
    );
  }

  static Future<void> event(String name, [Map<String, Object>? parameters]) {
    // Firebase accepts only strings and numbers. Keep call sites expressive while
    // normalizing flags here so analytics can never interrupt a user action.
    final normalized = parameters?.map((key, value) {
      final Object safeValue = switch (value) {
        bool flag => flag ? 1 : 0,
        String() || num() => value,
        _ => value.toString(),
      };
      return MapEntry(key, safeValue);
    });
    return _analytics?.logEvent(name: name, parameters: normalized) ??
        Future.value();
  }
}
