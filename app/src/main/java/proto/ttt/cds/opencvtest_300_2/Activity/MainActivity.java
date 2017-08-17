package proto.ttt.cds.opencvtest_300_2.Activity;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import proto.ttt.cds.opencvtest_300_2.Background.Periodic.MyAlarmReceiver;
import proto.ttt.cds.opencvtest_300_2.Background.Periodic.PlantWatcherService;
import proto.ttt.cds.opencvtest_300_2.Database.PlantDBHandler;
import proto.ttt.cds.opencvtest_300_2.R;

public class MainActivity extends AppCompatActivity {
    static final boolean DEBUG = true;
    public static final String TAG = "cdsTest";

    public static final int MAX_CONTOUR = 0;
    public static final int SECOND_MAX_CONTOUR = 1;

    public static final boolean USE_JAVA_CAMERA = false;

    public static final int PLANTS_NUM = 3;
    public static final int CAMERA_NUM = 1;

    private TextView mInfoViews[] = new TextView[PLANTS_NUM];
    private View[] mDividerViews = new View[PLANTS_NUM + 1];
    private float[] mDividersPos = new float[PLANTS_NUM + 1];
    private JavaCameraView[] mJavaCameraViews;
    private CameraBridgeViewBase.CvCameraViewListener2 mCvCameraViewListener2;
    private Button mBtn_startService, mBtn_stopService, mBtn_delData;
    private BaseLoaderCallback mLoaderCallback;
    private Mat mRGB, mImageGray, mTempMat;
    final H mH = new H();

    private int mFrameHeight, mFrameWidth;
    private int mCurOrientation;

    private PlantDBHandler mDBHelper;

    private PendingIntent mAlarmManagerIntent;

    private SurfaceView mCameraSurfaceView;
    private SurfaceHolder.Callback mSurfaceListener;
    private Camera mCamera;

    static {
        if (OpenCVLoader.initDebug()) {
            Log.d(TAG, "MainActivity: OpenCV Loaded");
        } else {
            Log.d(TAG, "MainActivity: OpenCV NOT Loaded");
        }
    }

    public MainActivity() {}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        if (CAMERA_NUM > Camera.getNumberOfCameras()) {
            Toast.makeText(this, "Declared number of cameras not supported in current system environment"
                    , Toast.LENGTH_LONG);
            finish();
        }

