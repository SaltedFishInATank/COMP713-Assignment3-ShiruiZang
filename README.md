# COMP723Assignment-3[README.md](https://github.com/user-attachments/files/34210150/README.md)
# Fieldwork — COMP713 Assessment 3

Shirui Zang · 21136179 · Solo development · 31 October 2026 (Pacific/Auckland)

Fieldwork extends Shirui Zang's Assessment2 Project and Task Manager into a shared campus-maintenance workflow. A coordinator assigns work in the browser; a field worker accepts it in the native Android app, submits an inspection note and location, and the coordinator approves or requests rework. Both clients use the same real Jakarta REST API deployed on Payara Server. A Jakarta WebSocket feed updates open browsers. Submission events pass through real OpenMQ to an independent Java consumer with its own persistent notification database.

**Submission status and exact verified results:** see [docs/verification-status.md](docs/verification-status.md) and `evidence/`. Build, IDE deployment, host HTTP tests, browser tests and Android device tests establish different facts. A demonstration script is provided; a narrated video has not been recorded. The brief specifies2–3members and3weeks of development history; this is the requested solo implementation and cannot establish those requirements in one session. The individual report is explicitly a preparation draft for the student's independently written report.

Repository: **not published / no public GitHub URL supplied**. Final source version: local tag `assessment3-local-v3.0.0` (see `VERSION.txt` and the supplied local history). Do not submit an invented repository link. Publish the reviewable source to your chosen GitHub repository and update this line before submission. Assessment2 history is not Assessment3 development history.

## Layout

- `backend/`: Maven Jakarta EE10 WAR; embedded web client in `src/main/webapp`.
- `android/`: native Java Android Studio project, configurable server URL, GPS/runtime permissions/manual coordinates.
- `consumer/`: independently running Jakarta Messaging consumer, its own H2 notification store.
- `scripts/`: reproducible build, CLI administration fallback and live tests.
- `docs/`: course audit, setup/deployment/API/troubleshooting/broker/demo/progress guides.
- `evidence/`: actual run results and logs; `reports/`: technical report and clearly labeled individual preparation draft.

## Prerequisites and local setup

Use **Payara Server Community6.2025.10 Full**, **JDK21**, Maven3.9.9, VS Code with Java Extension Pack and Payara Tools1.7.0, Android Studio, Android SDK34/build-tools34, and Node22+ for optional integration tests. The course archives use Java21 and JakartaEE10; no weekly material pins a patch version. The existing compatible Assessment2 runtime therefore supplies the selected Payara version. Full profile supplies Jakarta Messaging/OpenMQ; a standalone web-profile server is insufficient for this broker path.

Observed local installations:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\openjdk\jdk-21.0.8'
$env:MAVEN_HOME='C:\Program Files\NetBeans-25\netbeans\java\maven'
$env:PAYARA_HOME=(Resolve-Path '..\.work\tools\payara6').Path
$env:Path="$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"
```

Run from the `Assessment3` directory. Substitute your own installation paths on another machine. These assignments affect only the current shell. See [docs/payara-setup.md](docs/payara-setup.md) for supported versions, exact VSCode registration/JDK steps and ports.

```powershell
.\scripts\Build.ps1
# In VS Code open Assessment3/backend, register Payara, select JDK21, Start,
# then Run on Payara Server on backend/pom.xml.
```

The extension task deliberately uses a complete command string `mvn -B -ntp clean verify`: installed Payara Tools1.7.0 parses that string and does not forward a separate tasks.json `args` array. Set VSCode `maven.home` to the Maven installation directory (a no-space path such as `C:\PROGRA~1\NetBeans-25\netbeans\java\maven` avoids its Windows command quoting problem). Keep the supplied launch.json configurations array. See [docs/payara-deployment.md](docs/payara-deployment.md).

CLI fallback for independently verifying the same Payara system:

```powershell
.\scripts\Payara.ps1 start
.\scripts\Payara.ps1 deploy
.\scripts\Payara.ps1 status
```

WAR: `backend/target/comp713-a3.war`; context: `/comp713-a3`. Browser: <http://localhost:8080/comp713-a3/>; API health: <http://localhost:8080/comp713-a3/api/health>; admin4848; OpenMQ7676; WebSocket `/comp713-a3/ws/tasks`. The earlier `/comp713` application/database is preserved.

## Accounts and sample data

| Role | Username | Demo password |
|---|---|---|
| Coordinator | coordinator | Coordinator713! |
| Field worker | worker | Worker713! |
| Second worker | worker2 | Worker713! |

On the first database initialization, seed campus-maintenance project and a task assigned to worker. These are local demonstration credentials; the app is a coursework deployment. Passwords use salted PBKDF2 and sessions use random hashed tokens. Use HTTPS and deployment-managed secrets before public hosting; this package does not configure public hosting.

H2 is persistent at `<domain>/lib/databases/comp713-a3/tasks.mv.db`. Override the **database base filename** with JVM system property `comp713.a3.db.path` when needed (without `.mv.db`). Schema V1 is bundled as `backend/src/main/resources/schema.sql`; initialization is idempotent and retains business records. Database access uses retained Assessment2 JDBC with explicit commit/rollback, prepared statements and task row locks. No JDBC pool/JNDI resource/JPA entity manager is required by this design; see the course audit rationale. This embedded file belongs to Payara only. Clients and consumer never read it directly. The consumer owns a separate H2 notifications database.

## Two shared workflows

1. **Dispatch and acceptance.** Coordinator signs into web, creates a project/task and assigns worker. Worker signs into Android, refreshes assignments, opens the task and accepts. The coordinator's open board updates automatically to In progress.
2. **Evidence and review.** Worker adds a note and gets device location or enters manual coordinates, submits. Coordinator sees Awaiting review live, opens evidence and approves or requests rework. Worker reloads the assignment to receive that decision. A stale write returns409 and asks the client to restore latest state.

Android Studio: open `android/`, select JDK21, sync and run `app`. Emulator API base `http://10.0.2.2:8080/comp713-a3/api/`; change it on the login screen for a physical device using the actual host LAN IP. No emulator URL is scattered through business code. Local cleartext HTTP is enabled for this coursework demo; a physical device must share a reachable network. Follow [android/README.md](android/README.md) for exact build/device tests and location permission behavior.

