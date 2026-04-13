package com.voiceit.voiceit3;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public class VideoVerificationView extends AppCompatActivity implements SensorEventListener {

    private final String mTAG = "VideoVerificationView";

    private CameraXBinder cameraBinder;
    private MLKitFaceAnalyzer faceAnalyzer;
    private PreviewView previewView;

    private final Handler timingHandler = new Handler();
    private int voiceitThemeColor = 0;

    private Context mContext;
    private RadiusOverlayView mOverlay;

    private VoiceItAPI3 mVoiceIt3;
    private String mUserId = "";
    private String mContentLanguage = "";
    private String mPhrase = "";
    private File mPictureFile;
    private final int mNeededEnrollments = 1;
    private int mFailedAttempts = 0;
    private final int mMaxFailedAttempts = 3;
    private boolean mContinueVerifying = false;

    private volatile boolean continueDetecting = false;
    private volatile boolean lookingAway = false;

    private SensorManager sensorManager = null;
    private Sensor lightSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            mVoiceIt3 = new VoiceItAPI3(bundle.getString("apiKey"), bundle.getString("apiToken"));
            mUserId = bundle.getString("userId");
            mContentLanguage = bundle.getString("contentLanguage");
            mPhrase = bundle.getString("phrase");
            mVoiceIt3.setNotificationURL(bundle.getString("notificationURL"));
            this.voiceitThemeColor = bundle.getInt("voiceitThemeColor");
            if (this.voiceitThemeColor == 0) {
                this.voiceitThemeColor = getResources().getColor(R.color.progressCircle);
            }
        }

        try {
            getSupportActionBar().hide();
        } catch (NullPointerException e) {
            Log.d(mTAG, "Cannot hide action bar");
        }

        mContext = this;
        setContentView(R.layout.activity_video_verification_view);
        previewView = findViewById(R.id.camera_preview);
        mOverlay = findViewById(R.id.overlay);

        ((android.widget.TextView) findViewById(R.id.toolbarTitle)).setText("Verifying Video");
        findViewById(R.id.cancelButton).setOnClickListener(
                v -> exitViewWithMessage("voiceit-failure", "User cancelled"));

        if (Build.VERSION.SDK_INT >= 18) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        } else {
            setRequestedOrientation(Utils.lockOrientationCode(
                    getWindowManager().getDefaultDisplay().getRotation()));
        }

        PackageManager pm = getPackageManager();
        if (pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_LIGHT)) {
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }
        mPictureFile = Utils.getOutputMediaFile(".jpeg", this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void startVerificationFlow() {
        mContinueVerifying = true;
        continueDetecting = false;

        if (faceAnalyzer == null) faceAnalyzer = new MLKitFaceAnalyzer(this::onFacesDetected);
        if (cameraBinder == null) cameraBinder = new CameraXBinder(this, previewView);

        cameraBinder.bindForFace(faceAnalyzer, () -> {
            mVoiceIt3.getAllVideoEnrollments(mUserId, new Callback() {
                @Override
                public void onSuccess(JSONObject response) {
                    try {
                        if (response.getInt("count") < mNeededEnrollments) {
                            mOverlay.updateDisplayText("NOT_ENOUGH_ENROLLMENTS");
                            timingHandler.postDelayed(
                                    () -> exitViewWithMessage("voiceit-failure", "Not enough enrollments"),
                                    2500);
                        } else {
                            mOverlay.updateDisplayText("VIDEO_LOOK_INTO_CAM", mPhrase);
                            continueDetecting = true;
                        }
                    } catch (JSONException e) {
                        Log.d(mTAG, "JSON exception: " + e);
                    }
                }

                @Override
                public void onFailure(int statusCode, JSONObject errorBody, Throwable error) {
                    handleNetworkFailure(errorBody);
                }
            });
        });
    }

    private void onFacesDetected(MLKitFaceAnalyzer.Result result) {
        if (!continueDetecting) return;

        if (result.faceCount == 0) {
            lookingAway = true;
            mOverlay.setProgressCircleAngle(270.0, 0.0);
            mOverlay.updateDisplayText("VIDEO_LOOK_INTO_CAM", mPhrase);
            return;
        }

        if (result.faceCount > 1) {
            mOverlay.updateDisplayText("TOO_MANY_FACES");
            mOverlay.setProgressCircleAngle(270.0, 0.0);
            return;
        }

        if (mOverlay.insidePortraitCircle(this, result.boundingBox,
                result.imageWidth, result.imageHeight)) {
            lookingAway = false;
            continueDetecting = false;

            mOverlay.updateDisplayText("WAIT");
            new Handler().postDelayed(() -> {
                mOverlay.setProgressCircleAngle(270.0, 0.0);
                mOverlay.setProgressCircleColor(getResources().getColor(R.color.progressCircle));
                mOverlay.displayPicture = true;
                takePicture();
            }, 750);
        }
    }

    private void takePicture() {
        if (mPictureFile == null) {
            exitViewWithMessage("voiceit-failure", "Could not allocate picture file");
            return;
        }
        cameraBinder.takePicture(mPictureFile, new CameraXBinder.PictureCallback() {
            @Override
            public void onPictureTaken(File file) {
                cameraBinder.bindForVideo(VideoVerificationView.this::recordVideoAndVerify);
            }

            @Override
            public void onError(Throwable error) {
                Log.e(mTAG, "Camera capture exception", error);
                exitViewWithMessage("voiceit-failure", "Camera Error");
            }
        });
    }

    private void recordVideoAndVerify() {
        if (!mContinueVerifying) return;

        mOverlay.updateDisplayText("SAY_PASSPHRASE", mPhrase);
        final File audioVideoFile = Utils.getOutputVideoFile(".mp4", this);
        final File audioFile = Utils.getOutputAudioFile(".wav", this);
        if (audioVideoFile == null || audioFile == null) {
            exitViewWithMessage("voiceit-failure", "Creating audio file failed");
            return;
        }

        mOverlay.setProgressCircleColor(this.voiceitThemeColor);
        mOverlay.startDrawingProgressCircle();

        cameraBinder.startRecording(audioVideoFile, new CameraXBinder.VideoCallback() {
            @Override
            public void onVideoFinalized(File file) {
                Utils.stripAudio(audioVideoFile, audioFile, () -> {
                    mOverlay.updateDisplayText("WAIT");
                    submitVerification(audioFile, audioVideoFile);
                });
            }

            @Override
            public void onError(Throwable error) {
                Log.e(mTAG, "Video recording error", error);
                exitViewWithMessage("voiceit-failure", "Recording Error");
            }
        });

        timingHandler.postDelayed(() -> {
            if (mContinueVerifying && cameraBinder != null) cameraBinder.stopRecording();
        }, 4800);
    }

    private void submitVerification(final File audioFile, final File audioVideoFile) {
        mVoiceIt3.videoVerification(mUserId, mContentLanguage, mPhrase,
                audioFile, mPictureFile, new Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    if (response.getString("responseCode").equals("SUCC")) {
                        mOverlay.setProgressCircleColor(getResources().getColor(R.color.success));
                        mOverlay.updateDisplayText("VERIFY_SUCCESS");
                        timingHandler.postDelayed(() -> {
                            audioFile.deleteOnExit();
                            mPictureFile.deleteOnExit();
                            audioVideoFile.deleteOnExit();
                            exitViewWithJSON("voiceit-success", response);
                        }, 2000);
                    } else {
                        audioFile.deleteOnExit();
                        mPictureFile.deleteOnExit();
                        audioVideoFile.deleteOnExit();
                        failVerification(response);
                    }
                } catch (JSONException e) {
                    Log.d(mTAG, "JSON Error: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(int statusCode, JSONObject errorBody, Throwable error) {
                if (errorBody != null) {
                    audioFile.deleteOnExit();
                    mPictureFile.deleteOnExit();
                    failVerification(errorBody);
                } else {
                    Log.e(mTAG, "No response from server");
                    mOverlay.updateDisplayTextAndLock("CHECK_INTERNET");
                    timingHandler.postDelayed(() -> exitViewWithMessage(
                            "voiceit-failure", "No response from server"), 2000);
                }
            }
        });
    }

    private void failVerification(final JSONObject response) {
        mOverlay.setPicture(null);
        mOverlay.setProgressCircleColor(getResources().getColor(R.color.failure));
        mOverlay.updateDisplayText("VERIFY_FAIL");
        timingHandler.postDelayed(() -> {
            try {
                mOverlay.updateDisplayText(response.getString("responseCode"));
            } catch (JSONException e) {
                Log.d(mTAG, "JSON exception: " + e);
            }
            timingHandler.postDelayed(() -> {
                mFailedAttempts++;
                if (mFailedAttempts >= mMaxFailedAttempts) {
                    mOverlay.updateDisplayText("TOO_MANY_ATTEMPTS");
                    timingHandler.postDelayed(
                            () -> exitViewWithJSON("voiceit-failure", response),
                            2000);
                } else if (mContinueVerifying) {
                    if (lookingAway) {
                        mOverlay.updateDisplayText("VIDEO_LOOK_INTO_CAM", mPhrase);
                    }
                    if (cameraBinder != null) {
                        cameraBinder.bindForFace(faceAnalyzer, () -> continueDetecting = true);
                    }
                }
            }, 4500);
        }, 1500);
    }

    private void handleNetworkFailure(JSONObject errorBody) {
        if (errorBody != null) {
            try {
                mOverlay.updateDisplayText(errorBody.getString("responseCode"));
            } catch (JSONException e) {
                Log.d(mTAG, "JSON exception: " + e);
            }
            timingHandler.postDelayed(
                    () -> exitViewWithJSON("voiceit-failure", errorBody),
                    2000);
        } else {
            Log.e(mTAG, "No response from server");
            mOverlay.updateDisplayTextAndLock("CHECK_INTERNET");
            timingHandler.postDelayed(
                    () -> exitViewWithMessage("voiceit-failure", "No response from server"),
                    2000);
        }
    }

    private void requestHardwarePermissions() {
        final int ASK_MULTIPLE_PERMISSION_REQUEST_CODE = 2;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA}, ASK_MULTIPLE_PERMISSION_REQUEST_CODE);
        } else {
            startVerificationFlow();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(mTAG, "Hardware Permissions not granted");
            exitViewWithMessage("voiceit-failure", "User Canceled");
        } else {
            startVerificationFlow();
        }
    }

    private void exitViewWithMessage(String action, String message) {
        mContinueVerifying = false;
        timingHandler.removeCallbacksAndMessages(null);
        Intent intent = new Intent(action);
        JSONObject json = new JSONObject();
        try {
            json.put("message", message);
        } catch (JSONException e) {
            Log.d(mTAG, "JSON Exception: " + e.getMessage());
        }
        intent.putExtra("Response", json.toString());
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        finish();
        overridePendingTransition(0, 0);
    }

    private void exitViewWithJSON(String action, JSONObject json) {
        mContinueVerifying = false;
        timingHandler.removeCallbacksAndMessages(null);
        Intent intent = new Intent(action);
        intent.putExtra("Response", json.toString());
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        finish();
        overridePendingTransition(0, 0);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override public void onSensorChanged(SensorEvent event) {
        float lux = event.values[0];
        mOverlay.setLowLightMode(lux < Utils.luxThreshold);
    }

    @Override
    public void onBackPressed() {
        exitViewWithMessage("voiceit-failure", "User Canceled");
    }

    @Override
    protected void onStart() {
        super.onStart();
        requestHardwarePermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (mContinueVerifying) {
            exitViewWithMessage("voiceit-failure", "User Canceled");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraBinder != null) cameraBinder.shutdown();
        if (faceAnalyzer != null) faceAnalyzer.close();
    }
}