        getSupportActionBar().hide();
        updateConfigForOrientationChange();
        if (mCurOrientation == Configuration.ORIENTATION_PORTRAIT) {
            setContentView(R.layout.activity_main_port);
        } else if (mCurOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.activity_main_land);
        }

        initViews();
        setListeners();

        PlantWatcherService.resetPlantNames(this);
        PlantWatcherService.savePlantName(this, "canary", 0);
        PlantWatcherService.savePlantName(this, "rose", 1);
        PlantWatcherService.savePlantName(this, "lettuce", 2);

        mLoaderCallback = new BaseLoaderCallback(this) {
            @Override
            public void onManagerConnected(int status) {
                switch (status) {
                    case BaseLoaderCallback.SUCCESS:
                        if (USE_JAVA_CAMERA) {
                            for (int i = 0; i < mJavaCameraViews.length; i++) {
                                if (mJavaCameraViews[i] != null) {
                                    mJavaCameraViews[i].enableView();
                                } else {
                                    Log.d(TAG, "onManagerConnected: mJavaCameraViews element is NULL: i = " + i);
                                }
                            }
                        }
                        break;
                }
                super.onManagerConnected(status);
            }
        };

        // instantiate DB
        mDBHelper = new PlantDBHandler(getApplicationContext());
    }

    private void createAlarmManagerIntentIfNeeded() {
        if (mAlarmManagerIntent == null) {
            if (mDividersPos.length >= 2 && mFrameHeight != 0) {
                Rect[] subAreas = new Rect[mDividersPos.length - 1];
                for (int i = 0; i < subAreas.length; i++) {
                    subAreas[i] = new Rect((int)(mDividersPos[i] - mCameraOffSet), 0,
                            (int)(mDividersPos[i + 1] - mCameraOffSet), mFrameHeight);
                }

                mAlarmManagerIntent = createWatchPlantServiceIntent(this,
                        PlantWatcherService.DEFAULT_CONTOUR_COUNT, subAreas,
                        new Rect(0, 0, mFrameWidth, mFrameHeight));
            } else {
                Log.d(TAG, "createAlarmManagerIntentIfNeeded(): Detecting INVALID values: mDividersPos.length = "
                        + mDividersPos.length + ", mFrameWidth = " + mFrameHeight);
            }
        }
    }

    public static PendingIntent createWatchPlantServiceIntent(Context context, int numOfContours
            , Rect[] areas, Rect wholeRect) {
        Intent intent = new Intent(context, MyAlarmReceiver.class);
        intent.setAction(MyAlarmReceiver.ACTION_WATCH_PLANT_SERVICE);
        Bundle bundle = new Bundle();
        bundle.putInt("numberOfContours", numOfContours);
        bundle.putParcelableArray("observeAreas", areas);
        bundle.putParcelable("baseArea", wholeRect);
        intent.putExtras(bundle);
        return PendingIntent.getBroadcast(context, MyAlarmReceiver.REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }


    float mCameraOffSet;
    private void initViews() {
        Log.d(TAG, "initViews()");
        if (USE_JAVA_CAMERA) {
            mJavaCameraViews = new JavaCameraView[CAMERA_NUM];
             int visibleCamIdx = -1;
            for (int i = 0; i < mJavaCameraViews.length; i++) {
                int resId = getResources().getIdentifier("javaCameraView_" + (i + 1), "id", getPackageName());
                mJavaCameraViews[i] = (JavaCameraView) findViewById(resId);
//            if (visibleCamIdx == -1) {
//                visibleCamIdx = i;
                mJavaCameraViews[i].setVisibility(SurfaceView.VISIBLE);
//            mJavaCameraViews[i].setVisibility(SurfaceView.INVISIBLE);
//            } else {
//                mJavaCameraViews[i].setVisibility(SurfaceView.INVISIBLE);
//            }
            }
        } else {
            int resId = getResources().getIdentifier("cameraPreview", "id", getPackageName());
            mCameraSurfaceView = (SurfaceView) findViewById(resId);
        }

        for(int i = 0; i < mInfoViews.length; i++) {
            int resId = getResources().getIdentifier("txt_largest_area_" + (i+1), "id", getPackageName());
            mInfoViews[i] = (TextView) findViewById(resId);
        }

        for (int i = 0; i < mDividerViews.length; i++) {
            int resId = getResources().getIdentifier("divider_" + (i+1), "id", getPackageName());
            mDividerViews[i] = (View) findViewById(resId);

            // set it to become touch-draggable
            if (mDividerViews[i] != null && i > 0 && i < mDividerViews.length - 1) {
                final int pos = i;
                mDividerViews[pos].setOnTouchListener(new View.OnTouchListener() {
                    float dx;
                    @Override
                    public boolean onTouch(View view, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                dx = view.getX() - event.getRawX();
                                break;
                            case MotionEvent.ACTION_MOVE:
                                float newX = event.getRawX() + dx;
                                if (newX > mDividersPos[pos - 1] + 10
                                        && newX < mDividersPos[pos + 1] - 10) {
                                    mDividersPos[pos] = newX;
                                    setDivderAndInfoPosition(mDividersPos);
                                }
                                break;
                            default:
                                return false;
                        }
                        return true;
                    }
                });
            }
        }

        mBtn_startService = (Button) findViewById(R.id.btn_start_service);
        if (mBtn_startService != null) {
            mBtn_startService.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    //schedule service then destroy activity
                    scheduleAlarm();
                    finish();
                }
            });
        }

        mBtn_stopService = (Button) findViewById(R.id.btn_stop_service);
        if (mBtn_stopService != null) {
            mBtn_stopService.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    stopScheduleAlarm();
                    Toast.makeText(getApplicationContext(), "Alarm service stopped", Toast.LENGTH_LONG).show();
                }
            });
        }

        mBtn_delData = (Button) findViewById(R.id.btn_delete_data);
        if (mBtn_delData != null) {
            mBtn_delData.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mDBHelper.deleteData();
                    Toast.makeText(getApplicationContext(), "Data deleted", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void setListeners() {
        Log.d(TAG, "setListeners()");
        if (USE_JAVA_CAMERA) {
            mCvCameraViewListener2 = new CameraBridgeViewBase.CvCameraViewListener2() {
                @Override
                public void onCameraViewStarted(int width, int height) {
                    Log.d(TAG, "onCameraViewStarted");
                    mFrameHeight = height;
                    mFrameWidth = width;

                    mRGB = new Mat(height, width, CvType.CV_8UC4);
                    mImageGray = new Mat(height, width, CvType.CV_8UC1);
                    mTempMat = new Mat(height, width, CvType.CV_8UC1);

                    int winWidth = mWinRect.width();
                    mCameraOffSet = (winWidth - width) / 2;
                    float tmpSubWidth = width / PLANTS_NUM;

                    if (DEBUG) {
                        Log.d(TAG, "onCameraViewStarted(): mCameraOffSet = " + mCameraOffSet + ", tmpSubWidth = " + tmpSubWidth
                                + "\nwidth = " + width + ", height = " + height
                                + "\nwinWidth = " + winWidth);
                    }

                    for (int i = 0; i < mDividersPos.length; i++) {
                        mDividersPos[i] = mCameraOffSet + tmpSubWidth * i;
                    }
                    setDivderAndInfoPosition(mDividersPos);
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
                    Imgproc.cvtColor(mRGB, mTempMat, Imgproc.COLOR_RGB2HSV);

                    Mat masked = new Mat();
                    Scalar green_l = new Scalar(30, 50, 50);
                    Scalar green_u = new Scalar(90, 255, 255);

                    Core.inRange(mTempMat, green_l, green_u, masked);
                    Imgproc.dilate(masked, masked, Imgproc.getStructuringElement(Imgproc.MORPH_DILATE, new Size(15,15)));

                    Mat preview = new Mat();
                    masked.copyTo(preview);

                    List<MatOfPoint> contourPoints = new ArrayList<MatOfPoint>();

                    // creates an array to store the biggest, and the second biggest contour
                    double[][] maxAreaArr = new double[][]{{0, 0, 0}, {0, 0, 0}};

//                    mFrameHeight = masked.rows();
//                    mFrameWidth = masked.cols();

                    for (int i = 0; i < PLANTS_NUM; i++) {
                        contourPoints.clear();
                        if (mCurOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                            int area_leftX = (int)(mDividersPos[i] - mCameraOffSet);
                            int area_RightX = (int)(mDividersPos[i + 1] - mCameraOffSet);

                            if (area_leftX < 0 || area_RightX > masked.cols()) {
                                Log.d(TAG, "onCameraFrame(): Wrong subArea bounds, area_leftX = " + area_leftX +
                                        ", area_RightX = " + area_RightX + "\nmasked(rows, cols) = (" + masked.rows()
                                        + ", " + masked.cols() + ")");
                                return null;
                            }

                            Imgproc.findContours(masked.submat(0, mFrameHeight, area_leftX, area_RightX),
                                    contourPoints, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
                        } else {
                            Imgproc.findContours(masked, contourPoints, new Mat()
                                    , Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
                        }

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
            };

            for (int i = 0; i < mJavaCameraViews.length && mJavaCameraViews[i] != null; i++) {
                mJavaCameraViews[i].setCvCameraViewListener(mCvCameraViewListener2);
            }
        } else {
            mSurfaceListener = new SurfaceHolder.Callback() {
                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    Log.i(TAG, "surfaceDestroyed()");
                    mCamera.release();
                    mCamera = null;
                }

                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    Log.i(TAG, "surfaceCreated()");
                    mCamera = Camera.open();
                    try {
                        Camera.Parameters parameters = mCamera.getParameters();
                        if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
                            parameters.set("orientation", "portrait");
                            mCamera.setDisplayOrientation(90);
                            parameters.setRotation(90);
                        } else {
                            parameters.set("orientation", "landscape");
                            mCamera.setDisplayOrientation(0);
                            parameters.setRotation(0);
                        }
                        mCamera.setParameters(parameters);

                        mCamera.setPreviewDisplay(holder);
                        mCamera.startPreview();
                    } catch (IOException e) {
                        Log.d(TAG, "Error setting camera preview: " + e.getMessage());
                    }
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    Log.i(TAG, "surfaceChanged(): width = " + width + ", height = " + height);
                    if (holder.getSurface() == null) {
                        Log.d(TAG,"surfaceChanged(): No existing surface");
                        return;
                    }

                    mFrameHeight = height;
                    mFrameWidth = width;

                    try {
                        mCamera.stopPreview();
                    } catch (Exception e){
                    }

                    Camera.Parameters params = mCamera.getParameters();
                    List<String> focusModes = params.getSupportedFocusModes();
                    if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                        params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                    }
                    Camera.Size prevSize = getOptimalPreviewSize(params.getSupportedPreviewSizes(), params.getPictureSize().width, params.getPictureSize().height);
                    params.setPreviewSize(prevSize.width, prevSize.height);
                    mCamera.setParameters(params);
                    mCamera.startPreview();

                    int winWidth = mWinRect.width();
                    mCameraOffSet = (winWidth - width) / 2;
                    float tmpSubWidth = width / PLANTS_NUM;

                    if (DEBUG) {
                        Log.d(TAG, "surfaceChanged(): mCameraOffSet = " + mCameraOffSet + ", tmpSubWidth = " + tmpSubWidth
                                + "\nwidth = " + width + ", height = " + height
                                + "\nwinWidth = " + winWidth);
                    }

                    for (int i = 0; i < mDividersPos.length; i++) {
                        mDividersPos[i] = mCameraOffSet + tmpSubWidth * i;
                    }
                    setDivderAndInfoPosition(mDividersPos);
                }
            };
            if (mCameraSurfaceView != null) {
                mCameraSurfaceView.getHolder().addCallback(mSurfaceListener);
            }
        }
    }

    private void scheduleAlarm() {
        createAlarmManagerIntentIfNeeded();
        long firstMillis = System.currentTimeMillis();
        AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, firstMillis, 1000 * 30, mAlarmManagerIntent);
    }

    private void stopScheduleAlarm() {
        createAlarmManagerIntentIfNeeded();
        AlarmManager am = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        am.cancel(mAlarmManagerIntent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        if (USE_JAVA_CAMERA) {
            if (mJavaCameraViews != null) {
                for (int i = 0; i < mJavaCameraViews.length; i++) {
                    if (mJavaCameraViews[i] != null) {
                        mJavaCameraViews[i].disableView();
                    } else {
                        Log.d(TAG, "onManagerConnected: mJavaCameraViews element is NULL: i = " + i);
                    }
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (USE_JAVA_CAMERA) {
            if (mJavaCameraViews != null) {
                for (int i = 0; i < mJavaCameraViews.length; i++) {
                    if (mJavaCameraViews[i] != null) {
                        mJavaCameraViews[i].disableView();
                    } else {
                        Log.d(TAG, "onManagerConnected: mJavaCameraViews element is NULL: i = " + i);
                    }
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


    private void setDivderAndInfoPosition(float[] newPosition) {
        mH.obtainMessage(H.SET_DIVIDER_AND_INFO_POSITION, newPosition).sendToTarget();
    }

    private void setText(String[] str) {
        mH.obtainMessage(H.SET_TEXT, str).sendToTarget();
    }

    Rect mWinRect = new Rect();
    private void updateConfigForOrientationChange() {
        if (mCurOrientation != getResources().getConfiguration().orientation) {
            Window win = getWindow();
            win.getDecorView().getWindowVisibleDisplayFrame(mWinRect);

            mCurOrientation = getResources().getConfiguration().orientation;
        }
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) h / w;

        if (sizes == null)
            return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.height / size.width;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue;

            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    final class H extends Handler {
        public static final int SET_TEXT = 0;
        public static final int SET_DIVIDER_AND_INFO_POSITION = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SET_TEXT:
                    String[] str = (String[]) msg.obj;
                    if (str.length != 0 && str.length == mInfoViews.length) {
                        for (int i = 0; i < str.length; i++) {
                            if (mInfoViews[i] != null) {
                                mInfoViews[i].setText(str[i]);
                            }
                        }
                    }
                    break;
                case SET_DIVIDER_AND_INFO_POSITION:
                    float[] pos = (float[]) msg.obj;
                    for (int i = 0; i < mDividerViews.length; i++) {
                        if (mDividerViews[i] != null) {
                            mDividerViews[i].setX((int) pos[i]);
                        }

                        if (i < mInfoViews.length && mInfoViews[i] != null) {
                            if (i == 0) {
                                mInfoViews[i].setX((int) pos[i] + 10);
                            } else {
                                mInfoViews[i].setX((int) pos[i] + 25);
                            }
                        }
                    }
                    break;
            }
        }
    }

}
