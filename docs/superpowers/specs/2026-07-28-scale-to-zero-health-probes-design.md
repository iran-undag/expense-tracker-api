# Scale-to-Zero Database and Health Probe Design

## Goal

Allow the production API to scale to zero without periodic health checks consuming Azure SQL serverless vCore seconds. When a request wakes the API, startup must tolerate the database resume delay.

The Azure Container App is configured separately with zero minimum replicas and two maximum replicas.

## Application Configuration

Production HikariCP will keep no minimum idle database connections and will retire unused connections after one minute:

- `minimum-idle`: `0`
- `idle-timeout`: `60000` milliseconds
- `maximum-pool-size`: unchanged, with its existing environment-variable override

The SQL Server JDBC driver will tolerate an Azure SQL serverless resume:

- `loginTimeout`: `120` seconds
- `connectRetryCount`: `5`
- `connectRetryInterval`: `15` seconds

Each value will retain an environment-variable override. Azure SQL error 40613 is already in the Microsoft JDBC driver's built-in transient connection error list, so no custom retry rules are required.

## Health Probes

Spring Boot Actuator probe support will be enabled. The two health groups will explicitly contain only their matching application-availability indicators:

- `/actuator/health/liveness` includes `livenessState`
- `/actuator/health/readiness` includes `readinessState`

Neither endpoint will include the datasource health indicator or acquire a database connection. The existing aggregate `/actuator/health` endpoint remains available for on-demand diagnostics and may continue to check the database.

Production security will permit `/actuator/health/**` without authentication so Azure Container Apps can call the grouped endpoints.

## Azure Container Apps Configuration

The container definition should use port `8081` and these probe settings:

| Probe | Path | Initial delay | Period | Timeout | Failure threshold |
| --- | --- | ---: | ---: | ---: | ---: |
| Startup | `/actuator/health/liveness` | 5 seconds | 5 seconds | 5 seconds | 60 |
| Liveness | `/actuator/health/liveness` | 0 seconds | 30 seconds | 5 seconds | 3 |
| Readiness | `/actuator/health/readiness` | 0 seconds | 10 seconds | 5 seconds | 6 |

Each probe uses a success threshold of one. The startup settings provide a five-minute failure window after the initial delay.

Probe traffic generated internally by Container Apps only exists while a replica is running. An external uptime monitor must not poll the application if scale-to-zero behavior is required, because external HTTP requests can activate or keep a replica running.

The Azure resource configuration is not stored in this repository, so this change documents but does not mutate those external settings.

## Failure Behavior

Database unavailability must not restart the container or remove it from service through health probes. Database-backed requests can still fail if SQL does not resume within the configured connection retry window; those failures will follow the API's existing exception handling.

A failure of the liveness availability state tells Container Apps that the process should be restarted. Readiness reports whether the completed Spring application is ready to receive traffic.

## Verification

Tests will verify that:

- Both grouped health endpoints return HTTP 200 in a healthy application.
- The liveness response includes `livenessState` and excludes `db`.
- The readiness response includes `readinessState` and excludes `db`.
- Production security allows unauthenticated requests to both grouped endpoints.
- The production property file contains the idle-pool and JDBC retry defaults with environment-variable overrides.

The full Maven test suite will run after implementation.
