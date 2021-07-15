package com.voiceit.voiceit2;

import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;

import com.loopj.android.http.JsonHttpResponseHandler;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cz.msebera.android.httpclient.Header;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4ClassRunner.class)
public class VoiceItInstrumentedTest {

    final private String apiKey = InstrumentationRegistry.getArguments().getString("key");
    final private String apiTok = InstrumentationRegistry.getArguments().getString("tok");

    final private VoiceItAPI2 myVoiceIt = new VoiceItAPI2(apiKey, apiTok);

    final private String phrase = "Never forget tomorrow is a new day";

    private final ArrayList<String> userIds = new ArrayList<>();
    private String groupId = "";
    private final ArrayList<String> enrollmentIds = new ArrayList<>();
    private final ArrayList<String> faceEnrollmentIds = new ArrayList<>();

    final private String dir = InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir().getAbsolutePath();

    private CountDownLatch signal;

    private JsonHttpResponseHandler ResponseHandler(final String method, final int expectedStatusCode, final String expectedResponseCode, final CountDownLatch signal){

        return new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                System.out.println(method + " onSuccess Response:" + response.toString());
                assertEquals(expectedStatusCode, statusCode);
                try {
                    assertEquals(expectedResponseCode, response.getString("responseCode"));
                    if(response.has("userId")) {
                        userIds.add(response.getString("userId"));
                    }
                    if(response.has("groupId")) {
                        groupId = response.getString("groupId");
                    }
                    if(response.has("faceEnrollmentId")) {
                        faceEnrollmentIds.add(response.getString("faceEnrollmentId"));
                    }
                    if(response.has("id")) {
                        enrollmentIds.add(response.getString("id"));
                    }
                } catch (JSONException e) {
                    System.out.println("ResponseHandler JSONException" + e.getMessage());
                }
                signal.countDown();
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                if (errorResponse != null) {
                    System.out.println(method + " onFailure Result : " + errorResponse.toString());
                    assertEquals(expectedStatusCode, statusCode);
                    signal.countDown();
                } else {
                    assertEquals(true, false);
                }
            }

