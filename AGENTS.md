# Repository Guidelines

## Project Structure & Module Organization
The Spring Boot app lives in `src/main/java/click/yinsb`. Controllers sit in `controller`, Temporal wiring spans `workflow`, `starter`, and `activities`, DTOs live in `dto`, and infrastructure knobs in `config`. Shared configs and assets belong under `src/main/resources`. Tests mirror this layout in `src/test/java`, with integration smoke checks in `SpringTemporalApplicationTests`. Infra manifests include `docker-compose.yml` for the Temporal/Postgres/UI stack and `prometheus.yml` for metrics scraping.

## Build, Test, and Development Commands
- `mvn clean verify` – compile plus tests; run before every PR.
- `mvn spring-boot:run` – serve the API at `http://localhost:8080` with live reload.
- `docker compose up temporal postgres temporal-ui` – start Temporal, Postgres, and the UI; add `prometheus` for metrics on `9091`.
- `mvn package -DskipTests` – build the runnable JAR in `target/` when you only need an artifact.

## Coding Style & Naming Conventions
Target Java 21 on Spring Boot 3.5. Use four-space indentation, Lombok for DTO boilerplate, and constructor injection for Spring beans. Controllers end with `Controller`, workflow contracts with `Workflow`, implementations with `*Impl`, and activity interfaces with `Activities`. Keep API payloads under `click.yinsb.dto`, and avoid cross-package imports unless necessary.

## Testing Guidelines
JUnit 5 ships via `spring-boot-starter-test`. Keep file names ending in `Tests` so Surefire discovers them. Mock Temporal stubs for unit coverage; use the Docker stack for workflow integration. Each new activity or workflow branch should have a happy-path and failure-path assertion. Run `mvn test` before pushing, and narrow investigations with `mvn -Dtest=TravelWorkflowTests test`.

## Commit & Pull Request Guidelines
History favors short, imperative commit titles (e.g., `add micrometer and prometheus`). Bundle related work per commit and reference issues in the body when relevant. Pull requests need a concise summary of behavior changes, evidence of testing (`mvn clean verify`, curl traces, Temporal UI screenshots), and a note about any schema or docker-compose adjustments so reviewers refresh their environments.

## Environment Notes
Expose Temporal on `7233` and Temporal UI on `8088` through Docker when validating workflows. Keep Prometheus targets aligned with `prometheus.yml` whenever new metrics endpoints appear. Do not commit `.idea/`, `target/`, or other generated artifacts; `.gitignore` already handles them.
