# LogSpectra Backend

Spring Boot service for log ingestion, search, real-time streaming, AI-assisted analysis, and issue grouping.

## License

This project is licensed under the Apache License 2.0. See `LICENSE` for the full text.

## What This Service Does

- Ingests service logs from Kafka and indexes them into Elasticsearch.
- Exposes REST APIs for search, analytics, projects, users, severity rules, and AI settings.
- Streams live logs and AI analysis tokens/events to clients using SSE.
- Publishes error alerts to Kafka and SSE (`alert` events on the live log stream).
- Persists operational data in PostgreSQL and uses Redis for runtime support.

## Architecture Overview

```
Producer Apps -> Kafka(service-logs) -> LogIngestionConsumer
                                   |-> Elasticsearch (search/analytics)
                                   |-> Kafka(error-alerts)
                                   |-> SSE push (/api/v1/logs/stream)

Frontend -> REST API (/api/v1/*) -> Services -> PostgreSQL / Elasticsearch / AI Providers
Frontend -> SSE (/api/v1/logs/stream, /api/v1/logs/analyze)
```

Key components:

- `src/main/java/com/loganalyzer/api/controller`: HTTP API layer.
- `src/main/java/com/loganalyzer/service`: business logic and orchestration.
- `src/main/java/com/loganalyzer/ai`: provider abstraction and streaming integrations (OpenAI, Claude, Ollama).
- `src/main/java/com/loganalyzer/kafka`: ingestion consumer and alert producer.
- `src/main/java/com/loganalyzer/elasticsearch`: documents, queries, repository logic.
- `src/main/resources/db/changelog`: Liquibase migrations.

## Tech Stack

- Java 21, Spring Boot 3.3.x
- Spring Security (JWT)
- Spring Data JPA + PostgreSQL
- Elasticsearch Java Client 8.15.x
- Spring Kafka
- Redis
- Liquibase
- Springdoc OpenAPI (Swagger UI)

## Prerequisites

- JDK 21 (project is compiled for Java 21)
- Maven 3.9+
- Docker Desktop (for local infra)

## Quick Start

### 1) Start dependencies (without backend)

```powershell
docker compose up -d postgres redis zookeeper kafka elasticsearch
```

### 2) Run backend locally

```powershell
mvn clean spring-boot:run
```

### 3) Or run everything with Compose

```powershell
docker compose up -d --build
```

## Configuration

Primary config file: `src/main/resources/application.yml`.

Most important environment variables:

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`
- `KAFKA_BOOTSTRAP_SERVERS`
- `ELASTICSEARCH_URIS`, `ELASTICSEARCH_USERNAME`, `ELASTICSEARCH_PASSWORD`
- `JWT_SECRET_BASE64`, `JWT_EXPIRATION_SECONDS`
- `AI_PROVIDER`, `AI_DEFAULT_PROVIDER`
- `AI_OPENAI_BASE_URL`, `AI_OPENAI_API_KEY`
- `AI_CLAUDE_BASE_URL`, `AI_CLAUDE_API_KEY`, `AI_CLAUDE_API_VERSION`
- `AI_OLLAMA_HOST`, `AI_OLLAMA_PORT`

> Never commit real API keys or secrets.

## API and Streaming

- OpenAPI JSON: `/api-docs`
- Swagger UI: `/swagger-ui.html`
- Login (public): `POST /api/v1/auth/login`
- Live logs SSE: `GET /api/v1/logs/stream`
- AI analysis SSE: `POST /api/v1/logs/analyze`

### AI Analysis SSE Event Types

`/api/v1/logs/analyze` emits JSON payloads with event names:

- `progress`: analysis lifecycle updates
- `token`: incremental model output chunks
- `result`: final structured analysis object
- `error`: failure details
- `complete`: stream completion marker

## Kafka Topics

- `service-logs`: incoming logs for ingestion.
- `error-alerts`: emitted for `ERROR` logs.

Topic names are configurable via `kafka.topics.*`.

## Database and Migrations

- Liquibase is enabled by default.
- Master changelog: `src/main/resources/db/changelog/db.changelog-master.yaml`.
- Add new migrations under `src/main/resources/db/changelog/changes`.

## Testing

Run all tests:

```powershell
mvn test
```

Run a single test class:

```powershell
mvn -Dtest=LogAnalysisServiceTest test
```

## Docker Image

Build image:

```powershell
docker build -t logspectra/logspectra-backend:latest .
```

Push tags:

```powershell
docker push logspectra/logspectra-backend:latest
docker push logspectra/logspectra-backend:1.0.0
```

## Troubleshooting

- `UnsupportedClassVersionError` usually means runtime JRE is older than build JDK.
  - This project targets Java 21 class files.
- Elasticsearch memory errors in Docker:
  - adjust `ES_JAVA_OPTS` in `docker-compose.yml`.

## Contributing

Please read `CONTRIBUTING.md` for branch strategy, coding standards, testing expectations, and PR checklist.

