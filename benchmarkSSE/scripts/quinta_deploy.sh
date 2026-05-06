#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEFAULT_CLUSTER_CONFIG="$ROOT_DIR/benchmarkSSE/config/quinta.env"

COMMAND=""
CLUSTER_CONFIG="$DEFAULT_CLUSTER_CONFIG"
REPLICA_COUNT=10
CLIENT_COUNT=1
RUN_NAME="latest"

usage() {
  cat >&2 <<EOF
Usage: $(basename "$0") <command> [options]

Commands:
  layout      Show replica/client placement.
  preflight   Check SSH, Java, IPs, disk, and relevant ports.
  deploy      Build local deploy, configure it, and copy it to Quinta.
  start       Start remote replicas.
  wait        Wait for all replicas to become ready.
  status      Show remote process status and log tails.
  stop        Stop remote replicas for the run.
  clean       Remove the remote run directory.
  preclean    Stop stale Quinta benchmark processes, free run ports, and remove the remote run directory.

Options:
  --cluster-config PATH  Default: benchmarkSSE/config/quinta.env
  --replicas N           Default: 10
  --clients N            Default: 1
  --run-name NAME        Default: latest
EOF
}

die() {
  echo "Error: $*" >&2
  exit 1
}

ssh_remote() {
  ssh -n \
    -o BatchMode=yes \
    -o ConnectTimeout="${QUINTA_SSH_CONNECT_TIMEOUT_SECONDS:-10}" \
    -o ServerAliveInterval="${QUINTA_SSH_SERVER_ALIVE_INTERVAL_SECONDS:-5}" \
    -o ServerAliveCountMax="${QUINTA_SSH_SERVER_ALIVE_COUNT_MAX:-2}" \
    "$@"
}

rsync_remote() {
  rsync -az --delete \
    -e "ssh -o BatchMode=yes -o ConnectTimeout=${QUINTA_SSH_CONNECT_TIMEOUT_SECONDS:-10} -o ServerAliveInterval=${QUINTA_SSH_SERVER_ALIVE_INTERVAL_SECONDS:-5} -o ServerAliveCountMax=${QUINTA_SSH_SERVER_ALIVE_COUNT_MAX:-2}" \
    "$@"
}

parse_args() {
  if [[ "$#" -lt 1 ]]; then
    usage
    exit 1
  fi

  COMMAND="$1"
  shift

  while [[ "$#" -gt 0 ]]; do
    case "$1" in
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
        REPLICA_COUNT="$2"
        shift 2
        ;;
      --replicas=*)
        REPLICA_COUNT="${1#--replicas=}"
        [[ -n "$REPLICA_COUNT" ]] || die "Missing value for --replicas"
        shift
        ;;
      --clients)
        [[ "$#" -ge 2 ]] || die "Missing value for --clients"
        CLIENT_COUNT="$2"
        shift 2
        ;;
      --clients=*)
        CLIENT_COUNT="${1#--clients=}"
        [[ -n "$CLIENT_COUNT" ]] || die "Missing value for --clients"
        shift
        ;;
      --run-name)
        [[ "$#" -ge 2 ]] || die "Missing value for --run-name"
        RUN_NAME="$2"
        shift 2
        ;;
      --run-name=*)
        RUN_NAME="${1#--run-name=}"
        [[ -n "$RUN_NAME" ]] || die "Missing value for --run-name"
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
}

