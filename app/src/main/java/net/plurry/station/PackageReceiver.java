package net.plurry.station;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by imgwang-gug on 2015. 10. 8..
 */
public class PackageReceiver extends BroadcastReceiver{

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if(action.equals(intent.ACTION_PACKAGE_ADDED)) {
            Intent i = new Intent(context, WebsocketService.class);
            context.startService(i);
        }
    }
}
