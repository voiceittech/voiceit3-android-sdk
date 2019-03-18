package com.voiceit.voiceit2;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.media.MediaRecorder;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.Surface;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.File;
import java.io.IOException;
import java.util.Random;

class Utils {
    private static final String mTAG = "Utils";
    static int oldBrightness;
    static float luxThreshold = 50.0f;

     /** Create a File for saving an image or audio file */
     static File getOutputMediaFile(String suffix){
        try {
            return File.createTempFile("tempfile", suffix);
        } catch (IOException e) {
            Log.e(mTAG,"Creating " + suffix + " file failed with exception : " + e.getMessage());
            return null;
        }
    }

    static void randomizeArrayOrder(int [] array) {
        final Random rand = new Random();
        for(int i = 0; i < array.length; i++) {
            int j = rand.nextInt(array.length -1);
            int temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
    }

    static void startMediaRecorder(MediaRecorder mediaRecorder, File audioFile) {
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mediaRecorder.setAudioSamplingRate(44100);
        mediaRecorder.setAudioChannels(1);
        mediaRecorder.setAudioEncodingBitRate(16000);
        mediaRecorder.setOutputFile(audioFile.getAbsolutePath());
        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            Log.e(mTAG, "MediaRecorder prepare failed");
        }
        mediaRecorder.start();
    }

    static int lockOrientationCode(int code) {
        switch (code) {
            case Surface.ROTATION_0:
                return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            case Surface.ROTATION_180:
                return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            case Surface.ROTATION_90:
                return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            case Surface.ROTATION_270:
                return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
            default:
                return 1;
        }
    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    static boolean startCameraSource(Activity activity, CameraSource cameraSource, CameraSourcePreview preview) {

        // Check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity);
        if (code != ConnectionResult.SUCCESS) {
            final int RC_HANDLE_GMS = 9001;
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(activity, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (cameraSource != null) {
            try {
                preview.start(cameraSource);
            } catch (IOException e) {
                Log.d(mTAG, "Unable to start camera source.", e);
                cameraSource.release();
                return false;
            }
        }
        return  true;
    }

    /**
     * Creates and starts the camera.
     */
    static CameraSource createCameraSource(Activity activity, MultiProcessor.Factory<Face> faceTrackerFactory) {

        FaceDetector detector = new FaceDetector.Builder(activity)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setMode(FaceDetector.ACCURATE_MODE)
                .setProminentFaceOnly(true)
                .build();

        detector.setProcessor(
                new MultiProcessor.Builder<>(faceTrackerFactory)
                        .build());

        if (!detector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.w(mTAG, "Face detector dependencies are not yet available.");
            Toast.makeText(activity, "Downloading Face detector dependencies", Toast.LENGTH_LONG).show();
            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            IntentFilter lowStorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = activity.registerReceiver(null, lowStorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(activity, "Face detector dependencies cannot be downloaded due to low device storage", Toast.LENGTH_LONG).show();
                Log.w(mTAG, "Face detector dependencies cannot be downloaded due to low device storage");
            }
        }

        // Build camera source and attach detector
        return new CameraSource.Builder(activity, detector)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .build();
    }

    static boolean setBrightness(Activity activity, int brightness) {

        if(Build.VERSION.SDK_INT >= 23) {
            if (!Settings.System.canWrite(activity)) {
                return false;
            }
        }

        // Content resolver used as a handle to the system's settings
        ContentResolver cResolver = activity.getContentResolver();

        try {
            // To handle the auto
            Settings.System.putInt(cResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            // Get the current system brightness
            Utils.oldBrightness = Settings.System.getInt(cResolver, Settings.System.SCREEN_BRIGHTNESS);
        } catch (Settings.SettingNotFoundException e) {
            Log.e("Error", "SettingNotFoundException: " + e.getMessage());
            return false;
        }

        // Set the system brightness using the brightness variable value
        Settings.System.putInt(cResolver, Settings.System.SCREEN_BRIGHTNESS, brightness);

        Window window = activity.getWindow();
        // Get the current window attributes
        WindowManager.LayoutParams layoutparams = window.getAttributes();
        // Set the brightness of this window
        layoutparams.screenBrightness = 1;
        // Apply attribute changes to this window
        window.setAttributes(layoutparams);

        return true;
    }
}
