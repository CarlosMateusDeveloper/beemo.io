FROM caddy:2.10-alpine
RUN mkdir -p /data /config && chown -R 10001:10001 /data /config
COPY deploy/proxy.Caddyfile /etc/caddy/Caddyfile
USER 10001:10001
EXPOSE 8080 8443
