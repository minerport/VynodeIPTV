FROM golang:1.27.2-bookworm AS build
WORKDIR /src
COPY server/ ./
RUN go test ./... && CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /iptv-server .
FROM debian:bookworm-slim
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends ffmpeg ca-certificates tini curl tzdata && rm -rf /var/lib/apt/lists/* && groupadd -g 10001 iptv && useradd -u 10001 -g iptv -M iptv && mkdir /config /transcode /recordings && chown iptv:iptv /config /transcode /recordings
COPY --from=build /iptv-server /usr/local/bin/iptv-server
ARG VERSION=0.14.2
ARG REVISION=unknown
LABEL org.opencontainers.image.title="VynodeIPTV" \
      org.opencontainers.image.description="Self-hosted IPTV server and browser player for Android TV and Unraid" \
      org.opencontainers.image.source="https://github.com/minerport/VynodeIPTV" \
      org.opencontainers.image.url="https://github.com/minerport/VynodeIPTV" \
      org.opencontainers.image.version=$VERSION \
      org.opencontainers.image.revision=$REVISION
USER 10001:10001
ENV IPTV_CONFIG=/config IPTV_SCRATCH=/transcode IPTV_RECORDINGS=/recordings IPTV_LISTEN=:8080
EXPOSE 8080
VOLUME ["/config", "/transcode", "/recordings"]
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s CMD curl -fsS http://127.0.0.1:8080/healthz || exit 1
ENTRYPOINT ["/usr/bin/tini", "--", "/usr/local/bin/iptv-server"]
