package com.buttershystd.yogachallengeapp.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.WindowManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Isaac on 10/06/2015.
 * <p/>
 * Class to control device cameras easier
 */
@SuppressWarnings("deprecation")
public class CameraController {

    //region Fields
    Context context;
    Camera camera;
    MediaRecorder mediaRecorder;
    SurfaceView preview;
    TextureView tPreview;
    WindowManager wm;

    boolean cameraReady;
    boolean timeOut;
    boolean cameraPreparing;
    boolean mediaRecorderReady;
    boolean recording;
    Integer currentCameraId;

    //Listener to get a callback when camera finished loading
    private OnCameraFinishedLoading onCameraFinishedLoading;
    //endregion Fields

    //region Getters/Setters

    public OnCameraFinishedLoading getOnCameraFinishedLoading() {
        return onCameraFinishedLoading;
    }

    public void setOnCameraFinishedLoading(OnCameraFinishedLoading onCameraFinishedLoading) {
        this.onCameraFinishedLoading = onCameraFinishedLoading;
    }

    //endregion Getters/Setters

    //region Constructors
    public CameraController(Context context) {
        this.context = context;
    }
    //endregion Constructors

    //region Methods

    public boolean isCameraReady() {
        return cameraReady;
    }

    //region Prepare Hidden Camera Methods

