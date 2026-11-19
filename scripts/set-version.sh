#!/usr/bin/env bash
# Sets the Android version everywhere (build.gradle.kts + F-Droid metadata)
# from a single semantic version string, so the installer never shows a stale
# version. Usage: ./scripts/set-version.sh 0.1.16
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${REPO_ROOT}"

VERSION="${1:-}"
if [[ ! "${VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "usage: $0 MAJOR.MINOR.PATCH  (e.g. 0.1.16)" >&2
  exit 1
fi

IFS='.' read -r MAJOR MINOR PATCH <<<"${VERSION}"
# Matches the existing scheme: 0.1.15 -> 115.
CODE=$(( MAJOR * 10000 + MINOR * 100 + PATCH ))

echo "==> versionName=${VERSION} versionCode=${CODE}"

python3 - "${VERSION}" "${CODE}" <<'PY'
import re, sys, pathlib
version, code = sys.argv[1], sys.argv[2]

gradle = pathlib.Path("app/build.gradle.kts")
s = gradle.read_text()
s, n1 = re.subn(r'(?m)^\s*versionCode\s*=\s*\d+\s*$', f'        versionCode = {code}', s)
s, n2 = re.subn(r'(?m)^\s*versionName\s*=\s*"[^"]+"\s*$', f'        versionName = "{version}"', s)
s = re.sub(r'// \d+\.\d+\.\d+ -> major\*10000 \+ minor\*100 \+ patch\.',
           f'// {version} -> major*10000 + minor*100 + patch.', s)
if n1 != 1 or n2 != 1:
    sys.exit(f"failed to patch build.gradle.kts (code={n1}, name={n2})")
gradle.write_text(s)

meta = pathlib.Path("metadata/org.osmium.keyboard.yml")
if meta.exists():
    m = meta.read_text()
    m = re.sub(r'(?m)^(\s*-\s*versionName:\s*)\S+', rf'\g<1>{version}', m, count=1)
    m = re.sub(r'(?m)^(\s*versionCode:\s*)\d+', rf'\g<1>{code}', m, count=1)
    m = re.sub(r'(?m)^(\s*commit:\s*)v\d+\.\d+\.\d+', rf'\g<1>v{version}', m, count=1)
    meta.write_text(m)
PY

git add app/build.gradle.kts metadata/org.osmium.keyboard.yml
git commit -q -m "chore(release): bump version to ${VERSION} (${CODE})"
echo "==> committed $(git rev-parse --short HEAD)"
