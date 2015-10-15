package net.plurry.station;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import android.app.Activity;
import android.graphics.Point;
import android.media.AudioManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;

import android.opengl.GLSurfaceView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.plurry.station.skylink.*;

import org.json.JSONException;
import org.json.JSONObject;

import sg.com.temasys.skylink.sdk.config.SkylinkConfig;
import sg.com.temasys.skylink.sdk.listener.LifeCycleListener;
import sg.com.temasys.skylink.sdk.listener.MediaListener;
import sg.com.temasys.skylink.sdk.listener.RemotePeerListener;
import sg.com.temasys.skylink.sdk.rtc.SkylinkConnection;
import sg.com.temasys.skylink.sdk.rtc.UserInfo;


public class MainActivity extends AppCompatActivity implements LifeCycleListener, MediaListener, RemotePeerListener {

    private Activity this_activity = this;
    private SharedPreferences pref;
    private String prefName = "session";
    private TextView mSmartPhoneCode;
    //Skylink
    private static final String TAG = MainActivity.class.getCanonicalName();
    public static final String ROOM_NAME = Constants.ROOM_NAME_VIDEO;
    public static final String MY_USER_NAME = "videoCallUser";
    private static final String ARG_SECTION_NUMBER = "section_number";
    //set height width for self-video when in call
    public static final int WIDTH = 350;
    public static final int HEIGHT = 350;
    private LinearLayout parentFragment;
    private LinearLayout codeLayout;
    private Button toggleAudioButton;
    private Button toggleVideoButton;
    private SkylinkConnection skylinkConnection;
    private String roomName;
    private String peerId;
    private ViewGroup.LayoutParams selfLayoutParams;
    private boolean audioMuted;
    private boolean videoMuted;
    private boolean connected;
    private AudioRouter audioRouter;

