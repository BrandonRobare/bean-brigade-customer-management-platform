FROM nginxinc/nginx-unprivileged:stable-alpine@sha256:15c994d10d6d78658721c3bcafff14cb281fba2a4bdf9d5ba92c416a472516e3
USER root
RUN apk del --no-cache nginx-module-geoip nginx-module-image-filter nginx-module-njs nginx-module-xslt
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist/lab50-crm-ui/browser/ /usr/share/nginx/html/
USER 101:101
EXPOSE 8080
