package proto.ttt.cds.green_data.Receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import proto.ttt.cds.green_data.Background.Periodic.AreaWatcherService;
import proto.ttt.cds.green_data.Background.Periodic.YellowWatcherService;

/**
 * Created by changdo on 17. 8. 2.
 *
 * All periodic services are started through here
 */

public class MyAlarmReceiver extends BroadcastReceiver {

    public static final int REQUEST_CODE_WATCH_PLANT = 9999;
    public static final int REQUEST_CODE_WATCH_YELLOW = 9998;
    public static final String TAG = "MyAlarmReceiver";

    public static final String ACTION_WATCH_PLANT_SERVICE = "watch_plant";
    public static final String ACTION_WATCH_YELLOW_SERVICE = "watch_yellow";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        switch(action) {
            case ACTION_WATCH_PLANT_SERVICE: {
                Log.d(TAG, "ACTION_WATCH_PLANT_SERVICE");
                Intent serviceIntent = new Intent(context, AreaWatcherService.class);
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    serviceIntent.putExtras(extras);
                }
                context.stopService(serviceIntent);
                context.startService(serviceIntent);
            } break;

            case ACTION_WATCH_YELLOW_SERVICE: {
                Log.d(TAG, "ACTION_WATCH_YELLOW_SERVICE");
                Intent serviceIntent = new Intent(context, YellowWatcherService.class);
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    serviceIntent.putExtras(extras);
                }
                context.stopService(serviceIntent);
                context.startService(serviceIntent);
            } break;
        }

    }
}
