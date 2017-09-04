package proto.ttt.cds.green_data.Class;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
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

//    private static final boolean DEBUG_CAMERA = PlantWatcherService.DEBUG;
    private static final boolean DEBUG_CAMERA = true;
    public static final int CAMERA_NUM = Camera.getNumberOfCameras();

    public static final int INVALID_CAM_INDEX = -99;
    public static final int DEFAULT_CAM_INDEX = 0;
    public static final File DEFAULT_STORAGE_DIR = Environment
            .getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);

    private static Camera mCam;
    private final H mH = new H();

    private final int[] openedCams = new int[CAMERA_NUM];
    private int mOpenCamIndex = -1;
    private String mStoragePath, mFilePath;

    private ArrayList<ICameraCallback> listeners = new ArrayList<>();
    private CameraNoPreview mCameraNoPreview;

    private CameraNoPreview() {
        if (CAMERA_NUM < 1) {
            Log.d(TAG, "CameraNoPreview(): No available cameras");
            return;
        }
        setStorageDir(null);
    }

    public CameraNoPreview getCameraNoPreview() {
        if (mCameraNoPreview == null) {
            mCameraNoPreview = new CameraNoPreview();
        }
        return mCameraNoPreview;
    }

    public CameraNoPreview(String storageDir) {
        if (CAMERA_NUM < 1) {
            Log.d(TAG, "CameraNoPreview(): No available cameras");
            return;
        }
        setStorageDir(storageDir);
    }

    public void openCamera(int index, String caller) {
        try {
            mCam = Camera.open(index);
            updateCameraStatus(index, true);
            Log.d(TAG, "openCamera(): CAMERA# " + index + " opened, Caller = " + caller);
        } catch (RuntimeException e) {
            Log.e(TAG, "openCamera(): Camera failed to open: " + e.getLocalizedMessage() +
                    ", Caller = " + caller);
            mH.obtainMessage(H.NOTIFY_CAMERA_ALREADY_IN_USE, index).sendToTarget();
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

    private void updateCameraStatus(int camId, boolean isOpened) {
        if (camId < 0 || camId >= CAMERA_NUM) {
            return;
        }

        if (isOpened) {
            mOpenCamIndex = camId;
            openedCams[camId] = 1;
            mH.obtainMessage(H.NOTIFY_CAMERA_OPENED, camId).sendToTarget();
        } else {
            mOpenCamIndex = INVALID_CAM_INDEX;
            openedCams[camId] = 0;
            mH.obtainMessage(H.NOTIFY_CAMERA_CLOSED, camId).sendToTarget();
        }
    }

    public void setStorageDir(String storageDir) {
        mStoragePath = storageDir != null ? storageDir : DEFAULT_STORAGE_DIR.getPath();
    }

    public void takePictureWithoutPreview(@NonNull String name) {
        if (mStoragePath == null) {
            mStoragePath = DEFAULT_STORAGE_DIR.getAbsolutePath();
        }
        mFilePath = mStoragePath + "/" + name;
        takePictureWithoutPreview();
    }

    private void takePictureWithoutPreview() {
        if (mCam != null && mFilePath != null) {
            try {
                mCam.setPreviewTexture(new SurfaceTexture(0));
                mCam.startPreview();
                mCam.takePicture(null, null, getJpegCallback(mFilePath));
                Log.d(TAG, "takePictureWithoutPreview(): PICTURE TAKEN");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Log.d(TAG, "takePictureWithoutPreview(): camera is NULL, mCam = " + mCam + ", mFilePath = " + mFilePath);
        }
    }

    private Camera.PictureCallback getJpegCallback(String path) {
        final String filePath = path;
        Camera.PictureCallback jpeg = new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] bytes, Camera camera) {
                try {
                    FileOutputStream foStream = new FileOutputStream(new File(filePath));
                    foStream.write(bytes);
                    foStream.close();
                    if (DEBUG_CAMERA) Log.d(TAG, "onPictureTaken(): files saved, path = " + filePath
                            + ", openedCamIndex = " + mOpenCamIndex);
                    mH.obtainMessage(H.NOTIFY_PICTURE_TAKEN, mOpenCamIndex).sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                    if (DEBUG_CAMERA) Log.d(TAG, "onPictureTaken(): IOException");
                } finally {
                    if (DEBUG_CAMERA) Log.d(TAG, "onPictureTaken(): close camera");
                    closeCamera();
                }
            }
        };

        return jpeg;
    }

    public void registerCameraListener(ICameraCallback listener) {
        listeners.add(listener);
    }

    public String getStorageDirectory() {
        return mStoragePath;
    }

    public interface ICameraCallback {
        void onFailedToAccessOpenedCamera(int camIndex);
        void onCameraOpened(int camIndex);
        void onPictureTaken(int camIndex);
        void onCameraClosed(int camIndex);
    }

    final class H extends Handler {
        public static final int NOTIFY_CAMERA_ALREADY_IN_USE = 0;
        public static final int NOTIFY_CAMERA_OPENED = 1;
        public static final int NOTIFY_PICTURE_TAKEN = 2;
        public static final int NOTIFY_CAMERA_CLOSED = 3;

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case NOTIFY_CAMERA_ALREADY_IN_USE: {
                    int camIndex = (int) msg.obj;
                    int size = listeners.size();
                    for (int i = 0; i < size; i++) {
                        listeners.get(i).onFailedToAccessOpenedCamera(camIndex);
                    }
                    break;
                }
                case NOTIFY_CAMERA_OPENED: {
                    int camIndex = (int) msg.obj;
                    int size = listeners.size();
                    for (int i = 0; i < size; i++) {
                        listeners.get(i).onCameraOpened(camIndex);
                    }
                    break;
                }
                case NOTIFY_PICTURE_TAKEN: {
                    int camIndex = (int) msg.obj;
                    int size = listeners.size();
                    for (int i = 0; i < size; i++) {
                        listeners.get(i).onPictureTaken(camIndex);
                    }
                    break;
                }
                case NOTIFY_CAMERA_CLOSED: {
                    int camIndex = (int) msg.obj;
                    int size = listeners.size();
                    for (int i = 0; i < size; i++) {
                        listeners.get(i).onCameraClosed(camIndex);
                    }
                    break;
                }
            }
        }
    }
}
