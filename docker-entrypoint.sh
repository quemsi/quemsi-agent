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
fi

exec /quemsi-agent "$@"
