# quemsi-agent Architecture

Reference for understanding the agent’s role in Quemsi and how to extend it.
Captured for future work; extend as the design evolves.

**Version context:** `2.6.0-SNAPSHOT` · Java 21 · Spring Boot 3.3.6 · GraalVM native-image capable

---

## Role in the Quemsi system

**quemsi-agent is an edge worker, not a second API.** It long-polls quemsi-api for commands, materializes an `AgentModel`, and runs backup/restore pipelines on customer infrastructure. Almost all domain logic lives in **quemsi-model**; the agent is the thin runtime shell.

```
UI (ui-next)  →  quemsi-api  →  AgentCommandManager (queue)
                                      ↕ long-poll / REST
                               quemsi-agent (edge)
                                      ↓
                    DBs / Redis / local FS / Azure / S3
```

| Layer | Responsibility |
|--------|----------------|
| **quemsi-api** | Auth, agent registry, model assembly, command queue, execution history, retention orchestration |
| **quemsi-model** | Shared DTOs + flow engine (steps, sources, targets, subset, DB adapters) |
| **quemsi-agent** | Auth to Keycloak, fetch model, register resources, execute commands, report results/logs |
| **quemsi-commons** | Shared utilities (transitively via model) |

There is **no Maven dependency on quemsi-api** — only HTTP + shared model types.

Dependency direction: `commons` ← `model` ← `agent` and `api` (siblings).

---

## Project structure

```
quemsi-agent/
├── pom.xml
├── Dockerfile
├── docker-entrypoint.sh
└── src/main/java/com/quemsi/agent/
    ├── AgentApplication.java          # entry; --version / -v
    ├── AgentApplicationStartup.java   # ApplicationReady → coordinator.start()
    ├── AgentCoordinator.java          # init + command loop + watchdog
    ├── AgentShutdownListener.java
    ├── AgentRuntimeDetector.java      # windows / linux / docker / java
    ├── AgentRuntimeHintsRegistrar.java # GraalVM AOT hints
    ├── api/           # HTTP clients to API + Keycloak
    ├── aspect/        # GlobalErrorHandling (AOP → NotifyError)
    ├── config/        # Spring beans, WebClient, properties
    ├── control/       # Browser-facing control (home, download + schema builder)
    ├── flow/          # TimerImpl (Quartz cron)
    └── service/       # FlowManager, bean registry, storage, cmds
```

### Important classes

| Class | Role |
|--------|------|
| `AgentApplication` | Boot entry; excludes DB/WebFlux-server auto-config |
| `AgentApplicationStartup` | On `ApplicationReadyEvent`, calls `AgentCoordinator.start()` |
| `AgentCoordinator` | Fetches `AgentModel`, registers beans, runs command loop, idle watchdog |
| `AgentShutdownListener` | Stop loop, dispose Netty pool, shutdown virtual-thread executor |
| `api.QuemsiApi` | Declarative HTTP interface to `/api/agent/*` |
| `api.TokenApi` | Keycloak client-credentials token |
| `api.ApiManager` | Token cache + implements `com.quemsi.model.api.ApiClient` |
| `service.FlowManager` | Parse flow JSON → `Flow` steps; timer-triggered runs |
| `service.SpringBeanManager` | Dynamic singleton registration of timers/DS/drives/storages |
| `service.AgentCommandExecutor` | Dispatch to `service.cmd.*` handlers |
| `service.AgentBatchedLogger` | Queue + flush logs to API `/api/agent/logs` |
| `flow.TimerImpl` | Quartz cron → `tick()` runs attached flow runnables |
| `aspect.GlobalErrorHandling` | After-throwing on `service..*` → `NotifyError` to API |

**Not present:** REST controllers for app logic, Feign, Kafka, Rabbit, gRPC, plugin SPI (`ServiceLoader`).

---

## Runtime lifecycle

1. Authenticate to Keycloak (`client_credentials`) using `CLIENT_ID` / `CLIENT_SECRET`.
2. `GET /api/agent/all-model` with version + runtime → full `AgentModel`.
3. Register timers, datasources, drives, storages, flows into the Spring context.
4. Long-poll `GET /api/agent/next-command` forever; execute commands on virtual threads.
5. Report results/errors/logs via `POST /api/agent/agent-command` and `/logs`.
6. Idle watchdog: if no successful `next-command` within timeout (default 5m), exit code `2`.

