#!/usr/bin/env bash
# ============================================================
#  Trade Empire — Quick Test Runner
# ============================================================
#
#  Faster test script for development (assumes app is running).
#  For first run or after changes to dependencies, use ./run-tests.sh
#
#  USAGE:
#    ./test.sh              # Run all tests
#    ./test.sh Auth         # Run only AuthenticationTests
#    ./test.sh Production   # Run only ProductionAndTickTests
#
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

BUILD_DIR="build"
LIB_DIR="WebContent/WEB-INF/lib"
SRC_DIR="src"
TEST_SRC="$SRC_DIR/test"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

info()  { printf "${CYAN}[INFO]${NC}  %s\n" "$*"; }
ok()    { printf "${GREEN}[✓]${NC}    %s\n" "$*"; }
error() { printf "${RED}[✗]${NC}    %s\n" "$*"; exit 1; }

# Find test dependencies
if [ ! -f "$LIB_DIR/junit-4.13.2.jar" ]; then
  error "JUnit not found. Run: ./run-tests.sh"
fi

# Build absolute-path classpath (required on Windows bash)
PROJ_DIR="$(pwd)"
CLASSPATH="$PROJ_DIR/$BUILD_DIR"
for jar in "$PROJ_DIR"/"$LIB_DIR"/*.jar; do
  CLASSPATH="$CLASSPATH:$jar"
done

# Determine test class - match against actual test files
TEST_CLASS="test.AllTests"
if [ -n "${1:-}" ]; then
  # Try exact match first
  if [ -f "$TEST_SRC/${1}Tests.java" ]; then
    TEST_CLASS="test.${1}Tests"
  # Then try with partial matching for convenience
  elif [ -f "$TEST_SRC/"*"${1}"*.java ]; then
    # Find matching test file
    MATCHED=$(find "$TEST_SRC" -maxdepth 1 -name "*${1}*.java" ! -name "RestTestBase.java" ! -name "TestDataBuilder.java" ! -name "AllTests.java" | head -1)
    if [ -n "$MATCHED" ]; then
      CLASS_NAME=$(basename "$MATCHED" .java)
      TEST_CLASS="test.$CLASS_NAME"
    fi
  fi
fi

# Compile (quick, just in case)
mkdir -p "$BUILD_DIR"

info "Compiling..."
javac \
  -source 17 -target 17 \
  -d "$BUILD_DIR" \
  -cp "$CLASSPATH" \
  $(find "$SRC_DIR" -name "*.java" ! -path "*/test/*" 2>/dev/null) \
  $(find "$TEST_SRC" -name "*.java" 2>/dev/null) \
  2>&1 | grep -E "(error|Error)" || true

ok "Running: $TEST_CLASS"
echo ""

# Run tests
java \
  -cp "$CLASSPATH" \
  org.junit.runner.JUnitCore \
  "$TEST_CLASS"
