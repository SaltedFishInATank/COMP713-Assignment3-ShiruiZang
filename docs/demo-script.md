# Assessment3 recording plan

Shirui Zang, 21136179. Record a readable screen demonstration of no more than 12 minutes. This plan is not the video deliverable and does not establish that a screen or workflow has been demonstrated. Use the final source version and actual test results. Show Payara as the running backend throughout.

## Preparation

Open `backend` in VS Code, its registered Payara6/domain1 instance, Output and the fresh server log. Prepare the web client at `http://localhost:8080/comp713-a3/`, an Android emulator/device with the native app, and a separate consumer terminal. Use readable zoom/font sizes and avoid recording session tokens. Know the actual data paths, broker endpoint and final commit. Retain sample project/task IDs for restart verification.

The coordinator account is `coordinator` / `Coordinator713!`; field worker is `worker` / `Worker713!`. A second worker `worker2` demonstrates assignment restrictions. Initial sample data includes **Campus maintenance** and **Inspect library entrance**. Create an additional task if previous verification changed the sample status.

## Suggested sequence

| Time | Show and explain |
|---|---|
| 0:00-0:40 | Briefly identify the student, system and Assessment2 starting point. Show final repository/version and acknowledge solo development. Explain coordinator/worker roles. |
| 0:40-2:00 | Show VS Code Payara Tools, registered domain and JDK. Use **Start** if stopped, then **Run on Payara Server**. Show successful A3 deployment, fresh domain log and actual health response. If already started, explain and use a deliberate stop/start while preserving data. |
| 2:00-3:20 | In coordinator web client create a project/task or use prepared sample data; assign it to worker. Explain that the saved assignment changes which tasks worker may act on. |
| 3:20-5:00 | On native Android sign in as worker using the real Payara API base. Show loading/result state, assigned tasks and detail. Accept the task to in_progress. Show location permission and a genuine device/emulator fix, or explicitly demonstrate denial/unavailable state and manual coordinate fallback. Add completion note and submit. |
| 5:00-6:00 | Keep coordinator web view visible. Show the task becoming submitted automatically through the WebSocket connection without refresh/polling. Review it: request rework or approve done. Load Android current state and show the changed allowed action/status. This completes the assignment/acceptance and completion/review connected workflows. |
| 6:00-7:30 | Show real broker publication and the separately running consumer. Demonstrate a consumer outage/queued event and later processing, or a repeated-event/crash case. Show durable notification result, stable event ID, ack/retry and duplicate suppression. Distinguish DB commit from messaging acknowledgement. |
| 7:30-8:30 | Disconnect/reconnect the live feed or stop/restart Payara with the browser open. Make/retain a relevant change; show connection feedback and authoritative current-state restoration after recovery. Missed WebSocket events are not historical replay. |
| 8:30-9:20 | Show invalid input or a forbidden request, plus a stale-version conflict if time permits. Show clear feedback rather than an unhandled stack trace. Explain that uncertain writes require reading current state before retry. |
| 9:20-10:20 | Show a saved task ID/state, use Payara Tools **Restart**, wait for readiness, then fetch the same record and show persistence. This may be combined with the failure/reconnect segment to save time. |
| 10:20-11:20 | Show actual automated test output and the response-time target, conditions/sample size and measured result. Mention any test that failed or client workflow that remains unverified. |
| 11:20-12:00 | Show genuine A3 repository timestamps/checkpoints and explain current limitations. Current-day commits cannot establish three weeks. State any outstanding video/report/publication/solo approval issue accurately. |

Use data or logs to show outcomes. If startup takes longer than the allocation, shorten commentary and combine restart/reconnect/persistence segments. Editing for readability is acceptable; do not splice different implementations into a misleading successful flow.

## Evidence to retain with the recording

- Final commit/tag and repository URL matching the submitted ZIP.
- Payara Tools deployment output and fresh Payara domain log identifying `/comp713-a3`.
- Real Android request and WebSocket event/state restoration.
- IDs and before/after values for persistent records.
- Consumer log/result readback for ack/retry and duplicate/failure behavior.
- Actual tests and response-time results, including limitations.
- A video file that opens, is readable and lasts no more than 12 minutes.