Boot design choices:

- No embedded DB/JPA/Mongo auto-config — DBs are customer datasources registered at runtime.
- Quartz auto-config excluded — custom single-thread scheduler in `FlowConfig`.
- WebFlux *server* auto-config excluded — WebClient is HTTP *client* only; servlet Tomcat still starts on port `9082` (max 2 threads).

---

## Communication protocol

**Protocol:** REST over HTTP(S) via Spring 6 HTTP Interface + WebClient/Reactor Netty.

**Auth:** OAuth2 client credentials against Keycloak; tokens refreshed ~60s before expiry.

### API surface (`QuemsiApi` ↔ `AgentApiController`)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/agent/all-model` | Full agent model (+ version/runtime metadata) |
| GET | `/api/agent/next-command` | Long-poll next command (`DeferredResult`) |
| POST | `/api/agent/initate/{flowName}` | Start flow execution (typo preserved) |
| POST | `/api/agent/flow-execution` | Save flow execution |
| POST | `/api/agent/flow-execution-step` | Save step progress |
| GET | `/api/agent/find-version/{flowName}` | Resolve data version |
| POST | `/api/agent/agent-command` | Results / NotifyError from agent |
| POST | `/api/agent/logs` | Batched agent logs |
| POST | `/api/agent/download-grants/{token}/redeem` | Redeem one-time download grant |
| GET | `/api/agent/gdrive-credentials` | Google Drive credentials |

Optional TLS: `quemsi-api.truststore.*` / `TRUSTSTORE_*` for corporate CAs.

---

## Control plane (browser → agent)

Interactive user actions that must touch private storage or live schema go through a **ticketed control HTTP surface** on the agent (not the long-poll command queue).

| Concept | Detail |
|---------|--------|
| **controlBaseUrl** | One URL per agent (UI setup, stored on `agent.control_base_url`), e.g. `http://127.0.0.1:9082` |
| **Download grant** | User `POST /api/datas/files/{fileId}/download-grant` → opaque one-time ticket + `downloadUrl` |
| **Agent download** | Browser opens `GET {controlBaseUrl}/control/download?ticket=…` |
| **Redeem download** | Agent calls `POST /api/agent/download-grants/{token}/redeem` with its JWT, then streams via `Storage.getFiles` |
| **Builder session** | User `POST /api/builder-sessions` → agent opens `GET /control/builder?ticket=…`, browses schema, submits config |
| **Builder modes** | `CLEAR_TABLES`, `DROP_TABLES`, `MASK_COLUMNS`, `UPDATE_SEQUENCES` |

### Download

```
UI  →  API (create grant)  →  UI opens agent URL
Agent  →  API (redeem)  →  stream file bytes to browser
```

Progress uses the browser’s native download UI (`Content-Disposition` + `Content-Length`).

### Schema builder (ClearTables / DropTables / MaskColumns / UpdateSequences / Subset)

```
Flow editor  →  API create builder session  →  popup agent builder
Agent redeem open ticket  →  list tables/columns/sequences from DbModel
Apply  →  API stores result_config  →  popup closes / postMessage
Flow editor fetches GET /api/builder-sessions/{id}/result  →  merge into step
```

- Session TTL ~30 minutes; result pickup ~10 minutes after complete.
- Only **configuration** returns to quemsi.com — not row data.
  - Clear/Drop: `{ all, tables }`
  - Mask: `{ columns: [{ schema, table, column }] }` (mask type/char stay in the flow editor)
  - Sequences: `{ customMappings: [{ sequence, schema, table, column }] }` (template/column stay in the flow editor)
  - Subset: `{ enabled, drivers: [{ table, where, limit, entireTable }] }` merged into From `source.subset`
