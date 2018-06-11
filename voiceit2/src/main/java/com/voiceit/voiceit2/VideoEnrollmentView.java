package com.voiceit.voiceit2;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.loopj.android.http.JsonHttpResponseHandler;
import cz.msebera.android.httpclient.Header;

import org.json.JSONException;
import org.json.JSONObject;

public class VideoEnrollmentView extends AppCompatActivity {

    private CameraSource mCameraSource = null;
    private CameraSourcePreview mPreview;
    private final File mPictureFile = Utils.getOutputMediaFile(".jpeg");
    private MediaRecorder mMediaRecorder = null;

    private final String mTAG = "VideoEnrollmentView";
    private Context mContext;

    private RadiusOverlayView mOverlay;

    private VoiceItAPI2 mVoiceIt2;
    private String mUserID = "";
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
            mUserID = bundle.getString("userID");
            mContentLanguage = bundle.getString("contentLanguage");
            mPhrase = bundle.getString("phrase");
        }

        // Hide action bar
        try {
            this.getSupportActionBar().hide();
        } catch (NullPointerException e) {
            Log.d(mTAG,"Cannot hide action bar");
        }

        // Set context
        mContext = this;
        // Set content view
        setContentView(R.layout.activity_video_enrollment_view);
        mPreview = findViewById(R.id.camera_preview);

        // Text output on mOverlay
        mOverlay = findViewById(R.id.overlay);

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
        createCameraSource();
        // Try to start camera
        startCameraSource();

        // Delete enrollments and re-enroll
        mVoiceIt2.deleteAllEnrollmentsForUser(mUserID, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject Response) {
                mOverlay.updateDisplayText(getString(R.string.LOOK_INTO_CAM));
                // Start tracking faces
                FaceTracker.continueDetecting = true;
            }
            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                if (errorResponse != null) {
                    exitViewWithJSON("voiceit-failure", errorResponse);
                } else {
                    Log.e(mTAG, "No response from server");
                    mOverlay.updateDisplayTextAndLock(getString(R.string.CHECK_INTERNET));
                    // Wait for 2.0 seconds
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            exitViewWithMessage("voiceit-failure","No response from server");
                        }
                    }, 2000);
                }
            }
        });
    }

    /**
     * Creates and starts the camera.
     */
    private void createCameraSource() {

        Context context = getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setMode(FaceDetector.ACCURATE_MODE)
                .setProminentFaceOnly(true)
                .build();

        detector.setProcessor(
                new MultiProcessor.Builder<>(new FaceTrackerFactory(this))
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
            Toast.makeText(this, "Downloading Face detector dependencies", Toast.LENGTH_LONG).show();
            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            IntentFilter lowStorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowStorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(this, "Face detector dependencies cannot be downloaded due to low device storage", Toast.LENGTH_LONG).show();
                Log.w(mTAG, "Face detector dependencies cannot be downloaded due to low device storage");
            }
        }

        // Build camera source and attach detector
        mCameraSource = new CameraSource.Builder(context, detector)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .build();
    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            final int RC_HANDLE_GMS = 9001;
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource);
            } catch (IOException e) {
                Log.d(mTAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
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
            return new FaceTracker(mOverlay, mActivity, new FaceTrackerCallBackImpl(), new int[]{}, false);
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
        mContinueEnrolling = false;
        stopRecording();
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
        mContinueEnrolling = false;
        stopRecording();
        Intent intent = new Intent(action);
        intent.putExtra("Response", json.toString());
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
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

    // Enroll after taking picture
    private final CameraSource.PictureCallback mPicture = new CameraSource.PictureCallback() {
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

    private void takePicture() {
        try {
            // Take picture of face
            mCameraSource.takePicture(null, mPicture);
        } catch (Exception e) {
            Log.d(mTAG, "Camera exception : " + e.getMessage());
            exitViewWithMessage("voiceit-failure", "Camera Error");
        }
    }

    private void failEnrollment(final JSONObject response) {
        mOverlay.setProgressCircleColor(getResources().getColor(R.color.red));
        mOverlay.updateDisplayText(getString(R.string.ENROLL_FAIL));

        // Wait for ~1.5 seconds
        new Handler().postDelayed(new Runnable() {
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
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mFailedAttempts++;

                        // User failed too many times
                        if(mFailedAttempts >= mMaxFailedAttempts) {
                            mOverlay.updateDisplayText(getString(R.string.TOO_MANY_ATTEMPTS));
                            // Wait for ~2 seconds
                            new Handler().postDelayed(new Runnable() {
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
        // Display enrollment mPhrase to user
        mOverlay.updateDisplayText(getString(getResources().getIdentifier("ENROLL_" + (mEnrollmentCount + 1) + "_PHRASE", "string", getPackageName()), mPhrase));
        try {
            // Create file for audio
            final File audioFile = Utils.getOutputMediaFile(".wav");
            if(audioFile == null) {
                exitViewWithMessage("voiceit-failure", "Creating audio file failed");
            }

            // Setup device and capture audio
            mMediaRecorder = new MediaRecorder();
            Utils.startMediaRecorder(mMediaRecorder, audioFile);

            mOverlay.setProgressCircleColor(getResources().getColor(R.color.yellow));
            mOverlay.startDrawingProgressCircle();
            // Record for ~5 seconds, then send enrollment data
            // 4800 to make sure recording is not over 5 seconds
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mContinueEnrolling) {
                        stopRecording();

                        mOverlay.updateDisplayText(getString(R.string.WAIT));
                        mVoiceIt2.createVideoEnrollment(mUserID, mContentLanguage, audioFile, mPictureFile, new JsonHttpResponseHandler() {
                            @Override
                            public void onSuccess(int statusCode, Header[] headers, final JSONObject response) {
                                try {
                                    // Wrong Phrase
                                    if (!response.getString("text").toLowerCase().equals(mPhrase.toLowerCase())) {
                                        mOverlay.setProgressCircleColor(getResources().getColor(R.color.red));
                                        mOverlay.updateDisplayText(getString(R.string.ENROLL_FAIL));

                                        // Wait for ~1.5 seconds
                                        new Handler().postDelayed(new Runnable() {
                                            @Override
                                            public void run() {

                                                mOverlay.updateDisplayText(getString(R.string.INCORRECT_PASSPHRASE, mPhrase));

                                                // Wait for ~4.5 seconds
                                                new Handler().postDelayed(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        audioFile.deleteOnExit();
                                                        mPictureFile.deleteOnExit();
                                                        mFailedAttempts++;

                                                        // User failed too many times
                                                        if (mFailedAttempts > mMaxFailedAttempts) {
                                                            mOverlay.updateDisplayText(getString(R.string.TOO_MANY_ATTEMPTS));
                                                            // Wait for ~2 seconds
                                                            new Handler().postDelayed(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    exitViewWithMessage("voiceit-failure", "Too many attempts");
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
                                                }, 4800);
                                            }
                                        }, 1500);

                                        // Success
                                    } else if (response.getString("responseCode").equals("SUCC")) {
                                        mOverlay.setProgressCircleColor(getResources().getColor(R.color.green));
                                        mOverlay.updateDisplayText(getString(R.string.ENROLL_SUCCESS));

                                        // Wait for ~2 seconds
                                        new Handler().postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                audioFile.deleteOnExit();
                                                mPictureFile.deleteOnExit();
                                                mEnrollmentCount++;

                                                if (mEnrollmentCount == mNeededEnrollments) {
                                                    mOverlay.updateDisplayText(getString(R.string.ALL_ENROLL_SUCCESS));
                                                    // Wait for ~2.5 seconds
                                                    new Handler().postDelayed(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            exitViewWithJSON("voiceit-success", response);
                                                        }
                                                    }, 2500);
                                                } else {
                                                    if(FaceTracker.lookingAway) {
                                                        mOverlay.updateDisplayText(getString(R.string.LOOK_INTO_CAM));
                                                    }
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
                                    new Handler().postDelayed(new Runnable() {
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

    class FaceTrackerCallBackImpl implements FaceTracker.viewCallBacks { // Implements callback methods defined in FaceTracker interface
        public void authMethodToCallBack() {
            enrollUser();
        }
        public void takePictureCallBack() { takePicture(); }
    }

}