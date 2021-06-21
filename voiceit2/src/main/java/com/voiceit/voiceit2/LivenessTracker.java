package com.voiceit.voiceit2;

import android.app.Activity;
import android.content.Intent;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Handler;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;
import android.media.MediaPlayer;

import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.loopj.android.http.JsonHttpResponseHandler;


import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.List;

import cz.msebera.android.httpclient.Header;

/**
 * Face tracker for each detected individual.
 */
class LivenessTracker extends Tracker<Face> {

    private final Activity mActivity;
    private final RadiusOverlayView mOverlay;
    private final viewCallBacks mCallbacks;

    private final String mTAG = "LivenessTracker";

    private final int [] mLivenessChallengeOrder;
    private MediaPlayer mediaPlayer = new MediaPlayer();

    private final boolean mDoLivenessCheck;
    private final boolean mDoLivenessAudioCheck;
    private final int mLivenessChallengeFailsAllowed;
    private final int mLivenessChallengesNeeded;
    private static int challengeIndex = 0;

    static int livenessChallengesPassed = 0;
    static int livenessChallengeFails = 0;
    private static final double faceTurnedValue = 18.0;
    private boolean mDisplayingChallenge = false;
    private boolean mDisplayingChallengeOutcome = false;
    private boolean mTimingLiveness = false;
    private boolean mLivenessSuccess = false;
    private List<String> mLcoStrings;
    private List<String> mLco;
    private String mLcoId;
    private String mUiLivenessInstruction;
    private float mChallengeTime;
    private String contentLanguage;
    private String mScreenType;
    private String mUserId;
    private String mPhrase;
    private boolean isCameraPrepared;
    private CameraSource mCameraSource;
    private MediaRecorder mRecorder;
    static boolean continueDetecting = true;
    static boolean lookingAway = false;
    static final Handler livenessTimer = new Handler();
    private CameraSourcePreview mPreview;
    private VoiceItAPI2 mVoiceItAPI2;
    File file;

    LivenessTracker(VoiceItAPI2 mVoiceIt2, RadiusOverlayView overlay, Activity activity, viewCallBacks callbacks, int[] livenessChallengeOrder,
                    boolean doLivenessCheck, boolean doLivenessAudioCheck, CameraSourcePreview preview, String phrase,
                    int livenessChallengeFailsAllowed, int livenessChallengesNeeded, String livenessInstruction,
                    String userId, List<String> lcoStrings, List<String> lco, float challengeTime, boolean livenessSuccess,
                    String lcoId, String contentLanguage, String screenType, CameraSource cameraSource, MediaRecorder recorder) {
        mOverlay = overlay;
        mActivity = activity;
        mCallbacks = callbacks;
        mLivenessChallengeOrder = livenessChallengeOrder;
        mDoLivenessCheck = doLivenessCheck;
        mDoLivenessAudioCheck = doLivenessAudioCheck;
        mLivenessChallengeFailsAllowed = livenessChallengeFailsAllowed;
        mLivenessChallengesNeeded = livenessChallengesNeeded;
        mLivenessSuccess = livenessSuccess;
        mChallengeTime = challengeTime;
        mLco = lco;
        mLcoStrings = lcoStrings;
        mLcoId = lcoId;
        mUiLivenessInstruction = livenessInstruction;
        contentLanguage = contentLanguage;
        mScreenType = screenType;
        mPhrase = phrase;
        mCameraSource = cameraSource;
        mRecorder = recorder;
        mPreview = preview;
        mUserId = userId;
        mVoiceItAPI2 = mVoiceIt2;
    }

    private void createFile() throws IOException {
        file = new File(mActivity.getFilesDir() + "/" + File.separator + "video.mp4");
        file.createNewFile();
    }