- `DROP_TABLES` with `all: true` means drop tables plus views/sequences/triggers/functions/etc. at runtime; selective mode drops listed tables only.
- `MASK_COLUMNS` schema browser infers source from the flow’s **From** step: `StoredData` → resolve that version’s archive and load `db-model.json`; `RdbmsBackup` → live From datasource browse. Runtime always masks using archive `db-model.json`.
- `SUBSET` uses the live From datasource. Builder browse/preview run on the agent (`/control/builder/api/browse-rows`, `/preview-subset`); From **Count** remains the API→agent sync `PreviewSubset` command.
- Agent UI is static HTML/JS under `classpath:/control-builder/` (mode-aware).
- Opening the control base URL (`/`) serves a landing page from `classpath:/control-home/` that identifies the agent and directs users to the Quemsi UI.

**Follow-up:** browser-side availability check against `controlBaseUrl` (distinct from long-poll ONLINE).

---

## Domain concepts

| Concept | Meaning |
|---------|---------|
| **Agent** | Edge process identified by Keycloak client (`CLIENT_ID`); owns resources for one company environment |
| **AgentModel** | Snapshot: agentId, datasources, timers, local/Azure/S3 drives, storages, flows |
| **Datasource** | Named JDBC/Mongo factory; credentials may be env-var names (`useEnvVar`) |
| **Drive** | Capacity-bearing location (local path, Azure account, S3 bucket) |
| **Storage** | Logical store on a drive with retention policy |
| **Timer** | Cron schedule (Quartz) that triggers attached flows |
| **Flow** | Ordered pipeline of **Steps** from JSON (`model` on `FlowDetail`) |
| **AgentCommand** | Jackson-polymorphic message (`name` discriminator) for API ↔ agent |
| **Subset** | Selective backup planning (`SubsetConfig` / `SubsetPlanner`) |

### Flow step types (from `StepFactory`)

`From`, `To`, `Zip`, `Unzip`, `ClearTables`, `DropTables`, `MySqlScript`, `StopReplica`, `StartReplica`, `SchemaMapping`, `MaskColumns`, `UpdateSequences`, `UpdateSchema`, `ClearRedis`

### Source / target types

- Sources: `RdbmsBackup` (optional subset), `StoredData`
- Targets: named `Storage`, `MySqlDb`, `RdbmsTarget`
- DB engines: MySQL, Postgres, SQL Server, Oracle, MongoDB; plus Redis

### Inbound / outbound commands

| Direction | Types |
|-----------|--------|
| API → agent | `ExecuteFlow`, `UpdateAgentModel`, `RetentionExecute`, `VersionDeleteRequest`, `TestDatasource`, `TestFolderAccess`, `TestAzureBlobDrive`, `TestAWSS3Drive`, `TestRedis`, `PreviewSubset`, `DelayAgentCommand` |
| Agent → API | `NotifyError`, `RetentionCompleted`, `VersionDeleted`, `*Result` variants |

---

## Configuration

### Defaults (`application.yml`)

- `server.port: 9082`
- `quemsi-api.server-url` / `keycloak-url` (prod default `https://quemsi.com`)
- `quemsi-api.client-id/secret: ${CLIENT_ID}` / `${CLIENT_SECRET}`
- `quemsi-api.retry: 5`
- `quemsi.logging.batch-size: 50`, `flush-interval-seconds: 5`
- `agent.watchdog.enabled: true`, `timeout: 5m`

### Important env / system properties

| Variable | Purpose |
|----------|---------|
| `CLIENT_ID` / `CLIENT_SECRET` | Required OAuth client |
| `TRUSTSTORE_PATH` / `PASSWORD` / `TYPE` | Corporate CA |
| `QUEMSI_RUNTIME` | Force runtime label (`windows`/`linux`/`docker`/`java`) |
| `BAKERUP_HOME` | Home dir default |
| Per-datasource/drive env names | When `useEnvVar=true` |
| `-Dquemsi-api.server-url` / `keycloak-url` | Common overrides |
| `QUEMSI_UI_URL` | Optional Quemsi UI link on the agent landing page; default is derived from the API URL (`/app/`) |

Local profile: `application-local.yml` points API/Keycloak at `127.0.0.1:9081` / `127.0.0.1`.

---

## Build / runtime

