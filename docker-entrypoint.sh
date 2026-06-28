#!/bin/sh
set -e

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

# Optional corporate CA truststore. TRUSTSTORE_PATH is read by the agent at runtime
# (Reactor Netty loads the file directly). Do not pass javax.net.ssl.trustStore here:
# GraalVM native images fall back to DummyX509TrustManager when that file is missing.
if [ -n "${TRUSTSTORE_PATH}" ]; then
  if [ ! -f "${TRUSTSTORE_PATH}" ]; then
    echo "ERROR: TRUSTSTORE_PATH=${TRUSTSTORE_PATH} does not exist inside the container." >&2
    echo "Check the docker-compose volume mount path." >&2
    exit 1
  fi
  if [ ! -r "${TRUSTSTORE_PATH}" ]; then
    echo "ERROR: TRUSTSTORE_PATH=${TRUSTSTORE_PATH} is not readable." >&2
    exit 1
  fi
  # Also set JVM truststore for non-HTTP clients (JDBC, cloud SDKs).
  set -- \
    -Djavax.net.ssl.trustStore="${TRUSTSTORE_PATH}" \
    -Djavax.net.ssl.trustStoreType="${TRUSTSTORE_TYPE:-JKS}" \
    -Djavax.net.ssl.trustStorePassword="${TRUSTSTORE_PASSWORD:-changeit}" \
    "$@"
fi

exec /quemsi-agent "$@"
