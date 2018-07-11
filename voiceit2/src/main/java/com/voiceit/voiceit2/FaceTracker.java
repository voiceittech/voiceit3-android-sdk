package com.voiceit.voiceit2;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Face tracker for each detected individual.
 */
class FaceTracker extends Tracker<Face> {

    private final Activity mActivity;
    private final RadiusOverlayView mOverlay;
    private final viewCallBacks mCallbacks;

    private final String mTAG = "FaceTracker";

    private final int [] mLivenessChallengeOrder;

    private final boolean mDoLivenessCheck;
    private final int mLivenessChallengeFailsAllowed;
    private final int mLivenessChallengesNeeded;

    public static int livenessChallengesPassed = 0;
    public static int livenessChallengeFails = 0;
    private boolean mDisplayingChallenge = false;
    private boolean mDisplayingChallengeOutcome = false;
    private boolean mTimingLiveness = false;
//    private boolean updateBlinkDisplay = true;
//    private boolean blinking = false;
//    private int blinks = 0;
//    private int neededBlinks = 2;

    public static boolean continueDetecting = true;
    public static boolean lookingAway = false;
    public static final Handler livenessTimer = new Handler();

    FaceTracker(RadiusOverlayView overlay, Activity activity, viewCallBacks callbacks, int [] livenessChallengeOrder, boolean doLivenessCheck, int livenessChallengeFailsAllowed, int livenessChallengesNeeded) {
        mOverlay = overlay;
        mActivity = activity;
        mCallbacks = callbacks;
        mLivenessChallengeOrder = livenessChallengeOrder;
        mDoLivenessCheck = doLivenessCheck;
        mLivenessChallengeFailsAllowed = livenessChallengeFailsAllowed;
        mLivenessChallengesNeeded = livenessChallengesNeeded;
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

    private void completeLivenessChallenge() {
        FaceTracker.continueDetecting = false;
        FaceTracker.livenessTimer.removeCallbacksAndMessages(null);
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
                        FaceTracker.livenessChallengesPassed++;
                        // Take picture in the middle of liveness checks
                        if (FaceTracker.livenessChallengesPassed == 1) {
                            mCallbacks.takePictureCallBack();
                        } else {
                            FaceTracker.continueDetecting = true;
                        }
                    }
                }, 750);
            }
        });
    }

    private void livenessCheck(Face face) {

        switch (mLivenessChallengeOrder[FaceTracker.livenessChallengesPassed]) {

            // Smile
            case 1:
                if (!mDisplayingChallenge) {
                    mDisplayingChallenge = true;
                    updateDisplayText(mActivity.getString(R.string.SMILE), false);

                } else if (face.getIsSmilingProbability() > .5 && !mDisplayingChallengeOutcome) {
                    mDisplayingChallengeOutcome = true;
                    setProgressCircleColor(R.color.success);
                    setProgressCircleAngle(270.0,359.999);
                    completeLivenessChallenge();
                }
                break;

                // Turn face left
            case 2:
                if (!mDisplayingChallenge) {
                    mDisplayingChallenge = true;
                    updateDisplayText(mActivity.getString(R.string.TURN_LEFT), false);
                    setProgressCircleColor(R.color.pendingLivenesSuccess);
                    setProgressCircleAngle(135.0, 90.0);

                } else if (!mDisplayingChallengeOutcome) {
                    if(face.getEulerY() > 18.0) {
                        mDisplayingChallengeOutcome = true;
                        setProgressCircleColor(R.color.success);
                        completeLivenessChallenge();

                    } else if (face.getEulerY() < -22.0) {
                        // Turned wrong direction so fail
                        failLiveness();
                    }
                }
                break;

                // Turn face right
            case 3:
                if (!mDisplayingChallenge) {
                    mDisplayingChallenge = true;
                    updateDisplayText(mActivity.getString(R.string.TURN_RIGHT), false);
                    setProgressCircleColor(R.color.pendingLivenesSuccess);
                    setProgressCircleAngle(315.0, 90.0);

                } else if (!mDisplayingChallengeOutcome) {
                    if(face.getEulerY() < -18.0) {
                        mDisplayingChallengeOutcome = true;
                        setProgressCircleColor(R.color.success);
                        completeLivenessChallenge();

                    } else if (face.getEulerY() > 22.0) {
                        // Turned wrong direction so fail
                        failLiveness();
                    }
                }
                break;

                /* The current Android FaceDetector does not catch blinks fast/well in
                    enough environments for liveness detection  */

                // Blink
//            case 4:
//                if(!mDisplayingChallenge && updateBlinkDisplay) {
//                    mActivity.runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            updateDisplayText(mActivity.getString(mActivity.getResources()
//                                    .getIdentifier("BLINK_"+ (neededBlinks - blinks) +"_TIME" ,
//                                            "string", mActivity.getPackageName())));
//                            updateBlinkDisplay = false;
//                        }
//                    });
//                }
//
//                double rightProb = face.getIsRightEyeOpenProbability();
//                double leftProb = face.getIsLeftEyeOpenProbability();
//                double eyeOpenThreshold = .45;
//
//                if(!blinking) {
//                    if (rightProb < eyeOpenThreshold
//                            && leftProb < eyeOpenThreshold) {
//                        blinking = true;
//                    }
//                } else {
//                    if (rightProb > eyeOpenThreshold
//                            && leftProb > eyeOpenThreshold) {
//                        blinking = false;
//                        blinks++;
//                        updateBlinkDisplay = true;
//                    }
//                }
//
//                if(blinks == neededBlinks && !mDisplayingChallengeOutcome) {
//                    mDisplayingChallengeOutcome = true;
//                    mActivity.runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            overlay.setProgressCircleColor(mActivity.getResources().getColor(R.color.success));
//                            overlay.setProgressCircleAngle(359.999);
//                            // Quick pause in-between challenges
//                            new Handler().postDelayed(new Runnable() {
//                                @Override
//                                public void run() {
//                                    completeLivenessChallenge();
//                                    updateBlinkDisplay = true;
//                                }
//                             }, 750);
//                        }
//                    });
//                }
//                break;
            default:
                break;
        }
    }

    private void failLiveness() {
        // Cleanup
        FaceTracker.continueDetecting = false;
        FaceTracker.livenessTimer.removeCallbacksAndMessages(null);
        mDisplayingChallenge = false;
        mDisplayingChallengeOutcome = false;
        mTimingLiveness = false;

        // Display fail to user
        setProgressCircleAngle(0.0, 0.0);
        setProgressCircleColor(R.color.failure);
        setProgressCircleAngle(270.0, 359.0);

        FaceTracker.livenessChallengeFails++;
        if(FaceTracker.livenessChallengeFails > mLivenessChallengeFailsAllowed) {
            updateDisplayText(mActivity.getString(R.string.FAILED_LIVENESS), false);
        } else {
            updateDisplayText(mActivity.getString(R.string.FAILED_LIVENESS_CHALLENGE), false);
        }

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Wait for 2.0 seconds
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {

                        // Exit if failed too many times, else restart
                        if(FaceTracker.livenessChallengeFails > mLivenessChallengeFailsAllowed) {
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
                            FaceTracker.continueDetecting = true;
                        }
                    }
                }, 2000);
            }
        });
    }

    /**
     * Update the position/characteristics of the face.
     */
    @Override
    public void onUpdate(FaceDetector.Detections<Face> detectionResults, final Face face) {
        if (FaceTracker.continueDetecting) {
            final int numFaces = detectionResults.getDetectedItems().size();

            if (numFaces == 1) {
                FaceTracker.lookingAway = false;

                // Success or skip liveness detection
                if (FaceTracker.livenessChallengesPassed == mLivenessChallengesNeeded || !mDoLivenessCheck) {
                    FaceTracker.continueDetecting = false;
                    FaceTracker.livenessChallengeFails = 0;

                    updateDisplayText(mActivity.getString(R.string.WAIT), false);
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    // Quick pause, 1 seconds, after all challenges are done
                                    setProgressCircleAngle(270.0, 0.0);
                                    setProgressCircleColor(R.color.progressCircle);
                                    if (!mDoLivenessCheck) {
                                        // Since picture was not taken during liveness check, take one now then auth
                                        mCallbacks.takePictureCallBack();
                                    } else {
                                        // verify or enroll callback
                                        mCallbacks.authMethodToCallBack();
                                    }
                                }
                            }, 1000);
                        }
                    });

                } else if (FaceTracker.livenessChallengesPassed < mLivenessChallengesNeeded) {
                    // Display a liveness challenge to the user
                    livenessCheck(face);

                    // Start a timer for current liveness challenge
                    if (!mTimingLiveness && FaceTracker.continueDetecting) {
                        mTimingLiveness = true;
                        mActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // Fail after 3 seconds
                                livenessTimer.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        // Fail if user didn't pass a liveness check in time
                                        failLiveness();
                                    }
                                }, 3000);
                            }
                        });
                    }
                }

            } else if (numFaces > 1) {
                Log.d("FaceDetection", "Too many faces present");
                updateDisplayText(mActivity.getString(R.string.TOO_MANY_FACES), false);
                setProgressCircleAngle(270.0, 0.0);
            }
        }
    }

    /**
     * Called when the face is assumed to be gone for good.
     */
    @Override
    public void onDone() {
        FaceTracker.lookingAway = true;
        if(FaceTracker.continueDetecting) {
            Log.d("FaceDetection", "No face present");

            setProgressCircleAngle(270.0, 0.0);
            updateDisplayText(mActivity.getString(R.string.LOOK_INTO_CAM), false);
        }
    }

    public interface viewCallBacks { // interface with callback methods for the views
        void authMethodToCallBack();
        void takePictureCallBack();
    }
}
