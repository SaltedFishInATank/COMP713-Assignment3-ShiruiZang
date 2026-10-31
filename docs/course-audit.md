# Assessment 3 course and baseline audit

Shirui Zang, 21136179, COMP713. Inspected 31 October 2026, Pacific/Auckland. Page references are one-based physical PDF pages. This document records source requirements and design decisions; it is not execution evidence.

## Source scope and method

All 44 supplied weekly lecture/lab PDFs (838 pages) were searched for Payara and the relevant Java, Jakarta, Maven, REST, database, mobile, messaging and WebSocket terms. The original PDFs were reopened with `pypdf`; existing `.work/course_extract` and `.work/course-text` extracts helped locate complete relevant pages. All 15 weekly ZIP inventories were inspected, including the original Week 3 Maven files and Week 5 bundled test XML. The two assessment PDFs and the submitted Assessment 2 source were read separately.

Payara mentions in the original weekly PDFs occur in:

| Source | Pages containing Payara |
|---|---|
| Week 2/Lab/COMP713_Lab_03_04_W2.pdf | 1, 2, 3, 6, 10, 16, 17, 18 |
| Week 2/Lecture/Class04_WebServers_illustrated.pdf | 5, 6 |
| Week 3/Lab/COMP713_Lab_05_06_W3.pdf | 2, 5, 6, 9 |
| Week 3/Lecture/Class06_Database_Connectivity.pdf | 2, 7, 10, 18 |

The other 40 weekly PDFs contain no literal Payara reference. This is a search result, not a claim that every source teaches the same server. Weeks 4 and 7 concern coordination; Weeks 5 and 6 also contain Spring/gRPC examples; Weeks 8 and 9 teach native Android; Week 10 covers messaging and live updates; Week 11 covers federated/IoT systems. Their concepts can be reused without replacing the required Payara application server. Lecture examples, starter TODOs, supplied build output and archive timestamps do not establish personal lab completion or historical development.

## Technology decision

The user explicitly requires VS Code, Payara Tools, actual Payara deployment, Java, Jakarta EE, Maven WAR, Jakarta REST, JSON, a relational database, native Android, a web client, WebSocket and one advanced integration. These requirements are stricter than the platform flexibility of Assessment 2 and the optional bonus wording for WebSocket in Assessment 3.

The weekly PDFs specify Payara Server Community but do not name an exact Payara release. Week 3's original `COMP713_w3_pets.zip`, member `COMP713_Lab05_06_Starter_Aligned/pom.xml`, specifies Java release 21, WAR packaging, `jakarta.platform:jakarta.jakartaee-api:10.0.0` with `provided` scope, Maven compiler plugin 3.13.0 and WAR plugin 3.4.0. Week 2 lab p.7 gives the same Java21/EE10 pattern. Week 5's bundled `week5/enrolment-service/target/surefire-reports/TEST-*.xml` contains a lecturer machine PATH ending in `payara6/bin`. This supports the Payara 6 family; it does not identify a patch release or prove execution on this computer.

Use the already verified local Payara Server Community **6.2025.10**, Microsoft OpenJDK **21.0.8**, Maven **3.9.9** and installed Payara Tools **1.7.0**. The A2 application already runs on this compatible family. The A3 backend targets Java21 to match the weekly Maven examples; the supporting consumer and Android sources use Java17 compatibility. Preserve Jakarta EE 10 application imports and `provided` API scope. `java.sql` and `javax.sql.DataSource` are Java SE APIs, so those names do not constitute a forbidden old Java EE dependency. Do not add `javax.ws.rs`, `javax.persistence` or `javax.websocket` application dependencies.

## Exact teaching references

