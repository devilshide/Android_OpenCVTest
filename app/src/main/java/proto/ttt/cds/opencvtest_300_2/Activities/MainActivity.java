package proto.ttt.cds.opencvtest_300_2.Activities;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import proto.ttt.cds.opencvtest_300_2.Classes.PlantDBHelper;
import proto.ttt.cds.opencvtest_300_2.R;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2  {

    static final boolean DEBUG = false;

    public static final int MAX_CONTOUR = 0;
    public static final int SECOND_MAX_CONTOUR = 1;

    public static final String TAG = "cdsTest";
    public static final int PLANTS_NUM = 3;
//    public static final int CAMERA_NUM = PLANTS_NUM;
    public static final int CAMERA_NUM = 1;

    float[] mSectorDividersPos;
    private TextView mText_Infos[] = new TextView[PLANTS_NUM];
    private TextView[] mDividerViews = new TextView[PLANTS_NUM + 1];
    private JavaCameraView[] mJavaCameraViewArr;
    private BaseLoaderCallback mLoaderCallback;
    private Mat mRGB, mImageGray, mImageCanny, mTempMat;
    final H mH = new H();

    private int mDisplayHeight, mDisplayWidth;
    private int mCurrentOrientation;

    private PlantDBHelper mDBHelper;

    static {
        if (OpenCVLoader.initDebug()) {
            Log.d(TAG, "MainActivity: OpenCV Loaded");
        } else {
            Log.d(TAG, "MainActivity: OpenCV NOT Loaded");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        if (CAMERA_NUM > Camera.getNumberOfCameras()) {
            Toast.makeText(this, "Declared number of cameras not supported in current system environment"
                    , Toast.LENGTH_LONG);
            onDestroy();
        }

        updateConfigForOrientationChange();
        if (mCurrentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            setContentView(R.layout.activity_main_port);
        } else if (mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.activity_main_land);
        }

        mJavaCameraViewArr = new JavaCameraView[CAMERA_NUM];
        int visibleCamIdx = -1;
        for(int i = 0 ; i < mJavaCameraViewArr.length; i++) {
            int resId = getResources().getIdentifier("javaCameraView_" + (i+1), "id", getPackageName());
            mJavaCameraViewArr[i] = (JavaCameraView) findViewById(resId);
//            if (visibleCamIdx == -1) {
//                visibleCamIdx = i;
                mJavaCameraViewArr[i].setVisibility(SurfaceView.VISIBLE);
//            mJavaCameraViewArr[i].setVisibility(SurfaceView.INVISIBLE);
//            } else {
//                mJavaCameraViewArr[i].setVisibility(SurfaceView.INVISIBLE);
//            }
            mJavaCameraViewArr[i].setCvCameraViewListener(this);
        }

        for(int i = 0 ; i < mText_Infos.length; i++) {
            int resId = getResources().getIdentifier("txt_largest_area_" + (i+1), "id", getPackageName());
            mText_Infos[i] = (TextView) findViewById(resId);
        }

        for (int i = 0; i < mDividerViews.length; i++) {
            int resId = getResources().getIdentifier("divider_" + (i+1), "id", getPackageName());
            mDividerViews[i] = (TextView) findViewById(resId);
        }

        mLoaderCallback = new BaseLoaderCallback(this) {
            @Override
            public void onManagerConnected(int status) {
                switch(status) {
                    case BaseLoaderCallback.SUCCESS:
                        for(int i = 0 ; i < mJavaCameraViewArr.length; i++) {
                            if (mJavaCameraViewArr[i] != null) {
                                mJavaCameraViewArr[i].enableView();
                            } else {
                                Log.d(TAG, "onManagerConnected: mJavaCameraViewArr element is NULL: i = " + i);
                            }
                        }
                        break;
                }
                super.onManagerConnected(status);
            }
        };


        // instantiate DB
        mDBHelper = new PlantDBHelper(getApplicationContext());
    }


    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        if(mJavaCameraViewArr != null) {
            for(int i = 0 ; i < mJavaCameraViewArr.length; i++) {
                if (mJavaCameraViewArr[i] != null) {
                    mJavaCameraViewArr[i].disableView();
                } else {
                    Log.d(TAG, "onManagerConnected: mJavaCameraViewArr element is NULL: i = " + i);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if(mJavaCameraViewArr != null) {
            for(int i = 0 ; i < mJavaCameraViewArr.length; i++) {
                if (mJavaCameraViewArr[i] != null) {
                    mJavaCameraViewArr[i].disableView();
                } else {
                    Log.d(TAG, "onManagerConnected: mJavaCameraViewArr element is NULL: i = " + i);
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (OpenCVLoader.initDebug()) {
            Log.d(TAG, "onResume: OpenCV Loaded");
            mLoaderCallback.onManagerConnected(BaseLoaderCallback.SUCCESS);
        } else {
            Log.d(TAG, "onResume: OpenCV NOT Loaded");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.d(TAG, "onCameraViewStarted");
        mRGB = new Mat(height, width, CvType.CV_8UC4);
        mImageGray = new Mat(height, width, CvType.CV_8UC1);
        mImageCanny = new Mat(height, width, CvType.CV_8UC1);
        mTempMat = new Mat(height, width, CvType.CV_8UC1);

        float cameraOffSet= (mDisplayWidth - width) / 2;
        float oneThirdWidth = width / 3;

        mSectorDividersPos = new float[]{cameraOffSet, cameraOffSet + oneThirdWidth,
                cameraOffSet + (oneThirdWidth * 2), cameraOffSet + width}; //TODO: set values in terms of landscape orientation for now

        for(int i = 0; i < mText_Infos.length; i++) {
            if (mText_Infos[i] != null) {
                mText_Infos[i].setWidth((int) oneThirdWidth);
            }
        }

        setDivderAndInfoPosition();
    }

    @Override
    public void onCameraViewStopped() {
        Log.d(TAG, "onCameraViewStopped");
        mRGB.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Log.d(TAG, "onCameraFrame");
        mRGB = inputFrame.rgba();
        Imgproc.cvtColor(mRGB, mImageGray, Imgproc.COLOR_RGB2GRAY);
//        Imgproc.Canny(mImageGray, mImageCanny, 20, 150);
        Imgproc.cvtColor(mRGB, mTempMat, Imgproc.COLOR_RGB2HSV);

        Mat masked = new Mat();
        Scalar green_l = new Scalar(30, 50, 50);
        Scalar green_u = new Scalar(90, 255, 255);

        Core.inRange(mTempMat, green_l, green_u, masked);
        Imgproc.dilate(masked, masked, Imgproc.getStructuringElement(Imgproc.MORPH_DILATE, new Size(15,15)));
//        Imgproc.erode(masked, masked, Imgproc.getStructuringElement(Imgproc.MORPH_ERODE, new Size(10,10)));

        Mat preview = new Mat();
        masked.copyTo(preview);

        List<MatOfPoint> contourPoints = new ArrayList<MatOfPoint>();
//        Mat heirarchy = new Mat();
//        Imgproc.findContours(masked, contourPoints, heirarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        double[] maxArea = new double[]{0, 0, 0};
        double[] secondMaxArea = new double[]{0, 0, 0};

        // creates an array to store the biggest, and the second biggest contour
        double[][] maxAreaArr = new double[][]{{0, 0, 0}, {0, 0, 0}};
        updateConfigForOrientationChange();

        int matRows = masked.rows();
        int matCols = masked.cols();
        for (int i = 0; i < PLANTS_NUM; i++) {
            contourPoints.clear();
            if (mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                int area_leftX = (matCols / 3) * i;
                int area_RightX = (matCols / 3) * (i +  1);

                Imgproc.findContours(masked.submat(0, matRows, area_leftX, area_RightX),
                        contourPoints, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            } else {
                Imgproc.findContours(masked, contourPoints, new Mat()
                        , Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            }


            double smallest_x = 0, smallest_y = 0, largest_x = 0, largest_y = 0;
            Iterator<MatOfPoint> each = contourPoints.iterator();
            while (each.hasNext()) {
                MatOfPoint next = each.next();

                double area = Imgproc.contourArea(next);
                if (area > maxAreaArr[MAX_CONTOUR][i]) {
                    maxAreaArr[SECOND_MAX_CONTOUR][i] = maxAreaArr[MAX_CONTOUR][i];
                    maxAreaArr[MAX_CONTOUR][i] = area;
                } else if (area > maxAreaArr[SECOND_MAX_CONTOUR][i]) {
                    maxAreaArr[SECOND_MAX_CONTOUR][i] = area;
                }

                if (DEBUG) {
                    smallest_x = Double.MAX_VALUE;
                    smallest_y = Double.MAX_VALUE;
                    largest_x = 0;
                    largest_y = 0;
                    org.opencv.core.Point[] arr = next.toArray();
                    for (int k = 0; k < arr.length; k++) {
                        if (arr[k].x > largest_x) {
                            largest_x = arr[k].x;
                        }
                        if (smallest_x > arr[k].x) {
                            smallest_x = arr[k].x;
                        }

                        if (arr[k].y > largest_y) {
                            largest_y = arr[k].y;
                        }
                        if (smallest_y > arr[k].y) {
                            smallest_y = arr[k].y;
                        }
                    }
                }
            }

            if (DEBUG) {
                if ((maxAreaArr[MAX_CONTOUR][i] / 100) > 7000) {
                    char sectorChar = i == 0 ? 'A' : (i == 1 ? 'B' : 'C');
                    Log.d(TAG, "[" + sectorChar + "]" + " onCameraFrame: maxArea = " + maxAreaArr[MAX_CONTOUR][i]
                            + ", x = [" + smallest_x
                            + ", " + largest_x + "]"
                            + ", y = [" + smallest_y
                            + ", " + largest_y + "]");
                }
            } else {
                char sectorChar = i == 0 ? 'A' : (i == 1 ? 'B' : 'C');
                Log.d(TAG, "[" + sectorChar + "]" + " onCameraFrame: maxArea = " + maxAreaArr[MAX_CONTOUR][i]);
            }
        }

        String[] strArr = {"Area A\nPlant 1 = " + (int)(maxAreaArr[MAX_CONTOUR][0] / 100)
                + "\nPlant 2 = " + (int)(maxAreaArr[SECOND_MAX_CONTOUR][0] / 100) ,
                "Area B\nPlant 1 = " + (int)(maxAreaArr[MAX_CONTOUR][1] / 100)
                        + "\nPlant 2 = " + (int)(maxAreaArr[SECOND_MAX_CONTOUR][1] / 100) ,
                "Area C\nPlant 1 = " + (int)(maxAreaArr[MAX_CONTOUR][2] / 100)
                        + "\nPlant 2 = " + (int)(maxAreaArr[SECOND_MAX_CONTOUR][2] / 100)};
        setText(strArr);


        return preview;
    }

    private void setDivderAndInfoPosition() {
        mH.obtainMessage(H.SET_DIVIDER_AND_INFO_POS).sendToTarget();
    }

    private void setText(String[] str) {
        mH.obtainMessage(H.SET_TEXT, str).sendToTarget();
    }

    private void updateConfigForOrientationChange() {
        if (mCurrentOrientation != getResources().getConfiguration().orientation) {
            WindowManager wm = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
            Display dp = wm.getDefaultDisplay();
            Point screenSize = new Point();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                dp.getRealSize(screenSize);
            } else {
                dp.getSize(screenSize);
            }
            mDisplayHeight = screenSize.y;
            mDisplayWidth = screenSize.x;

            mCurrentOrientation = getResources().getConfiguration().orientation;
        }
    }

    final class H extends Handler {
        public static final int SET_TEXT = 0;
        public static final int SET_DIVIDER_AND_INFO_POS = 1;
        public static final int SAVE_DB = 2;

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case SET_TEXT:
                    String[] str = (String[]) msg.obj;
                    if (str.length != 0 && str.length == mText_Infos.length) {
                        for (int i = 0; i < str.length; i++) {
                            if (mText_Infos[i] != null) {
                                mText_Infos[i].setText(str[i]);
                            }
                        }
                    }
                    break;

                case SET_DIVIDER_AND_INFO_POS:
                    for (int i = 0; i < mDividerViews.length; i++) {
                        if (mDividerViews[i] != null) {
                            mDividerViews[i].setX((int) mSectorDividersPos[i]);
                        }

                        if (i < mText_Infos.length && mText_Infos[i] != null) {
                            mText_Infos[i].setX((int) mSectorDividersPos[i] + 10);
                        }
                    }
                    break;

                case SAVE_DB:

                    break;
            }
        }
    }

    private void saveToDB(String name, String subTitle, int area) {
        SQLiteDatabase db = mDBHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(PlantDBHelper.FeedEntry.COLUMN_NAME_TITLE, name);
        values.put(PlantDBHelper.FeedEntry.COLUMN_NAME_SUBTITLE, subTitle);
        values.put(PlantDBHelper.FeedEntry.COLUMN_AREA_SIZE, area);

        long newRowId = db.insert(PlantDBHelper.FeedEntry.TABLE_NAME, null, values);
    }
}
