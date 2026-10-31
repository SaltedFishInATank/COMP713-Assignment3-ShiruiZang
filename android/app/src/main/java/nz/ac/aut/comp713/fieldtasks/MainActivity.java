package nz.ac.aut.comp713.fieldtasks;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native Java client, following the course's Activity/HttpURLConnection/LocationManager examples. */
public final class MainActivity extends Activity {
    private static final String DEFAULT_API = "http://10.0.2.2:8080/comp713-a3/api/";
    private static final int LOCATION_PERMISSION = 713;
    private static final int INK = Color.rgb(24, 45, 44);
    private static final int TEAL = Color.rgb(20, 125, 117);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<View> actions = new ArrayList<>();
    private SharedPreferences preferences;
    private Session session;
    private LinearLayout body;
    private TextView message;
    private ProgressBar progress;
    private EditText noteInput;
    private EditText latitudeInput;
    private EditText longitudeInput;
    private TextView locationMessage;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Runnable locationTimeout;
    private Task currentTask;
    private int screenVersion;
    private boolean busy;
    private boolean detailsVisible;
    private Bundle restoredDraft;

    private static final class Session {
        String baseUrl;
        String token;
        String displayName;
        String role;
    }

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        preferences = getSharedPreferences("fieldtasks", MODE_PRIVATE);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Object retained = getLastNonConfigurationInstance();
        session = retained instanceof Session ? (Session) retained : null;
        if (session == null) showLogin(null);
        else if (savedState != null && savedState.getLong("taskId", 0) != 0) {
            restoredDraft = savedState;
            loadTask(savedState.getLong("taskId"), null);
        } else showTasks();
    }

    @Override public Object onRetainNonConfigurationInstance() { return session; }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (detailsVisible && currentTask != null) {
            state.putLong("taskId", currentTask.id);
            if (noteInput != null) {
                state.putString("note", noteInput.getText().toString());
                state.putString("latitude", latitudeInput.getText().toString());
                state.putString("longitude", longitudeInput.getText().toString());
            }
        }
    }

    private void screen(String eyebrow, String title, String subtitle) {
        screenVersion++;
        stopLocation();
        busy = false;
        detailsVisible = false;
        noteInput = null;
        actions.clear();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(243, 247, 246));
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(24), dp(22), dp(30));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        TextView brand = text(eyebrow.toUpperCase(Locale.ROOT), 12, TEAL);
        brand.setLetterSpacing(0.16f);
        brand.setTypeface(null, Typeface.BOLD);
        add(brand);
        TextView heading = text(title, 30, INK);
        heading.setTypeface(null, Typeface.BOLD);
        add(heading);
        add(text(subtitle, 15, Color.DKGRAY));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        add(progress);
        message = text("", 15, Color.rgb(160, 40, 35));
        message.setVisibility(View.GONE);
        add(message);
    }

    private void showLogin(String explanation) {
        session = null;
        currentTask = null;
        restoredDraft = null;
        screen("COMP713 · FieldTasks", "Work, connected.",
                "Sign in to accept assigned tasks and submit field evidence for review.");
        label("Payara API address");
        EditText baseInput = input(preferences.getString("baseUrl", DEFAULT_API),
                "http://10.0.2.2:8080/comp713-a3/api/", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        add(text("Emulator: 10.0.2.2 · Physical phone: your computer's LAN address. Include /comp713-a3/api/.", 13, Color.DKGRAY));
        label("Username");
        EditText usernameInput = input(preferences.getString("username", "worker"), "Username", InputType.TYPE_CLASS_TEXT);
        usernameInput.setSingleLine(true);
        label("Password");
        EditText passwordInput = input("", "Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setSingleLine(true);
        button("Sign in", true, () -> {
            final String base;
            try { base = ApiClient.normalizeBaseUrl(baseInput.getText().toString()); }
            catch (IllegalArgumentException error) { error(error.getMessage()); return; }
            String username = usernameInput.getText().toString().trim();
            String password = passwordInput.getText().toString();
            if (username.isEmpty() || password.isEmpty()) { error("Enter your username and password."); return; }
            preferences.edit().putString("baseUrl", base).putString("username", username).apply();
            run("Signing in…", () -> new ApiClient(base, null).login(username, password), data -> {
                JSONObject user = data.getJSONObject("user");
                session = new Session();
                session.baseUrl = base;
                session.token = data.getString("token");
                session.displayName = user.optString("displayName", username);
                session.role = user.getString("role");
                passwordInput.setText("");
                showTasks();
            }, null);
        });
        add(text("Demo worker: worker / Worker713!\nCoordinator: coordinator / Coordinator713! (use the web workspace to assign and approve).", 14, Color.DKGRAY));
        add(text("Only the server address and username are saved. Passwords and session tokens remain in memory. HTTP is enabled for the local course deployment; use an HTTPS URL on a trusted deployment.", 12, Color.DKGRAY));
        if (explanation != null) error(explanation);
    }

    private void showTasks() {
        currentTask = null;
        screen("Field workspace", "Your assignments", "Welcome, " + session.displayName + ".");
        if (!"worker".equalsIgnoreCase(session.role)) {
            add(text("Coordinators assign tasks and review submissions in the web workspace. Sign in with a worker account to perform field work here.", 17, INK));
            button("Sign out / change server", false, () -> showLogin(null));
            return;
        }
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh = new Button(this);
        refresh.setText(R.string.refresh);
        refresh.setOnClickListener(v -> showTasks());
        toolbar.addView(refresh, new LinearLayout.LayoutParams(0, -2, 1));
        actions.add(refresh);
        Button signOut = new Button(this);
        signOut.setText(R.string.sign_out);
        signOut.setOnClickListener(v -> showLogin(null));
        toolbar.addView(signOut, new LinearLayout.LayoutParams(0, -2, 1));
        add(toolbar);
        add(text("Only work assigned to your account is shown. Tap a task to view the latest server state.", 14, Color.DKGRAY));
        ApiClient client = client();
        run("Loading assignments from Payara…", () -> {
            List<ProjectTasks> result = new ArrayList<>();
            JSONArray projects = client.projects();
            for (int i = 0; i < projects.length(); i++) {
                JSONObject project = projects.getJSONObject(i);
                JSONArray tasks = client.tasks(project.getLong("id"));
                ProjectTasks group = new ProjectTasks(project.getString("name"));
                for (int t = 0; t < tasks.length(); t++) group.tasks.add(Task.fromJson(tasks.getJSONObject(t)));
                if (!group.tasks.isEmpty()) result.add(group);
            }
            return result;
        }, groups -> {
            if (groups.isEmpty()) add(text("No assignments yet. Your coordinator can assign a task in the web workspace; then tap Refresh.", 18, INK));
            for (ProjectTasks group : groups) {
                label(group.name);
                for (Task task : group.tasks) {
                    Button taskButton = button(task.title + "\n" + task.statusLabel() + " · #" + task.id, false, () -> loadTask(task.id, null));
                    taskButton.setAllCaps(false);
                    taskButton.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                    taskButton.setPadding(dp(16), dp(12), dp(16), dp(12));
                }
            }
        }, null);
    }

    private void loadTask(long taskId, String notice) {
        currentTask = null;
        screen("Field workspace", "Task details", "Retrieving current state…");
        detailsVisible = true;
        button("Back to assignments", false, this::showTasks);
        run("Loading task…", () -> client().task(taskId), task -> showTask(task, notice, true), null);
    }

    private void showTask(Task task, String notice, boolean errorNotice) {
        currentTask = task;
        screen("Task #" + task.id, task.title, task.statusLabel());
        detailsVisible = true;
        button("Back to assignments", false, this::showTasks);
        label("Task brief");
        add(text(task.description.isEmpty() ? "No description provided." : task.description, 17, INK));
        add(text("Assigned to " + task.assigneeName + " · Version " + task.version, 13, Color.DKGRAY));
        if ("todo".equals(task.status)) {
            add(text("Accept this assignment when you are ready to start. Your coordinator will see the change immediately.", 16, INK));
            button("Accept assignment", true, () -> update(task, new JSONObject(), false));
        } else if ("in_progress".equals(task.status)) {
            label("Completion note");
            noteInput = input(task.completionNote, "Describe the work completed and evidence found", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            noteInput.setMinLines(3);
            noteInput.setMaxLines(8);
            label("Field location");
            add(text("Use your device location, or enter the site coordinates manually if permission is denied or a fix is unavailable. Both coordinates are required when submitting.", 14, Color.DKGRAY));
            button("Use device location", false, this::requestLocation);
            locationMessage = text("Location is requested only when you tap the button.", 13, Color.DKGRAY);
            add(locationMessage);
            label("Latitude (−90 to 90)");
            int coordinateInput = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
            latitudeInput = input(task.latitude == null ? "" : task.latitude.toString(), "e.g. -36.8485", coordinateInput);
            latitudeInput.setSingleLine(true);
            label("Longitude (−180 to 180)");
            longitudeInput = input(task.longitude == null ? "" : task.longitude.toString(), "e.g. 174.7633", coordinateInput);
            longitudeInput.setSingleLine(true);
            if (restoredDraft != null && restoredDraft.getLong("taskId") == task.id) {
                noteInput.setText(restoredDraft.getString("note", task.completionNote));
                latitudeInput.setText(restoredDraft.getString("latitude", ""));
                longitudeInput.setText(restoredDraft.getString("longitude", ""));
            }
            restoredDraft = null;
            button("Submit for review", true, () -> submit(task));
        } else {
            label("Submission evidence");
            add(text(task.completionNote.isEmpty() ? "No completion note." : task.completionNote, 17, INK));
            if (task.latitude != null && task.longitude != null) add(text(String.format(Locale.US,
                    "Site coordinates: %.6f, %.6f", task.latitude, task.longitude), 15, INK));
            add(text("submitted".equals(task.status)
                    ? "Your coordinator can approve this submission or return it for further work."
                    : "This task has been approved by the coordinator.", 15, Color.DKGRAY));
        }
        button("Refresh task", false, () -> loadTask(task.id, null));
        if (notice != null) {
            message.setText(notice);
            message.setTextColor(errorNotice ? Color.rgb(160, 40, 35) : TEAL);
            message.setVisibility(View.VISIBLE);
            message.announceForAccessibility(notice);
        }
    }

    private void submit(Task task) {
        String note = noteInput.getText().toString().trim();
        if (note.isEmpty() || note.length() > 1000) {
            error("Enter a completion note of 1–1,000 characters."); return;
        }
        try {
            double latitude = Double.parseDouble(latitudeInput.getText().toString().trim());
            double longitude = Double.parseDouble(longitudeInput.getText().toString().trim());
            if (Double.isNaN(latitude) || Double.isInfinite(latitude) || latitude < -90 || latitude > 90
                    || Double.isNaN(longitude) || Double.isInfinite(longitude) || longitude < -180 || longitude > 180) throw new NumberFormatException();
            JSONObject changes = new JSONObject().put("completionNote", note).put("latitude", latitude).put("longitude", longitude);
            update(task, changes, true);
        } catch (NumberFormatException error) {
            error("Enter valid latitude (−90 to 90) and longitude (−180 to 180), or use device location.");
        } catch (Exception error) { error(error.getMessage()); }
    }

    private void update(Task task, JSONObject changes, boolean submission) {
        try { changes.put("status", submission ? "submitted" : "in_progress"); }
        catch (Exception error) { error(error.getMessage()); return; }
        if (submission) {
            restoredDraft = new Bundle();
            restoredDraft.putLong("taskId", task.id);
            restoredDraft.putString("note", noteInput.getText().toString());
            restoredDraft.putString("latitude", latitudeInput.getText().toString());
            restoredDraft.putString("longitude", longitudeInput.getText().toString());
        }
        run(submission ? "Submitting field evidence…" : "Accepting assignment…", () -> client().update(task, changes), updated -> {
            restoredDraft = null;
            showTask(updated, submission ? "Submitted. The coordinator can now review your evidence." : "Assignment accepted.", false);
        }, failure -> {
            if (failure instanceof ApiClient.ApiException && ((ApiClient.ApiException) failure).status == 409) {
                loadTask(task.id, "This task changed on the server. The latest version is shown; review it before trying again.");
                return true;
            }
            return false;
        });
    }

    private void requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION);
            return;
        }
        fetchLocation();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != LOCATION_PERMISSION || !detailsVisible || noteInput == null) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) fetchLocation();
        else locationMessage.setText(R.string.location_denied);
    }

    private void fetchLocation() {
        if (!detailsVisible || noteInput == null || busy) return;
        stopLocation();
        try {
            Criteria criteria = new Criteria();
            boolean precise = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            criteria.setAccuracy(precise ? Criteria.ACCURACY_FINE : Criteria.ACCURACY_COARSE);
            String provider = locationManager.getBestProvider(criteria, true);
            if (provider == null) {
                locationMessage.setText(R.string.location_disabled); return;
            }
            locationMessage.setText(R.string.location_finding);
            int version = screenVersion;
            locationListener = new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    if (version != screenVersion || noteInput == null) { stopLocation(); return; }
                    latitudeInput.setText(String.format(Locale.US, "%.6f", location.getLatitude()));
                    longitudeInput.setText(String.format(Locale.US, "%.6f", location.getLongitude()));
                    locationMessage.setText(String.format(Locale.US, "Device location captured · accuracy ±%.0f m. Check that this is the task site.", location.getAccuracy()));
                    stopLocation();
                }
                @Override public void onProviderDisabled(String provider) {
                    if (version == screenVersion) locationMessage.setText(R.string.location_provider_disabled);
                    stopLocation();
                }
                @Override public void onProviderEnabled(String provider) { }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
            };
            locationTimeout = () -> {
                if (version == screenVersion) locationMessage.setText(R.string.location_timeout);
                stopLocation();
            };
            handler.postDelayed(locationTimeout, 20000);
            locationManager.requestLocationUpdates(provider, 0, 0, locationListener, Looper.getMainLooper());
        } catch (SecurityException exception) {
            stopLocation(); locationMessage.setText(R.string.location_changed_permission);
        } catch (Exception exception) {
            stopLocation(); locationMessage.setText(R.string.location_unavailable);
        }
    }

    private void stopLocation() {
        if (locationTimeout != null) { handler.removeCallbacks(locationTimeout); locationTimeout = null; }
        if (locationListener != null && locationManager != null) {
            try { locationManager.removeUpdates(locationListener); } catch (SecurityException ignored) { }
            locationListener = null;
        }
    }

    private ApiClient client() { return new ApiClient(session.baseUrl, session.token); }

    private interface Work<T> { T perform() throws Exception; }
    private interface Success<T> { void receive(T value) throws Exception; }
    private interface Failure { boolean handle(Exception failure); }

    private <T> void run(String status, Work<T> work, Success<T> success, Failure failure) {
        if (busy) return;
        int version = screenVersion;
        setBusy(true);
        message.setText(status);
        message.setTextColor(TEAL);
        message.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                T result = work.perform();
                handler.post(() -> {
                    if (isFinishing() || isDestroyed() || version != screenVersion) return;
                    setBusy(false);
                    message.setVisibility(View.GONE);
                    try { success.receive(result); } catch (Exception exception) { error("Invalid server response: " + exception.getMessage()); }
                });
            } catch (Exception exception) {
                handler.post(() -> {
                    if (isFinishing() || isDestroyed() || version != screenVersion) return;
                    setBusy(false);
                    if (exception instanceof ApiClient.ApiException && ((ApiClient.ApiException) exception).status == 401 && session != null) {
                        showLogin("Your session expired. Sign in again to continue."); return;
                    }
                    if (failure != null && failure.handle(exception)) return;
                    String detail = exception.getMessage();
                    if (!(exception instanceof ApiClient.ApiException)) detail = "Cannot reach the Payara API. Check the server address, network and deployment. " + (detail == null ? "" : detail);
                    error(detail == null ? "Request failed. Try again." : detail);
                });
            }
        });
    }

    private void setBusy(boolean value) {
        busy = value;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        for (View action : actions) action.setEnabled(!value);
    }

    private void error(String value) {
        message.setTextColor(Color.rgb(160, 40, 35));
        message.setText(value);
        message.setVisibility(View.VISIBLE);
        message.announceForAccessibility(value);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(5), 0, dp(7));
        return view;
    }

    private void label(String value) {
        TextView label = text(value, 16, INK);
        label.setTypeface(null, Typeface.BOLD);
        label.setPadding(0, dp(20), 0, dp(4));
        add(label);
    }

    private EditText input(String value, String hint, int inputType) {
        EditText field = new EditText(this);
        field.setInputType(inputType); field.setHint(hint); field.setText(value);
        field.setTextSize(16); field.setTextColor(INK); field.setPadding(dp(10), dp(10), dp(10), dp(10));
        field.setContentDescription(hint);
        add(field); actions.add(field); return field;
    }

    private Button button(String caption, boolean primary, Runnable action) {
        Button button = new Button(this);
        button.setText(caption); button.setAllCaps(false); button.setTextSize(16);
        button.setTextColor(primary ? Color.WHITE : TEAL);
        if (primary) button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(TEAL));
        button.setOnClickListener(v -> action.run());
        add(button); actions.add(button); return button;
    }

    private void add(View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(5);
        body.addView(view, params);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onBackPressed() {
        if (session != null && detailsVisible) showTasks(); else if (session != null) showLogin(null); else super.onBackPressed();
    }

    @Override protected void onStop() {
        super.onStop();
        if (locationListener != null && locationMessage != null) locationMessage.setText(R.string.location_paused);
        stopLocation();
    }

    @Override protected void onDestroy() {
        screenVersion++;
        stopLocation();
        executor.shutdownNow();
        super.onDestroy();
    }

    private static final class ProjectTasks {
        final String name;
        final List<Task> tasks = new ArrayList<>();
        ProjectTasks(String name) { this.name = name; }
    }
}
