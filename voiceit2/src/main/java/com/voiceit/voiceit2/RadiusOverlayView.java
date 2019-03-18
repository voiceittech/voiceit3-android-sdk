package com.voiceit.voiceit2;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.google.android.gms.vision.face.Face;

import java.util.ArrayList;
import java.util.Vector;

class RadiusOverlayView extends LinearLayout {
    private Bitmap mWindowFrame;

    private final String mTAG = "RadiusOverlayView";

    private String mDisplayText = "";
    private boolean mUpdateText = true;

    private int mViewHeight;
    private float mViewMid;
    private int mViewWidth;
    private int portraitHeight;

    private double mProgressCircleEndAngle = 0;
    private double mProgressCircleStartAngle = 270; // Start at the top and go clockwise
    private final Paint invertedCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float circleRadius;
    private float circleCenterX;
    private float circleCenterY;
    private RectF screenRectangle;

    private boolean mLockTextDisplay = false;
    private final Vector<String> textLines = new Vector<>();
    private final Vector<Rect> textBounds = new Vector<>();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean mDrawingProgressCircle = false;
    private double mDrawingProgressCircleStartTime;
    private final Paint progressCirclePaint = new Paint();

    private boolean mDrawWaveform = false;
    private float mWaveAmplitude = 0.0f;
    private float mWaveformMaxAmplitude;
    private final int mNumberOfWaves = 4;
    private final ArrayList<Paint> mWaveformLinePaints = new ArrayList<>();
    private Path mWaveformPath;
    private float mWaveformPhase = 0.0f;
    private final int mWaveColor = getResources().getColor(R.color.waveform);

    private Bitmap mPicture = null;
    public boolean displayPicture = false;

    public void unlockDisplay() {
        mLockTextDisplay = false;
    }

    public void updateDisplayText(String str) {
        if(!mLockTextDisplay) {
            mDisplayText = str;
            mUpdateText = true;
            this.invalidate();
        }
    }

    public void updateDisplayTextAndLock(String str) {
        mDisplayText = str;
        mUpdateText = true;
        mLockTextDisplay = true;
        this.invalidate();
    }

    public void startDrawingProgressCircle() {
        mDrawingProgressCircle = true;
        this.mDrawingProgressCircleStartTime = System.currentTimeMillis();
    }

    public void setProgressCircleAngle(double startAngle, double endAngle) {
        mProgressCircleStartAngle = startAngle; // 270 is at the top of the circle
        mProgressCircleEndAngle = endAngle;
        this.invalidate();
    }

    public void setProgressCircleColor(int colorId){
        progressCirclePaint.setColor(colorId);
        this.invalidate();
    }

    public void setWaveformMaxAmplitude(float amplitude) {
        // Clamp amp value
        final float mWaveformPhaseShift = -0.25f;
        mWaveformPhase += mWaveformPhaseShift;
        mWaveAmplitude = (mWaveAmplitude + Math.max((amplitude * 1.6f )/ 5590.5337f, 0.01f)) / 2.0f;
        this.invalidate();
    }

    private void setStyle(TypedArray attr){
        String type = attr.getString(R.styleable.RadiusOverlayView_view_type);
        if(type.equals("voice")) {
            mDrawWaveform = true;

            for (int i = 0; i < mNumberOfWaves; i++) {
                float multiplier = Math.min(1.0f, (((1.0f - (((float) i) / ((float) mNumberOfWaves))) / 3.0f) * 2.0f) + 0.33333334f);
                Paint p;
                if (i == 0) {
                    p = new Paint(1);
                    p.setColor(mWaveColor);
                    p.setStrokeWidth(10);
                    p.setStyle(Paint.Style.STROKE);
                    mWaveformLinePaints.add(p);
                } else {
                    p = new Paint(1);
//                    Log.v("Color", BuildConfig.FLAVOR + ((int) ((((double) (1.0f * multiplier)) * 0.7d) * 255.0d)));
                    p.setColor(mWaveColor);
                    p.setAlpha((int) ((((double) (1.0f * multiplier)) * 0.8d) * 255.0d));
                    p.setStrokeWidth(3);
                    p.setStyle(Paint.Style.STROKE);
                    mWaveformLinePaints.add(p);
                }
            }
            mWaveformPath = new Path();
        }
        attr.recycle();
    }