| Source and pages | Application to Assessment 3 |
|---|---|
| Week 1/Lecture/Class02_with_illustrations.pdf, pp.18, 22-25 | Client/server communication, concurrency, timeouts and partial failure remain relevant even when a framework handles sockets. |
| Week 2/Lab/COMP713_Lab_03_04_W2.pdf, pp.3, 6-10 | JDK21/Maven/Payara Tools; servlet lifecycle; Maven WAR; `mvn clean package`; start Payara and deploy through Payara Tools. p.10 explicitly requires matching browser requests with Payara output. |
| Same Week 2 lab, pp.14, 18 | Repeat valid/invalid HTTP requests; malformed JSON/method/media-type errors; diagnose context paths; serve the client over HTTP. |
| Week 3/Lab/COMP713_Lab_05_06_W3.pdf, pp.2-4 | Browser -> resource/controller -> shared service -> repository. Validation is shared service logic; JSON and rendered paths must agree. VS Code Java Extension Pack, JDK21, Maven, Payara Community and Payara Tools are specified. |
| Same Week 3 lab, pp.5-7 | Install the JDBC driver in the domain, restart, create a `javax.sql.DataSource` pool, ping it, expose `jdbc/Animals`, inject with `@Resource`, use prepared statements and close resources. Keep credentials in deployment configuration. The teaching MySQL account is not project configuration. |
| Same Week 3 lab, pp.8-9 | End-to-end 400 and controlled database-unavailable 503 checks; adapt the example to related entities in the student's own project. |
| Week 3/Lecture/Class06_Database_Connectivity.pdf, pp.3-13, 15 | Keep the database behind server logic; relationships/constraints; JDBC versus ORM; managed DataSource preferred for a Payara web app; prepared queries; repository responsibility. |
| Same database lecture, pp.16-22 | Jakarta Persistence entities, `jakarta.persistence`, `@PersistenceContext`, JTA persistence unit and relationships; both JDBC and JPA are valid abstractions. Small query sets can favor JDBC (p.22); concurrent requests must not share mutable Connection/EntityManager objects (p.21). |
| Week 5/Lecture/Class09_Remote_Communication_and_Invocation_Models.pdf, pp.2-5, 22-24 | Request/reply, latency and uncertainty: a timeout does not prove that a write failed. Use deliberate retry and current-state recovery. |
| Week 5/Lecture/Class10_Server_Side_Services.pdf, pp.5-9, 15-20 | HTTP adapter, injected service and data-access boundaries; business operations define atomic commit/rollback boundaries. |
| Week 6/Lecture/Class11_Service_Interfaces_REST_APIs_Contracts.pdf, pp.3-12 | Resource and method contract, correct HTTP statuses, representations, safe/idempotent behavior and error model. |
| Week 6/Lecture/Class12_Microservices_Cloud_Containers.pdf | Service responsibilities and ownership are design decisions; examples do not require a Spring replacement for Payara. |
| Week 8/Lab/COMP713_Lab15_16_Android_UI_Lab.pdf | Native Android Studio UI, purposeful screens and user-visible interaction states. |
| Week 9/Lecture/COMP713_Class17_Android_Networking.pdf | Real HTTP outside the UI thread, JSON, permissions, emulator host routing and loading/success/error feedback. |
| Week 9/Lecture/COMP713_Class18_Location_Based_Services.pdf, pp.12-20, 23-25 | Java/Android Studio example, runtime approximate-location permission, one current fix, null/denied/disabled handling, emulator position command and manual campus fallback. Never invent coordinates when no fix is available. |
| Week 10/Lecture/Class19_Message_Brokers_and_Event_Driven_Systems.pdf, pp.6, 12-20, 22-26 | REST commands/current state; stable event contract; real broker and independent consumer; acknowledge after saving; atomic deduplication; bounded retries; persistent messages; transactional outbox closes DB/publish gap. |
| Week 10/Lab/COMP713_Week10_Messaging_Lab_Activities.pdf, pp.2-3, 7 | Persistent queue, independent receipt worker, separate DB/JMS commits, intentional crash after DB commit, duplicate-ID guard in the same DB transaction, rollback/retry and optional DLQ. |
| Week 10/Lecture/Class20_Direct_Messaging_WebSockets_Streaming.pdf, pp.6-8, 17-19, 22-23 | WebSocket does not replay missed history; reconnect then restore authoritative state; subscription/snapshot ordering and monotonic versions prevent stale UI state. |
| Same Week 10 lab, p.4 | Push after DB commit, confirm subscription before REST snapshot, ignore older versions, demonstrate disconnected changes and recovery. Polling does not satisfy the live-update requirement. |

## Assessment 2 starting point

Selected source: `COMP713_Assessment2_Submission_ShiruiZang_21136179/Project_Codebase`, author Shirui Zang, baseline HEAD **d9b2ec58db73831fb5e1dde26961381d37253734** (31 October 2026, 12:50:22 +13:00). This current baseline is already Java/Jakarta EE on Payara, rather than the older Flask version mentioned in historical audit text. Preserve the original submission and history.

The baseline is Option A Project and Task Manager: browser Fetch -> Jakarta REST `TaskResource`/`RestApplication` -> CDI `TaskService` -> `persistence.TaskRepository`/`Database` -> persistent H2. It already has Project and Task model classes, related projects/tasks tables, CRUD, normalized unique project names, status validation, exception mapping, prepared statements, explicit transactions, cascading deletion and H2 tests. Its pom packages `comp713.war`, uses Jakarta EE10 Web API, H2 2.3.232, and Java17 bytecode with JDK21. Its README reports real A2 deployment evidence separately. Those A2 checks do not establish A3 behavior.

Preserve this class architecture and compatible code. Add role-aware shared workflows, assignment/review data, mobile check-in metadata, durable workflow events, a separate consumer result and WebSocket notifications. Keep client access through Payara. Use a distinct `/comp713-a3` deployment and a separate database base path so the submitted A2 state remains intact.

JDBC is retained deliberately. The user says JPA "where appropriate," and the database lecture p.22 explicitly allows direct JDBC for a small query set. Converting a verified A2 repository to ORM would add migration risk without meeting an additional workflow requirement. Prepared queries, short per-operation connections, explicit atomic transactions and relational constraints still apply. Document clearly that this implementation uses application-managed H2/JDBC, rather than claiming the course lab's managed MySQL pool or JPA. A managed DataSource/JPA migration remains a future improvement.

