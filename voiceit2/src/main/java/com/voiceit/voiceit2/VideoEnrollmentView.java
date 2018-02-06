package com.voiceit.voiceit2;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import com.loopj.android.http.JsonHttpResponseHandler;
import cz.msebera.android.httpclient.Header;

import org.json.JSONException;
import org.json.JSONObject;

public class VideoEnrollmentView extends AppCompatActivity {

    final int PERMISSIONS_REQUEST_RECORD_AUDIO = 0;
    final int PERMISSIONS_REQUEST_CAMERA = 1;
    final int ASK_MULTIPLE_PERMISSION_REQUEST_CODE = 2;

    private Camera mCamera;
    private CameraPreview mPreview;
    private MediaRecorder mMediaRecorder;
    Camera.FaceDetectionListener faceDetectObj;
    private static final String TAG = CameraPreview.class.getName();
    private static Context mContext;

    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_AUDIO = 2;

    private VoiceItAPI2 myVoiceIt2;
    private String userID = "";
    private String contentLanguage = "";
    private String phrase = "";

    private int enrollmentCount = 0;
    private final int neededEnrollments = 3;
    private boolean isRecording = false;
    private int failedAttempts = 0;
    private final int maxFailedAttempts = 3;
    private RadiusOverlayView overlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);

        super.onCreate(savedInstanceState);

        // Grab data from parent activity
        Bundle bundle = getIntent().getExtras();
        if(bundle != null) {
            myVoiceIt2 = new VoiceItAPI2(bundle.getString("apiKey"), bundle.getString("apiToken"));
            userID = bundle.getString("userID");
            contentLanguage = bundle.getString("contentLanguage");
            phrase = bundle.getString("phrase");
        }

        // Hide action bar
        try {
            this.getSupportActionBar().hide();
        } catch (NullPointerException e) {
            System.out.println("Cannot hide action bar");
        }

        // Set context
        mContext = this;
        // Set content view
        setContentView(R.layout.activity_video_enrollment_view);

        // Request media device permissions
        requestHardwarePermissions();

        // Orient screen
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Text output on overlay
        overlay = findViewById(R.id.overlay);

        // Create native face detection listener
         faceDetectObj = new Camera.FaceDetectionListener() {
            @Override
            public void onFaceDetection(Camera.Face[] faces, Camera camera) {
                if(!isRecording && enrollmentCount < neededEnrollments) {
                    if (faces.length > 0) {
                        Log.d("FaceDetection", "face detected at: " + faces.length +
                                " Face 1 Location X: " + faces[0].rect.centerX() +
                                " Y: " + faces[0].rect.centerY());
                        overlay.setProgressCircleAngle(0);
                        // Try to enroll
                        enrollUser();
                    } else {
                        Log.d("FaceDetection", "No face present");
                        // Tell user there is no face in camera preview
                        overlay.updateDisplayText(getString(R.string.LOOK_INTO_CAM));
                        overlay.setProgressCircleAngle(0);
                    }
                }
            }
        };

        // Request media device permissions
        requestHardwarePermissions();
        // Try to access and setup access camera
        accessCamera(faceDetectObj);
    }

    public void accessCamera(Camera.FaceDetectionListener faceDetectObj) {
        // Check access to camera
        if (checkCameraHardware(this)) {
            try {
                releaseCamera();
                // Create an instance of Camera
                mCamera = getCameraInstance();
                if (mCamera != null) {
                    // Create our Preview view and set it as the content of our activity.
                    mPreview = new CameraPreview(this, mCamera);
                    mPreview.setDrawingCacheEnabled(true);
                    FrameLayout preview = findViewById(R.id.camera_preview);
                    preview.addView(mPreview);

                    // Set face detection listener
                    mCamera.setFaceDetectionListener(faceDetectObj);
                } else {
                    System.out.println("Cannot access camera");
                }
            }
            catch(Exception e) {
                System.out.println("Preview Exception : " + e.getMessage());
            }
        }
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent("enrollment-failure");
        JSONObject json = new JSONObject();
        try {
            json.put("message", "User Canceled");
        } catch(JSONException e) {
            System.out.println("JSON Exception : " + e.getMessage());
        }
        intent.putExtra("JSON", json.toString());
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Try to access and setup camera
        accessCamera(faceDetectObj);
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseMediaRecorder();       // if you are using MediaRecorder, release it first
        releaseCamera();              // release the camera immediately on pause event
    }

    private void releaseMediaRecorder(){
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
            mCamera.lock();           // lock camera for later use
        }
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    /** Check if this device has a camera */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            System.out.println("Could not find camera device");
            return false;
        }
    }

    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(1); // attempt to get a Camera instance
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
            System.out.println("Camera instance exception : " + e.getMessage());
        }
        return c; // returns null if camera is unavailable
    }

    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            // Reset camera for next picture
            mCamera.stopPreview();
            mCamera.startPreview();

            // Create file
            final File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
            if (pictureFile == null) {
                Log.d(TAG, "Error creating media file, check storage permissions");
                return;
            }
            // Write picture to file
            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }

            // Wait for ~1 seconds
            new CountDownTimer(1000, 1000) {
                public void onTick(long millisUntilFinished) {}
                public void onFinish() {
                    overlay.updateDisplayText(getString(getResources().getIdentifier("ENROLL_"+ (enrollmentCount + 1) +"_PHRASE" , "string", getPackageName())));

                    // Wait for ~0.1 seconds for user to see phrase
                    new CountDownTimer(100, 1000) {
                        public void onTick(long millisUntilFinished) {}
                        public void onFinish() {

                            // Capture Audio
                            final MediaRecorder recorder = new MediaRecorder();
                            try {
                                // Create file for audio
                                final File audioFile = getOutputMediaFile(MEDIA_TYPE_AUDIO);

                                // Setup audio device
                                recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                                recorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
                                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
                                recorder.setAudioSamplingRate(44000);
                                recorder.setAudioChannels(1);
                                recorder.setAudioEncodingBitRate(8000);
                                recorder.setOutputFile(audioFile.getAbsolutePath());
                                recorder.prepare();
                                recorder.start();

                                overlay.setProgressCircleColor(getResources().getColor(R.color.yellow));
                                // Record for ~5 seconds, then send enrollment data
                                // 4800 to make sure recording is not over 5 seconds
                                new CountDownTimer(4800, 10) {
                                    public void onTick(long millisUntilFinished) {
                                        overlay.setProgressCircleAngle(360 * (1 - ((double) millisUntilFinished / 4800)));
                                    }
                                    public void onFinish() {
                                        overlay.setProgressCircleAngle(360);
                                        try {
                                            recorder.stop();
                                            recorder.reset();
                                            recorder.release();

                                            overlay.updateDisplayText(getString(R.string.WAIT));
                                            myVoiceIt2.createVideoEnrollment(userID, contentLanguage, audioFile, pictureFile, new JsonHttpResponseHandler() {
                                                @Override
                                                public void onSuccess(int statusCode, Header[] headers, final JSONObject response) {
                                                    System.out.println("JSONResult : " + response.toString());

                                                    try {
                                                        if (!response.get("text".toLowerCase()).equals(phrase.toLowerCase())) {
                                                            overlay.setProgressCircleColor(getResources().getColor(R.color.red));
                                                            overlay.updateDisplayText(getString(R.string.ENROLL_FAIL));

                                                            // Wait for ~1.5 seconds
                                                            new CountDownTimer(1500, 10) {
                                                                public void onTick(long millisUntilFinished) {}
                                                                public void onFinish() {

                                                                    overlay.updateDisplayText(getString(R.string.INCORRECT_PASSPHRASE));

                                                                    // Wait for ~4.5 seconds
                                                                    new CountDownTimer(4500, 1000) {
                                                                        public void onTick(long millisUntilFinished) {}
                                                                        public void onFinish() {
                                                                            audioFile.deleteOnExit();
                                                                            pictureFile.deleteOnExit();
                                                                            failedAttempts++;
                                                                            isRecording = false;
                                                                        }
                                                                    }.start();

                                                                    // User failed too many times
                                                                    if(failedAttempts > maxFailedAttempts) {
                                                                        overlay.updateDisplayText(getString(R.string.TOO_MANY_ATTEMPTS));
                                                                        // Wait for ~2 seconds
                                                                        new CountDownTimer(2000, 1000) {
                                                                            public void onTick(long millisUntilFinished) {}
                                                                            public void onFinish() {
                                                                                Intent intent = new Intent("enrollment-failure");
                                                                                JSONObject json = new JSONObject();
                                                                                try {
                                                                                    json.put("message", "Too many attempts");
                                                                                } catch(JSONException e) {
                                                                                    System.out.println("JSON Exception : " + e.getMessage());
                                                                                }
                                                                                intent.putExtra("JSON", json.toString());
                                                                                LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
                                                                                finish();
                                                                            }
                                                                        }.start();
                                                                    }
                                                                }
                                                            }.start();
                                                        } else {
                                                            overlay.setProgressCircleColor(getResources().getColor(R.color.green));
                                                            overlay.updateDisplayText(getString(R.string.ENROLL_SUCCESS));

                                                            // Wait for ~2 seconds
                                                            new CountDownTimer(2000, 1000) {
                                                                public void onTick(long millisUntilFinished) {}
                                                                public void onFinish() {
                                                                    audioFile.deleteOnExit();
                                                                    pictureFile.deleteOnExit();

                                                                    Intent intent = new Intent("enrollment-success");
                                                                    intent.putExtra("JSON", response.toString());
                                                                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
                                                                    finish();
                                                                }
                                                            }.start();
                                                        }
                                                    } catch (JSONException e) {
                                                        System.out.println("JSON Error: " + e.getMessage());
                                                    }
                                                }

                                                @Override
                                                public void onFailure(int statusCode, Header[] headers, Throwable throwable, final JSONObject errorResponse) {
                                                    if (errorResponse != null) {
                                                        System.out.println("JSONResult : " + errorResponse.toString());

                                                        overlay.setProgressCircleColor(getResources().getColor(R.color.red));
                                                        overlay.updateDisplayText(getString(R.string.ENROLL_FAIL));

                                                        // Wait for ~1.5 seconds
                                                        new CountDownTimer(1500, 10) {
                                                            public void onTick(long millisUntilFinished) {}
                                                            public void onFinish() {
                                                                try {
                                                                    overlay.updateDisplayText(getString((getResources().getIdentifier(errorResponse.
                                                                            getString("responseCode"), "string", getPackageName()))));
                                                                } catch (JSONException e) {
                                                                    System.out.println("JSON exception : " + e.toString());
                                                                }
                                                                // Wait for ~4.5 seconds
                                                                new CountDownTimer(4500, 1000) {
                                                                    public void onTick(long millisUntilFinished) {}
                                                                    public void onFinish() {
                                                                        audioFile.deleteOnExit();
                                                                        pictureFile.deleteOnExit();
                                                                        failedAttempts++;
                                                                        isRecording = false;
                                                                    }
                                                                }.start();

                                                                // User failed too many times
                                                                if(failedAttempts > maxFailedAttempts) {
                                                                    overlay.updateDisplayText(getString(R.string.TOO_MANY_ATTEMPTS));
                                                                    // Wait for ~2 seconds
                                                                    new CountDownTimer(2000, 1000) {
                                                                        public void onTick(long millisUntilFinished) {}
                                                                        public void onFinish() {
                                                                            Intent intent = new Intent("enrollment-failure");
                                                                            intent.putExtra("JSON", errorResponse.toString());
                                                                            LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
                                                                            finish();
                                                                        }
                                                                    }.start();
                                                                }
                                                            }
                                                        }.start();
                                                    }
                                                }
                                            });
                                        } catch (Exception ex) {
                                            System.out.println("Enrollment Exception Error: " + ex.getMessage());
                                        }
                                    }
                                }.start();
                            } catch (Exception ex) {
                                System.out.println("Recording Error:" + ex.getMessage());
                            }
                        }
                    }.start();
                }
            }.start();
        }
    };

    /** Create a File for saving an image or audio file */
    private static File getOutputMediaFile(int type){
        try {
            if (type == MEDIA_TYPE_IMAGE) {
                return File.createTempFile("prefix-", "-suffix");
            } else if (type == MEDIA_TYPE_AUDIO) {
                return File.createTempFile("tempfile", ".wav");
            } else {
                return null;
            }
        } catch (IOException e) {
            System.out.println("Creating file failed with exception : " + e.getMessage());
            return null;
        }
    }

    private void requestHardwarePermissions() {
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
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            System.out.println("Hardware Permissions not granted");
            finish();
        }
    }

    private void enrollUser() {
        isRecording = true;
        // Check enrollments then enroll
        myVoiceIt2.getAllEnrollmentsForUser(userID, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, final JSONObject response) {
                System.out.println("getAllEnrollmentsForUser JSONResult : " + response.toString());

                try {
                    enrollmentCount = response.getInt("count");
                    // If enough enrollments then return to previous activity
                    if(enrollmentCount >= neededEnrollments) {
                        overlay.updateDisplayText(getString(R.string.ALL_ENROLL_SUCCESS));
                        // Wait for ~2.5 seconds
                        new CountDownTimer(2500, 1000) {
                            public void onTick(long millisUntilFinished) {}
                            public void onFinish() {
                                Intent intent = new Intent("enrollment-success");
                                intent.putExtra("JSON", response.toString());
                                LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
                                finish();
                            }
                        }.start();
                    } else if (enrollmentCount < neededEnrollments && enrollmentCount == 0) {
                        // Delete enrollments and re-enroll
                        myVoiceIt2.deleteAllEnrollmentsForUser(userID, new JsonHttpResponseHandler() {
                            @Override
                            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                                try {
                                    mCamera.takePicture(null, null, mPicture);
                                } catch (Exception e) {
                                    System.out.println("Camera exception : " + e.getMessage());
                                    finish();
                                }
                            }
                            @Override
                            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse){
                                if (errorResponse != null) {
                                    Intent intent = new Intent("enrollment-failure");
                                    intent.putExtra("JSON", errorResponse.toString());
                                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
                                    finish();
                                }
                            }
                        });
                    } else {
                        // Start enrollment
                        try {
                            mCamera.takePicture(null, null, mPicture);
                        } catch (Exception e) {
                            System.out.println("Camera exception : " + e.getMessage());
                            finish();
                        }
                    }
                } catch (JSONException e) {
                    System.out.println("JSON userId error: " + e.getMessage());
                    finish();
                }
            }
            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse){
                if (errorResponse != null) {
                    Intent intent = new Intent("enrollment-failure");
                    intent.putExtra("JSON", errorResponse.toString());
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
                    finish();
                }
            }
        });
    }
}