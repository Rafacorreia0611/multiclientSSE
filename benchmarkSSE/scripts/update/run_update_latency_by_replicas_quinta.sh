#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT_DIR/benchmarkSSE/scripts/common/quinta_benchmark_common.sh"

DEFAULT_CONFIG="$ROOT_DIR/benchmarkSSE/config/update/update_latency_by_associations.properties"
DEFAULT_CLUSTER_CONFIG="$ROOT_DIR/benchmarkSSE/config/quinta.env"
CONFIG_PATH="$DEFAULT_CONFIG"
CLUSTER_CONFIG="$DEFAULT_CLUSTER_CONFIG"
REPLICA_LIST="4,7,10"

ASSOCIATIONS_RUNNER="$ROOT_DIR/benchmarkSSE/scripts/update/run_update_latency_by_associations_quinta.sh"
AGGREGATE_SCRIPT="$ROOT_DIR/benchmarkSSE/scripts/update/aggregate_update_latency_by_replicas.py"
GNUPLOT_SCRIPT="$ROOT_DIR/benchmarkSSE/gnuplot/update/update_latency_by_replicas.gp"

usage() {
  cat >&2 <<EOF
Usage: $(basename "$0") [--config PATH] [--cluster-config PATH] [--replicas N[,N...]]
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
      --cluster-config)
        if [[ "$#" -lt 2 || "$2" == --* ]]; then
          echo "Missing value for --cluster-config." >&2
          usage
          exit 1
        fi
        CLUSTER_CONFIG="$2"
        shift 2
        ;;
      --cluster-config=*)
        CLUSTER_CONFIG="${1#--cluster-config=}"
        if [[ -z "$CLUSTER_CONFIG" ]]; then
          echo "Missing value for --cluster-config." >&2
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

  [[ "$CONFIG_PATH" = /* ]] || CONFIG_PATH="$ROOT_DIR/$CONFIG_PATH"
  [[ "$CLUSTER_CONFIG" = /* ]] || CLUSTER_CONFIG="$ROOT_DIR/$CLUSTER_CONFIG"
}

prepare_outputs() {
  SWEEP_DATE="$(date +%d_%m_%Y)"
  SWEEP_NAME="$(date +%H_%M_%S)_quinta_update_replica_sweep"
  SWEEP_RESULTS_DIR="$ROOT_DIR/benchmarkSSE/results/data/$SWEEP_DATE/$SWEEP_NAME"
  SWEEP_PLOTS_DIR="$ROOT_DIR/benchmarkSSE/plots/$SWEEP_DATE/$SWEEP_NAME"
  SWEEP_LOG_DIR="$ROOT_DIR/benchmarkSSE/results/logs/$SWEEP_DATE/$SWEEP_NAME"
  SWEEP_SUMMARY_PATH="$SWEEP_RESULTS_DIR/update_latency_by_replicas_summary.tsv"
  SWEEP_LOG_PATH="$SWEEP_LOG_DIR/run_update_latency_by_replicas_quinta.log"

  mkdir -p "$SWEEP_RESULTS_DIR" "$SWEEP_PLOTS_DIR" "$SWEEP_LOG_DIR"
}

latest_summary_for_run() {
  local marker_path="$1"
  local replica_count="$2"
  local fault_count="$3"
  local summary_path

  summary_path="$(
    find "$ROOT_DIR/benchmarkSSE/results/data" \
      -maxdepth 3 \
      -type f \
      -name "update_latency_by_associations_summary.tsv" \
      -newer "$marker_path" \
      -path "*_quinta_update_assoc_r${replica_count}_f${fault_count}_db*/update_latency_by_associations_summary.tsv" \
      -print |
      sort |
      tail -n 1
  )"

  if [[ -z "$summary_path" ]]; then
    echo "Could not find update summary for replicaCount=$replica_count f=$fault_count." >&2
    exit 1
  fi

  printf '%s\n' "$summary_path"
}

run_one_benchmark() {
  local replica_count="$1"
  local fault_count marker_path summary_path

  fault_count="$(fault_count_for "$replica_count")"
  marker_path="$(mktemp "$ROOT_DIR/benchmarkSSE/results/data/.quinta_update_replica_sweep_marker.XXXXXX")"
  {
    echo
    echo "===== Running Quinta update latency benchmark with replicaCount=$replica_count f=$fault_count ====="
  } | tee -a "$SWEEP_LOG_PATH"

  if ! "$ASSOCIATIONS_RUNNER" --config "$CONFIG_PATH" --cluster-config "$CLUSTER_CONFIG" --replicas "$replica_count" 2>&1 | tee -a "$SWEEP_LOG_PATH"; then
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
  echo "Aggregating ${#SUMMARY_PATHS[@]} update summary file(s)..." | tee -a "$SWEEP_LOG_PATH"
  python3 "$AGGREGATE_SCRIPT" --output "$SWEEP_SUMMARY_PATH" "${SUMMARY_PATHS[@]}" 2>&1 | tee -a "$SWEEP_LOG_PATH"
}

generate_plots() {
  echo "Generating Quinta update replica sweep plot..." | tee -a "$SWEEP_LOG_PATH"
  gnuplot \
    -e "input_path='$SWEEP_SUMMARY_PATH'; output_dir='$SWEEP_PLOTS_DIR'; replica_values='${REPLICA_COUNTS[*]}'" \
    "$GNUPLOT_SCRIPT" 2>&1 | tee -a "$SWEEP_LOG_PATH"
}

verify_outputs() {
  if [[ ! -s "$SWEEP_SUMMARY_PATH" ]]; then
    echo "Aggregated update replica summary is missing or empty: $SWEEP_SUMMARY_PATH" >&2
    exit 1
  fi

  if [[ ! -s "$SWEEP_PLOTS_DIR/update_latency_by_replicas.png" ]]; then
    echo "Update replica plot is missing or empty: $SWEEP_PLOTS_DIR/update_latency_by_replicas.png" >&2
    exit 1
  fi
}

main() {
  parse_args "$@"
  parse_replica_list
  ensure_executable_exists "$ASSOCIATIONS_RUNNER"
  ensure_file_exists "$AGGREGATE_SCRIPT"
  ensure_file_exists "$GNUPLOT_SCRIPT"
  ensure_file_exists "$CONFIG_PATH"
  ensure_file_exists "$CLUSTER_CONFIG"
  ensure_command_exists python3
  ensure_command_exists gnuplot

  SUMMARY_PATHS=()

  prepare_outputs
  echo "Quinta update replica sweep: ${REPLICA_COUNTS[*]}" | tee -a "$SWEEP_LOG_PATH"
  echo "Config: $CONFIG_PATH" | tee -a "$SWEEP_LOG_PATH"
  echo "Cluster config: $CLUSTER_CONFIG" | tee -a "$SWEEP_LOG_PATH"

  local replica_count
  for replica_count in "${REPLICA_COUNTS[@]}"; do
    run_one_benchmark "$replica_count"
  done

  aggregate_results
  generate_plots
  verify_outputs

  echo "Quinta update replica sweep summary written to $SWEEP_SUMMARY_PATH"
  echo "Quinta update replica sweep plot written to $SWEEP_PLOTS_DIR"
}

main "$@"
