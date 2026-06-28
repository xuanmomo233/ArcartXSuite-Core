#!/usr/bin/env bash
# 维护者：打标签并推送，触发 GitHub Actions 自动构建 axs-api JAR 并发布 Release。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="${1:-}"
if [ -z "$VERSION" ]; then
  VERSION="$(grep '^version=' gradle.properties | cut -d= -f2)"
fi

TAG="v${VERSION}"

echo "==> Sync gradle.properties version to ${VERSION}"
sed -i.bak "s/^version=.*/version=${VERSION}/" gradle.properties && rm -f gradle.properties.bak

echo "==> Local build check"
chmod +x gradlew
./gradlew :axs-api:jar --no-daemon
ls -la "axs-api/build/libs/axs-api-${VERSION}.jar"

echo "==> Commit version bump (if changed)"
git add gradle.properties
if ! git diff --cached --quiet; then
  git commit -m "chore: bump axs-api version to ${VERSION}"
fi

echo "==> Tag and push ${TAG}"
git tag -a "$TAG" -m "axs-api ${VERSION}" 2>/dev/null || git tag -f "$TAG" -m "axs-api ${VERSION}"
git push origin main
git push origin "$TAG"

echo "Done. Watch: https://github.com/xuanmomo233/ArcartXSuite-Core/actions"
