#!/bin/sh

appHome="/opt/provisioning-rabbitmq"
# shellcheck source=docker/opt/provisioning-rabbitmq/static-functions
. "${appHome}/static-functions"

log "INFO" "Launching the Provisioning RabbitMQ"

# JAVA_OPTS carries a space-separated option list and must be split into words.
# shellcheck disable=SC2086
exec java $JAVA_OPTS -jar "${appHome}/app.jar"
