#!/usr/bin/env bash
# Generates release_notes/<version>.txt from the commit log between the previous release tag
# (by version-sort order) and the given tag. Used both for one-off backfills and by the
# "release-notes" CI job that runs on every version tag push (see .github/workflows/ci.yml).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${REPO_ROOT}/release_notes"

generate_one() {
  local tag="$1"
  local version="${tag#v}"
  local prev_tag
  prev_tag=$(git -C "$REPO_ROOT" tag --sort=v:refname | grep -B1 -x "$tag" | head -1)
  local release_date
  release_date=$(git -C "$REPO_ROOT" log -1 --format=%ai "$tag" | cut -d' ' -f1)
  local outfile="${OUT_DIR}/${version}.txt"

  {
    echo "Release Notes ${version}"
    echo "Released: ${release_date}"
    echo ""
    echo "Changes:"
    if [ "$prev_tag" = "$tag" ]; then
      git -C "$REPO_ROOT" log --reverse --pretty=format:"- %s" "$tag"
    else
      git -C "$REPO_ROOT" log --reverse --pretty=format:"- %s" "${prev_tag}..${tag}"
    fi
    echo ""
  } > "$outfile"

  echo "Wrote ${outfile}"
}

mkdir -p "$OUT_DIR"

if [ "${1:-}" = "--all" ]; then
  while IFS= read -r tag; do
    generate_one "$tag"
  done < <(git -C "$REPO_ROOT" tag --sort=v:refname)
else
  if [ $# -ne 1 ]; then
    echo "Usage: $0 <tag> | --all" >&2
    exit 1
  fi
  generate_one "$1"
fi
