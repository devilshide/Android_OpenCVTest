package proto.ttt.cds.green_data.Background.Periodic;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.util.Log;

import org.opencv.core.Scalar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Map;
import java.util.Set;

import proto.ttt.cds.green_data.Class.ImageProcessor;
import proto.ttt.cds.green_data.Database.PlantData;
import proto.ttt.cds.green_data.Database.PlantDBHandler;

/**
 *
 * This is a Service to periodically take pictures, process image, and store phenotype data of plants
 *
 */

public class AreaWatcherService extends Service {

    private static final boolean DEBUG = true;
    public static final String TAG = "AreaWatcherService";
    public static final ArrayList<Integer> CAMERAS = new ArrayList<>(Arrays.asList(0,1));

    // DB  Access
    private static final String FILE_NAME = TAG;
    public static final String SHARED_PREF_PLANT = "plantsName";
    // System values
    public static final int MAX_NUMBER_OF_PLANTS = 6;
    public static final int DEFAULT_CONTOUR_COUNT = 2;
    // Bundle keys
    public static final String BUNDLE_KEY_CONTOUR_NUM = "numberOfContours";
    public static final String BUNDLE_KEY_PREVIEW_RECT = "previewRect";
    public static final String BUNDLE_KEY_SUBAREA_DIMEN_1_SIZE = "previewNum";
    public static final String BUNDLE_KEY_SUBAREA_RECT = "subAreaRect";

    private static final int BIGGEST_CONTOUR_INDEX = 0;

    private ImageProcessor mImageProcessor;
    private PlantDBHandler mDB;

    private int mNumOfContours = 0;
    private int mNumOfPlants = 0;
    private Rect[][] mSubAreaRect;
    private Rect[] mPreviewRect;

    private Intent mIntent;
    private Context mContext;
    private String[] mPlantsName = new String[MAX_NUMBER_OF_PLANTS];
    private Scalar mGreen_low = new Scalar(25, 50, 50);
    private Scalar mGreen_upper = new Scalar(80, 255, 255);

    private int mResultForCameras = 0x0;  // Flag to check whether all the results for each camera elements has been returned

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");
        mContext = getApplicationContext();

        mImageProcessor = new ImageProcessor();
        mDB = new PlantDBHandler(mContext);

        loadPlantNames(mContext, mPlantsName);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        if (mAreaReceiver != null) {
            unregisterReceiver(mAreaReceiver);
        }
    }

    private void initResultForCameras() {
        mResultForCameras = 0x0;
        for (int i=0; i<CAMERAS.size(); i++) {
            mResultForCameras |= (CAMERAS.get(i) + 1);
        }
    }

    private void sendBroadcast() {
        Log.d(TAG, "sendBroadcast()");
        Intent intent = new Intent(PictureTakerService.InfoReceiver.REQUEST_INFO);
        Bundle bundle = new Bundle();
        bundle.putString(PictureTakerService.REQUEST_CODE, AreaReceiver.REQUEST_CODE_AREA);
        bundle.putString(PictureTakerService.FILE_NAME, this.FILE_NAME);
        bundle.putIntegerArrayList(PictureTakerService.CAM_ID, CAMERAS);
        intent.putExtras(bundle);
        sendBroadcast(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand()");
        mIntent = intent;
        registerReceiver();

        if (mIntent != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Bundle extras = mIntent.getExtras();

                    //get number of contours to find in each sub rect
                    mNumOfContours = extras.getInt(BUNDLE_KEY_CONTOUR_NUM, 0);

                    //get each preview rect
                    Parcelable[] previews = extras.getParcelableArray(BUNDLE_KEY_PREVIEW_RECT);
                    mPreviewRect = new Rect[previews.length];
                    for (int i=0; i<mPreviewRect.length; i++) {
                        mPreviewRect[i] = (Rect) previews[i];
                    }

                    //get sub rects
                    int prevSize = extras.getInt(BUNDLE_KEY_SUBAREA_DIMEN_1_SIZE, 0);
                    mSubAreaRect = new Rect[prevSize][];
                    for (int i=0; i<prevSize; i++) {
                        Parcelable[] subs = extras.getParcelableArray(BUNDLE_KEY_SUBAREA_RECT + i);
                        mSubAreaRect[i] = new Rect[subs.length];
                        for (int j=0; j<mSubAreaRect[i].length; j++) {
                            mSubAreaRect[i][j] = (Rect) subs[j];
                        }
                    }

                    //get total number of plants
                    if (mNumOfPlants == 0) {
                        mNumOfPlants = mSubAreaRect.length * mSubAreaRect[0].length;
                    }

                    initResultForCameras();
                    sendBroadcast();
                }
            }).start();
            return Service.START_STICKY;
        }
        return Service.START_REDELIVER_INTENT;
    }

    private void processImage(int camId, String path) {
        Log.d(TAG, "processImage(), camId = " + camId + ", path = " + path);
        mResultForCameras = mResultForCameras & ~(camId + 1);

        final int camIndex = camId;
        final String filePath = path;
        new Thread(new Runnable() {
            @Override
            public void run() {
                double[][] contours = mImageProcessor.
                        getBiggestContoursFromImg(filePath, mSubAreaRect[camIndex],
                                mPreviewRect[camIndex], mNumOfContours, new Scalar[]{mGreen_low, mGreen_upper});

                if (contours == null) {
                    return;
                }

                if (DEBUG) {
                    for (int i = 0; i < contours.length; i++) {
                        for (int j = 0; j < contours[0].length; j++) {
                            Log.d(TAG, "onPictureTaken(): contour[" + i + "][" + j + "] = "
                                    + contours[i][j] + ", filePath = " + filePath);
                        }
                    }
                }

                // Get the # number of the biggest contours in each sub section
                synchronized (this) {
                    long currentTime = getCurrentTime();
                    int smallestContourIndex = mNumOfContours - 1;
                    int subAreaCnt = contours[0].length;
                    for (int order = BIGGEST_CONTOUR_INDEX; order <= smallestContourIndex; order++) {
                        for (int loc = 0; loc < subAreaCnt; loc++) {
                            int realLoc = (camIndex * subAreaCnt) + loc;
                            String name = getPlant(realLoc);
                            if (name != null) {
                                double areaSize = contours[order][loc];
                                PlantData plant = new PlantData(realLoc, name, order,
                                        areaSize, currentTime);
                                mDB.insertData(plant);
                            }
                        }
                    }
                }

                printLogPlantData();
                if (mResultForCameras == 0) {
                    Log.d(TAG, "onPictureTaken(): Stopping, filePath = " + filePath);
                    stopSelf();
                }
            }
        }).start();
    }

    @Override
    public IBinder onBind(Intent arg0) {
        Log.d(TAG, "onBind()");
        return null;
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
                Log.d(TAG, "savePlantName(): Overwriting at location: " + realLocation
                        + " for new plant: " + plantName);
            } else {
                Log.d(TAG, "savePlantName(): Adding plant: " + plantName + " at location: " +
                        realLocation);
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




    AreaReceiver mAreaReceiver;
    private void registerReceiver(){
        mAreaReceiver = new AreaReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(AreaReceiver.REQUEST_CODE_AREA);
        registerReceiver(mAreaReceiver, intentFilter);
    }

    public class AreaReceiver extends BroadcastReceiver {
        static final String REQUEST_CODE_AREA = "requestArea";
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive()");
            int camId = intent.getIntExtra(PictureTakerService.CAM_ID, -1);
            String picPath = intent.getStringExtra(PictureTakerService.STORAGE_PATH);

            processImage(camId, picPath);
        }
    }
}
