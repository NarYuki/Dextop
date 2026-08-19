#!/bin/sh
set -eu

cd "$(dirname "$0")/.."

flutter build apk --release --target-platform android-arm64

version=$(awk '/^version:/ {print $2; exit}' pubspec.yaml)
version_name=${version%%+*}
source_file="build/app/outputs/flutter-apk/app-release.apk"
target_file="build/app/outputs/flutter-apk/Dextop-v${version_name}.apk"

cp "$source_file" "$target_file"
printf '%s\n' "$target_file"
