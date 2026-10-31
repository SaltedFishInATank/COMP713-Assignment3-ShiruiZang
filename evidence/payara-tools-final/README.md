# Actual Payara Tools execution

Verification uses the installed Payara Tools1.7.0 inside real VSCode1.144.0, not an emulated IDE. An isolated local VSCode profile contains the server registration and selected JDK21. The verification calls the extension's actual registered Start and Run on Payara Server handlers. The Add Payara Server wizard was not clicked manually; no screenshot/video of that wizard is claimed.

`verification.json`, `command-events.jsonl` and `extension-maven-build.log` establish the final source build and fresh successful deployment. `server-runtime.log` is the genuine domain log; earlier startup/development errors may appear before the successful final run. Final successful deployment at2026-10-31T00:20:04Z (13:20:04NewZealand), independent health200 at00:20:05Z. The full live suite passed at00:20:27Z. The extension build runs all9backend tests.

Initial successful deployment is separately recorded in `../payara-tools/`. A reviewed web-only build was briefly deployed using the CLI between those runs; final Java lifecycle changes were built/deployed again using Payara Tools. No initial build-only result is credited as an IDE deployment.

The source of the harness was adapted from the earlier genuine A2 extension-host workflow. Paths in its configuration are observed local values. Exact manual registration, deployment and screenshot steps are in docs/payara-setup.md and docs/payara-deployment.md.
