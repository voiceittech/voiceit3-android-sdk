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

public class VideoEnrollmentView extends AppCompatActivity implements SensorEventListener {

    private final String mTAG = "VideoEnrollmentView";

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
    private int mEnrollmentCount = 0;
    private final int mNeededEnrollments = 3;
    private int mFailedAttempts = 0;
    private final int mMaxFailedAttempts = 3;
    private boolean mContinueEnrolling = false;

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
        setContentView(R.layout.activity_video_enrollment_view);
        previewView = findViewById(R.id.camera_preview);
        mOverlay = findViewById(R.id.overlay);

        ((android.widget.TextView) findViewById(R.id.toolbarTitle)).setText("Enrolling Video");
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

    private void startEnrollmentFlow() {
        mContinueEnrolling = true;
        continueDetecting = false;

        if (faceAnalyzer == null) faceAnalyzer = new MLKitFaceAnalyzer(this::onFacesDetected);
        if (cameraBinder == null) cameraBinder = new CameraXBinder(this, previewView);

        cameraBinder.bindForFace(faceAnalyzer, () -> {
            if (mEnrollmentCount < mNeededEnrollments) {
                mOverlay.updateDisplayText("VIDEO_LOOK_INTO_CAM", mPhrase);
                continueDetecting = true;
            } else {
                mVoiceIt3.deleteAllEnrollments(mUserId, new Callback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        mOverlay.updateDisplayText("VIDEO_LOOK_INTO_CAM", mPhrase);
                        continueDetecting = true;
                    }

                    @Override
                    public void onFailure(int statusCode, JSONObject errorBody, Throwable error) {
                        handleNetworkFailure(errorBody);
                    }
                });
            }
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
                // Picture done — switch CameraX to video capture mode for the
                // 5-second recording, then enroll.
                cameraBinder.bindForVideo(VideoEnrollmentView.this::recordVideoAndEnroll);
            }

            @Override
            public void onError(Throwable error) {
                Log.e(mTAG, "Camera capture exception", error);
                exitViewWithMessage("voiceit-failure", "Camera Error");
            }
        });
    }

    private void recordVideoAndEnroll() {
        if (!mContinueEnrolling) return;

        mOverlay.updateDisplayText("ENROLL_" + (mEnrollmentCount + 1) + "_PHRASE", mPhrase);
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
                    submitEnrollment(audioFile, audioVideoFile);
                });
            }

            @Override
            public void onError(Throwable error) {
                Log.e(mTAG, "Video recording error", error);
                exitViewWithMessage("voiceit-failure", "Recording Error");
            }
        });

        // Stop after 4.8s so the resulting clip is comfortably under 5s.
        timingHandler.postDelayed(() -> {
            if (mContinueEnrolling && cameraBinder != null) cameraBinder.stopRecording();
        }, 4800);
    }

    private void submitEnrollment(final File audioFile, final File audioVideoFile) {
        mVoiceIt3.createVideoEnrollment(mUserId, mContentLanguage, mPhrase,
                audioFile, mPictureFile, new Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    if (response.getString("responseCode").equals("SUCC")) {
                        mOverlay.setProgressCircleColor(getResources().getColor(R.color.success));
                        mOverlay.updateDisplayText("ENROLL_SUCCESS");
                        timingHandler.postDelayed(() -> {
                            audioFile.deleteOnExit();
                            mPictureFile.deleteOnExit();
                            audioVideoFile.deleteOnExit();
                            mEnrollmentCount++;
                            if (mEnrollmentCount == mNeededEnrollments) {
                                mOverlay.updateDisplayText("ALL_ENROLL_SUCCESS");
                                timingHandler.postDelayed(
                                        () -> exitViewWithJSON("voiceit-success", response),
                                        2500);
                            } else {
                                if (lookingAway) {
                                    mOverlay.updateDisplayText("VIDEO_LOOK_INTO_CAM", mPhrase);
                                }
                                mOverlay.setPicture(null);
                                startEnrollmentFlow();
                            }
                        }, 2000);
                    } else {
                        audioFile.deleteOnExit();
                        mPictureFile.deleteOnExit();
                        audioVideoFile.deleteOnExit();
                        failEnrollment(response);
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
                    failEnrollment(errorBody);
                } else {
                    Log.e(mTAG, "No response from server");
                    mOverlay.updateDisplayTextAndLock("CHECK_INTERNET");
                    timingHandler.postDelayed(() -> exitViewWithMessage(
                            "voiceit-failure", "No response from server"), 2000);
                }
            }
        });
    }

    private void failEnrollment(final JSONObject response) {
        mOverlay.setPicture(null);
        mOverlay.setProgressCircleColor(getResources().getColor(R.color.failure));
        mOverlay.updateDisplayText("ENROLL_FAIL");
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
                } else if (mContinueEnrolling) {
                    if (lookingAway) {
                        mOverlay.updateDisplayText("VIDEO_LOOK_INTO_CAM", mPhrase);
                    }
                    startEnrollmentFlow();
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

    // ---- Permissions / lifecycle ----

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
            startEnrollmentFlow();
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
            startEnrollmentFlow();
        }
    }

    private void exitViewWithMessage(String action, String message) {
        mContinueEnrolling = false;
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
        mContinueEnrolling = false;
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
        if (mContinueEnrolling) {
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
