package proto.ttt.cds.green_data.Class;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by changdo on 17. 9. 5.
 */

public abstract class SequencePictureTaker implements CameraNoPreview.ICameraCallback {
    public static final boolean DEBUG = true;
    public static final String TAG = "SequencePictureTaker";

    public static final int CAMERA_NUM = Camera.getNumberOfCameras();
    private static final long TIMEOUT_MS = 5 * 1000;

    private String mCaller;
    private Context mContext;
    private CameraManager mCameraManager;
    private CameraManager.AvailabilityCallback mCamAvailabilityCallback;
    private Queue<String> mCamPendingList = new LinkedList<String>();
    private int mUnavailableCameras = 0x0;  // Flag to check whether there's a opened camera, for convenience sake, cam indexes are stored as +1 value
    private CameraNoPreview mCamNoPreview;

    private int mCurrCameraId = -1;
    private boolean mShouldRetakePicture = false;
    private String mName;
    private final Runnable mTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (mShouldRetakePicture && !mCamPendingList.contains("" + mCurrCameraId)) {
                Log.d(TAG, "mTimeoutRunnable(): TIMED OUT, retaking picture, CAM_ID = " +
                        mCurrCameraId);
                takePicture();
            }
        }
    };


    public SequencePictureTaker(Context context, String picName, String caller) {
        mCaller = caller;
        mContext = context;
        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        mCamNoPreview = new CameraNoPreview(null);
        mName = picName;

        for(int i=0; i<CAMERA_NUM; i++) {
            mUnavailableCameras |= (i + 1);
        }
    }

    public void initCallbacks() {
        mCameraManager.registerAvailabilityCallback(getCamAvailabilityCallback(),
                new Handler(mContext.getMainLooper()));

        mCamNoPreview.registerCameraListener(this);
    }

    public void stop() {
        if (mCamNoPreview != null) {
            mCamNoPreview.closeCamera();
        }
        if (mCamAvailabilityCallback != null) {
            mCameraManager.unregisterAvailabilityCallback(mCamAvailabilityCallback);
        }
        mShouldRetakePicture = false;
    }

    private CameraManager.AvailabilityCallback getCamAvailabilityCallback() {
        if (mCamAvailabilityCallback == null) {
            mCamAvailabilityCallback = new CameraManager.AvailabilityCallback() {
                @Override
                public void onCameraAvailable(@NonNull String cameraId) {
                    super.onCameraAvailable(cameraId);
//                    if (DEBUG) Log.d(TAG, "onCameraAvailable, id = " + cameraId);

                    updateUnavailableCameras("" + cameraId, false);
                    if (!mCamPendingList.isEmpty()) {
                        String nextCamId = mCamPendingList.poll();
                        mCurrCameraId = Integer.parseInt(nextCamId);
                        takePicture();
                    }
                }

                @Override
                public void onCameraUnavailable(@NonNull String cameraId) {
                    super.onCameraUnavailable(cameraId);
//                    if (DEBUG) Log.d(TAG, "onCameraUnavailable, id = " + cameraId);
                    updateUnavailableCameras("" + cameraId, true);
                }
            };
        }
        return mCamAvailabilityCallback;
    }


    private void updateUnavailableCameras(String camId, boolean isUnavailable) {
        if (isUnavailable) {
            int openedCamHex = Integer.parseInt(camId, 16) + 1;
            mUnavailableCameras |= openedCamHex;
        } else {
            int openedCamHex = Integer.parseInt(camId, 16) + 1;
            mUnavailableCameras = mUnavailableCameras & ~openedCamHex;
        }
    }

    private boolean isAllCameraReady() {
        if (mUnavailableCameras == 0) {
            if (DEBUG) Log.d(TAG, "isAllCameraReady(): ALL CAMS READY");
            return true;
        } else {
            if (DEBUG) Log.d(TAG, "isAllCameraReady(): ALL CAMS NOT READY");
            return false;
        }
    }

    private void takePicture() {
        if (mCamNoPreview != null && mCurrCameraId >= 0 && mCurrCameraId < CAMERA_NUM) {
            if (isAllCameraReady() && mCamNoPreview.openCamera(mCurrCameraId, mCaller)) {
                mCamNoPreview.takePicture(mName + mCurrCameraId);

                mShouldRetakePicture = true;
                new Handler().postDelayed(mTimeoutRunnable, TIMEOUT_MS);
            } else {
                if (DEBUG) Log.d(TAG, "takePicture() A CAMERA IN USE, ADD TO PENDING, camId = "
                        + mCurrCameraId);
                if (!mCamPendingList.contains("" + mCurrCameraId)) {
                    mCamPendingList.add("" + mCurrCameraId);
                }
            }
        }
    }

    public void takePictureStart() {
        mCurrCameraId = 0;
        takePicture();
    }

    public String getPicturePath(int camId) {
        return mCamNoPreview.getStoragePath() + "/" + (mName + camId);
    }

    @Override
    public void onFailedToAccessOpenedCamera(int camId) {
        if (DEBUG) Log.d(TAG, "onFailedToAccessOpenedCamera() camId = " + camId);
        if (!mCamPendingList.contains("" + mCurrCameraId)) {
            mCamPendingList.add("" + camId);
        }
        onFailedToAccessOpenedCameraCB(camId);
    }

    @Override
    public void onCameraOpened(int camId) {
        if (DEBUG) Log.d(TAG, "onCameraOpened() camId = " + camId);
        onCameraOpenedCB(camId);
    }

    @Override
    public void onPictureTaken(int camId) {
        mShouldRetakePicture = false;
        onPictureTakenCB(camId);

    }

    @Override
    public void onCameraClosed(int camId) {
        boolean isLastCam = camId == CAMERA_NUM - 1;
        if (isLastCam) {
            if (DEBUG) Log.d(TAG, "onCameraClosed(): LAST CAM CLOSED, camId = " + camId);
            mCameraManager.unregisterAvailabilityCallback(mCamAvailabilityCallback);
        } else {
            if (DEBUG) Log.d(TAG, "onCameraClosed(): taking another picture, camId = " + camId);
            mCurrCameraId = camId + 1;
            takePicture();
        }
        onCameraClosedCB(camId);
    }

    public abstract void onFailedToAccessOpenedCameraCB(int camId);
    public abstract void onCameraOpenedCB(int camId);
    public abstract void onPictureTakenCB(int camId);
    public abstract void onCameraClosedCB(int camId);

}
