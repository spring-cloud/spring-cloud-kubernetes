#!/usr/bin/env bash

# Runs a command and re-runs it from scratch if it fails.
#
# This exists for the steps that build the docker images, which happen before any test
# runs and are therefore not covered by run-tests-with-retry.sh. Those steps fail for
# reasons that have nothing to do with this project: the paketo buildpacks download a JRE
# and the buildpack images from the network on every build, so a reset connection or a
# slow mirror fails the whole shard about twenty minutes before it would have finished.
# A real example, which is what prompted this script:
#
#   BellSoft Liberica JRE 17.0.20: Contributing to layer
#     Downloading from https://github.com/bell-sw/Liberica/releases/download/...
#   fetching dependency BellSoft Liberica JRE failed
#   Get "https://release-assets.githubusercontent.com/...": read tcp ...: read: connection
#   reset by peer
#   ERROR: failed to build: exit status 1
#
# In that same build a module running in parallel downloaded the very same JRE half a
# second later without trouble, so simply doing it again is the entire fix.
#
# Unlike run-tests-with-retry.sh this retries the whole command rather than narrowing it.
# There are no test reports to narrow against at this point in the job, and re-running
# 'clean install' is cheap next to losing the shard.
#
# Usage: retry-command.sh <command> [args...]

set -eo pipefail

# Number of extra attempts after the first one. 0 disables retrying.
MAX_RETRIES="${COMMAND_RETRY_COUNT:-2}"
# Seconds to wait before the first retry. Multiplied by the attempt number, so with the
# default the waits are 15s and then 30s, giving a blipping mirror a moment to recover.
RETRY_DELAY="${COMMAND_RETRY_DELAY:-15}"

if [[ $# -eq 0 ]]; then
  echo "usage: retry-command.sh <command> [args...]" >&2
  exit 2
fi

# Leaves a note in the job summary so a step that only went green on a retry is visible
# in the run overview instead of being buried in tens of thousands of log lines.
note_retry() {
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    echo "- shard \`${CURRENT_INDEX:-?}\`: \`$1\` succeeded on attempt $2 of $3" \
      >> "${GITHUB_STEP_SUMMARY}"
  fi
}

main() {
  local attempt=0
  local rc=0
  local delay
  local total=$(( MAX_RETRIES + 1 ))

  while true; do
    set +e
    "$@"
    rc=$?
    set -e

    if [[ $rc -eq 0 ]]; then
      if [[ $attempt -gt 0 ]]; then
        echo "=========================================================================="
        echo "command succeeded on attempt $(( attempt + 1 )) of ${total}"
        echo "=========================================================================="
        note_retry "$1" "$(( attempt + 1 ))" "${total}"
      fi
      return 0
    fi

    # Out of budget. Keep the command's own exit code so the step fails exactly as it
    # would have without this wrapper.
    if [[ $attempt -ge $MAX_RETRIES ]]; then
      echo "=========================================================================="
      echo "command failed with exit code ${rc} after ${total} attempt(s), giving up"
      echo "=========================================================================="
      return $rc
    fi

    attempt=$(( attempt + 1 ))
    delay=$(( RETRY_DELAY * attempt ))

    echo "=========================================================================="
    echo "command failed with exit code ${rc}"
    echo "retrying in ${delay}s (attempt $(( attempt + 1 )) of ${total}): $*"
    echo "=========================================================================="
    sleep "${delay}"
  done
}

main "$@"
