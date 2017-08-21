package proto.ttt.cds.green_data.Activity;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

import proto.ttt.cds.green_data.Background.Periodic.MyAlarmReceiver;
import proto.ttt.cds.green_data.Background.Periodic.PlantWatcherService;
import proto.ttt.cds.green_data.Class.CameraNoPreview;
import proto.ttt.cds.green_data.Database.PlantDBHandler;
import proto.ttt.cds.green_data.R;

public class MainActivity extends AppCompatActivity {
    static final boolean DEBUG = true;
    public static final String TAG = "MainActivity";
    public static final boolean USE_CAMERA_PREVIEW = false;

    public static final int PLANTS_NUM = 3;
    public static final int PREVIEW_IMG_NUM = 1;
    public static final int CAMERA_NUM = Camera.getNumberOfCameras();

    private TextView mInfoViews[] = new TextView[PLANTS_NUM];
    private View[] mDividerViews = new View[PLANTS_NUM + 1];
    private float[] mDividersPos = new float[PLANTS_NUM + 1];
    private Button mBtn_startService, mBtn_stopService, mBtn_delData;
    final H mH = new H();

    private int mFrameHeight, mFrameWidth;
    private int mCurOrientation;

    private PlantDBHandler mDBHelper;

    private PendingIntent mAlarmManagerIntent;

    private SurfaceView mCameraSurfaceView;
    private SurfaceHolder.Callback mSurfaceListener;
    private Camera mCamera;

    private ImageView[] mPrevImageViews = new ImageView[PREVIEW_IMG_NUM];
    private CameraNoPreview mCam;
    private CameraNoPreview.ICameraCallback mCameraPrevListener;

    public MainActivity() {}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        if (PREVIEW_IMG_NUM > CAMERA_NUM) {
            Toast.makeText(this, "Number of preview images not supported in current system environment"
                    , Toast.LENGTH_LONG);
            finish();
        }

