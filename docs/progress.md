# Contributions and development progress

Shirui Zang, 21136179. COMP713 Assessment3, solo project as requested by the student. The supplied official brief describes groups of 2-3; this submission must identify the actual solo arrangement and cannot infer course approval.

## Starting point

Assessment2 source author: Shirui Zang. Selected baseline: `COMP713_Assessment2_Submission_ShiruiZang_21136179/Project_Codebase`, commit `d9b2ec58db73831fb5e1dde26961381d37253734`, 31 October 2026, 12:50:22 +13:00. Original submission/history are preserved separately.

Retained concepts/code include Project/Task domain, Jakarta REST registration and JSON/error handling, the service/repository/database separation, prepared SQL, H2 persistence and transaction approach, and appropriate browser interaction patterns. New Assessment3 work adds authenticated roles, assignment and review workflow, task versions, mobile completion/location data, native Android client, WebSocket channel/recovery, durable event outbox and real broker/independent consumer. Consult the final source and commit diff for precise file-level changes.

OpenAI Codex assisted with implementation, review, tests and documentation. Generated code and report drafts require the student's review and understanding. The individual technical report must be independently written as required by the brief; an AI-prepared draft is support material and must not be presented as an independently authored personal reflection.

## Traceable current work

All current implementation dates must remain genuine. Use final repository commits and recorded checks to substantiate contribution. Do not assign personal actions, testing outcomes or reflections to the student merely because a tool performed them.

| Area | Technical evidence to review |
|---|---|
| Payara backend and retained A2 architecture | `backend/pom.xml`; `backend/src/main/java/.../api`, `service`, `repository`, `persistence`; schema; deployment and API output |
| Web/shared workflow | `backend/src/main/webapp`; role/state controls; actual browser and WebSocket verification |
| Native Android | `android/app/src/main`; API client, task views and location/manual fallback; Gradle build and actual device evidence |
| Broker consumer | `backend/.../messaging/TaskEventPublisher.java`; outbox; `consumer/src/main`; separate process/result/ack/duplicate evidence |
| Reproducibility and assessment analysis | root README, `docs`, tests/scripts and executed `evidence`; final reports and real recording |

## Required working checkpoints

The official brief requires two dated working checkpoints and regular attributable Assessment3 commits across at least three weeks. A2 history does not count. Files prepared on 31 October 2026 cannot establish earlier working checkpoints or three weeks of development.

| Required checkpoint | Honest evidence rule |
|---|---|
| Android-to-API workflow | Record the date/version and real native Android request plus persistent workflow result when actually observed. A build or host-side HTTP test is not enough. |
| Later WebSocket or advanced prototype | Record a later actual working run/version with push/recovery or broker/consumer evidence. Do not create an earlier date or infer elapsed weeks. |

Two actual current-day working checkpoints were subsequently recorded. Times below are Pacific/Auckland on 31 October 2026; they are evidence of working functionality, without establishing three weeks of development.

| Checkpoint | Observed date/time and evidence |
|---|---|
| Android-to-API workflow and open-web update | Native instrumentation accepted and submitted project9/task6 through Payara. The open coordinator browser changed to IN PROGRESS at 13:22:53.181 and AWAITING REVIEW at 13:23:03.354, without navigation/reload. Native screenshots/log: `android/evidence/native-runtime.log` and `screenshots`; web record: `evidence/web/android-observer-result.json`. |
| Later advanced integration with native GPS event | Native GPS task8 submitted event `40be3c44-4963-4562-b35e-def2b911edfb` at 13:23:17.542. Independent consumer verification completed at 13:24:03.879, showing retry/redelivery, acknowledgement, duplicate suppression and one persistent notification. Evidence: `evidence/broker/android/android-event.json`, `result.json` and process logs. |

The real implementation checkpoint is `f9ab1ec54be78a1695f5f17721a26c235998e415` (13:20:07 +13:00), authored as Codex AI-assisted implementation. Later final verification/report commits retain their actual times. No historical Assessment3 checkpoints were supplied; these current-day runs must not be portrayed as semester-long progress.

## Publication and final delivery

The root README must identify a real publicly accessible GitHub repository and exact final commit/tag. If publication has not occurred, state it as outstanding; a suggested repository name or local Git history is not a public URL. Exclude secrets, runtime databases, dependency folders, generated build output and IDE caches from the code ZIP. Submit the group and individual reports as separate files outside the ZIP, and record a genuine <=12-minute video. A recording plan is not a completed demonstration.

