package com.voiceit.voiceit3;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public class VoiceEnrollmentView extends AppCompatActivity {

    private final String mTAG = "VoiceEnrollmentView";
    private Context mContext;

    private RadiusOverlayView mOverlay;
    private MediaRecorder mMediaRecorder = null;
    private final Handler timingHandler = new Handler();
    private int voiceitThemeColor = 0;

    private VoiceItAPI3 mVoiceIt3;
    private String mUserId = "";
    private String mContentLanguage = "";
    private String mPhrase = "";

    private int mEnrollmentCount = 0;
    private final int mNeededEnrollments = 3;
    private int mFailedAttempts = 0;
    private final int mMaxFailedAttempts = 3;
    private boolean mContinueEnrolling = false;

    private boolean displayWaveform = true;
    private final long REFRESH_WAVEFORM_INTERVAL_MS = 30;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            mVoiceIt3 = new VoiceItAPI3(bundle.getString("apiKey"), bundle.getString("apiToken"));
            mUserId = bundle.getString("userId");
            mVoiceIt3.setNotificationURL(bundle.getString("notificationURL"));
            mContentLanguage = bundle.getString("contentLanguage");
            mPhrase = bundle.getString("phrase");
            this.voiceitThemeColor = bundle.getInt("voiceitThemeColor");
            if (this.voiceitThemeColor == 0) {
                this.voiceitThemeColor = getResources().getColor(R.color.waveform);
            }
        }

        try {
            getSupportActionBar().hide();
        } catch (NullPointerException e) {
            Log.d(mTAG, "Cannot hide action bar");
        }

        mContext = this;
        setContentView(R.layout.activity_voice_enrollment_view);
        mOverlay = findViewById(R.id.overlay);

        ((android.widget.TextView) findViewById(R.id.toolbarTitle)).setText("Enrolling Voice");
        findViewById(R.id.cancelButton).setOnClickListener(
                v -> exitViewWithMessage("voiceit-failure", "User cancelled"));

        mOverlay.setWaveformColor(this.voiceitThemeColor);

        if (Build.VERSION.SDK_INT >= 18) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        } else {
            setRequestedOrientation(Utils.lockOrientationCode(
                    getWindowManager().getDefaultDisplay().getRotation()));
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void startEnrollmentFlow() {
        mContinueEnrolling = true;
        mVoiceIt3.deleteAllEnrollments(mUserId, new Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                recordVoice();
            }

            @Override
            public void onFailure(int statusCode, JSONObject errorBody, Throwable error) {
                handleNetworkFailure(errorBody);
            }
        });
    }

    private void requestHardwarePermissions() {
        final int ASK_MULTIPLE_PERMISSION_REQUEST_CODE = 1;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    ASK_MULTIPLE_PERMISSION_REQUEST_CODE);
        } else {
            startEnrollmentFlow();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(mTAG, "Hardware Permissions not granted");
            exitViewWithMessage("voiceit-failure", "Hardware Permissions not granted");
        } else {
            startEnrollmentFlow();
        }
    }

    private void exitViewWithMessage(String action, String message) {
        mContinueEnrolling = false;
        stopRecording();
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
        stopRecording();
        timingHandler.removeCallbacksAndMessages(null);
        Intent intent = new Intent(action);
        intent.putExtra("Response", json.toString());
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        finish();
        overridePendingTransition(0, 0);
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
    protected void onPause() {
        super.onPause();
        if (mContinueEnrolling) {
            exitViewWithMessage("voiceit-failure", "User Canceled");
        }
    }

    private void stopRecording() {
        if (mMediaRecorder != null) {
            try {
                mMediaRecorder.stop();
            } catch (Exception e) {
                Log.d(mTAG, "Error trying to stop MediaRecorder");
            }
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    private void failEnrollment(final JSONObject response) {
        mOverlay.setProgressCircleColor(getResources().getColor(R.color.failure));
        mOverlay.updateDisplayText("ENROLL_FAIL");
        timingHandler.postDelayed(() -> {
            try {
                if (response.getString("responseCode").equals("PDNM")) {
                    mOverlay.updateDisplayText(response.getString("responseCode"), mPhrase);
                } else {
                    mOverlay.updateDisplayText(response.getString("responseCode"));
                }
            } catch (JSONException e) {
                Log.d(mTAG, "JSON exception: " + e);
            }
            timingHandler.postDelayed(() -> {
                mFailedAttempts++;
                if (mFailedAttempts > mMaxFailedAttempts) {
                    mOverlay.updateDisplayText("TOO_MANY_ATTEMPTS");
                    timingHandler.postDelayed(
                            () -> exitViewWithJSON("voiceit-failure", response),
                            2000);
                } else if (mContinueEnrolling) {
                    recordVoice();
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

    private long redrawWaveform() {
        final long currentTime = System.currentTimeMillis();
        runOnUiThread(() -> {
            if (mMediaRecorder != null) {
                mOverlay.setWaveformMaxAmplitude(mMediaRecorder.getMaxAmplitude());
            }
        });
        return System.currentTimeMillis() - currentTime;
    }

    private void recordVoice() {
        if (!mContinueEnrolling) return;

        mOverlay.updateDisplayText("ENROLL_" + (mEnrollmentCount + 1) + "_PHRASE", mPhrase);
        try {
            final File audioFile = Utils.getOutputMediaFile(".wav");
            if (audioFile == null) {
                exitViewWithMessage("voiceit-failure", "Creating audio file failed");
                return;
            }

            mMediaRecorder = new MediaRecorder();
            Utils.startMediaRecorder(mMediaRecorder, audioFile);

            displayWaveform = true;
            new Thread(() -> {
                while (displayWaveform) {
                    try {
                        Thread.sleep(Math.max(0, REFRESH_WAVEFORM_INTERVAL_MS - redrawWaveform()));
                    } catch (Exception e) {
                        Log.d(mTAG, "MediaRecorder getMaxAmplitude Exception: " + e.getMessage());
                    }
                }
            }).start();

            timingHandler.postDelayed(() -> {
                displayWaveform = false;
                if (!mContinueEnrolling) return;

                stopRecording();
                mOverlay.setWaveformMaxAmplitude(1);
                mOverlay.updateDisplayText("WAIT");

                mVoiceIt3.createVoiceEnrollment(mUserId, mContentLanguage, mPhrase, audioFile,
                        new Callback() {
                            @Override
                            public void onSuccess(JSONObject response) {
                                try {
                                    if (response.getString("responseCode").equals("SUCC")) {
                                        mOverlay.setProgressCircleColor(
                                                getResources().getColor(R.color.success));
                                        mOverlay.updateDisplayText("ENROLL_SUCCESS");
                                        timingHandler.postDelayed(() -> {
                                            audioFile.deleteOnExit();
                                            mEnrollmentCount++;
                                            if (mEnrollmentCount == mNeededEnrollments) {
                                                mOverlay.updateDisplayText("ALL_ENROLL_SUCCESS");
                                                timingHandler.postDelayed(
                                                        () -> exitViewWithJSON("voiceit-success", response),
                                                        2500);
                                            } else {
                                                recordVoice();
                                            }
                                        }, 2000);
                                    } else {
                                        audioFile.deleteOnExit();
                                        failEnrollment(response);
                                    }
                                } catch (JSONException e) {
                                    Log.d(mTAG, "JSON exception: " + e);
                                }
                            }

                            @Override
                            public void onFailure(int statusCode, JSONObject errorBody, Throwable error) {
                                if (errorBody != null) {
                                    audioFile.deleteOnExit();
                                    failEnrollment(errorBody);
                                } else {
                                    Log.e(mTAG, "No response from server");
                                    mOverlay.updateDisplayTextAndLock("CHECK_INTERNET");
                                    timingHandler.postDelayed(() -> exitViewWithMessage(
                                            "voiceit-failure", "No response from server"), 2000);
                                }
                            }
                        });
            }, 4800);
        } catch (Exception ex) {
            Log.d(mTAG, "Recording Error: " + ex.getMessage());
            exitViewWithMessage("voiceit-failure", "Recording Error");
        }
    }
}
