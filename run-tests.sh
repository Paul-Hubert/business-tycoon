#!/usr/bin/env bash
# ============================================================
#  Trade Empire — Complete Test Runner
# ============================================================
#
#  This script runs the full test suite reliably:
#    1. Ensures app is running (starts Docker if needed)
#    2. Downloads JUnit/Hamcrest if needed
#    3. Compiles all source + test files
#    4. Runs all tests via JUnit
#    5. Reports results with statistics
#
#  USAGE:
#    ./run-tests.sh              # Run all tests
#    ./run-tests.sh --only Auth  # Run only specific test class
#    ./run-tests.sh --verbose    # Show compilation details
#    ./run-tests.sh --help       # Show this help
#
# ============================================================

set -euo pipefail

# ── Configuration ──────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

APP_PORT="8080"
APP_URL="http://localhost:$APP_PORT"
HEALTH_CHECK_TIMEOUT=60  # seconds
COMPILE_TIMEOUT=30
TEST_TIMEOUT=300        # 5 minutes for all tests

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

info()    { printf "${CYAN}[INFO]${NC}   %s\n" "$*"; }
ok()      { printf "${GREEN}[✓]${NC}     %s\n" "$*"; }
warn()    { printf "${YELLOW}[WARN]${NC}   %s\n" "$*"; }
error()   { printf "${RED}[✗]${NC}     %s\n" "$*"; exit 1; }
success() { printf "${GREEN}${BOLD}[SUCCESS]${NC} %s\n" "$*"; }

# Directories
SRC_DIR="src"
BUILD_DIR="build"
LIB_DIR="WebContent/WEB-INF/lib"
TEST_SRC="$SRC_DIR/test"

# Parse flags
ONLY_CLASS=""
VERBOSE=0

while [[ $# -gt 0 ]]; do
  case $1 in
    --only)
      ONLY_CLASS="$2"
      shift 2
      ;;
    --verbose)
      VERBOSE=1
      shift
      ;;
    --help|-h)
      head -20 "$0" | tail -16
      exit 0
      ;;
    *)
      error "Unknown option: $1"
      ;;
  esac
done

# ============================================================
#  Step 1 — Check and start app
# ============================================================

info "Checking if app is running at $APP_URL..."

APP_READY=0
for i in $(seq 1 5); do
  if curl -s "$APP_URL" > /dev/null 2>&1; then
    APP_READY=1
    ok "App is running"
    break
  fi
  if [ $i -lt 5 ]; then
    warn "App not responding (attempt $i/5), waiting..."
    sleep 2
  fi
done

if [ $APP_READY -eq 0 ]; then
  warn "App is not running. Attempting to start via Docker..."

  if ! command -v docker &>/dev/null; then
    error "Docker not found. Please install Docker and start the app manually with: ./setup.sh"
  fi

  # Check if containers exist
  TOMCAT_CONTAINER=$(docker ps -a --filter "name=business-tycoon-tomcat" --format "{{.ID}}" 2>/dev/null || echo "")

  if [ -z "$TOMCAT_CONTAINER" ]; then
    warn "Docker containers not found. Building..."
    if [ ! -f "docker-compose.yml" ]; then
      error "docker-compose.yml not found. Run ./setup.sh first."
    fi
    docker compose up --build -d || error "Failed to start Docker containers"
  else
    warn "Starting existing containers..."
    docker compose up -d || error "Failed to start Docker containers"
  fi

  # Wait for app to be ready
  info "Waiting for app startup (max $HEALTH_CHECK_TIMEOUT seconds)..."
  ELAPSED=0
  while [ $ELAPSED -lt $HEALTH_CHECK_TIMEOUT ]; do
    if curl -s "$APP_URL" > /dev/null 2>&1; then
      ok "App is now running"
      APP_READY=1
      break
    fi
    sleep 2
    ELAPSED=$((ELAPSED + 2))
    printf "."
  done
  echo ""

  if [ $APP_READY -eq 0 ]; then
    error "App failed to start within ${HEALTH_CHECK_TIMEOUT}s. Check logs: docker compose logs"
  fi
fi

# ============================================================
#  Step 2 — Setup JUnit and Hamcrest
# ============================================================

info "Setting up test dependencies..."

if [ ! -d "$LIB_DIR" ]; then
  error "Library directory not found: $LIB_DIR"
fi

JUNIT_JAR="$LIB_DIR/junit-4.13.2.jar"
HAMCREST_JAR="$LIB_DIR/hamcrest-core-1.3.jar"

# Download JUnit if missing
if [ ! -f "$JUNIT_JAR" ]; then
  info "Downloading JUnit 4.13.2..."
  JUNIT_URL="https://repo1.maven.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar"
  if command -v curl &> /dev/null; then
    curl -L -o "$JUNIT_JAR" "$JUNIT_URL" 2>/dev/null || error "Failed to download JUnit"
  elif command -v wget &> /dev/null; then
    wget -q -O "$JUNIT_JAR" "$JUNIT_URL" || error "Failed to download JUnit"
  else
    error "curl or wget not found. Cannot download JUnit."
  fi
  ok "Downloaded JUnit"
