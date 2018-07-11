package com.voiceit.voiceit2sdk;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Switch;

import com.loopj.android.http.JsonHttpResponseHandler;
import org.json.JSONObject;
import cz.msebera.android.httpclient.Header;

import com.voiceit.voiceit2.VoiceItAPI2;

public class MainActivity extends AppCompatActivity {

    private VoiceItAPI2 myVoiceIt;
    private String userId = "USER_ID";
    private String phrase = "Never forget tomorrow is a new day";
    private String contentLanguage = "en-US";
    private boolean doLivenessCheck = false; // Liveness detection is not used for enrollment views

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myVoiceIt = new VoiceItAPI2("API_KEY","API_TOK");
    }

    public void toggleLiveness(View view) {
        Switch s = findViewById(R.id.switch1);
        doLivenessCheck = s.isChecked();
    }

    public void encapsulatedVoiceEnrollment(View view) {
        myVoiceIt.encapsulatedVoiceEnrollment(this, userId, contentLanguage, phrase, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                System.out.println("encapsulatedVoiceEnrollment Result : " + response.toString());
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                if (errorResponse != null) {
                    System.out.println("encapsulatedVoiceEnrollment Result : " + errorResponse.toString());
                }
            }
        });
    }

    public void encapsulatedVoiceVerification(View view) {
        myVoiceIt.encapsulatedVoiceVerification(this, userId, contentLanguage, phrase, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                System.out.println("encapsulatedVoiceVerification Result : " + response.toString());
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                if (errorResponse != null) {
                    System.out.println("encapsulatedVoiceVerification Result : " + errorResponse.toString());
                }
            }
        });
    }

    public void encapsulatedVideoEnrollment(View view) {
        myVoiceIt.encapsulatedVideoEnrollment(this, userId, contentLanguage, phrase, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                System.out.println("encapsulatedVideoEnrollment Result : " + response.toString());
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                if (errorResponse != null) {
                    System.out.println("encapsulatedVideoEnrollment Result : " + errorResponse.toString());
                }
            }
        });
    }

    public void encapsulatedVideoVerification(View view) {
        myVoiceIt.encapsulatedVideoVerification(this, userId, contentLanguage, phrase, doLivenessCheck, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                System.out.println("encapsulatedVideoVerification Result : " + response.toString());
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                if (errorResponse != null) {
                    System.out.println("encapsulatedVideoVerification Result : " + errorResponse.toString());
                }
            }
        });
    }

    public void encapsulatedFaceEnrollment(View view) {
        myVoiceIt.encapsulatedFaceEnrollment(this, userId, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                System.out.println("encapsulatedFaceEnrollment Result : " + response.toString());
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                if (errorResponse != null) {
                    System.out.println("encapsulatedFaceEnrollment Result : " + errorResponse.toString());
                }
            }
        });
    }

    public void encapsulatedFaceVerification(View view) {
        myVoiceIt.encapsulatedFaceVerification(this, userId, doLivenessCheck, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                System.out.println("encapsulatedFaceVerification Result : " + response.toString());
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                if (errorResponse != null) {
                    System.out.println("encapsulatedFaceVerification Result : " + errorResponse.toString());
                }
            }
        });
    }

}
