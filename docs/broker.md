# Message broker integration (Path B)

The Payara WAR is the main backend. Its `TaskEventPublisher` sends `task.submitted`
events to the actual OpenMQ broker bundled with Payara 6.2025.10. An independent
Java process consumes `TaskEventsQueue` and creates a persistent review notification
in its own H2 database. It does not query the backend database or call a mock service.

## Data ownership and delivery

1. A worker submits an assigned task through the Payara REST API. The backend saves
   the task transition and an `event_outbox` row in the same SQL transaction.
2. Every five seconds an EJB timer reads committed, unpublished outbox rows. It sends
   a Jakarta Messaging `PERSISTENT` message using Payara's
   `java:comp/DefaultJMSConnectionFactory`, then marks the row published. The timer is
   infrastructure delivery; the web client receives state changes through WebSocket.
3. The separate consumer parses the JSON event and saves a human-readable review
   notification. Its `notifications.event_id` primary key is also the deduplication
   key. The result and key commit together before the JMS session commits.
4. `session.commit()` acknowledges consumption. On processing failure,
   `session.rollback()` returns the message for redelivery. Five failed broker
   deliveries route the message to `TaskEventsQueueDeadLetter` in the same JMS
   transaction as the original acknowledgement. The consumer's persistent audit
   records attempts, redeliveries, duplicates, and outcome.

This is **at-least-once delivery** with idempotent notification creation. A crash
after a broker send but before the backend's published update can publish twice.
A crash after the consumer's H2 commit but before JMS acknowledgement can deliver
twice. Both cases keep one business notification per event id. There is no claim of
a distributed atomic transaction between H2 and JMS.

If the broker is unavailable, outbox rows remain unpublished and the EJB retries.
If the consumer is stopped, persistent messages remain on the broker queue. The
consumer reconnects every two seconds after a broken broker connection. Run one
consumer against each consumer data path; its embedded H2 file is single-process.

## Build and run

Select a supported JDK (this machine uses
`C:\Program Files\Android\openjdk\jdk-21.0.8`). Build from the `Assessment3` directory:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\openjdk\jdk-21.0.8'
mvn -f consumer/pom.xml clean package
```

The POM uses the Jakarta OpenMQ client/API jars from
`../../.work/tools/payara6/mq/lib` relative to `consumer`. On another machine supply
the selected Payara installation, for example:

```powershell
mvn -f consumer/pom.xml -Dpayara.home=C:/Tools/payara6 clean package
```

Start Payara and deploy the WAR first, then run the separate process:

```powershell
./consumer/Run-Consumer.ps1 -JavaHome $env:JAVA_HOME
```

For another Payara installation add `-PayaraHome C:/Tools/payara6`. The wrapper uses
the built JAR, Maven-copied runtime libraries in `target/lib`, and the same
installation's `imq.jar` and `jms.jar`. Both application sides import `jakarta.jms`.
No legacy `javax.jms` jar is added.

The observed domain configuration uses embedded OpenMQ and broker port 7676.
Override the wrapper's `-Broker host:port` for another instance. The local development
broker uses its default `guest` account; set `-User` and environment variable
`COMP713_BROKER_PASSWORD` if that broker's authentication is changed. Production
deployment requires broker credentials and access restrictions appropriate to its
environment.

The default consumer data is `consumer/data/notifications.mv.db`. It contains
notifications and delivery audit, independently of Payara's authoritative task DB.
Stop the consumer and inspect its durable result without connecting to the broker:

```powershell
./consumer/Run-Consumer.ps1 -JavaHome $env:JAVA_HOME -Status
```

## Reproducible failure and duplicate demonstration

Use a fresh consumer data base path for each demonstration so counts are easy to
interpret. Do not start another consumer on the same queue during this sequence.

1. Keep the consumer stopped. Submit a task as the worker in Android or the web
   client. Wait at least five seconds for the outbox publisher. Payara logs
   `BROKER_PUBLISHED eventId=... queue=TaskEventsQueue`.
2. Start the independent consumer with failure injection:

   ```powershell
   ./consumer/Run-Consumer.ps1 -JavaHome $env:JAVA_HOME `
     -Data ./consumer/data/retry-demo -FailOnce -Limit 1 -IdleSeconds 30
   ```

   It logs `RETRY_ROLLBACK`, then `redelivered=true`, `NOTIFICATION_CREATED`, and
   `ACK_COMMIT`. The resulting persistent audit has two attempts and one redelivery.
