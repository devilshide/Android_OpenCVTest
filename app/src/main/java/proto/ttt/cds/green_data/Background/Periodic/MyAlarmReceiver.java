package proto.ttt.cds.green_data.Background.Periodic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * Created by changdo on 17. 8. 2.
 *
 * All periodic services are started through here
 */

public class MyAlarmReceiver extends BroadcastReceiver {

    public static final int REQUEST_CODE = 9999;

    public static final String ACTION_WATCH_PLANT_SERVICE = "watch_plant";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        switch(action) {
            case ACTION_WATCH_PLANT_SERVICE:
                Intent serviceIntent = new Intent(context, PlantWatcherService.class);
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    serviceIntent.putExtras(extras);
                }
                serviceIntent.setAction(PlantWatcherService.ACTION_GET_AREA);
                context.startService(serviceIntent);
                break;
        }

    }
}