    void setLowLightMode(Boolean lowLightMode) {
        if(lowLightMode) {
            invertedCirclePaint.setColor(getResources().getColor(R.color.portraitBackgroundLowLight));
            textPaint.setColor(getResources().getColor(R.color.instructionalTextLowLight));
        } else {
            invertedCirclePaint.setColor(getResources().getColor(R.color.portraitBackground));
            textPaint.setColor(getResources().getColor(R.color.instructionalText));
        }
        this.invalidate();

    }

    private void setup() {

        // Create bitmap to draw on
        mWindowFrame = Bitmap.createBitmap(Resources.getSystem().getDisplayMetrics().widthPixels, Resources.getSystem().getDisplayMetrics().heightPixels, Bitmap.Config.ARGB_8888);

        // overlay
        screenRectangle = new RectF(0, 0, mViewWidth, mViewHeight);

        portraitHeight = (int) (mViewHeight * .8);

        // rect inverted circle overlay
        invertedCirclePaint.setColor(getResources().getColor(R.color.portraitBackground));
        invertedCirclePaint.setAlpha(230);

        // inverted circle for portrait
        invertedCirclePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OUT)); // A out B http://en.wikipedia.org/wiki/File:Alpha_compositing.svg
        circleRadius = Math.min(mViewWidth, portraitHeight) * 0.475f;
        circleCenterX = mViewWidth / 2f;
        circleCenterY = mViewHeight / 2.5f;

        // progressCircle
        progressCirclePaint.setAntiAlias(true);
        progressCirclePaint.setStyle(Paint.Style.STROKE);
        progressCirclePaint.setStrokeCap(Paint.Cap.SQUARE);
        progressCirclePaint.setStrokeWidth(getContext().getResources().getDisplayMetrics().density * 10);
        progressCirclePaint.setColor(getResources().getColor(R.color.progressCircle));

        // text color
        textPaint.setColor(getResources().getColor(R.color.instructionalText));
    }

    public void setPicture(byte[] data) {

        if(data == null) {
            mPicture = null;
            return;
        }

        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);

        Matrix matrix = new Matrix();

        // Flip over X axis
        matrix.postScale(-1, 1, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);

        // Scale to preview size based off orientation
        if (CameraSourcePreview.isPortraitMode(getContext())) {
            matrix.postScale( ((float) CameraSourcePreview.childHeight) / bitmap.getWidth(),
                    ((float) CameraSourcePreview.childWidth) / bitmap.getHeight());
            matrix.postRotate(90);
        } else {
            matrix.postScale( ((float) CameraSourcePreview.childWidth) / bitmap.getWidth(),
                    ((float) CameraSourcePreview.childHeight) / bitmap.getHeight());
        }

        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        mPicture = bitmap;

        this.invalidate();
    }

    public RadiusOverlayView(Context context) {
        super(context);
    }

    public RadiusOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setStyle(context.obtainStyledAttributes(attrs, R.styleable.RadiusOverlayView, 0, 0));
    }

    public RadiusOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setStyle(context.obtainStyledAttributes(attrs, R.styleable.RadiusOverlayView, defStyleAttr, 0));
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public RadiusOverlayView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setStyle(context.obtainStyledAttributes(attrs, R.styleable.RadiusOverlayView, defStyleAttr, 0));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        // Draw captured picture of user
        if(mPicture != null && displayPicture) {
            canvas.drawBitmap(mPicture, 0, 0, new Paint());
        }

        createPortraitFrame(); // Creation of the window frame
        canvas.drawBitmap(mWindowFrame, 0, 0, null);
    }

    private void createPortraitFrame() {
        // Reset bitmap for redraw
        mWindowFrame.eraseColor(Color.TRANSPARENT);

        // Create a canvas to draw onto the new image
        Canvas canvas = new Canvas(mWindowFrame);

        // Draw waveform
        if (mDrawWaveform) {

            canvas.drawColor(getResources().getColor(R.color.portraitBackground));

            final float mWaveformDensity = 1.0f;
            final float mWaveformFrequency = 1.2f;

            // Based off: https://github.com/ankitmhatre/AndroidSiriWave
            for (int i = 0; i < mNumberOfWaves; i++) {
                float normedAmplitude = ((1.5f * (1.0f - (((float) i) / ((float) mNumberOfWaves)))) - 0.99f) * mWaveAmplitude;

                // Clamp normedAmplitude so waves don't stretch outside screen
                normedAmplitude = (normedAmplitude > 1.0f) ? 1.0f : normedAmplitude;
                normedAmplitude = (normedAmplitude < -1.0f) ? -1.0f : normedAmplitude;

                mWaveformPath.reset();
                float x = 0.0f;
                while (x < ((float) mViewWidth) + mWaveformDensity) {

                    double y = (((((double) mWaveformMaxAmplitude) * ((-Math.pow((double) ((x / mViewMid) - 1.0f), 2.0d)) + 1.0d))
                            * ((double) normedAmplitude))
                            * Math.sin(((6.282d * ((double) (x / ((float) mViewWidth))))
                            * ((double) mWaveformFrequency)) + ((double) mWaveformPhase)))
                            + (((double) mViewHeight) / 2.0d);

                    if (x == 0.0f) {
                        mWaveformPath.moveTo(x, (float) y);
                    } else {
                        mWaveformPath.lineTo(x, (float) y);
                    }
                    x += mWaveformDensity;
                }
                canvas.drawPath(mWaveformPath, mWaveformLinePaints.get(i));
            }

        } else { // Draw portrait

            // Draw rect inverted circle
            canvas.drawRect(screenRectangle, invertedCirclePaint);

            // Draw inverted circle
            canvas.drawCircle(circleCenterX, circleCenterY, circleRadius, invertedCirclePaint);

            final double mRecordingDuration = 4800; // ~4.8 seconds
            // Draw progress circle
            if (mDrawingProgressCircle) {
                double elapsedTime = System.currentTimeMillis() - mDrawingProgressCircleStartTime;
                if (elapsedTime <= mRecordingDuration) {
                    mProgressCircleEndAngle = 360 * (elapsedTime / mRecordingDuration);
                    this.invalidate();
                } else {
                    mProgressCircleEndAngle = 359.999;
                    mDrawingProgressCircle = false;
                }
            }

            final RectF portrait = new RectF();
            portrait.set(circleCenterX - circleRadius, circleCenterY - circleRadius, circleCenterX + circleRadius, circleCenterY + circleRadius);
            Path circlePath = new Path();
            // Start at the top and go clockwise
            circlePath.arcTo(portrait, (float) mProgressCircleStartAngle, (float) mProgressCircleEndAngle, true);
            canvas.drawPath(circlePath, progressCirclePaint);
        }

        // Skip processing and displaying text if empty
        if (mDisplayText.isEmpty()) {
            return;
        }

        // No need to re-calculate text cycle
        if (mUpdateText) {
            mUpdateText = false;

            int textSize = 1;
            // text size in pixels
            textPaint.setTextSize((textSize));

            StringBuilder lineBuilder = new StringBuilder();
            // Reset and assign whole text string to first line
            textBounds.clear();
            textLines.clear();
            textLines.add(mDisplayText);
            textBounds.add(new Rect());

            // Get textBounds of first line
            textPaint.getTextBounds(textLines.get(0), 0, textLines.get(0).length(), textBounds.get(0));

            int paddingWidth = mViewWidth / 10;
            int paddingHeight = mViewHeight / 8;

            int textArea = textBounds.get(0).height() * textBounds.get(0).width();
            int textBoxArea = (mViewHeight - portraitHeight - paddingHeight) * (mViewWidth - paddingWidth);

            // To scale for max text size on different phones
            float maxTextSize = getContext().getResources().getDisplayMetrics().density * 40;

            // Rescale text to fit area of text box
            while (textArea < (textBoxArea - (paddingWidth * paddingHeight)) && textSize < maxTextSize) {
                textSize++;
                textPaint.setTextSize(textSize);
                textPaint.getTextBounds(textLines.get(0), 0, textLines.get(0).length(), textBounds.get(0));
                textArea = textBounds.get(0).height() * textBounds.get(0).width();
            }

            // Move word(s) onto next line if text is wider than the screen
            int line_i = 0;
            while (textBounds.get(line_i).width() >= mViewWidth - paddingWidth) {
                // Add textBounds of line
                textBounds.add(new Rect());
                // Move last word to front of line builder
                lineBuilder.insert(0, textLines.get(line_i).substring(textLines.get(line_i).lastIndexOf(" ") + 1) + " ");
                // Cut word off end of current line
                textLines.set(line_i, textLines.get(line_i).substring(0, textLines.get(line_i).lastIndexOf(" ")));
                // Set textBounds of current line
                textPaint.getTextBounds(textLines.get(line_i), 0, textLines.get(line_i).length(), textBounds.get(line_i));

                // If current line is not wider than the screen anymore
                if (textBounds.get(line_i).width() < mViewWidth - paddingWidth) {
                    // Assign line builder string to next line and reset
                    textLines.add(lineBuilder.toString());
                    lineBuilder.setLength(0);
                    // Increment and measure next line
                    line_i++;
                    textBounds.add(new Rect());
                    textPaint.getTextBounds(textLines.get(line_i), 0, textLines.get(line_i).length(), textBounds.get(line_i));
                }
            }
        }

        // Position and draw text
        Vector<Integer> x = new Vector<>();
        Vector<Integer> y = new Vector<>();
        for (int i = 0; i < textLines.size(); i++) {
            int verticalOffset = (int) ((textBounds.get(0).height() * 1.2) * i);
            // Center width of screen
            x.add((mViewWidth - textBounds.get(i).width()) / 2);
            // Center height adjusted for portrait and other lines
            y.add((portraitHeight
                    + textBounds.get(0).height()
                    + (textBounds.get(0).height() / 2) // So not directly touching the bottom of the portrait
                    + verticalOffset));
            // Draw line of text to screen
            canvas.drawText(textLines.get(i), x.get(i), y.get(i), textPaint);
        }
    }

    public boolean insidePortraitCircle(Activity activity, Face face) {

        final float faceX = (float)mViewWidth - (face.getPosition().x + face.getWidth() / 2)
                * (float)mViewWidth / (float)CameraSourcePreview.previewWidth;
        final float faceY = (face.getPosition().y + face.getHeight() / 2)
                * (float)mViewHeight / (float)CameraSourcePreview.previewHeight;

        final float radius = circleRadius * 0.7f;

        // Face close enough to camera
        if(face.getWidth() < (circleRadius/2)
                || face.getHeight() < (circleRadius/2)) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateDisplayText(getContext().getString(R.string.MOVE_CLOSER));
                }
            });
            return false;
        }

        // Face inside portrait circle
        return (faceX < circleCenterX + radius
                && faceX > circleCenterX - radius
                && faceY < circleCenterY + radius
                && faceY > circleCenterY - radius);
    }

    @Override
    public boolean isInEditMode() {
        return true;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        mViewHeight = getHeight();
        mViewWidth = getWidth();
        mViewMid = ((float) mViewWidth) / 2.0f;
        mWaveformMaxAmplitude = (((float) mViewHeight) / 4.0f) - 4.0f;

        mWindowFrame = null; // If the layout changes null the frame so it can be recreated with the new width and height

        // Setup for display
        setup();
    }
}
