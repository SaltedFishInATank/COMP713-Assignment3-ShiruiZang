package nz.ac.aut.comp713.fieldtasks;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ScrollView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Opt-in live integration test. Creates a real project/task on the configured Payara server. */
public final class PayaraSmokeInstrumentation extends Instrumentation {
    private Bundle arguments;
    private Activity activity;
    private String api;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        arguments = args == null ? new Bundle() : args;
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            api = ApiClient.normalizeBaseUrl(arguments.getString("api", "http://10.0.2.2:8080/comp713-a3/api/"));
            JSONObject worker = new ApiClient(api, null).login("worker", "Worker713!");
            JSONObject coordinator = new ApiClient(api, null).login("coordinator", "Coordinator713!");
            String coordinatorToken = coordinator.getString("token");
            long workerId = worker.getJSONObject("user").getLong("id");
            String suffix = Long.toString(System.currentTimeMillis());
            String title;
            long projectId;
            long taskId;
            if (arguments.containsKey("projectId") && arguments.containsKey("taskId")) {
                projectId = Long.parseLong(arguments.getString("projectId"));
                taskId = Long.parseLong(arguments.getString("taskId"));
                Task fixture = new ApiClient(api, worker.getString("token")).task(taskId);
                require(fixture.projectId == projectId && "todo".equals(fixture.status), "Fixture must be an assigned todo task in the specified project");
                title = fixture.title;
            } else {
                title = "Android field evidence " + suffix;
                JSONObject project = server("POST", "projects", new JSONObject()
                        .put("name", "Android verification " + suffix).put("description", "Real native Android / Payara integration evidence."), coordinatorToken);
                projectId = project.getLong("id");
                JSONObject created = server("POST", "projects/" + projectId + "/tasks", new JSONObject()
                        .put("title", title).put("description", "Inspect the AUT city campus access point.").put("assignedTo", workerId), coordinatorToken);
                taskId = created.getLong("id");
            }
            result.putLong("projectId", projectId);
            result.putLong("taskId", taskId);
            report("Created real coordinator assignment #" + taskId);

            AccessibilityServiceInfo serviceInfo = getUiAutomation().getServiceInfo();
            serviceInfo.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            getUiAutomation().setServiceInfo(serviceInfo);
            activity = startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitText("Work, connected.");
            fill("http://10.0.2.2:8080/comp713-a3/api/", api);
            fill("Username", "worker");
            fill("Password", "incorrect-password");
            click("Sign in");
            waitText("Sign in with a valid account.");
            screenshot("invalid-credentials");
            report("Native invalid-password HTTP401 displayed the server error");
            fill("Password", "Worker713!");
            screenshot("01-login");
            click("Sign in");
            waitButtonPrefix(title);
            screenshot("02-assignments");
            report("Native login and server-filtered assignment list passed");
            clickPrefix(title);
            waitText("Accept assignment");
            click("Accept assignment");
            waitText("Submit for review");
            Task accepted = new ApiClient(api, worker.getString("token")).task(taskId);
            require("in_progress".equals(accepted.status), "Native accept did not persist in Payara");
            screenshot("03-accepted");
            report("Native accept persisted with version " + accepted.version);
            SystemClock.sleep(Long.parseLong(arguments.getString("observerDelayMs", "2500")));

            click("Use device location");
            denyLocationPermission();
            waitText("Location permission denied.");
            fill("Describe the work completed and evidence found", "Android emulator inspected site; entrance is clear and signage is intact.");
            fill("e.g. -36.8485", "-36.8485");
            fill("e.g. 174.7633", "174.7633");
            screenshot("04-permission-denied-manual-fallback");

            // Another actor edits the task after the worker has loaded its version.
            server("PATCH", "tasks/" + taskId, new JSONObject().put("version", accepted.version)
                    .put("description", "Coordinator updated the inspection brief while the worker was in the field."), coordinatorToken);
            click("Submit for review");
            waitText("This task changed on the server.");
            screenshot("conflict-notice");
            require(readInput("e.g. -36.8485").equals("-36.8485"), "409 did not retain coordinate draft");
            require(readInput("Describe the work completed and evidence found").contains("Android emulator inspected"), "409 did not retain note draft");
            screenshot("05-stale-version-recovery");
            report("Real HTTP409 reloaded server state and retained the unsent evidence draft");

