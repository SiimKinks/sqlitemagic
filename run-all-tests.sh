#!/usr/bin/env bash

set -u
set -o pipefail
trap 'exit 130' INT
trap 'exit 143' TERM

usage() {
  printf 'Usage: %s [--clean] [all | SUITE ...]\n       %s --help\n' "${0##*/}" "${0##*/}"
  printf 'No arguments or all runs every suite; otherwise combine any suite selectors.\n'
  printf '  compiler                  Compiler JVM tests\n'
  printf '  runtime                   Runtime JVM tests\n'
  printf '  gradle-plugin             Gradle plugin unit and consumer integration tests\n'
  printf '  gradle-plugin-unit        Gradle plugin unit and task-cache tests, without publication\n'
  printf '  gradle-plugin-integration Gradle plugin consumer integration tests, with publication\n'
  printf '  consumer                  sqlitemagic-tests JVM tests\n'
  printf '  android                   sqlitemagic-tests connected Android tests\n'
  printf '  sample                    Sample JVM tests, debug assembly, and lint\n'
  printf '  sample-android            Sample connected Android tests\n'
  printf 'Examples: %s runtime gradle-plugin-unit; %s gradle-plugin-integration\n' \
      "${0##*/}" "${0##*/}"
  printf '%s --clean removes saved run logs and test results/reports without running tests.\n' "${0##*/}"
  printf '%s --clean runtime removes those results before running selected suites.\n' "${0##*/}"
  printf 'Consumer, Android, and sample suites first publish current artifacts to Maven local.\n'
  printf 'Test runs require the configured JDK and Android SDK.\n'
  printf 'Connected Android verification also requires Python 3.\n'
  printf 'A connected emulator or device is only needed when selecting Android tests.\n'
  printf 'Artifacts use the normal Maven local repository; logs are retained under build/all-tests/.\n'
}

if [ "$#" -eq 1 ] && [ "$1" = '--help' ]; then
  usage
  exit 0
fi

select_all=0
select_compiler=0
select_runtime=0
select_plugin_unit=0
select_plugin_integration=0
select_consumer=0
select_android=0
select_sample=0
select_sample_android=0
clean_results=0
if [ "$#" -eq 0 ]; then
  select_all=1
fi
for selector in "$@"; do
  case "$selector" in
    --clean) clean_results=1 ;;
    all) select_all=1 ;;
    compiler) select_compiler=1 ;;
    runtime) select_runtime=1 ;;
    gradle-plugin)
      select_plugin_unit=1
      select_plugin_integration=1
      ;;
    gradle-plugin-unit) select_plugin_unit=1 ;;
    gradle-plugin-integration) select_plugin_integration=1 ;;
    consumer) select_consumer=1 ;;
    android) select_android=1 ;;
    sample) select_sample=1 ;;
    sample-android) select_sample_android=1 ;;
    *)
      printf 'Unknown suite selector: %s\n' "$selector" >&2
      usage >&2
      exit 2
      ;;
  esac
done

root_tasks=()
nested_tasks=()
sample_tasks=()
if [ "$select_all" -eq 1 ]; then
  root_tasks=(test ":gradle-plugin:integrationTest")
  select_consumer=1
  select_android=1
  select_sample=1
  select_sample_android=1
else
  if [ "$select_compiler" -eq 1 ]; then
    root_tasks+=(":compiler:test")
  fi
  if [ "$select_runtime" -eq 1 ]; then
    root_tasks+=(":migration-testing:test" ":runtime:test")
  fi
  if [ "$select_plugin_unit" -eq 1 ]; then
    root_tasks+=(":gradle-plugin:test")
  fi
  if [ "$select_plugin_integration" -eq 1 ]; then
    root_tasks+=(":gradle-plugin:integrationTest")
  fi
fi
if [ "$select_consumer" -eq 1 ]; then
  nested_tasks+=(test)
fi
if [ "$select_android" -eq 1 ]; then
  nested_tasks+=(connectedAndroidTest)
fi

if [ "$select_sample" -eq 1 ]; then
  sample_tasks=(":app:testDebugUnitTest" ":app:assembleDebug" ":app:lintDebug")
fi
if [ "$select_sample_android" -eq 1 ]; then
  sample_tasks+=(":app:connectedDebugAndroidTest")
