# Static visualization for bounded-pruned Yen + A*, A* vs Dijkstra, and the
# AllDirectedPaths sandwich prune. Single self-contained HTML file served by
# nginx-alpine.
FROM nginx:1.27-alpine

# Run as non-root for safer deployment.
RUN addgroup -S app && adduser -S -G app app \
    && touch /var/run/nginx.pid \
    && chown -R app:app /var/cache/nginx /var/run/nginx.pid /usr/share/nginx/html /etc/nginx/conf.d

COPY --chown=app:app index.html /usr/share/nginx/html/index.html
COPY --chown=app:app nginx.conf /etc/nginx/conf.d/default.conf

USER app

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -q --spider http://127.0.0.1:8080/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
