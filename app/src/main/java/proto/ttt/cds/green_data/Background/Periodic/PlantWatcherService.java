package proto.ttt.cds.green_data.Background.Periodic;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import proto.ttt.cds.green_data.Class.CameraNoPreview;
import proto.ttt.cds.green_data.Class.ImageProcessor;
import proto.ttt.cds.green_data.Class.PlantData;
import proto.ttt.cds.green_data.Database.PlantDBHandler;

/**
 * Created by changdo on 17. 8. 3.
 *
 * This is a Service to periodically take pictures, process image, and store phenotype data of plants
 *
 */

public class PlantWatcherService extends Service implements CameraNoPreview.ICameraCallback {

    public static final boolean DEBUG = true;
    public static final String TAG = "PlantWatcherService";
    private static final long TIMEOUT_MS = 5 * 1000;

    public static final String ACTION_GET_AREA = "calc_area";
    // DB  Access
    public static final String FILE_STORAGE_DIR = CameraNoPreview.DEFAULT_STORAGE_DIR.getAbsolutePath();
    public static final String FILE_NAME = "plantCam.jpeg";
    public static final String IMG_DIR = FILE_STORAGE_DIR + "/" + FILE_NAME;
    public static final String SHARED_PREF_PLANT = "plantsName";
    // System values
    public static final int MAX_NUMBER_OF_PLANTS = 6;
    public static final int DEFAULT_CONTOUR_COUNT = 2;
    // Intent keys
    public static final String BUNDLE_KEY_CONTOUR_NUM = "numberOfContours";
    public static final String BUNDLE_KEY_PREVIEW_RECT = "previewRect";
    public static final String BUNDLE_KEY_PREVIEW_NUM = "previewNum";
    public static final String BUNDLE_KEY_SUBAREA_RECT = "subAreaRect";

    private static final int BIGGEST_CONTOUR_INDEX = 0;

    private CameraNoPreview mCam;
    private ImageProcessor mImageProcessor;
    private PlantDBHandler mDB;

    private int mNumOfCameras = 0;
    private int mNumOfContours = 0;
    private int mNumOfPlants = 0;
    private Rect[][] mSubAreaRect;
    private Rect[] mPreviewRect;

    private Intent mIntent;
    private String mAction;

    private Context mContext;
    private String[] mPlantsName = new String[MAX_NUMBER_OF_PLANTS];
    CameraManager mCameraManager;
    CameraManager.AvailabilityCallback mCamAvailabilityCallback;
    Queue<String> mCamPendingList = new LinkedList<String>();

    private Handler mH = new Handler();
    private int mCurrCameraId = -1;
    private boolean mShouldRetakePicture = false;
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

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");
        mNumOfCameras = Camera.getNumberOfCameras();
        mContext = getApplicationContext();

        mCam = new CameraNoPreview(FILE_STORAGE_DIR);
        mCam.registerCameraListener(this);
        mImageProcessor = new ImageProcessor();
        mDB = new PlantDBHandler(mContext);
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        for(int i=0; i<2; i++) {
            mUnavailableCameras |= (i + 1);
        }

        mCameraManager.registerAvailabilityCallback(getCamAvailabilityCallback(),
                new Handler(mContext.getMainLooper()));

