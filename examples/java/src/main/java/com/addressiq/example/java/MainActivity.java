package com.addressiq.example.java;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;

import com.addressiq.android.AddressIQEnvironment;
import com.addressiq.android.VerificationLifecycleState;
import com.addressiq.android.java.AddressIQJava;
import com.addressiq.android.ui.AddressIQVerifyContract;
import com.addressiq.android.ui.AddressIQVerifyInput;
import com.addressiq.android.ui.AddressIQVerifyResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Java parity example for the AddressIQ SDK — the Java-bridge counterpart of the
 * Kotlin sample. It exercises the same SDK surface through {@link AddressIQJava}
 * (a {@link CompletableFuture} façade over the coroutine API) and the Collect UI
 * via {@link AddressIQVerifyContract}:
 *
 *   Login → Collect → Verify (digital / physical / combined) → Permissions →
 *   Developer (raw calls, state, cancel, providers) → Settings (logout / reset).
 *
 * Track A (Collect UI contract) and Track B (imperative {@code AddressIQJava.*})
 * are both driven here, matching the Kotlin example. UI is built programmatically
 * to keep the parity surface in one readable file.
 *
 * The SDK is linked to the LOCAL build via the composite build in settings.gradle.kts.
 */
public class MainActivity extends ComponentActivity {

    private static final String SEED_KEY = "aiq_test_demo_bank_seed01";

    private LinearLayout container;
    private TextView logView;
    private final StringBuilder logBuffer = new StringBuilder();

    // Login inputs.
    private EditText apiKeyField;
    private EditText appUserIdField;
    private EditText businessNameField;
    private AddressIQEnvironment environment = AddressIQEnvironment.STAGING;
    private Button envButton;

    // Collected locationCodes; the last one drives the verify actions.
    private final List<String> locationCodes = new ArrayList<>();

    // Collect UI launcher (Track A). Registered before the activity starts.
    private ActivityResultLauncher<AddressIQVerifyInput> verifyLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        container = findViewById(R.id.container);

        verifyLauncher = registerForActivityResult(new AddressIQVerifyContract(), this::onCollectResult);

