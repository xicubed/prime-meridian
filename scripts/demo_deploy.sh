#!/usr/bin/env bash
# Deploy the class demo to the PIN-locked site (Assignment 7, playable link):
# builds dist/demo/ (scripts/demo_build.sh) and rsyncs it to the Lightsail box's
# docroot, served by Apache at https://pm.perstitio.us behind basic auth.
#   bb demo-deploy            build + deploy
#   bb demo-deploy --no-build deploy what's in dist/demo/
# The ssh host `lightsail` is in ~/.ssh/config (admin@44.226.174.176, the bot's
# deploy key); the docroot is group-writable by admin (setgid www-data).
set -euo pipefail
cd "$(dirname "$0")/.."
[ "${1:-}" = "--no-build" ] || bash scripts/demo_build.sh
rsync -az --delete --stats --exclude '.DS_Store' dist/demo/ lightsail:/var/www/pm/
echo "demo-deploy: https://pm.perstitio.us (user guest33 + the PIN)"
