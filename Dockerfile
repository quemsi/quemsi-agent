FROM ubuntu:jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY target/quemsi-agent /quemsi-agent
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /quemsi-agent /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]
