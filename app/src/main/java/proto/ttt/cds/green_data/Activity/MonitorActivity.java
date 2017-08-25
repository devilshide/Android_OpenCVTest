package proto.ttt.cds.green_data.Activity;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import proto.ttt.cds.green_data.Background.Periodic.MyAlarmReceiver;
import proto.ttt.cds.green_data.Background.Periodic.PlantWatcherService;
import proto.ttt.cds.green_data.Class.CameraNoPreview;
import proto.ttt.cds.green_data.Database.PlantDBHandler;
import proto.ttt.cds.green_data.R;

public class MonitorActivity extends AppCompatActivity {
    static final boolean DEBUG = true;
    public static final String TAG = "MonitorActivity";

    public static final int PLANTS_NUM = 6;
    public static final int PREVIEW_NUM = 2;
    public static final int PLANTS_NUM_PREVIEW = PLANTS_NUM / PREVIEW_NUM;
    public static final int CAMERA_NUM = Camera.getNumberOfCameras();

    private TextView mInfoViews[] = new TextView[PLANTS_NUM];
    private String mInfoTextString[] = new String[PLANTS_NUM]; // Names of plants for each location respectively
    private View[][] mDividerViews = new View[PREVIEW_NUM + 1][PLANTS_NUM_PREVIEW];
    private float[][] mDividerViewPos = new float[PREVIEW_NUM + 1][PLANTS_NUM_PREVIEW];
    private boolean[][] mStationaryDivIndex = new boolean[PREVIEW_NUM + 1][PLANTS_NUM_PREVIEW];;
    private Button mBtn_startService, mBtn_stopService, mBtn_delData;
    final H mH = new H();

    private int mCurOrientation;
    private PendingIntent mAlarmManagerIntent;

    private ImageView[] mPrevImageView = new ImageView[PREVIEW_NUM];
    private Rect[] mPrevRect = new Rect[PREVIEW_NUM];
    private CameraNoPreview mCam;

    private String[] mPlantNames = new String[PlantWatcherService.MAX_NUMBER_OF_PLANTS];


    public MonitorActivity() {}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_monitor);
        if (PREVIEW_NUM > CAMERA_NUM) {
            Toast.makeText(this, "Number of preview images not supported in current system environment"
                    , Toast.LENGTH_LONG);
            finish();
        }

        getSupportActionBar().hide();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        updateConfigForOrientationChange();

        mCam = new CameraNoPreview(null);
        setStationaryIndexes();

        initViews();
        initCamera();

