#!/usr/bin/env bash
# Run Gradle with the right JDK/SDK regardless of shell profile: scripts/gw.sh <gradle args>
set -euo pipefail
. "$(dirname "$0")/env.sh"
cd "$(dirname "$0")/.."
exec ./gradlew --console=plain "$@"
