#!/usr/bin/env bash
# Single entry point for the template: start/stop/status, tests, setup,
# role management, database reset.
# Backend: Spring Boot + PostgreSQL (backend/). Frontend: Next.js (frontend/).

set -u

# ---- configuration ----

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
GREEN='\033[32m'; YELLOW='\033[33m'; RED='\033[31m'; NC='\033[0m'

PORT_BACKEND=8080
PORT_FRONTEND=3000

# Spring backend lives here; used for the service dir, pid file and log name.
BACKEND_DIR=backend

# Seconds to wait for each service's port. Gradle may need to compile before
# the backend's port opens (cold build/), so it gets a generous window;
# npm run dev is quick.
BACKEND_START_TRIES=120
FRONTEND_START_TRIES=20

# Service logs land at $LOG_BASE-<dir>.log.
LOG_BASE=/tmp/spring-template

# ---- database ----

# Personal root .env wins, .env.dev fills in for development — the same
# precedence the backend's env loader applies. Used by the Postgres check,
# database reset, and re-seed commands.
load_db_url() {
  local url="postgres://postgres:postgres@localhost:5432/template-db?sslmode=disable"
  if [ -f "$ROOT_DIR/.env" ]; then
    url=$(grep -E '^DATABASE_URL=' "$ROOT_DIR/.env" | tail -1 | cut -d= -f2- | tr -d '"' || true)
  fi
  if [ ! -f "$ROOT_DIR/.env" ] && [ -f "$ROOT_DIR/.env.dev" ]; then
    url=$(grep -E '^DATABASE_URL=' "$ROOT_DIR/.env.dev" | tail -1 | cut -d= -f2- | tr -d '"')
  fi
  echo "$url"
}

# ---- service control ----

wait_for_port() {
  local port="$1" name="$2" tries="$3" i=0
  until lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; do
    i=$((i + 1))
    if [ "$i" -ge "$tries" ]; then
      echo -e "${RED}$name did not come up on :$port${NC}"
      return 1
    fi
    sleep 1
  done
}

# start_service <Name> <dir> <port> <tries> <cmd...> — checks the port is
# free, backgrounds <cmd> in <dir>, waits for the port, writes the PID to
# <dir>/<dir>.pid.
start_service() {
  local name="$1" dir="$2" port="$3" tries="$4"; shift 4
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo -e "${YELLOW}$name already running on :$port${NC}"
    return 0
  fi
  echo "Starting $name on :$port ..."
  (cd "$ROOT_DIR/$dir" && "$@" > "$LOG_BASE-$dir.log" 2>&1 &)
  wait_for_port "$port" "$name" "$tries" || return 1
  # PID of the actual listening process (the bootRun JVM / next dev).
  local pid
  pid=$(lsof -nP -tiTCP:"$port" -sTCP:LISTEN | head -1)
  if [ -n "$pid" ]; then echo "$pid" > "$ROOT_DIR/$dir/$dir.pid"; fi
}

start_backend() {
  local url
  url=$(load_db_url)
  # Refuses to pointlessly launch if PostgreSQL is down (the backend exits
  # immediately anyway).
  if command -v pg_isready >/dev/null 2>&1 && ! pg_isready -q -d "$url"; then
    echo -e "${RED}PostgreSQL is not running (checked $url).${NC}"
    echo "Start it first, e.g. brew services start postgresql@16"
    return 1
  fi
  if ! start_service "Backend" "$BACKEND_DIR" "$PORT_BACKEND" "$BACKEND_START_TRIES" ./gradlew bootRun; then
    echo "→ see $LOG_BASE-$BACKEND_DIR.log"
    return 1
  fi
}

