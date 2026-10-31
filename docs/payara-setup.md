# Payara and VS Code setup

Shirui Zang, 21136179. COMP713 Assessment 3. These instructions target the actual Payara 6/Jakarta EE10 stack selected from the course audit. Executed results belong in `evidence`; following a setup guide alone does not prove deployment.

## Inspected development environment

| Component | Observed value |
|---|---|
| Payara Server Community Full | 6.2025.10, local distribution at `../.work/tools/payara6` relative to the Assessment3 folder; Full profile supplies EJB/Jakarta Messaging/OpenMQ |
| Domain | `domain1` under `<PAYARA_HOME>/glassfish/domains/domain1` |
| JDK | Microsoft OpenJDK 21.0.8+9 LTS, `C:\Program Files\Android\openjdk\jdk-21.0.8` |
| Maven | Apache Maven 3.9.9, `C:\Program Files\NetBeans-25\netbeans\java\maven` |
| VS Code | 1.144.0, `C:\Users\zsr20\AppData\Local\Programs\Microsoft VS Code\Code.exe` |
| Payara Tools | `payara.payara-vscode` 1.7.0 |
| Extension Pack for Java | `vscjava.vscode-java-pack` 0.31.2; Red Hat Java 1.58.0; Java Debug 0.60.0; Java Test 0.47.0; Maven 0.46.0; Java Dependency 0.27.7; Gradle 3.18.1 |
| Backend | Maven WAR, Jakarta EE10 API `provided`, H2 2.3.232; Java21 source/bytecode built/run with JDK21 |
| Other Java targets | Consumer and Android source compatibility17; the consumer runs on the selected JDK21, while Android compiles through its Android Gradle toolchain |
| HTTP / administration | 8080 / 4848 in the inspected domain; HTTPS listener 8181 is also configured |

Do not copy these personal installation paths to another computer. Install/extract equivalent compatible tools, then set their actual paths. Payara is a prerequisite distribution rather than part of the source ZIP. No global Java installation needs changing.

