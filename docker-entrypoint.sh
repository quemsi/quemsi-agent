#!/bin/sh
set -e

# GraalVM native images embed the JDK trust store at build time and do not use the
# OS certificate store. On corporate networks with SSL inspection, mount a
# truststore that includes your company's root CA and set TRUSTSTORE_PATH.
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
  set -- "$@" -DCLIENT_ID="$client_id"
fi
if [ -n "$client_secret" ]; then
  set -- "$@" -DCLIENT_SECRET="$client_secret"
fi

if [ -z "$client_id" ] || [ -z "$client_secret" ]; then
  echo "ERROR: CLIENT_ID and CLIENT_SECRET must be set." >&2
  exit 1
fi

exec /quemsi-agent "$@"