            click("Submit for review");
            waitText("Awaiting coordinator review");
            Task submitted = new ApiClient(api, worker.getString("token")).task(taskId);
            require("submitted".equals(submitted.status), "Native submission did not persist");
            require(submitted.latitude != null && Math.abs(submitted.latitude + 36.8485) < 0.000001, "Latitude was not persisted");
            require(submitted.longitude != null && Math.abs(submitted.longitude - 174.7633) < 0.000001, "Longitude was not persisted");
            require(submitted.completionNote.contains("Android emulator inspected"), "Completion note was not persisted");
            screenshot("06-submitted");
            report("Native note/coordinate submission persisted in Payara's database");
            SystemClock.sleep(Long.parseLong(arguments.getString("observerDelayMs", "2500")));

            server("PATCH", "tasks/" + taskId, new JSONObject().put("version", submitted.version).put("status", "done"), coordinatorToken);
            click("Refresh task");
            waitText("Approved · complete");
            screenshot("07-coordinator-approved");
            report("Coordinator approval displayed after native refresh");

            String gpsTitle = "Android GPS evidence " + suffix;
            JSONObject gpsCreated = server("POST", "projects/" + projectId + "/tasks", new JSONObject()
                    .put("title", gpsTitle).put("description", "Capture a fresh device GPS fix at the inspection site.").put("assignedTo", workerId), coordinatorToken);
            long gpsTaskId = gpsCreated.getLong("id");
            result.putLong("gpsTaskId", gpsTaskId);
            click("Back to assignments");
            waitButtonPrefix(gpsTitle);
            clickPrefix(gpsTitle);
            waitText("Accept assignment");
            click("Accept assignment");
            waitText("Submit for review");
            click("Use device location");
            allowLocationPermission();
            waitText("Device location captured");
            fill("Describe the work completed and evidence found", "Fresh device GPS captured on the Android14 emulator at the field site.");
            screenshot("08-device-gps-captured");
            click("Submit for review");
            waitText("Awaiting coordinator review");
            Task gpsSubmitted = new ApiClient(api, worker.getString("token")).task(gpsTaskId);
            require("submitted".equals(gpsSubmitted.status) && gpsSubmitted.latitude != null && gpsSubmitted.longitude != null,
                    "Device GPS submission did not persist");
            require(Math.abs(gpsSubmitted.latitude + 36.8485) < 0.001 && Math.abs(gpsSubmitted.longitude - 174.7633) < 0.001,
                    "Device GPS did not match the injected emulator site fix");
            screenshot("09-gps-submitted");
            report("Permission grant, fresh LocationManager fix and GPS-backed native submission passed; task #" + gpsTaskId);
            result.putString("status", "passed");
            result.putString("stream", "PASS: native login, assigned list, accept, permission denial/manual coordinates, real409 recovery, submit, coordinator approval, permission grant and fresh device GPS submission. Project " + projectId + ", tasks " + taskId + "/" + gpsTaskId + ". Screenshots: externalFilesDir/evidence.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("status", "failed");
            result.putString("stream", "FAIL: " + failure + "\n");
            result.putString("failure", android.util.Log.getStackTraceString(failure));
            try { screenshot("failure"); } catch (Throwable ignored) { }
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void report(String value) {
        Bundle status = new Bundle();
        status.putString("stream", value + "\n");
        sendStatus(0, status);
    }

    private void screenshot(String name) throws Exception {
        waitForIdleSync();
        if (activity != null && (name.startsWith("04-") || name.startsWith("05-") || name.startsWith("08-"))) {
            runOnMainSync(() -> {
                ScrollView scroll = findScroll(activity.getWindow().getDecorView());
                if (scroll != null) scroll.fullScroll(View.FOCUS_DOWN);
            });
            waitForIdleSync();
        }
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        require(bitmap != null, "Screenshot unavailable");
        File directory = getTargetContext().getExternalFilesDir("evidence");
        require(directory != null && (directory.exists() || directory.mkdirs()), "Cannot create evidence directory");
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        bitmap.recycle();
    }

    private ScrollView findScroll(View view) {
        if (view instanceof ScrollView) return (ScrollView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            ScrollView result = findScroll(((ViewGroup) view).getChildAt(i));
            if (result != null) return result;
        }
        return null;
    }

    private void fill(String hint, String value) {
        runOnMainSync(() -> {
            EditText input = findInput(activity.getWindow().getDecorView(), hint);
            require(input != null, "Missing field: " + hint);
            input.setText(value);
        });
    }

    private String readInput(String hint) {
        String[] value = new String[1];
        runOnMainSync(() -> {
            EditText input = findInput(activity.getWindow().getDecorView(), hint);
            require(input != null, "Missing field: " + hint);
            value[0] = input.getText().toString();
        });
        return value[0];
    }

    private EditText findInput(View view, String hint) {
        if (view instanceof EditText && hint.equals(String.valueOf(((EditText) view).getHint()))) return (EditText) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            EditText found = findInput(((ViewGroup) view).getChildAt(i), hint);
            if (found != null) return found;
        }
        return null;
    }

