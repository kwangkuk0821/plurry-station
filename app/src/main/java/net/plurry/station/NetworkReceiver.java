package net.plurry.station;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * Created by imgwang-gug on 2015. 10. 8..
 */
public class NetworkReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (checkInternet(context)) {
            Toast.makeText(context, "Network Connected", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(context, "Network Disconnected", Toast.LENGTH_LONG).show();
        }
    }

    public boolean checkInternet(Context context) {
        ServiceManager serviceManager = new ServiceManager(context);
        if (serviceManager.isNetworkAvailable()) {
            return true;
        } else {
            return false;
        }
    }
}
