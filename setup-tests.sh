#!/usr/bin/env bash
# ============================================================
#  Trade Empire — Test Setup
# ============================================================
#
#  Downloads JUnit 4 and Hamcrest for testing.
#  Run this once before running tests.
#
# ============================================================

set -euo pipefail

# ── Colors ─────────────────────────────────────────────────

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'   # No Color

info()  { printf "${CYAN}[INFO]${NC}  %s\n" "$*"; }
ok()    { printf "${GREEN}[OK]${NC}    %s\n" "$*"; }
warn()  { printf "${YELLOW}[WARN]${NC}  %s\n" "$*"; }
error() { printf "${RED}[ERROR]${NC} %s\n" "$*"; exit 1; }

# ── Resolve project root ─────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

LIB_DIR="WebContent/WEB-INF/lib"

if [ ! -d "$LIB_DIR" ]; then
    error "Library directory not found: $LIB_DIR"
fi

# ── Download JARs ───────────────────────────────────────────

info "Setting up JUnit 4 and Hamcrest..."

# JUnit 4.13.2
JUNIT_URL="https://repo1.maven.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar"
JUNIT_FILE="$LIB_DIR/junit-4.13.2.jar"

# Hamcrest 1.3
HAMCREST_URL="https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"
HAMCREST_FILE="$LIB_DIR/hamcrest-core-1.3.jar"

# Download JUnit
if [ -f "$JUNIT_FILE" ]; then
    ok "JUnit already present: $JUNIT_FILE"
else
    info "Downloading JUnit 4.13.2..."
    if command -v curl &> /dev/null; then
        curl -L -o "$JUNIT_FILE" "$JUNIT_URL" || error "Failed to download JUnit"
        ok "Downloaded JUnit"
    elif command -v wget &> /dev/null; then
        wget -O "$JUNIT_FILE" "$JUNIT_URL" || error "Failed to download JUnit"
        ok "Downloaded JUnit"
    else
        error "curl or wget not found. Please download manually from: $JUNIT_URL"
    fi
fi

# Download Hamcrest
if [ -f "$HAMCREST_FILE" ]; then
    ok "Hamcrest already present: $HAMCREST_FILE"
else
    info "Downloading Hamcrest 1.3..."
    if command -v curl &> /dev/null; then
        curl -L -o "$HAMCREST_FILE" "$HAMCREST_URL" || error "Failed to download Hamcrest"
        ok "Downloaded Hamcrest"
    elif command -v wget &> /dev/null; then
        wget -O "$HAMCREST_FILE" "$HAMCREST_URL" || error "Failed to download Hamcrest"
        ok "Downloaded Hamcrest"
    else
        error "curl or wget not found. Please download manually from: $HAMCREST_URL"
    fi
fi

# ── Summary ─────────────────────────────────────────────────

info "Verifying JARs..."
ls -lh "$JUNIT_FILE" "$HAMCREST_FILE" || error "JARs not found"

ok "Test setup complete!"
printf "\n${CYAN}Next steps:${NC}\n"
printf "  1. Ensure application is running: ./setup.sh\n"
printf "  2. Run tests in Eclipse: Right-click src/test/ → Run As → JUnit Test\n"
printf "  3. Or run all tests: src/test/AllTests.java\n"
printf "\nFor detailed testing guide, see TESTING.md\n\n"
