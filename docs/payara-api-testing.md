# API and live verification

Base URL: `http://localhost:8080/comp713-a3/api/`. JSON responses use `{ "data": ... }`; failures use `{ "error": { "code": "...", "message": "..." } }`. Supply `Accept: application/json` and `Content-Type: application/json` for writes. Successful creates return201 and Location; deletes/logout204. Bad input400, unauthenticated401, unauthorized403, missing404, duplicate/stale version409, unsupported media415, unavailable database503. Session bearer tokens expire after24hours and are stored hashed in H2; logout revokes one session. Demo credentials are in root README.

| Operation | Authorization and behavior |
|---|---|
| GET health | Public, queries real task table |
| POST auth/login | username/password, returns token and user |
| GET auth/me; POST auth/logout | Current session / revoke |
| GET users | Coordinator, available workers |
| GET/POST projects | Both read; coordinator create name/description |
| GET/PATCH/DELETE projects/{id} | Both read; coordinator update/delete |
| GET/POST projects/{id}/tasks | Worker sees assigned tasks; coordinator creates title/description/assignedTo |
| GET/PATCH/DELETE tasks/{id} | Assigned worker or coordinator read; coordinator deletes |
| GET events | Coordinator, durable outbox with publication state |

The retained Assessment2 resource paths and project CRUD are extended for A3 roles. This produces more than the indicative6–10 operations; those paths keep the established project/task workflow instead of creating artificial endpoint counts. Six domain/auth/event tables plus a one-row schema version table support this application; consumer owns its separate notification table.

Every task PATCH requires integer `version` from its latest GET. Worker transitions are `todo -> in_progress -> submitted`. Submission requires completionNote1–1000characters, latitude[-90,90] and longitude[-180,180]. Coordinator reviews `submitted -> done` or `submitted -> in_progress` for rework; title/description edits are also allowed. Reassignment is limited to todo tasks. The row is locked and its version checked in one JDBC transaction. Submission and outbox insert commit together. Unknown request fields are rejected.

```powershell
$base='http://localhost:8080/comp713-a3/api/'
$session=Invoke-RestMethod ($base+'auth/login') -Method Post -ContentType 'application/json' -Body '{"username":"coordinator","password":"Coordinator713!"}'
$headers=@{Authorization=('Bearer '+$session.data.token);Accept='application/json'}
Invoke-RestMethod ($base+'projects') -Headers $headers
```

WebSocket: `ws://localhost:8080/comp713-a3/ws/tasks`. First send `{"type":"auth","token":"<session>"}`; server sends `{"type":"ready"}`. `task.changed` and `project.changed` are invalidation events without private record content. The client fetches authorized current state on ready and each event. Heartbeat ping/pong keeps the connection active; disconnects use exponential reconnect1–30seconds. The browser does not poll application data. Server closes idle connections after70seconds; clients reauthenticate after reconnect. Token logout/expiry is detected by API checks and WebSocket heartbeat. View activity publication status after opening that view; broker publication itself is not a separate WebSocket event.

## Executable evidence

1. `mvn -f backend/pom.xml clean verify`: auth, roles, conflict, rollback, workflow, persistence and retained CRUD tests.
2. After deployment, Node22+: `node scripts/test-live.mjs`. Actual HTTP/WebSocket checks, two roles, outbox and warm representative task GET timing; writes `evidence/live-api.json` and a persisted task checkpoint. Target p95<=500ms over40sequential localhost requests after warmup, chosen for responsive task navigation; this is a development-machine target, not a production capacity claim.
3. `consumer/Run-Consumer.ps1` and `scripts/Test-Broker.ps1`: see broker.md for separate JVM, rollback/redelivery/ack, restart persistence and replay dedupe.
4. `scripts/test-web.cjs`: genuine browser workflows; see its environment variables and evidence output.
5. Restart Payara after the live test, then log in again and GET the task in `evidence/restart-checkpoint.json`. Compare its ID, status, note, location and version; retain the response and genuine shutdown/start logs. Server outage must fail requests rather than produce successful cached application data.
6. Android runtime instructions are in android/README.md. Building an APK alone does not establish actual device requests. Retain instrumentation/device logs separately from host-side API tests.

Never record bearer tokens in evidence or screenshots. Test scripts use fixture projects, leaving their task records for inspection. Course source audit, environment setup, troubleshooting and demo checklist are separate docs.
