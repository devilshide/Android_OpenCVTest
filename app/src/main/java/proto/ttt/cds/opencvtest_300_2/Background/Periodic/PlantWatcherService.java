package proto.ttt.cds.opencvtest_300_2.Background.Periodic;

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

import proto.ttt.cds.opencvtest_300_2.Class.CameraAction;
import proto.ttt.cds.opencvtest_300_2.Class.ImageProcessor;
import proto.ttt.cds.opencvtest_300_2.Class.PlantData;
import proto.ttt.cds.opencvtest_300_2.Database.PlantDBHandler;

/**
 * Created by changdo on 17. 8. 3.
 */

public class PlantWatcherService extends Service implements CameraAction.ICameraCallback {

    public static final boolean DEBUG = true;

    public static final String TAG = "PlantWatcherService";
    public static final String ACTION_GET_AREA = "calc_area";

    // DB  Access
    public static final String FILE_NAME = "plantCam.jpeg";
    public static final String SHARED_PREF_PLANT = "plantsName";

    public static final int MAX_NUMBER_OF_PLANTS = 6;   //TODO: subject to change
    public static final int PLANTS_PER_CAM = 3;   //TODO: subject to change

    private static final int BIGGEST_AREA = 0;
    private static final int SECOND_BIGGEST_AREA = 1;
    private static final int THIRD_BIGGEST_AREA = 2;

    private CameraAction mCamAction;
    private ImageProcessor mImageProcessor;
    private PlantDBHandler mDB;

    private int mNumOfCameras = 0;
    private int mNumOfContours = 0;
    private Rect[] mSubAreaRect;

    private Intent mIntent;
    private String mAction;

    private Context mContext;
    private String[] mPlantsName = new String[MAX_NUMBER_OF_PLANTS];
//    private String[][] mPlantsName;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");
        mNumOfCameras = Camera.getNumberOfCameras();
        mContext = getApplicationContext();

        mCamAction = new CameraAction();
        mCamAction.registerPictureTakenListeners(this);
        mImageProcessor = new ImageProcessor(CameraAction.STORAGE_DIR_FILE.getAbsolutePath() + "/" + FILE_NAME);
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
        mAction = mIntent.getAction();
        new Thread(new Runnable() {
            @Override
            public void run() {
                switch(mAction) {
                    case ACTION_GET_AREA:
                        Bundle extras = mIntent.getExtras();
                        mNumOfContours = extras.getInt("numberOfContours", 0);
                        Parcelable[] allParcelables = mIntent.getExtras().getParcelableArray("observeAreas");
                        mSubAreaRect = new Rect[allParcelables.length];
                        for(int k = 0; k < allParcelables.length; k++) {
                            mSubAreaRect[k] = (Rect) allParcelables[k];
                        }

                        takePictureForNextCamIfNeeded();
                        break;
                }
            }
        }).start();

        return Service.START_STICKY;
    }


    private int mNextCamIndex = 0;
    private void takePictureForNextCamIfNeeded() {
        synchronized (this) {
            if (mNextCamIndex >= 0 && mNextCamIndex < mNumOfCameras) {
                mCamAction.openCamera(mNextCamIndex);
                mCamAction.takePictureNoPrev(FILE_NAME);
                mNextCamIndex++;
            } else {
                mNextCamIndex = 0;
            }
        }
    }

    @Override
    public IBinder onBind(Intent arg0) {
        Log.d(TAG, "onBind()");
        return null;
    }

    @Override
    public void onCameraOpened() {
        if (DEBUG) Log.d(TAG, "onCameraOpened() (CALLBACK)");
    }

    @Override
    public void onCameraClosed() {
        if (DEBUG) Log.d(TAG, "onCameraClosed() (CALLBACK)");
        switch(mAction) {
            case ACTION_GET_AREA:
                takePictureForNextCamIfNeeded();
                break;
        }
    }

    @Override
    public void onPictureTaken() {
        if (DEBUG) Log.d(TAG, "onPictureTaken() (CALLBACK)");
        switch(mAction) {
            case ACTION_GET_AREA:
                long currentTime = getCurrentTime();
                double[][] contours
                        = mImageProcessor.getBiggestContoursFromImg(mSubAreaRect, mNumOfContours);

                if (DEBUG) {
                    for (int i = 0; contours != null && i < contours.length; i++) {
                        for (int j = 0; contours[0] != null && j < contours[0].length; j++) {
                            Log.d(TAG, "onPictureTaken(): contour[" + i + "][" + j + "] = "
                                    + contours[i][j]);
                        }
                    }
                }

                // TODO: For now, get only the biggest contour. In the following versions we'll search for the second biggest as well
                if (contours != null && contours[BIGGEST_AREA] != null) {
                    int plantsCnt = contours[BIGGEST_AREA].length;
                    synchronized (this) {
                        for (int plantLoc = 0; plantLoc < plantsCnt; plantLoc++) {
                            String name = getPlantName((mNextCamIndex - 1) * plantsCnt + plantLoc);
                            if (name != null) {
                                double areaSize = contours[BIGGEST_AREA][plantLoc];
                                PlantData plant = new PlantData(name, BIGGEST_AREA, areaSize, currentTime);
                                mDB.insertData(plant);
                            }
                        }
                    }
                }

                mDB.getData("canary"); //test
                stopSelf();
                break;
        }

    }

    private String getPlantName(int loc) {
        if (loc < 0 || loc >= mPlantsName.length || mPlantsName[loc] == null) {
            Log.d(TAG, "getPlantName(): No plant existing in the location: " + loc);
            return null;
        }
        return mPlantsName[loc];
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

        if (prefs.getString("" + location, null) != null) {
            Log.d(TAG, "savePlantName(): Overwriting at location: " + location + " for new plant: "
                    + plantName);
        }
        Log.d(TAG, "savePlantName(): Adding plant: " + plantName + " at location: " + location);
        editor.putString("" + location, plantName);
        return editor.commit();
    }

    public static void loadPlantNames(Context context, String[] plants) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_PLANT, 0);

        Map<String, ?> entries = prefs.getAll();
        int size = entries.size();
        if (size == 0) {
            Log.d(TAG, "loadPlantNames(): Size is 0 for " + SHARED_PREF_PLANT);
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
