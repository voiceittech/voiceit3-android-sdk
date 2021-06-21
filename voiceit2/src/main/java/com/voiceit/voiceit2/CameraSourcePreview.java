/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.voiceit.voiceit2;

import android.content.Context;
import android.content.res.Configuration;
import android.hardware.Camera;
import androidx.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import com.google.android.gms.common.images.Size;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

class CameraSourcePreview extends ViewGroup {
    private static final String mTAG = "CameraSourcePreview";

    private final Context mContext;
    private final SurfaceView mSurfaceView;
    private boolean mStartRequested;
    private boolean mSurfaceAvailable;
    private CameraSource mCameraSource;

    public static int previewWidth;
    public static int previewHeight;
    public static int childWidth;
    public static int childHeight;

    public CameraSourcePreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mStartRequested = false;
        mSurfaceAvailable = false;

        mSurfaceView = new SurfaceView(context);
        mSurfaceView.getHolder().addCallback(new SurfaceCallback());
        addView(mSurfaceView);
    }

    public void start(CameraSource cameraSource) throws IOException {
        if (cameraSource == null) {
            stop();
        }

        mCameraSource = cameraSource;

        if (mCameraSource != null) {
            mStartRequested = true;
            startIfReady();
        }
    }

    public SurfaceHolder getSurfaceHolder(){
        return mSurfaceView.getHolder();
    }

    public void stop() {
        if (mCameraSource != null) {
            mCameraSource.stop();
        }
    }

    public void release() {
        if (mCameraSource != null) {
            mCameraSource.release();
            mCameraSource = null;
        }
    }

    private void startIfReady() throws IOException {
        if (mStartRequested && mSurfaceAvailable) {
            mCameraSource.start(mSurfaceView.getHolder());
            mStartRequested = false;

            Camera mCamera = getCamera(mCameraSource);
            if(mCamera != null) {
                mCamera.enableShutterSound(false);
                Camera.Parameters params = mCamera.getParameters();
                // No need to send high resolution pictures
                int resolutionSizeCap = 1228800;
                List<Camera.Size> sizes = params.getSupportedPictureSizes();
                int max = 0, index = 0;
                for (int i = 0; i < sizes.size(); i++) {
                    Camera.Size s = sizes.get(i);
                    int size = s.height * s.width;
                    if (size > max && size < resolutionSizeCap) {
                        index = i;
                        max = size;
                    }
                }
                params.setPictureSize(sizes.get(index).width, sizes.get(index).height);
                mCamera.setParameters(params);

                // On some devices the camera preview sizes
                // were unavailable before in onLayout so retry now
                if (previewWidth == -1 || previewHeight == -1) {
                    requestLayout();
                }
            }

        }
    }

    private class SurfaceCallback implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder surface) {
            mSurfaceAvailable = true;
            try {
                startIfReady();
            } catch (IOException e) {
                Log.e(mTAG, "Could not start camera source: ", e);
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surface) {
            mSurfaceAvailable = false;
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        }
    }

    private static Camera getCamera(@NonNull CameraSource cameraSource)  {
        Field[] declaredFields = CameraSource.class.getDeclaredFields();
        for (Field field : declaredFields) {
            if (field.getType() == Camera.class) {
                field.setAccessible(true);
                try {
                    Camera camera = (Camera) field.get(cameraSource);
                    if (camera != null) {
                        return camera;
                    }
                    return null;
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                break;
            }
        }
        return null;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        previewWidth = -1;
        previewHeight = -1;
        if (mCameraSource != null) {
            Size size = mCameraSource.getPreviewSize();
            if (size != null) {
                previewWidth = size.getWidth();
                previewHeight = size.getHeight();
            }
        }

        // Swap width and height sizes when in portrait, since it will be rotated 90 degrees
        if (isPortraitMode(mContext)) {
            int tmp = previewWidth;
            previewWidth = previewHeight;
            previewHeight = tmp;
        }

        final int layoutWidth = right - left-1;
        final int layoutHeight = bottom - top-1;

        // Computes height and width for potentially doing fit width.
        childWidth = layoutWidth;
        childHeight = (int)(((float) layoutWidth / (float) previewWidth) * previewHeight);

        // If height is too tall using fit width, does fit height instead.
        if (childHeight < layoutHeight) {
            childHeight = layoutHeight;
            childWidth = (int)(((float) layoutHeight / (float) previewHeight) * previewWidth);
        }

        for (int i = 0; i < getChildCount(); ++i) {
            getChildAt(i).layout(0, 0, childWidth, childHeight);
        }


    }

    public static boolean isPortraitMode(Context context) {
        int orientation = context.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return false;
        }
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            return true;
        }

        Log.d(mTAG, "isPortraitMode returning false by default");
        return false;
    }
}
