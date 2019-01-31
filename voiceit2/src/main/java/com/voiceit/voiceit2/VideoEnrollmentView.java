package com.voiceit.voiceit2;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.loopj.android.http.JsonHttpResponseHandler;
import cz.msebera.android.httpclient.Header;

import org.json.JSONException;
import org.json.JSONObject;

public class VideoEnrollmentView extends AppCompatActivity {

    private CameraSource mCameraSource = null;
    private CameraSourcePreview mPreview;
    private final File mPictureFile = Utils.getOutputMediaFile(".jpeg");
    private MediaRecorder mMediaRecorder = null;
    private final Handler handler = new Handler();

    private final String mTAG = "VideoEnrollmentView";
    private Context mContext;

    private RadiusOverlayView mOverlay;

    private VoiceItAPI2 mVoiceIt2;
    private String mUserId = "";
    private String mContentLanguage = "";
    private String mPhrase = "";

    private int mEnrollmentCount = 0;
    private final int mNeededEnrollments = 3;
    private int mFailedAttempts = 0;
    private final int mMaxFailedAttempts = 3;
    private boolean mContinueEnrolling = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);

        super.onCreate(savedInstanceState);

        // Grab data from parent activity
        Bundle bundle = getIntent().getExtras();
        if(bundle != null) {
            mVoiceIt2 = new VoiceItAPI2(bundle.getString("apiKey"), bundle.getString("apiToken"));
            mUserId = bundle.getString("userId");
            mContentLanguage = bundle.getString("contentLanguage");
            mPhrase = bundle.getString("phrase");
            CameraSource.displayPreviewFrame = bundle.getBoolean("displayPreviewFrame");
        }

        // Hide action bar
        try {
            this.getSupportActionBar().hide();
        } catch (NullPointerException e) {
            Log.d(mTAG,"Cannot hide action bar");
        }

        // Set screen brightness to full
        if(!Utils.setBrightness(this, 255)){
            exitViewWithMessage("voiceit-failure","Hardware Permissions not granted");
        }

        // Set context
        mContext = this;
        // Set content view
        setContentView(R.layout.activity_video_enrollment_view);
        mPreview = findViewById(R.id.camera_preview);

        // Text output on mOverlay
        mOverlay = findViewById(R.id.overlay);
        CameraSource.mOverlay = mOverlay;

        // Lock orientation
        if (Build.VERSION.SDK_INT >= 18) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        } else {
            setRequestedOrientation(Utils.lockOrientationCode(getWindowManager().getDefaultDisplay().getRotation()));
        }
    }

    private void startEnrollmentFlow() {
        mContinueEnrolling = true;
        // Try to setup camera source
        mCameraSource = Utils.createCameraSource(this, new FaceTrackerFactory(this));
        // Try to start camera
        if(!Utils.startCameraSource(this, mCameraSource, mPreview)){
            exitViewWithMessage("voiceit-failure","Error starting camera");
        } else {
            // Delete enrollments and re-enroll
            mVoiceIt2.deleteAllVideoEnrollments(mUserId, new JsonHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, JSONObject Response) {
                    mOverlay.updateDisplayText(getString(R.string.LOOK_INTO_CAM));
                    // Start tracking faces
                    FaceTracker.continueDetecting = true;
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, Throwable throwable, final JSONObject errorResponse) {
                    if (errorResponse != null) {
                        try {
                            // Report error to user
                            mOverlay.updateDisplayText(getString((getResources().getIdentifier(errorResponse.
                                    getString("responseCode"), "string", getPackageName()))));
                        } catch (JSONException e) {
                            Log.d(mTAG, "JSON exception : " + e.toString());
                        }
                        // Wait for 2.0 seconds
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                exitViewWithJSON("voiceit-failure", errorResponse);
                            }
                        }, 2000);
                    } else {
                        Log.e(mTAG, "No response from server");
                        mOverlay.updateDisplayTextAndLock(getString(R.string.CHECK_INTERNET));
                        // Wait for 2.0 seconds
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                exitViewWithMessage("voiceit-failure", "No response from server");
                            }
                        }, 2000);
                    }
                }
            });
        }
    }

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class FaceTrackerFactory implements MultiProcessor.Factory<Face> {

        private final Activity mActivity;

        private FaceTrackerFactory(VideoEnrollmentView activity) {
            mActivity = activity;
            FaceTracker.continueDetecting = false;
            FaceTracker.livenessChallengesPassed = 0;
        }

        @Override
        public Tracker<Face> create(Face face) {
            return new FaceTracker(mOverlay, mActivity, new FaceTrackerCallBackImpl(), new int[]{}, false, 0, 0);
        }
    }

    private void requestHardwarePermissions() {
        final int PERMISSIONS_REQUEST_RECORD_AUDIO = 0;
        final int PERMISSIONS_REQUEST_CAMERA = 1;
        final int ASK_MULTIPLE_PERMISSION_REQUEST_CODE = 2;
        // MY_PERMISSIONS_REQUEST_* is an app-defined int constant. The callback method gets the
        // result of the request.
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.N) {
                requestPermissions(new String[]{
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.CAMERA},
                        ASK_MULTIPLE_PERMISSION_REQUEST_CODE);
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                            PERMISSIONS_REQUEST_RECORD_AUDIO);
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                            PERMISSIONS_REQUEST_CAMERA);
                }
            }
        } else {
            // Permissions granted, so continue with view
            startEnrollmentFlow();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(mTAG,"Hardware Permissions not granted");
            exitViewWithMessage("voiceit-failure", "Hardware Permissions not granted");
        } else {
            // Permissions granted, so continue with view
            startEnrollmentFlow();
        }
    }

    private void exitViewWithMessage(String action, String message) {
        Utils.setBrightness(this, Utils.oldBrightness);
        mContinueEnrolling = false;
        stopRecording();
        handler.removeCallbacksAndMessages(null);
        Intent intent = new Intent(action);
        JSONObject json = new JSONObject();
        try {
            json.put("message", message);
        } catch(JSONException e) {
            Log.d(mTAG,"JSON Exception : " + e.getMessage());
        }
        intent.putExtra("Response", json.toString());
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        finish();
    }

    private void exitViewWithJSON(String action, JSONObject json) {
        Utils.setBrightness(this, Utils.oldBrightness);
        mContinueEnrolling = false;
        stopRecording();
        handler.removeCallbacksAndMessages(null);
        Intent intent = new Intent(action);
        intent.putExtra("Response", json.toString());
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        exitViewWithMessage("voiceit-failure", "User Canceled");
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Confirm permissions and start enrollment flow
        requestHardwarePermissions();
    }
    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
        if(mContinueEnrolling) {
            exitViewWithMessage("voiceit-failure", "User Canceled");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
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

    private void takePicture() {

        // Enroll after taking picture
        final CameraSource.PictureCallback mPictureCallback = new CameraSource.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data) {
                // Check file
                if (mPictureFile == null) {
                    Log.d(mTAG, "Error creating media file, check storage permissions");
                    return;
                }
                // Write picture to file
                try {
                    FileOutputStream fos = new FileOutputStream(mPictureFile);
                    fos.write(data);
                    fos.close();
                } catch (FileNotFoundException e) {
                    Log.d(mTAG, "File not found: " + e.getMessage());
                } catch (IOException e) {
                    Log.d(mTAG, "Error accessing file: " + e.getMessage());
                }

                // Enroll with picture taken
                enrollUser();
            }
        };

        try {
            // Take picture of face
            mCameraSource.takePicture(null, mPictureCallback);
        } catch (Exception e) {
            Log.d(mTAG, "Camera exception : " + e.getMessage());
            exitViewWithMessage("voiceit-failure", "Camera Error");
        }
    }

    private void failEnrollment(final JSONObject response) {

        // Continue showing live camera preview
        mOverlay.setPicture(null);

        mOverlay.setProgressCircleColor(getResources().getColor(R.color.failure));
        mOverlay.updateDisplayText(getString(R.string.ENROLL_FAIL));

        // Wait for ~1.5 seconds
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    // Report error to user
                    if (response.getString("responseCode").equals("PDNM")) {
                        mOverlay.updateDisplayText(getString((getResources().getIdentifier(response.
                                getString("responseCode"), "string", getPackageName())), mPhrase));
                    } else {
                        mOverlay.updateDisplayText(getString((getResources().getIdentifier(response.
                                getString("responseCode"), "string", getPackageName()))));
                    }
                } catch (JSONException e) {
                    Log.d(mTAG,"JSON exception : " + e.toString());
                }
                // Wait for ~4.5 seconds
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mFailedAttempts++;

                        // User failed too many times
                        if(mFailedAttempts >= mMaxFailedAttempts) {
                            mOverlay.updateDisplayText(getString(R.string.TOO_MANY_ATTEMPTS));
                            // Wait for ~2 seconds
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    exitViewWithJSON("voiceit-failure", response);
                                }
                            }, 2000);
                        } else if (mContinueEnrolling) {
                            if(FaceTracker.lookingAway) {
                                mOverlay.updateDisplayText(getString(R.string.LOOK_INTO_CAM));
                            }
                            // Try again
                            FaceTracker.continueDetecting = true;
                        }
                    }
                }, 4500);
            }
        }, 1500);
    }

    private void enrollUser() {
        if(mContinueEnrolling) {
            // Display enrollment mPhrase to user
            mOverlay.updateDisplayText(getString(getResources().getIdentifier("ENROLL_" + (mEnrollmentCount + 1) + "_PHRASE", "string", getPackageName()), mPhrase));
            try {
                // Create file for audio
                final File audioFile = Utils.getOutputMediaFile(".wav");
                if (audioFile == null) {
                    exitViewWithMessage("voiceit-failure", "Creating audio file failed");
                }

                // Setup device and capture audio
                mMediaRecorder = new MediaRecorder();
                Utils.startMediaRecorder(mMediaRecorder, audioFile);

                mOverlay.setProgressCircleColor(getResources().getColor(R.color.progressCircle));
                mOverlay.startDrawingProgressCircle();
                // Record for ~5 seconds, then send enrollment data
                // 4800 to make sure recording is not over 5 seconds
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mContinueEnrolling) {
                            stopRecording();

                            mOverlay.updateDisplayText(getString(R.string.WAIT));
                            mVoiceIt2.createVideoEnrollment(mUserId, mContentLanguage, mPhrase, audioFile, mPictureFile, new JsonHttpResponseHandler() {
                                @Override
                                public void onSuccess(int statusCode, Header[] headers, final JSONObject response) {
                                    try {
                                         if (response.getString("responseCode").equals("SUCC")) {
                                            mOverlay.setProgressCircleColor(getResources().getColor(R.color.success));
                                            mOverlay.updateDisplayText(getString(R.string.ENROLL_SUCCESS));

                                            // Wait for ~2 seconds
                                            handler.postDelayed(new Runnable() {
                                                @Override
                                                public void run() {
                                                    audioFile.deleteOnExit();
                                                    mPictureFile.deleteOnExit();
                                                    mEnrollmentCount++;

                                                    if (mEnrollmentCount == mNeededEnrollments) {
                                                        mOverlay.updateDisplayText(getString(R.string.ALL_ENROLL_SUCCESS));
                                                        // Wait for ~2.5 seconds
                                                        handler.postDelayed(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                exitViewWithJSON("voiceit-success", response);
                                                            }
                                                        }, 2500);
                                                    } else {
                                                        if (FaceTracker.lookingAway) {
                                                            mOverlay.updateDisplayText(getString(R.string.LOOK_INTO_CAM));
                                                        }
                                                        // Continue showing live camera preview
                                                        mOverlay.setPicture(null);
                                                        // Continue Enrolling
                                                        FaceTracker.continueDetecting = true;
                                                    }
                                                }
                                            }, 2000);

                                            // Fail
                                        } else {
                                            audioFile.deleteOnExit();
                                            mPictureFile.deleteOnExit();
                                            failEnrollment(response);
                                        }
                                    } catch (JSONException e) {
                                        Log.d(mTAG, "JSON Error: " + e.getMessage());
                                    }
                                }

                                @Override
                                public void onFailure(int statusCode, Header[] headers, Throwable throwable, final JSONObject errorResponse) {
                                    if (errorResponse != null) {
                                        Log.d(mTAG, "JSONResult : " + errorResponse.toString());
                                        audioFile.deleteOnExit();
                                        mPictureFile.deleteOnExit();
                                        failEnrollment(errorResponse);
                                    } else {
                                        Log.e(mTAG, "No response from server");
                                        mOverlay.updateDisplayTextAndLock(getString(R.string.CHECK_INTERNET));
                                        // Wait for 2.0 seconds
                                        handler.postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                exitViewWithMessage("voiceit-failure", "No response from server");
                                            }
                                        }, 2000);
                                    }
                                }
                            });

                        }
                    }
                }, 4800);

            } catch (Exception ex) {
                Log.d(mTAG, "Recording Error:" + ex.getMessage());
                exitViewWithMessage("voiceit-failure", "Recording Error");
            }
        }
    }

    class FaceTrackerCallBackImpl implements FaceTracker.viewCallBacks { // Implements callback methods defined in FaceTracker interface
        public void authMethodToCallBack() { enrollUser(); }
        public void takePictureCallBack() { takePicture(); }
    }

}