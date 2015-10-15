package net.plurry.station;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import net.plurry.station.websocket.*;

import java.net.URI;
import java.util.List;

/**
 * Created by imgwang-gug on 2015. 10. 8..
 */
public class WebsocketService extends Service {

    private SharedPreferences pref;
    private String prefName = "session";
    private WebSocketClient client = null;

    private PowerManager.WakeLock fullWakeLock;
    private PowerManager.WakeLock partialWakeLock;
    KeyguardManager.KeyguardLock keyguardLock;
    PowerManager powerManager;

    private boolean keyguard = true;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static boolean isRunningProcess(Context context, String packageName) {

        boolean isRunning = false;

        ActivityManager actMng = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        List<ActivityManager.RunningAppProcessInfo> list = actMng.getRunningAppProcesses();

        for (ActivityManager.RunningAppProcessInfo rap : list) {
            if (rap.processName.equals(packageName)) {
                isRunning = true;
                break;
            }
        }
        return isRunning;
    }

    protected void createWakeLocks() {
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        fullWakeLock = powerManager.newWakeLock((PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), "Loneworker - FULL WAKE LOCK");
        partialWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Loneworker - PARTIAL WAKE LOCK");
    }

    public void wakeDevice() {
        boolean isScreenOn = powerManager.isScreenOn();
        if(!isScreenOn) {
            fullWakeLock.acquire();
        }
    }

    public ComponentName getCurrentActivity() {
        ActivityManager am = (ActivityManager) getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);

        ComponentName topActivity = taskInfo.get(0).topActivity;

        return topActivity;
    }

    @Override
    public void onCreate() {
        sendBroadcast(new Intent("net.plurry.station.networkreceiver"));
        Log.e("WebsocketService", "onCreate");
        createWakeLocks();

        String product_id = getPreferences("product_id");
        client = new WebSocketClient(URI.create("ws://plurry.cycorld.com:3000/ws/debug/" + product_id), new WebSocketClient.Listener() {
            @Override
            public void onConnect() {
                Log.d("Connect", "Connected!");
                client.send("station on");
            }

            @Override
            public void onMessage(String message) {
                if (message.equals("remote on")) {
                    boolean isScreenOn = powerManager.isScreenOn();
                    if (isScreenOn) {
                        if (!getCurrentActivity().getPackageName().equals("net.plurry.station")) {
                            wakeDevice();
                            Intent intent = new Intent();
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            intent.setClassName(getPackageName(), "net.plurry.station.MainActivity");
                            startActivity(intent);
                        }
                    } else {
                        wakeDevice();
                        Intent intent = new Intent();
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        intent.setClassName(getPackageName(), "net.plurry.station.MainActivity");
                        startActivity(intent);
                    }
                }
            }

            @Override
            public void onMessage(byte[] data) {
                Log.d("byte", String.format("Got binary message! %s", ("" + data)));
            }

            @Override
            public void onDisconnect(int code, String reason) {
                Log.d("Disconnect", String.format("Disconnected! Code: %d Reason: %s", code, reason));
            }

            @Override
            public void onError(Exception error) {
                Log.e("Error", "Error!", error);
            }

        }, null);
        client.connect();
        startForeground(1, new Notification());
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.e("WebsocketService", "onDestory");
        client.disconnect();
        client = null;
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e("WebsocketService", "onStartCommand");
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        keyguardLock = keyguardManager.newKeyguardLock("TAG");
        keyguardLock.disableKeyguard();
        if (!client.isConnected()) client.connect();
        return START_STICKY;
    }

    //값 불러오기
    private String getPreferences(String key) {
        pref = getSharedPreferences(prefName, MODE_PRIVATE);
        String data = pref.getString(key, "");
        return data;
    }

    // 값 저장하기
    private void savePreferences(String key, String value) {
        pref = getSharedPreferences(prefName, MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(key, value);
        editor.commit();
    }

    // 값(Key Data) 삭제하기
    private void removePreferences(String key) {
        pref = getSharedPreferences(prefName, MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.remove(key);
        editor.commit();
    }

    // 값(ALL Data) 삭제하기
    private void removeAllPreferences() {
        pref = getSharedPreferences(prefName, MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.clear();
        editor.commit();
    }
}
