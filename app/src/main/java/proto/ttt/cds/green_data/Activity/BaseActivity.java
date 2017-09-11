package proto.ttt.cds.green_data.Activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import proto.ttt.cds.green_data.Background.Periodic.YellowWatcherService;
import proto.ttt.cds.green_data.R;

public class BaseActivity extends AppCompatActivity {
    public static final String TAG = "BaseActivity";

    YellowReceiver mYellowReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

    }

    private void registerReceiver(){
        mYellowReceiver = new YellowReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(YellowWatcherService.SEND_YELLOW_LOCATION);
        registerReceiver(mYellowReceiver, intentFilter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(mYellowReceiver);
        super.onStop();
    }

    public class YellowReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive()");
            int location = intent.getIntExtra("location", -1);
            Log.d(TAG, "onReceive(), YELLOWED AREA AT LOC = " + location);
        }
    }
}
