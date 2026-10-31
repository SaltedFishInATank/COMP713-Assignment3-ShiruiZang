# Payara troubleshooting

Use the fresh domain log and actual HTTP status to identify the failing component. Selected local ports are HTTP8080 and administration4848; the deployed A3 context is `/comp713-a3`.

| Symptom | Check and recovery |
|---|---|
| `mvn` missing or wrong Java | Set process-local `JAVA_HOME`, `MAVEN_HOME` and PATH as in setup; verify both `java -version` and `mvn -version` use Java 21. |
| Payara Tools says Maven home missing | Set VS Code `maven.home` to the Maven installation root; installed 1.7.0 does not use `maven.executable.path` alone. |
| Extension reports `'C:\Program' is not recognized` | Its Windows build subprocess needs a no-space Maven path or verified Windows short path; the observed working path is `C:\PROGRA~1\NetBeans-25\netbeans\java\maven`. |
| Java imports remain unresolved | Open `backend` containing `pom.xml`, install Java extensions, allow Maven import/dependency resolution, and select the proper JDK. |
| Add Server rejects a folder | Choose the distribution root containing `glassfish`, rather than the domain folder or project. |
| Server Start fails | Read `<PAYARA_HOME>/glassfish/domains/domain1/logs/server.log`; inspect JDK selection, write permissions and occupied ports. Do not start two processes using the same domain. |
| Application404 | Confirm a fresh successful A3 deployment; use `/comp713-a3/` and `/comp713-a3/api/health`, rather than the A2 `/comp713` context. |
| Old code is served | Rebuild and redeploy A3, inspect the fresh domain log, and reload assets. Verify the running application name/source version. |
| Browser works but Android cannot connect | Emulator uses `10.0.2.2`, rather than `localhost`; physical device uses PC LAN address. Verify port, context, network access and app base URL. |
| HTTP401 / live channel requests sign-in | Session is absent/expired; sign in again. Do not paste bearer tokens into screenshots or logs. |
| HTTP403 | Check the signed-in role and task assignment. Workers cannot edit another worker's task or perform coordinator review/admin actions. |
| HTTP409 stale_version | Another client changed the task. Fetch current state and retry a deliberate action with the new version. |
| HTTP400 on submit | Provide a nonblank completion note and valid latitude/longitude; use the manual fallback when location is unavailable. Task must first be accepted to in_progress. |
| HTTP503 database unavailable | Check writable database folder and server diagnostics. Another JVM must not open the same H2 file; stop the owner before backup/inspection. Keep the separate backend and consumer paths. |
| No new broker result | Distinguish durable backend outbox from broker send and consumer commit. Confirm queue name, actual broker endpoint, consumer process readiness and its own database output. |
| Outbox remains unpublished | Look for `BROKER_RETRY`; unsent rows remain persistent for a later dispatcher attempt. Confirm broker connectivity before retrying or restarting. |
| Duplicate broker delivery | Stable event ID should suppress the repeated persistent effect, then acknowledge the delivery. Inspect consumer attempts/redelivery/duplicate results; do not claim transport exactly-once delivery. |
| WebSocket disconnected | Check `/comp713-a3/ws/tasks`, session authentication and connection feedback. Reconnect, authenticate, then retrieve current REST state; missed messages are not automatically replayed. |
| Location denied, disabled or null | Explain the state, allow retry and retain the manual latitude/longitude fallback. Do not manufacture a device fix. |
| H2 data appears reset | Confirm unchanged `comp713.a3.db.path` and domain instance root. A different base path selects a different database. |

Diagnostics:

```powershell
Invoke-RestMethod 'http://localhost:8080/comp713-a3/api/health'
Get-Content -LiteralPath "$env:PAYARA_HOME\glassfish\domains\domain1\logs\server.log" -Tail 100
Get-NetTCPConnection -State Listen | Where-Object LocalPort -in 8080,4848,7676
```

Payara Tools provides **View Domain Server Log**, **View Domain Admin Console**, **View Domain Server Config**, **Open Domain Directory**, **Restart**, **Stop**, application **Open in Browser** and **Undeploy**. Removing an A3 deployment should not delete its external database; deleting the H2 file is a separate data-destruction operation.

Known development constraints and actually executed checks must remain visible in the root README and evidence. The supplied setup, test and demonstration scripts establish reproducibility, while their recorded outputs establish what was run.


## Issues actually encountered in this session

- Payara Tools1.7.0 splits the `payara-server-build` **command string**. A task with `command: "mvn"` and a separate `args` array was rejected before build. The supplied task uses `command: "mvn -B -ntp clean verify"`.
- An earlier Payara process already occupied8080/4848. Starting another copy caused `BindException: Address already in use`. Identify the domain/ports before starting; do not terminate unrelated Java processes. A graceful admin REST POST to `/management/domain/stop` with `X-Requested-By` stopped the identified domain.
- Payara Tools launches its selected JVM directly. `asadmin list-domains` may report not running when a CLI PID file is absent although the real admin/API endpoints respond. The admin endpoint, deployed API and actual server log are stronger checks. `scripts/Payara.ps1 stop` uses the domain's genuine administrative stop command for this case.
- Exiting the isolated VSCode extension-test host also ended its child server. Final restart verification must start the domain independently before leaving it running for normal use.
- An API timing run overlapped redeployment and received503; it was retained as interrupted evidence. A separate stable run revealed warm task-read p95~700ms with immediate H2 engine closure. The corrected lifecycle retains H2 for10idle seconds, explicitly shuts it down on undeploy, and preserves commit/rollback boundaries. Retained evidence shows the original failure and the later39.31ms passing run.

These are observed development issues with corrections, not unresolved claims that every future environment will behave identically.
