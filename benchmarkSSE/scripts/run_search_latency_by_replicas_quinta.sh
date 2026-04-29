#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEFAULT_CONFIG="$ROOT_DIR/benchmarkSSE/config/search_latency_by_docs.properties"
DEFAULT_CLUSTER_CONFIG="$ROOT_DIR/benchmarkSSE/config/quinta.env"
CONFIG_PATH="$DEFAULT_CONFIG"
CLUSTER_CONFIG="$DEFAULT_CLUSTER_CONFIG"
REPLICA_LIST="4,7,10"

DOCS_RUNNER="$ROOT_DIR/benchmarkSSE/scripts/run_search_latency_by_docs_quinta.sh"
AGGREGATE_SCRIPT="$ROOT_DIR/benchmarkSSE/scripts/aggregate_search_latency_by_replicas.py"
GNUPLOT_SCRIPT="$ROOT_DIR/benchmarkSSE/gnuplot/search_latency_by_replicas.gp"

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

ensure_file_exists() {
  local path="$1"
  if [[ ! -f "$path" ]]; then
    echo "Required file not found: $path" >&2
    exit 1
  fi
}

ensure_executable_exists() {
  local path="$1"
  if [[ ! -x "$path" ]]; then
    echo "Required executable not found: $path" >&2
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

get_property() {
  local key="$1"
  local value
  value="$(sed -n "s/^${key}=//p" "$CONFIG_PATH" | tail -n 1)"
  if [[ -z "$value" ]]; then
    echo "Missing property '$key' in $CONFIG_PATH" >&2
    exit 1
  fi
  printf '%s\n' "$value"
}

normalize_csv_list() {
  printf '%s' "$1" | tr -d '[:space:]'
}

doc_counts_to_buckets() {
  local raw_doc_counts="$1"
  local compact_doc_counts doc_count buckets=""

  compact_doc_counts="$(normalize_csv_list "$raw_doc_counts")"
  if [[ -z "$compact_doc_counts" ]]; then
    echo "syntheticDocCounts cannot be empty." >&2
    exit 1
  fi

  IFS=',' read -r -a DOC_COUNT_BUCKET_VALUES <<< "$compact_doc_counts"
  for doc_count in "${DOC_COUNT_BUCKET_VALUES[@]}"; do
    if ! [[ "$doc_count" =~ ^[0-9]+$ ]] || [[ "$doc_count" -lt 1 ]]; then
      echo "syntheticDocCounts values must be positive integers: $doc_count" >&2
      exit 1
    fi
    [[ -z "$buckets" ]] || buckets+=","
    buckets+="${doc_count}:${doc_count}"
  done

  printf '%s\n' "$buckets"
}

parse_replica_list() {
  local compact_list replica_count

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

load_bucket_config() {
  local bucket dataset_type

  dataset_type="$(get_property datasetType)"
  case "$dataset_type" in
    enron)
      BUCKET_LIST="$(get_property processorBuckets)"
      ;;
    synthetic)
      BUCKET_LIST="$(doc_counts_to_buckets "$(get_property syntheticDocCounts)")"
      ;;
    *)
      echo "datasetType must be either 'enron' or 'synthetic'." >&2
      exit 1
      ;;
  esac
  BUCKET_SPECS="${BUCKET_LIST//,/ }"
  BUCKET_LABELS=""

  for bucket in $BUCKET_SPECS; do
    [[ -z "$BUCKET_LABELS" ]] || BUCKET_LABELS+=" "
    BUCKET_LABELS+="${bucket/:/-}"
  done
}

fault_count_for() {
  local replica_count="$1"
  printf '%s\n' $(((replica_count - 1) / 3))
}

prepare_outputs() {
  SWEEP_DATE="$(date +%d_%m_%Y)"
  SWEEP_NAME="$(date +%H_%M_%S)_quinta_replica_sweep"
  SWEEP_RESULTS_DIR="$ROOT_DIR/benchmarkSSE/results/data/$SWEEP_DATE/$SWEEP_NAME"
  SWEEP_PLOTS_DIR="$ROOT_DIR/benchmarkSSE/plots/$SWEEP_DATE/$SWEEP_NAME"
  SWEEP_LOG_DIR="$ROOT_DIR/benchmarkSSE/results/logs/$SWEEP_DATE/$SWEEP_NAME"
  SWEEP_SUMMARY_PATH="$SWEEP_RESULTS_DIR/search_latency_by_replicas_summary.tsv"
  SWEEP_LOG_PATH="$SWEEP_LOG_DIR/run_search_latency_by_replicas_quinta.log"

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
  marker_path="$(mktemp "$ROOT_DIR/benchmarkSSE/results/data/.quinta_replica_sweep_marker.XXXXXX")"
  {
    echo
    echo "===== Running Quinta search latency benchmark with replicaCount=$replica_count f=$fault_count ====="
  } | tee -a "$SWEEP_LOG_PATH"

  if ! "$DOCS_RUNNER" --config "$CONFIG_PATH" --cluster-config "$CLUSTER_CONFIG" --replicas "$replica_count" 2>&1 | tee -a "$SWEEP_LOG_PATH"; then
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
  echo "Generating Quinta replica sweep plots..." | tee -a "$SWEEP_LOG_PATH"
  gnuplot \
    -e "input_path='$SWEEP_SUMMARY_PATH'; output_dir='$SWEEP_PLOTS_DIR'; bucket_specs='$BUCKET_SPECS'; bucket_labels='$BUCKET_LABELS'" \
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
  ensure_executable_exists "$DOCS_RUNNER"
  ensure_file_exists "$AGGREGATE_SCRIPT"
  ensure_file_exists "$GNUPLOT_SCRIPT"
  ensure_file_exists "$CONFIG_PATH"
  ensure_file_exists "$CLUSTER_CONFIG"
  ensure_command_exists python3
  ensure_command_exists gnuplot
  load_bucket_config

  SUMMARY_PATHS=()

  prepare_outputs
  echo "Quinta replica sweep: ${REPLICA_COUNTS[*]}" | tee -a "$SWEEP_LOG_PATH"
  echo "Config: $CONFIG_PATH" | tee -a "$SWEEP_LOG_PATH"
  echo "Cluster config: $CLUSTER_CONFIG" | tee -a "$SWEEP_LOG_PATH"
  echo "Buckets: $BUCKET_LIST" | tee -a "$SWEEP_LOG_PATH"

  local replica_count
  for replica_count in "${REPLICA_COUNTS[@]}"; do
    run_one_benchmark "$replica_count"
  done

  aggregate_results
  generate_plots
  verify_outputs

  echo "Quinta replica sweep summary written to $SWEEP_SUMMARY_PATH"
  echo "Quinta replica sweep plots written to $SWEEP_PLOTS_DIR"
}

main "$@"
