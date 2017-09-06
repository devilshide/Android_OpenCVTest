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

import proto.ttt.cds.green_data.Background.Periodic.AreaWatcherService;
import proto.ttt.cds.green_data.Background.Periodic.MyAlarmReceiver;
import proto.ttt.cds.green_data.Class.SequencePictureTaker;
import proto.ttt.cds.green_data.Database.PlantDBHandler;
import proto.ttt.cds.green_data.R;

public class MonitorActivity extends AppCompatActivity {
    private static final boolean DEBUG = true;
    private static final String FILE_NAME_PREVIEW = "MonitorPrevImage";

    public static final String TAG = "MonitorActivity";

    private static final long FREQUECY_WATCH_AREA = 1000 * 30;
    private static final long FREQUECY_WATCH_YELLOW = 1000 * 100;

    public static final int PLANTS_NUM = 6;
    public static final int PREVIEW_NUM = 2;
    public static final int PLANTS_NUM_IN_PREVIEW = PLANTS_NUM / PREVIEW_NUM;
    public static final int CAMERA_NUM = Camera.getNumberOfCameras();
    public static final int[] CAMERAS = new int[]{0, 1};

    private TextView mInfoViews[] = new TextView[PLANTS_NUM];
    private String mInfoTextString[] = new String[PLANTS_NUM]; // Names of plants for each location respectively
    private View[][] mDividerViews = new View[PREVIEW_NUM + 1][PLANTS_NUM_IN_PREVIEW];
    private float[][] mDividerViewPos = new float[PREVIEW_NUM + 1][PLANTS_NUM_IN_PREVIEW];
    private boolean[][] mStationaryDivIndex = new boolean[PREVIEW_NUM + 1][PLANTS_NUM_IN_PREVIEW];;
    private Button mBtn_startService, mBtn_stopService, mBtn_delData;
    final H mH = new H();

    private int mCurOrientation;
    private PendingIntent mPlantWatcherPendingIntent, mYellowWatcherPendingIntent;

    private ImageView[] mPrevImageView = new ImageView[PREVIEW_NUM];
    private Rect[] mPrevRect = new Rect[PREVIEW_NUM];
    private String[] mPlantNames = new String[AreaWatcherService.MAX_NUMBER_OF_PLANTS];

    private SequencePictureTaker mPictureTaker;

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
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        updateConfigForOrientationChange();

        setStationaryIndexes();
        initViews();

        createSequencePictureTaker();

