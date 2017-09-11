package proto.ttt.cds.green_data.Background.Periodic;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import proto.ttt.cds.green_data.Class.CameraNoPreview;

public class PictureTakerService extends Service implements CameraNoPreview.ICameraCallback {

    public static final boolean DEBUG = true;
    public static final String TAG = "PictureTakerService";

    private CameraNoPreview mCamNoPreview;
//    private Queue<String> mCamPendingList = new LinkedList<String>();
    private Map<String, Integer> mCamPendingList = new LinkedHashMap<String, Integer>();
    private int mCurrCameraId = -1;
    private boolean mShouldRetakePicture = false;
    private String mCaller;
    private final Runnable mTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
//            if (mShouldRetakePicture && !mCamPendingList.contains("" + mCurrCameraId)) {
//                Log.d(TAG, "mTimeoutRunnable(): TIMED OUT, retaking picture, CAM_ID = " +
//                        mCurrCameraId + ", caller = " + mCaller);
//                takePicture();
//            }
        }
    };


    public PictureTakerService() {
        mCamNoPreview = CameraNoPreview.getCameraNoPreview();

    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand()");

        return Service.START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }




    @Override
    public void onFailedToAccessOpenedCamera(int camIndex) {

    }

    @Override
    public void onCameraOpened(int camIndex) {

    }

    @Override
    public void onPictureTaken(int camIndex) {

    }

    @Override
    public void onCameraClosed(int camIndex) {

    }


}
