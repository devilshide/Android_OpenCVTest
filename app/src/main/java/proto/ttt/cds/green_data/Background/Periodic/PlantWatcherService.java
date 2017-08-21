package proto.ttt.cds.green_data.Background.Periodic;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.util.Log;

import java.util.Calendar;
import java.util.Map;
import java.util.Set;

import proto.ttt.cds.green_data.Class.CameraNoPreview;
import proto.ttt.cds.green_data.Class.ImageProcessor;
import proto.ttt.cds.green_data.Class.PlantData;
import proto.ttt.cds.green_data.Database.PlantDBHandler;

/**
 * Created by changdo on 17. 8. 3.
 */

public class PlantWatcherService extends Service implements CameraNoPreview.ICameraCallback {

    public static final boolean DEBUG = true;
    public static final String TAG = "PlantWatcherService";

    public static final String ACTION_GET_AREA = "calc_area";
    // DB  Access
    public static final String FILE_STORAGE_DIR = CameraNoPreview.DEFAULT_STORAGE_DIR.getAbsolutePath();
    public static final String FILE_NAME = "plantCam.jpeg";
    public static final String IMG_DIR = FILE_STORAGE_DIR + "/" + FILE_NAME;
    public static final String SHARED_PREF_PLANT = "plantsName";
    // System values
    public static final int MAX_NUMBER_OF_PLANTS = 6;
    public static final int DEFAULT_CONTOUR_COUNT = 2;

    private static final int BIGGEST_CONTOUR_INDEX = 0;

    private CameraNoPreview mCam;
    private ImageProcessor mImageProcessor;
    private PlantDBHandler mDB;

    private int mNumOfCameras = 0;
    private int mNumOfContours = 0;
    private Rect[] mSubAreaRect;
    private Rect mWholeAreaRect;

    private Intent mIntent;
    private String mAction;

    private Context mContext;
    private String[] mPlantsName = new String[MAX_NUMBER_OF_PLANTS];

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");
        mNumOfCameras = Camera.getNumberOfCameras();
        mContext = getApplicationContext();

        mCam = new CameraNoPreview(FILE_STORAGE_DIR);
        mCam.registerPictureTakenListeners(this);
        mImageProcessor = new ImageProcessor();
        mDB = new PlantDBHandler(mContext);

        loadPlantNames(mContext, mPlantsName);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
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
                            mNumOfContours = extras.getInt("numberOfContours", 0);
                            Parcelable[] allSubAreas = mIntent.getExtras().getParcelableArray("observeAreas");
                            mSubAreaRect = new Rect[allSubAreas.length];
                            for (int k = 0; k < allSubAreas.length; k++) {
                                mSubAreaRect[k] = (Rect) allSubAreas[k];
                            }
                            Parcelable wholeArea = mIntent.getExtras().getParcelable("baseArea");
                            mWholeAreaRect = (Rect) wholeArea;

                            takePictureIfNeeded(0);
                            break;
                    }
                }
            }).start();
            return Service.START_STICKY;
        }
        return Service.START_REDELIVER_INTENT;
    }

    private void takePictureIfNeeded(int camId) {
        mNumOfCameras = 1;
        if (camId >= 0 && camId < mNumOfCameras) {
            mCam.openCamera(camId);
            mCam.takePictureWithoutPrev(FILE_NAME);
        }
    }

    @Override
    public IBinder onBind(Intent arg0) {
        Log.d(TAG, "onBind()");
        return null;
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
                takePictureIfNeeded(camIndex + 1);
                break;
        }
    }

    @Override
    public void onPictureTaken(int camId) {
        if (DEBUG) Log.d(TAG, "onPictureTaken() camIndex = " + camId);
        switch(mAction) {
            case ACTION_GET_AREA:
                long currentTime = getCurrentTime();
                double[][] contours = mImageProcessor.getBiggestContoursFromImg(IMG_DIR, mSubAreaRect,
                        mWholeAreaRect, mNumOfContours);

                if (contours == null) {
                    return;
                }

                if (DEBUG) {
                    for (int i = 0; i < contours.length; i++) {
                        for (int j = 0; j < contours[0].length; j++) {
                            Log.d(TAG, "onPictureTaken(): contour[" + i + "][" + j + "] = " + contours[i][j]);
                        }
                    }
                }

                // Get the # number of the biggest contours in each sub section
                synchronized (this) {
                    int smallestSizeIndex = mNumOfContours - 1;
                    int subAreaCnt = contours[0].length;
                    for (int order = BIGGEST_CONTOUR_INDEX; order <= smallestSizeIndex; order++) {
//                        int count = contours[order].length;
                        for (int loc = 0; loc < subAreaCnt; loc++) {
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

//                mDB.getData("canary"); //test
                mDB.getData(1); //test
                stopSelf();
                break;
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

    public static boolean savePlantName(Context context, String plantName, int location) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_PLANT, 0);
        SharedPreferences.Editor editor = prefs.edit();

        if (DEBUG) {
            if (prefs.getString("" + location, null) != null) {
                Log.d(TAG, "savePlantName(): Overwriting at location: " + location + " for new plant: "
                        + plantName);
            } else {
                Log.d(TAG, "savePlantName(): Adding plant: " + plantName + " at location: " + location);
            }
        }

        editor.putString("" + location, plantName);
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