stop_service() {
  local dir="$1" name="$2" port="$3"
  # Separate statement: in one `local` line every RHS is expanded before any
  # assignment happens, so $dir above would still be unbound here (set -u).
  local pid_file="$ROOT_DIR/$dir/$dir.pid"
  local pid=""
  local recorded=1
  if [ -f "$pid_file" ]; then
    pid=$(cat "$pid_file")
    rm -f "$pid_file"
  fi
  # Nothing recorded, so fall back to whatever holds the port (started manually,
  # or a stale pid file). That process may not be ours at all — another project's
  # dev server can grab 8080 — so confirm before killing it.
  if [ -z "$pid" ]; then
    pid=$(lsof -nP -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null | head -1)
    recorded=0
  fi
  if [ -z "$pid" ]; then
    echo -e "${YELLOW}$name is not running${NC}"
    return 0
  fi
  if [ "$recorded" -eq 0 ]; then
    echo -e "${YELLOW}$name has no pid file; :$port is held by pid $pid.${NC}"
    read -r -p "Kill pid $pid? It may belong to another project. [y/N] " answer
    case "$answer" in
      y|Y) ;;
      *) echo "Left pid $pid alone."; return 0 ;;
    esac
  fi
  if kill "$pid" 2>/dev/null; then
    echo "Stopped $name (pid $pid)"
  else
    echo -e "${YELLOW}$name is not running${NC}"
  fi
}

start_all() {
  start_backend || return 1
  start_frontend || { echo -e "${RED}Frontend did not start${NC}"; return 1; }
  echo -e "${GREEN}Backend: http://localhost:$PORT_BACKEND  Frontend: http://localhost:$PORT_FRONTEND${NC}"
  echo "Logs: $LOG_BASE-$BACKEND_DIR.log, $LOG_BASE-frontend.log"
}

# The frontend can't come up without its dependencies: say so up front instead
# of letting `npm run dev` die into the log file and the port wait time out.
require_frontend_deps() {
  if [ ! -d "$ROOT_DIR/frontend/node_modules" ]; then
    echo -e "${YELLOW}frontend/node_modules is missing — run ./manage.sh setup first${NC}"
    return 1
  fi
}

start_frontend() {
  require_frontend_deps || return 1
  start_service "Frontend" frontend "$PORT_FRONTEND" "$FRONTEND_START_TRIES" npm run dev
}

stop_all() {
  stop_service "$BACKEND_DIR" Backend "$PORT_BACKEND"
  stop_service frontend Frontend "$PORT_FRONTEND"
}

show_status() {
  if lsof -nP -iTCP:"$PORT_BACKEND" -sTCP:LISTEN >/dev/null 2>&1; then
    echo -e "  Backend : ${GREEN}running${NC} on :$PORT_BACKEND"
  else
    echo -e "  Backend : ${RED}not running${NC}"
  fi
  if lsof -nP -iTCP:"$PORT_FRONTEND" -sTCP:LISTEN >/dev/null 2>&1; then
    echo -e "  Frontend: ${GREEN}running${NC} on :$PORT_FRONTEND"
  else
    echo -e "  Frontend: ${RED}not running${NC}"
  fi
}

# ---- workflows ----

first_time_setup() {
  echo "→ frontend: npm install"
  (cd "$ROOT_DIR/frontend" && npm install) || return 1
  echo "→ backend: ./gradlew build (compiles + tests, may take a few minutes)"
  (cd "$ROOT_DIR/$BACKEND_DIR" && ./gradlew build) || return 1
  echo "→ database: migrations apply automatically on backend start."
  echo "  Requires a running PostgreSQL (see DATABASE_URL in .env.example)."
  echo -e "${GREEN}Setup complete. Start everything with option 1.${NC}"
}

# Backend tests (./gradlew test) + frontend build. Integration tests need
# TEST_DATABASE_URL; without it they skip and the unit tests still run.
run_tests() {
  if [ -n "${TEST_DATABASE_URL:-}" ]; then
    (cd "$ROOT_DIR/$BACKEND_DIR" && TEST_DATABASE_URL="$TEST_DATABASE_URL" ./gradlew test) || return 1
  else
    echo -e "${YELLOW}TEST_DATABASE_URL not set — unit-only tests (integration tests skip).${NC}"
    (cd "$ROOT_DIR/$BACKEND_DIR" && ./gradlew test) || return 1
  fi
}

# What menu option 6 and `./manage.sh test` run: the backend suite, then the
# frontend build (which typechecks it as a side effect).
run_checks() {
  run_tests || return 1
  require_frontend_deps || return 1
  (cd "$ROOT_DIR/frontend" && npm run build)
}

# The artifact build: the boot jar the Dockerfile copies, plus the frontend's
# production bundle.
build_all() {
  (cd "$ROOT_DIR/$BACKEND_DIR" && ./gradlew build) || return 1
  require_frontend_deps || return 1
  (cd "$ROOT_DIR/frontend" && npm run build)
}

