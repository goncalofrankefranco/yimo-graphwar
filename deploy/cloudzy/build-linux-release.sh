#!/usr/bin/env bash
set -Eeuo pipefail
umask 027

source_dir="${YIMO_SOURCE_DIR:-/opt/yimo-source}"
java_home="${YIMO_JAVA_HOME:-/opt/yimo/java8}"
output_dir=''
while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir) output_dir="$2"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

[[ -d "$source_dir/src" ]] || { echo "Missing Java source tree: $source_dir/src" >&2; exit 1; }
[[ -x "$java_home/bin/javac" && -x "$java_home/bin/jar" ]] || {
  echo "Java 8 JDK is required at $java_home." >&2
  exit 1
}
if [[ -z "$output_dir" ]]; then output_dir="$(mktemp -d /tmp/yimo-built-release.XXXXXX)"; else mkdir -p "$output_dir"; fi
classes="$(mktemp -d /tmp/yimo-java-classes.XXXXXX)"
sources="$(mktemp /tmp/yimo-java-sources.XXXXXX)"
cleanup() { rm -rf "$classes" "$sources"; }
trap cleanup EXIT

find "$source_dir/src" -type f -name '*.java' -print > "$sources"
"$java_home/bin/javac" -encoding UTF-8 -source 8 -target 8 -d "$classes" "@$sources"
"$java_home/bin/jar" cfe "$output_dir/YIMO-Graphwar-2.0.0.jar" Graphwar.Graphwar -C "$classes" . -C "$source_dir" rsc
"$java_home/bin/jar" cfe "$output_dir/globalServer.jar" GlobalServer.GlobalServer -C "$classes" . -C "$source_dir" rsc
"$java_home/bin/jar" cfe "$output_dir/roomServer.jar" RoomServer.RoomServer -C "$classes" . -C "$source_dir" rsc
cp -a "$source_dir/rsc" "$output_dir/rsc"
cp -a "$source_dir/tournament" "$output_dir/tournament"
for legal in COPYING LICENSE README.md NOTICE.md THIRD-PARTY-LICENSES.md; do
  [[ -e "$source_dir/$legal" ]] && cp -a "$source_dir/$legal" "$output_dir/$legal"
done
revision="$(git -C "$source_dir" rev-parse HEAD 2>/dev/null || printf 'local-build')"
printf 'YIMO Graphwar Linux server release\nBuild ID: YIMO-Graphwar-2.0.0\nProtocol version: 2\nSource revision: %s\n' "$revision" > "$output_dir/RELEASE-MANIFEST.txt"
printf '%s\n' "$output_dir"
