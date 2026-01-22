#!/usr/bin/env bash
set -euo pipefail

# Always operate relative to the location of runme.sh
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
  echo "Usage:"
  echo "  ./runme.sh -c                 Compile all services"
  echo "  ./runme.sh -u                 Start UserService"
  echo "  ./runme.sh -p                 Start ProductService"
  echo "  ./runme.sh -i                 Start ISCS"
  echo "  ./runme.sh -o                 Start OrderService"
  echo "  ./runme.sh -w workloadfile    Run workload parser (you implement)"
  exit 1
}

compile_service() {
  local service="$1"
  local src_dir="$ROOT_DIR/src/$service"
  local out_dir="$ROOT_DIR/compiled/$service"

  mkdir -p "$out_dir"

  # Compile all .java files in that service folder
  # (If you later use packages, we can adjust this.)
  if compgen -G "$src_dir/*.java" > /dev/null; then
    echo "[compile] $service"
    javac -Xlint:all -d "$out_dir" "$src_dir"/*.java
  else
    echo "[compile] $service: no Java files found in $src_dir"
  fi
}

start_java_service() {
  local service="$1"
  local out_dir="$ROOT_DIR/compiled/$service"
  local config_path="$ROOT_DIR/config.json"

  if [ ! -d "$out_dir" ]; then
    echo "Error: $out_dir does not exist. Run ./runme.sh -c first."
    exit 1
  fi
  if [ ! -f "$config_path" ]; then
    echo "Error: config.json not found at $config_path"
    exit 1
  fi

  echo "[start] $service"
  cd "$out_dir"
  # Run from inside compiled/<Service> so "java ServiceName" works easily
  java "$service" "$config_path"
}

run_workload() {
  local workload_file="$1"

  if [ ! -f "$workload_file" ]; then
    echo "Error: workload file not found: $workload_file"
    exit 1
  fi

  echo "[workload] TODO: run your workload parser here with file: $workload_file"
  echo "Hint: implement WorkloadParser later (can be any language)."
  echo "Example placeholder:"
  echo "  python3 \"$ROOT_DIR/compiled/OrderService/WorkloadParser.py\" \"$workload_file\""
}

# --- main ---
if [ $# -lt 1 ]; then
  usage
fi

case "$1" in
  -c)
    mkdir -p "$ROOT_DIR/compiled" "$ROOT_DIR/docs" "$ROOT_DIR/tests"
    compile_service "UserService"
    compile_service "ProductService"
    compile_service "ISCS"
    compile_service "OrderService"
    echo "[compile] done"
    ;;
  -u)
    start_java_service "UserService"
    ;;
  -p)
    start_java_service "ProductService"
    ;;
  -i)
    start_java_service "ISCS"
    ;;
  -o)
    start_java_service "OrderService"
    ;;
  -w)
    if [ $# -ne 2 ]; then usage; fi
    run_workload "$2"
    ;;
  *)
    usage
    ;;
esac
