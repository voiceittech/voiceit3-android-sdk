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
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.util.JsonUtils;
import com.loopj.android.http.*;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

import java.io.File;
import java.io.FileNotFoundException;

public class VoiceItAPI2 {
    private final AsyncHttpClient client;
    private final String apiKey;
    private final String apiToken;
    private String notificationURL;
    private int voiceitThemeColor = 0;

    private final String mTAG = "VoiceItAPI2";
    private String BASE_URL = "https://api.voiceit.io";
    private String LIVENESS_URL = "https://liveness.voiceit.io/v1";

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

    public VoiceItAPI2(String apiKey, String apiToken, int voiceitThemeColor) {
        this.apiKey = apiKey;
        this.apiToken = apiToken;
        this.client = new AsyncHttpClient();
        this.client.removeAllHeaders();
        this.client.setTimeout(30 * 1000);
        this.client.setBasicAuth(apiKey, apiToken);
        this.client.addHeader("platformId", "40");
        this.client.addHeader("platformVersion", BuildConfig.VERSION_NAME);
        this.voiceitThemeColor = voiceitThemeColor;
    }

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
        client.get(getAbsoluteUrl("/phrases/" + contentLanguage + "?notificationURL=" + this.notificationURL), responseHandler);
    }

    public void setNotificationURL(String notificationUrl) {
        this.notificationURL = notificationUrl;
    }

    public void getInitialLivenessData(String userID, String contentLanguage, String pageCategory, AsyncHttpResponseHandler responseHandler ) {
        if(!userIdFormatted(userID)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        client.get(getAbsoluteLivenessUrl("/"+pageCategory+"/"+userID+"/"+contentLanguage + "?notificationURL=" + this.notificationURL), responseHandler);
    }

    public void getAllUsers(AsyncHttpResponseHandler responseHandler) {
        RequestParams params = new RequestParams();

        client.get(getAbsoluteUrl("/users" + "?notificationURL=" + this.notificationURL), params, responseHandler);
    }

    public void createUser(AsyncHttpResponseHandler responseHandler) {
        RequestParams params = new RequestParams();

        client.post(getAbsoluteUrl("/users" + "?notificationURL=" + this.notificationURL), params, responseHandler);
    }

    public void checkUserExists(String userId, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        client.get(getAbsoluteUrl("/users/" + userId + "?notificationURL=" + this.notificationURL), responseHandler);
    }

    public void deleteUser(String userId, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        client.delete(getAbsoluteUrl("/users/" + userId + "?notificationURL=" + this.notificationURL), responseHandler);
    }

    public void getGroupsForUser(String userId, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        client.get(getAbsoluteUrl("/users/" + userId + "/groups" + "?notificationURL=" + this.notificationURL), responseHandler);
    }

    public void deleteAllEnrollments(String userId, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }

        client.delete(getAbsoluteUrl("/enrollments/" + userId + "/all" + "?notificationURL=" + this.notificationURL), responseHandler);
    }

    public void getAllVoiceEnrollments(String userId, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        client.get(getAbsoluteUrl("/enrollments/voice/" + userId + "?notificationURL=" + this.notificationURL), responseHandler);
    }

    public void getAllFaceEnrollments(String userId, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("notificationURL", this.notificationURL);

        client.get(getAbsoluteUrl("/enrollments/face/" + userId + "?notificationURL=" + this.notificationURL), params, responseHandler);
    }

    public void getAllVideoEnrollments(String userId, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        client.get(getAbsoluteUrl("/enrollments/video/" + userId + "?notificationURL=" + this.notificationURL), responseHandler);
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

        client.post(getAbsoluteLivenessUrl("/face" + "?notificationURL=" + this.notificationURL), params, responseHandler);
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

        client.post(getAbsoluteLivenessUrl("/video" + "?notificationURL=" + this.notificationURL), params, responseHandler);
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

        client.post(getAbsoluteUrl("/enrollments/voice" + "?notificationURL=" + this.notificationURL), params, responseHandler);
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

        client.post(getAbsoluteUrl("/enrollments/voice/byUrl" + "?notificationURL=" + this.notificationURL), params, responseHandler);
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

        client.post(getAbsoluteUrl("/enrollments/face" + "?notificationURL=" + this.notificationURL), params, responseHandler);
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

        client.post(getAbsoluteUrl("/enrollments/face" + "?notificationURL=" + this.notificationURL), params, responseHandler);
    }

    public void createFaceEnrollmentByUrl(String userId, String fileUrl, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        params.put("fileUrl", fileUrl);

        client.post(getAbsoluteUrl("/enrollments/face/byUrl" + "?notificationURL=" + this.notificationURL), params, responseHandler);
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

        client.post(getAbsoluteUrl("/enrollments/video" + "?notificationURL=" + this.notificationURL), params, responseHandler);
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

        client.post(getAbsoluteUrl("/enrollments/video" + "?notificationURL=" + this.notificationURL), params, responseHandler);
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

        client.post(getAbsoluteUrl("/enrollments/video/byUrl" + "?notificationURL=" + this.notificationURL), params, responseHandler);

    }

    public void getAllGroups(AsyncHttpResponseHandler responseHandler) {
        client.get(getAbsoluteUrl("/groups" + "?notificationURL=" + this.notificationURL), responseHandler);
    }

    public void getGroup(String groupId, AsyncHttpResponseHandler responseHandler) {
        if(!groupIdFormatted(groupId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        client.get(getAbsoluteUrl("/groups/" + groupId + "?notificationURL=" + this.notificationURL), responseHandler);
    }

    public void groupExists(String groupId, AsyncHttpResponseHandler responseHandler) {
        if(!groupIdFormatted(groupId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        client.get(getAbsoluteUrl("/groups/" + groupId + "/exists" + "?notificationURL=" + this.notificationURL), responseHandler);
    }

    public void createGroup(String description, AsyncHttpResponseHandler responseHandler) {
        RequestParams params = new RequestParams();
        params.put("description", description);

        client.post(getAbsoluteUrl("/groups" + "?notificationURL=" + this.notificationURL), params, responseHandler);
    }

    public void addUserToGroup(String groupId, String userId, AsyncHttpResponseHandler responseHandler) {
        if(!groupIdFormatted(groupId) || !userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("groupId", groupId);
        params.put("userId", userId);

        client.put(getAbsoluteUrl("/groups/addUser" + "?notificationURL=" + this.notificationURL), params, responseHandler);
    }

    public void removeUserFromGroup(String groupId, String userId, AsyncHttpResponseHandler responseHandler) {
        if(!groupIdFormatted(groupId) || !userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("groupId", groupId);
        params.put("userId", userId);

        client.put(getAbsoluteUrl("/groups/removeUser" + "?notificationURL=" + this.notificationURL), params, responseHandler);
    }

    public void deleteGroup(String groupId, AsyncHttpResponseHandler responseHandler) {
        if(!groupIdFormatted(groupId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        client.delete(getAbsoluteUrl("/groups/" + groupId + "?notificationURL=" + this.notificationURL), responseHandler);
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
        client.post(getAbsoluteUrl("/verification/voice" + "?notificationURL=" + this.notificationURL), params, responseHandler);
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

        client.post(getAbsoluteUrl("/verification/voice/byUrl" + "?notificationURL=" + this.notificationURL), params, responseHandler);
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

        client.post(getAbsoluteLivenessUrl("/verification/face" + "?notificationURL=" + this.notificationURL), params, responseHandler);
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

        client.post(getAbsoluteUrl("/verification/face" + "?notificationURL=" + this.notificationURL), params, responseHandler);
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


        client.post(getAbsoluteUrl("/verification/face" + "?notificationURL=" + this.notificationURL), params, responseHandler);
    }

    public void faceVerificationByUrl(String userId, String fileUrl, AsyncHttpResponseHandler responseHandler) {
        if(!userIdFormatted(userId)) {
            responseHandler.sendFailureMessage(200, null, buildJSONFormatMessage().toString().getBytes(), new Throwable());
            return;
        }
        RequestParams params = new RequestParams();
        params.put("userId", userId);
        params.put("fileUrl", fileUrl);

        client.post(getAbsoluteUrl("/verification/face/byUrl" + "?notificationURL=" + this.notificationURL), params, responseHandler);
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

        client.post(getAbsoluteUrl("/verification/video" + "?notificationURL=" + this.notificationURL), params, responseHandler);
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

        String url = getAbsoluteLivenessUrl("/verification/video"+ "?notificationURL=" + this.notificationURL);
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

        client.post(getAbsoluteUrl("/verification/video" + "?notificationURL=" + this.notificationURL), params, responseHandler);
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

        client.post(getAbsoluteUrl("/verification/video/byUrl" + "?notificationURL=" + this.notificationURL), params, responseHandler);
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

        client.post(getAbsoluteUrl("/identification/voice" + "?notificationURL=" + this.notificationURL), params, responseHandler);
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

        client.post(getAbsoluteUrl("/identification/voice/byUrl" + "?notificationURL=" + this.notificationURL), params, responseHandler);
    }

    public void encapsulatedVoiceEnrollment(Activity activity, String userId, String contentLanguage, String phrase, final JsonHttpResponseHandler responseHandler) {
        if (!userIdFormatted(userId)) {
            int duration = Toast.LENGTH_SHORT;
            String response = buildJSONFormatMessage().toString();
            Toast toast = Toast.makeText(activity, "Please make sure the user id is correct", duration);
            try {
                JSONObject json = new JSONObject(response);
                toast = Toast.makeText(activity, json.get("message").toString(), duration);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            responseHandler.sendFailureMessage(200, null, response.getBytes(), new Throwable());
            toast.show();
            return;
        }

        Intent intent = new Intent(activity, VoiceEnrollmentView.class);
        Bundle bundle = new Bundle();
        bundle.putString("apiKey", this.apiKey);
        bundle.putString("apiToken", this.apiToken);
        bundle.putInt("voiceitThemeColor", this.voiceitThemeColor);
        bundle.putString("userId", userId);
        bundle.putString("contentLanguage", contentLanguage);
        bundle.putString("phrase", phrase);
        bundle.putInt("voiceitThemeColor", this.voiceitThemeColor);
        bundle.putString("notificationURL", this.notificationURL);
        intent.putExtras(bundle);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);

        broadcastMessageHandler(activity, responseHandler);
    }

    public void encapsulatedVoiceVerification(Activity activity, String userId, String contentLanguage, String phrase, final JsonHttpResponseHandler responseHandler) {
        if (!userIdFormatted(userId)) {
            int duration = Toast.LENGTH_SHORT;
            String response = buildJSONFormatMessage().toString();
            Toast toast = Toast.makeText(activity, "Please make sure the user id is correct", duration);
            try {
                JSONObject json = new JSONObject(response);
                toast = Toast.makeText(activity, json.get("message").toString(), duration);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            responseHandler.sendFailureMessage(200, null, response.getBytes(), new Throwable());
            toast.show();
            return;
        }

        Intent intent = new Intent(activity, VoiceVerificationView.class);
        Bundle bundle = new Bundle();
        bundle.putString("apiKey", this.apiKey);
        bundle.putString("apiToken", this.apiToken);
        bundle.putInt("voiceitThemeColor", this.voiceitThemeColor);
        bundle.putString("userId", userId);
        bundle.putString("notificationURL", this.notificationURL);
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
        bundle.putInt("voiceitThemeColor", this.voiceitThemeColor);
        bundle.putString("notificationURL", this.notificationURL);
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
            int duration = Toast.LENGTH_SHORT;
            String response = buildJSONFormatMessage().toString();
            Toast toast = Toast.makeText(activity, "Please make sure the user id is correct", duration);
            try {
                JSONObject json = new JSONObject(response);
                toast = Toast.makeText(activity, json.get("message").toString(), duration);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            responseHandler.sendFailureMessage(200, null, response.getBytes(), new Throwable());
            toast.show();
            return;
        }

        Intent intent = new Intent(activity, VideoEnrollmentView.class);
        Bundle bundle = new Bundle();
        bundle.putString("apiKey", this.apiKey);
        bundle.putString("apiToken", this.apiToken);
        bundle.putInt("voiceitThemeColor", this.voiceitThemeColor);
        bundle.putString("userId", userId);
        bundle.putString("notificationURL", this.notificationURL);
        bundle.putString("contentLanguage", contentLanguage);
        bundle.putString("phrase", phrase);
        bundle.putBoolean("displayPreviewFrame", mDisplayPreviewFrame);
        intent.putExtras(bundle);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);

        broadcastMessageHandler(activity, responseHandler);

        //requestWritePermission(activity);
    }

    public void encapsulatedVideoVerification(Activity activity, String userId, String contentLanguage, String phrase, boolean doLivenessCheck, boolean doLivenessAudioCheck, final JsonHttpResponseHandler responseHandler) {
        encapsulatedVideoVerification(doLivenessAudioCheck,activity, userId, contentLanguage, phrase, doLivenessCheck, 0, 2, responseHandler);
    }

    public void encapsulatedVideoVerification(boolean doLivenessAudioCheck, Activity activity, String userId, String contentLanguage, String phrase, boolean doLivenessCheck, int livenessChallengeFailsAllowed, int livenessChallengesNeeded, final JsonHttpResponseHandler responseHandler) {
        if (!userIdFormatted(userId)) {
            int duration = Toast.LENGTH_SHORT;
            String response = buildJSONFormatMessage().toString();
            Toast toast = Toast.makeText(activity, "Please make sure the user id is correct", duration);
            try {
                JSONObject json = new JSONObject(response);
                toast = Toast.makeText(activity, json.get("message").toString(), duration);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            responseHandler.sendFailureMessage(200, null, response.getBytes(), new Throwable());
            toast.show();
            return;
        }

        Intent intent = new Intent(activity, VideoVerificationView.class);
        Bundle bundle = new Bundle();
        bundle.putString("apiKey", this.apiKey);
        bundle.putString("apiToken", this.apiToken);
        bundle.putInt("voiceitThemeColor", this.voiceitThemeColor);
        bundle.putString("userId", userId);
        bundle.putString("contentLanguage", contentLanguage);
        bundle.putString("phrase", phrase);
        bundle.putString("notificationURL", this.notificationURL);
        bundle.putBoolean("doLivenessCheck", doLivenessCheck);
        bundle.putInt("livenessChallengeFailsAllowed", livenessChallengeFailsAllowed);
        bundle.putInt("livenessChallengesNeeded", livenessChallengesNeeded);
        bundle.putBoolean("displayPreviewFrame", mDisplayPreviewFrame);
        bundle.putBoolean("doLivenessAudioCheck",doLivenessAudioCheck);
        intent.putExtras(bundle);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);

        broadcastMessageHandler(activity, responseHandler);

        //requestWritePermission(activity);
    }

    public void encapsulatedFaceEnrollment(Activity activity, String userId, final JsonHttpResponseHandler responseHandler) {
        if (!userIdFormatted(userId)) {
            int duration = Toast.LENGTH_SHORT;
            String response = buildJSONFormatMessage().toString();
            Toast toast = Toast.makeText(activity, "Please make sure the user id is correct", duration);
            try {
                JSONObject json = new JSONObject(response);
                toast = Toast.makeText(activity, json.get("message").toString(), duration);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            responseHandler.sendFailureMessage(200, null, response.getBytes(), new Throwable());
            toast.show();
            return;
        }

        Intent intent = new Intent(activity, FaceEnrollmentView.class);
        Bundle bundle = new Bundle();
        bundle.putString("apiKey", this.apiKey);
        bundle.putString("apiToken", this.apiToken);
        bundle.putString("notificationURL", this.notificationURL);
        bundle.putInt("voiceitThemeColor", this.voiceitThemeColor);
        bundle.putString("userId", userId);
        bundle.putBoolean("displayPreviewFrame", mDisplayPreviewFrame);
        intent.putExtras(bundle);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);

        broadcastMessageHandler(activity, responseHandler);

        //requestWritePermission(activity);
    }

    public void encapsulatedFaceVerification(Activity activity, String userId, String contentLanguage, boolean doLivenessCheck, boolean doLivenessAudioCheck, final JsonHttpResponseHandler responseHandler) {
        encapsulatedFaceVerification(activity, userId, contentLanguage, doLivenessCheck, doLivenessAudioCheck, 0, 2, responseHandler);
    }

    public void encapsulatedFaceVerification(Activity activity, String userId, String contentLanguage, boolean doLivenessCheck, boolean doLivenessAudioCheck, int livenessChallengeFailsAllowed, int livenessChallengesNeeded, final JsonHttpResponseHandler responseHandler) {
        if (!userIdFormatted(userId)) {
            int duration = Toast.LENGTH_SHORT;
            String response = buildJSONFormatMessage().toString();
            Toast toast = Toast.makeText(activity, "Please make sure the user id is correct", duration);
            try {
                JSONObject json = new JSONObject(response);
                toast = Toast.makeText(activity, json.get("message").toString(), duration);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            responseHandler.sendFailureMessage(200, null, response.getBytes(), new Throwable());
            toast.show();
            return;
        }

        Intent intent = new Intent(activity, FaceVerificationView.class);
        Bundle bundle = new Bundle();
        bundle.putString("apiKey", this.apiKey);
        bundle.putInt("voiceitThemeColor", this.voiceitThemeColor);
        bundle.putString("apiToken", this.apiToken);
        bundle.putString("userId", userId);
        bundle.putString("notificationURL", this.notificationURL);
        bundle.putString("contentLanguage", contentLanguage);
        bundle.putBoolean("doLivenessCheck", doLivenessCheck);
        bundle.putBoolean("doLivenessAudioCheck", doLivenessAudioCheck);
        bundle.putInt("livenessChallengeFailsAllowed", livenessChallengeFailsAllowed);
        bundle.putInt("livenessChallengesNeeded", livenessChallengesNeeded);
        bundle.putBoolean("displayPreviewFrame", mDisplayPreviewFrame);
        intent.putExtras(bundle);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);

        broadcastMessageHandler(activity, responseHandler);

        //requestWritePermission(activity);
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