3. Run `-Status` against that same `-Data` path to show the result survives a separate
   process restart. It prints `PERSISTED_NOTIFICATIONS count=1` and the review text.
4. Re-publish the last processed event with exactly the same event id:

   ```powershell
   ./consumer/Run-Consumer.ps1 -JavaHome $env:JAVA_HOME `
     -Data ./consumer/data/retry-demo -ReplayLast
   ./consumer/Run-Consumer.ps1 -JavaHome $env:JAVA_HOME `
     -Data ./consumer/data/retry-demo -Limit 1 -IdleSeconds 30
   ```

   The second run logs `DUPLICATE_SUPPRESSED` and acknowledges it. The notification
   count stays one and the persistent duplicate count increases.
5. To test the crash window itself, submit another task and use a fresh data path
   with `-FailAfterStore -Limit 1 -IdleSeconds 30`. It stores the result, rolls back
   the broker transaction, and suppresses the redelivery; one result remains.

`-Limit` counts acknowledged deliveries (including suppressed duplicates), and
`-IdleSeconds` bounds the wait for a message. Omit both for a continuously running
consumer. Failure flags are demonstration controls and apply to each event's first
observed delivery in that consumer database.

Automate steps 2–4 against an already queued real Payara event with
`scripts/Test-Broker.ps1`. Keep the logs as evidence; a successful build alone does
not prove a broker delivery. The consumer's three unit tests cover file-backed
restart deduplication, retry without an early result, and event-contract validation.

## Observed verification on 31 October 2026

The deployed Payara application and its real embedded OpenMQ were exercised from
independent consumer JVMs. The following results are recorded, rather than inferred
from the source:

| Scenario | Recorded effect | Evidence |
| --- | --- | --- |
| Processing failure before result commit, then replay | One notification, three deliveries observed, one broker redelivery, one duplicate suppressed, successful acknowledgements | `evidence/broker/result.json` and its adjacent logs |
| Failure after H2 result commit, before JMS acknowledgement | One notification after redelivery; two attempts and one duplicate suppressed | `evidence/broker/crash-window-result.json`, `crash-window.log` |
| Actual native Android GPS submission, task 8 | Event `40be3c44-4963-4562-b35e-def2b911edfb` passed from Payara through OpenMQ to the independent consumer; rollback, redelivery, acknowledgement, restart inspection and duplicate replay passed; one notification remained | `evidence/broker/android/result.json`, `android-event.json` and adjacent logs |

The Android event contains the recorded GPS-workflow completion note, and the
consumer result includes its task title and review message. Device coordinates
remain authoritative in the task record; the notification contract does not copy
coordinates. These local tests establish the demonstrated failure paths. They do
not measure production throughput or test broker high availability.

## Sources

- [OpenMQ Jakarta Messaging classic API and local transactions](https://eclipse-ee4j.github.io/openmq/guides/mq-dev-guide-java/using-the-java-api.html)
- [OpenMQ client connection and reconnection properties](https://eclipse-ee4j.github.io/openmq/guides/mq-admin-guide/administered-object-attributes.html)
- [Payara 6.2025.10 JMS administration](https://docs.payara.fish/community/docs/6.2025.10/Technical%20Documentation/Payara%20Server%20Documentation/General%20Administration/Administering%20the%20Java%20Message%20Service.html)
