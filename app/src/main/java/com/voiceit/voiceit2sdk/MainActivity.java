package com.voiceit.voiceit2sdk;

import android.app.Activity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.loopj.android.http.JsonHttpResponseHandler;
import org.json.JSONObject;
import cz.msebera.android.httpclient.Header;

import com.voiceit.voiceit2.VoiceItAPI2;


public class MainActivity extends AppCompatActivity {

    public VoiceItAPI2 myVoiceIt2;
    public String userId = "usr_047a6db16bc84150a8f367289df39061";
    public Activity mActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myVoiceIt2 = new VoiceItAPI2("key_f3a9fb29944a4e4180d4c98e7f03c713","tok_2b13ba7ac80741c98113c301da31001b");
        mActivity = this;
    }

    public void encapsulatedVideoEnrollment(View view) {
        myVoiceIt2.encapsulatedVideoEnrollment(mActivity, userId, "en-US", "My face and voice identify me", new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                System.out.println("VideoEnrollmentView JSONResult : " + response.toString());
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                if (errorResponse != null) {
                    System.out.println("VideoEnrollmentView JSONResult : " + errorResponse.toString());
                }
            }
        });
    }

    public void encapsulatedVideoVerification(View view) {
        myVoiceIt2.encapsulatedVideoVerification(this, userId, "en-US", "My face and voice identify me", new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                System.out.println("VideoVerificationView JSONResult : " + response.toString());
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                if (errorResponse != null) {
                    System.out.println("VideoVerificationView JSONResult : " + errorResponse.toString());
                }
            }
        });
    }

}