        loadPlantNames(mContext, mPlantsName);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
        if (mCam != null) {
            mCam.closeCamera();
        }
        mCameraManager.unregisterAvailabilityCallback(mCamAvailabilityCallback);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand()");
        mIntent = intent;
        if (mIntent != null) {
            mAction = mIntent.getAction();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    switch (mAction) {
                        case ACTION_GET_AREA:
                            Bundle extras = mIntent.getExtras();
                            mNumOfContours = extras.getInt(BUNDLE_KEY_CONTOUR_NUM, 0);

                            Parcelable[] previews = mIntent.getExtras().getParcelableArray(BUNDLE_KEY_PREVIEW_RECT);
                            mPreviewRect = new Rect[previews.length];
                            for (int i=0; i<mPreviewRect.length; i++) {
                                mPreviewRect[i] = (Rect)previews[i];
                            }

                            int prevSize = extras.getInt(BUNDLE_KEY_PREVIEW_NUM, 0);
                            mSubAreaRect = new Rect[prevSize][];
                            for (int i=0; i<prevSize; i++) {
                                Parcelable[] subs = mIntent.getExtras().getParcelableArray(BUNDLE_KEY_SUBAREA_RECT + i);
                                mSubAreaRect[i] = new Rect[subs.length];
                                for (int j=0; j<mSubAreaRect[i].length; j++) {
                                    mSubAreaRect[i][j] = (Rect)subs[j];
                                }
                            }

                            if (mNumOfPlants == 0) {
                                mNumOfPlants = mSubAreaRect.length * mSubAreaRect[0].length;
                            }

                            mCurrCameraId = 0;
                            takePicture();
                            break;
                    }
                }
            }).start();
            return Service.START_STICKY;
        }
        return Service.START_REDELIVER_INTENT;
    }

    private CameraManager.AvailabilityCallback getCamAvailabilityCallback() {
        if (mCamAvailabilityCallback == null) {
            mCamAvailabilityCallback = new CameraManager.AvailabilityCallback() {
                @Override
                public void onCameraAvailable(@NonNull String cameraId) {
                    super.onCameraAvailable(cameraId);
                    if (DEBUG) Log.d(TAG, "onCameraAvailable, id = " + cameraId);
                    updateUnavailableCameras(cameraId, false);
                    if (!mCamPendingList.isEmpty()) {
                        mCurrCameraId = Integer.parseInt(mCamPendingList.poll());
                        takePicture();
                    }
                }

                @Override
                public void onCameraUnavailable(@NonNull String cameraId) {
                    super.onCameraUnavailable(cameraId);
                    updateUnavailableCameras(cameraId, true);
                    if (DEBUG) Log.d(TAG, "onCameraUnavailable, id = " + cameraId);

                }
            };
        }
        return mCamAvailabilityCallback;
    }

    private int mUnavailableCameras = 0x0;
    private boolean isAllCameraReady() {
        if (mUnavailableCameras == 0) {
            if (DEBUG) Log.d(TAG, "isAllCameraReady(): ALL CAMERA READY!");
            return true;
        } else {
            if (DEBUG) Log.d(TAG, "isAllCameraReady(): NOT READY -> " + mUnavailableCameras);
            return false;
        }
    }

    private void takePicture() {
        if (mCurrCameraId >= 0 && mCurrCameraId < mNumOfCameras) {
            if (isAllCameraReady()) {
                boolean isOpened = mCam.openCamera(mCurrCameraId, TAG);
                if (isOpened) {
                    mCam.takePictureWithoutPreview(FILE_NAME);
                    mShouldRetakePicture = true;
                    mH.postDelayed(mTimeoutRunnable, TIMEOUT_MS);
                } else {
                    Log.d(TAG, "takePicture() A CAMERA IS NULL, ADD TO PENDING, camId = " + mCurrCameraId);
                    mCamPendingList.add("" + mCurrCameraId);
                }
            } else {
                Log.d(TAG, "takePicture() A CAMERA IN USE, ADD TO PENDING, camId = " + mCurrCameraId);
                mCamPendingList.add("" + mCurrCameraId);
            }
        }
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

    @Override
    public IBinder onBind(Intent arg0) {
        Log.d(TAG, "onBind()");
        return null;
    }

    @Override
    public void onFailedToAccessOpenedCamera(int camId) {
        Log.d(TAG, "onFailedToAccessOpenedCamera() camId = " + camId);
        mCamPendingList.add("" + camId);
    }

    @Override
    public void onCameraOpened(int camIndex) {
        if (DEBUG) Log.d(TAG, "onCameraOpened() camIndex = " + camIndex);
    }

    @Override
    public void onCameraClosed(int camIndex) {
        if (DEBUG) Log.d(TAG, "onCameraClosed() camIndex = " + camIndex);

        switch(mAction) {
            case ACTION_GET_AREA:
                mCurrCameraId = camIndex + 1;
                takePicture();
//                mCamPendingList.add("" + (camIndex+1));
                break;
        }
    }

    @Override
    public void onPictureTaken(int camId) {
        if (DEBUG) Log.d(TAG, "onPictureTaken() camIndex = " + camId);
        mShouldRetakePicture = false;
        switch(mAction) {
            case ACTION_GET_AREA:
                long currentTime = getCurrentTime();
                double[][] contours = mImageProcessor.getBiggestContoursFromImg(IMG_DIR,
                        mSubAreaRect[camId], mPreviewRect[camId], mNumOfContours);

                if (contours == null) {
                    return;
                }

                if (DEBUG) {
                    for (int i=0; i<contours.length; i++) {
                        for (int j=0; j<contours[0].length; j++) {
                            Log.d(TAG, "onPictureTaken(): contour[" + i + "][" + j + "] = " + contours[i][j]);
                        }
                    }
                }

                // Get the # number of the biggest contours in each sub section
                synchronized (this) {
                    int smallestContourIndex = mNumOfContours - 1;
                    int subAreaCnt = contours[0].length;
                    for (int order=BIGGEST_CONTOUR_INDEX; order<=smallestContourIndex; order++) {
                        for (int loc=0; loc<subAreaCnt; loc++) {
                            int realLoc = (camId * subAreaCnt) + loc;
                            String name = getPlant(realLoc);
                            if (name != null) {
                                double areaSize = contours[order][loc];
                                PlantData plant = new PlantData(realLoc, name, order, areaSize, currentTime);
                                mDB.insertData(plant);
                            }
                        }
                    }
                }

//                mDB.getData(1); //test
                printLogPlantData();
                if (camId == mNumOfCameras -1) {
                    stopSelf();
                }
                break;
        }

    }

    public void printLogPlantData() {
        if (PlantDBHandler.DEBUG_PLANT_DB) {
            for (int i = 0; i < mNumOfPlants; i++) {
                mDB.getData(i);
            }
        } else {
            Log.d(TAG, "printLogPlantData(): Cannot print log in a non-debug mode");
        }
    }

    private String getPlant(int location) {
        if (location < 0 || location >= mPlantsName.length || mPlantsName[location] == null) {
            Log.d(TAG, "getPlant(): No plant existing in location: " + location);
            return null;
        }
        return mPlantsName[location];
    }

    /**
     * Time in YYMMDDHHMM format
     */
    private long getCurrentTime() {
        Calendar c = Calendar.getInstance();
        int[] timeElements = new int[5];
        timeElements[4] = c.get(Calendar.YEAR) % 2000;
        timeElements[3] = c.get(Calendar.MONTH) + 1;
        timeElements[2] = c.get(Calendar.DAY_OF_MONTH);
        timeElements[1] = c.get(Calendar.HOUR_OF_DAY);
        timeElements[0] = c.get(Calendar.MINUTE);
        long time = 0;
        long multiplier = 100;
        for(int i = 0; i < timeElements.length; i++) {
            if (i == 0) {
                time += timeElements[i];
            } else {
                time += timeElements[i] * multiplier;
                multiplier *= 100;
            }
        }
        return time;
    }

    public static boolean savePlantName(Context context, String plantName, int realLocation) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_PLANT, 0);
        SharedPreferences.Editor editor = prefs.edit();

        if (DEBUG) {
            if (prefs.getString("" + realLocation, null) != null) {
                Log.d(TAG, "savePlantName(): Overwriting at location: " + realLocation + " for new plant: "
                        + plantName);
            } else {
                Log.d(TAG, "savePlantName(): Adding plant: " + plantName + " at location: " + realLocation);
            }
        }

        editor.putString("" + realLocation, plantName);
        return editor.commit();
    }

    public static void loadPlantNames(Context context, String[] plants) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_PLANT, 0);
        Map<String, ?> entries = prefs.getAll();
        int size = entries.size();
        if (size == 0) {
            Log.d(TAG, "loadPlantNames(): No entries for " + SHARED_PREF_PLANT);
            return;
        }

        Set<String> keys = entries.keySet();
        for(String key : keys) {
            plants[Integer.parseInt(key)] = (String)entries.get(key);
        }
    }

    public static void resetPlantNames(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_PLANT, 0);
        prefs.edit().clear().commit();
        Log.d(TAG, "resetPlantNames(): Clear SharedPreferences (dataName: " + SHARED_PREF_PLANT + ")");
    }

}