# The repo's formatter. Java formatting isn't wired up yet (no formatter plugin
# in the Gradle build), so this covers the JS/TS/CSS/JSON/Markdown side.
format_code() {
  (cd "$ROOT_DIR" && npx prettier --write . --ignore-path .gitignore)
}

# Build the jar, then run the CLI: it reads DATABASE_URL directly and ignores
# .env files, matching the subcommand it replaces.
set_user_role_for() {
  local email="$1" role="$2"
  (cd "$ROOT_DIR/$BACKEND_DIR" && ./gradlew bootJar -q && java -jar build/libs/app.jar set-role "$email" "$role") || return 1
}

# Menu entry point: prompts for what the subcommand takes as arguments.
set_user_role() {
  read -r -p "Email: " email
  read -r -p "Role (client/staff/admin): " role
  set_user_role_for "$email" "$role"
}

# Destructive steps ask for a typed 'yes'; --yes skips the prompt so a script
# (or `make db-reset YES=1`) can run them unattended. Declining is a failure, so
# callers can tell "aborted" from "done".
confirm_destructive() {
  local url="$1" assume_yes="$2"
  echo -e "${RED}This drops ALL tables in: ${url}${NC}"
  if [ "$assume_yes" = "--yes" ]; then return 0; fi
  read -r -p "Type 'yes' to confirm: " confirm
  if [ "$confirm" != "yes" ]; then echo "Aborted."; return 1; fi
}

reset_database() {
  local url
  url=$(load_db_url)
  confirm_destructive "$url" "${1:-}" || return 1
  psql "$url" -c 'DROP SCHEMA public CASCADE; CREATE SCHEMA public;' || return 1
  echo -e "${GREEN}Database reset. Tables re-apply on next backend start.${NC}"
}

# Drop the schema, then restart the backend so it re-migrates and re-seeds
# (dev admin). One-shot "start fresh".
re_seed() {
  local url
  url=$(load_db_url)
  confirm_destructive "$url" "${1:-}" || return 1
  psql "$url" -c 'DROP SCHEMA public CASCADE; CREATE SCHEMA public;' || return 1
  stop_service "$BACKEND_DIR" Backend "$PORT_BACKEND"
  start_backend || return 1
  echo -e "${GREEN}Database re-seeded.${NC}"
}

# Tail a service log. Ctrl-C to stop following. No argument prompts (the menu's
# behaviour); otherwise backend|frontend|both.
view_logs() {
  local which="${1:-}"
  if [ -z "$which" ]; then
    echo "Which log?"
    echo "  b) Backend"
    echo "  f) Frontend"
    echo "  a) Both"
    read -r -p "Choose: " which
    case "$which" in
      b) which=backend ;;
      f) which=frontend ;;
      a) which=both ;;
      *) echo -e "${YELLOW}Unknown option${NC}"; return 1 ;;
    esac
  fi
  case "$which" in
    backend) tail -f "$LOG_BASE-$BACKEND_DIR.log" ;;
    frontend) tail -f "$LOG_BASE-frontend.log" ;;
    both) tail -f "$LOG_BASE-$BACKEND_DIR.log" "$LOG_BASE-frontend.log" ;;
    *) echo -e "${YELLOW}Unknown log: $which (use backend|frontend|both)${NC}"; return 1 ;;
  esac
}

# ---- compose (the primary path) ----

require_docker() {
  if ! docker info >/dev/null 2>&1; then
    echo -e "${RED}Docker isn't running.${NC}"
    echo "Start Docker Desktop / Rancher Desktop, or take the native path: ./manage.sh up"
    return 1
  fi
}

# Detached, so it doesn't hijack the shell. `compose:build` is what rebuilds the
# images after a code change.
compose_up() {
  require_docker || return 1
  (cd "$ROOT_DIR" && docker compose up -d) || return 1
  echo -e "${GREEN}API: http://localhost:$PORT_BACKEND  Frontend: http://localhost:$PORT_FRONTEND  Mailpit: http://localhost:8025${NC}"
  echo "Logs: ./manage.sh compose:logs"
}