        buildUi();
    }

    // ── UI ──────────────────────────────────────────────────────────────

    private void buildUi() {
        header("AddressIQ — Java bridge");

        section("Login");
        apiKeyField = field("API key", SEED_KEY);
        appUserIdField = field("App user ID", "cust_sample_001");
        businessNameField = field("Business name (fallback)", "Kuda Business");
        envButton = button("Environment: " + environment, this::toggleEnvironment);
        button("Continue  (initialize + setUser)", this::login);

        section("Collect");
        button("Collect Address  (Collect UI → startVerification)", this::launchCollect);

        section("Verify an address");
        button("Digital Verification", () -> startDigital(workingLocationCode()));
        button("Physical Verification", () -> startPhysical(workingLocationCode()));
        button("Digital + Physical", () -> startCombined(workingLocationCode()));

        section("Permissions");
        button("Refresh permission state", this::refreshPermissions);
        button("Request permissions", this::requestPermissions);

        section("Developer");
        button("getVerificationState()", this::showState);
        button("cancelVerification(code)", this::cancel);
        button("listProviders()", this::listProviders);

        section("Settings");
        button("Log out", this::logout);
        button("Reset SDK", this::reset);

        section("Log");
        logView = new TextView(this);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextSize(12);
        logView.setLayoutParams(rowParams());
        container.addView(logView);
        log("ready — set credentials and tap Continue");
    }

    private void header(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(20);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLayoutParams(rowParams());
        container.addView(t);
    }

    private void section(String title) {
        TextView t = new TextView(this);
        t.setText(title.toUpperCase());
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(18), 0, dp(4));
        t.setLayoutParams(rowParams());
        container.addView(t);
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextSize(14);
        e.setLayoutParams(rowParams());
        container.addView(e);
        return e;
    }

    private Button button(String label, Runnable onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener((View v) -> onClick.run());
        b.setLayoutParams(rowParams());
        container.addView(b);
        return b;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(6);
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void log(String line) {
        logBuffer.insert(0, line + "\n");
        if (logView != null) logView.setText(logBuffer.toString());
    }

    /** Marshal a bridge callback back to the UI thread and log it. */
    private <T> void whenDone(CompletableFuture<T> future, Consumer<T> onOk, String label) {
        future.whenComplete((value, err) -> runOnUiThread(() -> {
            if (err != null) log(label + " error: " + err.getMessage());
            else onOk.accept(value);
        }));
    }

    // ── Actions (mirror the Kotlin SampleViewModel) ─────────────────────

    private void toggleEnvironment() {
        // Cycle STAGING → PRODUCTION → DEVELOPMENT (local backend at
        // http://10.0.2.2:4000, the emulator's view of the host's localhost).
        switch (environment) {
            case STAGING:
                environment = AddressIQEnvironment.PRODUCTION;
                break;
            case PRODUCTION:
                environment = AddressIQEnvironment.DEVELOPMENT;
                break;
            default:
                environment = AddressIQEnvironment.STAGING;
                break;
        }
        envButton.setText("Environment: " + environment);
    }

    private void login() {
        try {
            AddressIQJava.initialize(AddressIQJava.config()
                    .apiKey(text(apiKeyField))
                    .environment(environment)
                    .build());
            log("initialized (" + environment + ")");
        } catch (Exception e) {
            log("init error: " + e.getMessage());
            return;
        }
        whenDone(
                AddressIQJava.setUser(AddressIQJava.user().appUserId(text(appUserIdField)).firstName("Sample").build()),
                v -> log("user set; lifecycle: " + AddressIQJava.getVerificationState().getState()),
                "setUser");
    }

    private void launchCollect() {
        // 12-arg data-class constructor: Java can't use Kotlin default args, so
        // fill the optional slots with null. Order matches AddressIQVerifyInput.
        AddressIQVerifyInput input = new AddressIQVerifyInput(
                text(apiKeyField),                 // apiKey
                text(appUserIdField),              // appUserId
                environment,                       // environment
                null, null, null, null,            // phone, firstName, lastName, email
                null,                              // theme
                null, null,                        // privacyPolicyUrl, termsUrl
                blankToNull(text(businessNameField)), // businessName
                null                               // widgetUrl
        );
        verifyLauncher.launch(input);
    }

    private void onCollectResult(AddressIQVerifyResult result) {
        if (result instanceof AddressIQVerifyResult.Completed) {
            AddressIQVerifyResult.Completed c = (AddressIQVerifyResult.Completed) result;
            locationCodes.add(c.getLocationCode());
            log("collected: " + c.getLocationCode() + " (" + c.getFormattedAddress() + ")");
            // The Collect UI collects only — the host starts verification here.
            startDigital(c.getLocationCode());
        } else if (result instanceof AddressIQVerifyResult.Failed) {
            AddressIQVerifyResult.Failed f = (AddressIQVerifyResult.Failed) result;
            log("collect failed: [" + f.getCode() + "] " + f.getMessage());
        } else {
            log("collect cancelled");
        }
    }

    private void startDigital(String code) {
        whenDone(AddressIQJava.startVerification(this, code),
                res -> log("digital started: " + res.get("verificationCode") + " / " + res.get("status")),
                "startVerification");
    }

    private void startPhysical(String code) {
        whenDone(AddressIQJava.startPhysicalVerification(this, code, "internal_agents"),
                res -> log("physical started: " + res.get("verificationCode") + " / " + res.get("status")),
                "startPhysicalVerification");
    }

    private void startCombined(String code) {
        whenDone(AddressIQJava.startDigitalAndPhysicalVerification(this, code, "internal_agents"),
                res -> log("combined started: " + res.get("verificationCode") + " / " + res.get("status")),
                "startDigitalAndPhysicalVerification");
    }

    private void cancel() {
        String vid = AddressIQJava.getVerificationState().getVerificationId();
        if (vid == null) {
            log("cancel: no active verification");
            return;
        }
        whenDone(AddressIQJava.cancelVerification(vid),
                res -> log("cancelled: " + res.get("status")),
                "cancelVerification");
    }

    private void listProviders() {
        whenDone(AddressIQJava.listProviders(),
                providers -> log("providers: " + providers.size() + " → " + providers),
                "listProviders");
    }

    private void refreshPermissions() {
        Map<String, String> perms = AddressIQJava.getPermissionState(this);
        StringBuilder sb = new StringBuilder("permissions:");
        for (Map.Entry<String, String> e : perms.entrySet()) {
            sb.append("\n  ").append(e.getKey()).append(" = ").append(e.getValue());
        }
        log(sb.toString());
    }

    private void requestPermissions() {
        whenDone(AddressIQJava.requestPermissions(this),
                perms -> log("permissions after prompt: " + perms),
                "requestPermissions");
    }

    private void showState() {
        VerificationLifecycleState s = AddressIQJava.getVerificationState();
        log("state=" + s.getState()
                + " verificationId=" + s.getVerificationId()
                + " locationCode=" + s.getLocationCode());
    }

    private void logout() {
        whenDone(AddressIQJava.logout(), v -> log("logged out"), "logout");
    }

    private void reset() {
        whenDone(AddressIQJava.reset(), v -> log("SDK reset"), "reset");
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private String workingLocationCode() {
        return locationCodes.isEmpty() ? "loc_sample_demo" : locationCodes.get(locationCodes.size() - 1);
    }

    private static String text(EditText e) {
        return e.getText().toString().trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
