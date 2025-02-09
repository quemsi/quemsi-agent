FROM ubuntu:jammy
COPY target/quemsi-agent /quemsi-agent
CMD ["/quemsi-agent"]