package proto.ttt.cds.green_data.Class;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
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

//    private static final boolean DEBUG = AreaWatcherService.DEBUG;
    private static final boolean DEBUG = true;
    public static final int CAMERA_NUM = Camera.getNumberOfCameras();

    private static final int INVALID_CAM_INDEX = -99;
    private static final File DEFAULT_STORAGE_DIR = Environment
            .getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);

    private static Camera mCam;
    private final H mH = new H();

    private int mOpenCamIndex = -1;
    private String mStoragePath;

    private ArrayList<ICameraCallback> listeners = new ArrayList<>();
    private static CameraNoPreview mCameraNoPreview;
    private String mCaller;
    private String mFilePath;

    private CameraNoPreview() {
        if (CAMERA_NUM < 1) {
            Log.d(TAG, "CameraNoPreview(): No available cameras");
            return;
        }
        setStoragePath(null);
    }

    public static CameraNoPreview getCameraNoPreview() {
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
        setStoragePath(storageDir);
    }

    public boolean openCamera(int index, String caller) {
        try {
            mCaller = caller;
            if (mCam != null) {
                mCam.stopPreview();
                mCam.release();
                mCam = null;
            }
            mCam = Camera.open(index);
            if (mCam != null) {
                updateCameraStatus(index, true);
                Log.d(TAG, "openCamera(): CAMERA# " + index + " opened, Caller = " + caller);
                return true;
            } else {
                Log.d(TAG, "openCamera(): CAMERA# " + index + " is NULL");
                return false;
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "openCamera(): Camera failed to open: " + e.getLocalizedMessage() +
                    ", Caller = " + caller);
            mH.obtainMessage(H.NOTIFY_CAMERA_ALREADY_IN_USE, index).sendToTarget();
            return false;
        }
    }

    public void closeCamera() {
        if (mCam != null) {
            Log.d(TAG, "closeCamera(): releasing camera, caller = " + mCaller);
            mCam.stopPreview();
            mCam.release();
            mCam = null;
            updateCameraStatus(mOpenCamIndex, false);
        } else {
            Log.d(TAG, "closeCamera(): camera already released, caller = " + mCaller);
        }
    }

    private void updateCameraStatus(int camId, boolean isOpened) {
        if (camId < 0 || camId >= CAMERA_NUM) {
            return;
        }

        if (isOpened) {
            mOpenCamIndex = camId;
            mH.obtainMessage(H.NOTIFY_CAMERA_OPENED, camId).sendToTarget();
        } else {
            mOpenCamIndex = INVALID_CAM_INDEX;
            mH.obtainMessage(H.NOTIFY_CAMERA_CLOSED, camId).sendToTarget();
        }
    }

    public void setStoragePath(String storageDir) {
        mStoragePath = storageDir != null ? storageDir : DEFAULT_STORAGE_DIR.getPath();
    }

    public void takePicture(@NonNull String name) {
        if (mStoragePath == null) {
            mStoragePath = DEFAULT_STORAGE_DIR.getAbsolutePath();
        }

        final String filePath = mStoragePath + "/" + name + ".jpeg";
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (mCam != null && filePath != null) {
                    try {
                        mCam.setPreviewTexture(new SurfaceTexture(0));
                        mCam.startPreview();
                        mCam.takePicture(null, null, getJpegCallback(filePath));
                        Log.d(TAG, "takePicture(): PICTURE TAKEN, mCaller = " + mCaller);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.d(TAG, "takePicture(): camera is NULL, mCam = " + mCam + ", filePath = " + filePath);
                }
            }
        }).start();
    }

    private Camera.PictureCallback getJpegCallback(String path) {
        mFilePath = path;
        final int camIndex = mOpenCamIndex;
        Camera.PictureCallback jpeg = new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] bytes, Camera camera) {
                try {
                    FileOutputStream foStream = new FileOutputStream(new File(mFilePath));
                    foStream.write(bytes);
                    foStream.close();
                    if (DEBUG) Log.d(TAG, "onPictureTaken(): files saved, path = " + mFilePath
                            + ", openedCamIndex = " + camIndex + ", caller = " + mCaller);
                    Message msg = mH.obtainMessage(H.NOTIFY_PICTURE_TAKEN);
                    msg.obj = mFilePath;
                    msg.arg1 = camIndex;
                    msg.sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                    if (DEBUG) Log.d(TAG, "onPictureTaken(): IOException, caller = " + mCaller);
                } finally {
                    if (DEBUG) Log.d(TAG, "onPictureTaken(): close camera, caller = " + mCaller);
                    closeCamera();
                }
            }
        };

        return jpeg;
    }

    public void registerCameraListener(ICameraCallback listener) {
        listeners.add(listener);
    }

    public String getStoragePath() {
        return mStoragePath;
    }

    public interface ICameraCallback {
        void onFailedToAccessOpenedCamera(int camIndex);
        void onCameraOpened(int camIndex);
        void onPictureTaken(int camIndex, String picturePath);
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
                    String path = (String) msg.obj;
                    int camIndex = msg.arg1;
                    int size = listeners.size();
                    for (int i = 0; i < size; i++) {
                        listeners.get(i).onPictureTaken(camIndex, path);
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