## Advanced path decision

Choose **Path B: message broker**. Payara remains the main HTTP backend and producer. Its real embedded OpenMQ broker and an independent Java consumer implement the asynchronous workflow. OpenMQ is bundled with the existing Payara distribution and speaks the Jakarta Messaging API taught in Week 10. The broker is real software hosted in the Payara process; the consumer runs in a separate JVM. The course lab uses Artemis; this is an implementation substitution, not a claim that OpenMQ was the lab's broker.

Use one durable queue and a meaningful task workflow event. The consumer must create a visible or persistent result, commit that effect before acknowledging, and preserve stable event IDs so repeated delivery does not repeat the effect. Broker/consumer unavailability, rollback/retry and a repeated-event or crash-boundary demonstration need genuine logs and result readback. An HTTP call within the same process, an in-memory event list, or a WebSocket broadcast alone does not satisfy this path. A producer success also does not prove consumer completion.

## Assessment 3 requirements and evidence mapping

Source: `Assignment 2/Assignment_3.pdf`, four pages. The following are required/target evidence, not a completion claim.

| Source | Required behavior/deliverable | Suitable evidence |
|---|---|---|
| p.1 | Extend a submitted A2 project; at least two connected workflows and two distinct users/roles, with one action changing another user's view/actions | Identify baseline above; coordinator assigns/reviews and field worker updates/submits; demonstrate both directions using both clients. |
| p.1 | Usable web client and native Android Studio app sharing server-persisted data | HTTP logs, Android device/emulator run and web interaction; meaningful mobile role workflow. |
| p.1 | Validation, persistence, controlled errors; client/application/data-access separation | Backend code boundaries; success, invalid input, missing/forbidden requests, communication failure and restart readback. |
| p.1 | One useful mobile capability | Location-supported field check-in with runtime permission and manual fallback; show denial/disabled/error state. |
| p.1 | WebSocket bonus in brief, mandatory under user's request | Android change updates open browser automatically; disconnect/reconnect restores current state; no refresh/polling substitute. |
| p.1 | At least one measurable response-time requirement with justified target, conditions and observed result | Timed deployed API operations; record sample size, warm-up, machine/concurrency and actual median/p95 or distribution. Do not turn an unexecuted target into a passing result. |
| pp.1-2 | Two dated checkpoints; regular meaningful attributable A3 commits across at least three weeks; A2 commits do not count | Actual commits and working checkpoint evidence only. Current-day implementation cannot establish three weeks of development. |
| p.2 | Path B broker: event, independent consumer, visible/persistent result, ack/retry and delivery failure/repeat case | Separate process logs, stable event ID, queued event while offline, result readback, duplicate outcome/ack. |
| p.2 | Indicative: web 3-5 views, Android 2-3 screens, API 6-10 operations, 3-6 entities, 10-16 focused checks | Keep purposeful scope; retained CRUD can cause endpoint count to exceed guidance. Explain coherent operations rather than splitting endpoints artificially. |
| p.3 | Group report: overview/baseline; architecture/communication; honest status; executed tests/NFR; contributions/progress; limitations | PDF/DOCX with source/evidence identifiers and actual implementation status. |
| p.3 | Independently written individual report: system understanding; critical reflection (including AI review/testing); personal technical contribution | Separate PDF/DOCX; actual files/commits/decisions/tests; student review and authorship must be clear. |
| p.4 | Complete code ZIP; both reports separately outside ZIP; <=12-minute readable genuine demonstration video | Complete backend/web/Android/consumer/config/migrations/tests and exact run instructions; a script is not a video. |
| p.4 | Publicly accessible GitHub URL and final commit/tag in root README | Actual remote/public accessibility and exact ZIP source version. Local Git alone does not establish publication. |
| p.4 | README prerequisites, commands, ports/env, test accounts/sample data, tests/workflows and limitations | Reproduce from extracted source without IDE caches, dependency folders, generated output or secrets. |

## Source inconsistencies and honest limits

- The official brief describes a group of 2-3 students; the user explicitly requests a solo project. Identify Shirui Zang as the sole contributor and do not invent teammates. Solo approval is a course/marker decision outside technical implementation.
- Page 2 describes advanced integration as 10 points; page 4's detailed rubric allocates 8. Record the functionality fully; do not promise a particular mark.
- Page 3 says the individual report uses four sections but enumerates only three. Cover all three enumerated requirements explicitly; an additional verification/learning section may support them.
- No supplied three-week A3 development history, public repository, checkpoint recording or narrated demonstration can be inferred from source files or a successful build. Create current evidence with genuine dates and state remaining gaps.
- Installation, compilation and a prepared deployment command are distinct from actual server startup, IDE deployment, HTTP behavior, Android execution and a successful complete workflow. Claims must identify which was observed.

