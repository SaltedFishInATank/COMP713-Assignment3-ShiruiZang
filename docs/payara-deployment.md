# Build, deploy and verify on Payara

Run these steps after [payara-setup.md](payara-setup.md). Commands assume a PowerShell terminal at the Assessment3 root and the selected Java21/Maven/Payara paths. A Maven build and a server deployment establish different facts.

## Build the WAR

```powershell
mvn -f .\backend\pom.xml clean verify
Get-Item -LiteralPath .\backend\target\comp713-a3.war
jar tf .\backend\target\comp713-a3.war
```

The WAR must contain compiled application classes, `WEB-INF/beans.xml`, the web assets and H2 driver. Jakarta EE API implementation JARs are supplied by Payara; they should not be bundled as a second incompatible runtime. A failed build must be resolved before deployment. Read the actual Surefire reports for tests that were executed.

## Deploy using Payara Tools 1.7.0

1. Open **Assessment3/backend** as the VS Code folder and select the registered local domain/JDK21 described in setup.
2. Right-click the registered server and select **Start**, or refresh if it is already running. Read startup messages and verify port8080/4848 readiness.
3. Right-click the backend project folder or its `pom.xml`, select **Run on Payara Server**, and choose the intended server if prompted. The extension builds the project before deploying.
4. Wait for the extension's successful deployment result and a fresh domain log entry identifying `comp713-a3` at `/comp713-a3`. The application node must refer to A3, rather than the earlier `/comp713` application.
5. Select the application **Open in Browser**, or open `http://localhost:8080/comp713-a3/`. Verify `http://localhost:8080/comp713-a3/api/health` independently.
6. Right-click the server and use **View Domain Server Log**. The physical log is `<PAYARA_HOME>/glassfish/domains/domain1/logs/server.log`. **View Domain Admin Console** opens `http://localhost:4848` in this domain.

The extension may deploy an exploded WAR folder. The separate `clean verify` above produces the reviewable WAR artifact. Record which deployment method was actually used. A CLI fallback can verify Payara execution but cannot establish an IDE deployment claim.

## CLI fallback for verification

If direct IDE operation is unavailable, the same WAR can be verified using Payara's available command-line administration tool:

```powershell
& "$env:PAYARA_HOME\bin\asadmin.bat" list-applications
& "$env:PAYARA_HOME\bin\asadmin.bat" deploy --force=true --name comp713-a3 --contextroot comp713-a3 .\backend\target\comp713-a3.war
Invoke-RestMethod 'http://localhost:8080/comp713-a3/api/health'
```

Start the configured domain first if it is stopped. Credentials/ports must match that domain. `--force=true` replaces the named A3 deployment; confirm the application name before using it. Keep `/comp713` and its database intact. Retain CLI output as CLI evidence and complete the manual Payara Tools steps for the required IDE demonstration.

## Verify actual behavior

1. Confirm the health response identifies `comp713-a3` and database availability. A static web page alone does not establish backend execution.
2. Sign in as coordinator, inspect seeded projects/tasks and create a task assigned to worker. Verify persisted IDs and returned HTTP statuses.
3. Sign in on native Android as worker using `http://10.0.2.2:8080/comp713-a3/api/`. Accept a task, add a completion note and device/manual location, then submit it.
4. Keep the coordinator web view open. Confirm an automatic WebSocket-driven refresh shows the submitted task and review actions. Approve it, or request rework; verify Android current state changes when loaded again.
5. Inspect the outbox and actual broker/independent consumer logs. Show the consumer's persistent notification result and a retry/repeated-event outcome.
6. Try invalid input, an unauthorized action and a stale version. Show controlled errors and useful user feedback.
7. Record a project/task ID, use Payara Tools **Restart**, then read the same data after readiness. Restart/redeploy must preserve the same H2 base path.
8. Demonstrate a communication failure and recovery. After an uncertain write, load current state before retrying.
9. Disconnect/reconnect the web live feed or restart the server while the browser remains open. Verify connection feedback and restoration of current server state.
10. Run the supplied tests and response-time measurement, then retain actual output with timestamps and final source version.

## Deployment evidence record

For every final verification run retain the code version, WAR SHA-256, Payara/JDK versions, domain, HTTP/admin/broker ports, command or IDE action, timestamp, fresh log lines, actual API responses and remaining failures. State separately whether Android was compiled, installed, launched and used for a complete real workflow. Do not reuse A2 evidence as an A3 result.

Observed on 31 October 2026 (Pacific/Auckland): actual installed Payara Tools 1.7.0 commands ran in a real VS Code 1.144.0 extension host. The final **Start** command was invoked at 13:18:55 and **Run on Payara Server** at 13:19:41. The fresh domain log confirmed `comp713-a3` deployed in 4513 ms, and an independent health request returned 200 identifying the application and working database. `evidence/payara-tools-final/verification.json`, command events and the extension Maven build log preserve the scope. Registration was seeded in an isolated profile; wizard clicks were not claimed.

After the final deployed build, 15 live HTTP/WebSocket checks and 11 actual Chrome UI checks passed. Native Android instrumentation completed acceptance/submission, permission-denial/manual fallback, conflict recovery, approval refresh and a granted fresh emulator GPS fix. The open coordinator browser reflected the native submission without reload. Independent consumer logs show persistent notification, rollback/redelivery, acknowledgement, replay deduplication and a failure after DB save/before acknowledgement. A later real domain stop/start caused a failed HTTP request while offline and preserved all nine compared task fields at 13:25:08. Full source records are in `evidence`, native records in `android/evidence`, and the final scope is summarized by `docs/verification-status.md`.

Earlier failures are retained: a live-test request overlapped deployment, the first H2 lifecycle run missed its 500 ms p95 target, an offline-only browser test needed to close its established socket explicitly, and an early post-start login probed before application readiness. Final tests followed the corresponding corrections; these records are not hidden or presented as successful initial runs.

The required video is a readable screen recording, no more than 12 minutes. [demo-script.md](demo-script.md) is a recording plan, rather than a recorded deliverable.

