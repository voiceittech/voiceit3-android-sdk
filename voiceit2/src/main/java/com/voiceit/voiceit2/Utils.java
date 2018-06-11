package com.voiceit.voiceit2;

import android.content.pm.ActivityInfo;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.util.Random;

class Utils {
    private static final String mTAG = "Utils";

     /** Create a File for saving an image or audio file */
     public static File getOutputMediaFile(String suffix){
        try {
            return File.createTempFile("tempfile", suffix);
        } catch (IOException e) {
            Log.d(mTAG,"Creating file failed with exception : " + e.getMessage());
            return null;
        }
    }

    public static void randomizeArrayOrder(int size, int [] array) {
        final Random rand = new Random();
        for(int i = 0; i < size; i++) {
            int j = rand.nextInt(size -1);
            int temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
    }

    public static void startMediaRecorder(MediaRecorder mediaRecorder, File audioFile) {
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mediaRecorder.setAudioSamplingRate(44100);
        mediaRecorder.setAudioChannels(1);
        mediaRecorder.setAudioEncodingBitRate(16000);
        mediaRecorder.setOutputFile(audioFile.getAbsolutePath());
        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            Log.e(mTAG, "mMediaRecorder prepare failed");
        }
        mediaRecorder.start();
    }

    public static int lockOrientationCode(int code) {
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
}
