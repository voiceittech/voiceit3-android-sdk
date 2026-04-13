package com.voiceit.voiceit3;

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
import android.os.Looper;
import android.provider.Settings;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * VoiceIt API 3 client. All async methods invoke {@link Callback} on the main
 * thread.
 *
 * Network transport is OkHttp; previous releases used the abandoned
 * com.loopj.android:android-async-http library. The public API is now built
 * around the {@link Callback} interface — see migration notes in the README.
 */
public class VoiceItAPI3 {
    private static final String mTAG = "VoiceItAPI3";
    /** Reported in the platformVersion header. Keep in sync with the
     *  voiceit3/build.gradle versionName. */
    public static final String VERSION = "4.0.0";
    private static final MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");

    private final String apiKey;
    private final String apiToken;
    private final OkHttpClient client;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String notificationURL;
    private int voiceitThemeColor = 0;
    private String BASE_URL = "https://api.voiceit.io";

    public boolean mDisplayPreviewFrame = false;

    public VoiceItAPI3(String apiKey, String apiToken) {
        this(apiKey, apiToken, null, 0);
    }

    public VoiceItAPI3(String apiKey, String apiToken, String url) {
        this(apiKey, apiToken, url, 0);
    }

    public VoiceItAPI3(String apiKey, String apiToken, int voiceitThemeColor) {
        this(apiKey, apiToken, null, voiceitThemeColor);
    }

    public VoiceItAPI3(String apiKey, String apiToken, int voiceitThemeColor, String url) {
        this(apiKey, apiToken, url, voiceitThemeColor);
    }