        getSupportActionBar().hide();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        updateConfigForOrientationChange();
        if (mCurOrientation == Configuration.ORIENTATION_PORTRAIT) {
            setContentView(R.layout.activity_main_port);
        } else if (mCurOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.activity_main_land);
        }

        if (!USE_CAMERA_PREVIEW) {
            mCam = new CameraNoPreview(null);
        }

        initViews();
        setListeners();

        PlantWatcherService.resetPlantNames(this);
        PlantWatcherService.savePlantName(this, "canary", 0);
        PlantWatcherService.savePlantName(this, "rose", 1);
        PlantWatcherService.savePlantName(this, "lettuce", 2);

        // instantiate DB
        mDBHelper = new PlantDBHandler(getApplicationContext());
    }

    private void createAlarmManagerIntentIfNeeded() {
        if (mAlarmManagerIntent == null) {
            if (mDividersPos.length >= 2 && mFrameHeight != 0) {
                Rect[] subAreas = new Rect[mDividersPos.length - 1];
                for (int i = 0; i < subAreas.length; i++) {
                    subAreas[i] = new Rect((int)(mDividersPos[i] - mViewOffset), 0,
                            (int)(mDividersPos[i + 1] - mViewOffset), mFrameHeight);
                }

                mAlarmManagerIntent = createWatchPlantServiceIntent(this,
                        PlantWatcherService.DEFAULT_CONTOUR_COUNT, subAreas,
                        new Rect(0, 0, mFrameWidth, mFrameHeight));
            } else {
                Log.d(TAG, "createAlarmManagerIntentIfNeeded(): Intent NOT created! INVALID values: mDividersPos.length = "
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


    float mViewOffset;
    private void initViews() {
        Log.d(TAG, "initViews()");
        if (USE_CAMERA_PREVIEW) {
            mCameraSurfaceView = (SurfaceView) findViewById(getResources().getIdentifier("cameraPreview", "id", getPackageName()));
        } else {
            for (int i = 0; i < mPrevImageViews.length; i++) {
                int resId = getResources().getIdentifier("imgPreview" + (i+1), "id", getPackageName());
                mPrevImageViews[i] = (ImageView) findViewById(resId);
            }
        }

        for(int i = 0; i < mInfoViews.length; i++) {
            int resId = getResources().getIdentifier("area_loc_" + (i+1), "id", getPackageName());
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
        if (USE_CAMERA_PREVIEW) {
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
                        Log.d(TAG, "surfaceChanged(): No existing surface");
                        return;
                    }

                    mFrameHeight = height;
                    mFrameWidth = width;

                    try {
                        mCamera.stopPreview();
                    } catch (Exception e) {
                    }

                    Camera.Parameters params = mCamera.getParameters();
                    List<String> focusModes = params.getSupportedFocusModes();
                    if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                        params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                    }
                    Camera.Size prevSize = getOptimalPreviewSize(params.getSupportedPreviewSizes(),
                            params.getPictureSize().width, params.getPictureSize().height);
                    params.setPreviewSize(prevSize.width, prevSize.height);
                    mCamera.setParameters(params);
                    mCamera.startPreview();

                    setDividersAndInfoPos(width, height);
                }
            };
            if (mCameraSurfaceView != null) {
                mCameraSurfaceView.getHolder().addCallback(mSurfaceListener);
            }
        } else {
            mCameraPrevListener = new CameraNoPreview.ICameraCallback() {
                @Override
                public void onCameraOpened(int camId) {
                    Log.d(TAG, "onCameraOpened()");
                }

                @Override
                public void onPictureTaken(int camId) {
                    Log.d(TAG, "onPictureTaken()");
                    String path = CameraNoPreview.DEFAULT_STORAGE_DIR.getAbsolutePath() + "/" + getImageFileName(camId);
                    Drawable d = Drawable.createFromPath(path);
                    if (d != null && mPrevImageViews[camId] != null) {
                        mPrevImageViews[camId].setImageDrawable(d);
                        setFrameWidthAndHeight(d.getBounds().width(), d.getBounds().height());
                        setDividersAndInfoPos(mFrameWidth, mFrameHeight);

                        int subAreaCnt = mDividersPos.length - 1;
                        String[] infoText = new String[subAreaCnt];
                        for (int i = 0; i < subAreaCnt; i++) {
                            int realLoc = (camId * subAreaCnt) + i;
                            infoText[i] = "Loc" + realLoc;
                        }
                        setText(infoText);
                    }
                }

                @Override
                public void onCameraClosed(int camId) {
                    Log.d(TAG, "onCameraClosed()");
                    takePictureForCamIfNeeded(camId + 1);
                }
            };
            if (mCam != null) {
                mCam.registerPictureTakenListeners(mCameraPrevListener);
            }
        }
    }

    private String getImageFileName(int camId) {
        return "prevImg" + camId + ".jpeg";
    }

    private void setFrameWidthAndHeight(int width, int height) {
        double ratio = (double) width / height;
        mFrameHeight = mWinRect.height();
        if (mWinRect.width() != width) {
            mFrameWidth = (int) (mFrameHeight * ratio);
        } else {
            mFrameWidth = width;
        }
    }

    private void takePictureForCamIfNeeded(int camId) {
        if (camId >= 0 && camId < CAMERA_NUM && camId < PREVIEW_IMG_NUM) {
            mCam.openCamera(camId);
            mCam.takePictureWithoutPrev(getImageFileName(camId));
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        if (!USE_CAMERA_PREVIEW) {
            takePictureForCamIfNeeded(0);
        }
    }

    private void setDividersAndInfoPos(int frameWidth, int frameHeight) {
        int winWidth = mWinRect.width();
        mViewOffset = (winWidth - frameWidth) / 2;
        float tmpSubWidth = frameWidth / PLANTS_NUM;

        if (DEBUG) {
            Log.d(TAG, "setDividersAndInfoPos(): mViewOffset = " + mViewOffset + ", tmpSubWidth = "
                    + tmpSubWidth + "width = " + frameWidth + ", height = " + frameHeight + "winWidth = " + winWidth);
        }

        for (int i = 0; i < mDividersPos.length; i++) {
            mDividersPos[i] = mViewOffset + tmpSubWidth * i;
        }
        setDivderAndInfoPosition(mDividersPos);
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
