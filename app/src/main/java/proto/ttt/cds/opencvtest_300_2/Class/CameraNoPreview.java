package proto.ttt.cds.opencvtest_300_2.Class;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;


/**
 * Created by changdo on 17. 8. 2.
 */

public class CameraNoPreview {
    public static final String TAG = "CameraNoPreview";

//    private static final boolean DEBUG_CAMERA_ACTION = PlantWatcherService.DEBUG;
    private static final boolean DEBUG_CAMERA_ACTION = false;

    public static final int INVALID_CAM_INDEX = -99;
    public static final int DEFAULT_CAM_INDEX = 0;
    public static final File STORAGE_DIR_FILE = Environment
            .getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);

    private static Camera mCam;
    private final H mH = new H();

    private static final int[] openedCamIndex = new int[Camera.getNumberOfCameras()];
    private int mOpenCamIndex = -1;

    private ArrayList<ICameraCallback> listeners = new ArrayList<>();


    public CameraNoPreview() {
        if (openedCamIndex.length == 0) {
            Log.d(TAG, "CameraNoPreview(): No available cameras");
            return;
        }
    }

    public CameraNoPreview(int index) {
        if (openedCamIndex.length == 0) {
            Log.d(TAG, "CameraNoPreview(): No available cameras");
            return;
        }
    }

    public void openCamera() {
        if (mOpenCamIndex == DEFAULT_CAM_INDEX ) {
            Log.d(TAG, "openCamera(): DEFAULT CAM already opened");
            return;
        }

        try {
            mCam = Camera.open();
            updateCameraStatus(DEFAULT_CAM_INDEX, true);
            Log.d(TAG, "openCamera(): DEFAULT CAMERA opened");
        } catch (RuntimeException e) {
            Log.e(TAG, "openCamera(): Camera failed to open: " + e.getLocalizedMessage());
        }
    }

    public void openCamera(int index) {
        if (mOpenCamIndex == index ) {
            Log.d(TAG, "openCamera(): CAMERA# " + index + " already opened");
            return;
        }

        try {
            mCam = Camera.open(index);
            updateCameraStatus(index, true);
            Log.d(TAG, "openCamera(): CAMERA# " + index + " opened");
        } catch (RuntimeException e) {
            Log.e(TAG, "openCamera(): Camera failed to open: " + e.getLocalizedMessage());
        }
    }

    public void closeCamera() {
        if (mCam != null) {
            Log.d(TAG, "closeCamera(): releasing camera");
            mCam.stopPreview();
            mCam.release();
            mCam = null;
            updateCameraStatus(mOpenCamIndex, false);
        } else {
            Log.d(TAG, "closeCamera(): camera already released");
        }
    }

    private void updateCameraStatus(int openCamIndex, boolean isOpened) {
        if (openCamIndex < 0 || openCamIndex >= Camera.getNumberOfCameras()) {
            return;
        }

        if (isOpened) {
            mOpenCamIndex = openCamIndex;
            openedCamIndex[openCamIndex] = 1;
            mH.obtainMessage(H.NOTIFY_CAMERA_OPENED).sendToTarget();
        } else {
            mOpenCamIndex = INVALID_CAM_INDEX;
            openedCamIndex[openCamIndex] = 0;
            mH.obtainMessage(H.NOTIFY_CAMERA_CLOSED).sendToTarget();
        }
    }

    public void takePictureWithoutPrev(String name) {
        if (mCam != null) {
            try {
                mCam.setPreviewTexture(new SurfaceTexture(0));
                mCam.startPreview();
                mCam.takePicture(null, null, getJpegCallback(name));
                Log.d(TAG, "takePictureWithoutPrev(): picture taken");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Camera.PictureCallback getJpegCallback(String name) {
        final String fileName = name;
        Camera.PictureCallback jpeg = new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] bytes, Camera camera) {
                try {
                    FileOutputStream foStream = new FileOutputStream(new File(STORAGE_DIR_FILE, fileName));
                    foStream.write(bytes);
                    foStream.close();
                    if (DEBUG_CAMERA_ACTION) Log.d(TAG, "getJpegCallback(): onPictureTaken");
                    mH.obtainMessage(H.NOTIFY_PICTURE_TAKEN).sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    closeCamera();
                }
            }
        };

        return jpeg;
    }

    public void registerPictureTakenListeners(ICameraCallback listener) {
        listeners.add(listener);
    }

    public interface ICameraCallback {
        void onCameraOpened();
        void onPictureTaken();
        void onCameraClosed();
    }

    final class H extends Handler {
        public static final int NOTIFY_CAMERA_OPENED = 0;
        public static final int NOTIFY_PICTURE_TAKEN = 1;
        public static final int NOTIFY_CAMERA_CLOSED = 2;

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case NOTIFY_CAMERA_OPENED: {
                    if (DEBUG_CAMERA_ACTION) Log.d(TAG, "handleMessage(): NOTIFY_CAMERA_OPENED");
                    int size = listeners.size();
                    for (int i = 0; i < size; i++) {
                        listeners.get(i).onCameraOpened();
                    }
                    break;
                }
                case NOTIFY_PICTURE_TAKEN: {
                    if (DEBUG_CAMERA_ACTION) Log.d(TAG, "handleMessage(): NOTIFY_PICTURE_TAKEN");
                    int size = listeners.size();
                    for (int i = 0; i < size; i++) {
                        listeners.get(i).onPictureTaken();
                    }
                    break;
                }
                case NOTIFY_CAMERA_CLOSED: {
                    if (DEBUG_CAMERA_ACTION) Log.d(TAG, "handleMessage(): NOTIFY_CAMERA_CLOSED");
                    int size = listeners.size();
                    for (int i = 0; i < size; i++) {
                        listeners.get(i).onCameraClosed();
                    }
                    break;
                }
            }
        }
    }
}