compose_down() {
  require_docker || return 1
  (cd "$ROOT_DIR" && docker compose down) || return 1
  echo "Compose stack stopped (the database volume is kept)."
}

compose_build() {
  require_docker || return 1
  (cd "$ROOT_DIR" && docker compose build)
}

# The whole stack, or one service: ./manage.sh compose:logs api
compose_logs() {
  require_docker || return 1
  if [ -n "${1:-}" ]; then
    (cd "$ROOT_DIR" && docker compose logs -f "$1")
  else
    (cd "$ROOT_DIR" && docker compose logs -f)
  fi
}

# ---- menu ----

interactive_menu() {
  while true; do
    echo ""
    echo "==== Spring Boot + Next.js template ===="
    echo " 1) Start All (Backend + Frontend)"
    echo " 2) Start Backend only"
    echo " 3) Start Frontend only"
    echo " 4) Stop All"
    echo " 5) Status"
    echo " 6) Run Tests (backend gradle test + frontend build)"
    echo " 7) First-Time Setup (install deps)"
    echo " 8) Set User Role"
    echo " 9) Reset Database (destructive)"
    echo " 10) View Logs (tail)"
    echo " 11) Re-seed (reset DB + restart backend)"
    echo " q) Quit"
    echo " (no argument also prints the subcommand list: ./manage.sh help)"
    read -r -p "Choose: " choice
    case "$choice" in
      1) start_all ;;
      2) start_backend ;;
      3) start_frontend ;;
      4) stop_all ;;
      5) show_status ;;
      6) run_checks || echo -e "${RED}Checks failed (see above)${NC}" ;;
      7) first_time_setup ;;
      8) set_user_role ;;
      9) reset_database ;;
      10) view_logs ;;
      11) re_seed ;;
      q) break ;;
      *) echo -e "${YELLOW}Unknown option${NC}" ;;
    esac
  done
}

# ---- subcommands ----

usage() {
  cat <<'USAGE'
Spring Boot + Next.js template.

  ./manage.sh                       interactive menu

Docker — the primary path (no local Java, Node or Postgres needed)
  compose:up                        start the stack detached
  compose:down                      stop it (the database volume is kept)
  compose:build                     rebuild the images after a code change
  compose:logs [service]            follow the stack, or one service

Native — needs Java 21, Node 20+ and a running PostgreSQL
  up | down | status                start, stop, what's running
  backend | frontend                start just one of them
  logs [backend|frontend|both]      follow a service log

Work
  setup                             first-time dependency install
  test                              backend tests + frontend build
  build                             API jar + frontend production bundle
  fmt                               prettier --write
  role <email> <role>               set a role (client|staff|admin)
  db:reset [--yes]                  drop and recreate the schema
  db:reseed [--yes]                 drop the schema, restart the backend

TEST_DATABASE_URL enables the 12 DB-backed integration tests; see docs/DATABASE.md.
USAGE
}

# Runs one step and exits with its status, so `./manage.sh test && ...` and the
# Makefile both see failures.
step() {
  local action="$1"; shift
  "$action" "$@"
  exit $?
}

case "${1:-}" in
  "")             interactive_menu ;;
  help|-h|--help) usage ;;
  up)             step start_all ;;
  backend)        step start_backend ;;
  frontend)       step start_frontend ;;
  down)           step stop_all ;;
  status)         step show_status ;;
  logs)           step view_logs "${2:-}" ;;
  setup)          step first_time_setup ;;
  test)           step run_checks ;;
  build)          step build_all ;;
  fmt)            step format_code ;;
  db:reset)       step reset_database "${2:-}" ;;
  db:reseed)      step re_seed "${2:-}" ;;
  compose:up)     step compose_up ;;
  compose:down)   step compose_down ;;
  compose:build)  step compose_build ;;
  compose:logs)   step compose_logs "${2:-}" ;;
  role)
    if [ -z "${2:-}" ] || [ -z "${3:-}" ]; then
      echo -e "${YELLOW}Usage: ./manage.sh role <email> <client|staff|admin>${NC}"
      exit 2
    fi
    step set_user_role_for "$2" "$3"
    ;;
  *)
    echo -e "${YELLOW}Unknown subcommand: $1${NC}"
    usage
    exit 2
    ;;
esac
