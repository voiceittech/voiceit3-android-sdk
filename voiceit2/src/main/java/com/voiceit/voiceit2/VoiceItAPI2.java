package com.voiceit.voiceit2;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.loopj.android.http.*;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;

public class VoiceItAPI2 {
    private final AsyncHttpClient client;
    private final String apiKey;
    private final String apiToken;

    private final String mTAG = "VoiceItAPI2";
    private String BASE_URL = "https://api.voiceit.io";
    private String LIVENESS_URL = "https://liveness.voiceit.io";

    public boolean mDisplayPreviewFrame = false;

    public VoiceItAPI2(String apiKey, String apiToken) {
        this.apiKey = apiKey;
        this.apiToken = apiToken;
        this.client = new AsyncHttpClient();
        this.client.removeAllHeaders();
        this.client.setTimeout(30 * 1000);
        this.client.setBasicAuth(apiKey, apiToken);
        this.client.addHeader("platformId", "40");
        this.client.addHeader("platformVersion", BuildConfig.VERSION_NAME);
    }
    // TODO: add a constructor that also overrides the liveness URL constant
    public VoiceItAPI2(String apiKey, String apiToken, String url) {
        this.apiKey = apiKey;
        this.apiToken = apiToken;
        this.client = new AsyncHttpClient();
        this.client.removeAllHeaders();
        this.client.setTimeout(30 * 1000);
        this.client.setBasicAuth(apiKey, apiToken);
        this.client.addHeader("platformId", "40");
        this.client.addHeader("platformVersion", BuildConfig.VERSION_NAME);
        BASE_URL = url;
    }

    public void setURL(String url) {
      BASE_URL = url.replaceAll("\\s+","");
    }

    private String getAbsoluteUrl(String relativeUrl) {
        return BASE_URL + relativeUrl;
    }

    private String getAbsoluteLivenessUrl(String relativeUrl) {
        return LIVENESS_URL + relativeUrl;
    }

    public void getPhrases(String contentLanguage, AsyncHttpResponseHandler responseHandler) {
        client.get(getAbsoluteUrl("/phrases/" + contentLanguage), responseHandler);
    }