- **Artifact:** GraalVM native binary (also JVM jar); Docker image `quemsi/quemsi-agent:{version}`
- **Drivers / cloud:** MySQL, Postgres, SQL Server, Oracle, Mongo sync, Jedis; Azure Blob; AWS S3
- **Compose:** `runtime/docker-compose.yml` (`demo-agent` / `local-agent`)
- **Release docs:** `documents/release-agent.md`

---

## Extension guide

There is **no Java plugin SPI**. Extension is intentional and multi-module: model contract → API enqueue/handle → agent execute → (optional) UI.

### Design constraints

- **Control plane stays in API.** Agent should stay a worker: no local business DB of record, no public REST for users.
- **Shared contract = quemsi-model.** Keep DTOs/commands/steps versioned together (agent and API both aligned).
- **Hot model updates** already exist (`UpdateAgentModel`); prefer that over restart for config.
- **Native image:** every new reflective/polymorphic type needs hints in `AgentRuntimeHintsRegistrar`.
- **Watchdog:** long new commands must finish within `agent.watchdog.timeout` (or arming must change).
- **Credentials:** prefer `useEnvVar`; keep masking via `CredentialLogSanitizer`.

### 1. New flow step (pipeline capability)

Best fit for “agent can do more during backup/restore.”

1. Implement `Step` (or Source/Storage) in **quemsi-model**
2. Register in `StepFactory` / `SourceFactory` / `StorageFactory`
3. Add Graal reflection hints in `AgentRuntimeHintsRegistrar`
4. Expose in UI flow editor if needed

Agent code often unchanged — `FlowManager` builds steps from JSON via factories.

### 2. New AgentCommand (imperative, one-shot work)

Best fit for “API can ask the agent to do X outside a flow.”

1. **quemsi-model:** subtype of `AgentCommand` (+ result under `onapi` if needed) and `@JsonSubTypes`
2. **quemsi-api:** enqueue via `AgentCommandManager`; handle inbound result in `AgentApiService.executeCommand`
3. **quemsi-agent:** branch in `AgentCoordinator.execute`, handler under `service.cmd.*`, wire in `AgentCommandExecutor`
4. Native hints for new DTOs

Pattern to copy: `TestDatasource` → `ExecuteTestDatasource` → `TestDatasourceResult`.

### 3. New resource type on AgentModel

e.g. new drive/datasource kind.

1. Fields on `AgentModel` + API `AgentApiService.findAllModel`
2. `SpringBeanManager.register*`
3. Possibly new `Storage` / `DataSourceFactory` implementations
4. UI setup for agents

### 4. New API surface on the agent client

Extend `QuemsiApi` + matching `AgentApiController` when the agent needs to pull/push more than today’s model/commands/logs/executions.

### 5. New control endpoint (browser → agent)

For interactive actions that must stay in the customer environment (download, later browse/preview):

1. Grant/ticket issued by **quemsi-api** (user JWT)
2. Browser hits **agent** `/control/...` with ticket
3. Agent redeems with API (agent JWT) then performs local/cloud I/O
4. Prefer one `controlBaseUrl` per agent; do not open unauthenticated path APIs

---

## Suggested reading order

1. `AgentCoordinator` — lifecycle + command dispatch
2. `QuemsiApi` / `AgentApiController` — protocol
3. `control/ControlHomeController` — landing page at the control base URL
4. `control/ControlDownloadController` — ticketed file download
5. `control/ControlBuilderController` — schema builder sessions (ClearTables / DropTables / MaskColumns / UpdateSequences / Subset)
6. `AgentModel` + `AgentCommand` — shared contract
7. `FlowManager` + `StepFactory` — pipeline engine
8. `SpringBeanManager` — dynamic resource registration
9. One `service/cmd/Execute*` — handler pattern

---

## Cross-workspace references

| Location | Relevance |
|----------|-----------|
| `quemsi-api` | `AgentApiController`, `AgentCommandManager`, release/install config |
| `quemsi-model` | DTOs, flow engine, factories |
| `runtime/` | Compose, agent config mounts, release staging |
| `ui-next` | Agent admin under `/setup/agents` |
| `documents/` | Release/deploy docs for agent images |