        AreaWatcherService.loadPlantNames(this, mPlantNames);
    }

    private void createSequencePictureTaker() {
        mPictureTaker = new SequencePictureTaker(this, FILE_NAME_PREVIEW, CAMERAS, TAG) {
            @Override
            public void onFailedToAccessOpenedCameraCB(int camId) {}

            @Override
            public void onCameraOpenedCB(int camId) {}

            @Override
            public void onPictureTakenCB(int camId) {
                String path = this.getPicturePath(camId);
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
            public void onCameraClosedCB(int camId) {}
        };
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

    private void initViews() {
        Log.d(TAG, "initViews()");
        for (int i=0; i<mPrevImageView.length; i++) {
            int resId = getResources().getIdentifier("imgPreview"+(i+1), "id", getPackageName());
            mPrevImageView[i] = (ImageView) findViewById(resId);
            if (mPrevImageView[i] != null) {
                mPrevImageView[i].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mPictureTaker.takePictureStart();
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

    private void scheduleAlarm() {
        createPlantWatcherIntentIfNeeded();
        AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(mPlantWatcherPendingIntent);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(),
                FREQUECY_WATCH_AREA, mPlantWatcherPendingIntent);

        createYellowWatcherIntentIfNeeded();
        alarmManager.cancel(mYellowWatcherPendingIntent);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000,
                FREQUECY_WATCH_YELLOW, mYellowWatcherPendingIntent);

    }

    private void stopScheduleAlarm() {
        createPlantWatcherIntentIfNeeded();
        AlarmManager am = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);

        if (mPlantWatcherPendingIntent != null) {
            am.cancel(mPlantWatcherPendingIntent);
        }
        if (mYellowWatcherPendingIntent != null) {
            am.cancel(mYellowWatcherPendingIntent);
        }
    }

    private void createYellowWatcherIntentIfNeeded() {
        if (mYellowWatcherPendingIntent == null) {
            mYellowWatcherPendingIntent = createWatchYellowServiceIntent(this);
            if (DEBUG) {
                Log.d(TAG, "createYellowWatcherIntentIfNeeded()");
            }
        }
    }

    private static PendingIntent createWatchYellowServiceIntent(Context context) {
        Intent intent = new Intent(context, MyAlarmReceiver.class);
        intent.setAction(MyAlarmReceiver.ACTION_WATCH_YELLOW_SERVICE);
        return PendingIntent.getBroadcast(context, MyAlarmReceiver.REQUEST_CODE_WATCH_YELLOW, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void createPlantWatcherIntentIfNeeded() {
        if (mPlantWatcherPendingIntent == null) {
            if (mDividerViewPos.length>=2) {
                Rect[][] subAreas = new Rect[PREVIEW_NUM][PLANTS_NUM_IN_PREVIEW];
                for (int y=0; y<subAreas.length; y++) {
                    for (int x=0; x<subAreas[y].length; x++) {
                        int left = (int)getRealDividerPos(y, x);
                        int right = x < subAreas[y].length-1 ? (int)getRealDividerPos(y, x + 1) :
                                mPrevRect[y].width();
                        subAreas[y][x] = new Rect(left, 0, right, mPrevRect[y].height());
                        if (DEBUG) {
                            Log.d(TAG, "createPlantWatcherIntentIfNeeded(): subAreas(" + y + "," + x + ") = "
                                    + subAreas[y][x]);
                        }
                    }
                }

                mPlantWatcherPendingIntent = createWatchPlantServiceIntent(this,
                        AreaWatcherService.DEFAULT_CONTOUR_COUNT, mPrevRect, subAreas);
            } else {
                Log.d(TAG, "createPlantWatcherIntentIfNeeded(): Intent NOT created! INVALID values: " +
                        "mDividerViewPos.length = " + mDividerViewPos.length);
            }
        }
    }

    private static PendingIntent createWatchPlantServiceIntent(Context context, int numOfContours
            ,Rect[] rect, Rect[][] subRect) {
        Intent intent = new Intent(context, MyAlarmReceiver.class);
        intent.setAction(MyAlarmReceiver.ACTION_WATCH_PLANT_SERVICE);
        Bundle bundle = new Bundle();
        bundle.putInt(AreaWatcherService.BUNDLE_KEY_CONTOUR_NUM, numOfContours);
        bundle.putParcelableArray(AreaWatcherService.BUNDLE_KEY_PREVIEW_RECT, rect);

        int previewSize = subRect.length;
        bundle.putInt(AreaWatcherService.BUNDLE_KEY_SUBAREA_DIMEN_1_SIZE, previewSize);
        for (int i=0; i<previewSize; i++) {
            bundle.putParcelableArray(AreaWatcherService.BUNDLE_KEY_SUBAREA_RECT + i, subRect[i]);
        }

        intent.putExtras(bundle);
        return PendingIntent.getBroadcast(context, MyAlarmReceiver.REQUEST_CODE_WATCH_PLANT, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        mPictureTaker.stop();
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

        mPictureTaker.initCallbacks();
        mPictureTaker.takePictureStart();
    }

    private void calcDividerDefaultPosForPrev(int camId, int left, int right) {
        float tmpSubWidth = (float)(right - left) / PLANTS_NUM_IN_PREVIEW;
        for (int i = 0; i< mDividerViewPos[camId].length; i++) {
            mDividerViewPos[camId][i] = left + tmpSubWidth * i - mDividerViews[camId][i].getPaddingLeft();
        }
        setDivderViewPosition();
    }

    private void updateInfoText() {
        for (int i=0; i<PREVIEW_NUM; i++) {
            for (int j = 0; j< PLANTS_NUM_IN_PREVIEW; j++) {
                int realLoc = (i * PLANTS_NUM_IN_PREVIEW) + j;
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
        AreaWatcherService.savePlantName(getApplicationContext(), str, realLocation);
        mPlantNames[realLocation] = str;
        updateInfoText();
    }

    private void resetPlantName() {
        AreaWatcherService.resetPlantNames(getApplicationContext());
        mPlantNames = new String[AreaWatcherService.MAX_NUMBER_OF_PLANTS];
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