    private PowerManager.WakeLock fullWakeLock;
    private PowerManager.WakeLock partialWakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sendBroadcast(new Intent("net.plurry.station.packagereceiver"));
        websocketServiceStart();
        createWakeLocks();
        super.onCreate(savedInstanceState);
        initUi();
        setListeners();
    }

    @Override
    protected void onPause() {
        Log.e("error", "onPause");
        super.onPause();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        fullWakeLock = pm.newWakeLock((PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), "Loneworker - FULL WAKE LOCK");
        if(!pm.isScreenOn()) {
            finish();
            System.exit(0);
        }
    }

    private void initUi() {
        setContentView(R.layout.activity_main);
        String session = getPreferences("session_id");
        String code = getPreferences("code");
        String product_id = getPreferences("product_id");

        if (session.isEmpty() || code.isEmpty() || product_id.isEmpty()) {
            new CodeTask().execute(
                    "http://plurry.cycorld.com:3000/owr/generate",
                    ""
            );
        }

        mSmartPhoneCode = (TextView) findViewById(R.id.SmartphoneCode);
        parentFragment = (LinearLayout) findViewById(R.id.ll_video_call);
        codeLayout = (LinearLayout) findViewById(R.id.CodeLayout);
        toggleAudioButton = (Button) findViewById(R.id.toggle_audio);
        toggleVideoButton = (Button) findViewById(R.id.toggle_video);

        hideVideo();

        mSmartPhoneCode.setText(code);
        joinRoom(session);
    }

    private void websocketServiceStart() {
        Intent i = new Intent(this_activity, WebsocketService.class);
        startService(i);
    }

    private void joinRoom(String session) {
        if(session.isEmpty()) return;

        String appKey = getString(R.string.app_key);
        String appSecret = getString(R.string.app_secret);

        // Initialize the skylink connection
        initializeSkylinkConnection();

        // Initialize the audio router
        initializeAudioRouter();

        // Obtaining the Skylink connection string done locally
        // In a production environment the connection string should be given
        // by an entity external to the App, such as an App server that holds the Skylink App secret
        // In order to avoid keeping the App secret within the application
        String skylinkConnectionString = Utils.
                getSkylinkConnectionString(session, appKey,
                        appSecret, new Date(), SkylinkConnection.DEFAULT_DURATION);

        skylinkConnection.connectToRoom(skylinkConnectionString,
                MY_USER_NAME);

        // Use the Audio router to switch between headphone and headset
        audioRouter.startAudioRouting(this_activity.getApplicationContext());
        connected = true;
    }

    private void setListeners() {
        toggleAudioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // If audio is enabled, mute audio and if audio is enabled, mute it
                audioMuted = !audioMuted;
                if (audioMuted) {
                    toggleAudioButton.setText(getString(R.string.enable_audio));
                } else {
                    toggleAudioButton.setText(getString(R.string.mute_audio));
                }

                skylinkConnection.muteLocalAudio(audioMuted);
            }
        });

        toggleVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // If video is enabled, mute video and if video is enabled, mute it
                videoMuted = !videoMuted;
                if (videoMuted) {
                    toggleVideoButton.setText(getString(R.string.enable_video));
                } else {
                    toggleVideoButton.setText(getString(R.string.mute_video));
                }

                skylinkConnection.muteLocalVideo(videoMuted);
            }
        });
    }

    private void initializeAudioRouter() {
        if (audioRouter == null) {
            audioRouter = AudioRouter.getInstance();
            audioRouter.init(((AudioManager) this_activity.
                    getSystemService(android.content.Context.AUDIO_SERVICE)));
        }
    }

    private void initializeSkylinkConnection() {
        if (skylinkConnection == null) {
            skylinkConnection = SkylinkConnection.getInstance();
            //the app_key and app_secret is obtained from the temasys developer console.
            skylinkConnection.init(getString(R.string.app_key),
                    getSkylinkConfig(), this.this_activity.getApplicationContext());
            //set listeners to receive callbacks when events are triggered
            skylinkConnection.setLifeCycleListener(this);
            skylinkConnection.setMediaListener(this);
            skylinkConnection.setRemotePeerListener(this);
        }
    }

    private SkylinkConfig getSkylinkConfig() {
        SkylinkConfig config = new SkylinkConfig();
        //AudioVideo config options can be NO_AUDIO_NO_VIDEO, AUDIO_ONLY, VIDEO_ONLY, AUDIO_AND_VIDEO;
        config.setAudioVideoSendConfig(SkylinkConfig.AudioVideoConfig.AUDIO_AND_VIDEO);
        config.setAudioVideoReceiveConfig(SkylinkConfig.AudioVideoConfig.AUDIO_AND_VIDEO);
        config.setHasPeerMessaging(true);
        config.setHasFileTransfer(true);
        config.setTimeout(Constants.TIME_OUT);
        config.setMirrorLocalView(true);
        return config;
    }

    protected void createWakeLocks() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        fullWakeLock = powerManager.newWakeLock((PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), "Loneworker - FULL WAKE LOCK");
        partialWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Loneworker - PARTIAL WAKE LOCK");
    }

    @Override
    public void onStop() {
        //close the connection when the fragment is detached, so the streams are not open.
        super.onStop();
        if (skylinkConnection != null && connected) {
            skylinkConnection.disconnectFromRoom();
            skylinkConnection.setLifeCycleListener(null);
            skylinkConnection.setMediaListener(null);
            skylinkConnection.setRemotePeerListener(null);
            connected = false;
            audioRouter.stopAudioRouting(this_activity.getApplicationContext());
        }
    }
    /***
     * Lifecycle Listener Callbacks -- triggered during events that happen during the SDK's lifecycle
     */

    /**
     * Triggered when connection is successful
     *
     * @param isSuccess
     * @param message
     */

    @Override
    public void onConnect(boolean isSuccess, String message) {
        if (isSuccess) {
            toggleAudioButton.setVisibility(View.VISIBLE);
            toggleVideoButton.setVisibility(View.VISIBLE);
        } else {
            Log.e(TAG, "Skylink Failed " + message);
        }
    }

    @Override
    public void onLockRoomStatusChange(String remotePeerId, boolean lockStatus) {
        //Toast.makeText(this_activity, "Peer " + remotePeerId +
        //       " has changed Room locked status to " + lockStatus, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onWarning(int errorCode, String message) {
        Log.d(TAG, message + "warning");
        //Toast.makeText(this_activity, "Warning is errorCode" + errorCode, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisconnect(int errorCode, String message) {
        Log.d(TAG, message + " disconnected");
        //Toast.makeText(this_activity, "onDisconnect " + message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onReceiveLog(String message) {
        Log.d(TAG, message + " on receive log");
    }

    /**
     * Media Listeners Callbacks - triggered when receiving changes to Media Stream from the remote peer
     */

    /**
     * Triggered after the user's local media is captured.
     *
     * @param videoView
     */
    @Override
    public void onLocalMediaCapture(GLSurfaceView videoView) {
        if (videoView != null) {
            View self = parentFragment.findViewWithTag("self");
            videoView.setTag("self");
            videoView.setVisibility(View.GONE);
            // Allow self view to switch between different cameras (if any) when tapped.
            videoView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    skylinkConnection.switchCamera();
                }
            });

            if (self == null) {
                //show media on screen
                parentFragment.removeView(videoView);
                parentFragment.addView(videoView);
            } else {
                videoView.setLayoutParams(self.getLayoutParams());

                // If peer video exists, remove it first.
                View peer = parentFragment.findViewWithTag("peer");
                if (peer != null) {
                    parentFragment.removeView(peer);
                }

                // Remove the old self video and add the new one.
                parentFragment.removeView(self);
                parentFragment.addView(videoView);

                // Return the peer video, if it was there before.
                if (peer != null) {
                    parentFragment.addView(peer);
                }
            }
        }
    }

    @Override
    public void onVideoSizeChange(String peerId, Point size) {
        Log.d(TAG, "PeerId: " + peerId + " got size " + size.toString());
    }

    @Override
    public void onRemotePeerAudioToggle(String remotePeerId, boolean isMuted) {
        String message = null;
        if (isMuted) {
            message = "Your peer muted their audio";
        } else {
            message = "Your peer unmuted their audio";
        }

        //Toast.makeText(this_activity, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRemotePeerVideoToggle(String peerId, boolean isMuted) {
        String message = null;
        if (isMuted)
            message = "Your peer muted video";
        else
            message = "Your peer unmuted their video";

        //Toast.makeText(this_activity, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Remote Peer Listener Callbacks - triggered during events that happen when data or connection
     * with remote peer changes
     */

    @Override
    public void onRemotePeerJoin(String remotePeerId, Object userData, boolean hasDataChannel) {
        showVideo();

        Toast.makeText(this_activity, "상대방과 연결되었습니다.", Toast.LENGTH_SHORT).show();
        UserInfo remotePeerUserInfo = skylinkConnection.getUserInfo(remotePeerId);
        Log.d(TAG, "isAudioStereo " + remotePeerUserInfo.isAudioStereo());
        Log.d(TAG, "video height " + remotePeerUserInfo.getVideoHeight());
        Log.d(TAG, "video width " + remotePeerUserInfo.getVideoHeight());
        Log.d(TAG, "video frameRate " + remotePeerUserInfo.getVideoFps());
    }

    @Override
    public void onRemotePeerMediaReceive(String remotePeerId, GLSurfaceView videoView) {
        if (videoView == null) {
            return;
        }

        if (!TextUtils.isEmpty(this.peerId) && !remotePeerId.equals(this.peerId)) {
            Toast.makeText(this_activity, "이미 상대방과 연결되어 있는 상태입니다.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Resize self view
        View self = parentFragment.findViewWithTag("self");
        if (this.selfLayoutParams == null) {
            // Record the original size of the layout
            this.selfLayoutParams = self.getLayoutParams();
        }

        self.setLayoutParams(new ViewGroup.LayoutParams(WIDTH, HEIGHT));
        parentFragment.removeView(self);
        parentFragment.addView(self);

        // Remove previous peer video if it exist
        View viewToRemove = parentFragment.findViewWithTag("peer");
        if (viewToRemove != null) {
            parentFragment.removeView(viewToRemove);
        }

        // Add new peer video
        videoView.setTag("peer");
        parentFragment.addView(videoView);

        this.peerId = remotePeerId;
    }

    @Override
    public void onRemotePeerLeave(String remotePeerId, String message) {
        hideVideo();
        finish();
        System.exit(0);

        Toast.makeText(this_activity, "상대방과의 연결이 끊어졌습니다.", Toast.LENGTH_SHORT).show();
        if (remotePeerId != null && remotePeerId.equals(this.peerId)) {
            this.peerId = null;
            View peerView = parentFragment.findViewWithTag("peer");
            parentFragment.removeView(peerView);

            // Resize self view to original size
            if (this.selfLayoutParams != null) {
                View self = parentFragment.findViewWithTag("self");
                self.setLayoutParams(selfLayoutParams);
            }
        }
    }

    @Override
    public void onRemotePeerUserDataReceive(String remotePeerId, Object userData) {
        Log.d(TAG, "onRemotePeerUserDataReceive " + remotePeerId);
    }

    @Override
    public void onOpenDataConnection(String peerId) {
        Log.d(TAG, "onOpenDataConnection");
    }

    public class CodeTask extends AsyncTask<String, Void, String> {

        ProgressDialog dataPending = new ProgressDialog(this_activity);

        public String jsonConverter(String str) {
            str = str.replace("\\", "");
            str = str.replace("\"{", "{");
            str = str.replace("}\",", "},");
            str = str.replace("}\"", "}");

            return str;
        }

        @Override
        protected void onPreExecute() {
            dataPending.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dataPending.setMessage("데이터를 불러오는 중 입니다...");

            dataPending.show();
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... params) {
            HttpURLConnection conn;

            URL url = null;
            int responseCode = 0;
            String urlParameters = null;
            String response = null;
            DataOutputStream os = null;
            InputStream is = null;
            BufferedReader br = null;
            try {
                url = new URL(params[0]);
                urlParameters = params[1];
                Log.d("parameters", urlParameters);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setReadTimeout(5000);
                conn.setConnectTimeout(5000);
                conn.setRequestProperty("charset", "euc-kr");
                conn.setDoInput(true);
                conn.setDoOutput(true);

                os = new DataOutputStream(conn.getOutputStream());
                os.writeBytes(urlParameters);
                os.flush();

                responseCode = conn.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {

                    is = conn.getInputStream();
                    br = new BufferedReader(new InputStreamReader(is));

                    response = new String(br.readLine());
                    response = jsonConverter(response);

                    JSONObject responseJSON = new JSONObject(response);
                }
            } catch (MalformedURLException e) {
                Log.d("MalformedURLException", "ERROR " + e.getMessage());
            } catch (IOException e) {
                Log.d("IOException", "ERROR " + e.getMessage());
            } catch (JSONException e) {
                Log.d("JSONException", "ERROR " + e.getMessage());
            }

            if (responseCode == HttpURLConnection.HTTP_OK) {
                return response;
            } else {
                return "fail";
            }
        }
        protected void onPostExecute(String data) {
            dataPending.dismiss();
            // result is what you got from your connection
            if(!data.equals("fail")) {
                JSONObject resultJSON = null;
                String result = null;
                String what = null;
                try {
                    resultJSON = new JSONObject(data);
                    result = resultJSON.getString("result");
                    what = resultJSON.getString("what");
                    if(what.equals("generate code")) {
                        JSONObject product = (JSONObject) resultJSON.get("data");
                        String code = product.getString("code");
                        String session_id = product.getString("owr_session_id");
                        String product_id = product.getString("product_id");
                        savePreferences("code", code);
                        savePreferences("session_id", session_id);
                        savePreferences("product_id", product_id);
                        finish();
                        startActivity(getIntent());

                        Log.d(TAG,product_id.toString());
                    }
                } catch (JSONException e) {
                    Log.d("JSONException", "ERROR " + e.getMessage());
                }
            }
        }
    }

    private void showVideo() {
        parentFragment.setVisibility(View.VISIBLE);
        codeLayout.setVisibility(View.GONE);
    }

    private void hideVideo() {
        parentFragment.setVisibility(View.GONE);
        codeLayout.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    //값 불러오기
    public String getPreferences(String key) {
        pref = getSharedPreferences(prefName, MODE_PRIVATE);
        String data = pref.getString(key, "");
        return data;
    }

    // 값 저장하기
    public void savePreferences(String key, String value) {
        pref = getSharedPreferences(prefName, MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(key, value);
        editor.commit();
    }

    // 값(Key Data) 삭제하기
    public void removePreferences(String key) {
        pref = getSharedPreferences(prefName, MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.remove(key);
        editor.commit();
    }

    // 값(ALL Data) 삭제하기
    public void removeAllPreferences() {
        pref = getSharedPreferences(prefName, MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.clear();
        editor.commit();
    }
}