else
  ok "JUnit already present"
fi

# Download Hamcrest if missing
if [ ! -f "$HAMCREST_JAR" ]; then
  info "Downloading Hamcrest 1.3..."
  HAMCREST_URL="https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"
  if command -v curl &> /dev/null; then
    curl -L -o "$HAMCREST_JAR" "$HAMCREST_URL" 2>/dev/null || error "Failed to download Hamcrest"
  elif command -v wget &> /dev/null; then
    wget -q -O "$HAMCREST_JAR" "$HAMCREST_URL" || error "Failed to download Hamcrest"
  else
    error "curl or wget not found. Cannot download Hamcrest."
  fi
  ok "Downloaded Hamcrest"
else
  ok "Hamcrest already present"
fi

# ============================================================
#  Step 3 — Compile source code
# ============================================================

info "Compiling source code..."

mkdir -p "$BUILD_DIR"

# Find Java compiler
if ! command -v javac &>/dev/null; then
  error "javac not found. Please install JDK 17 or later."
fi

JAVAC_VERSION=$(javac -version 2>&1 | head -1)
ok "Using $JAVAC_VERSION"

# Build absolute-path classpath (required on Windows bash)
PROJ_DIR="$(pwd)"
CLASSPATH="$PROJ_DIR/$BUILD_DIR"
for jar in "$PROJ_DIR"/"$LIB_DIR"/*.jar; do
  CLASSPATH="$CLASSPATH:$jar"
done

# Compile main source
if [ $VERBOSE -eq 1 ]; then
  info "Compiling: $SRC_DIR/**/*.java → $BUILD_DIR/"
fi

javac \
  -source 17 -target 17 \
  -d "$BUILD_DIR" \
  -cp "$CLASSPATH" \
  $(find "$SRC_DIR" -name "*.java" ! -path "*/test/*" 2>/dev/null) \
  2>&1 | head -20 || error "Source compilation failed"

ok "Source code compiled"

# Compile test code
if [ $VERBOSE -eq 1 ]; then
  info "Compiling: $TEST_SRC/**/*.java → $BUILD_DIR/"
fi

javac \
  -source 17 -target 17 \
  -d "$BUILD_DIR" \
  -cp "$CLASSPATH" \
  $(find "$TEST_SRC" -name "*.java" 2>/dev/null) \
  2>&1 | head -20 || error "Test compilation failed"

ok "Test code compiled"

# ============================================================
#  Step 4 — Run tests
# ============================================================

echo ""
info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
info "Running tests..."
info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

START_TIME=$(date +%s)

if [ -n "$ONLY_CLASS" ]; then
  info "Running only: $ONLY_CLASS"
  TEST_CLASS="test.${ONLY_CLASS}Tests"
else
  info "Running full test suite"
  TEST_CLASS="test.AllTests"
fi

# Run tests with JUnit
java \
  -cp "$CLASSPATH" \
  org.junit.runner.JUnitCore \
  "$TEST_CLASS" 2>&1 | tee /tmp/test_output.txt

TEST_EXIT_CODE=${PIPESTATUS[0]}

END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))

# ============================================================
#  Step 5 — Parse and report results
# ============================================================

echo ""
info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Extract test statistics from output
TEST_OUTPUT=$(cat /tmp/test_output.txt)

if echo "$TEST_OUTPUT" | grep -q "OK"; then
  # All tests passed
  TESTS_RUN=$(echo "$TEST_OUTPUT" | grep "^OK" | grep -o "[0-9]* test" | head -1 | awk '{print $1}')
  success "All tests passed! ($TESTS_RUN tests, ${ELAPSED}s)"
  echo ""
  exit 0
elif echo "$TEST_OUTPUT" | grep -q "FAILURES"; then
  # Some tests failed
  TESTS_RUN=$(echo "$TEST_OUTPUT" | grep "Tests run:" | awk -F'[,:]' '{print $1}' | grep -o "[0-9]*$")
  FAILURES=$(echo "$TEST_OUTPUT" | grep "Failures:" | awk '{print $2}')
  ERRORS=$(echo "$TEST_OUTPUT" | grep "Errors:" | awk '{print $2}')

  echo ""
  warn "Tests completed with failures (${ELAPSED}s)"
  echo ""

  if [ -n "$TESTS_RUN" ]; then
    printf "  Tests run: %s\n" "$TESTS_RUN"
  fi
  if [ -n "$FAILURES" ]; then
    printf "  ${RED}Failures: %s${NC}\n" "$FAILURES"
  fi
  if [ -n "$ERRORS" ]; then
    printf "  ${RED}Errors: %s${NC}\n" "$ERRORS"
  fi
  echo ""

  # Show failed test names
  echo "Failed tests:"
  echo "$TEST_OUTPUT" | grep "^  " | head -20
  echo ""

  exit 1
else
  # Unknown error
  echo "$TEST_OUTPUT"
  error "Test run failed with exit code $TEST_EXIT_CODE"
fi