    private VoiceItAPI3(String apiKey, String apiToken, String customBaseURL, int voiceitThemeColor) {
        this.apiKey = apiKey;
        this.apiToken = apiToken;
        this.voiceitThemeColor = voiceitThemeColor;
        if (customBaseURL != null) {
            BASE_URL = customBaseURL;
        }
        final String authHeader = Credentials.basic(apiKey, apiToken);
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("Authorization", authHeader)
                        .header("platformId", "40")
                        .header("platformVersion", VERSION)
                        .build()))
                .build();
    }

    public void setURL(String url) {
        BASE_URL = url.replaceAll("\\s+", "");
    }

    public void setNotificationURL(String notificationUrl) {
        this.notificationURL = notificationUrl;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            // UTF-8 is always supported on Android.
            throw new AssertionError(e);
        }
    }

    /**
     * Append ?notificationURL=encoded(notificationURL) (or a connecting
     * &amp;notificationURL=...) to a URL when one has been configured. The
     * caller passes the URL as it stands; this method picks the right
     * separator based on whether the URL already contains a query string.
     */
    private String withNotification(String url) {
        if (notificationURL == null || notificationURL.isEmpty()) {
            return url;
        }
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + "notificationURL=" + enc(notificationURL);
    }

    private String absUrl(String relative) {
        return BASE_URL + relative;
    }

    private void executeAsync(Request request, final Callback cb) {
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                postFailure(cb, 0, null, e);
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                final int code = response.code();
                final ResponseBody body = response.body();
                final String text = body == null ? "" : body.string();
                response.close();

                JSONObject json = null;
                try {
                    if (!text.isEmpty()) {
                        json = new JSONObject(text);
                    }
                } catch (JSONException ignored) {
                    // Body wasn't JSON — leave json as null.
                }

                if (response.isSuccessful() && json != null) {
                    final JSONObject finalJson = json;
                    mainHandler.post(() -> cb.onSuccess(finalJson));
                } else {
                    final JSONObject finalErr = json;
                    final Throwable err = response.isSuccessful()
                            ? new IOException("Response was not valid JSON")
                            : null;
                    postFailure(cb, code, finalErr, err);
                }
            }
        });
    }

    private void postFailure(final Callback cb, final int code,
                             final JSONObject errBody, final Throwable err) {
        mainHandler.post(() -> cb.onFailure(code, errBody, err));
    }

    private void failValidation(Callback cb) {
        postFailure(cb, 0, buildJSONFormatMessage(),
                new IllegalArgumentException("Incorrectly formatted id argument"));
    }

    private void doGet(String relative, Callback cb) {
        executeAsync(new Request.Builder().url(withNotification(absUrl(relative))).get().build(), cb);
    }

    private void doDelete(String relative, Callback cb) {
        executeAsync(new Request.Builder().url(withNotification(absUrl(relative))).delete().build(), cb);
    }

    private void doMultipartPost(String relative, MultipartBody body, Callback cb) {
        executeAsync(new Request.Builder().url(withNotification(absUrl(relative))).post(body).build(), cb);
    }

    private void doMultipartPut(String relative, MultipartBody body, Callback cb) {
        executeAsync(new Request.Builder().url(withNotification(absUrl(relative))).put(body).build(), cb);
    }

    private static MultipartBody.Builder mb() {
        return new MultipartBody.Builder().setType(MultipartBody.FORM);
    }

    private static MultipartBody.Builder withFile(MultipartBody.Builder b,
                                                  String name, File file) {
        return b.addFormDataPart(name, file.getName(),
                RequestBody.create(file, OCTET_STREAM));
    }

    // ------------------------------------------------------------------
    // Phrases
    // ------------------------------------------------------------------

    public void getPhrases(String contentLanguage, Callback cb) {
        doGet("/phrases/" + enc(contentLanguage), cb);
    }

    // ------------------------------------------------------------------
    // Users
    // ------------------------------------------------------------------

    public void getAllUsers(Callback cb) {
        doGet("/users", cb);
    }

    public void createUser(Callback cb) {
        doMultipartPost("/users", mb().addFormDataPart("_", "_").build(), cb);
    }

    public void checkUserExists(String userId, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        doGet("/users/" + enc(userId), cb);
    }

    public void deleteUser(String userId, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        doDelete("/users/" + enc(userId), cb);
    }

    public void getGroupsForUser(String userId, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        doGet("/users/" + enc(userId) + "/groups", cb);
    }

    // ------------------------------------------------------------------
    // Enrollment listing / deletion
    // ------------------------------------------------------------------

    public void deleteAllEnrollments(String userId, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        doDelete("/enrollments/" + enc(userId) + "/all", cb);
    }

    public void getAllVoiceEnrollments(String userId, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        doGet("/enrollments/voice/" + enc(userId), cb);
    }

    public void getAllFaceEnrollments(String userId, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        doGet("/enrollments/face/" + enc(userId), cb);
    }

    public void getAllVideoEnrollments(String userId, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        doGet("/enrollments/video/" + enc(userId), cb);
    }

    // ------------------------------------------------------------------
    // Voice enrollment
    // ------------------------------------------------------------------

    public void createVoiceEnrollment(String userId, String contentLanguage,
                                      String phrase, String recordingPath, Callback cb) {
        createVoiceEnrollment(userId, contentLanguage, phrase, new File(recordingPath), cb);
    }

    public void createVoiceEnrollment(String userId, String contentLanguage,
                                      String phrase, File recording, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        if (!recording.exists()) { failValidation(cb); return; }
        MultipartBody body = withFile(mb()
                .addFormDataPart("userId", userId)
                .addFormDataPart("contentLanguage", contentLanguage)
                .addFormDataPart("phrase", phrase),
                "recording", recording).build();
        doMultipartPost("/enrollments/voice", body, cb);
    }

    public void createVoiceEnrollment(final String userId, final String contentLanguage,
                                      final String phrase, final Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        try {
            final File recordingFile = File.createTempFile("tempEnrollmentFile", ".wav");
            final MediaRecorder myRecorder = new MediaRecorder();
            Utils.startMediaRecorder(myRecorder, recordingFile);
            new Handler().postDelayed(() -> {
                myRecorder.stop();
                myRecorder.reset();
                myRecorder.release();
                createVoiceEnrollment(userId, contentLanguage, phrase, recordingFile, cb);
            }, 4800);
        } catch (Exception ex) {
            Log.e(mTAG, "Recording Exception: " + ex.getMessage());
            postFailure(cb, 0, buildJSONFormatMessage(), ex);
        }
    }

    public void createVoiceEnrollmentByUrl(String userId, String contentLanguage,
                                           String phrase, String fileUrl, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        MultipartBody body = mb()
                .addFormDataPart("userId", userId)
                .addFormDataPart("contentLanguage", contentLanguage)
                .addFormDataPart("phrase", phrase)
                .addFormDataPart("fileUrl", fileUrl)
                .build();
        doMultipartPost("/enrollments/voice/byUrl", body, cb);
    }

    // ------------------------------------------------------------------
    // Face enrollment
    // ------------------------------------------------------------------

    public void createFaceEnrollment(String userId, String videoPath, Callback cb) {
        createFaceEnrollment(userId, new File(videoPath), cb);
    }

    public void createFaceEnrollment(String userId, File video, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        if (!video.exists()) { failValidation(cb); return; }
        MultipartBody body = withFile(mb()
                .addFormDataPart("userId", userId), "video", video).build();
        doMultipartPost("/enrollments/face", body, cb);
    }

    public void createFaceEnrollmentWithPhoto(String userId, String photoPath, Callback cb) {
        createFaceEnrollmentWithPhoto(userId, new File(photoPath), cb);
    }

    public void createFaceEnrollmentWithPhoto(String userId, File photo, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        if (!photo.exists()) { failValidation(cb); return; }
        MultipartBody body = withFile(mb()
                .addFormDataPart("userId", userId), "photo", photo).build();
        doMultipartPost("/enrollments/face", body, cb);
    }

    public void createFaceEnrollmentByUrl(String userId, String fileUrl, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        MultipartBody body = mb()
                .addFormDataPart("userId", userId)
                .addFormDataPart("fileUrl", fileUrl)
                .build();
        doMultipartPost("/enrollments/face/byUrl", body, cb);
    }

    // ------------------------------------------------------------------
    // Video enrollment
    // ------------------------------------------------------------------

    public void createVideoEnrollment(String userId, String contentLanguage,
                                      String phrase, File audio, File photo, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        if (!audio.exists() || !photo.exists()) { failValidation(cb); return; }
        MultipartBody body = withFile(withFile(mb()
                .addFormDataPart("userId", userId)
                .addFormDataPart("contentLanguage", contentLanguage)
                .addFormDataPart("phrase", phrase),
                "audio", audio), "photo", photo).build();
        doMultipartPost("/enrollments/video", body, cb);
    }

    public void createVideoEnrollment(String userId, String contentLanguage,
                                      String phrase, String videoPath, Callback cb) {
        createVideoEnrollment(userId, contentLanguage, phrase, new File(videoPath), cb);
    }

    public void createVideoEnrollment(String userId, String contentLanguage,
                                      String phrase, File video, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        if (!video.exists()) { failValidation(cb); return; }
        MultipartBody body = withFile(mb()
                .addFormDataPart("userId", userId)
                .addFormDataPart("contentLanguage", contentLanguage)
                .addFormDataPart("phrase", phrase),
                "video", video).build();
        doMultipartPost("/enrollments/video", body, cb);
    }

    public void createVideoEnrollmentByUrl(String userId, String contentLanguage,
                                           String phrase, String fileUrl, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        MultipartBody body = mb()
                .addFormDataPart("userId", userId)
                .addFormDataPart("contentLanguage", contentLanguage)
                .addFormDataPart("phrase", phrase)
                .addFormDataPart("fileUrl", fileUrl)
                .build();
        doMultipartPost("/enrollments/video/byUrl", body, cb);
    }

    // ------------------------------------------------------------------
    // Groups
    // ------------------------------------------------------------------

    public void getAllGroups(Callback cb) {
        doGet("/groups", cb);
    }

    public void getGroup(String groupId, Callback cb) {
        if (!groupIdFormatted(groupId)) { failValidation(cb); return; }
        doGet("/groups/" + enc(groupId), cb);
    }

    public void groupExists(String groupId, Callback cb) {
        if (!groupIdFormatted(groupId)) { failValidation(cb); return; }
        doGet("/groups/" + enc(groupId) + "/exists", cb);
    }

    public void createGroup(String description, Callback cb) {
        MultipartBody body = mb().addFormDataPart("description", description).build();
        doMultipartPost("/groups", body, cb);
    }

    public void addUserToGroup(String groupId, String userId, Callback cb) {
        if (!groupIdFormatted(groupId) || !userIdFormatted(userId)) { failValidation(cb); return; }
        MultipartBody body = mb()
                .addFormDataPart("groupId", groupId)
                .addFormDataPart("userId", userId)
                .build();
        doMultipartPut("/groups/addUser", body, cb);
    }

    public void removeUserFromGroup(String groupId, String userId, Callback cb) {
        if (!groupIdFormatted(groupId) || !userIdFormatted(userId)) { failValidation(cb); return; }
        doDelete("/groups/removeUser?groupId=" + enc(groupId) + "&userId=" + enc(userId), cb);
    }

    public void deleteGroup(String groupId, Callback cb) {
        if (!groupIdFormatted(groupId)) { failValidation(cb); return; }
        doDelete("/groups/" + enc(groupId), cb);
    }

    // ------------------------------------------------------------------
    // Voice verification / identification
    // ------------------------------------------------------------------

    public void voiceVerification(String userId, String contentLanguage, String phrase,
                                  String recordingPath, Callback cb) {
        voiceVerification(userId, contentLanguage, phrase, new File(recordingPath), cb);
    }

    public void voiceVerification(String userId, String contentLanguage, String phrase,
                                  File recording, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        if (!recording.exists()) { failValidation(cb); return; }
        MultipartBody body = withFile(mb()
                .addFormDataPart("userId", userId)
                .addFormDataPart("contentLanguage", contentLanguage)
                .addFormDataPart("phrase", phrase),
                "recording", recording).build();
        doMultipartPost("/verification/voice", body, cb);
    }

    public void voiceVerification(final String userId, final String contentLanguage,
                                  final String phrase, final Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        try {
            final File recordingFile = File.createTempFile("tempEnrollmentFile", ".wav");
            final MediaRecorder myRecorder = new MediaRecorder();
            Utils.startMediaRecorder(myRecorder, recordingFile);
            new Handler().postDelayed(() -> {
                myRecorder.stop();
                myRecorder.reset();
                myRecorder.release();
                voiceVerification(userId, contentLanguage, phrase, recordingFile, cb);
            }, 4800);
        } catch (Exception ex) {
            Log.e(mTAG, "Recording Error: " + ex.getMessage());
            postFailure(cb, 0, buildJSONFormatMessage(), ex);
        }
    }

    public void voiceVerificationByUrl(String userId, String contentLanguage, String phrase,
                                       String fileUrl, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        MultipartBody body = mb()
                .addFormDataPart("userId", userId)
                .addFormDataPart("contentLanguage", contentLanguage)
                .addFormDataPart("phrase", phrase)
                .addFormDataPart("fileUrl", fileUrl)
                .build();
        doMultipartPost("/verification/voice/byUrl", body, cb);
    }

    // ------------------------------------------------------------------
    // Face verification
    // ------------------------------------------------------------------

    public void faceVerification(String userId, String videoPath, Callback cb) {
        faceVerification(userId, new File(videoPath), cb);
    }

    public void faceVerification(String userId, File video, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        if (!video.exists()) { failValidation(cb); return; }
        MultipartBody body = withFile(mb()
                .addFormDataPart("userId", userId), "video", video).build();
        doMultipartPost("/verification/face", body, cb);
    }

    public void faceVerificationWithPhoto(String userId, String photoPath, Callback cb) {
        faceVerificationWithPhoto(userId, new File(photoPath), cb);
    }

    public void faceVerificationWithPhoto(String userId, File photo, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        if (!photo.exists()) { failValidation(cb); return; }
        MultipartBody body = withFile(mb()
                .addFormDataPart("userId", userId), "photo", photo).build();
        doMultipartPost("/verification/face", body, cb);
    }

    public void faceVerificationByUrl(String userId, String fileUrl, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        MultipartBody body = mb()
                .addFormDataPart("userId", userId)
                .addFormDataPart("fileUrl", fileUrl)
                .build();
        doMultipartPost("/verification/face/byUrl", body, cb);
    }

    // ------------------------------------------------------------------
    // Video verification
    // ------------------------------------------------------------------

    public void videoVerification(String userId, String contentLanguage, String phrase,
                                  String videoPath, Callback cb) {
        videoVerification(userId, contentLanguage, phrase, new File(videoPath), cb);
    }

    public void videoVerification(String userId, String contentLanguage, String phrase,
                                  File video, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        if (!video.exists()) { failValidation(cb); return; }
        MultipartBody body = withFile(mb()
                .addFormDataPart("userId", userId)
                .addFormDataPart("contentLanguage", contentLanguage)
                .addFormDataPart("phrase", phrase),
                "video", video).build();
        doMultipartPost("/verification/video", body, cb);
    }

    public void videoVerification(String userId, String contentLanguage, String phrase,
                                  File audio, File photo, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        if (!audio.exists() || !photo.exists()) { failValidation(cb); return; }
        MultipartBody body = withFile(withFile(mb()
                .addFormDataPart("userId", userId)
                .addFormDataPart("contentLanguage", contentLanguage)
                .addFormDataPart("phrase", phrase),
                "audio", audio), "photo", photo).build();
        doMultipartPost("/verification/video", body, cb);
    }

    public void videoVerificationByUrl(String userId, String contentLanguage, String phrase,
                                       String fileUrl, Callback cb) {
        if (!userIdFormatted(userId)) { failValidation(cb); return; }
        MultipartBody body = mb()
                .addFormDataPart("userId", userId)
                .addFormDataPart("contentLanguage", contentLanguage)
                .addFormDataPart("phrase", phrase)
                .addFormDataPart("fileUrl", fileUrl)
                .build();
        doMultipartPost("/verification/video/byUrl", body, cb);
    }

    // ------------------------------------------------------------------
    // Voice identification
    // ------------------------------------------------------------------

    public void voiceIdentification(String groupId, String contentLanguage, String phrase,
                                    String recordingPath, Callback cb) {
        voiceIdentification(groupId, contentLanguage, phrase, new File(recordingPath), cb);
    }

    public void voiceIdentification(String groupId, String contentLanguage, String phrase,
                                    File recording, Callback cb) {
        if (!groupIdFormatted(groupId)) { failValidation(cb); return; }
        if (!recording.exists()) { failValidation(cb); return; }
        MultipartBody body = withFile(mb()
                .addFormDataPart("groupId", groupId)
                .addFormDataPart("contentLanguage", contentLanguage)
                .addFormDataPart("phrase", phrase),
                "recording", recording).build();
        doMultipartPost("/identification/voice", body, cb);
    }

    public void voiceIdentification(final String groupId, final String contentLanguage,
                                    final String phrase, final Callback cb) {
        if (!groupIdFormatted(groupId)) { failValidation(cb); return; }
        try {
            final File recordingFile = File.createTempFile("tempEnrollmentFile", ".wav");
            final MediaRecorder myRecorder = new MediaRecorder();
            Utils.startMediaRecorder(myRecorder, recordingFile);
            new Handler().postDelayed(() -> {
                myRecorder.stop();
                myRecorder.reset();
                myRecorder.release();
                voiceIdentification(groupId, contentLanguage, phrase, recordingFile, cb);
            }, 4800);
        } catch (Exception ex) {
            Log.e(mTAG, "Recording Exception:" + ex.getMessage());
            postFailure(cb, 0, buildJSONFormatMessage(), ex);
        }
    }

    public void voiceIdentificationByUrl(String groupId, String contentLanguage, String phrase,
                                         String fileUrl, Callback cb) {
        if (!groupIdFormatted(groupId)) { failValidation(cb); return; }
        MultipartBody body = mb()
                .addFormDataPart("groupId", groupId)
                .addFormDataPart("contentLanguage", contentLanguage)
                .addFormDataPart("phrase", phrase)
                .addFormDataPart("fileUrl", fileUrl)
                .build();
        doMultipartPost("/identification/voice/byUrl", body, cb);
    }

    // ------------------------------------------------------------------
    // Encapsulated views (launch our Activities)
    // ------------------------------------------------------------------

    public void encapsulatedVoiceEnrollment(Activity activity, String userId,
                                            String contentLanguage, String phrase,
                                            final Callback cb) {
        if (!userIdFormatted(userId)) { showValidationToast(activity, cb); return; }
        Intent intent = new Intent(activity, VoiceEnrollmentView.class);
        intent.putExtras(buildEncapsulatedBundle(userId, null, contentLanguage, phrase));
        startEncapsulatedActivity(activity, intent, cb);
    }

    public void encapsulatedVoiceVerification(Activity activity, String userId,
                                              String contentLanguage, String phrase,
                                              final Callback cb) {
        if (!userIdFormatted(userId)) { showValidationToast(activity, cb); return; }
        Intent intent = new Intent(activity, VoiceVerificationView.class);
        intent.putExtras(buildEncapsulatedBundle(userId, null, contentLanguage, phrase));
        startEncapsulatedActivity(activity, intent, cb);
    }

    public void encapsulatedVoiceIdentification(Activity activity, String groupId,
                                                String contentLanguage, String phrase,
                                                final Callback cb) {
        if (!groupIdFormatted(groupId)) { failValidation(cb); return; }
        Intent intent = new Intent(activity, VoiceIdentificationView.class);
        intent.putExtras(buildEncapsulatedBundle(null, groupId, contentLanguage, phrase));
        startEncapsulatedActivity(activity, intent, cb);
    }

    public void encapsulatedVideoEnrollment(Activity activity, String userId,
                                            String contentLanguage, String phrase,
                                            final Callback cb) {
        if (!userIdFormatted(userId)) { showValidationToast(activity, cb); return; }
        Intent intent = new Intent(activity, VideoEnrollmentView.class);
        Bundle b = buildEncapsulatedBundle(userId, null, contentLanguage, phrase);
        b.putBoolean("displayPreviewFrame", mDisplayPreviewFrame);
        intent.putExtras(b);
        startEncapsulatedActivity(activity, intent, cb);
    }

    public void encapsulatedVideoVerification(Activity activity, String userId,
                                              String contentLanguage, String phrase,
                                              final Callback cb) {
        if (!userIdFormatted(userId)) { showValidationToast(activity, cb); return; }
        Intent intent = new Intent(activity, VideoVerificationView.class);
        Bundle b = buildEncapsulatedBundle(userId, null, contentLanguage, phrase);
        b.putBoolean("displayPreviewFrame", mDisplayPreviewFrame);
        intent.putExtras(b);
        startEncapsulatedActivity(activity, intent, cb);
    }

    public void encapsulatedFaceEnrollment(Activity activity, String userId,
                                           String contentLanguage,
                                           final Callback cb) {
        if (!userIdFormatted(userId)) { showValidationToast(activity, cb); return; }
        Intent intent = new Intent(activity, FaceEnrollmentView.class);
        Bundle b = buildEncapsulatedBundle(userId, null, contentLanguage, null);
        b.putBoolean("displayPreviewFrame", mDisplayPreviewFrame);
        intent.putExtras(b);
        startEncapsulatedActivity(activity, intent, cb);
    }

    public void encapsulatedFaceVerification(Activity activity, String userId,
                                             String contentLanguage,
                                             final Callback cb) {
        if (!userIdFormatted(userId)) { showValidationToast(activity, cb); return; }
        Intent intent = new Intent(activity, FaceVerificationView.class);
        Bundle b = buildEncapsulatedBundle(userId, null, contentLanguage, null);
        b.putBoolean("displayPreviewFrame", mDisplayPreviewFrame);
        intent.putExtras(b);
        startEncapsulatedActivity(activity, intent, cb);
    }

    private Bundle buildEncapsulatedBundle(String userId, String groupId,
                                           String contentLanguage, String phrase) {
        Bundle b = new Bundle();
        b.putString("apiKey", apiKey);
        b.putString("apiToken", apiToken);
        b.putInt("voiceitThemeColor", voiceitThemeColor);
        b.putString("notificationURL", notificationURL);
        if (userId != null) b.putString("userId", userId);
        if (groupId != null) b.putString("groupId", groupId);
        if (contentLanguage != null) b.putString("contentLanguage", contentLanguage);
        if (phrase != null) b.putString("phrase", phrase);
        return b;
    }

    private void startEncapsulatedActivity(Activity activity, Intent intent, Callback cb) {
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
        broadcastMessageHandler(activity, cb);
    }

    private void showValidationToast(Activity activity, Callback cb) {
        JSONObject response = buildJSONFormatMessage();
        try {
            Toast.makeText(activity, response.getString("message"), Toast.LENGTH_SHORT).show();
        } catch (JSONException ignored) {
            Toast.makeText(activity, "Invalid id argument", Toast.LENGTH_SHORT).show();
        }
        postFailure(cb, 0, response, new IllegalArgumentException("Invalid id"));
    }

    private void broadcastMessageHandler(final Activity activity, final Callback cb) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            boolean fired = false;

            @Override
            public void onReceive(Context context, Intent intent) {
                if (fired) return;
                fired = true;
                String responseStr = intent.getStringExtra("Response");
                JSONObject json = null;
                try {
                    if (responseStr != null) json = new JSONObject(responseStr);
                } catch (JSONException ignored) {
                }
                if ("voiceit-success".equals(intent.getAction()) && json != null) {
                    final JSONObject finalJson = json;
                    mainHandler.post(() -> cb.onSuccess(finalJson));
                } else {
                    final JSONObject finalErr = json;
                    mainHandler.post(() -> cb.onFailure(0, finalErr,
                            new IOException("Encapsulated flow failed")));
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("voiceit-success");
        filter.addAction("voiceit-failure");
        LocalBroadcastManager.getInstance(activity).registerReceiver(receiver, filter);
    }

    @SuppressWarnings("unused")
    private void requestWritePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= 23) {
            if (!Settings.System.canWrite(activity)) {
                Toast.makeText(activity, activity.getString(R.string.GRANT_WRITE_PERMISSON),
                        Toast.LENGTH_LONG).show();
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
            }
        }
    }

    private boolean userIdFormatted(String arg) {
        if (arg == null || arg.length() < 4) return false;
        String id = arg.substring(arg.lastIndexOf('_') + 1);
        if (!id.matches("[A-Za-z0-9]+")
                || !arg.substring(0, 3).equals("usr")
                || id.length() != 32) {
            Log.e(mTAG, "UserId does not meet requirements, please ensure it is your "
                    + "user's 36 character alphanumeric string generated from the createUser API call");
            return false;
        }
        return true;
    }

    private boolean groupIdFormatted(String arg) {
        if (arg == null || arg.length() < 4) return false;
        String id = arg.substring(arg.lastIndexOf('_') + 1);
        if (!id.matches("[A-Za-z0-9]+")
                || !arg.substring(0, 3).equals("grp")
                || id.length() != 32) {
            Log.e(mTAG, "GroupId does not meet requirements, please ensure it is your "
                    + "group's 36 character alphanumeric string generated from the createGroup API call");
            return false;
        }
        return true;
    }

    private JSONObject buildJSONFormatMessage() {
        JSONObject json = new JSONObject();
        try {
            json.put("responseCode", "GERR");
            json.put("message", "Incorrectly formatted id argument. Check log output for more information");
        } catch (JSONException e) {
            Log.e(mTAG, "JSON Exception : " + e.getMessage());
        }
        return json;
    }
}
