package com.voiceit.voiceit2;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import java.io.IOException;

import pl.droidsonroids.gif.AnimationListener;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

public class InstructionalVideoView extends AppCompatActivity {

    private final String mTAG = "InstructionalVideoView";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_instructional_video_view);

        boolean isVideo = false;
        Bundle bundle = getIntent().getExtras();
        if(bundle != null) {
            isVideo = bundle.getBoolean("isVideo");
        }

        final ImageButton replayButton = findViewById(R.id.replayButton);
        replayButton.setVisibility(View.INVISIBLE);
        final Button continueButton = findViewById(R.id.continueButton);
        continueButton.setVisibility(View.INVISIBLE);

        GifImageView mGifImageView = findViewById(R.id.instruction_gif);
        try {
            final GifDrawable gifDrawable = new GifDrawable(getResources(), isVideo ? R.raw.android_video_verification : R.raw.android_face_verification);
            gifDrawable.setLoopCount(1);
            gifDrawable.addAnimationListener(new AnimationListener() {
                @Override
                public void onAnimationCompleted(int loopNumber) {
                    continueButton.setVisibility(View.VISIBLE);
                    continueButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Intent intent = new Intent();
                            setResult(RESULT_OK, intent);
                            finish();
                        }
                    });
                    replayButton.setVisibility(View.VISIBLE);
                    replayButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            gifDrawable.reset();
                            gifDrawable.start();
                            continueButton.setVisibility(View.INVISIBLE);
                            replayButton.setVisibility(View.INVISIBLE);
                        }
                    });
                }
            });

            mGifImageView.setImageDrawable(gifDrawable);

        } catch (IOException e) {
            Log.d(mTAG, "IOException : " + e.toString());
        }
    }
}
