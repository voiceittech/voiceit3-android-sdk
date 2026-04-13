package com.voiceit.voiceit3;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCase;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin wrapper around CameraX. Handles preview, image analysis (face
 * detection), photo capture, and audio+video recording. Replaces the old
 * Camera1-based CameraSource/CameraSourcePreview pair.
 *
 * Lifecycle:
 *   1. construct with the activity (LifecycleOwner) and the PreviewView
 *      from the layout
 *   2. call bindForFace(analyzer) or bindForVideo() to wire up the use cases
 *   3. takePicture(File, callback) once a face is detected
 *   4. startRecording(File) / stopRecording() for video flows
 *   5. shutdown() in onDestroy
 */
class CameraXBinder {

    private static final String TAG = "CameraXBinder";

    private final Activity activity;
    private final LifecycleOwner lifecycleOwner;
    private final PreviewView previewView;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();

    @Nullable private ProcessCameraProvider cameraProvider;
    @Nullable private ImageCapture imageCapture;
    @Nullable private VideoCapture<Recorder> videoCapture;
    @Nullable private Recording activeRecording;

    interface PictureCallback {
        void onPictureTaken(File file);
        void onError(Throwable error);
    }

    interface VideoCallback {
        void onVideoFinalized(File file);
        void onError(Throwable error);
    }

    CameraXBinder(Activity activity, PreviewView previewView) {
        this.activity = activity;
        this.lifecycleOwner = (LifecycleOwner) activity;
        this.previewView = previewView;
    }

    /**
     * Bind preview + image analysis + image capture for the face flows
     * (face enrollment, face verification).
     */
    void bindForFace(MLKitFaceAnalyzer analyzer, Runnable onReady) {
        bind(analyzer, /*forVideo=*/ false, onReady);
    }

    /**
     * Bind preview + video capture for video enrollment / video verification.
     * Image analysis is disabled — these flows do not run face detection
     * during recording.
     */
    void bindForVideo(Runnable onReady) {
        bind(/*analyzer=*/ null, /*forVideo=*/ true, onReady);
    }

    /**
     * Bind preview only (used for voice flows that need the camera off but
     * keep the activity layout consistent — most voice flows actually skip
     * CameraXBinder entirely; provided for completeness).
     */
    void bindPreviewOnly(Runnable onReady) {
        bind(/*analyzer=*/ null, /*forVideo=*/ false, onReady);
    }

    private void bind(@Nullable MLKitFaceAnalyzer analyzer, boolean forVideo,
                      @Nullable Runnable onReady) {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(activity);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                cameraProvider.unbindAll();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                CameraSelector selector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();

                if (forVideo) {
                    Recorder recorder = new Recorder.Builder()
                            .setQualitySelector(QualitySelector.from(Quality.HD))
                            .build();
                    videoCapture = VideoCapture.withOutput(recorder);
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture);
                } else {
                    imageCapture = new ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build();
                    UseCase[] useCases;
                    if (analyzer != null) {
                        ImageAnalysis analysis = new ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build();
                        analysis.setAnalyzer(analysisExecutor, analyzer);
                        useCases = new UseCase[]{ preview, imageCapture, analysis };
                    } else {
                        useCases = new UseCase[]{ preview, imageCapture };
                    }
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, useCases);
                }

                if (onReady != null) onReady.run();
            } catch (Throwable t) {
                Log.e(TAG, "Camera bind failed", t);
            }
        }, ContextCompat.getMainExecutor(activity));
    }

    void takePicture(final File outputFile, final PictureCallback cb) {
        if (imageCapture == null) {
            cb.onError(new IllegalStateException("ImageCapture not bound"));
            return;
        }
        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(outputFile).build();
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(activity),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        cb.onPictureTaken(outputFile);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exc) {
                        cb.onError(exc);
                    }
                });
    }

    @SuppressLint("MissingPermission")
    void startRecording(File outputFile, final VideoCallback cb) {
        if (videoCapture == null) {
            cb.onError(new IllegalStateException("VideoCapture not bound"));
            return;
        }
        FileOutputOptions options = new FileOutputOptions.Builder(outputFile).build();
        activeRecording = videoCapture.getOutput()
                .prepareRecording(activity, options)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(activity), event -> {
                    if (event instanceof VideoRecordEvent.Finalize) {
                        VideoRecordEvent.Finalize fin = (VideoRecordEvent.Finalize) event;
                        if (fin.hasError()) {
                            cb.onError(fin.getCause() != null
                                    ? fin.getCause()
                                    : new RuntimeException("Recording finalize error code "
                                            + fin.getError()));
                        } else {
                            cb.onVideoFinalized(outputFile);
                        }
                    }
                });
    }

    void stopRecording() {
        if (activeRecording != null) {
            activeRecording.stop();
            activeRecording = null;
        }
    }

    void shutdown() {
        stopRecording();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
        analysisExecutor.shutdown();
    }
}
