package com.techbirdssolutions.printcurrentwindow.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast; // Not used in the provided code, but good to keep if intended for future use

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.techbirdssolutions.printcurrentwindow.R;
import com.techbirdssolutions.printcurrentwindow.activity.ScreenshotCaptureActivity;

// import java.io.File; // For screenshot saving - Not used in this service, but related to the app's functionality

public class FloatingWindowService extends Service {

    private WindowManager mWindowManager;
    private View mFloatingView;
    public static Button screenshotButton; // Made public static as per your code, but consider if this is truly necessary

    private static final String CHANNEL_ID = "FloatingServiceChannel";
    private static final int NOTIFICATION_ID = 123;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // This service is not designed to be bound, so return null.
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. Create Notification Channel for Android O (API 26) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Floating Service Channel",
                    NotificationManager.IMPORTANCE_LOW // Low importance to be less intrusive
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        // 2. Build the Notification for the Foreground Service
        // A foreground service must have an ongoing notification.
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Assist Menu Running")
                .setContentText("Tap to open app, long press to stop.") // Example text
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your actual app icon
                .build();

        // Start the service in the foreground
        startForeground(NOTIFICATION_ID, notification);

        // 3. Inflate the Floating Layout
        mFloatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null);

        // 4. Set up Layout Parameters for the Floating Window
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, // Width of the floating view
                WindowManager.LayoutParams.WRAP_CONTENT, // Height of the floating view
                // Type of window overlay (different for older and newer Android versions)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : // For Android O and above
                        WindowManager.LayoutParams.TYPE_PHONE, // For older Android versions
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // Allows touch events to pass through to underlying windows except on the floating view itself
                PixelFormat.TRANSLUCENT // Allows transparency
        );

        // Set initial position and gravity
        params.gravity = Gravity.TOP | Gravity.START; // Position relative to top-left corner
        params.x = 0; // Initial X coordinate
        params.y = 100; // Initial Y coordinate

        // 5. Add the Floating View to the Window Manager
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (mWindowManager != null) {
            mWindowManager.addView(mFloatingView, params);
        }

        // 6. Find and Set up the Screenshot Button
        screenshotButton = mFloatingView.findViewById(R.id.screenshot_button);
        screenshotButton.setOnClickListener(v -> {
            // Start the ScreenshotCaptureActivity when the button is clicked
            Intent screenshotIntent = new Intent(FloatingWindowService.this, ScreenshotCaptureActivity.class);
            // These flags are important for starting an activity from a service context
            screenshotIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(screenshotIntent);
        });

        // 7. Make the Floating View Draggable
        mFloatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX; // Initial X coordinate of the floating view
            private int initialY; // Initial Y coordinate of the floating view
            private float initialTouchX; // Initial raw X coordinate of touch event
            private float initialTouchY; // Initial raw Y coordinate of touch event

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // Record initial positions when touch starts
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true; // Consume the event, indicating we're handling the touch
                    case MotionEvent.ACTION_UP:
                        // Check if it was a tap (small movement) or a drag
                        if (Math.abs(event.getRawX() - initialTouchX) < 10 &&
                                Math.abs(event.getRawY() - initialTouchY) < 10) {
                            v.performClick(); // If it's a tap, trigger the click listener
                        }
                        return true; // Consume the event
                    case MotionEvent.ACTION_MOVE:
                        // Calculate new position based on drag
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        // Update the view's layout in the WindowManager
                        mWindowManager.updateViewLayout(mFloatingView, params);
                        return true; // Consume the event
                }
                return false; // For any other actions, let them be handled elsewhere if necessary
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Remove the floating view from the window manager when the service is destroyed
        if (mFloatingView != null && mWindowManager != null) {
            mWindowManager.removeView(mFloatingView);
        }
        // Stop the foreground service and remove its notification
        stopForeground(true);
    }
}