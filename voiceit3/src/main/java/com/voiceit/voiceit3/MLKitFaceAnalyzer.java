package com.voiceit.voiceit3;

import android.graphics.Rect;
import android.media.Image;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

/**
 * ImageAnalysis analyzer that runs ML Kit's on-device face detector on each
 * preview frame and reports the result on the main thread via {@link FaceCallback}.
 *
 * Replaces the previous Mobile Vision FaceDetector + Tracker + MultiProcessor
 * pipeline. This analyzer is single-shot per frame — when the previous frame's
 * detection has not finished, ImageAnalysis drops the new frame
 * (STRATEGY_KEEP_ONLY_LATEST is the default in CameraX).
 */
class MLKitFaceAnalyzer implements ImageAnalysis.Analyzer {

    /** Result for a single processed frame. */
    static class Result {
        final int faceCount;
        /** Bounding box of the prominent face in image-coordinates, or null. */
        final Rect boundingBox;
        /** Width/height of the analyzed image (post-rotation). */
        final int imageWidth;
        final int imageHeight;

        Result(int faceCount, Rect boundingBox, int imageWidth, int imageHeight) {
            this.faceCount = faceCount;
            this.boundingBox = boundingBox;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
        }
    }

    interface FaceCallback {
        void onFaces(Result result);
    }

    private final FaceDetector detector;
    private final FaceCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    MLKitFaceAnalyzer(FaceCallback callback) {
        this.callback = callback;
        // Match the morning Mobile Vision configuration: prominent face only,
        // minimum face size 15% of frame, no landmarks/classifications since
        // we only use the bounding box.
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.15f)
                .build();
        this.detector = FaceDetection.getClient(options);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    @Override
    public void analyze(@NonNull final ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }
        final int rotation = imageProxy.getImageInfo().getRotationDegrees();
        InputImage input = InputImage.fromMediaImage(mediaImage, rotation);
        final int width = (rotation == 90 || rotation == 270)
                ? mediaImage.getHeight() : mediaImage.getWidth();
        final int height = (rotation == 90 || rotation == 270)
                ? mediaImage.getWidth() : mediaImage.getHeight();

        detector.process(input)
                .addOnSuccessListener(faces -> {
                    Rect bb = null;
                    if (!faces.isEmpty()) {
                        // Pick the largest detected face as the prominent one.
                        Face prominent = faces.get(0);
                        int largestArea = prominent.getBoundingBox().width()
                                * prominent.getBoundingBox().height();
                        for (int i = 1; i < faces.size(); i++) {
                            Face f = faces.get(i);
                            int area = f.getBoundingBox().width() * f.getBoundingBox().height();
                            if (area > largestArea) {
                                prominent = f;
                                largestArea = area;
                            }
                        }
                        bb = prominent.getBoundingBox();
                    }
                    final Result r = new Result(faces.size(), bb, width, height);
                    mainHandler.post(() -> callback.onFaces(r));
                })
                .addOnFailureListener(e -> {
                    // Detection failure — report no faces so the UI doesn't
                    // get stuck in WAIT state.
                    final Result r = new Result(0, null, width, height);
                    mainHandler.post(() -> callback.onFaces(r));
                })
                .addOnCompleteListener(t -> imageProxy.close());
    }

    void close() {
        detector.close();
    }
}
