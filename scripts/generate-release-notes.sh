#!/usr/bin/env bash
# Generates release_notes/<version>.txt from the commit log between the previous release tag
# (by version-sort order) and the given tag. Uses each commit's full message body (squash-merge
# commits in this repo carry a detailed description, not just a one-line title) rather than the
# bare subject line, for a more informative changelog than a list of PR numbers. Used both for
# one-off backfills and by the "release-notes" CI job that runs on every version tag push (see
# .github/workflows/ci.yml).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${REPO_ROOT}/release_notes"

format_commit() {
  local hash="$1"
  local body
  body=$(git -C "$REPO_ROOT" log -1 --format=%B "$hash")

  local subject
  subject=$(echo "$body" | head -1 | sed -E 's/ \(#[0-9]+\)$//')

  echo "- ${subject}"
  echo "$body" | tail -n +2 | sed '/^[[:space:]]*$/d' | { grep -viE '^(co-authored-by:|closes #[0-9]+$)' || true; } | sed 's/^/  /'
  echo ""
}

generate_one() {
  local tag="$1"
  local version="${tag#v}"
  local prev_tag
  prev_tag=$(git -C "$REPO_ROOT" tag --sort=v:refname | grep -B1 -x "$tag" | head -1)
  local release_date
  release_date=$(git -C "$REPO_ROOT" log -1 --format=%ai "$tag" | cut -d' ' -f1)
  local outfile="${OUT_DIR}/${version}.txt"

  local range
  if [ "$prev_tag" = "$tag" ]; then
    range="$tag"
  else
    range="${prev_tag}..${tag}"
  fi

  {
    echo "Release Notes ${version}"
    echo "Released: ${release_date}"
    echo ""
    echo "Changes:"
    echo ""
    while IFS= read -r hash; do
      format_commit "$hash"
    done < <(git -C "$REPO_ROOT" log --reverse --format=%H "$range")
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
