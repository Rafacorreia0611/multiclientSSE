#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
DEFAULT_CONFIG="$ROOT_DIR/benchmarkSSE/config/update/update_latency_by_associations.properties"
DEFAULT_CLUSTER_CONFIG="$ROOT_DIR/benchmarkSSE/config/quinta.env"
DEPLOY_SCRIPT="$ROOT_DIR/benchmarkSSE/scripts/quinta_deploy.sh"
COMMON_SCRIPT="$ROOT_DIR/benchmarkSSE/scripts/common/quinta_benchmark_common.sh"

CONFIG_PATH="$DEFAULT_CONFIG"
CLUSTER_CONFIG="$DEFAULT_CLUSTER_CONFIG"
REPLICA_COUNT_OVERRIDE=""
REPLICA_COUNT_SOURCE="config"
CLEANUP_DONE=0
CURRENT_RUN_ACTIVE=0
LOG_CAPTURE_STARTED=0

# shellcheck source=../common/quinta_benchmark_common.sh
source "$COMMON_SCRIPT"

usage() {
  cat >&2 <<EOF
Usage: $(basename "$0") [--config PATH] [--cluster-config PATH] [--replicas N]
EOF
}

parse_args() {
  while [[ "$#" -gt 0 ]]; do
    case "$1" in
      --config)
        [[ "$#" -ge 2 ]] || die "Missing value for --config"
        CONFIG_PATH="$2"
        shift 2
        ;;
      --config=*)
        CONFIG_PATH="${1#--config=}"
        [[ -n "$CONFIG_PATH" ]] || die "Missing value for --config"
        shift
        ;;
      --cluster-config)
        [[ "$#" -ge 2 ]] || die "Missing value for --cluster-config"
        CLUSTER_CONFIG="$2"
        shift 2
        ;;
      --cluster-config=*)
        CLUSTER_CONFIG="${1#--cluster-config=}"
        [[ -n "$CLUSTER_CONFIG" ]] || die "Missing value for --cluster-config"
        shift
        ;;
      --replicas)
        [[ "$#" -ge 2 ]] || die "Missing value for --replicas"
        REPLICA_COUNT_OVERRIDE="$2"
        shift 2
        ;;
      --replicas=*)
        REPLICA_COUNT_OVERRIDE="${1#--replicas=}"
        [[ -n "$REPLICA_COUNT_OVERRIDE" ]] || die "Missing value for --replicas"
        shift
        ;;
      --help|-h)
        usage
        exit 0
        ;;
      *)
        die "Unknown argument: $1"
        ;;
    esac
  done

  [[ "$CONFIG_PATH" = /* ]] || CONFIG_PATH="$ROOT_DIR/$CONFIG_PATH"
  [[ "$CLUSTER_CONFIG" = /* ]] || CLUSTER_CONFIG="$ROOT_DIR/$CLUSTER_CONFIG"
  CONFIG_DIR="$(cd "$(dirname "$CONFIG_PATH")" && pwd)"
}

load_config() {
  [[ -f "$CONFIG_PATH" ]] || die "Config file not found: $CONFIG_PATH"
  [[ -f "$CLUSTER_CONFIG" ]] || die "Cluster config not found: $CLUSTER_CONFIG"
  [[ -x "$DEPLOY_SCRIPT" ]] || die "Deploy script is not executable: $DEPLOY_SCRIPT"
  # shellcheck source=/dev/null
  source "$CLUSTER_CONFIG"

  BASE_DATASET_OUTPUT_PATH="$(get_property baseDatasetOutputPath)"
  TARGET_DB_ASSOCIATION_COUNT="$(get_property targetDbAssociationCount)"
  BASE_SYNTHETIC_DOC_COUNTS="$(get_property baseSyntheticDocCounts)"
  BASE_SYNTHETIC_SEED="$(get_property baseSyntheticSeed)"

  PAYLOAD_OUTPUT_PATH="$(get_property payloadOutputPath)"
  MEASURE_KEYWORD_COUNTS="$(normalize_csv_list "$(get_property measureKeywordCounts)")"
  MEASURE_DOC_IDS_PER_KEYWORD="$(get_property measureDocIdsPerKeyword)"
  WARMUP_PAYLOAD_COUNT="$(get_property warmupPayloadCount)"
  WARMUP_ASSOCIATIONS_PER_PAYLOAD="$(get_property warmupAssociationsPerPayload)"
  WARMUP_KEYWORDS_PER_PAYLOAD="$(get_property warmupKeywordsPerPayload)"
  PAYLOAD_SEED="$(get_property payloadSeed)"

  REPLICA_COUNT="$(get_property replicaCount)"
  if [[ -n "$REPLICA_COUNT_OVERRIDE" ]]; then
    REPLICA_COUNT="$REPLICA_COUNT_OVERRIDE"
    REPLICA_COUNT_SOURCE="CLI override"
  fi
  DEPLOY_CLIENT_COUNT="$(get_property deployClientCount)"
  POPULATE_CLIENT_DIR="$(get_property populateClientDir)"
  POPULATE_CLIENT_ID="$(get_property populateClientId)"
  POPULATE_BATCH_SIZE="$(get_property populateBatchSize)"
  BENCHMARK_CLIENT_DIR="$(get_property benchmarkClientDir)"
  SCENARIO="$(get_property scenario)"
  CLIENT_ID="$(get_property clientId)"
  TIMESTAMP_OUTPUTS="$(get_property timestampOutputs)"
  INPUT_PATH="$(get_property inputPath)"
  OUTPUT_PATH="$(get_property outputPath)"
  AGGREGATE_OUTPUT_PATH="$(get_property aggregateOutputPath)"

  ABS_SYNTHETIC_DATASET_SCRIPT="$ROOT_DIR/benchmarkSSE/scripts/datasets/syntheticDataset.py"
  ABS_UPDATE_PAYLOAD_SCRIPT="$ROOT_DIR/benchmarkSSE/scripts/update/generate_update_payloads.py"
  ABS_AGGREGATE_SCRIPT="$ROOT_DIR/benchmarkSSE/scripts/update/aggregate_update_latency_by_associations.py"
  ABS_BASE_DATASET_PATH="$(resolve_config_path "$BASE_DATASET_OUTPUT_PATH")"
  ABS_PAYLOAD_PATH="$(resolve_config_path "$PAYLOAD_OUTPUT_PATH")"
  ABS_INPUT_PATH="$(resolve_config_path "$INPUT_PATH")"
  BASE_OUTPUT_PATH="$(resolve_config_path "$OUTPUT_PATH")"
  BASE_AGGREGATE_OUTPUT_PATH="$(resolve_config_path "$AGGREGATE_OUTPUT_PATH")"

  validate_config
  calculate_fault_count
}

validate_config() {
  [[ "$SCENARIO" == "update-latency-by-associations" ]] || die "scenario must be update-latency-by-associations"

  validate_positive_integer "$TARGET_DB_ASSOCIATION_COUNT" "targetDbAssociationCount"
  validate_integer "$BASE_SYNTHETIC_SEED" "baseSyntheticSeed"
  validate_positive_integer "$REPLICA_COUNT" "replicaCount"
  [[ "$REPLICA_COUNT" -ge 4 ]] || die "replicaCount must be at least 4"
  validate_positive_integer "$DEPLOY_CLIENT_COUNT" "deployClientCount"
  validate_positive_integer "$POPULATE_CLIENT_ID" "populateClientId"
  validate_positive_integer "$POPULATE_BATCH_SIZE" "populateBatchSize"
  validate_positive_integer "$CLIENT_ID" "clientId"
  validate_integer "$WARMUP_PAYLOAD_COUNT" "warmupPayloadCount"
  [[ "$WARMUP_PAYLOAD_COUNT" -ge 0 ]] || die "warmupPayloadCount must be non-negative"
  validate_positive_integer "$WARMUP_ASSOCIATIONS_PER_PAYLOAD" "warmupAssociationsPerPayload"
  validate_positive_integer "$WARMUP_KEYWORDS_PER_PAYLOAD" "warmupKeywordsPerPayload"
  validate_integer "$PAYLOAD_SEED" "payloadSeed"
  validate_boolean "$TIMESTAMP_OUTPUTS" "timestampOutputs"
  validate_client_dir "$POPULATE_CLIENT_DIR" "populateClientDir"
  validate_client_dir "$BENCHMARK_CLIENT_DIR" "benchmarkClientDir"
  [[ -f "$ABS_SYNTHETIC_DATASET_SCRIPT" ]] || die "Synthetic dataset script not found: $ABS_SYNTHETIC_DATASET_SCRIPT"
  [[ -f "$ABS_UPDATE_PAYLOAD_SCRIPT" ]] || die "Update payload script not found: $ABS_UPDATE_PAYLOAD_SCRIPT"
  [[ -f "$ABS_AGGREGATE_SCRIPT" ]] || die "Update aggregate script not found: $ABS_AGGREGATE_SCRIPT"
  validate_measure_payload_config

  POPULATE_CLIENT_INDEX="${POPULATE_CLIENT_DIR#cli}"
  BENCHMARK_CLIENT_INDEX="${BENCHMARK_CLIENT_DIR#cli}"
  [[ "$ABS_INPUT_PATH" == "$ABS_PAYLOAD_PATH" ]] || die "inputPath must match payloadOutputPath"

  BASE_SYNTHETIC_DOC_COUNTS="$(normalize_csv_list "$BASE_SYNTHETIC_DOC_COUNTS")"
  BASE_SYNTHETIC_DOC_COUNT_TOTAL="$(sum_csv_list "$BASE_SYNTHETIC_DOC_COUNTS")"
  BASE_SYNTHETIC_DOC_COUNT_ITEMS="$(csv_item_count "$BASE_SYNTHETIC_DOC_COUNTS")"
  WARMUP_TOTAL_ASSOCIATIONS=$((WARMUP_PAYLOAD_COUNT * WARMUP_ASSOCIATIONS_PER_PAYLOAD))
  POPULATE_DB_ASSOCIATION_COUNT=$((TARGET_DB_ASSOCIATION_COUNT - WARMUP_TOTAL_ASSOCIATIONS))
  [[ "$POPULATE_DB_ASSOCIATION_COUNT" -gt 0 ]] || die "targetDbAssociationCount must be greater than warmupPayloadCount * warmupAssociationsPerPayload"
  [[ $((POPULATE_DB_ASSOCIATION_COUNT % BASE_SYNTHETIC_DOC_COUNT_TOTAL)) -eq 0 ]] || die "targetDbAssociationCount minus warmup associations must be divisible by sum(baseSyntheticDocCounts)"
  POPULATE_SYNTHETIC_KEYWORDS_PER_DOC_COUNT=$((POPULATE_DB_ASSOCIATION_COUNT / BASE_SYNTHETIC_DOC_COUNT_TOTAL))
  EXPECTED_BASE_DATASET_LINES=$((POPULATE_SYNTHETIC_KEYWORDS_PER_DOC_COUNT * BASE_SYNTHETIC_DOC_COUNT_ITEMS))

  if [[ "$WARMUP_PAYLOAD_COUNT" -gt 0 && "$WARMUP_KEYWORDS_PER_PAYLOAD" -gt "$WARMUP_ASSOCIATIONS_PER_PAYLOAD" ]]; then
    die "warmupKeywordsPerPayload cannot be greater than warmupAssociationsPerPayload when warmupPayloadCount > 0"
  fi

}

append_measure_value() {
  local measure_associations="$1"
  local measure_keywords="$2"

  MEASURE_ASSOCIATION_VALUES+=("$measure_associations")
  MEASURE_KEYWORD_VALUES+=("$measure_keywords")
}

validate_measure_payload_config() {
  local measure_keyword_count measure_associations

  MEASURE_ASSOCIATION_VALUES=()
  MEASURE_KEYWORD_VALUES=()
  MEASURE_ASSOCIATION_COUNTS=""

  [[ -n "$MEASURE_KEYWORD_COUNTS" ]] || die "measureKeywordCounts cannot be empty"
  validate_positive_integer "$MEASURE_DOC_IDS_PER_KEYWORD" "measureDocIdsPerKeyword"

  IFS=',' read -r -a RAW_MEASURE_KEYWORD_VALUES <<< "$MEASURE_KEYWORD_COUNTS"
  for measure_keyword_count in "${RAW_MEASURE_KEYWORD_VALUES[@]}"; do
    validate_positive_integer "$measure_keyword_count" "measureKeywordCounts"
    measure_associations=$((measure_keyword_count * MEASURE_DOC_IDS_PER_KEYWORD))
    append_measure_value "$measure_associations" "$measure_keyword_count"
  done
  MEASURE_ASSOCIATION_COUNTS="$(join_csv "${MEASURE_ASSOCIATION_VALUES[@]}")"
}

join_csv() {
  local output=""
  local value

  for value in "$@"; do
    [[ -z "$output" ]] || output+=","
    output+="$value"
  done

  printf '%s\n' "$output"
}

prepare_aggregate_paths() {
  if [[ "$TIMESTAMP_OUTPUTS" == "true" ]]; then
    GROUP_DATE="$(date +%d_%m_%Y)"
    GROUP_NAME="$(date +%H_%M_%S)_quinta_update_assoc_r${REPLICA_COUNT}_f${FAULT_COUNT}_db${TARGET_DB_ASSOCIATION_COUNT}"
  else
    GROUP_DATE="$(date +%d_%m_%Y)"
    GROUP_NAME="latest_quinta_update_assoc_r${REPLICA_COUNT}_f${FAULT_COUNT}_db${TARGET_DB_ASSOCIATION_COUNT}"
  fi

  ABS_AGGREGATE_OUTPUT_PATH="$(place_path_in_run_dir "$BASE_AGGREGATE_OUTPUT_PATH" "$GROUP_DATE/$GROUP_NAME")"
  AGGREGATE_LOG_DIR="$ROOT_DIR/benchmarkSSE/results/logs/$GROUP_DATE/$GROUP_NAME"
  AGGREGATE_LOG_PATH="$AGGREGATE_LOG_DIR/run_update_latency_by_associations_quinta.log"

  mkdir -p "$AGGREGATE_LOG_DIR" "$(dirname "$ABS_AGGREGATE_OUTPUT_PATH")"
  {
    printf 'Update latency Quinta run group: %s\n' "$GROUP_NAME"
    printf 'Config: %s\n' "$CONFIG_PATH"
    printf 'Cluster config: %s\n' "$CLUSTER_CONFIG"
    printf 'Replica count: %s\n' "$REPLICA_COUNT"
    printf 'Fault count: %s\n' "$FAULT_COUNT"
    printf 'Target DB associations: %s\n' "$TARGET_DB_ASSOCIATION_COUNT"
    printf 'Measure keyword counts: %s\n' "$MEASURE_KEYWORD_COUNTS"
    printf 'Measure doc IDs per keyword: %s\n' "$MEASURE_DOC_IDS_PER_KEYWORD"
    printf 'Measure association counts: %s\n' "$MEASURE_ASSOCIATION_COUNTS"
  } > "$AGGREGATE_LOG_PATH"
}

start_group_log_capture() {
  [[ "$LOG_CAPTURE_STARTED" -eq 0 ]] || return
  LOG_CAPTURE_STARTED=1
  exec > >(tee -a "$AGGREGATE_LOG_PATH") 2>&1
}

validate_existing_base_dataset() {
  [[ -s "$ABS_BASE_DATASET_PATH" ]] || return 1
  [[ "$(wc -l < "$ABS_BASE_DATASET_PATH")" -eq "$EXPECTED_BASE_DATASET_LINES" ]]
}

generate_base_dataset() {
  if validate_existing_base_dataset; then
    echo "Reusing base DB dataset: $ABS_BASE_DATASET_PATH"
    return
  fi

  echo "Generating synthetic base DB dataset..."
  python3 "$ABS_SYNTHETIC_DATASET_SCRIPT" \
    --output "$ABS_BASE_DATASET_PATH" \
    --keywords-per-doc-count "$POPULATE_SYNTHETIC_KEYWORDS_PER_DOC_COUNT" \
    --doc-counts "$BASE_SYNTHETIC_DOC_COUNTS" \
    --seed "$BASE_SYNTHETIC_SEED"

  validate_existing_base_dataset || die "Generated base DB dataset line count did not match config"
}

prepare_run_paths() {
  local measure_associations="$1"
  local measure_keywords="$2"

  if [[ "$TIMESTAMP_OUTPUTS" == "true" ]]; then
    RUN_DATE="$(date +%d_%m_%Y)"
    RUN_NAME="$(date +%H_%M_%S)_quinta_r${REPLICA_COUNT}_f${FAULT_COUNT}_db${TARGET_DB_ASSOCIATION_COUNT}_upd${measure_associations}_kw${measure_keywords}"
  else
    RUN_DATE="$(date +%d_%m_%Y)"
    RUN_NAME="latest_quinta_r${REPLICA_COUNT}_f${FAULT_COUNT}_db${TARGET_DB_ASSOCIATION_COUNT}_upd${measure_associations}_kw${measure_keywords}"
  fi

  ABS_OUTPUT_PATH="$(place_path_in_run_dir "$BASE_OUTPUT_PATH" "$RUN_DATE/$RUN_NAME")"
  ABS_LOG_DIR="$ROOT_DIR/benchmarkSSE/results/logs/$RUN_DATE/$RUN_NAME"
  LOCAL_RUNTIME_CONFIG_PATH="$ABS_LOG_DIR/runtime_update_latency_by_associations_quinta.properties"

  mkdir -p "$ABS_LOG_DIR" "$(dirname "$ABS_OUTPUT_PATH")"
}

generate_update_payload() {
  local measure_associations="$1"
  local measure_keywords="$2"

  echo "Generating update payload with $measure_associations associations across $measure_keywords keyword(s)..."
  python3 "$ABS_UPDATE_PAYLOAD_SCRIPT" \
    --output "$ABS_PAYLOAD_PATH" \
    --warmup-count "$WARMUP_PAYLOAD_COUNT" \
    --warmup-associations "$WARMUP_ASSOCIATIONS_PER_PAYLOAD" \
    --warmup-keywords-per-payload "$WARMUP_KEYWORDS_PER_PAYLOAD" \
    --measure-associations "$measure_associations" \
    --measure-keywords-per-payload "$measure_keywords" \
    --seed "$PAYLOAD_SEED"

  [[ -s "$ABS_PAYLOAD_PATH" ]] || die "Generated update payload is empty: $ABS_PAYLOAD_PATH"
  [[ "$(wc -l < "$ABS_PAYLOAD_PATH")" -eq "$((WARMUP_PAYLOAD_COUNT + 1))" ]] || die "Generated update payload line count did not match config"
}

write_local_runtime_config() {
  local remote_payload_path="$1"
  local remote_output_path="$2"
  local remote_base_dataset_path="$3"
  local measure_associations="$4"

  {
    printf '# Runtime benchmark config generated by %s\n' "$(basename "$0")"
    printf 'runtimeReplicaCount=%s\n' "$REPLICA_COUNT"
    printf 'runtimeFaultCount=%s\n' "$FAULT_COUNT"
    printf 'runtimeReplicaCountSource=%s\n' "$REPLICA_COUNT_SOURCE"
    printf 'runtimeTargetDbAssociationCount=%s\n' "$TARGET_DB_ASSOCIATION_COUNT"
    printf 'runtimePopulateDbAssociationCount=%s\n' "$POPULATE_DB_ASSOCIATION_COUNT"
    printf 'runtimeWarmupAssociationCount=%s\n' "$WARMUP_TOTAL_ASSOCIATIONS"
    printf 'runtimeMeasureAssociations=%s\n' "$measure_associations"
    printf 'runtimeMeasureKeywordCount=%s\n' "$MEASURE_KEYWORDS"
    printf 'runtimeMeasureDocIdsPerKeyword=%s\n' "$MEASURE_DOC_IDS_PER_KEYWORD"
    while IFS= read -r line; do
      case "$line" in
        baseDatasetOutputPath=*) printf 'baseDatasetOutputPath=%s\n' "$remote_base_dataset_path" ;;
        payloadOutputPath=*) printf 'payloadOutputPath=%s\n' "$remote_payload_path" ;;
        measureKeywordCounts=*) printf 'measureKeywordCounts=%s\n' "$MEASURE_KEYWORDS" ;;
        inputPath=*) printf 'inputPath=%s\n' "$remote_payload_path" ;;
        outputPath=*) printf 'outputPath=%s\n' "$remote_output_path" ;;
        replicaCount=*) printf 'replicaCount=%s\n' "$REPLICA_COUNT" ;;
        *) printf '%s\n' "$line" ;;
      esac
    done < "$CONFIG_PATH"
  } > "$LOCAL_RUNTIME_CONFIG_PATH"
}

copy_benchmark_inputs() {
  local populate_host benchmark_host remote_base

  populate_host="$(client_node "$POPULATE_CLIENT_INDEX")"
  benchmark_host="$(client_node "$BENCHMARK_CLIENT_INDEX")"
  remote_base="$(remote_run_dir)/benchmark"
  REMOTE_BASE_DATASET_PATH="$remote_base/input/$(basename "$ABS_BASE_DATASET_PATH")"
  REMOTE_PAYLOAD_PATH="$remote_base/input/$(basename "$ABS_PAYLOAD_PATH")"
  REMOTE_RUNTIME_CONFIG_PATH="$remote_base/runtime_update_latency_by_associations.properties"
  REMOTE_OUTPUT_PATH="$remote_base/results/update_latency_by_associations.csv"

  write_local_runtime_config "$REMOTE_PAYLOAD_PATH" "$REMOTE_OUTPUT_PATH" "$REMOTE_BASE_DATASET_PATH" "$MEASURE_ASSOCIATIONS"

  ssh_remote "$populate_host" "mkdir -p '$remote_base/input' '$remote_base/logs'"
  scp_remote "$ABS_BASE_DATASET_PATH" "$populate_host:$REMOTE_BASE_DATASET_PATH"

  ssh_remote "$benchmark_host" "mkdir -p '$remote_base/input' '$remote_base/results' '$remote_base/logs'"
  scp_remote "$ABS_PAYLOAD_PATH" "$benchmark_host:$REMOTE_PAYLOAD_PATH"
  scp_remote "$LOCAL_RUNTIME_CONFIG_PATH" "$benchmark_host:$REMOTE_RUNTIME_CONFIG_PATH"
}

run_populate_remote() {
  local host
  host="$(client_node "$POPULATE_CLIENT_INDEX")"
  echo "Running PopulateDB on $host..."
  ssh_remote "$host" "cd '$(remote_run_dir)/$POPULATE_CLIENT_DIR' && env JAVA_OPTS='$QUINTA_CLIENT_JAVA_OPTS' bash smartrun.sh sse.populatedb.PopulateDB --client-id '$POPULATE_CLIENT_ID' --input '$REMOTE_BASE_DATASET_PATH' --batch-size '$POPULATE_BATCH_SIZE'" \
    > "$ABS_LOG_DIR/populate.log" 2>&1
  grep -q "PopulateDB finished." "$ABS_LOG_DIR/populate.log" || {
    tail -n 40 "$ABS_LOG_DIR/populate.log" >&2 || true
    die "PopulateDB did not finish successfully"
  }
}

run_benchmark_remote() {
  local host
  host="$(client_node "$BENCHMARK_CLIENT_INDEX")"
  echo "Running BenchmarkClient on $host..."
  ssh_remote "$host" "cd '$(remote_run_dir)/$BENCHMARK_CLIENT_DIR' && env JAVA_OPTS='$QUINTA_CLIENT_JAVA_OPTS' bash smartrun.sh sse.benchmark.BenchmarkClient '$REMOTE_RUNTIME_CONFIG_PATH'" \
    > "$ABS_LOG_DIR/benchmark.log" 2>&1
}

fetch_results() {
  local benchmark_host node replica_id node_index remote_log
  benchmark_host="$(client_node "$BENCHMARK_CLIENT_INDEX")"
  scp_remote "$benchmark_host:$REMOTE_OUTPUT_PATH" "$ABS_OUTPUT_PATH"

  for (( replica_id = 0; replica_id < REPLICA_COUNT; replica_id++ )); do
    node_index=$((replica_id % ${#QUINTA_NODES[@]}))
    node="${QUINTA_NODES[$node_index]}"
    remote_log="$(remote_run_dir)/rep${replica_id}/server.log"
    scp_remote "${QUINTA_SSH_PREFIX}${node}:$remote_log" "$ABS_LOG_DIR/rep${replica_id}.log" || true
  done
}

verify_results() {
  local header line_count scenario operation run phase associations keyword_count doc_id_count

  [[ -s "$ABS_OUTPUT_PATH" ]] || die "Benchmark CSV not found or empty: $ABS_OUTPUT_PATH"
  header="$(head -n 1 "$ABS_OUTPUT_PATH" | tr -d '\r')"
  [[ "$header" == "scenario,operation,run,phase,associations_per_update,keyword_count,doc_id_count,payload_id,latency_ns" ]] || die "Unexpected CSV header"

  line_count="$(wc -l < "$ABS_OUTPUT_PATH")"
  [[ "$line_count" -eq 2 ]] || die "Update benchmark CSV must contain exactly one measured row"

  scenario="$(awk -F',' 'NR==2 {print $1}' "$ABS_OUTPUT_PATH")"
  operation="$(awk -F',' 'NR==2 {print $2}' "$ABS_OUTPUT_PATH")"
  run="$(awk -F',' 'NR==2 {print $3}' "$ABS_OUTPUT_PATH")"
  phase="$(awk -F',' 'NR==2 {print $4}' "$ABS_OUTPUT_PATH")"
  associations="$(awk -F',' 'NR==2 {print $5}' "$ABS_OUTPUT_PATH")"
  keyword_count="$(awk -F',' 'NR==2 {print $6}' "$ABS_OUTPUT_PATH")"
  doc_id_count="$(awk -F',' 'NR==2 {print $7}' "$ABS_OUTPUT_PATH")"

  [[ "$scenario" == "update-latency-by-associations" ]] || die "Unexpected scenario in CSV: $scenario"
  [[ "$operation" == "UPDATE" ]] || die "Unexpected operation in CSV: $operation"
  [[ "$run" == "1" ]] || die "Unexpected run in CSV: $run"
  [[ "$phase" == "measure" ]] || die "Unexpected phase in CSV: $phase"
  [[ "$associations" == "$MEASURE_ASSOCIATIONS" ]] || die "CSV associations_per_update $associations did not match $MEASURE_ASSOCIATIONS"
  [[ "$keyword_count" == "$MEASURE_KEYWORDS" ]] || die "CSV keyword_count $keyword_count did not match $MEASURE_KEYWORDS"
  [[ "$doc_id_count" == "$MEASURE_ASSOCIATIONS" ]] || die "CSV doc_id_count $doc_id_count did not match $MEASURE_ASSOCIATIONS"
}

stop_current_run() {
  if [[ "$CURRENT_RUN_ACTIVE" -eq 1 && "${RUN_NAME:-}" != "" ]]; then
    deploy_arg stop || true
    deploy_arg clean || true
    CURRENT_RUN_ACTIVE=0
  fi
}

cleanup() {
  local exit_status=$?
  trap - EXIT INT TERM
  if [[ "$CLEANUP_DONE" -eq 0 ]]; then
    CLEANUP_DONE=1
    stop_current_run
  fi
  exit "$exit_status"
}

run_one_measurement() {
  MEASURE_ASSOCIATIONS="$1"
  MEASURE_KEYWORDS="$2"

  echo
  echo "===== Running update latency benchmark with $MEASURE_ASSOCIATIONS associations/update across $MEASURE_KEYWORDS keyword(s) ====="
  prepare_run_paths "$MEASURE_ASSOCIATIONS" "$MEASURE_KEYWORDS"
  generate_update_payload "$MEASURE_ASSOCIATIONS" "$MEASURE_KEYWORDS"

  CURRENT_RUN_ACTIVE=1
  deploy_arg clean
  deploy_arg preflight
  deploy_arg deploy
  deploy_arg start
  deploy_arg wait
  copy_benchmark_inputs
  run_populate_remote
  run_benchmark_remote
  fetch_results
  verify_results
  UPDATE_CSV_PATHS+=("$ABS_OUTPUT_PATH")
  stop_current_run

  echo "Benchmark CSV: $ABS_OUTPUT_PATH"
}

aggregate_results() {
  [[ "${#UPDATE_CSV_PATHS[@]}" -gt 0 ]] || die "No update CSV paths were collected"

  echo "Aggregating ${#UPDATE_CSV_PATHS[@]} update latency CSV file(s)..."
  {
    printf '\nAggregating CSV files:\n'
    printf '  %s\n' "${UPDATE_CSV_PATHS[@]}"
  } >> "$AGGREGATE_LOG_PATH"

  python3 "$ABS_AGGREGATE_SCRIPT" --output "$ABS_AGGREGATE_OUTPUT_PATH" "${UPDATE_CSV_PATHS[@]}"
}

verify_aggregate_results() {
  local line_count expected_line_count

  [[ -s "$ABS_AGGREGATE_OUTPUT_PATH" ]] || die "Aggregated TSV not found or empty: $ABS_AGGREGATE_OUTPUT_PATH"
  expected_line_count=$((${#UPDATE_CSV_PATHS[@]} + 1))
  line_count="$(wc -l < "$ABS_AGGREGATE_OUTPUT_PATH")"
  [[ "$line_count" -ge "$expected_line_count" ]] || die "Aggregated TSV has $line_count lines, expected at least $expected_line_count"
}

main() {
  parse_args "$@"
  load_config
  trap cleanup EXIT INT TERM

  UPDATE_CSV_PATHS=()
  prepare_aggregate_paths
  start_group_log_capture
  generate_base_dataset
  for (( measure_index = 0; measure_index < ${#MEASURE_ASSOCIATION_VALUES[@]}; measure_index++ )); do
    run_one_measurement "${MEASURE_ASSOCIATION_VALUES[$measure_index]}" "${MEASURE_KEYWORD_VALUES[$measure_index]}"
  done
  aggregate_results
  verify_aggregate_results

  echo "Aggregated TSV: $ABS_AGGREGATE_OUTPUT_PATH"
}

main "$@"
