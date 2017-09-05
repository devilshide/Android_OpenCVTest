package proto.ttt.cds.green_data.Class;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by changdo on 17. 9. 5.
 */

public class SequencePictureTaker implements CameraNoPreview.ICameraCallback {
    public static final String TAG = "SequencePictureTaker";
    public static final int CAMERA_NUM = Camera.getNumberOfCameras();

    private static final long TIMEOUT_MS = 5 * 1000;


    private Context mContext;
    private CameraManager mCameraManager;
    private CameraManager.AvailabilityCallback mCamAvailabilityCallback;
    private Queue<String> mCamPendingList = new LinkedList<String>();
    private int mUnavailableCameras = 0x0;  // Flag to check whether there's a opened camera, for convenience sake, cam indexes are stored as +1 value
    private CameraNoPreview mCam;

    private int mCurrCameraId = -1;
    private boolean mShouldRetakePicture = false;
    private String mName;
    private final Runnable mTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (mShouldRetakePicture && !mCamPendingList.contains("" + mCurrCameraId)) {
//                Log.d(TAG, "mTimeoutRunnable(): TIMED OUT, retaking picture, CAM_ID = " +
//                        mCurrCameraId);
                takePicture();
            }
        }
    };


    public SequencePictureTaker(Context context, String picName) {
        mContext = context;
        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        mCam = new CameraNoPreview(null);
        mName = picName;

        for(int i=0; i<CAMERA_NUM; i++) {
            mUnavailableCameras |= (i + 1);
        }
    }

    public void initCamera() {
        mCameraManager.registerAvailabilityCallback(getCamAvailabilityCallback(),
                new Handler(mContext.getMainLooper()));

        if (mCam != null) {
            mCam.registerCameraListener(this);
        }
    }

    private CameraManager.AvailabilityCallback getCamAvailabilityCallback() {
        if (mCamAvailabilityCallback == null) {
            mCamAvailabilityCallback = new CameraManager.AvailabilityCallback() {
                @Override
                public void onCameraAvailable(@NonNull String cameraId) {
                    super.onCameraAvailable(cameraId);
//                    Log.d(TAG, "onCameraAvailable, id = " + cameraId);

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
//                    Log.d(TAG, "onCameraUnavailable, id = " + cameraId);
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
//            if (DEBUG) Log.d(TAG, "isAllCameraReady(): ALL CAMERA READY!");
            return true;
        } else {
//            if (DEBUG) Log.d(TAG, "isAllCameraReady(): NOT READY! -> " + mUnavailableCameras);
            return false;
        }
    }

    private void takePicture() {
        if (mCurrCameraId >= 0 && mCurrCameraId < CAMERA_NUM) {
            if (isAllCameraReady()) {
                boolean isOpen = mCam.openCamera(mCurrCameraId, TAG);
                if (isOpen) {
                    mCam.takePictureWithoutPreview(mName + mCurrCameraId);

                    mShouldRetakePicture = true;
                    new Handler().postDelayed(mTimeoutRunnable, TIMEOUT_MS);
                } else {
//                    Log.d(TAG, "takePicture() A CAMERA IS NULL, ADD TO PENDING, camId = " + mCurrCameraId);
                    mCamPendingList.add("" + mCurrCameraId);
                }
            } else {
//                Log.d(TAG, "takePicture() A CAMERA IN USE, ADD TO PENDING, camId = " + mCurrCameraId);
                mCamPendingList.add("" + mCurrCameraId);
            }
        }
    }

    @CallSuper
    public void onFailedToAccessOpenedCamera(int camId) {
//                    Log.d(TAG, "onFailedToAccessOpenedCamera() camId = " + camId);
        mCamPendingList.add("" + camId);
    }

    @Override
    public void onCameraOpened(int camId) {
//                    Log.d(TAG, "onCameraOpened() camId = " + camId);
    }

    @CallSuper
    public void onPictureTaken(int camId) {
        mShouldRetakePicture = false;

    }

    @CallSuper
    public void onCameraClosed(int camId) {
//        Log.d(TAG, "onCameraClosed() camId = " + camId);
        mCurrCameraId = camId + 1;
        takePicture();
    }

}
