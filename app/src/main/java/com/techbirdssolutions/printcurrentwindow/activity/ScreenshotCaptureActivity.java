package com.techbirdssolutions.printcurrentwindow.activity;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler; // Make sure this is imported
import android.os.Looper;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.techbirdssolutions.printcurrentwindow.R;
import com.techbirdssolutions.printcurrentwindow.service.BitmapPrintDocumentAdapter;
import com.techbirdssolutions.printcurrentwindow.service.FloatingWindowService;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreenshotCaptureActivity extends Activity {

    private static final String TAG = "ScreenshotCaptureAct";
    private static final int REQUEST_MEDIA_PROJECTION = 1;

    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;
    private ImageReader mImageReader;
    private int mScreenDensity;
    private int mWidth;
    private int mHeight;

    private Uri mCapturedImageUri;
    private Handler mHandler; // Handler is already declared and initialized in onCreate

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "ScreenshotCaptureActivity created.");

        mHandler = new Handler(Looper.getMainLooper()); // Initialize handler

        mMediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mMediaProjectionManager == null) {
            Log.e(TAG, "MediaProjectionManager is null.");
            Toast.makeText(this, "MediaProjectionManager not available.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        startProjectionRequest();
    }
    private void startProjectionRequest() {
        Log.d(TAG, "Requesting MediaProjection permission.");
        startActivityForResult(mMediaProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK) {
                mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
                if (mMediaProjection != null) {
                    setupVirtualDisplay();
                    if (FloatingWindowService.screenshotButton != null) {
                        FloatingWindowService.screenshotButton.setVisibility(View.INVISIBLE);
                    }
                    mHandler.postDelayed(this::captureScreenshot, 500);
                } else {
                    Log.e(TAG, "MediaProjection failed to start.");
                    Toast.makeText(this, "Failed to start MediaProjection.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            } else {
                Log.w(TAG, "Permission denied.");
                Toast.makeText(this, "Screen capture permission denied.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void setupVirtualDisplay() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        } else {
            metrics = getResources().getDisplayMetrics();
        }

        mScreenDensity = metrics.densityDpi;
        mWidth = metrics.widthPixels;
        mHeight = metrics.heightPixels;

        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 1);
        mMediaProjection.createVirtualDisplay(
                "ScreenCapture",
                mWidth,
                mHeight,
                mScreenDensity,
                0,
                mImageReader.getSurface(),
                null,
                null
        );
    }

    private void captureScreenshot() {
        Image image = null;
        Bitmap bitmap = null;
        try {
            image = mImageReader.acquireLatestImage();
            if (image == null) {
                Toast.makeText(this, "Failed to capture image.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * mWidth;

            bitmap = Bitmap.createBitmap(mWidth + rowPadding / pixelStride, mHeight, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);

            Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, mWidth, mHeight);
            bitmap.recycle();

            Bitmap grayscaleBitmap = toGrayscale(croppedBitmap);
            croppedBitmap.recycle();

            saveBitmapToGallery(grayscaleBitmap);
            grayscaleBitmap.recycle();

        } catch (Exception e) {
            Log.e(TAG, "Error capturing screenshot: " + e.getMessage(), e);
            Toast.makeText(this, "Error capturing screenshot.", Toast.LENGTH_SHORT).show();
            finish();
        } finally {
            if (image != null) image.close();
            if (FloatingWindowService.screenshotButton != null) {
                FloatingWindowService.screenshotButton.setVisibility(View.VISIBLE);
            }
        }
    }

    private Bitmap toGrayscale(Bitmap original) {
        Bitmap grayscale = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(grayscale);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(original, 0, 0, paint);
        return grayscale;
    }

    private void saveBitmapToGallery(Bitmap bitmap) throws IOException {
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "SCREENSHOT_" + timeStamp + ".png";

        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "Screenshots");
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots");
            if (!dir.exists()) dir.mkdirs();
            values.put(MediaStore.MediaColumns.DATA, new File(dir, fileName).getAbsolutePath());
        }

        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("Failed to create new MediaStore record.");

        try (OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) throw new IOException("Failed to get output stream for URI: " + uri);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            Toast.makeText(this, "Screenshot saved in black and white.", Toast.LENGTH_LONG).show();
        }

        mCapturedImageUri = uri;
        printScreenshot(uri);
    }

    private void printScreenshot(Uri imageUri) {
        if (imageUri == null) {
            Toast.makeText(this, "No image to print.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        if (printManager != null) {
            PrintDocumentAdapter adapter = new BitmapPrintDocumentAdapter(this, imageUri, mWidth, mHeight);
            printManager.print("Screenshot Print", adapter, new PrintAttributes.Builder().build());
            Log.d(TAG, "Print job initiated. Will close activity in 10 seconds.");
            Toast.makeText(this, "Printing screenshot...", Toast.LENGTH_SHORT).show();

            // --- ADDED: 10-second delay before finishing the activity ---
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "10-second delay complete. Closing ScreenshotCaptureActivity.");
                    finish();
                }
            }, 10000); // 10000 milliseconds = 10 seconds

        } else {
            Toast.makeText(this, "Printing not available on this device.", Toast.LENGTH_SHORT).show();
            finish(); // Close activity immediately if printing isn't available
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "ScreenshotCaptureActivity destroyed.");
        // It's good practice to remove any pending callbacks to prevent leaks,
        // although finish() usually handles this for activities.
        mHandler.removeCallbacksAndMessages(null);
        stopProjection();
    }

    private void stopProjection() {
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }
}