//        PlantWatcherService.resetPlantNames(this);
//        PlantWatcherService.savePlantName(this, "canary", 0);
//        PlantWatcherService.savePlantName(this, "rose", 1);
//        PlantWatcherService.savePlantName(this, "lettuce", 2);
        PlantWatcherService.loadPlantNames(this, mPlantNames);
    }

    private boolean isStationaryDivIndex(int y, int x) {
        return mStationaryDivIndex[y][x];
    }

    private void setStationaryIndexes() {
        for (int y=0; y<mStationaryDivIndex.length; y++) {
            for (int x=0; x<mStationaryDivIndex[y].length; x++) {
                if (x == 0 || y == mStationaryDivIndex.length-1) {
                    mStationaryDivIndex[y][x] = true;
                } else {
                    mStationaryDivIndex[y][x] = false;
                }
            }
        }
    }

    private void createAlarmManagerIntentIfNeeded() {
        if (mAlarmManagerIntent==null) {
            if (mDividerViewPos.length>=2) {
                Rect[][] subAreas = new Rect[PREVIEW_NUM][PLANTS_NUM_PREVIEW];
                for (int y=0; y<subAreas.length; y++) {
                    for (int x=0; x<subAreas[y].length; x++) {
                        int left = (int)getRealDividerPos(y, x);
                        int right = x < subAreas[y].length-1 ? (int)getRealDividerPos(y, x + 1) :
                                mPrevRect[y].width();
                        subAreas[y][x] = new Rect(left, 0, right, mPrevRect[y].height());
                        if (DEBUG) {
                            Log.d(TAG, "createAlarmManagerIntentIfNeeded(): subAreas(" + y + "," + x + ") = "
                                    + subAreas[y][x]);
                        }
                    }
                }

                mAlarmManagerIntent = createWatchPlantServiceIntent(this,
                        PlantWatcherService.DEFAULT_CONTOUR_COUNT, mPrevRect, subAreas);
            } else {
                Log.d(TAG, "createAlarmManagerIntentIfNeeded(): Intent NOT created! INVALID values: " +
                        "mDividerViewPos.length = " + mDividerViewPos.length);
            }
        }
    }

    public static PendingIntent createWatchPlantServiceIntent(Context context, int numOfContours
            ,Rect[] rect, Rect[][] subRect) {
        Intent intent = new Intent(context, MyAlarmReceiver.class);
        intent.setAction(MyAlarmReceiver.ACTION_WATCH_PLANT_SERVICE);
        Bundle bundle = new Bundle();
        bundle.putInt(PlantWatcherService.BUNDLE_KEY_CONTOUR_NUM, numOfContours);
        bundle.putParcelableArray(PlantWatcherService.BUNDLE_KEY_PREVIEW_RECT, rect);

        int areaSize = subRect.length;
        bundle.putInt(PlantWatcherService.BUNDLE_KEY_PREVIEW_NUM, areaSize);
        for (int i=0; i <areaSize; i++) {
            bundle.putParcelableArray(PlantWatcherService.BUNDLE_KEY_SUBAREA_RECT + i, subRect[i]);
        }

        intent.putExtras(bundle);
        return PendingIntent.getBroadcast(context, MyAlarmReceiver.REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void initViews() {
        Log.d(TAG, "initViews()");
        for (int i=0; i<mPrevImageView.length; i++) {
            int resId = getResources().getIdentifier("imgPreview"+(i+1), "id", getPackageName());
            mPrevImageView[i] = (ImageView) findViewById(resId);
            final int camId = i;
            if (mPrevImageView[i] != null) {
                mPrevImageView[i].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        takePictureForCamIfNeeded(camId);
                    }
                });
            }
            mPrevRect[i] = new Rect();
        }

        for(int i=0; i<mInfoViews.length; i++) {
            int resId = getResources().getIdentifier("area_loc_"+(i+1), "id", getPackageName());
            mInfoViews[i] = (TextView) findViewById(resId);
            if (mInfoViews[i] != null) {
                final int realLoc = i;
                mInfoViews[i].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        showInputDialog(realLoc);
                    }
                });
            }
        }

        for (int y=0; y<mDividerViews.length; y++) {
            for (int x=0; x<mDividerViews[y].length; x++) {
                int resId = getResources().getIdentifier("divider_"+y+"_"+x, "id", getPackageName());
                mDividerViews[y][x] = (View)findViewById(resId);

                // set it to become touch-draggable
                if (!isStationaryDivIndex(y, x) && mDividerViews[y][x]!=null) {
                    final int posY = y;
                    final int posX = x;
                    mDividerViews[posY][posX].setOnTouchListener(new View.OnTouchListener() {
                        float dx;
                        @Override
                        public boolean onTouch(View view, MotionEvent event) {
                            switch (event.getAction()) {
                                case MotionEvent.ACTION_DOWN:
                                    dx = view.getX() - event.getRawX();
                                    break;
                                case MotionEvent.ACTION_MOVE:
                                    float newX = event.getRawX() + dx;
                                    float padding = 30;
                                    float thresMin = getRealDividerPos(posY, posX - 1) + padding;
                                    float thresMax = posX < mDividerViewPos[posY].length - 1 ?
                                            getRealDividerPos(posY, posX + 1) - padding :
                                            mPrevRect[posY].width() - padding;

                                    if (newX + getRealDividerOffset(posY, posX) > thresMin &&
                                            newX + getRealDividerOffset(posY, posX) < thresMax) {
                                        mDividerViewPos[posY][posX] = newX;
                                        setDivderViewPosition();
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
                    resetPlantName();
                    new PlantDBHandler(getApplicationContext()).deleteData();
                    Toast.makeText(getApplicationContext(), "Data deleted", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private float getRealDividerPos(int y, int x) {
        float realDividerOffset = getRealDividerOffset(y, x);
        if (realDividerOffset == -1) {
            return -1;
        }
        return mDividerViewPos[y][x] + realDividerOffset;
    }

    private float getRealDividerOffset(int y, int x) {
        String tag = "realDivider";
        View divider = y>=0 && y <mDividerViews.length && x>=0 && x<mDividerViews[y].length ?
                mDividerViews[y][x] : null;
        if (divider == null) {
            Log.d(TAG, "getRealDividerOffset() (y,x) = (" + y + "," + x + ") : no existing view");
            return -1;
        }
        if (divider.findViewWithTag(tag) == null) {
            Log.d(TAG, "getRealDividerOffset() (y,x) = (" + y + "," + x + ") : no existing real-divider view");
            return -1;
        }
        return divider.findViewWithTag(tag).getX();
    }

    private void initCamera() {
        Log.d(TAG, "initCamera()");
        if (mCam != null) {
            mCam.registerCameraListener(new CameraNoPreview.ICameraCallback() {
                @Override
                public void onCameraOpened(int camId) {
                    Log.d(TAG, "onCameraOpened() camId = " + camId);
                }

                @Override
                public void onPictureTaken(int camId) {
                    Log.d(TAG, "onPictureTaken() camId = " + camId);
                    String path = CameraNoPreview.DEFAULT_STORAGE_DIR.getAbsolutePath() + "/"
                            + getImageFileName(camId);
                    Drawable d = Drawable.createFromPath(path);

                    if (d != null && mPrevImageView[camId] != null && mPrevRect[camId] != null) {
                        final int id = camId;
                        mPrevImageView[id].setImageDrawable(d);
                        mPrevImageView[id].setScaleType(ImageView.ScaleType.FIT_XY);
                        mPrevImageView[id].addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                            @Override
                            public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                                mPrevRect[id].set(i, i1, i2, i3);
                                calcDividerDefaultPosForPrev(id, i, i2);
                            }
                        });

                        updateInfoText();
                    }
                }

                @Override
                public void onCameraClosed(int camId) {
                    Log.d(TAG, "onCameraClosed() camId = " + camId);
                    takePictureForCamIfNeeded(camId + 1);
                }
            });
        }
    }

    private String getImageFileName(int camId) {
        return "prevImg" + camId + ".jpeg";
    }

    private void takePictureForCamIfNeeded(int camId) {
        if (camId >= 0 && camId < CAMERA_NUM && camId < PREVIEW_NUM) {
            mCam.openCamera(camId);
            mCam.takePictureWithoutPreview(getImageFileName(camId));
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
        if (mCam != null) {
            mCam.closeCamera();
        }
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

        takePictureForCamIfNeeded(0);
    }

    private void calcDividerDefaultPosForPrev(int camId, int left, int right) {
        float tmpSubWidth = (float)(right - left) / PLANTS_NUM_PREVIEW;
        for (int i = 0; i< mDividerViewPos[camId].length; i++) {
            mDividerViewPos[camId][i] = left + tmpSubWidth * i - mDividerViews[camId][i].getPaddingLeft();
        }
        setDivderViewPosition();
    }

    private void updateInfoText() {
        for (int i=0; i<PREVIEW_NUM; i++) {
            for (int j=0; j<PLANTS_NUM_PREVIEW; j++) {
                int realLoc = (i * PLANTS_NUM_PREVIEW) + j;
                if (mPlantNames[realLoc] == null) {
                    mInfoTextString[realLoc] = "Loc" + realLoc;
                } else {
                    mInfoTextString[realLoc] = mPlantNames[realLoc];
                }
            }
        }
        setInfoText();
    }

    private void setDivderViewPosition() {
        mH.obtainMessage(H.SET_DIVIDER_AND_INFO_POSITION, mDividerViewPos).sendToTarget();
    }

    private void setInfoText() {
        mH.obtainMessage(H.SET_INFO_TEXT, mInfoTextString).sendToTarget();
    }

    Rect mWinRect = new Rect();
    private void updateConfigForOrientationChange() {
        if (mCurOrientation != getResources().getConfiguration().orientation) {
            Window win = getWindow();
            win.getDecorView().getWindowVisibleDisplayFrame(mWinRect);

            mCurOrientation = getResources().getConfiguration().orientation;
        }
    }

    private void showInputDialog(int realLocation) {
        final int loc = realLocation;
        final Context context = this;
        // get prompts.xml view
        LayoutInflater li = LayoutInflater.from(this);
        View promptsView = li.inflate(R.layout.dialog_plant_input, null);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

        // set prompts.xml to alertdialog builder
        alertDialogBuilder.setView(promptsView);

        final EditText userInput = (EditText) promptsView.findViewById(R.id.editTextDialogUserInput);

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // get user input and set it to result
                                setPlantName(userInput.getText().toString(), loc);
                            }
                        })
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,int id) {
                                dialog.cancel();
                            }
                        });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    private void setPlantName(String str, int realLocation) {
        PlantWatcherService.savePlantName(getApplicationContext(), str, realLocation);
        mPlantNames[realLocation] = str;
        updateInfoText();
    }

    private void resetPlantName() {
        PlantWatcherService.resetPlantNames(getApplicationContext());
        mPlantNames = new String[PlantWatcherService.MAX_NUMBER_OF_PLANTS];
        updateInfoText();
    }

    final class H extends Handler {
        public static final int SET_INFO_TEXT = 0;
        public static final int SET_DIVIDER_AND_INFO_POSITION = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SET_INFO_TEXT:
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
                    float[][] pos = (float[][]) msg.obj;
                    for (int y=0; y<mDividerViews.length; y++) {
                        for (int x=0; x<mDividerViews[y].length; x++) {
                         if (!isStationaryDivIndex(y, x) && mDividerViews[y][x] != null &&
                                    mDividerViews[y][x].getX() != pos[y][x]) {
                                mDividerViews[y][x].setX((int) pos[y][x]);
                            }
                        }
                    }
                    break;
            }
        }
    }

}
