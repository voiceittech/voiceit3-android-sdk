package com.voiceit.voiceit2;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import java.util.Vector;

public class RadiusOverlayView extends LinearLayout {
    private Bitmap windowFrame;
    private String displayText = "VoiceIt View";
    private int progressCircleAngle = 270; // Start at the top and go clockwise
    private int angleChunkSize = (360/8);
    private int progressCircleColor;

    public void updateDisplayText(String str) {
        displayText = str;
        this.invalidate();
    }

    public void setProgressCircleAngle(double ang) {
        progressCircleAngle = ((ang == 360) ? 359 : ((int)(ang/angleChunkSize)) * angleChunkSize);
        this.invalidate();
    }

    public void setProgressCircleColor(int colorId){
        progressCircleColor = colorId;
        this.invalidate();
    }

    public RadiusOverlayView(Context context) {
        super(context);
    }

    public RadiusOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RadiusOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public RadiusOverlayView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        createWindowFrame(); // Creation of the window frame
        canvas.drawBitmap(windowFrame, 0, 0, null);
    }

    protected void createWindowFrame() {

        windowFrame = Bitmap.createBitmap(Resources.getSystem().getDisplayMetrics().widthPixels, Resources.getSystem().getDisplayMetrics().heightPixels, Bitmap.Config.ARGB_8888); // Create a new image we will draw over the map
        Canvas osCanvas = new Canvas(windowFrame); // Create a canvas to draw onto the new image

        int portraitHeight = (int) ((double) getHeight() / 1.3);
        RectF outerRectangle = new RectF(0, 0, getWidth(), portraitHeight);

        // Draw rect inverted circle
        Paint invertedCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG); // Anti alias allows for smooth corners
        invertedCirclePaint.setColor(getResources().getColor(R.color.black)); // This is the color of the activity background
        invertedCirclePaint.setAlpha(230);
        osCanvas.drawRect(outerRectangle, invertedCirclePaint);

        // Draw inverted circle
        invertedCirclePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OUT)); // A out B http://en.wikipedia.org/wiki/File:Alpha_compositing.svg
        float centerX = getWidth() / 2;
        float centerY = getHeight() / 2.8f;
        float radius = Math.min(getWidth(), getHeight()) / 2.2f;
        osCanvas.drawCircle(centerX, centerY, radius, invertedCirclePaint);

        // Draw progress circle
        Paint progressCirclePaint = new Paint();
        progressCirclePaint.setAntiAlias(true);
        progressCirclePaint.setStyle(Paint.Style.STROKE);
        progressCirclePaint.setStrokeCap(Paint.Cap.ROUND);
        progressCirclePaint.setStrokeWidth(30);
        progressCirclePaint.setColor(progressCircleColor);

        final RectF oval = new RectF();
        oval.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
        Path myPath = new Path();
        // Start at the top and go clockwise
        myPath.arcTo(oval, 270, (float) progressCircleAngle, true);
        osCanvas.drawPath(myPath, progressCirclePaint);

        // Draw lower board background for text
        Paint textBarPaint = new Paint();
        textBarPaint.setColor(Color.rgb(0, 0, 0));
        textBarPaint.setStrokeWidth(10);
        osCanvas.drawRect(0, portraitHeight, getWidth(), getHeight(), textBarPaint);

        // Draw text
        int scale = 5;
        int fontSize = 12;
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // text color - #FFFFFF
        textPaint.setColor(Color.rgb(255, 255, 255));
        // text size in pixels
        textPaint.setTextSize((fontSize * scale));

        Vector<Rect> bounds = new Vector<>();
        Vector<String> lines = new Vector<>();
        StringBuilder lineBuilder = new StringBuilder();
        // Assign whole text string to first line
        lines.add(displayText);
        bounds.add(new Rect());

        // Get bounds of first line
        textPaint.getTextBounds(lines.get(0), 0, lines.get(0).length(), bounds.get(0));

        // Make text at least a 1/6 of the size of the text bar background
        while(bounds.get(0).height() < (getHeight() - portraitHeight)/6) {
            scale++;
            textPaint.setTextSize((fontSize * scale));
            textPaint.getTextBounds(lines.get(0), 0, lines.get(0).length(), bounds.get(0));
        }


        // Move word(s) onto next line if text is wider than the screen
        int i = 0;
        int padding = getWidth()/10;
        while (bounds.get(i).width() >= getWidth() - padding) {
            bounds.add(new Rect());
            // Move last word to front of line builder
            lineBuilder.insert(0, lines.get(i).substring(lines.get(i).lastIndexOf(" ") + 1) + " ");
            // Cut word off end of current line
            lines.set(i, lines.get(i).substring(0, lines.get(i).lastIndexOf(" ")));
            // Set bounds of current line
            textPaint.getTextBounds(lines.get(i), 0, lines.get(i).length(), bounds.get(i));

            // If current line is not wider than the screen anymore
            if (bounds.get(i).width() < getWidth() - padding) {
                // Assign line builder string to next line and reset
                lines.add(lineBuilder.toString());
                lineBuilder.setLength(0);
                // Increment and measure next line
                i++;
                bounds.add(new Rect());
                textPaint.getTextBounds(lines.get(i), 0, lines.get(i).length(), bounds.get(i));
            }
        }

        Vector<Integer> x = new Vector<>();
        Vector<Integer> y = new Vector<>();
        for (i = 0; i < lines.size(); i++) {
            int verticalOffset = (int)((bounds.get(0).height() * 1.2) * i);
            // Center width of screen
            x.add((getWidth() - bounds.get(i).width()) / 2);
            // Center height adjusted for portrait and other lines
            y.add((portraitHeight
                    + bounds.get(0).height()
                    + (bounds.get(0).height() / 2) // So not directly touching the bottom of the portrait
                    + verticalOffset));
            // Draw line of text to screen
            osCanvas.drawText(lines.get(i), x.get(i), y.get(i), textPaint);
        }
    }

    @Override
    public boolean isInEditMode() {
        return true;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        windowFrame = null; // If the layout changes null the frame so it can be recreated with the new width and height
    }
}