    /**
     * Prepares a camera not visible by the user
     */
    public void prepareHiddenCamera() {

        cameraPreparing = true;

        //Set a time out for camera preparation
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (cameraPreparing && !cameraReady) {
                    timeOut = true;
                    if (onCameraFinishedLoading != null) {
                        onCameraFinishedLoading.onCameraFail();
                    }
                    //Toast.makeText(context, "Camera not ready when TimeOut, launching onCameraFail()", Toast.LENGTH_SHORT).show();
                } else {
                    //Toast.makeText(context, "Camera ready when TimeOut, no actions performed", Toast.LENGTH_SHORT).show();
                }
            }
        }, 15000);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            prepareHiddenCameraTexture();
        } else {
            prepareHiddenCameraSurface();
        }

        cameraPreparing = false;
    }

    /**
     * Prepares a Surface hidden in window manager to act as camera viewport (required to use the camera)
     */
    private void prepareHiddenCameraSurface() {
        preview = new SurfaceView(context);
        SurfaceHolder holder = preview.getHolder();
        // deprecated setting, but required on Android versions prior to 3.0
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            //The preview must happen at or after this point or takePicture fails
            public void surfaceCreated(SurfaceHolder holder) {
                //Toast.makeText(context, "Surface created", Toast.LENGTH_SHORT).show();

                camera = null;
                currentCameraId = 0;

                try {
                    //Toast.makeText(context, "Opened camera", Toast.LENGTH_SHORT).show();
                    currentCameraId = getFrontCameraId();
                    camera = Camera.open(currentCameraId);

                    try {
                        camera.setPreviewDisplay(holder);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    if (!timeOut) {
                        camera.startPreview();
                        cameraReady = true;
                        //Launch callback for camera ready
                        if (onCameraFinishedLoading != null) {
                            onCameraFinishedLoading.onCameraReady();
                        }
                        //Toast.makeText(context, "Launching onCameraReady()", Toast.LENGTH_SHORT).show();
                    } else {
                        camera.release();
                        camera = null;
                        currentCameraId = 0;
                        cameraReady = false;
                    }

                    //Toast.makeText(context, "Started preview", Toast.LENGTH_SHORT).show();

                } catch (Exception e) {
                    if (camera != null) {
                        releaseCamera();
                        //Launch callback for camera fail
                        if (onCameraFinishedLoading != null) {
                            onCameraFinishedLoading.onCameraFail();
                        }
                        //Toast.makeText(context, "Launching onCameraFail()", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }
        });

        wm = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                8, 16, //Must be at least 1x1
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                0,
                //Don't know if this is a safe default
                PixelFormat.UNKNOWN);
        params.gravity = Gravity.TOP | Gravity.RIGHT;

        //Don't set the preview visibility to GONE or INVISIBLE
        wm.addView(preview, params);


    }

    /**
     * Prepares a TextureView hidden in window manager, to act as camera viewport (required to use the camera)
     */
    @TargetApi(14)
    private void prepareHiddenCameraTexture() {
        tPreview = new TextureView(context);
        tPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                //Toast.makeText(context, "Surface created", Toast.LENGTH_SHORT).show();

                camera = null;
                currentCameraId = 0;

                try {
                    //Toast.makeText(context, "Opened camera", Toast.LENGTH_SHORT).show();
                    currentCameraId = getFrontCameraId();
                    camera = Camera.open(currentCameraId);

                    try {
                        camera.setPreviewTexture(tPreview.getSurfaceTexture());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    //Check for timeout
                    if (!timeOut) {
                        camera.startPreview();
                        cameraReady = true;
                        //Launch callback for camera ready
                        if (onCameraFinishedLoading != null) {
                            onCameraFinishedLoading.onCameraReady();
                        }
                        //Toast.makeText(context, "Launching onCameraReady()", Toast.LENGTH_SHORT).show();
                    } else {
                        camera.release();
                        camera = null;
                        currentCameraId = 0;
                        cameraReady = false;
                    }

                    //Toast.makeText(context, "Started preview", Toast.LENGTH_SHORT).show();

                } catch (Exception e) {
                    if (camera != null) {
                        releaseCamera();
                        //Launch callback for camera ready
                        if (onCameraFinishedLoading != null) {
                            onCameraFinishedLoading.onCameraFail();
                        }
                        //Toast.makeText(context, "Launching onCameraFail()", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });

        wm = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                8, 16, //Must be at least 1x1, 8x16 in some devices
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                0,
                //Don't know if this is a safe default
                PixelFormat.TRANSPARENT);
        params.gravity = Gravity.TOP | Gravity.RIGHT;
        params.flags = WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        params.alpha = 0;

        //Don't set the preview visibility to GONE or INVISIBLE
        wm.addView(tPreview, params);
    }
    //endregion Hidden Camera Methods

    //region Prepare Camera Methods

    /**
     * Prepares a camera from an existing Texture View to act as Camera viewport
     *
     * @param cameraSide Camera to enable (Front or back)
     */
    public TextureView prepareCamera(CameraSide cameraSide) {

        cameraPreparing = true;

        //UPGRADE: Set a time out for camera preparation?
        /*new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!cameraReady) {
                    timeOut = true;
                    if (onCameraFinishedLoading != null) {
                        onCameraFinishedLoading.onCameraFail();
                    }
                    //Toast.makeText(context, "Camera not ready when TimeOut, launching onCameraFail()", Toast.LENGTH_SHORT).show();
                } else {
                    //Toast.makeText(context, "Camera ready when TimeOut, no actions performed", Toast.LENGTH_SHORT).show();
                }
            }
        }, 15000);*/

        //Prepares the specified TextureView to act as camera viewport
        prepareCameraTexture(cameraSide);

        cameraPreparing = false;

        return tPreview;
    }

    /**
     * Prepares a TextureView hidden in window manager, to act as camera viewport (required to use the camera)
     *
     * @param cameraSide Camera to enable (Front or back)
     */
    @TargetApi(14)
    private void prepareCameraTexture(CameraSide cameraSide) {
        tPreview = new TextureView(context);
        tPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                //Toast.makeText(context, "Surface created", Toast.LENGTH_SHORT).show();
                new PrepareCameraAsyncTask().execute();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                //Toast.makeText(context, "onSurfaceTextureSizeChanged", Toast.LENGTH_SHORT).show();
                updateCameraDisplayOrientation();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                //Toast.makeText(context, "onSurfaceTextureDestroyed", Toast.LENGTH_SHORT).show();
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                //Toast.makeText(context, "onSurfaceTextureUpdated", Toast.LENGTH_SHORT).show();
            }
        });
    }
    //endregion Prepare Camera Methods

    //region Camera Actions

    /**
     * Takes a picture from currently prepared camera
     */
    public void takePhoto() {
        if (camera != null && cameraReady) {
            camera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    //Toast.makeText(context, "Took picture, start saving process", Toast.LENGTH_SHORT).show();

                    //Camera preview freezes after taking a picture, resume it
                    camera.startPreview();

                    //Start Save File Async process
                    new SaveFileAsyncTask(data).execute();
                }
            });
        } else {
            //Toast.makeText(context, "Error, camera not ready at the moment of the takePhoto() call", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Record video from the currently prepared camera
     */
    public void startVideoRecording() {
        if (mediaRecorderReady) {
            //Toast.makeText(context, "Starting mediaRecorder", Toast.LENGTH_SHORT).show();
            try {
                mediaRecorder.start();
                recording = true;
            } catch (Exception e) {
                //Toast.makeText(context, "Error on mediaRecorder.start()", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Stops the current video recording if any
     */
    public void stopVideoRecording() {
        if (recording) {
            //Toast.makeText(context, "Stopping mediaRecorder", Toast.LENGTH_SHORT).show();
            mediaRecorder.stop();
            mediaRecorder.reset();
            mediaRecorderReady = false;
            recording = false;
        }
    }

    /**
     * Change camera orientation according to screen on orientation changes
     */
    public void updateCameraDisplayOrientation() {
        if (cameraReady) {
            int rotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            int degrees = 0;
            android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
            android.hardware.Camera.getCameraInfo(getFrontCameraId(), info);

            switch (rotation) {
                case Surface.ROTATION_0:
                    degrees = 0;
                    break;
                case Surface.ROTATION_90:
                    degrees = 90;
                    break;
                case Surface.ROTATION_180:
                    degrees = 180;
                    break;
                case Surface.ROTATION_270:
                    degrees = 270;
                    break;
            }

            int result;
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (info.orientation + degrees) % 360;
                result = (360 - result) % 360;  // compensate the mirror
            } else {  // back-facing
                result = (info.orientation - degrees + 360) % 360;
            }
            camera.setDisplayOrientation(result);
        }
    }

    /**
     * Releases the currently prepared camera
     */
    public void releaseCamera() {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
            currentCameraId = 0;
        }

        if (preview != null) {
            if (wm != null) {
                wm.removeView(preview);
            }
            preview = null;
        } else if (tPreview != null) {
            if (wm != null) {
                wm.removeView(tPreview);
            }
            tPreview = null;
        }
        wm = null;

        cameraReady = false;
    }
    //endregion Camera Actions

    //region Helper methods

    /**
     * Prepares a Media File for the type specified according to file system
     *
     * @param fileType Image or Video
     * @return File prepared
     */
    private File getOutputMediaFile(FileType fileType) {
        File mediaStorageDir;

        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            // Write in external storage, in the specific for pictures
            mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "YogaChallengeApp");
        } else {
            //Write in internal memory if SD is not available
            mediaStorageDir = new File(context.getFilesDir(), "YogaChallengeApp");
            mediaStorageDir.setReadable(true, false);
        }

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

        File mediaFile;
        if (fileType == FileType.IMAGE) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + ".jpg");
        } else {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator + "VID_" + timeStamp + ".mp4");
        }
        mediaFile.setReadable(true, false);

        return mediaFile;
    }

    /**
     * Obtains the front camera ID if any
     *
     * @return int Front camera ID
     */
    private static int getFrontCameraId() {
        int camId = -1;
        int numberOfCameras = Camera.getNumberOfCameras();
        Camera.CameraInfo ci = new Camera.CameraInfo();

        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, ci);
            if (ci.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                camId = i;
            }
        }

        return camId;
    }

    /**
     * Checks if device has fromt camera
     *
     * @param context Context from where the method is being called
     * @return Boolean True if front camera present, false other way
     */
    public static Boolean deviceHasFrontCamera(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
    }
    //endregion Helper methods

    //endregion Methods

    //region Helper Classes
    public enum FileType {
        IMAGE, VIDEO;
    }

    public enum CameraSide {
        FRONT, BACK;
    }

    /**
     * Created by Isaac on 28/07/2015.
     * <p/>
     * Listener to get a callback when the camera is ready to use
     */
    public interface OnCameraFinishedLoading {
        /**
         * Callback that will be invoked when the camera is ready
         */
        void onCameraReady();

        /**
         * Callback that will be invoked when camera fails to load
         */
        void onCameraFail();
    }

    /**
     * AsyncTask to prepare camera
     */
    private class PrepareCameraAsyncTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            camera = null;
            currentCameraId = 0;
            //UPGRADE: Open camera specified by cameraSide parameter
            currentCameraId = getFrontCameraId();

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            try {
                camera = Camera.open(currentCameraId);
                //Toast.makeText(context, "Opened camera", Toast.LENGTH_SHORT).show();
                camera.setPreviewTexture(tPreview.getSurfaceTexture());

                //Check for timeout
                if (!timeOut) {
                    camera.startPreview();
                    cameraReady = true;
                    //Launch callback for camera ready
                    if (onCameraFinishedLoading != null) {
                        onCameraFinishedLoading.onCameraReady();
                    }
                    //Toast.makeText(context, "Launching onCameraReady()", Toast.LENGTH_SHORT).show();
                } else {
                    camera.release();
                    camera = null;
                    currentCameraId = 0;
                    cameraReady = false;
                }
            } catch (Exception e) {
                if (camera != null) {
                    releaseCamera();
                    //Launch callback for camera failed
                    if (onCameraFinishedLoading != null) {
                        onCameraFinishedLoading.onCameraFail();
                    }
                    //Toast.makeText(context, "Launching onCameraFail()", Toast.LENGTH_SHORT).show();
                }
            }

            //Toast.makeText(context, "Started preview", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * AsyncTask to save pictures
     */
    private class SaveFileAsyncTask extends AsyncTask<Void, Void, Void> {
        byte[] data;

        public SaveFileAsyncTask(byte[] data) {
            this.data = data;
        }

        @Override
        protected Void doInBackground(Void... params) {
            File pictureFile = getOutputMediaFile(FileType.IMAGE);

            if (pictureFile == null) {
                //Toast.makeText(context, "Error creating media file, check storage permissions", Toast.LENGTH_SHORT).show();
                return null;
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();

                //Scan image to show it on Android Gallery
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                Uri contentUri = Uri.fromFile(pictureFile);
                mediaScanIntent.setData(contentUri);
                context.sendBroadcast(mediaScanIntent);

                //Toast.makeText(context, "File created", Toast.LENGTH_SHORT).show();
            } catch (FileNotFoundException e) {
                //Toast.makeText(context, "File not found: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                //Toast.makeText(context, "Error accessing file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            //Toast.makeText(context, "Save file process finished", Toast.LENGTH_SHORT).show();
        }
    }
    //endregion Helper Classes
}
