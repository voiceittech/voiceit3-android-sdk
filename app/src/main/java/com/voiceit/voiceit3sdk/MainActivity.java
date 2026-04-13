package com.voiceit.voiceit3sdk;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.voiceit.voiceit3.Callback;
import com.voiceit.voiceit3.VoiceItAPI3;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private VoiceItAPI3 myVoiceIt;
    private TextInputEditText apiKeyInput, apiTokenInput, userIdInput, phraseInput;
    private Spinner languageSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        apiKeyInput = findViewById(R.id.apiKeyInput);
        apiTokenInput = findViewById(R.id.apiTokenInput);
        userIdInput = findViewById(R.id.userIdInput);
        phraseInput = findViewById(R.id.phraseInput);
        languageSpinner = findViewById(R.id.languageSpinner);

        String[] languages = {"en-US", "es-ES", "no-STT"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, languages);
        languageSpinner.setAdapter(adapter);

        findViewById(R.id.voiceEnrollBtn).setOnClickListener(v -> doVoiceEnrollment());
        findViewById(R.id.faceEnrollBtn).setOnClickListener(v -> doFaceEnrollment());
        findViewById(R.id.videoEnrollBtn).setOnClickListener(v -> doVideoEnrollment());
        findViewById(R.id.voiceVerifyBtn).setOnClickListener(v -> doVoiceVerification());
        findViewById(R.id.faceVerifyBtn).setOnClickListener(v -> doFaceVerification());
        findViewById(R.id.videoVerifyBtn).setOnClickListener(v -> doVideoVerification());
    }

    private boolean initSDK() {
        String apiKey = getText(apiKeyInput);
        String apiToken = getText(apiTokenInput);

        if (apiKey.isEmpty() || apiToken.isEmpty()) {
            showResult("Please enter your API Key and API Token");
            return false;
        }
        if (getText(userIdInput).isEmpty()) {
            showResult("Please enter a User ID");
            return false;
        }

        myVoiceIt = new VoiceItAPI3(apiKey, apiToken);
        return true;
    }

    private String getText(TextInputEditText input) {
        return input.getText() != null ? input.getText().toString().trim() : "";
    }

    private String getUserId() { return getText(userIdInput); }
    private String getPhrase() { return getText(phraseInput); }
    private String getLanguage() { return languageSpinner.getSelectedItem().toString(); }

    /** Reusable callback that shows the result in a dialog. */
    private Callback resultCallback(final String label) {
        return new Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                showResult(label + " successful!\n" + response.toString());
            }

            @Override
            public void onFailure(int statusCode, JSONObject errorBody, Throwable error) {
                showResult(label + " failed" + formatError(errorBody));
            }
        };
    }

    private void doVoiceEnrollment() {
        if (!initSDK()) return;
        myVoiceIt.encapsulatedVoiceEnrollment(this, getUserId(), getLanguage(), getPhrase(),
                resultCallback("Voice enrollment"));
    }

    private void doFaceEnrollment() {
        if (!initSDK()) return;
        myVoiceIt.encapsulatedFaceEnrollment(this, getUserId(), getLanguage(),
                resultCallback("Face enrollment"));
    }

    private void doVideoEnrollment() {
        if (!initSDK()) return;
        myVoiceIt.encapsulatedVideoEnrollment(this, getUserId(), getLanguage(), getPhrase(),
                resultCallback("Video enrollment"));
    }

    private void doVoiceVerification() {
        if (!initSDK()) return;
        myVoiceIt.encapsulatedVoiceVerification(this, getUserId(), getLanguage(), getPhrase(),
                resultCallback("Voice verification"));
    }

    private void doFaceVerification() {
        if (!initSDK()) return;
        myVoiceIt.encapsulatedFaceVerification(this, getUserId(), getLanguage(),
                resultCallback("Face verification"));
    }

    private void doVideoVerification() {
        if (!initSDK()) return;
        myVoiceIt.encapsulatedVideoVerification(this, getUserId(), getLanguage(), getPhrase(),
                resultCallback("Video verification"));
    }

    private void showResult(String message) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle("Result")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show());
    }

    private String formatError(JSONObject error) {
        return error != null ? "\n" + error.toString() : "";
    }
}
