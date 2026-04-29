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

scp_remote() {
  scp -q \
    -o BatchMode=yes \
    -o ConnectTimeout="${QUINTA_SSH_CONNECT_TIMEOUT_SECONDS:-10}" \
    -o ServerAliveInterval="${QUINTA_SSH_SERVER_ALIVE_INTERVAL_SECONDS:-5}" \
    -o ServerAliveCountMax="${QUINTA_SSH_SERVER_ALIVE_COUNT_MAX:-2}" \
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
    write_system_config "$deploy_dir/config/system.config"
    write_hosts_config "$deploy_dir/config/hosts.config"
  done

  for (( deploy_index = 0; deploy_index < CLIENT_COUNT; deploy_index++ )); do
    deploy_dir="$ROOT_DIR/build/local/cli${deploy_index}"
    [[ -d "$deploy_dir" ]] || die "Missing local deploy directory: $deploy_dir"
    write_system_config "$deploy_dir/config/system.config"
    write_hosts_config "$deploy_dir/config/hosts.config"
  done
}

expected_lib_count() {
  find "$ROOT_DIR/build/install/cobra/lib" -maxdepth 1 -type f | wc -l | tr -d ' '
}

repair_config_dirs_if_needed() {
  local deploy_dir="$1"
  local config_dir

  mkdir -p "$deploy_dir/config"
  for config_dir in keysECDSA keysRSA keysSSL_TLS keysSunEC workloads; do
    if [[ ! -d "$deploy_dir/config/$config_dir" ]]; then
      rm -rf "$deploy_dir/config/$config_dir"
      cp -R "$ROOT_DIR/config/$config_dir" "$deploy_dir/config/"
    fi
  done

  if [[ ! -f "$deploy_dir/config/keysSSL_TLS/EC_KeyPair_256.pkcs12" ]]; then
    mkdir -p "$deploy_dir/config"
    rm -rf "$deploy_dir/config/keysSSL_TLS"
    cp -R "$ROOT_DIR/config/keysSSL_TLS" "$deploy_dir/config/"
  fi
}

validate_one_local_deploy() {
  local deploy_dir="$1"
  local expected_count="$2"
  local actual_count

  [[ -d "$deploy_dir" ]] || die "Missing local deploy directory: $deploy_dir"
  repair_config_dirs_if_needed "$deploy_dir"

  actual_count="$(find "$deploy_dir/lib" -maxdepth 1 -type f | wc -l | tr -d ' ')"
  [[ "$actual_count" -eq "$expected_count" ]] || die "$deploy_dir/lib has $actual_count jars; expected $expected_count"
  [[ -f "$deploy_dir/config/keysSSL_TLS/EC_KeyPair_256.pkcs12" ]] || die "Missing TLS key in $deploy_dir"
  [[ -f "$deploy_dir/config/keysECDSA/publickey1001" ]] || die "Missing ECDSA keys in $deploy_dir"
  [[ -f "$deploy_dir/config/keysRSA/publickey1001" ]] || die "Missing RSA keys in $deploy_dir"
  [[ -f "$deploy_dir/config/keysSunEC/publickey1001" ]] || die "Missing SunEC keys in $deploy_dir"
  [[ -f "$deploy_dir/smartrun.sh" ]] || die "Missing smartrun.sh in $deploy_dir"
}

copy_local_deploy_dir() {
  local local_dir="$1"
  local host="$2"
  local remote_dir="$3"

  if ! scp_remote -r "$local_dir" "$host:$remote_dir/"; then
    echo "  retrying with legacy scp protocol..."
    scp_remote -O -r "$local_dir" "$host:$remote_dir/"
  fi
}

validate_local_deploy() {
  local expected_count deploy_index
  expected_count="$(expected_lib_count)"
  [[ "$expected_count" -gt 0 ]] || die "No jars found in build/install/cobra/lib"

  for (( deploy_index = 0; deploy_index < REPLICA_COUNT; deploy_index++ )); do
    validate_one_local_deploy "$ROOT_DIR/build/local/rep${deploy_index}" "$expected_count"
  done
  for (( deploy_index = 0; deploy_index < CLIENT_COUNT; deploy_index++ )); do
    validate_one_local_deploy "$ROOT_DIR/build/local/cli${deploy_index}" "$expected_count"
  done
}

deploy() {
  local replica_id client_index node host remote_dir

  echo "Preparing local deploy..."
  (
    cd "$ROOT_DIR"
    ./gradlew clean localDeploy -Pservers="$REPLICA_COUNT" -Pclients="$CLIENT_COUNT"
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

stop() {
  local node host

  for node in "${QUINTA_NODES[@]}"; do
    host="$(ssh_host "$node")"
    echo "Stopping on $host"
    ssh_remote "$host" "pkill -f '$(remote_run_dir).*[s]se.demo.server.Server' || true; pkill -f '$(remote_run_dir).*/[s]martrun.sh' || true"
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
    *)
      usage
      exit 1
      ;;
  esac
}

main "$@"
