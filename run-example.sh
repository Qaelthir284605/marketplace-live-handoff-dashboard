#!/bin/sh
set -eu
OUT="${TMPDIR:-/tmp}/marketplace-dashboard-classes"
mkdir -p "$OUT"
javac -d "$OUT" $(find src/main/java -name '*.java')
java -cp "$OUT" example.marketplace.MarketplaceDashboardServer
