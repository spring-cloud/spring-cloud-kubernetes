#!/usr/bin/env bash

# Runs one shard of the test suite, then retries only the test classes that failed.
#
# The test matrices in maven.yaml are 'fail-fast: false', so every shard now runs to
# completion. That makes a single flaky shard fail the whole build on its own, and the
# integration tests here start k3s/testcontainers in @BeforeAll, which is exactly the
# kind of failure that does not reproduce on a second run. Re-running the entire shard
# would cost tens of minutes, so instead we re-run only the classes whose surefire or
# failsafe report records a failure, in a fresh Maven invocation so that the containers
# are started from scratch.
#
# The retry set is deliberately not just "the classes that failed". Maven is run with
# '--fail-at-end' so that one failing module no longer stops the reactor before the later
# modules of the shard have run, but modules that depend on a failed one are still
# skipped. Any class of this shard that produced no report therefore never ran, and is
# retried alongside the ones that failed. Without that, a retry that fixes the flaky class
# would report a green shard whose remaining tests were never executed.
#
# Usage: run-tests-with-retry.sh <comma-separated-test-classes> [extra mvn args...]

set -eo pipefail

TESTS_TO_RUN="$1"
shift

# Number of extra attempts after the initial run. Set TEST_RETRY_COUNT=0 to disable
# retries entirely and get the previous behaviour back.
MAX_RETRIES="${TEST_RETRY_COUNT:-1}"

# $MAVEN_SETTINGS is deliberately unquoted: it holds '--settings <path>', which has to
# word-split into two arguments. This mirrors how the other composites invoke mvnw.
mvn_run() {
  local tests="$1"
  shift

  ./mvnw $MAVEN_SETTINGS \
      -DtestsToRun="${tests}" \
      "$@" \
      -P sonar -nsu --batch-mode --fail-at-end \
      -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn \
      -Dhttp.keepAlive=false \
      -Dmaven.wagon.http.pool=false \
      -Dmaven.wagon.http.retryHandler.class=standard \
      -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
      -Dmaven.wagon.http.retryHandler.count=3 \
      -Dmaven.resolver.transport=wagon \
      -Dspring-boot.build-image.skip=true
}

# Fully qualified names of the test classes whose report records a failure or an error.
# Surefire and failsafe both write one plain text report per class, named <FQCN>.txt,
# holding a summary line that looks like:
#   Tests run: 3, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 1.2 s <<< FAILURE!
# Note that the reports directory is not fixed: the cache-present shards redirect it to
# surefire-reports/<index>, hence matching on the path rather than on a literal dir.
failed_test_classes() {
  local report name

  while IFS= read -r -d '' report; do
    if grep -q -E 'Tests run:.*(Failures: [1-9]|Errors: [1-9])' "${report}"; then
      name="${report##*/}"
      echo "${name%.txt}"
    fi
  done < <(find . -type f \
                \( -path '*surefire-reports*' -o -path '*failsafe-reports*' \) \
                -name '*.txt' ! -name '*-output.txt' -print0) \
    | sort -u \
    | paste -sd, -
}

# Fully qualified names of the test classes that were asked for but produced no report
# at all, meaning they never ran.
#
# This is not a theoretical case. The reactor is 41 modules deep and a shard's classes are
# spread across many of them, so when one module fails, '--fail-at-end' still skips every
# module that depends on the failed one. Those classes have to be carried into the retry,
# otherwise a retry that fixes the flaky class lets the shard go green while tests that
# were never executed are silently dropped.

# Drops the entries of a comma separated test list that are not real class names: 'none'
# is the sentinel the cache-present composite uses for an index that has no tests, and
# empty entries show up when a slice runs past the end of the test list.
clean_list() {
  local test
  local -a input=() kept=()

  IFS=',' read -r -a input <<< "$1"
  for test in "${input[@]}"; do
    if [[ -n "${test}" && "${test}" != "none" ]]; then
      kept+=("${test}")
    fi
  done

  ( IFS=','; echo "${kept[*]}" )
}

