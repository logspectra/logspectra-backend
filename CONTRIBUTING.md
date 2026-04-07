# Contributing to LogSpectra Backend

Thank you for contributing. This guide helps keep changes reliable, secure, and easy to review.

## Contribution Workflow

1. Create or confirm an issue first (bug, feature, refactor, or docs).
2. Get the issue assigned (or assign yourself).
3. Create a feature branch from `main` linked to that issue.
4. Implement changes in small, reviewable commits.
5. Add or update tests.
6. Run local checks.
7. Open a Pull Request that references the issue.

## Local Development Setup

### Prerequisites

- JDK 21
- Maven 3.9+
- Docker Desktop

### Start dependencies

```powershell
docker compose up -d postgres redis zookeeper kafka elasticsearch
```

### Run backend

```powershell
mvn clean spring-boot:run
```

### Run tests

```powershell
mvn test
```

## Branch and Commit Guidelines

- Branch naming examples:
  - `feature/123-sse-analysis-events`
  - `fix/456-elasticsearch-timezone-filter`
  - `chore/789-dependency-updates`
- Commit messages should be short, imperative, and specific.
  - Example: `Add SSE error event payload for AI streaming`

## Issue-First Policy

- Do not start implementation without an issue unless it is an urgent hotfix approved by a maintainer.
- Include the issue number in branch names and PR titles when possible.
- Link the issue in the PR description (for example: `Closes #123`).

## Architecture Rules (Important)

Keep layering clean and intentional:

- Controllers in `api/controller` only handle HTTP concerns.
- Business orchestration belongs in `service`.
- AI provider-specific behavior belongs in `ai`.
- Infrastructure integration belongs in dedicated packages (`kafka`, `elasticsearch`, `security`, `infrastructure`).
- Repositories should not contain business workflows.

When adding features, keep project-access validation and auth concerns consistent with existing patterns.

## Coding Standards

- Prefer constructor injection.
- Keep methods focused and easy to test.
- Avoid blocking request threads for long-running operations.
- For stream endpoints, propagate partial events and send explicit completion/error events.
- Avoid hardcoding secrets, URLs, and model keys; use config/environment.

## Testing Expectations

Every non-trivial change should include tests.

At minimum:

- Unit tests for changed service logic.
- Controller tests when API behavior changes.
- Kafka/stream tests when ingestion or real-time behavior changes.

Recommended test commands:

```powershell
mvn test
mvn -Dtest=LogAnalysisServiceTest test
mvn -Dtest=LogIngestionConsumerTest test
```

## Database and Liquibase

Schema changes must go through Liquibase:

1. Add a new changelog file under `src/main/resources/db/changelog/changes`.
2. Register it in `src/main/resources/db/changelog/db.changelog-master.yaml`.
3. Keep migrations forward-only and idempotent where possible.
4. Never edit already-applied migrations in shared environments.

## API and Compatibility

When changing API contracts:

- Keep backward compatibility where practical.
- Update DTOs, validation, and controller tests together.
- Verify OpenAPI output (`/api-docs`) remains accurate.
- Document new headers/query params/event types in `README.md`.

## Security and Secrets

- Do not commit credentials, JWT secrets, or provider API keys.
- Use environment variables for sensitive values.
- Preserve authentication and authorization checks when refactoring.

## Pull Request Checklist

Before requesting review, confirm:

- [ ] Code follows architecture and style conventions.
- [ ] Tests were added/updated and pass locally.
- [ ] Liquibase changes are included for DB schema updates.
- [ ] API/streaming behavior changes are documented.
- [ ] No secrets or generated artifacts were committed.

## PR Description Template

Use this structure in your PR body:

- **What changed**
- **Why it changed**
- **How it was tested**
- **Migration/ops impact**
- **Follow-up tasks (if any)**

## Review Process

- At least one maintainer review is required.
- Reviewers focus on correctness, regressions, security, and operability.
- Address feedback with follow-up commits (avoid force-push during active review unless requested).

Thanks for helping improve LogSpectra.