## Broker consumer and failure behavior

Build/run the independent consumer while Payara's embedded OpenMQ is running:

```powershell
mvn -f consumer/pom.xml "-Dpayara.home=$env:PAYARA_HOME" clean package
.\consumer\Run-Consumer.ps1 -PayaraHome $env:PAYARA_HOME -JavaHome $env:JAVA_HOME
```

For an optional hidden local consumer process on Windows, `scripts/Start-Consumer.ps1` records its actual PID, output log and stop command under `consumer/data/runtime.json`. Start one instance for that data path.

The producer sends persistent `task.submitted` events from a transactional outbox every5seconds. Broker failure retains pending rows. The consumer commits a notification/dedupe eventId in its own H2 before acknowledging JMS. A crash after that commit can redeliver; the unique eventId suppresses duplicate effects. `-FailOnce` rolls back a delivery; `-ReplayLast` demonstrates repeated-event behavior; `-Status` shows durable notifications. See [docs/broker.md](docs/broker.md) for commands, retry/dead-letter policy and the independent console result.

## Verification

```powershell
mvn -f backend/pom.xml clean verify
node scripts/test-live.mjs
.\scripts\Test-Broker.ps1 -PayaraHome $env:PAYARA_HOME -JavaHome $env:JAVA_HOME
# Optional: installed Playwright required; see header in script for module paths.
node scripts/test-web.cjs
# Android project:
.\android\gradlew.bat -p android assembleDebug lintDebug
```

Live tests need running Payara. `API_URL` overrides test base. Run broker tests against a fresh outbox event ID if old notifications already consumed it. Test fixture projects remain visible for inspection. Representative task GET target p95<=500ms over40warm, sequential localhost requests; actual timings are reported in evidence and do not claim production load capacity. See [docs/payara-api-testing.md](docs/payara-api-testing.md) for endpoint contracts, transitions, error cases, reconnection and restart tests.

## Deliverables and remaining submission work

Reports belong outside the final code ZIP as separate files. Use the group-style technical report for the requested solo system and obtain course approval for the group-size mismatch. The individual preparation draft contains factual context and prompts; rewrite the personal analysis independently and verify every claim. Record the<=12-minute video using [docs/demo-script.md](docs/demo-script.md), showing actual Payara Tools, both clients, live update/recovery, broker and evidence. Publish/access-check the chosen repository and add the URL/final hash. Preserve genuine dated checkpoints and future meaningful commits; no backdating or fictional teamwork is supplied.

Known limits include local demo HTTP/accounts, single Payara instance/file-backed H2, no password reset or production rate limiting, task lists fetched per project, coordinates are user-provided evidence rather than verified geofencing, and a session of implementation cannot establish a three-week history. Runtime test-specific limitations are listed in the verification status and reports.