load_cluster_config() {
  if [[ "$CLUSTER_CONFIG" != /* ]]; then
    CLUSTER_CONFIG="$ROOT_DIR/$CLUSTER_CONFIG"
  fi
  [[ -f "$CLUSTER_CONFIG" ]] || die "Cluster config not found: $CLUSTER_CONFIG"
  # shellcheck source=/dev/null
  source "$CLUSTER_CONFIG"

  [[ "${#QUINTA_NODES[@]}" -gt 0 ]] || die "QUINTA_NODES cannot be empty"
  [[ "${#QUINTA_NODES[@]}" -eq "${#QUINTA_NODE_IPS[@]}" ]] || die "QUINTA_NODES and QUINTA_NODE_IPS must have the same length"
  validate_positive_integer "$REPLICA_COUNT" "replicas"
  validate_positive_integer "$CLIENT_COUNT" "clients"
  [[ "$REPLICA_COUNT" -ge 4 ]] || die "replicas must be at least 4"
  [[ "$RUN_NAME" =~ ^[A-Za-z0-9._-]+$ ]] || die "run-name may only contain letters, numbers, dot, underscore, and dash"
}

validate_positive_integer() {
  local value="$1"
  local label="$2"
  [[ "$value" =~ ^[0-9]+$ && "$value" -gt 0 ]] || die "$label must be a positive integer"
}

node_count() {
  printf '%s\n' "${#QUINTA_NODES[@]}"
}

fault_count() {
  printf '%s\n' $(((REPLICA_COUNT - 1) / 3))
}

initial_view() {
  local replica_id view=""
  for (( replica_id = 0; replica_id < REPLICA_COUNT; replica_id++ )); do
    [[ -z "$view" ]] || view+=","
    view+="$replica_id"
  done
  printf '%s\n' "$view"
}

replica_node_index() {
  local replica_id="$1"
  printf '%s\n' $((replica_id % $(node_count)))
}

replica_slot_on_node() {
  local replica_id="$1"
  printf '%s\n' $((replica_id / $(node_count)))
}

replica_node() {
  local node_index
  node_index="$(replica_node_index "$1")"
  printf '%s\n' "${QUINTA_NODES[$node_index]}"
}

replica_ip() {
  local node_index
  node_index="$(replica_node_index "$1")"
  printf '%s\n' "${QUINTA_NODE_IPS[$node_index]}"
}

replica_client_port() {
  local slot
  slot="$(replica_slot_on_node "$1")"
  printf '%s\n' $((QUINTA_BASE_CLIENT_PORT + slot * QUINTA_PORT_STEP))
}

replica_server_port() {
  printf '%s\n' $(($(replica_client_port "$1") + 1))
}

client_node() {
  local client_index="$1"
  local node_index=$(((REPLICA_COUNT + client_index) % $(node_count)))
  printf '%s\n' "${QUINTA_NODES[$node_index]}"
}

ssh_host() {
  printf '%s%s\n' "$QUINTA_SSH_PREFIX" "$1"
}

remote_run_dir() {
  printf '%s/%s\n' "$QUINTA_REMOTE_ROOT" "$RUN_NAME"
}

remote_replica_dir() {
  printf '%s/rep%s\n' "$(remote_run_dir)" "$1"
}

remote_client_dir() {
  printf '%s/cli%s\n' "$(remote_run_dir)" "$1"
}

print_layout() {
  local replica_id client_index

  echo "Run: $RUN_NAME"
  echo "Replicas: $REPLICA_COUNT (f=$(fault_count))"
  echo "Clients: $CLIENT_COUNT"
  echo "Remote root: $(remote_run_dir)"
  if (( REPLICA_COUNT > $(node_count) )); then
    echo "Notice: $REPLICA_COUNT replicas over $(node_count) nodes; some nodes will run more than one replica."
  fi
  echo
  echo "Replicas:"
  for (( replica_id = 0; replica_id < REPLICA_COUNT; replica_id++ )); do
    printf '  rep%-3s -> %-4s %-13s %s/%s\n' \
      "$replica_id" "$(replica_node "$replica_id")" "$(replica_ip "$replica_id")" \
      "$(replica_client_port "$replica_id")" "$(replica_server_port "$replica_id")"
  done
  echo
  echo "Clients:"
  for (( client_index = 0; client_index < CLIENT_COUNT; client_index++ )); do
    printf '  cli%-3s -> %s\n' "$client_index" "$(client_node "$client_index")"
  done
}

write_system_config() {
  local path="$1"
  local tmp_path="${path}.tmp"
  awk \
    -v replica_count="$REPLICA_COUNT" \
    -v fault_count_value="$(fault_count)" \
    -v initial_view_value="$(initial_view)" \
    '
      /^system\.servers\.num[[:space:]]*=/ {
        print "system.servers.num = " replica_count
        next
      }
      /^system\.servers\.f[[:space:]]*=/ {
        print "system.servers.f = " fault_count_value
        next
      }
      /^system\.initial\.view[[:space:]]*=/ {
        print "system.initial.view = " initial_view_value
        next
      }
      { print }
    ' "$path" > "$tmp_path"
  mv "$tmp_path" "$path"
}

write_hosts_config() {
  local path="$1"
  local replica_id
  {
    printf '# Generated by quinta_deploy.sh for run %s.\n' "$RUN_NAME"
    printf '#server id, address and port (the ids from 0 to n-1 are the service replicas)\n'
    for (( replica_id = 0; replica_id < REPLICA_COUNT; replica_id++ )); do
      printf '%s %s %s %s\n' \
        "$replica_id" "$(replica_ip "$replica_id")" \
        "$(replica_client_port "$replica_id")" "$(replica_server_port "$replica_id")"
    done
    printf '\n7001 %s %s\n' "$QUINTA_TTP_IP" "$QUINTA_TTP_PORT"
  } > "$path"
}

configure_local_deploy() {
  local deploy_dir deploy_index

  for (( deploy_index = 0; deploy_index < REPLICA_COUNT; deploy_index++ )); do
    deploy_dir="$ROOT_DIR/build/local/rep${deploy_index}"
    [[ -d "$deploy_dir" ]] || die "Missing local deploy directory: $deploy_dir"
    repair_local_deploy_from_reference "$deploy_dir"
    write_system_config "$deploy_dir/config/system.config"
    write_hosts_config "$deploy_dir/config/hosts.config"
  done

  for (( deploy_index = 0; deploy_index < CLIENT_COUNT; deploy_index++ )); do
    deploy_dir="$ROOT_DIR/build/local/cli${deploy_index}"
    [[ -d "$deploy_dir" ]] || die "Missing local deploy directory: $deploy_dir"
    repair_local_deploy_from_reference "$deploy_dir"
    write_system_config "$deploy_dir/config/system.config"
    write_hosts_config "$deploy_dir/config/hosts.config"
  done
}

reference_deploy_dir() {
  printf '%s\n' "$ROOT_DIR/build/install/cobra"
}

expected_source_jar_count() {
  local source_jars project_jars

  source_jars="$(find "$ROOT_DIR/lib" -maxdepth 1 -type f -name '*.jar' | wc -l | tr -d ' ')"
  project_jars="$(find "$ROOT_DIR/build/libs" -maxdepth 1 -type f -name '*.jar' | wc -l | tr -d ' ')"
  printf '%s\n' $((source_jars + project_jars))
}

validate_source_inputs() {
  local required_path

  for required_path in \
    config \
    pairing \
    scripts \
    lib \
    pairing_based_execution.sh \
    config/system.config \
    config/hosts.config \
    config/keysSSL_TLS/EC_KeyPair_256.pkcs12 \
    config/keysECDSA/publickey1001 \
    config/keysRSA/publickey1001 \
    config/keysSunEC/publickey1001 \
    pairing/relic/relic.zip \
    scripts/smartrun.sh; do
    [[ -e "$ROOT_DIR/$required_path" ]] || die "Missing source deploy path: $ROOT_DIR/$required_path"
  done

  [[ "$(find "$ROOT_DIR/lib" -maxdepth 1 -type f -name '*.jar' | wc -l | tr -d ' ')" -gt 0 ]] \
    || die "No source jars found in $ROOT_DIR/lib"
  [[ "$(find "$ROOT_DIR/build/libs" -maxdepth 1 -type f -name '*.jar' | wc -l | tr -d ' ')" -gt 0 ]] \
    || die "No project jar found in $ROOT_DIR/build/libs"
}

deploy_file_count() {
  local deploy_dir="$1"
  find "$deploy_dir" -type f | wc -l | tr -d ' '
}

deploy_jar_count() {
  local deploy_dir="$1"
  find "$deploy_dir/lib" -maxdepth 1 -type f -name '*.jar' | wc -l | tr -d ' '
}

repair_local_deploy_from_reference() {
  local deploy_dir="$1"
  local reference_dir

  reference_dir="$(reference_deploy_dir)"
  [[ -d "$reference_dir" ]] || die "Missing reference deploy directory: $reference_dir"
  [[ -d "$ROOT_DIR/config" ]] || die "Missing config source directory: $ROOT_DIR/config"

  mkdir -p "$deploy_dir"
  cp -R "$reference_dir/." "$deploy_dir/"
  rm -rf "$deploy_dir/config"
  mkdir -p "$deploy_dir/config"
  cp -R "$ROOT_DIR/config/." "$deploy_dir/config/"
}

validate_one_local_deploy() {
  local deploy_dir="$1"
  local expected_source_jars="$2"
  local actual_jars required_path source_path relative_path target_path

  [[ -d "$deploy_dir" ]] || die "Missing local deploy directory: $deploy_dir"

  for required_path in \
    config \
    lib \
    pairing \
    smartrun.sh \
    config/system.config \
    config/hosts.config \
    config/keysSSL_TLS/EC_KeyPair_256.pkcs12 \
    config/keysECDSA/publickey1001 \
    config/keysRSA/publickey1001 \
    config/keysSunEC/publickey1001; do
    [[ -e "$deploy_dir/$required_path" ]] || die "Missing local deploy path: $deploy_dir/$required_path"
  done

  while IFS= read -r source_path; do
    relative_path="${source_path#"$ROOT_DIR/config/"}"
    [[ -e "$deploy_dir/config/$relative_path" ]] \
      || die "Missing local deploy copy of source config: $deploy_dir/config/$relative_path"
  done < <(find "$ROOT_DIR/config" -type f)

  while IFS= read -r source_path; do
    relative_path="${source_path#"$ROOT_DIR/pairing/"}"
    [[ -e "$deploy_dir/pairing/$relative_path" ]] \
      || die "Missing local deploy copy of source pairing: $deploy_dir/pairing/$relative_path"
  done < <(find "$ROOT_DIR/pairing" -type f)

  while IFS= read -r source_path; do
    target_path="$deploy_dir/$(basename "$source_path")"
    [[ -e "$target_path" ]] \
      || die "Missing local deploy copy of source script: $target_path"
  done < <(find "$ROOT_DIR/scripts" -type f)

  [[ -e "$deploy_dir/pairing_based_execution.sh" ]] \
    || die "Missing local deploy copy of source file: $deploy_dir/pairing_based_execution.sh"

  while IFS= read -r source_path; do
    target_path="$deploy_dir/lib/$(basename "$source_path")"
    [[ -e "$target_path" ]] \
      || die "Missing local deploy copy of source jar: $target_path"
  done < <(find "$ROOT_DIR/lib" -maxdepth 1 -type f -name '*.jar')

  while IFS= read -r source_path; do
    target_path="$deploy_dir/lib/$(basename "$source_path")"
    [[ -e "$target_path" ]] \
      || die "Missing local deploy copy of project jar: $target_path"
  done < <(find "$ROOT_DIR/build/libs" -maxdepth 1 -type f -name '*.jar')

  actual_jars="$(deploy_jar_count "$deploy_dir")"
  [[ "$actual_jars" -ge "$expected_source_jars" ]] \
    || die "$deploy_dir/lib has $actual_jars jars; expected at least $expected_source_jars source/project jars"
}

remote_file_count() {
  local host="$1"
  local remote_target="$2"
  ssh_remote "$host" "find '$remote_target' -type f | wc -l | tr -d ' '"
}

remote_jar_count() {
  local host="$1"
  local remote_target="$2"
  ssh_remote "$host" "find '$remote_target/lib' -maxdepth 1 -type f -name '*.jar' | wc -l | tr -d ' '"
}

validate_remote_deploy() {
  local local_dir="$1"
  local host="$2"
  local remote_target="$3"
  local expected_files expected_jars actual_files actual_jars required_path

  expected_files="$(deploy_file_count "$local_dir")"
  expected_jars="$(deploy_jar_count "$local_dir")"

  for required_path in \
    "$remote_target" \
    "$remote_target/config" \
    "$remote_target/lib" \
    "$remote_target/pairing" \
    "$remote_target/smartrun.sh" \
    "$remote_target/config/system.config" \
    "$remote_target/config/hosts.config" \
    "$remote_target/config/keysSSL_TLS/EC_KeyPair_256.pkcs12" \
    "$remote_target/config/keysECDSA/publickey1001" \
    "$remote_target/config/keysRSA/publickey1001" \
    "$remote_target/config/keysSunEC/publickey1001"; do
    ssh_remote "$host" "test -e '$required_path'" \
      || { echo "Remote deploy missing required path: $host:$required_path" >&2; return 1; }
  done

  actual_files="$(remote_file_count "$host" "$remote_target")"
  [[ "$actual_files" -eq "$expected_files" ]] \
    || { echo "$host:$remote_target has $actual_files files; expected $expected_files" >&2; return 1; }

  actual_jars="$(remote_jar_count "$host" "$remote_target")"
  [[ "$actual_jars" -eq "$expected_jars" ]] \
    || { echo "$host:$remote_target/lib has $actual_jars jars; expected $expected_jars" >&2; return 1; }
}

copy_local_deploy_dir() {
  local local_dir="$1"
  local host="$2"
  local remote_dir="$3"
  local deploy_name remote_target

  deploy_name="$(basename "$local_dir")"
  remote_target="$remote_dir/$deploy_name"

  rsync_remote "$local_dir/" "$host:$remote_target/"
  if ! validate_remote_deploy "$local_dir" "$host" "$remote_target"; then
    echo "  remote deploy incomplete after rsync; retrying $deploy_name on $host..."
    rsync_remote "$local_dir/" "$host:$remote_target/"
    validate_remote_deploy "$local_dir" "$host" "$remote_target" \
      || die "Remote deploy is incomplete after retry: $host:$remote_target"
  fi
}

validate_local_deploy() {
  local expected_source_jars deploy_index
  validate_source_inputs
  expected_source_jars="$(expected_source_jar_count)"

  for (( deploy_index = 0; deploy_index < REPLICA_COUNT; deploy_index++ )); do
    validate_one_local_deploy "$ROOT_DIR/build/local/rep${deploy_index}" "$expected_source_jars"
  done
  for (( deploy_index = 0; deploy_index < CLIENT_COUNT; deploy_index++ )); do
    validate_one_local_deploy "$ROOT_DIR/build/local/cli${deploy_index}" "$expected_source_jars"
  done
}

deploy() {
  local replica_id client_index node host remote_dir

  echo "Preparing local deploy..."
  (
    cd "$ROOT_DIR"
    ./gradlew --no-daemon --no-watch-fs clean localDeploy -Pservers="$REPLICA_COUNT" -Pclients="$CLIENT_COUNT"
  )
  configure_local_deploy
  validate_local_deploy

  print_layout

  echo "Stopping previous processes for this run..."
  stop || true

  echo "Preparing remote directories..."
  for node in "${QUINTA_NODES[@]}"; do
    host="$(ssh_host "$node")"
    ssh_remote "$host" "rm -rf '$(remote_run_dir)' && mkdir -p '$(remote_run_dir)'"
  done

  echo "Copying replicas..."
  for (( replica_id = 0; replica_id < REPLICA_COUNT; replica_id++ )); do
    node="$(replica_node "$replica_id")"
    host="$(ssh_host "$node")"
    remote_dir="$(remote_run_dir)"
    echo "  rep$replica_id -> $host"
    copy_local_deploy_dir "$ROOT_DIR/build/local/rep${replica_id}" "$host" "$remote_dir"
  done

  echo "Copying clients..."
  for (( client_index = 0; client_index < CLIENT_COUNT; client_index++ )); do
    node="$(client_node "$client_index")"
    host="$(ssh_host "$node")"
    remote_dir="$(remote_run_dir)"
    echo "  cli$client_index -> $host"
    copy_local_deploy_dir "$ROOT_DIR/build/local/cli${client_index}" "$host" "$remote_dir"
  done
}

preflight() {
  local node host expected_ip java_output java_major replica_id ports port

  print_layout
  echo
  echo "Preflight:"
  for node_index in "${!QUINTA_NODES[@]}"; do
    node="${QUINTA_NODES[$node_index]}"
    expected_ip="${QUINTA_NODE_IPS[$node_index]}"
    host="$(ssh_host "$node")"
    echo "=== $host ==="
    ssh_remote "$host" "echo whoami=\$(whoami); hostname; ip -4 addr | grep -q '$expected_ip' && echo ip-ok=$expected_ip || echo ip-missing=$expected_ip; df -h / | tail -n 1"
    java_output="$(ssh_remote "$host" "java -version 2>&1 | head -n 1" || true)"
    echo "$java_output"
    java_major="$(printf '%s\n' "$java_output" | sed -n 's/.*version \"\([0-9][0-9]*\).*/\1/p')"
    [[ "$java_major" == "$QUINTA_REQUIRED_JAVA_MAJOR" ]] || die "$host has Java '$java_output', expected major $QUINTA_REQUIRED_JAVA_MAJOR"

    ports=""
    for (( replica_id = 0; replica_id < REPLICA_COUNT; replica_id++ )); do
      if [[ "$(replica_node "$replica_id")" == "$node" ]]; then
        ports+=" $(replica_client_port "$replica_id") $(replica_server_port "$replica_id")"
      fi
    done
    for port in $ports; do
      if ssh_remote "$host" "ss -ltn | grep -q ':$port '" >/dev/null 2>&1; then
        die "$host has port $port in use"
      fi
    done
  done
}

start() {
  local replica_id node host remote_dir

  for (( replica_id = 0; replica_id < REPLICA_COUNT; replica_id++ )); do
    node="$(replica_node "$replica_id")"
    host="$(ssh_host "$node")"
    remote_dir="$(remote_replica_dir "$replica_id")"
    echo "Starting rep$replica_id on $host"
    ssh_remote -f "$host" "cd '$remote_dir' && nohup env JAVA_OPTS='$QUINTA_REPLICA_JAVA_OPTS' bash smartrun.sh sse.demo.server.Server $replica_id > server.log 2>&1 < /dev/null & echo \$! > server.pid"
  done
}

wait_ready() {
  local deadline=$((SECONDS + QUINTA_READY_TIMEOUT_SECONDS))
  local replica_id node host remote_dir ready

  echo "Waiting up to ${QUINTA_READY_TIMEOUT_SECONDS}s for replicas..."
  while (( SECONDS < deadline )); do
    ready=1
    for (( replica_id = 0; replica_id < REPLICA_COUNT; replica_id++ )); do
      node="$(replica_node "$replica_id")"
      host="$(ssh_host "$node")"
      remote_dir="$(remote_replica_dir "$replica_id")"
      if ! ssh_remote "$host" "grep -q 'Ready to process operations' '$remote_dir/server.log' 2>/dev/null" >/dev/null 2>&1; then
        ready=0
        break
      fi
    done
    if [[ "$ready" -eq 1 ]]; then
      echo "All replicas are ready."
      return
    fi
    sleep 2
  done

  echo "Timed out waiting for replicas." >&2
  status >&2 || true
  exit 1
}

status() {
  local replica_id node host remote_dir

  for (( replica_id = 0; replica_id < REPLICA_COUNT; replica_id++ )); do
    node="$(replica_node "$replica_id")"
    host="$(ssh_host "$node")"
    remote_dir="$(remote_replica_dir "$replica_id")"
    echo "=== $host rep$replica_id ==="
    ssh_remote "$host" "pgrep -af '[s]se.demo.server.Server $replica_id' || echo 'not running'; grep -n 'Ready to process operations' '$remote_dir/server.log' 2>/dev/null || tail -n 20 '$remote_dir/server.log' 2>/dev/null || echo 'no server.log'"
  done
}

ports_for_node() {
  local node="$1"
  local replica_id ports=""

  for (( replica_id = 0; replica_id < REPLICA_COUNT; replica_id++ )); do
    if [[ "$(replica_node "$replica_id")" == "$node" ]]; then
      ports+=" $(replica_client_port "$replica_id") $(replica_server_port "$replica_id")"
    fi
  done

  printf '%s\n' "$ports"
}

kill_current_run_listener_on_port() {
  local host="$1"
  local port="$2"
  local signal="$3"

  ssh_remote "$host" "pid=\$(ss -ltnp 'sport = :$port' 2>/dev/null | sed -n 's/.*pid=\([0-9][0-9]*\).*/\1/p' | head -n 1); if [[ -n \"\$pid\" ]] && ps -p \"\$pid\" -o args= | grep -F -q '$(remote_run_dir)'; then echo \"Killing current-run listener on port $port: pid=\$pid signal=$signal\"; kill -$signal \"\$pid\" 2>/dev/null || true; fi"
}

kill_any_listener_on_port() {
  local host="$1"
  local port="$2"
  local signal="$3"

  ssh_remote "$host" "pid=\$(ss -ltnp 'sport = :$port' 2>/dev/null | sed -n 's/.*pid=\([0-9][0-9]*\).*/\1/p' | head -n 1); if [[ -n \"\$pid\" ]]; then echo \"Killing listener on port $port: pid=\$pid signal=$signal\"; kill -$signal \"\$pid\" 2>/dev/null || true; fi"
}

stop() {
  local node host ports port

  for node in "${QUINTA_NODES[@]}"; do
    host="$(ssh_host "$node")"
    echo "Stopping on $host"
    ssh_remote "$host" "pkill -TERM -f '$(remote_run_dir).*[s]se.demo.server.Server' || true; pkill -TERM -f '$(remote_run_dir).*/[s]martrun.sh' || true"
  done

  sleep 2

  for node in "${QUINTA_NODES[@]}"; do
    host="$(ssh_host "$node")"
    ports="$(ports_for_node "$node")"
    ssh_remote "$host" "pkill -KILL -f '$(remote_run_dir).*[s]se.demo.server.Server' || true; pkill -KILL -f '$(remote_run_dir).*/[s]martrun.sh' || true"
    for port in $ports; do
      kill_current_run_listener_on_port "$host" "$port" KILL
    done
  done
}

preclean() {
  local node host ports port

  for node in "${QUINTA_NODES[@]}"; do
    host="$(ssh_host "$node")"
    echo "Preclean stopping stale processes on $host"
    ssh_remote "$host" "pkill -TERM -f '${QUINTA_REMOTE_ROOT}/.*[s]se.demo.server.Server' || true; pkill -TERM -f '${QUINTA_REMOTE_ROOT}/.*/[s]martrun.sh' || true; pkill -TERM -f '${QUINTA_REMOTE_ROOT}/.*[s]se.benchmark.BenchmarkClient' || true; pkill -TERM -f '${QUINTA_REMOTE_ROOT}/.*[s]se.populatedb.PopulateDB' || true"
  done

  sleep 2

  for node in "${QUINTA_NODES[@]}"; do
    host="$(ssh_host "$node")"
    ports="$(ports_for_node "$node")"
    ssh_remote "$host" "pkill -KILL -f '${QUINTA_REMOTE_ROOT}/.*[s]se.demo.server.Server' || true; pkill -KILL -f '${QUINTA_REMOTE_ROOT}/.*/[s]martrun.sh' || true; pkill -KILL -f '${QUINTA_REMOTE_ROOT}/.*[s]se.benchmark.BenchmarkClient' || true; pkill -KILL -f '${QUINTA_REMOTE_ROOT}/.*[s]se.populatedb.PopulateDB' || true"
    for port in $ports; do
      kill_any_listener_on_port "$host" "$port" KILL
    done
  done

  for node in "${QUINTA_NODES[@]}"; do
    host="$(ssh_host "$node")"
    echo "Cleaning on $host"
    ssh_remote "$host" "rm -rf '$(remote_run_dir)'"
  done
}

clean() {
  local node host

  stop
  for node in "${QUINTA_NODES[@]}"; do
    host="$(ssh_host "$node")"
    echo "Cleaning on $host"
    ssh_remote "$host" "rm -rf '$(remote_run_dir)'"
  done
}

main() {
  parse_args "$@"
  load_cluster_config

  case "$COMMAND" in
    layout) print_layout ;;
    preflight) preflight ;;
    deploy) deploy ;;
    start) start ;;
    wait) wait_ready ;;
    status) status ;;
    stop) stop ;;
    clean) clean ;;
    preclean) preclean ;;
    *)
      usage
      exit 1
      ;;
  esac
}

main "$@"
