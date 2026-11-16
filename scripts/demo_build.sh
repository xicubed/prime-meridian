#!/usr/bin/env bash
# The demo for class (GDD §4.8, §6): a shadow-cljs release build of the game
# with pretend data only (pm.core/DEMO: no twin's file, crew status, crew log,
# hold or bridge), plus the static files it loads, in dist/demo/. Host it at a
# site root (the game loads /js, /kit, /assets, ... from there). Never copies
# state/ (real data lives outside public/ anyway) or the kit's source pack.
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf dist/demo
npx shadow-cljs release demo
mkdir -p dist/demo
# everything public/ serves except the dev build and the kit's original page
rsync -a --exclude 'js/' --exclude 'state/' --exclude '*.orig' --exclude '.DS_Store' public/ dist/demo/
du -sh dist/demo
echo "demo-build: dist/demo ready (serve it at a site root)"
