package proto.ttt.cds.green_data.Background.Periodic;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Camera;
import android.hardware.camera2.CameraManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import proto.ttt.cds.green_data.Class.CameraNoPreview;

public class PictureTakerService extends Service implements CameraNoPreview.ICameraCallback {
    public static final boolean DEBUG = true;
    public static final String TAG = "PictureTakerService";

    // Intent keys
    public static final String REQUEST_CODE = "requestCode";
    public static final String FILE_NAME  = "fileName";
    public static final String CAM_ID = "camId";
    public static final String STORAGE_PATH = "storagePath";

    public static final int CAMERA_NUM = Camera.getNumberOfCameras();
    private static final long TIMEOUT_MS = 5 * 1000;

    private String mPicName;
    private Context mContext;
    private CameraManager mCameraManager;
    private CameraManager.AvailabilityCallback mCamAvailabilityCallback;
    private CameraNoPreview mCamNoPreview;
    private Queue<CamAction> mCamPendingList = new LinkedList<CamAction>();
    private int mCurrCameraId = -1;
    private String mCurrKey;    // request code will be treated as keys for Map data
    private Handler mH = new Handler();
    private boolean mShouldRetakePicture = false;
    private int mUnavailableCameras = 0x0;  // Flag to check whether there's a opened camera, for convenience sake, cam indexes are stored with +1 value
    private String mCaller;
    private final Runnable mTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (mShouldRetakePicture) {
                Log.d(TAG, "mTimeoutRunnable(): TIMED OUT, retaking picture, CAM_ID = " +
                        mCurrCameraId + ", caller = " + mCaller + ", pending size = " + mCamPendingList.size());
                mCamPendingList.add(mCurrCamAction);
            }
        }
    };
    private IBinder mBinder = new Binder() {
        PictureTakerService getService() {
            return PictureTakerService.this;
        }
    };
    private CamAction mCurrCamAction;
    private String mPicturePath;


    public PictureTakerService() {}

    public void initCallbacks() {
        mCameraManager.registerAvailabilityCallback(getCamAvailabilityCallback(),
                new Handler(mContext.getMainLooper()));

        mCamNoPreview.registerCameraListener(this);
    }

    private CameraManager.AvailabilityCallback getCamAvailabilityCallback() {
        if (mCamAvailabilityCallback == null) {
            mCamAvailabilityCallback = new CameraManager.AvailabilityCallback() {
                @Override
                public void onCameraAvailable(@NonNull String cameraId) {
                    super.onCameraAvailable(cameraId);
                    updateUnavailableCameras(cameraId, false);

                    // Shoot next photo
                    takeNextPictureIfNeeded();
                }

                @Override
                public void onCameraUnavailable(@NonNull String cameraId) {
                    super.onCameraUnavailable(cameraId);
                    if (DEBUG) Log.d(TAG, "onCameraUNAVAILABLE, id = " + cameraId + ", caller = " + mCaller);
                    updateUnavailableCameras(cameraId, true);
                }
            };
        }
        return mCamAvailabilityCallback;
    }

    // This function is meant to be called only in callbacks
    private void updateUnavailableCameras(String camId, boolean isUnavailable) {
        if (isUnavailable) {
            int openedCamHex = Integer.parseInt(camId, 16) + 1;
            mUnavailableCameras |= openedCamHex;
        } else {
            int openedCamHex = Integer.parseInt(camId, 16) + 1;
            mUnavailableCameras = mUnavailableCameras & ~openedCamHex;
        }
    }

    private boolean getNextActionIfExists() {
        mCurrCamAction = mCamPendingList.poll();
        boolean hasNext = mCurrCamAction != null;
        if (hasNext) {
            if (DEBUG) Log.d(TAG, "getNextActionIfExists(): Next Action: mCurrKey = " + mCurrKey +
                    ", mCurrCameraId = " + mCurrCameraId + ", mPicName = " + mPicName +
                    ", pending size = " + mCamPendingList.size());
            mCurrKey = mCurrCamAction.requestCode;
            mCurrCameraId = mCurrCamAction.camId;
            mPicName = mCurrCamAction.picName;
            mCaller = mCurrKey;
        }
        return hasNext;
    }

    boolean mCanTakePicture = true;
    private void takeNextPictureIfNeeded() {
        if (mUnavailableCameras == 0 && mCanTakePicture) {
            if (getNextActionIfExists()) {
                if (DEBUG) Log.d(TAG, "takeNextPictureIfNeeded(): (ALL CAMS READY) TAKING NEXT PICTURE");
                takePicture();
            } else {
                if (DEBUG) Log.d(TAG, "takeNextPictureIfNeeded(): (ALL CAMS READY) NO ACTION TAKEN");
            }
        } else {
            if (DEBUG) Log.d(TAG, "takeNextPictureIfNeeded() CAMS NOT READY! KEEP IN PENDING, camId = "
                    + mCurrCameraId);
        }
    }

    private void takePicture() {
        if (mCamNoPreview != null && mCurrCameraId >= 0 && mCurrCameraId < CAMERA_NUM) {
            mCamNoPreview.openCamera(mCurrCameraId, mCaller);
            mCamNoPreview.takePicture(mPicName + mCurrCameraId);
            mShouldRetakePicture = true;
            mCanTakePicture = false;
            mH.postDelayed(mTimeoutRunnable, TIMEOUT_MS);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = getApplicationContext();
        mCamNoPreview = CameraNoPreview.getCameraNoPreview();
        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);

        initCallbacks();
        registerReceiver();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand()");

        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        return mBinder;
    }

    @Override
    public void onFailedToAccessOpenedCamera(int camIndex) {
        if (DEBUG) Log.d(TAG, "onFailedToAccessOpenedCamera() camIndex = " + camIndex);
    }

    @Override
    public void onCameraOpened(int camIndex) {
    }

    @Override
    public void onPictureTaken(int camIndex, String picturePath) {
        if (camIndex != mCurrCamAction.camId) {
            Log.d(TAG, "onPictureTaken() camIndex NOT MATCHING: camIndex = " + camIndex
                    + ",pendingList cam = " + mCurrCamAction.camId);
            return;
        }

        mPicturePath = picturePath;
        mShouldRetakePicture = false;
        mCanTakePicture = true;

        sendBroadcast();
    }

    @Override
    public void onCameraClosed(int camIndex) {
    }

    private void sendBroadcast() {
        Intent intent = new Intent(mCurrKey);
        intent.putExtra(CAM_ID, mCurrCamAction.camId);
        intent.putExtra(STORAGE_PATH, mPicturePath);
        sendBroadcast(intent);
        if (DEBUG) Log.d(TAG, "sendBroadcast() camId = " + mCurrCamAction.camId + ", path = " +
                mPicturePath);
    }

    class CamAction {
        public String requestCode;
        public String picName;
        public int camId;
        public CamAction(String request, String name, int id)  {
            requestCode = request;
            picName = name;
            camId = id;
        }
    }

    InfoReceiver mInfoReceiver;
    private void registerReceiver() {
        mInfoReceiver = new InfoReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(InfoReceiver.REQUEST_INFO);
        registerReceiver(mInfoReceiver, intentFilter);
    }

    public class InfoReceiver extends BroadcastReceiver {
        static final String REQUEST_INFO = "requestInfo";
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                String requestCode = bundle.getString(REQUEST_CODE);
                String fileName = bundle.getString(FILE_NAME);
                ArrayList<Integer> camList = bundle.getIntegerArrayList(CAM_ID);
                for (int i=0; i<camList.size(); i++) {
                    mCamPendingList.add(new CamAction(requestCode, fileName, camList.get(i)));
                }

                Log.d(TAG, "onReceive() requestCode = " + requestCode + ", fileName = " + fileName
                        + ", camList = " + camList + ", pending size = " + mCamPendingList.size());
                takeNextPictureIfNeeded();
            }

        }
    }
}
