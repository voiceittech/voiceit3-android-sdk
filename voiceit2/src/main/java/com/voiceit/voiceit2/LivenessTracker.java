package com.voiceit.voiceit2;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.media.MediaPlayer;

import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.voiceit.voiceit2.RadiusOverlayView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

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
    private String mCountryCode;


    static boolean continueDetecting = true;
    static boolean lookingAway = false;
    static final Handler livenessTimer = new Handler();

    LivenessTracker(RadiusOverlayView overlay, Activity activity, viewCallBacks callbacks, int[] livenessChallengeOrder,
                    boolean doLivenessCheck, boolean doLivenessAudioCheck, int livenessChallengeFailsAllowed,
                    int livenessChallengesNeeded, String uiLivenessInstruction, List<String> lcoStrings,
                    List<String> lco, float challengeTime, boolean livenessSuccess, String lcoId, String countryCode) {
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
        mUiLivenessInstruction = uiLivenessInstruction;
        mCountryCode = countryCode;
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
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(mediaPlayer.isPlaying()){
                    mediaPlayer.stop();
                }
                if(mCountryCode == "es-ES"){
                    int resId = mActivity.getApplicationContext().getResources().getIdentifier(
                            livenessPrompt+"_es",
                            "raw",
                            mActivity.getApplicationContext().getPackageName()
                    );
                    mediaPlayer = MediaPlayer.create(mActivity.getApplicationContext(), resId);
                    mediaPlayer.start();
                } else{
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


    private void completeLivenessChallenge() {
        LivenessTracker.continueDetecting = false;
        LivenessTracker.livenessTimer.removeCallbacksAndMessages(null);
        mDisplayingChallenge = false;
        mDisplayingChallengeOutcome = false;
        mTimingLiveness = false;

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Quick pause, .75 seconds, in-between challenges
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        setProgressCircleAngle(270.0, 0.0);
                        LivenessTracker.livenessChallengesPassed++;
                        if(challengeIndex >= mLivenessChallengeOrder.length-1) {
                            challengeIndex = 0;
                        } else {
                            challengeIndex++;
                        }
                        // Take picture in the middle of liveness checks
                        if (LivenessTracker.livenessChallengesPassed == 1) {
                            // Capture preview picture to show to the user later
                            CameraSource.captureNextPreviewFrame = true;

                            mCallbacks.takePictureCallBack();
                        } else {
                            LivenessTracker.continueDetecting = true;
                        }
                    }
                }, 750);
            }
        });
    }

    private void livenessCheck(Face face) {

        switch (mLivenessChallengeOrder[challengeIndex]) {

            // Smile
            case 1:
                if (!mDisplayingChallenge) {
                    mDisplayingChallenge = true;

                    // Smiling before prompt
                    if (face.getIsSmilingProbability() > .5) {
                        mDisplayingChallengeOutcome = true;
                        failLiveness(true);
                    } else {
                        if(mDoLivenessAudioCheck) {
                            playLivenessPrompt("smile");
                        }
                        updateDisplayText(mActivity.getString(R.string.SMILE), false);
                    }

                } else if (face.getIsSmilingProbability() > .5 && !mDisplayingChallengeOutcome) {
                    mDisplayingChallengeOutcome = true;
                    setProgressCircleColor(R.color.success);
                    setProgressCircleAngle(270.0, 359.999);
                    completeLivenessChallenge();
                }
                break;

            // Turn face left
            case 2:
                if (!mDisplayingChallenge) {
                    mDisplayingChallenge = true;

                    // Turned before prompt
                    if (face.getEulerY() > faceTurnedValue) {
                        mDisplayingChallengeOutcome = true;
                        failLiveness(true);
                    } else {
                        if(mDoLivenessAudioCheck) {
                            playLivenessPrompt("face_left");
                        }
                        updateDisplayText(mActivity.getString(R.string.TURN_LEFT), false);
                        setProgressCircleColor(R.color.pendingLivenessSuccess);
                        setProgressCircleAngle(135.0, 90.0);
                    }

                } else if (!mDisplayingChallengeOutcome) {
                    if(face.getEulerY() > faceTurnedValue) {
                        mDisplayingChallengeOutcome = true;
                        setProgressCircleColor(R.color.success);
                        completeLivenessChallenge();

                    } else if (face.getEulerY() < -22.0) {
                        mDisplayingChallengeOutcome = true;
                        // Turned wrong direction so fail
                        failLiveness(false);
                    }
                }
                break;

            // Turn face right
            case 3:
                if (!mDisplayingChallenge) {
                    mDisplayingChallenge = true;

                    // Turned before prompt
                    if(face.getEulerY() < -faceTurnedValue) {
                        mDisplayingChallengeOutcome = true;
                        failLiveness(true);
                    } else {
                        if(mDoLivenessAudioCheck) {
                            playLivenessPrompt("face_right");
                        }
                        updateDisplayText(mActivity.getString(R.string.TURN_RIGHT), false);
                        setProgressCircleColor(R.color.pendingLivenessSuccess);
                        setProgressCircleAngle(315.0, 90.0);
                    }

                } else if (!mDisplayingChallengeOutcome) {
                    if(face.getEulerY() < -faceTurnedValue) {
                        mDisplayingChallengeOutcome = true;
                        setProgressCircleColor(R.color.success);
                        completeLivenessChallenge();

                    } else if (face.getEulerY() > 22.0) {
                        mDisplayingChallengeOutcome = true;
                        // Turned wrong direction so fail
                        failLiveness(false);
                    }
                }
                break;

            default:
                break;
        }
    }

    private void failLiveness(boolean failedPrecheck) {
        // Cleanup
        LivenessTracker.continueDetecting = false;
        LivenessTracker.livenessTimer.removeCallbacksAndMessages(null);
        mDisplayingChallenge = false;
        mDisplayingChallengeOutcome = false;
        mTimingLiveness = false;

        // Display fail to user
        setProgressCircleAngle(0.0, 0.0);
        setProgressCircleColor(R.color.failure);
        setProgressCircleAngle(270.0, 359.0);

        LivenessTracker.livenessChallengeFails++;
        if(LivenessTracker.livenessChallengeFails > mLivenessChallengeFailsAllowed) {
            updateDisplayText((failedPrecheck ? mActivity.getString(R.string.FAILED_PRECHECK) + " " : "")
                    + mActivity.getString(R.string.FAILED_LIVENESS), false);
            Log.d(mTAG, "display : " + ((failedPrecheck ? mActivity.getString(R.string.FAILED_PRECHECK) + " " : "")
                    + mActivity.getString(R.string.FAILED_LIVENESS)));
        } else {
            updateDisplayText((failedPrecheck ? mActivity.getString(R.string.FAILED_PRECHECK) + " " : "")
                    + mActivity.getString(R.string.FAILED_LIVENESS_CHALLENGE), false);
            Log.d(mTAG, "display : " + ((failedPrecheck ? mActivity.getString(R.string.FAILED_PRECHECK) + " " : "")
                    + mActivity.getString(R.string.FAILED_LIVENESS)));
        }

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Wait for 2.5 seconds
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {

                        // Exit if failed too many times, else restart
                        if(LivenessTracker.livenessChallengeFails > mLivenessChallengeFailsAllowed) {
                            Intent intent = new Intent("voiceit-failure");
                            JSONObject json = new JSONObject();
                            try {
                                json.put("message", "User Failed Liveness Detection");
                            } catch (JSONException e) {
                                Log.d(mTAG, "JSON Exception : " + e.getMessage());
                            }
                            intent.putExtra("Response", json.toString());
                            LocalBroadcastManager.getInstance(mActivity).sendBroadcast(intent);
                            mActivity.finish();
                        } else { // Give another try
                            LivenessTracker.continueDetecting = true;
                        }
                    }
                }, 2500);
            }
        });
    }

    /**
     * Update the position/characteristics of the face.
     */
    @Override
    public void onUpdate(FaceDetector.Detections<Face> detectionResults, final Face face) {
        if(mDoLivenessCheck){
            updateDisplayText(mUiLivenessInstruction,false);
        }else {
            if (LivenessTracker.continueDetecting) {
                final int numFaces = detectionResults.getDetectedItems().size();

                if (numFaces == 1 && mOverlay.insidePortraitCircle(mActivity, face)) {
                    LivenessTracker.lookingAway = false;

                    // Success or skip liveness detection
                    if (LivenessTracker.livenessChallengesPassed == mLivenessChallengesNeeded || !mDoLivenessCheck) {
                        LivenessTracker.continueDetecting = false;
                        LivenessTracker.livenessChallengeFails = 0;

                        updateDisplayText(mActivity.getString(R.string.WAIT), false);
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
                        livenessCheck(face);

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
                                            failLiveness(false);
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

    private void illuminateCircles(String direction){
        setProgressCircleColor(R.color.pendingLivenessSuccess);
        switch (direction){
            case "Left":
                setProgressCircleAngle(135.0, 90.0);
                break;
            case "Right":
                setProgressCircleAngle(315.0, 90.0);
                break;
            case "Up":
                setProgressCircleAngle(-60.0, -65.0);
                break;
            case "Down":
                setProgressCircleAngle(60.0, 65.0);
                break;
            case "Tilt_Left":
                setProgressCircleAngle(270.0, -60.0);
                break;
            case "Tilt_Right":
                setProgressCircleAngle(-30.0, -60.0);
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