            @Override
            public boolean getUseSynchronousMode() {
                return false;
            }

        };
    }

    private void before() {
        signal = new CountDownLatch(1);
    }

    private void after(String method) {
        try {
            if(!signal.await(120, TimeUnit.SECONDS)){
                assertEquals(method, "Timeout");
            }
        } catch (InterruptedException e) { e.printStackTrace(); }
    }

    private void downloadFile(String source, String file) {
        try {
            FileUtils.copyURLToFile( new URL(source), new File(dir, file));
        } catch (MalformedURLException e) {
            System.err.println("downloadFile MalformedURLException: " + e);
        } catch (IOException e) {
            System.err.println("downloadFile IOException: " + e);
        }
    }

    private void deleteFile(String path) {
        File file = new File(dir, path);
        if(file.delete()) {
            System.out.println("File " + path + " deleted successfully");
        } else {
            System.err.println("Failed to delete file at: " + path);
        }
    }

    @Test
    public void TestBasics () {

        before();
        myVoiceIt.createUser(ResponseHandler("createUser",201, "SUCC", signal));
        after("createUser");

        before();
        myVoiceIt.getAllUsers(ResponseHandler("getAllUsers",200, "SUCC", signal));
        after("getAllUsers");

        before();
        myVoiceIt.checkUserExists(userIds.get(0), ResponseHandler("checkUserExists",200, "SUCC", signal));
        after("checkUserExists");

        before();
        myVoiceIt.createGroup("Sample Group Description", ResponseHandler("createGroup",201,
                "SUCC", signal));
        after("createGroup");

        before();
        myVoiceIt.getGroupsForUser(userIds.get(0), ResponseHandler("getGroupsForUser",200,
                "SUCC", signal));
        after("getGroupsForUser");

        before();
        myVoiceIt.addUserToGroup(groupId, userIds.get(0), ResponseHandler("addUserToGroup",200,
                "SUCC", signal));
        after("addUserToGroup");

        before();
        myVoiceIt.getGroupsForUser(userIds.get(0), ResponseHandler("getGroupsForUser",200,
                "SUCC", signal));
        after("getGroupsForUser");

        before();
        myVoiceIt.getGroup(groupId, ResponseHandler("getGroup",200, "SUCC", signal));
        after("getGroup");

        before();
        myVoiceIt.groupExists(groupId, ResponseHandler("groupExists",200, "SUCC", signal));
        after("groupExists");

        before();
        myVoiceIt.removeUserFromGroup(groupId, userIds.get(0), ResponseHandler("removeUserFromGroup",200,
                "SUCC", signal));
        after("removeUserFromGroup");

        before();
        myVoiceIt.deleteGroup(groupId, ResponseHandler("deleteGroup",200, "SUCC", signal));
        after("deleteGroup");

        before();
        myVoiceIt.deleteUser(userIds.get(0), ResponseHandler("deleteUser",200, "SUCC", signal));
        after("deleteUser");
        userIds.clear();

        before();
        myVoiceIt.getPhrases("en-US", ResponseHandler("getPhrases",200, "SUCC", signal));
        after("getPhrases");
    }

    @Test
    public void TestVideo() {

        // Download Video Enrollments
        downloadFile("https://drive.voiceit.io/files/videoEnrollmentD1.mov", "videoEnrollmentD1.mov");
        downloadFile("https://drive.voiceit.io/files/videoEnrollmentD2.mov", "videoEnrollmentD2.mov");
        downloadFile("https://drive.voiceit.io/files/videoEnrollmentD3.mov", "videoEnrollmentD3.mov");
        downloadFile("https://drive.voiceit.io/files/videoVerificationD1.mov", "videoVerificationD1.mov");
        downloadFile("https://drive.voiceit.io/files/videoVerificationD2.mov", "videoVerificationD2.mov");
        downloadFile("https://drive.voiceit.io/files/videoEnrollmentC1.mov", "videoEnrollmentC1.mov");
        downloadFile("https://drive.voiceit.io/files/videoEnrollmentC2.mov", "videoEnrollmentC2.mov");
        downloadFile("https://drive.voiceit.io/files/videoEnrollmentC3.mov", "videoEnrollmentC3.mov");

        before();
        myVoiceIt.createUser(ResponseHandler("createUser", 201, "SUCC", signal));
        after("createUser");

        before();
        myVoiceIt.createUser(ResponseHandler("createUser", 201, "SUCC", signal));
        after("createUser");

        before();
        myVoiceIt.createGroup("Sample Group Description", ResponseHandler("createGroup", 201,
                "SUCC", signal));
        after("createGroup");

        before();
        myVoiceIt.addUserToGroup(groupId, userIds.get(0), ResponseHandler("addUserToGroup",200, "SUCC", signal));
        after("addUserToGroup");

        before();
        myVoiceIt.addUserToGroup(groupId, userIds.get(1), ResponseHandler("addUserToGroup",200, "SUCC", signal));
        after("addUserToGroup");

        before();
        myVoiceIt.createVideoEnrollment(userIds.get(0), "en-US", phrase, dir+"/videoEnrollmentD1.mov",
                ResponseHandler("createVideoEnrollment",201, "SUCC", signal));
        after("createVideoEnrollment");

        before();
        myVoiceIt.createVideoEnrollment(userIds.get(0), "en-US", phrase, dir+"/videoEnrollmentD2.mov",
                ResponseHandler("createVideoEnrollment",201, "SUCC", signal));
        after("createVideoEnrollment");

        before();
        myVoiceIt.createVideoEnrollment(userIds.get(0), "en-US", phrase, dir+"/videoEnrollmentD3.mov",
                ResponseHandler("createVideoEnrollment", 201, "SUCC", signal));
        after("createVideoEnrollment");

        before();
        myVoiceIt.getAllVideoEnrollments(userIds.get(0), ResponseHandler("getAllVideoEnrollments",200, "SUCC", signal));
        after("getAllVideoEnrollments");

        before();
        myVoiceIt.createVideoEnrollment(userIds.get(1), "en-US", phrase, dir+"/videoEnrollmentC1.mov",
                ResponseHandler("createVideoEnrollment",201, "SUCC", signal));
        after("createVideoEnrollment");

        before();
        myVoiceIt.createVideoEnrollment(userIds.get(1), "en-US", phrase, dir+"/videoEnrollmentC2.mov",
                ResponseHandler("createVideoEnrollment",201, "SUCC", signal));
        after("createVideoEnrollment");

        before();
        myVoiceIt.createVideoEnrollment(userIds.get(1), "en-US", phrase, dir+"/videoEnrollmentC3.mov",
                ResponseHandler("createVideoEnrollment",201, "SUCC", signal));
        after("createVideoEnrollment");

        before();
        myVoiceIt.videoVerification(userIds.get(0), "en-US", phrase, dir+"/videoVerificationD1.mov",
                ResponseHandler("videoVerification",200, "SUCC", signal));
        after("videoVerification");

        before();
        myVoiceIt.deleteAllEnrollments(userIds.get(0), ResponseHandler("deleteAllVideoEnrollments",200, "SUCC", signal));
        after("deleteAllVideoEnrollments");

        before();
        myVoiceIt.deleteAllEnrollments(userIds.get(1), ResponseHandler("deleteAllVideoEnrollments",200, "SUCC", signal));
        after("deleteAllVideoEnrollments");

        // Cleanup
        before();
        myVoiceIt.deleteUser(userIds.get(0), ResponseHandler("deleteUser",200, "SUCC", signal));
        after("deleteUser");

        before();
        myVoiceIt.deleteUser(userIds.get(1), ResponseHandler("deleteUser",200, "SUCC", signal));
        after("deleteUser");

        before();
        myVoiceIt.deleteGroup(groupId, ResponseHandler("deleteGroup",200, "SUCC", signal));
        after("deleteGroup");

        userIds.clear();
        enrollmentIds.clear();

        deleteFile("videoEnrollmentD1.mov");
        deleteFile("videoEnrollmentD2.mov");
        deleteFile("videoEnrollmentD3.mov");
        deleteFile("videoVerificationD1.mov");
        deleteFile("videoVerificationD2.mov");
        deleteFile("videoEnrollmentC1.mov");
        deleteFile("videoEnrollmentC2.mov");
        deleteFile("videoEnrollmentC3.mov");

        // Video Enrollments By URL

        before();
        myVoiceIt.createUser(ResponseHandler("createUser",201, "SUCC", signal));
        after("createUser");

        before();
        myVoiceIt.createUser(ResponseHandler("createUser",201, "SUCC", signal));
        after("createUser");

        before();
        myVoiceIt.createGroup("Sample Group Description", ResponseHandler("createGroup",201, "SUCC", signal));
        after("createGroup");

        before();
        myVoiceIt.addUserToGroup(groupId, userIds.get(0), ResponseHandler("addUserToGroup",200, "SUCC", signal));
        after("addUserToGroup");

        before();
        myVoiceIt.addUserToGroup(groupId, userIds.get(1), ResponseHandler("addUserToGroup",200, "SUCC", signal));
        after("addUserToGroup");

        before();
        myVoiceIt.createVideoEnrollmentByUrl(userIds.get(0), "en-US", phrase, "https://drive.voiceit.io/files/videoEnrollmentD1.mov",
                ResponseHandler("createVideoEnrollmentByUrl",201, "SUCC", signal));
        after("createVideoEnrollmentByUrl");

        before();
        myVoiceIt.createVideoEnrollmentByUrl(userIds.get(0), "en-US", phrase, "https://drive.voiceit.io/files/videoEnrollmentD2.mov",
                ResponseHandler("createVideoEnrollmentByUrl",201, "SUCC", signal));
        after("createVideoEnrollmentByUrl");

        before();
        myVoiceIt.createVideoEnrollmentByUrl(userIds.get(0), "en-US", phrase, "https://drive.voiceit.io/files/videoEnrollmentD3.mov",
                ResponseHandler("createVideoEnrollmentByUrl",201, "SUCC", signal));
        after("createVideoEnrollmentByUrl");

        before();
        myVoiceIt.createVideoEnrollmentByUrl(userIds.get(1), "en-US", phrase, "https://drive.voiceit.io/files/videoEnrollmentC1.mov",
                ResponseHandler("createVideoEnrollmentByUrl",201, "SUCC", signal));
        after("createVideoEnrollmentByUrl");

        before();
        myVoiceIt.createVideoEnrollmentByUrl(userIds.get(1), "en-US", phrase, "https://drive.voiceit.io/files/videoEnrollmentC2.mov",
                ResponseHandler("createVideoEnrollmentByUrl",201, "SUCC", signal));
        after("createVideoEnrollmentByUrl");

        before();
        myVoiceIt.createVideoEnrollmentByUrl(userIds.get(1), "en-US", phrase, "https://drive.voiceit.io/files/videoEnrollmentC3.mov",
                ResponseHandler("createVideoEnrollmentByUrl",201, "SUCC", signal));
        after("createVideoEnrollmentByUrl");

        before();
        myVoiceIt.videoVerificationByUrl(userIds.get(0), "en-US", phrase, "https://drive.voiceit.io/files/videoVerificationD1.mov",
                ResponseHandler("videoVerificationByUrl",200, "SUCC", signal));
        after("videoVerificationByUrl");

        before();
        myVoiceIt.deleteAllEnrollments(userIds.get(0), ResponseHandler("deleteAllVideoEnrollments",200, "SUCC", signal));
        after("deleteAllVideoEnrollments");

        before();
        myVoiceIt.deleteAllEnrollments(userIds.get(1), ResponseHandler("deleteAllVideoEnrollments",200, "SUCC", signal));
        after("deleteAllVideoEnrollments");

        before();
        myVoiceIt.deleteUser(userIds.get(0), ResponseHandler("deleteUser",200, "SUCC", signal));
        after("deleteUser");

        before();
        myVoiceIt.deleteUser(userIds.get(1), ResponseHandler("deleteUser",200, "SUCC", signal));
        after("deleteUser");

        before();
        myVoiceIt.deleteGroup(groupId, ResponseHandler("deleteGroup",200, "SUCC", signal));
        after("deleteGroup");

        userIds.clear();
        enrollmentIds.clear();

    }

    @Test
    public void TestVoice() {

        // Download Voice Enrollments
        downloadFile("https://drive.voiceit.io/files/enrollmentA1.wav", "enrollmentA1.wav");
        downloadFile("https://drive.voiceit.io/files/enrollmentA2.wav", "enrollmentA2.wav");
        downloadFile("https://drive.voiceit.io/files/enrollmentA3.wav", "enrollmentA3.wav");
        downloadFile("https://drive.voiceit.io/files/verificationA1.wav", "verificationA1.wav");
        downloadFile("https://drive.voiceit.io/files/enrollmentA4.wav", "verificationA2.wav");
        downloadFile("https://drive.voiceit.io/files/enrollmentC1.wav", "enrollmentC1.wav");
        downloadFile("https://drive.voiceit.io/files/enrollmentC2.wav", "enrollmentC2.wav");
        downloadFile("https://drive.voiceit.io/files/enrollmentC3.wav", "enrollmentC3.wav");

        before();
        myVoiceIt.createUser(ResponseHandler("createUser",201, "SUCC", signal));
        after("createUser");

        before();
        myVoiceIt.createUser(ResponseHandler("createUser",201, "SUCC", signal));
        after("createUser");

        before();
        myVoiceIt.createGroup("Sample Group Description", ResponseHandler("createGroup",201,
                "SUCC", signal));
        after("createGroup");

        before();
        myVoiceIt.addUserToGroup(groupId, userIds.get(0), ResponseHandler("addUserToGroup",200, "SUCC", signal));
        after("addUserToGroup");

        before();
        myVoiceIt.addUserToGroup(groupId, userIds.get(1), ResponseHandler("addUserToGroup",200, "SUCC", signal));
        after("addUserToGroup");

        before();
        myVoiceIt.createVoiceEnrollment(userIds.get(0), "en-US", phrase, dir+"/enrollmentA1.wav",
                ResponseHandler("createVoiceEnrollment",201, "SUCC", signal));
        after("createVoiceEnrollment");

        before();
        myVoiceIt.createVoiceEnrollment(userIds.get(0), "en-US", phrase, dir+"/enrollmentA2.wav",
                ResponseHandler("createVoiceEnrollment",201, "SUCC", signal));
        after("createVoiceEnrollment");

        before();
        myVoiceIt.createVoiceEnrollment(userIds.get(0), "en-US", phrase, dir+"/enrollmentA3.wav",
                ResponseHandler("createVoiceEnrollment",201, "SUCC", signal));
        after("createVoiceEnrollment");

        before();
        myVoiceIt.getAllVoiceEnrollments(userIds.get(0),ResponseHandler("getAllVoiceEnrollments",200, "SUCC", signal));
        after("getAllVoiceEnrollments");

        before();
        myVoiceIt.createVoiceEnrollment(userIds.get(1), "en-US", phrase, new File(dir+"/enrollmentC1.wav"),
                ResponseHandler("createVoiceEnrollment",201, "SUCC", signal));
        after("createVoiceEnrollment");

        before();
        myVoiceIt.createVoiceEnrollment(userIds.get(1), "en-US", phrase, new File(dir+"/enrollmentC2.wav"),
                ResponseHandler("createVoiceEnrollment",201, "SUCC", signal));
        after("createVoiceEnrollment");

        before();
        myVoiceIt.createVoiceEnrollment(userIds.get(1), "en-US", phrase, new File(dir+"/enrollmentC3.wav"),
                ResponseHandler("createVoiceEnrollment",201, "SUCC", signal));
        after("createVoiceEnrollment");

        before();
        myVoiceIt.voiceVerification(userIds.get(0), "en-US", phrase, dir+"/verificationA1.wav",
                ResponseHandler("voiceVerification",200, "SUCC", signal));
        after("voiceVerification");

        // Cleanup

        before();
        myVoiceIt.deleteAllEnrollments(userIds.get(0), ResponseHandler("deleteAllVoiceEnrollments",200, "SUCC", signal));
        after("deleteAllVoiceEnrollments");

        before();
        myVoiceIt.deleteAllEnrollments(userIds.get(1), ResponseHandler("deleteAllVoiceEnrollments",200, "SUCC", signal));
        after("deleteAllVoiceEnrollments");

        before();
        myVoiceIt.deleteUser(userIds.get(0), ResponseHandler("deleteUser",200, "SUCC", signal));
        after("deleteUser");

        before();
        myVoiceIt.deleteUser(userIds.get(1), ResponseHandler("deleteUser",200, "SUCC", signal));
        after("deleteUser");

        before();
        myVoiceIt.deleteGroup(groupId, ResponseHandler("deleteGroup",200, "SUCC", signal));
        after("deleteGroup");

        userIds.clear();
        enrollmentIds.clear();

        deleteFile("enrollmentA1.wav");
        deleteFile("enrollmentA2.wav");
        deleteFile("enrollmentA3.wav");
        deleteFile("verificationA1.wav");
        deleteFile("verificationA2.wav");
        deleteFile("enrollmentC1.wav");
        deleteFile("enrollmentC2.wav");
        deleteFile("enrollmentC3.wav");

        // By URL

        before();
        myVoiceIt.createUser(ResponseHandler("createUser",201, "SUCC", signal));
        after("createUser");

        before();
        myVoiceIt.createUser(ResponseHandler("createUser",201, "SUCC", signal));
        after("createUser");

        before();
        myVoiceIt.createGroup("Sample Group Description", ResponseHandler("createGroup",201,
                "SUCC", signal));
        after("createGroup");

        before();
        myVoiceIt.addUserToGroup(groupId, userIds.get(0), ResponseHandler("addUserToGroup",200, "SUCC", signal));
        after("addUserToGroup");

        before();
        myVoiceIt.addUserToGroup(groupId, userIds.get(1), ResponseHandler("addUserToGroup",200, "SUCC", signal));
        after("addUserToGroup");

        before();
        myVoiceIt.createVoiceEnrollmentByUrl(userIds.get(0), "en-US", phrase, "https://drive.voiceit.io/files/enrollmentA1.wav",
                ResponseHandler("createVoiceEnrollmentByUrl",201, "SUCC", signal));
        after("createVoiceEnrollmentByUrl");

        before();
        myVoiceIt.createVoiceEnrollmentByUrl(userIds.get(0), "en-US", phrase, "https://drive.voiceit.io/files/enrollmentA2.wav",
                ResponseHandler("createVoiceEnrollmentByUrl",201, "SUCC", signal));
        after("createVoiceEnrollmentByUrl");

        before();
        myVoiceIt.createVoiceEnrollmentByUrl(userIds.get(0), "en-US", phrase, "https://drive.voiceit.io/files/enrollmentA3.wav",
                ResponseHandler("createVoiceEnrollmentByUrl",201, "SUCC", signal));
        after("createVoiceEnrollmentByUrl");

        before();
        myVoiceIt.createVoiceEnrollmentByUrl(userIds.get(1), "en-US", phrase, "https://drive.voiceit.io/files/enrollmentC1.wav",
                ResponseHandler("createVoiceEnrollmentByUrl",201, "SUCC", signal));
        after("createVoiceEnrollmentByUrl");

        before();
        myVoiceIt.createVoiceEnrollmentByUrl(userIds.get(1), "en-US", phrase, "https://drive.voiceit.io/files/enrollmentC2.wav",
                ResponseHandler("createVoiceEnrollmentByUrl",201, "SUCC", signal));
        after("createVoiceEnrollmentByUrl");

        before();
        myVoiceIt.createVoiceEnrollmentByUrl(userIds.get(1), "en-US", phrase, "https://drive.voiceit.io/files/enrollmentC3.wav",
                ResponseHandler("createVoiceEnrollmentByUrl",201, "SUCC", signal));
        after("createVoiceEnrollmentByUrl");

        before();
        myVoiceIt.voiceVerificationByUrl(userIds.get(0), "en-US", phrase, "https://drive.voiceit.io/files/verificationA1.wav",
                ResponseHandler("voiceVerificationByUrl",200, "SUCC", signal));
        after("voiceVerificationByUrl");

        before();
        myVoiceIt.voiceIdentificationByUrl(groupId, "en-US", phrase, "https://drive.voiceit.io/files/enrollmentA4.wav",
                ResponseHandler("voiceIdentificationByUrl",200, "SUCC", signal));
        after("voiceIdentificationByUrl");
        assertEquals(userIds.get(0), userIds.get(2));

        // Cleanup
        before();
        myVoiceIt.deleteAllEnrollments(userIds.get(0), ResponseHandler("deleteAllVoiceEnrollments",200, "SUCC", signal));
        after("deleteAllVoiceEnrollments");

        before();
        myVoiceIt.deleteAllEnrollments(userIds.get(1), ResponseHandler("deleteAllVoiceEnrollments",200, "SUCC", signal));
        after("deleteAllVoiceEnrollments");

        before();
        myVoiceIt.deleteUser(userIds.get(0), ResponseHandler("deleteUser",200, "SUCC", signal));
        after("deleteUser");

        before();
        myVoiceIt.deleteUser(userIds.get(1), ResponseHandler("deleteUser",200, "SUCC", signal));
        after("deleteUser");

        before();
        myVoiceIt.deleteGroup(groupId, ResponseHandler("deleteGroup",200, "SUCC", signal));
        after("deleteGroup");

        userIds.clear();
        enrollmentIds.clear();

    }

    @Test
    public void TestFace() {

        // Download Face Enrollments
        downloadFile("https://drive.voiceit.io/files/videoEnrollmentD1.mov", "videoEnrollmentD1.mov");
        downloadFile("https://drive.voiceit.io/files/videoEnrollmentD2.mov", "videoEnrollmentD2.mov");
        downloadFile("https://drive.voiceit.io/files/videoEnrollmentD3.mov", "videoEnrollmentD3.mov");
        downloadFile("https://drive.voiceit.io/files/videoVerificationD1.mov", "videoVerificationD1.mov");
        downloadFile("https://drive.voiceit.io/files/videoVerificationD2.mov", "videoVerificationD2.mov");
        downloadFile("https://drive.voiceit.io/files/videoEnrollmentC1.mov", "faceEnrollmentC1.mov");
        downloadFile("https://drive.voiceit.io/files/videoEnrollmentC2.mov", "faceEnrollmentC2.mov");
        downloadFile("https://drive.voiceit.io/files/videoEnrollmentC3.mov", "faceEnrollmentC3.mov");

        before();
        myVoiceIt.createUser(ResponseHandler("createUser",201, "SUCC", signal));
        after("createUser");

        before();
        myVoiceIt.createUser(ResponseHandler("createUser",201, "SUCC", signal));
        after("createUser");

        before();
        myVoiceIt.createGroup("Sample Group Description", ResponseHandler("createGroup",201,
                "SUCC", signal));
        after("createGroup");

        before();
        myVoiceIt.addUserToGroup(groupId, userIds.get(0), ResponseHandler("addUserToGroup",200, "SUCC", signal));
        after("addUserToGroup");

        before();
        myVoiceIt.addUserToGroup(groupId, userIds.get(1), ResponseHandler("addUserToGroup",200, "SUCC", signal));
        after("addUserToGroup");

        before();
        myVoiceIt.createFaceEnrollment(userIds.get(0), dir+"/videoEnrollmentD1.mov",
                ResponseHandler("createFaceEnrollment",201, "SUCC", signal));
        after("createFaceEnrollment");

        before();
        myVoiceIt.createFaceEnrollment(userIds.get(0), dir+"/videoEnrollmentD2.mov",
                ResponseHandler("createFaceEnrollment",201, "SUCC", signal));
        after("createFaceEnrollment");

        before();
        myVoiceIt.createFaceEnrollment(userIds.get(0), dir+"/videoEnrollmentD3.mov",
                ResponseHandler("createFaceEnrollment",201, "SUCC", signal));
        after("createFaceEnrollment");

        before();
        myVoiceIt.getAllFaceEnrollments(userIds.get(0),
                ResponseHandler("getAllFaceEnrollments",200, "SUCC", signal));
        after("getAllFaceEnrollments");

        before();
        myVoiceIt.createFaceEnrollment(userIds.get(1), dir+"/faceEnrollmentC1.mov",
                ResponseHandler("createFaceEnrollment",201, "SUCC", signal));
        after("createFaceEnrollment");

        before();
        myVoiceIt.createFaceEnrollment(userIds.get(1), dir+"/faceEnrollmentC2.mov",
                ResponseHandler("createFaceEnrollment",201, "SUCC", signal));
        after("createFaceEnrollment");

        before();
        myVoiceIt.createFaceEnrollment(userIds.get(1), dir+"/faceEnrollmentC3.mov",
                ResponseHandler("createFaceEnrollment",201, "SUCC", signal));
        after("createFaceEnrollment");

        before();
        myVoiceIt.faceVerification(userIds.get(0), dir+"/videoVerificationD1.mov",
                ResponseHandler("faceVerification",200, "SUCC", signal));
        after("faceVerification");

        // Cleanup

        before();
        myVoiceIt.deleteAllEnrollments(userIds.get(0), ResponseHandler("deleteAllFaceEnrollments",200, "SUCC", signal));
        after("deleteAllFaceEnrollments");

        before();
        myVoiceIt.deleteAllEnrollments(userIds.get(1), ResponseHandler("deleteAllFaceEnrollments",200, "SUCC", signal));
        after("deleteAllFaceEnrollments");

        before();
        myVoiceIt.deleteUser(userIds.get(0), ResponseHandler("deleteUser",200, "SUCC", signal));
        after("deleteUser");

        before();
        myVoiceIt.deleteUser(userIds.get(1), ResponseHandler("deleteUser",200, "SUCC", signal));
        after("deleteUser");

        before();
        myVoiceIt.deleteGroup(groupId, ResponseHandler("deleteGroup",200, "SUCC", signal));
        after("deleteGroup");

        userIds.clear();
        faceEnrollmentIds.clear();

        deleteFile("videoEnrollmentD1.mov");
        deleteFile("videoEnrollmentD2.mov");
        deleteFile("videoEnrollmentD3.mov");
        deleteFile("videoVerificationD1.mov");
        deleteFile("videoVerificationD2.mov");
        deleteFile("faceEnrollmentC1.mov");
        deleteFile("faceEnrollmentC2.mov");
        deleteFile("faceEnrollmentC3.mov");

        // By Url

        before();
        myVoiceIt.createUser(ResponseHandler("createUser",201, "SUCC", signal));
        after("createUser");

        before();
        myVoiceIt.createUser(ResponseHandler("createUser",201, "SUCC", signal));
        after("createUser");

        before();
        myVoiceIt.createGroup("Sample Group Description", ResponseHandler("createGroup",201,
                "SUCC", signal));
        after("createGroup");

        before();
        myVoiceIt.addUserToGroup(groupId, userIds.get(0), ResponseHandler("addUserToGroup",200, "SUCC", signal));
        after("addUserToGroup");

        before();
        myVoiceIt.addUserToGroup(groupId, userIds.get(1), ResponseHandler("addUserToGroup",200, "SUCC", signal));
        after("addUserToGroup");

        before();
        myVoiceIt.createFaceEnrollmentByUrl(userIds.get(0), "https://drive.voiceit.io/files/videoEnrollmentD1.mov",
                ResponseHandler("createFaceEnrollmentByUrl",201, "SUCC", signal));
        after("createFaceEnrollmentByUrl");

        before();
        myVoiceIt.createFaceEnrollmentByUrl(userIds.get(0), "https://drive.voiceit.io/files/videoEnrollmentD2.mov",
                ResponseHandler("createFaceEnrollmentByUrl",201, "SUCC", signal));
        after("createFaceEnrollmentByUrl");

        before();
        myVoiceIt.createFaceEnrollmentByUrl(userIds.get(0), "https://drive.voiceit.io/files/videoEnrollmentD3.mov",
                ResponseHandler("createFaceEnrollmentByUrl",201, "SUCC", signal));
        after("createFaceEnrollmentByUrl");

        before();
        myVoiceIt.createFaceEnrollmentByUrl(userIds.get(1), "https://drive.voiceit.io/files/videoEnrollmentC1.mov",
                ResponseHandler("createFaceEnrollmentByUrl",201, "SUCC", signal));
        after("createFaceEnrollmentByUrl");

        before();
        myVoiceIt.createFaceEnrollmentByUrl(userIds.get(1), "https://drive.voiceit.io/files/videoEnrollmentC2.mov",
                ResponseHandler("createFaceEnrollmentByUrl",201, "SUCC", signal));
        after("createFaceEnrollmentByUrl");

        before();
        myVoiceIt.createFaceEnrollmentByUrl(userIds.get(1), "https://drive.voiceit.io/files/videoEnrollmentC3.mov",
                ResponseHandler("createFaceEnrollmentByUrl",201, "SUCC", signal));
        after("createFaceEnrollmentByUrl");


        before();
        myVoiceIt.faceVerificationByUrl(userIds.get(0), "https://drive.voiceit.io/files/videoVerificationD1.mov",
                ResponseHandler("faceVerificationByUrl",200, "SUCC", signal));
        after("faceVerificationByUrl");

        // Cleanup
        before();

        before();
        myVoiceIt.deleteAllEnrollments(userIds.get(1), ResponseHandler("deleteAllFaceEnrollments",200, "SUCC", signal));
        after("deleteAllFaceEnrollments");

        before();
        myVoiceIt.deleteUser(userIds.get(0), ResponseHandler("deleteUser",200, "SUCC", signal));
        after("deleteUser");

        before();
        myVoiceIt.deleteUser(userIds.get(1), ResponseHandler("deleteUser",200, "SUCC", signal));
        after("deleteUser");

        before();
        myVoiceIt.deleteGroup(groupId, ResponseHandler("deleteGroup",200, "SUCC", signal));
        after("deleteGroup");

        userIds.clear();
        faceEnrollmentIds.clear();
    }
}
