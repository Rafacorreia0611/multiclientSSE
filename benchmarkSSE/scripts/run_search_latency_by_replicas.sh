#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEFAULT_CONFIG="$ROOT_DIR/benchmarkSSE/config/search_latency_by_docs.properties"
CONFIG_PATH="$DEFAULT_CONFIG"
REPLICA_LIST="4,7,10,13"
TIMESTAMP_FORMAT="%Y%m%d_%H%M%S"

DOCS_RUNNER="$ROOT_DIR/benchmarkSSE/scripts/run_search_latency_by_docs.sh"
AGGREGATE_SCRIPT="$ROOT_DIR/benchmarkSSE/scripts/aggregate_search_latency_by_replicas.py"
GNUPLOT_SCRIPT="$ROOT_DIR/benchmarkSSE/gnuplot/search_latency_by_replicas.gp"

usage() {
  cat >&2 <<EOF
Usage: $(basename "$0") [--config PATH] [--replicas N[,N...]]
EOF
}

parse_args() {
  while [[ "$#" -gt 0 ]]; do
    case "$1" in
      --config)
        if [[ "$#" -lt 2 || "$2" == --* ]]; then
          echo "Missing value for --config." >&2
          usage
          exit 1
        fi
        CONFIG_PATH="$2"
        shift 2
        ;;
      --config=*)
        CONFIG_PATH="${1#--config=}"
        if [[ -z "$CONFIG_PATH" ]]; then
          echo "Missing value for --config." >&2
          usage
          exit 1
        fi
        shift
        ;;
      --replicas)
        if [[ "$#" -lt 2 || "$2" == --* ]]; then
          echo "Missing value for --replicas." >&2
          usage
          exit 1
        fi
        REPLICA_LIST="$2"
        shift 2
        ;;
      --replicas=*)
        REPLICA_LIST="${1#--replicas=}"
        if [[ -z "$REPLICA_LIST" ]]; then
          echo "Missing value for --replicas." >&2
          usage
          exit 1
        fi
        shift
        ;;
      --help|-h)
        usage
        exit 0
        ;;
      *)
        echo "Unknown argument: $1" >&2
        usage
        exit 1
        ;;
    esac
  done

  if [[ "$CONFIG_PATH" != /* ]]; then
    CONFIG_PATH="$ROOT_DIR/$CONFIG_PATH"
  fi
}

ensure_file_exists() {
  local path="$1"
  if [[ ! -f "$path" ]]; then
    echo "Required file not found: $path" >&2
    exit 1
  fi
}

ensure_command_exists() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command not found on PATH: $command_name" >&2
    exit 1
  fi
}

parse_replica_list() {
  local raw_list compact_list replica_count

  compact_list="${REPLICA_LIST//[[:space:]]/}"
  if [[ -z "$compact_list" ]]; then
    echo "Replica list cannot be empty." >&2
    exit 1
  fi

  IFS=',' read -r -a REPLICA_COUNTS <<< "$compact_list"
  for replica_count in "${REPLICA_COUNTS[@]}"; do
    if ! [[ "$replica_count" =~ ^[0-9]+$ ]] || [[ "$replica_count" -lt 4 ]]; then
      echo "Replica counts must be integers greater than or equal to 4: $replica_count" >&2
      exit 1
    fi
  done
}

fault_count_for() {
  local replica_count="$1"
  printf '%s\n' $(((replica_count - 1) / 3))
}

prepare_outputs() {
  SWEEP_TIMESTAMP="$(date +"$TIMESTAMP_FORMAT")"
  SWEEP_NAME="replica_sweep_${SWEEP_TIMESTAMP}"
  SWEEP_RESULTS_DIR="$ROOT_DIR/benchmarkSSE/results/$SWEEP_NAME"
  SWEEP_PLOTS_DIR="$ROOT_DIR/benchmarkSSE/plots/$SWEEP_NAME"
  SWEEP_LOG_DIR="$ROOT_DIR/benchmarkSSE/results/logs/$SWEEP_NAME"
  SWEEP_SUMMARY_PATH="$SWEEP_RESULTS_DIR/search_latency_by_replicas_summary.tsv"
  SWEEP_LOG_PATH="$SWEEP_LOG_DIR/run_search_latency_by_replicas.log"

  mkdir -p "$SWEEP_RESULTS_DIR" "$SWEEP_PLOTS_DIR" "$SWEEP_LOG_DIR"
}

latest_summary_for_run() {
  local marker_path="$1"
  local replica_count="$2"
  local fault_count="$3"
  local summary_path

  summary_path="$(
    find "$ROOT_DIR/benchmarkSSE/results" \
      -maxdepth 2 \
      -type f \
      -name "search_latency_by_docs_summary.tsv" \
      -newer "$marker_path" \
      -path "*_r${replica_count}_f${fault_count}/search_latency_by_docs_summary.tsv" \
      -print |
      sort |
      tail -n 1
  )"

  if [[ -z "$summary_path" ]]; then
    echo "Could not find summary for replicaCount=$replica_count f=$fault_count." >&2
    exit 1
  fi

  printf '%s\n' "$summary_path"
}

run_one_benchmark() {
  local replica_count="$1"
  local fault_count marker_path summary_path

  fault_count="$(fault_count_for "$replica_count")"
  marker_path="$(mktemp "$ROOT_DIR/benchmarkSSE/results/.replica_sweep_marker.XXXXXX")"
  {
    echo
    echo "===== Running search latency benchmark with replicaCount=$replica_count f=$fault_count ====="
  } | tee -a "$SWEEP_LOG_PATH"

  if ! "$DOCS_RUNNER" --config "$CONFIG_PATH" --replicas "$replica_count" 2>&1 | tee -a "$SWEEP_LOG_PATH"; then
    rm -f "$marker_path"
    echo "Benchmark failed for replicaCount=$replica_count." >&2
    exit 1
  fi

  summary_path="$(latest_summary_for_run "$marker_path" "$replica_count" "$fault_count")"
  rm -f "$marker_path"
  SUMMARY_PATHS+=("$summary_path")

  echo "Summary for replicaCount=$replica_count: $summary_path" | tee -a "$SWEEP_LOG_PATH"
}

aggregate_results() {
  echo "Aggregating ${#SUMMARY_PATHS[@]} summary file(s)..." | tee -a "$SWEEP_LOG_PATH"
  python3 "$AGGREGATE_SCRIPT" --output "$SWEEP_SUMMARY_PATH" "${SUMMARY_PATHS[@]}" 2>&1 | tee -a "$SWEEP_LOG_PATH"
}

generate_plots() {
  echo "Generating replica sweep plots..." | tee -a "$SWEEP_LOG_PATH"
  gnuplot \
    -e "input_path='$SWEEP_SUMMARY_PATH'; output_dir='$SWEEP_PLOTS_DIR'" \
    "$GNUPLOT_SCRIPT" 2>&1 | tee -a "$SWEEP_LOG_PATH"
}

verify_outputs() {
  local plot_name

  if [[ ! -s "$SWEEP_SUMMARY_PATH" ]]; then
    echo "Aggregated summary is missing or empty: $SWEEP_SUMMARY_PATH" >&2
    exit 1
  fi

  for plot_name in \
    search_latency_by_replicas_median_fresh.png \
    search_latency_by_replicas_median_cached.png \
    search_latency_by_replicas_mean_fresh.png \
    search_latency_by_replicas_mean_cached.png; do
    if [[ ! -s "$SWEEP_PLOTS_DIR/$plot_name" ]]; then
      echo "Plot is missing or empty: $SWEEP_PLOTS_DIR/$plot_name" >&2
      exit 1
    fi
  done
}

main() {
  parse_args "$@"
  parse_replica_list
  ensure_file_exists "$DOCS_RUNNER"
  ensure_file_exists "$AGGREGATE_SCRIPT"
  ensure_file_exists "$GNUPLOT_SCRIPT"
  ensure_file_exists "$CONFIG_PATH"
  ensure_command_exists python3
  ensure_command_exists gnuplot

  SUMMARY_PATHS=()

  prepare_outputs
  echo "Replica sweep: ${REPLICA_COUNTS[*]}" | tee -a "$SWEEP_LOG_PATH"
  echo "Config: $CONFIG_PATH" | tee -a "$SWEEP_LOG_PATH"

  local replica_count
  for replica_count in "${REPLICA_COUNTS[@]}"; do
    run_one_benchmark "$replica_count"
  done

  aggregate_results
  generate_plots
  verify_outputs

  echo "Replica sweep summary written to $SWEEP_SUMMARY_PATH"
  echo "Replica sweep plots written to $SWEEP_PLOTS_DIR"
}

main "$@"