    private void setProgressCircleColor(final Integer color) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mOverlay.setProgressCircleColor(mActivity.getResources().getColor(color));
            }
        });
    }

    private void setProgressCircleAngle(final Double startAngle, final Double endAngle) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mOverlay.setProgressCircleAngle(startAngle, endAngle);
            }
        });
    }

    private void updateDisplayText(final String text, final boolean lock) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(lock) {
                    mOverlay.updateDisplayTextAndLock(text);
                } else {
                    mOverlay.updateDisplayText(text);
                }
            }
        });
    }
    private void playLivenessPrompt(final String livenessPrompt) {
        if(mDoLivenessAudioCheck) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                    if (contentLanguage == "es-ES") {
                        int resId = mActivity.getApplicationContext().getResources().getIdentifier(
                                livenessPrompt + "_es",
                                "raw",
                                mActivity.getApplicationContext().getPackageName()
                        );
                        mediaPlayer = MediaPlayer.create(mActivity.getApplicationContext(), resId);
                        mediaPlayer.start();
                    } else {
                        int resId = mActivity.getApplicationContext().getResources().getIdentifier(
                                livenessPrompt,
                                "raw",
                                mActivity.getApplicationContext().getPackageName()
                        );
                        mediaPlayer = MediaPlayer.create(mActivity.getApplicationContext(), resId);
                        mediaPlayer.start();
                    }
                }
            });
        }
    }

    /**
     * Update the position/characteristics of the face.
     */
    @Override
    public void onUpdate(FaceDetector.Detections<Face> detectionResults, final Face face) {
        if(mDoLivenessCheck) {
            isCameraPrepared = false;
            if (prepareForVideoRecording() && !isCameraPrepared) {
                isCameraPrepared = true;
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                performLivenessTest();
                            }
                        }, 3000);
                    }
                });
            }
        } else if(!mDoLivenessCheck) {
            if (LivenessTracker.continueDetecting) {
                final int numFaces = detectionResults.getDetectedItems().size();

                if (numFaces == 1 && mOverlay.insidePortraitCircle(mActivity, face)) {
                    LivenessTracker.lookingAway = false;

                    // Success or skip liveness detection
                    if (LivenessTracker.livenessChallengesPassed == mLivenessChallengesNeeded || !mDoLivenessCheck) {
                        LivenessTracker.continueDetecting = false;
                        LivenessTracker.livenessChallengeFails = 0;

                        mOverlay.updateDisplayText(mActivity.getString(R.string.WAIT));
                        mActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new Handler().postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        // Quick pause, .75 seconds, after all challenges are done
                                        setProgressCircleAngle(270.0, 0.0);
                                        setProgressCircleColor(R.color.progressCircle);
                                        // Display picture of user at the end of process
                                        mOverlay.displayPicture = true;

                                        if (!mDoLivenessCheck) {
                                            // Take picture of user in preview to show
                                            CameraSource.captureNextPreviewFrame = true;

                                            // Since picture was not taken during any liveness checks,
                                            // take one now then auth
                                            mCallbacks.takePictureCallBack();
                                        } else {
                                            // verify or enroll callback
                                            mCallbacks.authMethodToCallBack();
                                        }
                                    }
                                }, 750);
                            }
                        });

                    } else if (LivenessTracker.livenessChallengesPassed < mLivenessChallengesNeeded) {

                        // Display a liveness challenge to the user

                        // Start a timer for current liveness challenge
                        if (!mTimingLiveness && LivenessTracker.continueDetecting) {
                            mTimingLiveness = true;
                            mActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // Fail after 3 seconds
                                    livenessTimer.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            // Fail if user didn't pass a liveness check in time
                                        }
                                    }, 3000);
                                }
                            });
                        }
                    }

                } else if (numFaces > 1) {
                    Log.d(mTAG, "Too many faces present");
                    updateDisplayText(mActivity.getString(R.string.TOO_MANY_FACES), false);
                    setProgressCircleAngle(270.0, 0.0);
                }
            }
        }
    }

    private void beginChallenge() {
        mOverlay.updateDisplayText(mLcoStrings.get(0));
        illuminateCircles(mLco.get(0).toLowerCase());
        playLivenessPrompt(mLco.get(0).toLowerCase());
        long time = (long) mChallengeTime / mLco.size();
        for (int i = 1; i < mLco.size(); i++) {
            final String challenge = mLcoStrings.get(i);
            final int finalI = i;
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    playLivenessPrompt(mLco.get(finalI).toLowerCase());
                    mOverlay.updateDisplayText(challenge);
                    illuminateCircles(mLco.get(finalI).toLowerCase());
                }
            }, time * 1000);
        }
    }

    private boolean prepareForVideoRecording(){
        try {
            createFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCameraSource.getCameraInstance().unlock();
        mRecorder = new MediaRecorder();
        mRecorder.setCamera(mCameraSource.getCameraInstance());
        mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_480P));
        mRecorder.setOutputFile(mActivity.getFilesDir() + "/" + File.separator + "video.mp4");
        mRecorder.setPreviewDisplay(mPreview.getSurfaceHolder().getSurface());
        mRecorder.setOrientationHint(270);
        mRecorder.setVideoSize(640,480);
        try{
            mRecorder.prepare();
        }catch (IOException e){
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    private void releaseMediaRecorder(){
        if(mRecorder!=null){
            mRecorder.reset();
            mRecorder.release();
            mRecorder = null;
            mCameraSource.getCameraInstance().lock();
        }
    }

    public void startRecording() {
        try {
            mRecorder.start();
        } catch (IllegalStateException e){
            e.printStackTrace();
        }
    }

    public void stopRecording() {
        try {
            mRecorder.stop();
            releaseMediaRecorder();
            mCameraSource.getCameraInstance().lock();
        }
        catch (IllegalStateException e){
            e.printStackTrace();
        }
    }

    private void performLivenessTest() {
        //start recording
        try {
            startRecording();
        }catch (RuntimeException e){
            e.printStackTrace();
        }

        if(mScreenType.equals("face_verification")) {
            //begin challenge after 2 seconds for camera warm up
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    beginChallenge();

                    //stop recording after challenge time
                    long stop_time = (long)mChallengeTime;
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            stopRecording();
                            mOverlay.updateDisplayText("Please Wait...");
                            setProgressCircleAngle(0.0,360.0);
                            sendVideoFile();
                        }
                    }, stop_time*1000);
                }
            }, 2000);
        }

        if(mScreenType.equals("video_verification")) {
            //begin challenge after 2 seconds for camera warm up
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    //record after-challenge phrase
                    final Handler handler = new Handler();
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            updateDisplayText(mActivity.getString(R.string.SAY_PASSPHRASE, mPhrase), false);
                            setProgressCircleAngle(270.0,360.0);
                            setProgressCircleColor(R.color.progressCircle);
                            mOverlay.startDrawingProgressCircle();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    stopRecording();
                                    mOverlay.updateDisplayText("Please Wait...");
                                    sendVideoFile();
                                }
                            }, 5000);
                        }
                    }, (long)mChallengeTime * 1000);
                    //begin challenge
                    beginChallenge();
                }
            }, 2000);
        }
    }

    private void sendVideoFile() {
        if(mScreenType.equals("face_verification")) {
            mVoiceItAPI2.faceVerification(mUserId, contentLanguage, file, new JsonHttpResponseHandler(){
                @Override
                public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                    super.onSuccess(statusCode, headers, response);
                    handleResponse(response, file);
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                    super.onFailure(statusCode, headers, throwable, errorResponse);
                    handleError();
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                    super.onFailure(statusCode, headers, responseString, throwable);
                    handleError();
                }
            },mLcoId);

        } if(mScreenType.equals("video_verification")) {
            mVoiceItAPI2.videoVerification(mUserId, contentLanguage, mPhrase, file, new JsonHttpResponseHandler(){
                @Override
                public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                    super.onSuccess(statusCode, headers, response);
                    handleResponse(response, file);
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                    super.onFailure(statusCode, headers, throwable, errorResponse);
                    handleError();
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                    super.onFailure(statusCode, headers, responseString, throwable);
                    handleError();
                }
            }, mLcoId);
        }
    }

    private void handleError(){
        mOverlay.updateDisplayText("Server error please try again.");
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                exitViewWithMessage("voiceit-failure","Response Failure");
            }
        }, 3000);
    }

    private void exitViewWithMessage(String action, String message){
        Intent intent = new Intent(action);
        JSONObject json = new JSONObject();
        try {
            json.put("message", message);
        } catch(JSONException e) {
            Log.d(mTAG,"JSON Exception : " + e.getMessage());
        }
        intent.putExtra("Response", json.toString());
        LocalBroadcastManager.getInstance(mActivity).sendBroadcast(intent);
        mActivity.finish();
    }

    private void exitViewWithJSON(String action, JSONObject message){
        Intent intent = new Intent(action);
        JSONObject json = new JSONObject();
        try {
            json.put("message", message);
        } catch(JSONException e) {
            Log.d(mTAG,"JSON Exception : " + e.getMessage());
        }
        intent.putExtra("Response", json.toString());
        LocalBroadcastManager.getInstance(mActivity).sendBroadcast(intent);
        mActivity.finish();
    }

    private void handleResponse(final JSONObject response, File file) {
        try {
            file.delete();
            String uiMessage = response.getString("uiMessage");
            Boolean retry = response.getBoolean("retry");
            Boolean success = response.getBoolean("success");
            String audioPromptType = response.getString("audioPrompt");
            String audioString = audioPromptType.toLowerCase().substring(0, audioPromptType.length() - 4);

            if(!success && retry){
                playLivenessPrompt(audioString);
                mOverlay.updateDisplayText(uiMessage);
                prepareForVideoRecording();
                performLivenessTest();
            }

            if(!success && !retry){
                releaseMediaRecorder();
                mOverlay.updateDisplayText(uiMessage);
                playLivenessPrompt(audioString);
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                exitViewWithMessage("voiceit-failure","Liveness Failure");
                            }
                        }, 3000);
                    }
                });
            }

            if(success){
                releaseMediaRecorder();
                mOverlay.updateDisplayText(uiMessage);
                playLivenessPrompt(audioString);
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                exitViewWithJSON("voiceit-success", response);
                            }
                        }, 3000);
                    }
                });
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // LCO Type and response type is converted to visual clue
    private void illuminateCircles(String instruction){
        setProgressCircleColor(R.color.pendingLivenessSuccess);
        switch (instruction){
            case "face_left":
                setProgressCircleAngle(135.0, 90.0);
                break;
            case "face_right":
                setProgressCircleAngle(315.0, 90.0);
                break;
            case "face_up":
                setProgressCircleAngle(-60.0, -65.0);
                break;
            case "face_down":
                setProgressCircleAngle(60.0, 65.0);
                break;
            case "face_tilt_left":
                setProgressCircleAngle(270.0, -60.0);
                break;
            case "face_tilt_right":
                setProgressCircleAngle(-30.0, -60.0);
                break;
            default:
                setProgressCircleColor(R.color.portraitBackgroundLowLight);
                setProgressCircleAngle(0.0, 360.0);
                break;
        }
    }

    /**
     * Called when the face is assumed to be gone for good.
     */
    @Override
    public void onDone() {
        LivenessTracker.lookingAway = true;
        if(LivenessTracker.continueDetecting) {
            Log.d(mTAG, "No face present");

            setProgressCircleAngle(270.0, 0.0);
            updateDisplayText(mActivity.getString(R.string.LOOK_INTO_CAM), false);
        }
    }

    interface viewCallBacks { // interface with callback methods for the views
        void authMethodToCallBack();
        void takePictureCallBack();
    }
}