never_ran_test_classes() {
  local requested
  local existing test
  local -a requested_array=() missing=()

  requested=$(clean_list "$1")

  # one pass over the reports, turning 'target/failsafe-reports/3/org.foo.BarIT.txt' into
  # 'org.foo.BarIT'. Report basenames are class names, so they never contain a space.
  existing=$(find . -type f \
                  \( -path '*surefire-reports*' -o -path '*failsafe-reports*' \) \
                  -name '*.txt' ! -name '*-output.txt' \
               | sed -e 's|.*/||' -e 's|\.txt$||' \
               | sort -u)

  IFS=',' read -r -a requested_array <<< "${requested}"
  for test in "${requested_array[@]}"; do
    if ! grep -Fxq "${test}" <<< "${existing}"; then
      missing+=("${test}")
    fi
  done

  ( IFS=','; echo "${missing[*]}" )
}

# Joins the non-empty arguments with commas, so an empty failed set or an empty never-ran
# set does not produce a leading or doubled comma in '-DtestsToRun'.
join_non_empty() {
  local part joined=''

  for part in "$@"; do
    if [[ -n "${part}" ]]; then
      if [[ -z "${joined}" ]]; then joined="${part}"; else joined="${joined},${part}"; fi
    fi
  done

  echo "${joined}"
}

# Leaves a note in the job summary so a shard that only went green on a retry is
# visible in the run overview instead of being buried in the log.
note_retry() {
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    echo "- shard \`${CURRENT_INDEX:-?}\`: retried failed test classes -> \`$1\`" \
      >> "${GITHUB_STEP_SUMMARY}"
  fi
}

main() {
  local attempt=0
  local rc=0
  local failed never_ran retry_set
  # the set of classes this attempt is accountable for. It narrows on every retry, since
  # the classes that already passed do not need to be run or accounted for again.
  local outstanding="${TESTS_TO_RUN}"

  set +e
  mvn_run "${TESTS_TO_RUN}" "$@" -e clean install
  rc=$?
  set -e

  while [[ $rc -ne 0 && $attempt -lt $MAX_RETRIES ]]; do
    attempt=$(( attempt + 1 ))

    failed=$(failed_test_classes)
    never_ran=$(never_ran_test_classes "${outstanding}")
    retry_set=$(join_non_empty "${failed}" "${never_ran}")

    # Nothing to retry: every test this shard was asked for ran and passed, yet Maven
    # still failed. The failure is therefore somewhere other than the tests, typically a
    # later phase of a module whose tests were already green (packaging, the image build,
    # installing into the local repository).
    #
    # This must return rather than fall through. Retrying with an empty test set would
    # expand '<include>${testsToRun}</include>' to nothing, match zero classes and exit 0,
    # silently reporting a green shard for a build that failed.
    if [[ -z "${retry_set}" ]]; then
      echo "maven failed but every test of this shard produced a passing report, so the"
      echo "failure is outside the tests (packaging, image build, install, ...)."
      echo "not retrying."
      return $rc
    fi

    # Nothing failed and nothing ran: Maven died before it got as far as testing, which
    # means this is not a test failure but a compilation error, a dependency that would
    # not resolve, the runner running out of disk. A retry would fail in exactly the same
    # way, so keep the original exit code instead of paying for another full build.
    if [[ -z "${failed}" ]] && [[ "${never_ran}" == "$(clean_list "${outstanding}")" ]]; then
      echo "maven failed but no test of this shard produced a report, so this is not a"
      echo "test failure (compilation, dependency resolution, the runner itself, ...)."
      echo "not retrying."
      return $rc
    fi

    echo "=============================================================================="
    echo "attempt ${attempt} of ${MAX_RETRIES}: re-running the following test classes"
    if [[ -n "${failed}" ]]; then
      echo "-- failed:"
      echo "${failed}" | tr ',' '\n'
    fi
    if [[ -n "${never_ran}" ]]; then
      # Maven stops descending into the modules that depend on a failed one, so these were
      # requested by this shard but never got the chance to run.
      echo "-- never ran (their module was skipped after an earlier module failed):"
      echo "${never_ran}" | tr ',' '\n'
    fi
    echo "=============================================================================="
    note_retry "${retry_set}"

    outstanding="${retry_set}"

    # No 'clean' on the retry, on purpose. It would delete the reports of the classes
    # that already passed, and the steps after this one read every report to work out
    # how long each test took. Re-running writes fresh reports for the retried classes
    # only, which is exactly the bookkeeping we want.
    set +e
    mvn_run "${retry_set}" "$@" -e install
    rc=$?
    set -e
  done

  return $rc
}

main "$@"
