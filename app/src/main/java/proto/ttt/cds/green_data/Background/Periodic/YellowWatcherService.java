package proto.ttt.cds.green_data.Background.Periodic;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.util.Log;

import java.util.Calendar;
import java.util.Map;
import java.util.Set;

import proto.ttt.cds.green_data.Class.ImageProcessor;
import proto.ttt.cds.green_data.Class.SequencePictureTaker;
import proto.ttt.cds.green_data.Database.PlantDBHandler;
import proto.ttt.cds.green_data.Database.PlantData;

/**
 *
 * This is a Service to periodically take pictures, process image, and store phenotype data of plants
 *
 */

public class YellowWatcherService extends Service {

    private static final boolean DEBUG = true;
    public static final String TAG = "YellowWatcherService";
    public static final int[] CAMERAS = new int[]{0};

    // DB  Access
    private static final String FILE_NAME = TAG;
    public static final String SHARED_PREF_PLANT = "plantsName";
    // System values
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


    private SequencePictureTaker mPictureTaker;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");
        mContext = getApplicationContext();

        mImageProcessor = new ImageProcessor();
        mDB = new PlantDBHandler(mContext);

        createSequencePictureTakerIfNeeded();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
        if (mPictureTaker != null) {
            mPictureTaker.stop();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand()");
        mIntent = intent;
        if (mIntent != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Bundle extras = mIntent.getExtras();

                    //get number of contours to find in each sub rect
//                    mNumOfContours = extras.getInt(BUNDLE_KEY_CONTOUR_NUM, 0);
                    mNumOfContours = 1;

                    //get each preview rect
//                    Parcelable[] previews = extras.getParcelableArray(BUNDLE_KEY_PREVIEW_RECT);
//                    mPreviewRect = new Rect[previews.length];
//                    for (int i = 0; i < mPreviewRect.length; i++) {
//                        mPreviewRect[i] = (Rect) previews[i];
//                    }
//                    mPreviewRect = new Rect[]{new Rect()};
//
//                    //get sub rects
//                    int prevSize = extras.getInt(BUNDLE_KEY_SUBAREA_DIMEN_1_SIZE, 0);
//                    mSubAreaRect = new Rect[prevSize][];
//                    for (int i = 0; i < prevSize; i++) {
//                        Parcelable[] subs = extras.getParcelableArray(BUNDLE_KEY_SUBAREA_RECT + i);
//                        mSubAreaRect[i] = new Rect[subs.length];
//                        for (int j = 0; j < mSubAreaRect[i].length; j++) {
//                            mSubAreaRect[i][j] = (Rect) subs[j];
//                        }
//                    }

                    //get total number of plants
//                    if (mNumOfPlants == 0) {
//                        mNumOfPlants = mSubAreaRect.length * mSubAreaRect[0].length;
//                    }

                    mPictureTaker.initCallbacks();
                    mPictureTaker.takePictureStart();
                }
            }).start();
            return Service.START_STICKY;
        }
        return Service.START_REDELIVER_INTENT;
    }

    private void createSequencePictureTakerIfNeeded() {
        if (mPictureTaker == null) {
            mPictureTaker = new SequencePictureTaker(mContext, FILE_NAME, CAMERAS, TAG) {
                @Override
                public void onFailedToAccessOpenedCameraCB(int camId) {
                }

                @Override
                public void onCameraOpenedCB(int camId) {
                }

                @Override
                public void onPictureTakenCB(int camId) {
                    final int camIndex = camId;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {

                            Rect[] subRects = new Rect[] {
                                    new Rect(0,0,100,150),
                                    new Rect(100,0,200,150),
                                    new Rect(200,0,300,150),
                                    new Rect(0,150,100,300),
                                    new Rect(100,150,200,300),
                                    new Rect(200,150,300,300)};

                            Rect prevRect = new Rect(0,0,300,300);

                            double[][] contours = mImageProcessor.
                                    getBiggestContoursFromImg(getPicturePath(camIndex), subRects, prevRect, mNumOfContours);

                            if (contours == null) {
                                return;
                            }

                            if (DEBUG) {
                                for (int i = 0; i < contours.length; i++) {
                                    for (int j = 0; j < contours[0].length; j++) {
                                        Log.d(TAG, "onPictureTaken(): contour[" + i + "][" + j + "] = "
                                                + contours[i][j] + ", caller = " + mPictureTaker.getCaller());
                                    }
                                }
                            }

                            // Get the # number of the biggest contours in each sub section
//                            synchronized (this) {
//                                long currentTime = getCurrentTime();
//                                int smallestContourIndex = mNumOfContours - 1;
//                                int subAreaCnt = contours[0].length;
//                                for (int order = BIGGEST_CONTOUR_INDEX; order <= smallestContourIndex; order++) {
//                                    for (int loc = 0; loc < subAreaCnt; loc++) {
//                                        int realLoc = (camIndex * subAreaCnt) + loc;
//                                        String name = getPlant(realLoc);
//                                        if (name != null) {
//                                            double areaSize = contours[order][loc];
//                                            PlantData plant = new PlantData(realLoc, name, order,
//                                                    areaSize, currentTime);
//                                            mDB.insertData(plant);
//                                        }
//                                    }
//                                }
//                            }

                            if (camIndex == CAMERAS[CAMERAS.length - 1]) {
                                Log.d(TAG, "onPictureTaken(): Stopping, caller = " + mPictureTaker.getCaller());
                                stopSelf();
                            }
                        }
                    }).start();
                }

                @Override
                public void onCameraClosedCB(int camId) {
                }
            };
        }
    }

    @Override
    public IBinder onBind(Intent arg0) {
        Log.d(TAG, "onBind()");
        return null;
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

}
