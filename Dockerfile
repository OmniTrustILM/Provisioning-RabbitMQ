# Build stage
FROM maven:3.9.15-eclipse-temurin-21 AS build

COPY src /home/app/src
COPY pom.xml /home/app
COPY docker /home/app/docker

RUN mvn -f /home/app/pom.xml clean package -DskipTests

# Optimize stage
FROM eclipse-temurin:21-jdk-alpine AS optimize

COPY --from=build /home/app/target/*.jar /app/app.jar

WORKDIR /app

# List jar modules
RUN jar xf app.jar
RUN jdeps \
  --ignore-missing-deps \
  --print-module-deps \
  --multi-release 21 \
  --recursive \
  --class-path 'BOOT-INF/lib/*' \
  app.jar > modules.txt

# Create a custom Java runtime
RUN $JAVA_HOME/bin/jlink \
  --add-modules $(cat modules.txt),jdk.crypto.ec \
  --strip-debug \
  --no-man-pages \
  --no-header-files \
  --compress=2 \
  --output /javaruntime

# Package stage
FROM alpine:latest

ENV JAVA_HOME=/opt/jre
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# copy optimized JRE
COPY --from=optimize /javaruntime $JAVA_HOME

LABEL org.opencontainers.image.authors="ILM <support@otilm.com>"

# add non root user
RUN apk upgrade --no-cache \
    && addgroup --system --gid 10001 provisioning-rabbitmq \
    && adduser --system --home /opt/provisioning-rabbitmq --uid 10001 --ingroup provisioning-rabbitmq provisioning-rabbitmq

COPY --from=build /home/app/docker /
COPY --from=build /home/app/target/*.jar /opt/provisioning-rabbitmq/app.jar

WORKDIR /opt/provisioning-rabbitmq

ENV PORT=8080

ENV HTTP_PROXY=
ENV HTTPS_PROXY=
ENV NO_PROXY=

USER 10001

ENTRYPOINT ["/opt/provisioning-rabbitmq/entry.sh"]
