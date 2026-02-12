package com.voiceit.voiceit2;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
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

    static boolean continueDetecting = true;
    static boolean lookingAway = false;

    FaceTracker(RadiusOverlayView overlay, Activity activity, viewCallBacks callbacks) {
        mOverlay = overlay;
        mActivity = activity;
        mCallbacks = callbacks;
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

    /**
     * Update the position/characteristics of the face.
     */
    @Override
    public void onUpdate(FaceDetector.Detections<Face> detectionResults, final Face face) {

        if (FaceTracker.continueDetecting) {
            final int numFaces = detectionResults.getDetectedItems().size();

            if (numFaces == 1 && mOverlay.insidePortraitCircle(mActivity, face)) {
                FaceTracker.lookingAway = false;

                FaceTracker.continueDetecting = false;

                updateDisplayText("WAIT", false);
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // Quick pause, .75 seconds
                                setProgressCircleAngle(270.0, 0.0);
                                setProgressCircleColor(R.color.progressCircle);
                                // Display picture of user at the end of process
                                mOverlay.displayPicture = true;

                                // Take picture of user in preview to show
                                CameraSource.captureNextPreviewFrame = true;

                                // Take picture then auth
                                mCallbacks.takePictureCallBack();
                            }
                        }, 750);
                    }
                });

            } else if (numFaces > 1) {
                Log.d(mTAG, "Too many faces present");
                updateDisplayText("TOO_MANY_FACES", false);
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
            Log.d(mTAG, "No face present");

            setProgressCircleAngle(270.0, 0.0);
            updateDisplayText("LOOK_INTO_CAM", false);
        }
    }

    interface viewCallBacks { // interface with callback methods for the views
        void authMethodToCallBack();
        void takePictureCallBack();
    }
}
