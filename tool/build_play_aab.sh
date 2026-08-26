#!/bin/sh
set -eu

cd "$(dirname "$0")/.."
set -a
. ./.env
set +a

flutter build appbundle --flavor play --release --dart-define=DISTRIBUTION_CHANNEL=play

version=$(awk '/^version:/ {print $2; exit}' pubspec.yaml)
source_file="build/app/outputs/bundle/playRelease/app-play-release.aab"
target_file="build/app/outputs/bundle/release/Dextop-v${version}-play.aab"
cp "$source_file" "$target_file"
printf '%s\n' "$target_file"
