/// Base type for overridable component themes registered on `M3EThemeData`.
abstract class M3EThemeExtension<T extends M3EThemeExtension<T>> {
  /// Creates a theme extension.
  const M3EThemeExtension();

  /// Returns a copy with non-null fields replaced.
  T copyWith();

  /// Linearly interpolates between this theme and [other].
  T lerp(T? other, double t);
}
