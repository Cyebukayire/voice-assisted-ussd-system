package com.voiceussd.prototype;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.voiceussd.prototype.services.USSDDetectorService;

public class MainActivity extends Activity {
    private static final String TAG = "USSDDetector";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private TextView statusText;
    private Button enableServiceButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create simple UI
        setContentView(createUI());

        // Request necessary permissions
        requestPermissions();

        Log.d(TAG, "MainActivity created");
    }

    private android.widget.LinearLayout createUI() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        statusText = new TextView(this);
        statusText.setText("USSD Detector - Checking permissions...");
        statusText.setTextSize(16);
        layout.addView(statusText);

        enableServiceButton = new Button(this);
        enableServiceButton.setText("Enable Accessibility Service");
        enableServiceButton.setOnClickListener(v -> openAccessibilitySettings());
        layout.addView(enableServiceButton);

        return layout;
    }

    private void requestPermissions() {
        String[] permissions = {
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.SYSTEM_ALERT_WINDOW,
                Manifest.permission.RECORD_AUDIO
        };

        boolean needsPermission = false;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                needsPermission = true;
                break;
            }
        }

        if (needsPermission) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        } else {
            checkAccessibilityService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                statusText.setText("Permissions granted! Now enable accessibility service.");
                checkAccessibilityService();
            } else {
                statusText.setText("Permissions required for USSD detection!");
                Toast.makeText(this, "Please grant all permissions", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void checkAccessibilityService() {
        if (isAccessibilityServiceEnabled()) {
            statusText.setText("Ready! Dial *182# to test USSD detection.");
            enableServiceButton.setEnabled(false);
        } else {
            statusText.setText("Please enable the accessibility service to detect USSD windows.");
            enableServiceButton.setEnabled(true);
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        try {
            int accessibilityEnabled = Settings.Secure.getInt(
                    getApplicationContext().getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED
            );

            if (accessibilityEnabled != 1) {
                return false;
            }

            // Check if our specific service is enabled
            String settingValue = Settings.Secure.getString(
                    getApplicationContext().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );

            if (settingValue != null) {
                // Updated to reference the separate service class
                ComponentName expectedComponentName = new ComponentName(this, USSDDetectorService.class);
                String expectedServiceName = expectedComponentName.flattenToString();

                return settingValue.contains(getPackageName()) ||
                        settingValue.contains(expectedServiceName);
            }

        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Error checking accessibility service: " + e.getMessage());
        }

        return false;
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "Find 'USSD Detector' and enable it", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAccessibilityService();
    }
}