fi

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd) || exit 1
if [ "$clean_results" -eq 1 ]; then
  printf 'Removing saved run logs and generated test results/reports.\n'
  rm -rf -- "$repo_root/build/all-tests" || exit 1
  for module in annotations compiler migration-testing runtime gradle-plugin \
      sqlitemagic-tests/app sqlitemagic-tests/submodule sqlitemagic-tests/migration-consumer \
      sqlitemagic-tests/migration-consumer-feature sqlitemagic-sample/app; do
    rm -rf -- \
        "$repo_root/$module/build/test-results" \
        "$repo_root/$module/build/reports/tests" \
        "$repo_root/$module/build/outputs/androidTest-results" \
        "$repo_root/$module/build/reports/androidTests" \
        "$repo_root/$module/build/reports/lint-results-debug.html" \
        "$repo_root/$module/build/reports/lint-results-debug.xml" \
        "$repo_root/$module/build/reports/lint-results-debug.txt" || exit 1
  done
  if [ "${#root_tasks[@]}" -eq 0 ] && [ "${#nested_tasks[@]}" -eq 0 ] \
      && [ "${#sample_tasks[@]}" -eq 0 ]; then
    exit 0
  fi
fi
mkdir -p "$repo_root/build/all-tests" || exit 1
run_dir=$(mktemp -d "$repo_root/build/all-tests/run.XXXXXX") || exit 1

run_phase() {
  local name="$1"
  local project_dir="$2"
  shift 2
  printf '\nRunning %s; log: %s/%s.log\n' "$name" "$run_dir" "$name"
  (
    cd -- "$project_dir" || exit 1
    ./gradlew "$@"
  ) 2>&1 | tee "$run_dir/$name.log"
  local statuses=("${PIPESTATUS[@]}")
  if [ "${statuses[0]}" -ne 0 ]; then
    return "${statuses[0]}"
  fi
  return "${statuses[1]}"
}

verify_connected_results() {
  local name="$1"
  local project_dir="$2"
  local marker="$3"
  python3 - \
      "$project_dir/app/build/outputs/androidTest-results/connected" \
      "$marker" \
      "$run_dir/$name.log" \
      "$run_dir/$name-connected-results" <<'PY'
import re
import shutil
import sys
from pathlib import Path
import xml.etree.ElementTree as ET


class VerificationError(Exception):
  pass


def local_name(element):
  return element.tag.rsplit("}", 1)[-1]


def verify_results(results_dir, marker, log, evidence_dir):
  started_at = marker.stat().st_mtime_ns
  reports = sorted(
      path for path in results_dir.rglob("*.xml")
      if path.stat().st_mtime_ns >= started_at
  )
  if not reports:
    raise VerificationError(f"No fresh connected Android XML reports under {results_dir}")

  for report in reports:
    destination = evidence_dir / report.relative_to(results_dir)
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src=report, dst=destination)
  log_text = log.read_text(encoding="utf-8", errors="replace")
  if re.search(r"^AndroidTestRunner failed\b", log_text, flags=re.IGNORECASE | re.MULTILINE):
    raise VerificationError("Gradle log reports AndroidTestRunner failed")

  tests = 0
  skipped = 0
  for report in reports:
    try:
      root = ET.parse(report).getroot()
    except ET.ParseError as failure:
      raise VerificationError(f"Malformed connected Android XML report {report}: {failure}") from failure
    for element in root.iter():
      tag = local_name(element)
      if tag in ("failure", "error"):
        raise VerificationError(f"Connected Android report contains {tag}: {report}")
      for attribute in ("failures", "errors"):
        try:
          count = int(element.attrib.get(attribute, "0"))
        except ValueError as failure:
          raise VerificationError(f"Invalid {attribute} count in {report}") from failure
        if count != 0:
          raise VerificationError(f"Connected Android report has {attribute}={count}: {report}")
      if tag == "testsuite":
        cases = [case for case in element.iter() if local_name(case) == "testcase"]
        executed = any(
            not any(local_name(child) == "skipped" for child in case)
            for case in cases
        )
        if not executed:
          raise VerificationError(f"Fresh connected Android suite contains no executed tests: {report}")
      if tag == "testcase":
        tests += 1
        skipped += any(local_name(child) == "skipped" for child in element)
  if tests == skipped:
    raise VerificationError(
        f"Connected Android reports contain no executed tests ({tests} tests, {skipped} skipped)"
    )
  return len(reports), tests, skipped