    public void getInitialLivenessData(String userID, String countryCode, String pageCategory, AsyncHttpResponseHandler responseHandler ) {
        if(!userIdFormatted(userID)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        client.get(getAbsoluteLivenessUrl("/"+pageCategory+"/"+userID+"/"+countryCode), responseHandler);
    }

    public void getAllUsers(AsyncHttpResponseHandler responseHandler) {
        client.get(getAbsoluteUrl("/users"), responseHandler);
    }

    public void createUser(AsyncHttpResponseHandler responseHandler) {
        client.post(getAbsoluteUrl("/users"), responseHandler);
    }

    public void checkUserExists(String userId, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        client.get(getAbsoluteUrl("/users/" + userId), responseHandler);
    }

    public void deleteUser(String userId, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        client.delete(getAbsoluteUrl("/users/" + userId), responseHandler);
    }

    public void getGroupsForUser(String userId, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        client.get(getAbsoluteUrl("/users/" + userId + "/groups"), responseHandler);
    }

    public void deleteAllEnrollments(String userId, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        client.delete(getAbsoluteUrl("/enrollments/" + userId + "/all"), responseHandler);
    }

    public void getAllVoiceEnrollments(String userId, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        client.get(getAbsoluteUrl("/enrollments/voice/" + userId), responseHandler);
    }

    public void getAllFaceEnrollments(String userId, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        client.get(getAbsoluteUrl("/enrollments/face/" + userId), responseHandler);
    }

    public void getAllVideoEnrollments(String userId, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        client.get(getAbsoluteUrl("/enrollments/video/" + userId), responseHandler);
    }

    public void processLivenessFace(String userId,  String phrase, String videoPath, String lcoId,  AsyncHttpResponseHandler responseHandler) {
        processLivenessFace(userId, new File(videoPath), lcoId, responseHandler);
    }

    public void processLivenessFace(String userId, File video, String lcoId, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        params.put("lcoId", lcoId);
        try {
            params.put("file", video);
        } catch (FileNotFoundException e) {
            Log.e(mTAG, "FileNotFoundException: " + e.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        client.post(getAbsoluteLivenessUrl("/face"), params, responseHandler);
    }

    public void processLivenessVideo(String userId,  String phrase, String videoPath, String lcoId,  AsyncHttpResponseHandler responseHandler) {
        processLivenessVideo(userId, phrase, new File(videoPath), lcoId, responseHandler);
    }

    public void processLivenessVideo(String userId, String phrase, File video, String lcoId, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        params.put("lcoId", lcoId);
        params.put("phrase", phrase);
        try {
            params.put("file", video);
        } catch (FileNotFoundException e) {
            Log.e(mTAG, "FileNotFoundException: " + e.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        client.post(getAbsoluteLivenessUrl("/video"), params, responseHandler);
    }

    public void createVoiceEnrollment(String userId, String contentLanguage, String phrase, String recordingPath, AsyncHttpResponseHandler responseHandler) {
        createVoiceEnrollment(userId, contentLanguage, phrase, new File(recordingPath), responseHandler);
    }

    public void createVoiceEnrollment(String userId, String contentLanguage, String phrase, File recording, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        params.put("contentLanguage", contentLanguage);
        params.put("phrase", phrase);
        try {
            params.put("recording", recording);
        } catch (FileNotFoundException e) {
            Log.e(mTAG, "FileNotFoundException: " + e.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        client.post(getAbsoluteUrl("/enrollments/voice"), params, responseHandler);
    }

    public void createVoiceEnrollment(final String userId, final String contentLanguage, final String phrase, final AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        try{
            final File recordingFile =  File.createTempFile("tempEnrollmentFile", ".wav");
            final MediaRecorder myRecorder = new MediaRecorder();
            Utils.startMediaRecorder(myRecorder, recordingFile);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    myRecorder.stop();
                    myRecorder.reset();
                    myRecorder.release();
                    createVoiceEnrollment(userId, contentLanguage, phrase, recordingFile, responseHandler);
                }
            }, 4800);
        }
        catch (Exception ex) {
            Log.e(mTAG,"Recording Exception: " + ex.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
        }
    }

    public void createVoiceEnrollmentByUrl(String userId, String contentLanguage, String phrase, String fileUrl, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        params.put("contentLanguage", contentLanguage);
        params.put("phrase", phrase);
        params.put("fileUrl", fileUrl);

        client.post(getAbsoluteUrl("/enrollments/voice/byUrl"), params, responseHandler);
    }

    public void createFaceEnrollment(String userId, String videoPath, AsyncHttpResponseHandler responseHandler) {
        createFaceEnrollment(userId, new File(videoPath), responseHandler);
    }

    public void createFaceEnrollment(String userId, File video, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        try {
            params.put("video", video);
        } catch (FileNotFoundException e) {
            Log.e(mTAG, "FileNotFoundException: " + e.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        client.post(getAbsoluteUrl("/enrollments/face"), params, responseHandler);
    }

    public void createFaceEnrollmentWithPhoto(String userId, String photoPath, AsyncHttpResponseHandler responseHandler) {
        createFaceEnrollmentWithPhoto(userId, new File(photoPath), responseHandler);
    }

    public void createFaceEnrollmentWithPhoto(String userId, File photo, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        try {
            params.put("photo", photo);
        } catch (FileNotFoundException e) {
            Log.e(mTAG, "FileNotFoundException: " + e.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        client.post(getAbsoluteUrl("/enrollments/face"), params, responseHandler);
    }

    public void createFaceEnrollmentByUrl(String userId, String fileUrl, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        params.put("fileUrl", fileUrl);

        client.post(getAbsoluteUrl("/enrollments/face/byUrl"), params, responseHandler);
    }

    public void createVideoEnrollment(String userId, String contentLanguage, String phrase, File audio, File photo, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        params.put("contentLanguage", contentLanguage);
        params.put("phrase", phrase);
        try {
            params.put("audio", audio);
            params.put("photo", photo);
        } catch (FileNotFoundException e) {
            Log.e(mTAG, "FileNotFoundException: " + e.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        client.post(getAbsoluteUrl("/enrollments/video"), params, responseHandler);
    }

    public void createVideoEnrollment(String userId, String contentLanguage, String phrase,String videoPath, AsyncHttpResponseHandler responseHandler) {
        createVideoEnrollment(userId, contentLanguage, phrase, new File(videoPath), responseHandler);
    }

    public void createVideoEnrollment(String userId, String contentLanguage, String phrase, File video, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        params.put("contentLanguage", contentLanguage);
        params.put("phrase", phrase);
        try {
            params.put("video", video);
        } catch (FileNotFoundException e) {
            Log.e(mTAG, "FileNotFoundException: " + e.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        client.post(getAbsoluteUrl("/enrollments/video"), params, responseHandler);
    }

    public void createVideoEnrollmentByUrl(String userId, String contentLanguage, String phrase, String fileUrl, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        params.put("contentLanguage", contentLanguage);
        params.put("phrase", phrase);
        params.put("fileUrl", fileUrl);

        client.post(getAbsoluteUrl("/enrollments/video/byUrl"), params, responseHandler);

    }

    public void getAllGroups(AsyncHttpResponseHandler responseHandler) {
        client.get(getAbsoluteUrl("/groups"), responseHandler);
    }

    public void getGroup(String groupId, AsyncHttpResponseHandler responseHandler) {
        if(!groupIdFormatted(groupId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        client.get(getAbsoluteUrl("/groups/" + groupId), responseHandler);
    }

    public void groupExists(String groupId, AsyncHttpResponseHandler responseHandler) {
        if(!groupIdFormatted(groupId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        client.get(getAbsoluteUrl("/groups/" + groupId + "/exists"), responseHandler);
    }

    public void createGroup(String description, AsyncHttpResponseHandler responseHandler) {
        RequestParams params = new RequestParams();
        params.put("description", description);
        client.post(getAbsoluteUrl("/groups"), params, responseHandler);
    }

    public void addUserToGroup(String groupId, String userId, AsyncHttpResponseHandler responseHandler) {
        if(!groupIdFormatted(groupId) || !userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("groupId", groupId);
        params.put("userId", userId);
        client.put(getAbsoluteUrl("/groups/addUser"), params, responseHandler);
    }

    public void removeUserFromGroup(String groupId, String userId, AsyncHttpResponseHandler responseHandler) {
        if(!groupIdFormatted(groupId) || !userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("groupId", groupId);
        params.put("userId", userId);
        client.put(getAbsoluteUrl("/groups/removeUser"), params, responseHandler);
    }

    public void deleteGroup(String groupId, AsyncHttpResponseHandler responseHandler) {
        if(!groupIdFormatted(groupId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        client.delete(getAbsoluteUrl("/groups/" + groupId), responseHandler);
    }

    public void voiceVerification(String userId, String contentLanguage, String phrase, String recordingPath, AsyncHttpResponseHandler responseHandler) {
        voiceVerification(userId, contentLanguage,  phrase, new File(recordingPath), responseHandler);
    }

    public void voiceVerification(String userId, String contentLanguage, String phrase, File recording, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        params.put("contentLanguage", contentLanguage);
        params.put("phrase", phrase);
        try {
            params.put("recording", recording);
        } catch (FileNotFoundException e) {
            Log.e(mTAG, "FileNotFoundException: " + e.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        client.post(getAbsoluteUrl("/verification/voice"), params, responseHandler);
    }

    public void voiceVerification(final String userId, final String contentLanguage, final String phrase, final AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        try{
            final File recordingFile =  File.createTempFile("tempEnrollmentFile", ".wav");
            final MediaRecorder myRecorder = new MediaRecorder();
            Utils.startMediaRecorder(myRecorder, recordingFile);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                myRecorder.stop();
                myRecorder.reset();
                myRecorder.release();
                voiceVerification(userId, contentLanguage, phrase, recordingFile, responseHandler);
                }
            }, 4800);
        }
        catch (Exception ex) {
            Log.e(mTAG,"Recording Error: " + ex.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
        }
    }

    public void voiceVerificationByUrl(String userId, String contentLanguage, String phrase, String fileUrl, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        params.put("contentLanguage", contentLanguage);
        params.put("phrase", phrase);
        params.put("fileUrl", fileUrl);

        client.post(getAbsoluteUrl("/verification/voice/byUrl"), params, responseHandler);
    }

    public void faceVerification(String userId, String videoPath, AsyncHttpResponseHandler responseHandler) {
        faceVerification(userId, new File(videoPath), responseHandler);
    }

    public void faceVerification(String userId, String contentLanguage, File video, AsyncHttpResponseHandler responseHandler, String lcoId){
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        params.put("lcoId", lcoId);
        params.put("contentLanguage", contentLanguage);
        try {
            params.put("file", video);
        } catch (FileNotFoundException e) {
            Log.e(mTAG, "FileNotFoundException: " + e.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        client.post(getAbsoluteLivenessUrl("/verification/face"), params, responseHandler);
    }

    public void faceVerification(String userId, File video, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        try {
            params.put("video", video);
        } catch (FileNotFoundException e) {
            Log.e(mTAG, "FileNotFoundException: " + e.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        client.post(getAbsoluteUrl("/verification/face"), params, responseHandler);
    }


    public void faceVerificationWithPhoto(String userId, String photoPath, AsyncHttpResponseHandler responseHandler) {
        faceVerificationWithPhoto(userId, new File(photoPath), responseHandler);
    }

    public void faceVerificationWithPhoto(String userId, File photo, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        try {
            params.put("photo", photo);
        } catch (FileNotFoundException e) {
            Log.e(mTAG, "FileNotFoundException: " + e.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        client.post(getAbsoluteUrl("/verification/face"), params, responseHandler);
    }

    public void faceVerificationByUrl(String userId, String fileUrl, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        params.put("fileUrl", fileUrl);

        client.post(getAbsoluteUrl("/verification/face/byUrl"), params, responseHandler);
    }

    public void videoVerification(String userId, String contentLanguage, String phrase, String videoPath, AsyncHttpResponseHandler responseHandler) {
        videoVerification(userId, contentLanguage, phrase, new File(videoPath), responseHandler);
    }

    public void videoVerification(String userId, String contentLanguage, String phrase, File video, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        params.put("contentLanguage", contentLanguage);
        params.put("phrase", phrase);
        try {
            params.put("video", video);
        } catch (FileNotFoundException e) {
            Log.e(mTAG, "FileNotFoundException: " + e.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        client.post(getAbsoluteUrl("/verification/video"), params, responseHandler);
    }

    public void videoVerification(String userId, String contentLanguage, String phrase, File video, AsyncHttpResponseHandler responseHandler, String lcoId) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        params.put("contentLanguage", contentLanguage);
        params.put("phrase", phrase);
        params.put("lcoId", lcoId);
        try {
            params.put("file", video);
        } catch (FileNotFoundException e) {
            Log.e(mTAG, "FileNotFoundException: " + e.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        String url = getAbsoluteLivenessUrl("/verification/video");
        client.post(url, params, responseHandler);
    }

    public void videoVerification(String userId, String contentLanguage, String phrase, File audio, File photo, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        params.put("contentLanguage", contentLanguage);
        params.put("phrase", phrase);
        try {
            params.put("audio", audio);
            params.put("photo", photo);
        } catch (FileNotFoundException e) {
            Log.e(mTAG, "FileNotFoundException: " + e.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        client.post(getAbsoluteUrl("/verification/video"), params, responseHandler);
    }

    public void videoVerificationByUrl(String userId, String contentLanguage, String phrase, String fileUrl, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        params.put("contentLanguage", contentLanguage);
        params.put("phrase", phrase);
        params.put("fileUrl", fileUrl);

        client.post(getAbsoluteUrl("/verification/video/byUrl"), params, responseHandler);
    }

    public void voiceIdentification(String groupId, String contentLanguage, String phrase, String recordingPath, AsyncHttpResponseHandler responseHandler) {
        voiceIdentification(groupId, contentLanguage, phrase, new File(recordingPath), responseHandler);
    }

    public void voiceIdentification(String groupId, String contentLanguage, String phrase, File recording, AsyncHttpResponseHandler responseHandler) {
        if(!groupIdFormatted(groupId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("groupId", groupId);
        params.put("contentLanguage", contentLanguage);
        params.put("phrase", phrase);
        try {
            params.put("recording", recording);
        } catch (FileNotFoundException e) {
            Log.e(mTAG, "FileNotFoundException: " + e.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        client.post(getAbsoluteUrl("/identification/voice"), params, responseHandler);
    }

    public void voiceIdentification(final String groupId, final String contentLanguage, final String phrase, final AsyncHttpResponseHandler responseHandler) {
        if(!groupIdFormatted(groupId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        try{
            final File recordingFile =  File.createTempFile("tempEnrollmentFile", ".wav");
            final MediaRecorder myRecorder = new MediaRecorder();
            Utils.startMediaRecorder(myRecorder, recordingFile);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    myRecorder.stop();
                    myRecorder.reset();
                    myRecorder.release();
                    voiceIdentification(groupId, contentLanguage, phrase, recordingFile, responseHandler);
                }
            }, 4800);
        }
        catch (Exception ex) {
            Log.e(mTAG,"Recording Exception:" + ex.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
        }
    }

    public void voiceIdentificationByUrl(String groupId, String contentLanguage, String phrase, String fileUrl, AsyncHttpResponseHandler responseHandler) {
        if(!groupIdFormatted(groupId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("groupId", groupId);
        params.put("contentLanguage", contentLanguage);
        params.put("phrase", phrase);
        params.put("fileUrl", fileUrl);

        client.post(getAbsoluteUrl("/identification/voice/byUrl"), params, responseHandler);
    }

    public void videoIdentification(String groupId, String contentLanguage, String phrase, String videoPath, AsyncHttpResponseHandler responseHandler) {
        videoIdentification(groupId, contentLanguage, phrase, new File(videoPath), responseHandler);
    }

    public void videoIdentification(String groupId, String contentLanguage, String phrase, File video, AsyncHttpResponseHandler responseHandler) {
        if(!groupIdFormatted(groupId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("groupId", groupId);
        params.put("contentLanguage", contentLanguage);
        params.put("phrase", phrase);
        try {
            params.put("video", video);
        } catch (FileNotFoundException e) {
            Log.e(mTAG, "FileNotFoundException: " + e.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        client.post(getAbsoluteUrl("/identification/video"), params, responseHandler);
    }

    public void videoIdentificationWithPhoto(String groupId, String contentLanguage, String phrase, String audioPath, String photoPath, AsyncHttpResponseHandler responseHandler) {
        videoIdentificationWithPhoto(groupId, contentLanguage, phrase, new File(audioPath), new File(photoPath), responseHandler);
    }

    public void videoIdentificationWithPhoto(String groupId, String contentLanguage, String phrase, File audio, File photo, AsyncHttpResponseHandler responseHandler) {
        if(!groupIdFormatted(groupId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("groupId", groupId);
        params.put("contentLanguage", contentLanguage);
        params.put("phrase", phrase);
        try {
            params.put("audio", audio);
            params.put("photo", photo);
        } catch (FileNotFoundException e) {
            Log.e(mTAG, "FileNotFoundException: " + e.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        client.post(getAbsoluteUrl("/identification/video"), params, responseHandler);
    }

    public void videoIdentificationByUrl(String groupId, String contentLanguage, String phrase, String fileUrl, AsyncHttpResponseHandler responseHandler) {
        if(!groupIdFormatted(groupId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("groupId", groupId);
        params.put("contentLanguage", contentLanguage);
        params.put("phrase", phrase);
        params.put("fileUrl", fileUrl);

        client.post(getAbsoluteUrl("/identification/video/byUrl"), params, responseHandler);
    }

    public void faceIdentification(String groupId, String videoPath, AsyncHttpResponseHandler responseHandler) {
        faceIdentification(groupId, new File(videoPath), responseHandler);
    }

    public void faceIdentification(String groupId, File video, AsyncHttpResponseHandler responseHandler) {
        if(!groupIdFormatted(groupId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("groupId", groupId);
        try {
            params.put("video", video);
        } catch (FileNotFoundException e) {
            Log.e(mTAG, "FileNotFoundException: " + e.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        client.post(getAbsoluteUrl("/identification/face"), params, responseHandler);
    }

    public void faceIdentificationWithPhoto(String groupId, String photoPath, AsyncHttpResponseHandler responseHandler) {
        faceIdentificationWithPhoto(groupId, new File(photoPath), responseHandler);
    }

    public void faceIdentificationWithPhoto(String groupId, File photo, AsyncHttpResponseHandler responseHandler) {
        if(!groupIdFormatted(groupId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("groupId", groupId);
        try {
            params.put("photo", photo);
        } catch (FileNotFoundException e) {
            Log.e(mTAG, "FileNotFoundException: " + e.getMessage());
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        client.post(getAbsoluteUrl("/identification/face"), params, responseHandler);
    }

    public void faceIdentificationByUrl(String groupId, String fileUrl, AsyncHttpResponseHandler responseHandler) {
        if(!groupIdFormatted(groupId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("groupId", groupId);
        params.put("fileUrl", fileUrl);

        client.post(getAbsoluteUrl("/identification/face/byUrl"), params, responseHandler);
    }

    public void encapsulatedVoiceEnrollment(Activity activity, String userId, String contentLanguage, String phrase, final JsonHttpResponseHandler responseHandler) {
        if (!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        Intent intent = new Intent(activity, VoiceEnrollmentView.class);
        Bundle bundle = new Bundle();
        bundle.putString("apiKey", this.apiKey);
        bundle.putString("apiToken", this.apiToken);
        bundle.putString("userId", userId);
        bundle.putString("contentLanguage", contentLanguage);
        bundle.putString("phrase", phrase);
        intent.putExtras(bundle);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);

        broadcastMessageHandler(activity, responseHandler);
    }

    public void encapsulatedVoiceVerification(Activity activity, String userId, String contentLanguage, String phrase, final JsonHttpResponseHandler responseHandler) {
        if (!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        Intent intent = new Intent(activity, VoiceVerificationView.class);
        Bundle bundle = new Bundle();
        bundle.putString("apiKey", this.apiKey);
        bundle.putString("apiToken", this.apiToken);
        bundle.putString("userId", userId);
        bundle.putString("contentLanguage", contentLanguage);
        bundle.putString("phrase", phrase);
        intent.putExtras(bundle);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);

        broadcastMessageHandler(activity, responseHandler);
    }

    public void encapsulatedVoiceIdentification(Activity activity, String groupId, String contentLanguage, String phrase, final JsonHttpResponseHandler responseHandler) {
        if (!groupIdFormatted(groupId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        Intent intent = new Intent(activity, VoiceIdentificationView.class);
        Bundle bundle = new Bundle();
        bundle.putString("apiKey", this.apiKey);
        bundle.putString("apiToken", this.apiToken);
        bundle.putString("groupId", groupId);
        bundle.putString("contentLanguage", contentLanguage);
        bundle.putString("phrase", phrase);
        intent.putExtras(bundle);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);

        broadcastMessageHandler(activity, responseHandler);
    }

    public void encapsulatedVideoEnrollment(Activity activity, String userId, String contentLanguage, String phrase, final JsonHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        Intent intent = new Intent(activity, VideoEnrollmentView.class);
        Bundle bundle = new Bundle();
        bundle.putString("apiKey", this.apiKey);
        bundle.putString("apiToken", this.apiToken);
        bundle.putString("userId", userId);
        bundle.putString("contentLanguage", contentLanguage);
        bundle.putString("phrase", phrase);
        bundle.putBoolean("displayPreviewFrame", mDisplayPreviewFrame);
        intent.putExtras(bundle);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);

        broadcastMessageHandler(activity, responseHandler);

        requestWritePermission(activity);
    }

    public void encapsulatedVideoVerification(Activity activity, String userId, String contentLanguage, String phrase, boolean doLivenessCheck, boolean doLivenessAudioCheck, final JsonHttpResponseHandler responseHandler) {
        encapsulatedVideoVerification(doLivenessAudioCheck,activity, userId, contentLanguage, phrase, doLivenessCheck, 0, 2, responseHandler);
    }

    public void encapsulatedVideoVerification(boolean doLivenessAudioCheck, Activity activity, String userId, String contentLanguage, String phrase, boolean doLivenessCheck, int livenessChallengeFailsAllowed, int livenessChallengesNeeded, final JsonHttpResponseHandler responseHandler) {
        if (!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        Intent intent = new Intent(activity, VideoVerificationView.class);
        Bundle bundle = new Bundle();
        bundle.putString("apiKey", this.apiKey);
        bundle.putString("apiToken", this.apiToken);
        bundle.putString("userId", userId);
        bundle.putString("contentLanguage", contentLanguage);
        bundle.putString("phrase", phrase);
        bundle.putBoolean("doLivenessCheck", doLivenessCheck);
        bundle.putInt("livenessChallengeFailsAllowed", livenessChallengeFailsAllowed);
        bundle.putInt("livenessChallengesNeeded", livenessChallengesNeeded);
        bundle.putBoolean("displayPreviewFrame", mDisplayPreviewFrame);
        bundle.putBoolean("doLivenessAudioCheck",doLivenessAudioCheck);
        intent.putExtras(bundle);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);

        broadcastMessageHandler(activity, responseHandler);

        requestWritePermission(activity);
    }

    public void encapsulatedVideoIdentification(Activity activity, String groupId, String contentLanguage, String phrase, boolean doLivenessCheck, final JsonHttpResponseHandler responseHandler) {
        encapsulatedVideoIdentification(activity, groupId, contentLanguage, phrase, doLivenessCheck, 0, 2, responseHandler);
    }

    public void encapsulatedVideoIdentification(Activity activity, String groupId, String contentLanguage, String phrase, boolean doLivenessCheck, int livenessChallengeFailsAllowed, int livenessChallengesNeeded, final JsonHttpResponseHandler responseHandler) {
        if (!groupIdFormatted(groupId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        Intent intent = new Intent(activity, VideoIdentificationView.class);
        Bundle bundle = new Bundle();
        bundle.putString("apiKey", this.apiKey);
        bundle.putString("apiToken", this.apiToken);
        bundle.putString("groupId", groupId);
        bundle.putString("contentLanguage", contentLanguage);
        bundle.putString("phrase", phrase);
        bundle.putBoolean("doLivenessCheck", doLivenessCheck);
        bundle.putInt("livenessChallengeFailsAllowed", livenessChallengeFailsAllowed);
        bundle.putInt("livenessChallengesNeeded", livenessChallengesNeeded);
        bundle.putBoolean("displayPreviewFrame", mDisplayPreviewFrame);
        intent.putExtras(bundle);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);

        broadcastMessageHandler(activity, responseHandler);

        requestWritePermission(activity);
    }

    public void encapsulatedFaceEnrollment(Activity activity, String userId, final JsonHttpResponseHandler responseHandler) {
        if (!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        Intent intent = new Intent(activity, FaceEnrollmentView.class);
        Bundle bundle = new Bundle();
        bundle.putString("apiKey", this.apiKey);
        bundle.putString("apiToken", this.apiToken);
        bundle.putString("userId", userId);
        bundle.putBoolean("displayPreviewFrame", mDisplayPreviewFrame);
        intent.putExtras(bundle);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);

        broadcastMessageHandler(activity, responseHandler);

        requestWritePermission(activity);
    }

    public void encapsulatedFaceVerification(Activity activity, String userId, String countryCode, boolean doLivenessCheck, boolean doLivenessAudioCheck, final JsonHttpResponseHandler responseHandler) {
        encapsulatedFaceVerification(activity, userId, countryCode, doLivenessCheck, doLivenessAudioCheck, 0, 2, responseHandler);
    }

    public void encapsulatedFaceVerification(Activity activity, String userId, String countryCode, boolean doLivenessCheck, boolean doLivenessAudioCheck, int livenessChallengeFailsAllowed, int livenessChallengesNeeded, final JsonHttpResponseHandler responseHandler) {
        if (!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        Intent intent = new Intent(activity, FaceVerificationView.class);
        Bundle bundle = new Bundle();
        bundle.putString("apiKey", this.apiKey);
        bundle.putString("apiToken", this.apiToken);
        bundle.putString("userId", userId);
        bundle.putString("contentLanguage", countryCode);
        bundle.putBoolean("doLivenessCheck", doLivenessCheck);
        bundle.putBoolean("doLivenessAudioCheck", doLivenessAudioCheck);
        bundle.putInt("livenessChallengeFailsAllowed", livenessChallengeFailsAllowed);
        bundle.putInt("livenessChallengesNeeded", livenessChallengesNeeded);
        bundle.putBoolean("displayPreviewFrame", mDisplayPreviewFrame);
        intent.putExtras(bundle);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);

        broadcastMessageHandler(activity, responseHandler);

        requestWritePermission(activity);
    }

    public void encapsulatedFaceIdentification(Activity activity, String groupId, boolean doLivenessCheck, final JsonHttpResponseHandler responseHandler) {
        encapsulatedFaceIdentification(activity, groupId, doLivenessCheck, 0, 2, responseHandler);
    }

    public void encapsulatedFaceIdentification(Activity activity, String groupId, boolean doLivenessCheck, int livenessChallengeFailsAllowed, int livenessChallengesNeeded, final JsonHttpResponseHandler responseHandler) {
        if (!groupIdFormatted(groupId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        Intent intent = new Intent(activity, FaceIdentificationView.class);
        Bundle bundle = new Bundle();
        bundle.putString("apiKey", this.apiKey);
        bundle.putString("apiToken", this.apiToken);
        bundle.putString("groupId", groupId);
        bundle.putBoolean("doLivenessCheck", doLivenessCheck);
        bundle.putInt("livenessChallengeFailsAllowed", livenessChallengeFailsAllowed);
        bundle.putInt("livenessChallengesNeeded", livenessChallengesNeeded);
        bundle.putBoolean("displayPreviewFrame", mDisplayPreviewFrame);
        intent.putExtras(bundle);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);

        broadcastMessageHandler(activity, responseHandler);

        requestWritePermission(activity);
    }

    private void broadcastMessageHandler(final Activity activity, final JsonHttpResponseHandler responseHandler) {
        // Our handler for received Intents. This will be called whenever an Intent
        // with an action named "voiceit-event" is broad-casted.
        BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
            boolean broadcastTriggered = false;

            @Override
            public void onReceive(Context context, Intent intent) {
                if (!broadcastTriggered) {
                    broadcastTriggered = true;
                    // Get extra data included in the Intent
                    String Response = intent.getStringExtra("Response");

                    if (intent.getAction().equals("voiceit-success")) {
                        responseHandler.sendSuccessMessage(200, null, Response.getBytes());
                    }
                    if (intent.getAction().equals("voiceit-failure")) {
                        responseHandler.sendFailureMessage(200, null, Response.getBytes(), new Throwable());
                    }
                }
            }
        };

        // Register observers (mMessageReceiver) to receive Intents with named actions
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("voiceit-success");
        intentFilter.addAction("voiceit-failure");
        LocalBroadcastManager.getInstance(activity).registerReceiver(mMessageReceiver, intentFilter);
    }

    private void requestWritePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= 23) {
            if (!Settings.System.canWrite(activity)) {
                Toast.makeText(activity, activity.getString(R.string.GRANT_WRITE_PERMISSON), Toast.LENGTH_LONG).show();
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
            }
        }
    }

    private boolean userIdFormatted(String arg) {
        String id = arg.substring(arg.lastIndexOf('_') + 1);
        if (!id.matches("[A-Za-z0-9]+")
                || !arg.substring(0, 3).equals("usr")
                || id.length() != 32) {
             Log.e(mTAG,"UserId does not meet requirements, " +
                     "please ensure it is your user's 36 character alphanumeric string generated " +
                     "from the createUser API call");
            return false;
        }
        return true;
    }

    private boolean groupIdFormatted(String arg) {
        String id = arg.substring(arg.lastIndexOf('_') + 1);
        if (!id.matches("[A-Za-z0-9]+")
                || !arg.substring(0, 3).equals("grp")
                || id.length() != 32) {
             Log.e(mTAG,"GroupId does not meet requirements, " +
                     "please ensure it is your group's 36 character alphanumeric string generated " +
                     "from the createGroup API call");
            return false;
        }
        return true;
    }

    private JSONObject buildJSONFormatMessage() {
        JSONObject json = new JSONObject();
        try {
            json.put("message", "Incorrectly formatted id argument. Check log output for more information");
        } catch (JSONException e) {
            Log.e(mTAG,"JSON Exception : " + e.getMessage());
        }
        return json;
    }
}
