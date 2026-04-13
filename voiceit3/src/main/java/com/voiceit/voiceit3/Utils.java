package com.voiceit.voiceit3;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.pm.ActivityInfo;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.Surface;
import android.view.Window;
import android.view.WindowManager;

import java.io.File;
import java.io.IOException;
import java.util.Random;

class Utils {
    private static final String mTAG = "Utils";
    static int oldBrightness;
    static float luxThreshold = 50.0f;
    private static MediaPlayer mediaPlayer = new MediaPlayer();

    /** Create a File for saving an image or audio file */
     static File getOutputMediaFile(String suffix){
        try {
            return File.createTempFile("tempfile", suffix);
        } catch (IOException e) {
            Log.e(mTAG,"Creating " + suffix + " file failed with exception : " + e.getMessage());
            return null;
        }
    }

    /** Create a File for saving an image */
    static File getOutputMediaFile(String suffix, Activity mActivity){
        try {
            File file = new File(mActivity.getFilesDir() + "/" + File.separator + "pic.jpeg");
            file.createNewFile();
            return file;
        } catch (IOException e) {
            Log.e(mTAG,"Creating " + suffix + " file failed with exception : " + e.getMessage());
            return null;
        }
    }

    /** Create a File for saving an image or audio file */
    static File getOutputVideoFile(String suffix, Activity mActivity){
        try {
            File file = new File(mActivity.getFilesDir() + "/" + File.separator + "video.mp4");
            file.createNewFile();
            return file;
        } catch (IOException e) {
            Log.e(mTAG,"Creating " + suffix + " file failed with exception : " + e.getMessage());
            return null;
        }
    }

    /** Create a File for saving an image or audio file */
    static File getOutputAudioFile(String suffix, Activity mActivity){
        try {
            File file = new File(mActivity.getFilesDir() + "/" + File.separator + "audio.wav");
            file.createNewFile();
            return file;
        } catch (IOException e) {
            Log.e(mTAG,"Creating " + suffix + " file failed with exception : " + e.getMessage());
            return null;
        }
    }

    static void randomizeArrayOrder(int [] array) {
        final Random rand = new Random();
        for(int i = 0; i < array.length; i++) {
            int j = rand.nextInt(array.length -1);
            int temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
    }


    // Audio-only MediaRecorder for the voice flows. Video flows now use
    // CameraXBinder.startRecording instead of legacy MediaRecorder + Camera1.
    static void startMediaRecorder(MediaRecorder mediaRecorder, File audioFile) {
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mediaRecorder.setAudioSamplingRate(48000);
        mediaRecorder.setAudioChannels(1);
        mediaRecorder.setAudioEncodingBitRate(16000);
        mediaRecorder.setOutputFile(audioFile.getAbsolutePath());

        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            Log.e(mTAG, "MediaRecorder prepare failed");
        }
        mediaRecorder.start();
    }

    static int lockOrientationCode(int code) {
        switch (code) {
            case Surface.ROTATION_0:
                return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            case Surface.ROTATION_180:
                return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            case Surface.ROTATION_90:
                return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            case Surface.ROTATION_270:
                return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
            default:
                return 1;
        }
    }

    static boolean setBrightness(Activity activity, int brightness) {

        if(Build.VERSION.SDK_INT >= 23) {
            if (!Settings.System.canWrite(activity)) {
                return false;
            }
        }

        // Content resolver used as a handle to the system's settings
        ContentResolver cResolver = activity.getContentResolver();

        try {
            // To handle the auto
            Settings.System.putInt(cResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            // Get the current system brightness
            Utils.oldBrightness = Settings.System.getInt(cResolver, Settings.System.SCREEN_BRIGHTNESS);
            if (Utils.oldBrightness < 128){
                Utils.oldBrightness = 128;
            }
        } catch (Settings.SettingNotFoundException e) {
            Log.e("Error", "SettingNotFoundException: " + e.getMessage());
            return false;
        }

        // Set the system brightness using the brightness variable value
        Settings.System.putInt(cResolver, Settings.System.SCREEN_BRIGHTNESS, brightness);

        Window window = activity.getWindow();
        // Get the current window attributes
        WindowManager.LayoutParams layoutparams = window.getAttributes();
        // Set the brightness of this window
        layoutparams.screenBrightness = 1;
        // Apply attribute changes to this window
        window.setAttributes(layoutparams);

        return true;
    }

    public static void stripAudio(File audioVideoFile, File audioFile, AudioExtractionCompletion audioExtractionCompletion) {
        try {
            new AudioExtractor().genVideoUsingMuxer(audioVideoFile.getPath(), audioFile.getAbsolutePath(),-1,-1,true,false, audioExtractionCompletion);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