try:
  reports, tests, skipped = verify_results(*map(Path, sys.argv[1:]))
except (VerificationError, OSError) as failure:
  print(f"Connected Android verification failed: {failure}", file=sys.stderr)
  sys.exit(1)
print(
    f"Verified connected Android results: {reports} reports, "
    f"{tests} tests, {tests - skipped} executed, {skipped} skipped."
)
PY
}

publication_status='NOT SELECTED'
root_status='NOT SELECTED'
nested_status='NOT SELECTED'
sample_status='NOT SELECTED'
exit_status=0

if [ "${#nested_tasks[@]}" -gt 0 ] || [ "${#sample_tasks[@]}" -gt 0 ]; then
  if run_phase publish "$repo_root" publishToMavenLocal --continue --console=plain; then
    publication_status=PASS
  else
    publication_status=FAIL
    exit_status=1
  fi
fi

if [ "${#root_tasks[@]}" -gt 0 ]; then
  if run_phase root-tests "$repo_root" "${root_tasks[@]}" \
      --continue --rerun-tasks --no-build-cache --console=plain; then
    root_status=PASS
  else
    root_status=FAIL
    exit_status=1
  fi
fi

if [ "${#nested_tasks[@]}" -gt 0 ]; then
  if [ "$publication_status" = PASS ]; then
    android_connected_marker="$run_dir/android-connected-start"
    if [ "$select_android" -eq 1 ]; then
      touch "$android_connected_marker" || exit 1
    fi
    if run_phase android-tests "$repo_root/sqlitemagic-tests" "${nested_tasks[@]}" \
        --continue --rerun-tasks --no-build-cache --console=plain; then
      nested_status=PASS
    else
      nested_status=FAIL
      exit_status=1
    fi
    if [ "$select_android" -eq 1 ]; then
      if ! verify_connected_results android-tests "$repo_root/sqlitemagic-tests" "$android_connected_marker"; then
        nested_status=FAIL
        exit_status=1
      fi
    fi
  else
    nested_status=SKIPPED
    printf '\nSelected Android project tests SKIPPED: current artifacts could not be published to Maven local.\n'
  fi
fi

if [ "${#sample_tasks[@]}" -gt 0 ]; then
  if [ "$publication_status" = PASS ]; then
    sample_connected_marker="$run_dir/sample-connected-start"
    if [ "$select_sample_android" -eq 1 ]; then
      touch "$sample_connected_marker" || exit 1
    fi
    if run_phase sample-tests "$repo_root/sqlitemagic-sample" "${sample_tasks[@]}" \
        --continue --rerun-tasks --no-build-cache --console=plain; then
      sample_status=PASS
    else
      sample_status=FAIL
      exit_status=1
    fi
    if [ "$select_sample_android" -eq 1 ]; then
      if ! verify_connected_results sample-tests "$repo_root/sqlitemagic-sample" "$sample_connected_marker"; then
        sample_status=FAIL
        exit_status=1
      fi
    fi
  else
    sample_status=SKIPPED
    printf '\nSelected sample checks SKIPPED: current artifacts could not be published to Maven local.\n'
  fi
fi

printf '\nTest run summary:\n'
printf '  Publication: %s\n' "$publication_status"
printf '  Root JVM tests [%s]: %s\n' "${root_tasks[*]-}" "$root_status"
printf '  Android project tests [%s]: %s\n' "${nested_tasks[*]-}" "$nested_status"
printf '  Sample checks [%s]: %s\n' "${sample_tasks[*]-}" "$sample_status"
printf '  Retained logs: %s\n' "$run_dir"
printf '  Root test reports: %s/<module>/build/reports/tests/\n' "$repo_root"
printf '  Android project test reports: %s/sqlitemagic-tests/<module>/build/reports/\n' "$repo_root"
printf '  Sample reports: %s/sqlitemagic-sample/app/build/reports/\n' "$repo_root"
exit "$exit_status"
