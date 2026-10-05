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
  printf 'Examples: %s runtime gradle-plugin-unit; %s gradle-plugin-integration\n' \
      "${0##*/}" "${0##*/}"
  printf '%s --clean removes saved run logs and test results/reports without running tests.\n' "${0##*/}"
  printf '%s --clean runtime removes those results before running selected suites.\n' "${0##*/}"
  printf 'Consumer and Android suites first publish current artifacts to Maven local.\n'
  printf 'Test runs require the configured JDK and Android SDK.\n'
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
    *)
      printf 'Unknown suite selector: %s\n' "$selector" >&2
      usage >&2
      exit 2
      ;;
  esac
done

root_tasks=()
nested_tasks=()
if [ "$select_all" -eq 1 ]; then
  root_tasks=(test ":gradle-plugin:integrationTest")
  select_consumer=1
  select_android=1
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

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd) || exit 1
if [ "$clean_results" -eq 1 ]; then
  printf 'Removing saved run logs and generated test results/reports.\n'
  rm -rf -- "$repo_root/build/all-tests" || exit 1
  for module in annotations compiler migration-testing runtime gradle-plugin \
      sqlitemagic-tests/app sqlitemagic-tests/submodule sqlitemagic-tests/migration-consumer \
      sqlitemagic-tests/migration-consumer-feature; do
    rm -rf -- \
        "$repo_root/$module/build/test-results" \
        "$repo_root/$module/build/reports/tests" \
        "$repo_root/$module/build/outputs/androidTest-results" \
        "$repo_root/$module/build/reports/androidTests" || exit 1
  done
  if [ "${#root_tasks[@]}" -eq 0 ] && [ "${#nested_tasks[@]}" -eq 0 ]; then
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

publication_status='NOT SELECTED'
root_status='NOT SELECTED'
nested_status='NOT SELECTED'
exit_status=0

if [ "${#nested_tasks[@]}" -gt 0 ]; then
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
    if run_phase android-tests "$repo_root/sqlitemagic-tests" "${nested_tasks[@]}" \
        --continue --rerun-tasks --no-build-cache --console=plain; then
      nested_status=PASS
    else
      nested_status=FAIL
      exit_status=1
    fi
  else
    nested_status=SKIPPED
    printf '\nSelected Android project tests SKIPPED: current artifacts could not be published to Maven local.\n'
  fi
fi

printf '\nTest run summary:\n'
printf '  Publication: %s\n' "$publication_status"
printf '  Root JVM tests [%s]: %s\n' "${root_tasks[*]-}" "$root_status"
printf '  Android project tests [%s]: %s\n' "${nested_tasks[*]-}" "$nested_status"
printf '  Retained logs: %s\n' "$run_dir"
printf '  Root test reports: %s/<module>/build/reports/tests/\n' "$repo_root"
printf '  Android project test reports: %s/sqlitemagic-tests/<module>/build/reports/\n' "$repo_root"
exit "$exit_status"
