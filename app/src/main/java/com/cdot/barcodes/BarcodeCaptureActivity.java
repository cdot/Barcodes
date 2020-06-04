/*
 * From samples Copyright (C) The Android Open Source Project and
 * https://medium.com/@mt1729/an-android-journey-barcode-scanning-with-mobile-vision-api-and-camera2-part-1-8a97cc0d6747
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
package com.cdot.barcodes;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;

import android.os.Handler;
import android.util.Log;
import android.util.SparseArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.cdot.barcodes.databinding.BarcodeCaptureActivityBinding;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import com.google.android.material.snackbar.Snackbar;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * This detects barcodes with the rear facing camera.
 */
public final class BarcodeCaptureActivity extends AppCompatActivity {
    private static final String TAG = "BarcodeCapture";

    // intent request code to handle updating play services if needed.
    private static final int RC_HANDLE_GMS = 9001;

    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    // Status codes returned from this activity
    public static final int RESULT_BARCODE_SEEN = RESULT_OK;
    public static final int RESULT_ERROR = RESULT_FIRST_USER;
    // constants used to pass extra data in the intent
    public static final String BARCODE_EXTRA = "Barcode";

    private BarcodeCaptureActivityBinding mBinding;
    private BarcodeDetector mBarcodeDetector;

    private void abort(int code) {
        Intent i = new Intent();
        i.putExtra("message", code);
        setResult(RESULT_ERROR, i);
        finish();
    }

    /**
     * Initializes the UI and creates the detector.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mBinding = BarcodeCaptureActivityBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg = GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
            abort(R.string.barcode_gms_missing);
            return;
        }


        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED)
            createDetector();
        else
            requestCameraPermission();
    }

    private void createDetector() {
        BarcodeDetector.Builder builder = new BarcodeDetector.Builder(BarcodeCaptureActivity.this);
        builder.setBarcodeFormats(Barcode.ALL_FORMATS);
        mBarcodeDetector = builder.build();

        if (!mBarcodeDetector.isOperational()) {
            Log.d(TAG, "Barcode detector not operational");
            abort(R.string.barcode_no_detector);
            return;
        }


        mBinding.cameraPreview.getHolder().addCallback(new SurfaceHolder.Callback() {
            public void surfaceCreated(SurfaceHolder holder) {}
            public void surfaceDestroyed(SurfaceHolder holder) {}

            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                startCameraPreview(width, height);
            }
        });
    }

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            final Activity thisActivity = this;

            View.OnClickListener listener = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // TODO: remove the onClickListener
                    ActivityCompat.requestPermissions(thisActivity, permissions,
                            RC_HANDLE_CAMERA_PERM);
                }
            };

            mBinding.topLayout.setOnClickListener(listener);

            Snackbar.make(mBinding.cameraPreview, R.string.permission_camera_rationale,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.ok, listener)
                    .show();
        } else
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            createDetector();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        abort(R.string.barcode_no_camera_permission);
    }

    /**
     * See https://medium.com/@mt1729/an-android-journey-barcode-scanning-with-mobile-vision-api-and-camera2-part-1-8a97cc0d6747
     */
    private class CameraStateCallback extends CameraDevice.StateCallback {
        Handler mBkgHandler;
        ImageReader mImgReader;

        CameraStateCallback(Handler handler, int w, int h) {
            mBkgHandler = handler;
            mImgReader = ImageReader.newInstance(w, h, ImageFormat.JPEG, 1);
            mImgReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                public void onImageAvailable(ImageReader reader) {
                    Image cameraImage = reader.acquireNextImage();

                    ByteBuffer buffer = cameraImage.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.capacity()];
                    buffer.get(bytes);

                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, buffer.capacity(), null);
                    Frame frameToProcess = (new Frame.Builder()).setBitmap(bitmap).build();
                    SparseArray<Barcode> barcodeResults = mBarcodeDetector.detect(frameToProcess);

                    if (barcodeResults.size() > 0) {
                        Barcode bc = barcodeResults.get(barcodeResults.keyAt(0));
                        Log.d(TAG, "Barcode detected!" + bc.displayValue);
                        Toast.makeText(BarcodeCaptureActivity.this, "Barcode detected! " + bc.displayValue, Toast.LENGTH_SHORT).show();
                        Intent res = new Intent();
                        res.putExtra(BARCODE_EXTRA, bc);
                        setResult(RESULT_BARCODE_SEEN, res);
                        finish();
                    } else {
                        Log.d(TAG, "No barcode found");
                    }

                    cameraImage.close();
                }
            }, mBkgHandler);
        }

        private class SessionStateCallback extends CameraCaptureSession.StateCallback {
            CameraDevice mCamera;

            SessionStateCallback(CameraDevice c) {
                mCamera = c;
            }

            @Override
            public void onConfigured(CameraCaptureSession session) {
                try {
                    CaptureRequest.Builder builder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    builder.addTarget(mBinding.cameraPreview.getHolder().getSurface());
                    builder.addTarget(mImgReader.getSurface());
                    session.setRepeatingRequest(builder.build(), null, null);
                } catch (CameraAccessException e) {
                    Log.d(TAG, "exception" + e.getMessage());
                }
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession session) {
                Log.d(TAG, "onConfigureFailed");
                abort(R.string.barcode_configuration_failed);
            }
        }

        @Override
        public void onOpened(final CameraDevice camera) {
            CameraCaptureSession.StateCallback captureStateCallback = new SessionStateCallback(camera);
            List<Surface> p = new ArrayList<>();
            p.add(mBinding.cameraPreview.getHolder().getSurface());
            p.add(mImgReader.getSurface());
            try {
                camera.createCaptureSession(p, captureStateCallback, mBkgHandler);
            } catch (CameraAccessException e) {
                Log.d(TAG, "exception" + e.getMessage());
                abort(R.string.barcode_configuration_failed);
            }
        }

        @Override
        public void onClosed(CameraDevice camera) {
            // TODO
            Log.d(TAG, "onClosed");
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            // TODO
            Log.d(TAG, "onDisconnected");
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            // TODO
            Log.d(TAG, "onError");
        }
    };

    private void startCameraPreview(int width, int height) {
        final Handler cameraBkgHandler = new Handler();

        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            String[] cids = cameraManager.getCameraIdList();
            for (String cid : cids) {
                CameraCharacteristics cidc = cameraManager.getCameraCharacteristics(cid);
                int cameraDirection = cidc.get(CameraCharacteristics.LENS_FACING);

                if (cameraDirection == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraManager.openCamera(cid, new CameraStateCallback(cameraBkgHandler, width, height), cameraBkgHandler);
                    return;
                }
            }
        } catch (CameraAccessException cae) {
            Log.e(TAG, Objects.requireNonNull(cae.getMessage()));
        } catch (SecurityException se) {
            Log.e(TAG, Objects.requireNonNull(se.getMessage()));
        }
        abort(R.string.barcode_no_camera);
    }

    /**
     * Releases the resources associated with the detector.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBinding.cameraPreview.getHolder().getSurface().release();
    }
}
