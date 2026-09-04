#!/usr/bin/env bash
# Asserts .release-please-manifest.json matches the version this repo actually
# ships, and that a tag for it exists.
#
# release-please writes the manifest only when ITS OWN release PR merges. A
# release cut by hand (`chore(release): X`) tags the commit, bumps the version
# file and writes the CHANGELOG, but leaves the manifest behind — after which
# release-please recomputes from a stale baseline and proposes a version that
# is already tagged. That happened silently in all four SDK repos.
set -euo pipefail
cd "$(dirname "$0")/.."

manifest="$(sed -n 's/.*"\.": *"\([^"]*\)".*/\1/p' .release-please-manifest.json)"
shipped="$(tr -d ' \t\r\n' < version.txt)"

if [ "$manifest" != "$shipped" ]; then
  echo "::error::release-please manifest says '$manifest' but version.txt says '$shipped'." >&2
  echo "If the last release was cut by hand, set the manifest to '$shipped' so" >&2
  echo "release-please computes the next version from the right baseline." >&2
  exit 1
fi
echo "release-please manifest and version.txt agree: $shipped"
