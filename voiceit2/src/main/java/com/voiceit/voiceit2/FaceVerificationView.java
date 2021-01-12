    package com.voiceit.voiceit2;

    import android.Manifest;
    import android.app.Activity;
    import android.content.Context;
    import android.content.Intent;
    import android.content.SharedPreferences;
    import android.content.pm.ActivityInfo;
    import android.content.pm.PackageManager;
    import android.hardware.Sensor;
    import android.hardware.SensorEvent;
    import android.hardware.SensorEventListener;
    import android.hardware.SensorManager;
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
    import java.util.ArrayList;
    import java.util.List;

    import com.google.android.gms.vision.MultiProcessor;
    import com.google.android.gms.vision.Tracker;
    import com.google.android.gms.vision.face.Face;

    import com.loopj.android.http.JsonHttpResponseHandler;
    import cz.msebera.android.httpclient.Header;

    import org.json.JSONException;
    import org.json.JSONObject;

    public class FaceVerificationView extends AppCompatActivity implements SensorEventListener {

        private CameraSource mCameraSource = null;
        private CameraSourcePreview mPreview;
        private final File mPictureFile = Utils.getOutputMediaFile(".jpeg");
        private final Handler timingHandler = new Handler();

        private final String mTAG = "FaceVerificationView";
        private Context mContext;

        private RadiusOverlayView mOverlay;
        MediaRecorder recorder;

        private boolean playInstructionalVideo;

        private VoiceItAPI2 mVoiceIt2;
        private String mUserId = "";
        private boolean mDoLivenessCheck = false;
        private boolean mDoLivenessAudioCheck = false;
        private int mLivenessChallengeFailsAllowed;
        private int mLivenessChallengesNeeded;

        private final int mNeededFaceEnrollments = 1;

        private int mFailedAttempts = 0;
        private final int mMaxFailedAttempts = 3;
        private boolean mContinueVerifying = false;
        private String mContentLanguage ="en-US";

        private SensorManager sensorManager = null;
        private Sensor lightSensor;

        private boolean livenessSuccess = false;
        private String lcoId = "";
        private String uiLivenessInstruction;
        private List<String> lcoStrings = new ArrayList<String>();
        private List<String> lco= new ArrayList<String>();
        private float challengeTime;
        private final String SCREEN_TYPE = "face_verification";
        private final String mPhrase="";

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            setTheme(R.style.AppTheme);

            super.onCreate(savedInstanceState);

            // Grab data from parent activity
            Bundle bundle = getIntent().getExtras();
            if (bundle != null) {
                mVoiceIt2 = new VoiceItAPI2(bundle.getString("apiKey"), bundle.getString("apiToken"));
                mUserId = bundle.getString("userId");
                mDoLivenessCheck = bundle.getBoolean("doLivenessCheck");
                mDoLivenessAudioCheck = bundle.getBoolean("doLivenessAudioCheck");
                mContentLanguage = bundle.getString("contentLanguage");
                mLivenessChallengeFailsAllowed = bundle.getInt("livenessChallengeFailsAllowed");
                mLivenessChallengesNeeded = bundle.getInt("livenessChallengesNeeded");
                CameraSource.displayPreviewFrame = bundle.getBoolean("displayPreviewFrame");
            }

            // Hide action bar
            try {
                this.getSupportActionBar().hide();
            } catch (NullPointerException e) {
                Log.d(mTAG, "Cannot hide action bar");
            }

            // Set screen brightness to full
            if(!Utils.setBrightness(this, 255)){
                exitViewWithMessage("voiceit-failure","Hardware Permissions not granted");
            }

            // Set context
            mContext = this;
            // Set content view
            setContentView(R.layout.activity_face_verification_view);
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

            PackageManager pm = getPackageManager();
            if (pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_LIGHT)) {
                sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
                lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            }

            SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor prefEditor = sharedPref.edit();
            playInstructionalVideo = sharedPref.getBoolean("playInstructionalVideo", true);
            if (playInstructionalVideo && mDoLivenessCheck) {
                prefEditor.putBoolean("playInstructionalVideo", false);
                prefEditor.apply();

                Intent intent = new Intent(this, InstructionalVideoView.class);
                bundle = new Bundle();
                bundle.putBoolean("isVideo", false);
                intent.putExtras(bundle);
                this.startActivityForResult(intent, 0);
            }
        }

        private void handleError(){
            mOverlay.updateDisplayText("Error Getting Liveness Challenge");
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    exitViewWithMessage("voiceit-failure","Error Getting Liveness Challenge");
                }
            },   3000);
        }

        private void getLivenessData() {
            mVoiceIt2.getInitialLivenessData(mUserId, mContentLanguage, "verification", new JsonHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, final JSONObject response) {
                    Log.v("response", response.toString());
                    try {
                        lcoId = response.getString("lcoId");
                        livenessSuccess = response.getBoolean("success");
                        uiLivenessInstruction = response.getString("uiLivenessInstruction");
                        for(int i = 0; i < response.getJSONArray("lcoStrings").length(); i++ ){
                            lcoStrings.add(response.getJSONArray("lcoStrings").getString(i));
                        }
                        for(int i = 0; i < response.getJSONArray("lco").length(); i++ ){
                            lco.add(response.getJSONArray("lco").getString(i));
                        }
                        challengeTime = response.getInt("livenessChallengeTime");
                        beginVerification();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, Throwable throwable, final JSONObject errorResponse) {
                    handleError();
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                    super.onFailure(statusCode, headers, responseString, throwable);
                    handleError();
                }
            });
        }

        private void beginVerification(){
            mContinueVerifying = true;
            // Try to setup camera source
            mCameraSource = Utils.createCameraSource(this, new FaceTrackerFactory(this));
            // Try to start camera
            if (!Utils.startCameraSource(this, mCameraSource, mPreview)) {
                exitViewWithMessage("voiceit-failure", "Error starting camera");
            } else {
                mVoiceIt2.getAllFaceEnrollments(mUserId, new JsonHttpResponseHandler() {
                    @Override
                    public void onSuccess(int statusCode, Header[] headers, final JSONObject Response) {
                        try {
                            // Check If enough enrollments, otherwise return to previous activity
                            if (Response.getInt("count") < mNeededFaceEnrollments) {
                                mOverlay.updateDisplayText(getString(R.string.NOT_ENOUGH_ENROLLMENTS));
                                // Wait for ~2.5 seconds
                                timingHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        exitViewWithMessage("voiceit-failure", "Not enough enrollments");
                                    }
                                }, 2500);
                            } else {
                                if(mDoLivenessCheck){
                                    mOverlay.updateDisplayText(uiLivenessInstruction);
                                } else {
                                    mOverlay.updateDisplayText(getString(R.string.LOOK_INTO_CAM));
                                }
                                LivenessTracker.continueDetecting = true;
                            }
                        } catch (JSONException e) {
                            Log.d(mTAG, "JSON exception : " + e.toString());
                        }
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
                            timingHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    exitViewWithJSON("voiceit-failure", errorResponse);
                                }
                            }, 2000);
                        } else {
                            Log.e(mTAG, "No response from server");
                            mOverlay.updateDisplayTextAndLock(getString(R.string.CHECK_INTERNET));
                            // Wait for 2.0 seconds
                            timingHandler.postDelayed(new Runnable() {
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

        private void startVerificationFlow() {
            mOverlay.updateDisplayText(getString(R.string.WAIT));
            // get Live-nes Challenges and time
            if(mDoLivenessCheck) {
                getLivenessData();
            } else {
                beginVerification();
            }
        }

        private void setProgressCircleAngle(final Double startAngle, final Double endAngle) {
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mOverlay.setProgressCircleAngle(startAngle, endAngle);
                }
            });
        }

        private void setProgressCircleColor(final Integer color) {
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mOverlay.setProgressCircleColor(getResources().getColor(color));
                }
            });
        }


        /**
         * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
         * uses this factory to create face trackers as needed -- one for each individual.
         */
        private class FaceTrackerFactory implements MultiProcessor.Factory<Face> {

            private final Activity mActivity;
            private final int [] livenessChallengeOrder = {1, 2, 3};

            private FaceTrackerFactory(FaceVerificationView activity) {
                mActivity = activity;
                LivenessTracker.continueDetecting = false;
                LivenessTracker.livenessChallengesPassed = 0;
                LivenessTracker.livenessChallengeFails = 0;
                Utils.randomizeArrayOrder(livenessChallengeOrder);
            }

            @Override
            public Tracker<Face> create(Face face) {
                return new LivenessTracker(mVoiceIt2,mOverlay, mActivity, new FaceTrackerCallBackImpl(),
                        livenessChallengeOrder, mDoLivenessCheck, mDoLivenessAudioCheck, mPreview,
                        mPhrase, mLivenessChallengeFailsAllowed, mLivenessChallengesNeeded, uiLivenessInstruction, mUserId,
                        lcoStrings, lco, challengeTime, livenessSuccess, lcoId, mContentLanguage, SCREEN_TYPE, mCameraSource, recorder);
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

                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M) {
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
                startVerificationFlow();
            }
        }

        private void releaseMediaRecorder(){
            if(recorder!=null){
                recorder.reset();
                recorder.release();
                recorder = null;
                mCameraSource.getCameraInstance().lock();
            }
        }

        public void stopRecording() {
            if(recorder!=null && mDoLivenessCheck) {
                recorder.stop();
                releaseMediaRecorder();
                mCameraSource.getCameraInstance().lock();
            }
        }

        @Override
        protected void onStop() {
            super.onStop();
            stopRecording();
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);

            if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(mTAG,"Hardware Permissions not granted");
                exitViewWithMessage("voiceit-failure","User Canceled");
            } else {
                // Permissions granted, so continue with view
                startVerificationFlow();
            }
        }

        public void exitViewWithMessage(String action, String message) {
            Utils.setBrightness(this, Utils.oldBrightness);
            mContinueVerifying = false;
            timingHandler.removeCallbacksAndMessages(null);
            LivenessTracker.livenessTimer.removeCallbacksAndMessages(null);
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
            mContinueVerifying = false;
            timingHandler.removeCallbacksAndMessages(null);
            LivenessTracker.livenessTimer.removeCallbacksAndMessages(null);
            Intent intent = new Intent(action);
            intent.putExtra("Response", json.toString());
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
            finish();
        }

        @Override
        public final void onAccuracyChanged(Sensor sensor, int accuracy) {}

        @Override
        public final void onSensorChanged(SensorEvent event) {
            float lux = event.values[0];
            if(lux < Utils.luxThreshold) {
                mOverlay.setLowLightMode(true);
            } else {
                mOverlay.setLowLightMode(false);
            }
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            // Confirm permissions and start enrollment flow
            requestHardwarePermissions();
        }

        @Override
        public void onBackPressed() {
            exitViewWithMessage("voiceit-failure","User Canceled");
        }

        @Override
        protected void onStart() {
            super.onStart();
            if(!playInstructionalVideo || !mDoLivenessCheck) {
                // Confirm permissions and start enrollment flow
                requestHardwarePermissions();
            }
        }

        @Override
        protected void onResume() {
            super.onResume();
            if(sensorManager != null) {
                sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }

        @Override
        protected void onPause() {
            super.onPause();
            mPreview.stop();
            if(sensorManager != null) {
                sensorManager.unregisterListener(this);
            }
            if(mContinueVerifying) {
                exitViewWithMessage("voiceit-failure", "User Canceled");
            }
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            if (mCameraSource != null) {
                mCameraSource.release();
                mCameraSource=null;
            }
        }

        private void takePicture() {

            // Verify after taking picture
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

                    // With liveness checks enabled, a picture is taken before it is done
                    // and verify is called later
                    if(!mDoLivenessCheck) {
                        verifyUserFace();
                    } else {
                        // Continue liveness detection
                        LivenessTracker.continueDetecting = true;
                    }
                }
            };

            try {
                // Take picture of face
                mCameraSource.takePicture(null, mPictureCallback);
            } catch (Exception e) {
                Log.e(mTAG, "Camera exception : " + e.getMessage());
                exitViewWithMessage("voiceit-failure","Camera Error");
            }
        }

        private void failVerification(final JSONObject response) {

            // Continue showing live camera preview
            mOverlay.setPicture(null);

            mOverlay.setProgressCircleColor(getResources().getColor(R.color.failure));
            mOverlay.updateDisplayText(getString(R.string.VERIFY_FAIL));
            // Wait for ~1.5 seconds
            timingHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Report error to user
                        mOverlay.updateDisplayText(getString((getResources().getIdentifier(response.
                                getString("responseCode"), "string", getPackageName()))));
                    } catch (JSONException e) {
                        Log.d(mTAG,"JSON exception : " + e.toString());
                    }
                    // Wait for ~4.5 seconds
                    timingHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mFailedAttempts++;

                            // User failed too many times
                            if (mFailedAttempts >= mMaxFailedAttempts) {
                                mOverlay.updateDisplayText(getString(R.string.TOO_MANY_ATTEMPTS));
                                // Wait for ~2 seconds
                                timingHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        exitViewWithJSON("voiceit-failure", response);
                                    }
                                }, 2000);
                            } else if (mContinueVerifying) {
                                if(LivenessTracker.lookingAway) {
                                    mOverlay.updateDisplayText(getString(R.string.LOOK_INTO_CAM));
                                }
                                // Reset liveness check and try again
                                LivenessTracker.livenessChallengesPassed = 0;
                                LivenessTracker.livenessChallengeFails = 0;
                                LivenessTracker.continueDetecting = true;
                            }
                        }
                    }, 4500);
                }
            }, 1500);
        }

        private  void verifyUserFace() {
            mOverlay.setProgressCircleColor(getResources().getColor(R.color.progressCircle));
            mOverlay.setProgressCircleAngle(270, 359);

            mVoiceIt2.faceVerificationWithPhoto(mUserId, mPictureFile, new JsonHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, final JSONObject response) {
                    try {
                        // If successful verification
                        if (response.getString("responseCode").equals("SUCC")) {
                            LivenessTracker.continueDetecting = false;

                            mOverlay.setProgressCircleColor(getResources().getColor(R.color.success));
                            mOverlay.updateDisplayText(getString(R.string.VERIFY_SUCCESS));

                            // Wait for ~2 seconds
                            timingHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mPictureFile.deleteOnExit();
                                    exitViewWithJSON("voiceit-success", response);
                                }
                            }, 2000);
                        } else {
                            // If fail
                            mPictureFile.deleteOnExit();
                            failVerification(response);
                        }
                    } catch (JSONException e) {
                        Log.d(mTAG, "JSON exception : " + e.toString());
                    }
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, Throwable throwable, final JSONObject errorResponse) {
                    if (errorResponse != null) {
                        mPictureFile.deleteOnExit();
                        failVerification(errorResponse);
                    } else {
                        Log.e(mTAG, "No response from server");
                        mOverlay.updateDisplayTextAndLock(getString(R.string.CHECK_INTERNET));
                        // Wait for 2.0 seconds
                        timingHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                exitViewWithMessage("voiceit-failure", "No response from server");
                            }
                        }, 2000);
                    }
                }
            });
        }

        class FaceTrackerCallBackImpl implements LivenessTracker.viewCallBacks { // Implements callback methods defined in FaceTracker interface
            public void authMethodToCallBack() { verifyUserFace(); }
            public void takePictureCallBack() { takePicture(); }
        }
    }
