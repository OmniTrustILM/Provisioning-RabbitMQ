# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

`provisioning-rabbitmq` is a Spring Boot service for the ILM platform. It provisions and decommissions RabbitMQ queues and bindings for proxy instances, and issues signed JWT configuration tokens together with rendered installation instructions.

The REST contract lives in `src/main/resources/proxy-provisioning-api.yaml`. The OpenAPI generator produces the API interfaces and models from it at build time; those types are never edited by hand.

## Tech Stack

- Java 21
- Spring Boot (managed by the `com.otilm:dependencies` parent)
- Maven
- JJWT for token signing, JMustache for templating
- Testcontainers for integration tests

## Build and Run

### Build

```bash
mvn clean verify
```

### Run tests

```bash
mvn test                # unit tests only
mvn verify              # unit + integration tests (requires Docker)
```

Integration tests (`*IT`) run under failsafe and start a RabbitMQ container through Testcontainers, loading `rabbitmq/definitions.json` into it.

### Check code coverage

```bash
mvn verify
# Report at target/site/jacoco/index.html
```

The JaCoCo report is bound to the `verify` phase so integration-test coverage is included. The parent's `package`-phase report runs first and only sees unit tests.

### Run locally

```bash
export SECURITY_API_KEY=my-secret-api-key
export TOKEN_SIGNING_KEY=my-signing-key-at-least-32-characters-long
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile enables Spring Boot's Docker Compose support, which starts the broker defined in `compose.yaml`.

### Local SonarQube analysis

```bash
./scripts/sonar-local.sh
```

Runs an ephemeral SonarQube container and reports the gate, measures and issues. It has no "new code" baseline, so treat it as a smoke check — SonarCloud on the pull request is authoritative.

## Configuration

Set through `application.yml` and environment variables. `README.md` holds the full table; the ones that shape behaviour most:

- `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBITMQ_VIRTUAL_HOST` — broker connection used to manage queues and bindings
- `PROXY_AMQP_URL` — external AMQP URL embedded in proxy tokens, which may differ from the internal host
- `PROXY_EXCHANGE` — exchange proxies bind to (default `ilm-proxy`)
- `SECURITY_API_KEY`, `SECURITY_API_KEY_ENABLED` — `X-API-Key` authentication
- `TOKEN_SIGNING_KEY` — HMAC-SHA256 key, at least 32 characters

## Messaging Topology

The topology is defined by `charts/messaging-rabbitmq` in `OmniTrustILM/helm-charts`; `rabbitmq/definitions.json` mirrors it for local development. Changing exchange, queue or vhost names here without changing the chart will break interoperability with Core and the proxies.

- virtual host `/`
- direct exchange `ilm` — Core's own queues bind to it
- topic exchange `ilm-proxy` — proxy request and response bindings
- request routing keys `coremessage.<proxyCode>`, response routing keys `proxymessage.<proxyCode>`

## Architecture

```
Controller → Service (interface + Impl) → RabbitAdminSupport → RabbitAdmin
```

- **Package**: `com.otilm.provisioning.rabbitmq`
- `controller` — thin adapters implementing the generated API interfaces
- `service` — provisioning logic, token generation, template rendering
- `config` — configuration property records, the API-key filter, request logging
- `exception` — domain exceptions and the `@RestControllerAdvice` that maps them to responses
- `api`, `model` — generated from the OpenAPI contract, not present in the source tree

## Conventions

- Service interfaces with an `*Impl` implementation
- Configuration bound to `record` types with `@ConfigurationProperties`
- Unit tests alongside the class under test; integration tests named `*IT`
- 80%+ test coverage, less than 3% duplication, no open SonarCloud issues
- Generated sources are excluded from Sonar analysis, coverage and duplication

## Docker

```bash
docker build -t ilm/provisioning-rabbitmq:latest .
```

The image is Alpine-based with a jlink-trimmed runtime. The application lives in `/opt/provisioning-rabbitmq` and runs as the non-root `provisioning-rabbitmq` user (uid/gid 10001). The path and account are named after the component rather than the brand, so a future rebrand does not move them. `docker/` is copied into the image root, so `docker/opt/provisioning-rabbitmq/entry.sh` becomes the entrypoint.