    private void click(String caption) { clickInternal(caption, false); }
    private void clickPrefix(String caption) { clickInternal(caption, true); }
    private void clickInternal(String caption, boolean prefix) {
        runOnMainSync(() -> {
            Button button = findButton(activity.getWindow().getDecorView(), caption, prefix);
            require(button != null && button.isEnabled(), "Button unavailable: " + caption);
            button.performClick();
        });
    }

    private Button findButton(View view, String caption, boolean prefix) {
        if (view instanceof Button) {
            String value = ((Button) view).getText().toString();
            if (prefix ? value.startsWith(caption) : value.equals(caption)) return (Button) view;
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            Button found = findButton(((ViewGroup) view).getChildAt(i), caption, prefix);
            if (found != null) return found;
        }
        return null;
    }

    private void waitButtonPrefix(String caption) {
        long end = SystemClock.elapsedRealtime() + 30000;
        while (SystemClock.elapsedRealtime() < end) {
            boolean[] found = new boolean[1];
            runOnMainSync(() -> found[0] = findButton(activity.getWindow().getDecorView(), caption, true) != null);
            if (found[0]) return;
            SystemClock.sleep(200);
        }
        throw new AssertionError("Timed out waiting for assignment: " + caption + "\n" + screenText());
    }

    private void waitText(String value) {
        long end = SystemClock.elapsedRealtime() + 30000;
        while (SystemClock.elapsedRealtime() < end) {
            if (screenText().contains(value)) return;
            SystemClock.sleep(200);
        }
        throw new AssertionError("Timed out waiting for " + value + "\n" + screenText());
    }

    private String screenText() {
        StringBuilder result = new StringBuilder();
        runOnMainSync(() -> collectText(activity.getWindow().getDecorView(), result));
        return result.toString();
    }

    private void collectText(View view, StringBuilder result) {
        if (view instanceof TextView) result.append(((TextView) view).getText()).append('\n');
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) collectText(((ViewGroup) view).getChildAt(i), result);
    }

    private void denyLocationPermission() {
        choosePermission("permission_deny_button");
    }

    private void allowLocationPermission() {
        choosePermission("permission_allow_foreground_only_button");
    }

    private void choosePermission(String buttonId) {
        long end = SystemClock.elapsedRealtime() + 10000;
        while (SystemClock.elapsedRealtime() < end) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/" + buttonId);
                for (AccessibilityNodeInfo node : matches) if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
            }
            SystemClock.sleep(200);
        }
        throw new AssertionError("Android location permission dialog did not appear");
    }

    private JSONObject server(String method, String path, JSONObject body, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(api + path).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(12000); connection.setReadTimeout(15000);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            try (OutputStream output = connection.getOutputStream()) { output.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            int status = connection.getResponseCode();
            InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (input != null) try (InputStream stream = input) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            }
            String text = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            require(status >= 200 && status < 300, method + " " + path + " returned HTTP " + status + ": " + text);
            return new JSONObject(text).getJSONObject("data");
        } finally { connection.disconnect(); }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
