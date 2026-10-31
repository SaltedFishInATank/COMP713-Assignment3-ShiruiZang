# FieldTasks native Android client

Shirui Zang · 21136179 · COMP713 Assessment 3 · solo project.

Open this directory in Android Studio. This is a native **Java** application using Android `Activity`, standard widgets, `HttpURLConnection`, `ExecutorService`, `org.json`, and `LocationManager`. There are no web views or third-party runtime dependencies. The course Week 9 Class 17 network and Class 18 location examples informed the transport, Java source level, SDK level, and location permissions. The Gradle wrapper scripts/JAR were reused from Class 17.

## Build and install

Requirements: JDK 17 or 21, Android SDK platform 34, build tools 34.0.0, and network access for first-time Gradle/Android Gradle Plugin resolution. The project uses AGP 8.5.2 (the course example version), Gradle 8.10 (already available locally, newer than AGP's minimum 8.7), Java source 17, minimum Android 6/API 23, and target Android 14/API 34. Set Android Studio's **Gradle JDK to 17 or 21**; the currently installed Studio bundled JBR is Java 25 and is unsuitable for this Gradle version.

1. Open `Assessment3/android` with **File → Open** in Android Studio.
2. Install SDK platform 34/build tools 34.0.0 using SDK Manager if missing.
3. Set SDK location in Android Studio or create an untracked `local.properties` with `sdk.dir=C:/path/to/Android/Sdk`. Alternatively set `ANDROID_HOME`.
4. Allow Gradle sync; select a connected device or an Android Virtual Device and click **Run app**.

Command line, from this directory:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\openjdk\jdk-21.0.8'
$env:ANDROID_HOME = 'C:\path\to\Android\Sdk'
.\gradlew.bat --no-daemon assembleDebug lintDebug
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r .\app\build\outputs\apk\debug\app-debug.apk
```

macOS/Linux: set `JAVA_HOME` and `ANDROID_HOME`, run `chmod +x gradlew`, then `./gradlew assembleDebug lintDebug`.

The debug APK output is `app/build/outputs/apk/debug/app-debug.apk`. This is a development APK signed with a debug key, not a release store submission.

## Connect to the real Payara backend

Deploy `comp713-a3.war` to Payara first, as described in the project's Payara documentation. The login screen contains one editable API address, persisted across launches:

| Device | API address |
| --- | --- |
| Standard Android emulator | `http://10.0.2.2:8080/comp713-a3/api/` |
| USB device with `adb reverse tcp:8080 tcp:8080` | `http://127.0.0.1:8080/comp713-a3/api/` |
| Physical device on the same LAN | `http://<computer-LAN-IP>:8080/comp713-a3/api/` |

Use the observed Payara HTTP port if changed. For a LAN connection the phone and computer must be mutually reachable, Payara must listen on that interface, and the firewall must allow its HTTP port. The app does not add an emulator address elsewhere. Sign out to change the address.

Demo worker: **worker / Worker713!**. Coordinator: **coordinator / Coordinator713!**. A coordinator signing into Android is directed to the web workspace; assigning and approving belong to the coordinator web workflow. Only tasks assigned to the authenticated worker are returned by the server and displayed in the native app.

Cleartext HTTP is explicitly enabled for the local course deployment. Use an HTTPS API URL for an accessible production service. The app saves only API URL and username in private preferences; password and bearer token remain in memory. Rotation retains the in-memory session; restarting after process death requires login. Android backup is disabled.

## Shared workflow

1. Coordinator signs into the Payara web app and creates/assigns a task to `worker`.
2. Worker signs into Android, taps **Refresh**, and selects the assignment under its project.
3. Worker taps **Accept assignment**. A versioned `PATCH` records `in_progress` in Payara's database.
4. Worker enters a completion note (1–1,000 characters), captures location or enters both site coordinates, then taps **Submit for review**.
5. The `PATCH` records `submitted`, note and coordinates. An open coordinator web page receives the backend WebSocket event and refreshes its current state.
6. Coordinator approves (`done`) or returns it for further work. Android's **Refresh task** displays the server's authoritative status.

Location is requested only through **Use device location**. The Android permission dialog supports approximate location. With permission granted, `LocationManager` requests a fresh fix from an enabled provider and stops after the first fix or 20 seconds. No background tracking occurs. Denial, permanently denied permission, disabled providers, missing hardware and timeout all leave manual latitude/longitude entry available. Submission requires finite latitude −90…90 and longitude −180…180; the server validates the same ranges. Emulator location can be set through **Extended controls → Location**, then captured with the app's button.

Network requests run off the main thread with progress text, disabled mutation controls, connection/read timeouts and visible server error messages. A 401 returns to login. A 409 reloads the current task version; an unsent note/coordinates draft is restored when the task is still in progress, and the worker must review before retrying. Other failures preserve the current form for retry. Task submissions are never presented as complete before the server confirms them.

## API contract

All operations use JSON envelopes `{ "data": ... }`; errors use `{ "error": { "code": "...", "message": "..." } }`. Authenticated calls send `Authorization: Bearer <token>`.

| Method | Path below API base | Use |
| --- | --- | --- |
| POST | `auth/login` | Username/password → token and user |
| GET | `projects` | Projects available to the worker |
| GET | `projects/{id}/tasks` | Server-filtered assignments |
| GET | `tasks/{id}` | Refresh authoritative task/version |
| PATCH | `tasks/{id}` | `{version,status:"in_progress"}` or `{version,status:"submitted",completionNote,latitude,longitude}` |

## Verification and demonstration evidence

The native app was built with the real Android 34 SDK/build tools, JDK 21, AGP 8.5.2 and Gradle 8.10. `assembleDebug` and `lintDebug` passed; the final lint report says **No issues found**. See `evidence/build-and-lint.log`, `evidence/lint-results-debug.txt`, and the ready-to-install `dist/fieldtasks-debug.apk`. A build alone does not establish successful Android-to-Payara requests; live test evidence is recorded separately.

On **31 October 2026 (Pacific/Auckland)**, the real Android 14/API 34 x86_64 emulator `emulator-5554` ran the complete native UI test against Payara at the configured emulator URL. The runner returned **`status=passed` / `INSTRUMENTATION_CODE: -1`**. It verified invalid-password HTTP 401, native login and assigned list, accept, actual Android permission denial with manual coordinates, an intentional HTTP 409 with draft retention, evidence submission, coordinator approval and native refresh. A second assignment verified actual permission grant and a fresh `LocationManager` fix (±5 m) followed by successful GPS-backed submission. The GPS fix was injected through the emulator at the documented site; physical handset GPS has not been tested.

The run used project **9**, manual-evidence task **6** and GPS task **8**. See `evidence/native-runtime.log`, `evidence/verification.json` and the **11 actual device captures** in `evidence/screenshots/`. The Android runtime error log captured during the run is empty. A concurrently open coordinator web client observed task 6 change **TO DO → IN PROGRESS → AWAITING REVIEW** from real WebSocket events, with one initial page navigation and no reload; its independent evidence is in `../evidence/web/android-observer-result.json` and `../evidence/web/android-live-submitted-evidence.png`.

### Opt-in live native UI test

`app/src/androidTest/.../PayaraSmokeInstrumentation.java` is a dependency-free Android instrumentation runner. It uses the actual app's native widgets, real HTTP calls and the Android permission controller. It creates a real coordinator project and worker assignments, accepts through the native UI, denies permission and submits manual evidence, provokes a real stale-version 409, verifies draft retention, submits, approves as coordinator and refreshes the native view. A second task grants permission, captures a fresh `LocationManager` fix and submits it. The test asserts saved status/note/coordinates through Payara and saves device screenshots. Test records intentionally remain available for inspection.

For reproducible emulator GPS testing, inject the known test site **174.7633, −36.8485** through the emulator controls while the runner executes. This is an emulated GPS fix, not a physical device field measurement. Run this in a separate terminal before the instrumentation command:

```powershell
1..60 | ForEach-Object {
    & "$env:ANDROID_HOME\platform-tools\adb.exe" -s emulator-5554 emu geo fix 174.7633 -36.8485
    Start-Sleep -Seconds 2
}
```

Build/install/run from this directory with Payara already deployed:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest lintDebug
$taskAdb = "$env:ANDROID_HOME\platform-tools\adb.exe"
& $taskAdb -s emulator-5554 install -r .\app\build\outputs\apk\debug\app-debug.apk
& $taskAdb -s emulator-5554 install -r .\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
& $taskAdb -s emulator-5554 shell pm clear nz.ac.aut.comp713.fieldtasks
& $taskAdb -s emulator-5554 shell am instrument -w -r -e api http://10.0.2.2:8080/comp713-a3/api/ nz.ac.aut.comp713.fieldtasks.test/nz.ac.aut.comp713.fieldtasks.PayaraSmokeInstrumentation
& $taskAdb -s emulator-5554 pull /sdcard/Android/data/nz.ac.aut.comp713.fieldtasks/files/evidence ./evidence/screenshots
```

Require `INSTRUMENTATION_RESULT: status=passed`; a shell exit code alone is insufficient. Optional `-e projectId N -e taskId N` uses a pre-created worker-assigned **todo** fixture so an open coordinator web observer can select it before the native test starts. `-e observerDelayMs 5000` allows web capture between accept/submission and approval.

For the recorded assessment demonstration, show the same native flow alongside the live web page and Payara logs. Offline retry, expired-session reauthentication, disabled GPS and the 20-second no-fix timeout remain useful manual demonstrations; the automated run above verified the successful fix and permission-denial fallback. The phone/LAN and USB reverse instructions are provided for a physical-device follow-up.
