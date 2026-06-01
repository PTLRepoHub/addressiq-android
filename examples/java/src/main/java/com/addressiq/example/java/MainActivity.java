package com.addressiq.example.java;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.addressiq.android.AddressIQConfig;
import com.addressiq.android.AddressIQEnvironment;
import com.addressiq.android.SdkUser;
import com.addressiq.android.java.AddressIQJava;

/**
 * Minimal Java example for the AddressIQ Android SDK, using the Java bridge
 * ({@link AddressIQJava}) — static methods that return {@link
 * java.util.concurrent.CompletableFuture} instead of Kotlin coroutines.
 *
 * The SDK is linked to the LOCAL build via the composite build in
 * settings.gradle.kts.
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final TextView log = findViewById(R.id.log);
        final Button btn = findViewById(R.id.btn);

        // initialize() is synchronous; the async methods return CompletableFuture.
        AddressIQJava.initialize(
                new AddressIQConfig("aiq_test_demo_bank_seed01", AddressIQEnvironment.SANDBOX, null));
        log.setText("initialized");

        btn.setOnClickListener(v ->
                AddressIQJava.setUser(new SdkUser("cust_sample_001", null, null, null, null))
                        .whenComplete((unused, err) -> runOnUiThread(() -> {
                            if (err != null) {
                                log.setText("setUser error: " + err.getMessage());
                            } else {
                                log.setText("user set; lifecycle: "
                                        + AddressIQJava.getVerificationState().getState());
                            }
                        })));
    }
}
