# Final verification status

Shirui Zang, 21136179 · COMP713 Assessment3 ·31October2026 Pacific/Auckland. This records actual execution, distinct from setup instructions. Final source tag: `assessment3-local-v3.0.0`; local snapshot only, no public repository has been published.

| Area | Observed result | Evidence |
|---|---|---|
| Course environment | All44weekly PDFs/838pages and relevant starter archives searched; Payara6 family, JakartaEE10 and Java21 selected | course-audit.md |
| VSCode/Payara Tools | Actual VSCode1.144.0 extension host and installed Payara Tools1.7.0 used real Start and Run on Payara Server commands; isolated profile registration seeded, wizard not manually clicked | ../evidence/payara-tools-final/verification.json and extension-maven-build.log |
| Server/WAR | Payara Community6.2025.10, JDK21.0.8, Maven3.9.9; valid WAR built; final fresh deployment logged13:20:04NZ; health200 | ../evidence/payara-tools-final/ |
| Backend tests |9tests,0failures/errors/skips | ../evidence/maven-verify-final.log and final extension build |
| Live API/WebSocket |15checks passed, auth/roles/assignment/validation/conflict/workflow/push/reconnect/outbox/logout | ../evidence/live-api.json |
| Response time |Warm task GET p95=39.31ms across40sequential localhost requests; acceptance<=500ms. Earlier699.81ms failure retained; H2 lifecycle corrected | live-api.json; live-api-before-jdbc-lifecycle-fix.json |
| Browser |11checks passed in real Chrome155, two isolated roles, forms/errors/review/reconnect, no page reloads or script errors | ../evidence/web/result.json and screenshots |
| Android build |Native Java app, SDK34/min23, final assembleDebug+instrumentation build+lint succeeded; no lint issues | ../android/evidence/build-and-lint.log, lint-results-debug.txt |
| Native runtime |Real hardware-accelerated Android14 emulator: HTTP401, login/assignments/accept, denied location/manual fallback, real409with retained draft, submission, coordinator approval, permission grant/fresh emulated LocationManager fix and GPS submission | ../android/evidence/native-runtime.log; verification.json;11screenshots |
| Android→open web |Task6 moved todo→in_progress→submitted; native note/location displayed automatically, one navigation/no reload; exact task DOM and received WebSocket event captured | ../evidence/web/android-observer-summary.json and screenshots |
| Consumer unit tests |3tests passed; independently built Java JAR | ../evidence/consumer-tests/ |
| Real broker |Embedded real OpenMQ7676, Payara persistent producer/outbox, independent consumer JVM with owned H2; rollback/redelivery/ack/replay dedupe all passed; crash-after-store case passed | ../evidence/broker/result.json, crash-window.log |
| Native event→broker |Actual Android GPS task8event processed in separate consumer; retry+ack+replay suppressed, one durable notification | ../evidence/broker/android/result.json |
| Server outage/restart |Actual graceful domain stop caused real HTTP failure; independent CLI start reloaded application; saved task9fields all identical after restart | ../evidence/server-outage.json; persistence-after-restart.json; start/stop logs |

The exact runtime-tested Android APK is preserved separately in android/evidence; the final APK contains a subsequent cosmetic success-banner color correction and passed build/lint. No workflow or HTTP logic changed after the successful device run. The checksum distinction is explicit in android/evidence/verification.json. Device coordinates came from a genuine emulated LocationManager fix, not a real-world visit. Physical-device operation has instructions but was not tested.

## Honest limits and outstanding submission steps

- Record a genuine narrated demonstration of<=12minutes, showing VSCode/Payara Tools and both clients, including startup, live updates, broker retry and evidence. A script and real screenshots are supplied; no completed narrated video is claimed.
- Publish to an appropriate publicly accessible GitHub repository, put its real URL and final tag/hash in root README, and verify marker access. The source is currently local; assistant-created commits use actual timestamps and explicitly AI-assisted authorship.
- The official brief asks for2–3members. This is the requested solo project; obtain course approval rather than inventing team members.
- The official three-week A3commit history is not met. Both working checkpoints are genuine and occur today; they cannot establish earlier development. A2history does not count as A3history.
- The individual PDF is prominently a preparation DRAFT; the student's personal contribution/reflection must be reviewed and independently written. The system technical report describes actual code and evidence, not fictional student actions.
- Local demonstration HTTP/accounts, a single file-backed H2owner, no production auth hardening/rate limit, physical-device behavior and some mobile offline/expiry/timeout edge cases remain limitations documented in README and Android README. These do not negate the executed emulator/shared-workflow checks.

Initial failed test attempts are retained with reasons: redeployment interference; the original H2response-time failure; a browser offline-emulation test that did not close its existing WebSocket; and an early post-restart404before the application finished loading. Passing evidence is linked separately so those failures are not silently rewritten as passes.