From a PowerShell terminal opened at the Assessment3 root:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\openjdk\jdk-21.0.8'
$env:MAVEN_HOME = 'C:\Program Files\NetBeans-25\netbeans\java\maven'
$env:PAYARA_HOME = (Resolve-Path '..\.work\tools\payara6').Path
$env:Path = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"
java -version
mvn -version
```

Both commands must identify Java21. On another computer replace the three values before running them. An unrelated Java8/24/25 installation on PATH is not the selected runtime.

## VS Code project and server registration

The following labels/settings were checked directly in installed Payara Tools 1.7.0 `package.json`, `PayaraServerInstanceController.js` and `Maven.js`.

1. Open **Assessment3/backend** with **File > Open Folder**. The folder containing `pom.xml` must be the project root used by the extension. Install **Payara Tools** and **Extension Pack for Java**; allow Maven import and dependency downloads.
2. Open **Preferences: Open Workspace Settings (JSON)** and configure `maven.home` to the Maven installation root. On this computer its verified no-space form is `C:\\PROGRA~1\\NetBeans-25\\netbeans\\java\\maven`. On another computer a no-space installation such as `C:\\Tools\\apache-maven-3.9.9` is convenient.
3. Press **Ctrl+Shift+P**, run **Payara: Add Payara Server**, then select **Local Domain** in **Register Payara Server**.
4. Select the Payara distribution folder containing `glassfish`, using **Select Payara Server**. Give the server a recognizable name, then select **domain1**.
5. Open the Explorer **Payara Servers** view, or the Payara activity bar's **Servers** view. Right-click the registered server, select **JDK Home**, and choose the JDK21 folder containing `bin`.
6. Right-click the server and select **Start**. Wait for startup in Output/domain log before deploying. If the same domain is already running, refresh its status rather than starting another conflicting instance.
7. Build and deploy using [payara-deployment.md](payara-deployment.md), then inspect the real API and domain log.

Example workspace setting:

```json
{
  "maven.home": "C:\\PROGRA~1\\NetBeans-25\\netbeans\\java\\maven"
}
```

This installed extension reads `maven.home`, then `M2_HOME`, then `MAVEN_HOME`. `maven.executable.path` alone does not configure its build. Its Windows subprocess does not quote a Maven executable path containing spaces correctly; use a verified short path or no-space install. The server's **JDK Home** determines its build runtime. A `payara-server-build` task, if used, must contain the complete command string because this extension version does not apply a separate task `args` array to that build override.

## Database configuration

The application retains Assessment2's H2/JDBC architecture. The H2 driver is packaged in the WAR. The application creates the schema and demonstration accounts from bundled code on first use. No external MySQL account, JDBC pool, JNDI resource or `persistence.xml` is required by this implementation. This is an explicit JDBC choice, rather than a claim of JPA/managed DataSource usage; see [course-audit.md](course-audit.md).

The default persistent database base is:

```text
<PAYARA_HOME>/glassfish/domains/domain1/lib/databases/comp713-a3/tasks
```

H2 creates `tasks.mv.db`. It is separate from the A2 `comp713/tasks` database. Give the domain process permission to create this folder. Set JVM property `comp713.a3.db.path` to choose another absolute base path without `.mv.db`; a semicolon is rejected. In Payara Admin Console, use **Configurations > server-config > JVM Settings > JVM Options**, add a value such as `-Dcomp713.a3.db.path=C:/COMP713Data/assessment3/tasks`, save and restart before use. A different path selects a different database; it does not migrate existing records.

The backend owns users, sessions, projects, tasks and the durable event outbox. Every operation closes its own JDBC connection; `DB_CLOSE_DELAY=10` avoids repeatedly shutting down/reopening the underlying database between adjacent requests, and an application `@PreDestroy` method issues `SHUTDOWN` on undeployment. This retains explicit JDBC transactions without sharing a mutable connection between requests. H2 recommends a pool, retained connection or delayed closing for this repeated-open workload ([H2 performance guidance](https://h2database.com/html/performance.html)).

The consumer has its own H2 file and never directly opens the backend database. Do not point the two processes at the same database base. Keep data on restart/redeploy and back it up while its owning process is stopped. Do not submit personal runtime databases or session tokens.

Initial demonstration accounts are `coordinator` / `Coordinator713!`, `worker` / `Worker713!` and `worker2` / `Worker713!`. The seeded project is **Campus maintenance**, with a task assigned to `worker`. Passwords are stored as salted PBKDF2 hashes. These published accounts are for local assessment demonstration.

## Broker and Android configuration

The producer uses Payara's Jakarta Messaging resource adapter, default connection factory and the application queue `TaskEventsQueue`. The bundled OpenMQ broker supplies actual messaging; the consumer runs in a different JVM. Read the final broker/consumer commands in the root README and consumer README. The inspected original domain's JMS service type was `EMBEDDED`; a separate consumer must still connect to the broker's actual reachable address. Do not start another broker on an occupied port or describe an embedded broker as a separate process.

The native Android project is under `android`. Open it in Android Studio and use a compatible installed Android SDK. The local emulator host alias is `10.0.2.2`, so the API base is `http://10.0.2.2:8080/comp713-a3/api/`. A physical device needs the development PC's reachable LAN address, the same network and firewall access. Set the API base in the app's connection UI; do not replace the server with a mock. The web client uses the same origin as the deployed WAR and connects to `/comp713-a3/ws/tasks`.

## Manual screenshot checklist

- [ ] VS Code version and installed Payara Tools/Java extensions.
- [ ] Backend folder containing `pom.xml` open in VS Code.
- [ ] Registered Payara server, chosen `domain1` and JDK Home.
- [ ] **Start** result and readable Output/domain log.
- [ ] **Run on Payara Server** result and application node `/comp713-a3`.
- [ ] Actual REST response and the deployed web client.
- [ ] Native Android screen making a real request.
- [ ] Android action and automatic web WebSocket update; reconnect recovery.
- [ ] Independent consumer logs and persistent result, including duplicate/retry case.

Capture these during a real run. A seeded registration or documented click sequence does not prove that the registration wizard was manually exercised.


Observed local network on31October2026: Wi-Fi6 IPv4`192.168.1.12`; Payara listens on8080,4848,7676. The current physical-device API address is `http://192.168.1.12:8080/comp713-a3/api/`. Recheck the address after changing networks and allow the HTTP port through the local firewall as needed. Physical-device reachability was not tested; the Android14 emulator was tested. See `evidence/network.json`.
