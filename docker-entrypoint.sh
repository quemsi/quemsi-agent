#!/bin/sh
set -e

# NOTE: /bin/sh removes environment variables whose names contain hyphens (e.g.
# CLIENT-ID) before this script runs. Use CLIENT_ID and CLIENT_SECRET in
# docker-compose / docker run -e, not CLIENT-ID / CLIENT-SECRET.

if [ -n "${TRUSTSTORE_PATH}" ]; then
  set -- \
    -Djavax.net.ssl.trustStore="${TRUSTSTORE_PATH}" \
    -Djavax.net.ssl.trustStorePassword="${TRUSTSTORE_PASSWORD:-changeit}" \
    "$@"
else
  set -- "$@"
fi

client_id=$(printenv CLIENT_ID 2>/dev/null || true)
client_secret=$(printenv CLIENT_SECRET 2>/dev/null || true)

if [ -n "$client_id" ]; then
  set -- "$@" -DCLIENT-ID="$client_id" -DCLIENT_ID="$client_id"
fi
if [ -n "$client_secret" ]; then
  set -- "$@" -DCLIENT-SECRET="$client_secret" -DCLIENT_SECRET="$client_secret"
fi

if [ -z "$client_id" ] || [ -z "$client_secret" ]; then
  echo "ERROR: CLIENT_ID and CLIENT_SECRET must be set." >&2
  echo "Use underscore names in docker-compose, e.g. CLIENT_ID=agent-1 (not CLIENT-ID)." >&2
  exit 1
fi

exec /quemsi-agent "$@"
