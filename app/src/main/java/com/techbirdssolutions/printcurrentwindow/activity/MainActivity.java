package com.techbirdssolutions.printcurrentwindow.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.techbirdssolutions.printcurrentwindow.R;
import com.techbirdssolutions.printcurrentwindow.service.FloatingWindowService;

public class MainActivity extends AppCompatActivity {

    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // You'll need to create this layout

        Button startServiceButton = findViewById(R.id.start_service_button);
        startServiceButton.setOnClickListener(v -> {
            if (checkOverlayPermission()) {
                startFloatingService();
            } else {
                requestOverlayPermission();
            }
        });
    }

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true; // Permissions not needed for older Android versions
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (checkOverlayPermission()) {
                startFloatingService();
            } else {
                Toast.makeText(this, "Overlay permission denied. Cannot start service.", Toast.LENGTH_SHORT).show();
            }
        }
        // Handle MediaProjection permission result here if you start it from an activity
        // For MediaProjection, you'll typically have another request code (e.g., RESULT_OK)
        // mediaProjectionManager.getMediaProjection(resultCode, data);
    }

    private void startFloatingService() {
        Intent serviceIntent = new Intent(this, FloatingWindowService.class);
        // For Android O (API 26) and above, use startForegroundService
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "Floating service started!", Toast.LENGTH_SHORT).show();
        finish(); // Optionally close the main activity after starting the service
    }
}