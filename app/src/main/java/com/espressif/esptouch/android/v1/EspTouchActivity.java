package com.espressif.esptouch.android.v1;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;

import com.espressif.esptouch.android.EspTouchActivityAbs;
import com.espressif.esptouch.android.EspTouchApp;
import com.espressif.esptouch.android.R;
import com.espressif.esptouch.android.databinding.ActivityEsptouchBinding;
import com.espressif.esptouch.android.main.ColorBar;
import com.espressif.esptouch.android.main.ColorPickerDialog;
import com.espressif.esptouch.android.service.MyMqttService;
import com.espressif.iot.esptouch.EsptouchTask;
import com.espressif.iot.esptouch.IEsptouchResult;
import com.espressif.iot.esptouch.IEsptouchTask;
import com.espressif.iot.esptouch.util.ByteUtil;
import com.espressif.iot.esptouch.util.TouchNetUtil;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EspTouchActivity extends EspTouchActivityAbs {
    private static final String TAG = EspTouchActivity.class.getSimpleName();

    private Intent mIntent;
    private static final int REQUEST_PERMISSION = 0x01;

    private EsptouchAsyncTask4 mTask;

    private ActivityEsptouchBinding mBinding;

    private String mSsid;
    private byte[] mSsidBytes;
    private String mBssid;
    private static final String SP_KEY_CLIENT_ID = "clientID";

    private static MqttAndroidClient mqttAndroidClient;
    private MqttConnectOptions mMqttConnectOptions;
    public String HOST = "tcp://47.115.190.212:1883";//服务器地址（协议+地址+端口号）
    public String USERNAME = "";//用户名
    public String PASSWORD = "";//密码
    public String PUBLISH_TOPIC = "";//发布主题
    public String RESPONSE_TOPIC = "";//响应主题
    public String CLIENTID = "";
    public String topicMessage = "";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityEsptouchBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        mBinding.confirmBtn.setOnClickListener(v -> executeEsptouch());
        // mIntent = new Intent(this, MyMqttService.class);
        //  startService(mIntent);
     /*   new ColorPickerDialog(this, "title", new ColorPickerDialog.OnColorChangedListener() {
            @Override
            public void colorChanged(int color) {

            }
        }).show();*/
        mBinding.cancelButton.setOnClickListener(v -> {
            showProgress(false);
            if (mTask != null) {
                mTask.cancelEsptouch();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION};
            requestPermissions(permissions, REQUEST_PERMISSION);
        }

        EspTouchApp.getInstance().observeBroadcast(this, broadcast -> {
            Log.d(TAG, "onCreate: Broadcast=" + broadcast);
            onWifiChanged();
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        mBinding.confirmBtnMQTT.setOnClickListener(v -> {
            //  USERNAME = mBinding.etDeviceId.getText().toString();
            PASSWORD = mBinding.etPassword.getText().toString();
            topicMessage = mBinding.etTypeIdConnect.getText().toString();
            if (TextUtils.isEmpty(USERNAME)) {
                Toast.makeText(EspTouchActivity.this, "Not Connect!", Toast.LENGTH_LONG).show();
                return;
            }
            if (TextUtils.isEmpty(PASSWORD)) {
                Toast.makeText(EspTouchActivity.this, "Please type your device password!", Toast.LENGTH_LONG).show();
                return;
            }
            if (USERNAME.length() < 2) {
                Toast.makeText(EspTouchActivity.this, "Device ID error!", Toast.LENGTH_LONG).show();
                return;
            }

            //    PUBLISH_TOPIC = USERNAME.substring(0, USERNAME.length() - 1);
            //  RESPONSE_TOPIC = PUBLISH_TOPIC;
            //   CLIENTID = USERNAME;
            mBinding.etDeviceId.setEnabled(false);
            mBinding.etPassword.setEnabled(false);
            mBinding.confirmBtnMQTT.setEnabled(false);
            mBinding.etTypeIdConnect.setEnabled(false);
            new Thread() {
                @Override
                public void run() {
                    init();
                }
            }.start();
        });
        mBinding.cancelBtnMQTT.setOnClickListener(v -> {
            mBinding.etDeviceId.setEnabled(true);
            mBinding.etPassword.setEnabled(true);
            mBinding.confirmBtnMQTT.setEnabled(true);
            mBinding.etTypeIdConnect.setEnabled(true);
            //  mBinding.etDeviceId.setText("");
            //  mBinding.etPassword.setText("");
            mBinding.etTypeIdConnect.setText("");
            mBinding.tvMessage.setText("");
            //   mBinding.tvClientIDText.setText("");
            try {
                if (mqttAndroidClient != null) {
                    mqttAndroidClient.disconnect(); //断开连接
                    mMqttConnectOptions = null;
                    mqttAndroidClient = null;
                }
            } catch (MqttException e) {
                e.printStackTrace();
            }
        });

        mBinding.colorBar.setOnColorChangerListener(color -> {
            if (mqttAndroidClient != null && mqttAndroidClient.isConnected()) {
                response("\"RGB\":\"Red:" + Color.red(color) + ",Blue:" + Color.blue(color) + ",Green:" + Color.green(color) + "\"");
            }
        });

        String clientID = getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(SP_KEY_CLIENT_ID, "");
        mBinding.tvClientIDText.setText(clientID);
        if (!TextUtils.isEmpty(clientID)) {
            USERNAME = clientID + "1";
            CLIENTID = USERNAME;
            PUBLISH_TOPIC = clientID;
            RESPONSE_TOPIC = clientID;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onWifiChanged();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.esptouch1_location_permission_title)
                        .setMessage(R.string.esptouch1_location_permission_message)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                        .show();
            }

            return;
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void showProgress(boolean show) {
        if (show) {
            mBinding.content.setVisibility(View.INVISIBLE);
            mBinding.progressView.setVisibility(View.VISIBLE);
        } else {
            mBinding.content.setVisibility(View.VISIBLE);
            mBinding.progressView.setVisibility(View.GONE);
        }
    }

    @Override
    protected String getEspTouchVersion() {
        return getString(R.string.esptouch1_about_version, IEsptouchTask.ESPTOUCH_VERSION);
    }

    private StateResult check() {
        StateResult result = checkPermission();
        if (!result.permissionGranted) {
            return result;
        }
        result = checkLocation();
        result.permissionGranted = true;
        if (result.locationRequirement) {
            return result;
        }
        result = checkWifi();
        result.permissionGranted = true;
        result.locationRequirement = false;
        return result;
    }

    private void onWifiChanged() {
        StateResult stateResult = check();
        mSsid = stateResult.ssid;
        mSsidBytes = stateResult.ssidBytes;
        mBssid = stateResult.bssid;
        CharSequence message = stateResult.message;
        boolean confirmEnable = false;
        if (stateResult.wifiConnected) {
            confirmEnable = true;
            if (stateResult.is5G) {
                message = getString(R.string.esptouch1_wifi_5g_message);
            }
        } else {
            if (mTask != null) {
                mTask.cancelEsptouch();
                mTask = null;
                new AlertDialog.Builder(EspTouchActivity.this)
                        .setMessage(R.string.esptouch1_configure_wifi_change_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        }

        mBinding.apSsidText.setText(mSsid);
        mBinding.apBssidText.setText(mBssid);
        mBinding.messageView.setText(message);
        mBinding.confirmBtn.setEnabled(confirmEnable);
    }

    private void executeEsptouch() {
        byte[] ssid = mSsidBytes == null ? ByteUtil.getBytesByString(this.mSsid)
                : mSsidBytes;
        CharSequence pwdStr = mBinding.apPasswordEdit.getText();
        byte[] password = pwdStr == null ? null : ByteUtil.getBytesByString(pwdStr.toString());
        byte[] bssid = TouchNetUtil.parseBssid2bytes(this.mBssid);
        CharSequence devCountStr = mBinding.deviceCountEdit.getText();
        byte[] deviceCount = devCountStr == null ? new byte[0] : devCountStr.toString().getBytes();
        byte[] broadcast = {(byte) (mBinding.packageModeGroup.getCheckedRadioButtonId() == R.id.packageBroadcast
                ? 1 : 0)};

        if (mTask != null) {
            mTask.cancelEsptouch();
        }
        mTask = new EsptouchAsyncTask4(this);
        mTask.execute(ssid, bssid, password, deviceCount, broadcast);
    }

    @Override
    protected void onDestroy() {
        try {
            if (mqttAndroidClient != null) {
                mqttAndroidClient.disconnect(); //断开连接
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    /**
     * 初始化
     */
    private void init() {
        String serverURI = HOST; //服务器地址（协议+地址+端口号）
        mqttAndroidClient = new MqttAndroidClient(this, serverURI, CLIENTID);
        mqttAndroidClient.setCallback(mqttCallback); //设置监听订阅消息的回调
        mMqttConnectOptions = new MqttConnectOptions();
        mMqttConnectOptions.setCleanSession(true); //设置是否清除缓存
        mMqttConnectOptions.setConnectionTimeout(10); //设置超时时间，单位：秒
        mMqttConnectOptions.setKeepAliveInterval(20); //设置心跳包发送间隔，单位：秒
        mMqttConnectOptions.setUserName(USERNAME); //设置用户名
        mMqttConnectOptions.setPassword(PASSWORD.toCharArray()); //设置密码

        // last will message
        boolean doConnect = true;
        String message = "{\"terminal_uid\":\"" + CLIENTID + "\"}";
        String topic = PUBLISH_TOPIC;
        Integer qos = 2;
        Boolean retained = false;
        if ((!message.equals("")) || (!topic.equals(""))) {
            // 最后的遗嘱
            try {
                mMqttConnectOptions.setWill(topic, message.getBytes(), qos.intValue(), retained.booleanValue());
            } catch (Exception e) {
                Log.i(TAG, "Exception Occured", e);
                doConnect = false;
                iMqttActionListener.onFailure(null, e);
            }
        }
        if (doConnect) {
            doClientConnection();
        }
    }

    //订阅主题的回调
    private MqttCallback mqttCallback = new MqttCallback() {

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            Log.i(TAG, "收到消息： " + new String(message.getPayload()));
            //收到消息，这里弹出Toast表示。如果需要更新UI，可以使用广播或者EventBus进行发送
            //   Toast.makeText(getApplicationContext(), "messageArrived: " + new String(message.getPayload()), Toast.LENGTH_LONG).show();
            runOnUiThread(() -> {
                mBinding.tvMessage.setText(mBinding.tvMessage.getText() + "\n" + new String(message.getPayload()));
            });
            //收到其他客户端的消息后，响应给对方告知消息已到达或者消息有问题等
            // response("message arrived");
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken arg0) {

        }

        @Override
        public void connectionLost(Throwable arg0) {
            Log.i(TAG, "连接断开 ");
            //   doClientConnection();//连接断开，重连
            Toast.makeText(getApplicationContext(), "connection lost", Toast.LENGTH_LONG).show();
            mBinding.etDeviceId.setEnabled(true);
            mBinding.etPassword.setEnabled(true);
            mBinding.confirmBtnMQTT.setEnabled(true);
            mBinding.etTypeIdConnect.setEnabled(true);
            mBinding.colorBar.setVisibility(View.INVISIBLE);
        }
    };
    //MQTT是否连接成功的监听
    private IMqttActionListener iMqttActionListener = new IMqttActionListener() {

        @Override
        public void onSuccess(IMqttToken arg0) {
            Log.i(TAG, "连接成功 ");
            try {
                mqttAndroidClient.subscribe(PUBLISH_TOPIC, 1);//订阅主题，参数：主题、服务质量
                Toast.makeText(getApplicationContext(), "Connect success", Toast.LENGTH_LONG).show();
                runOnUiThread(() -> {
                    mBinding.colorBar.setVisibility(View.VISIBLE);
                });
                response("\"mate\":\"" + topicMessage + "\"");
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onFailure(IMqttToken arg0, Throwable arg1) {
            arg1.printStackTrace();
            Log.i(TAG, "连接失败 ");
            //   doClientConnection();//连接失败，重连（可关闭服务器进行模拟）
            Toast.makeText(getApplicationContext(), "MQTT connect failure！", Toast.LENGTH_LONG).show();
            runOnUiThread(() -> {
                mBinding.etDeviceId.setEnabled(true);
                mBinding.etPassword.setEnabled(true);
                mBinding.confirmBtnMQTT.setEnabled(true);
                mBinding.etTypeIdConnect.setEnabled(true);
            });
        }
    };

    /**
     * 连接MQTT服务器
     */
    private void doClientConnection() {
        if (!mqttAndroidClient.isConnected() && isConnectIsNomarl()) {
            try {
                mqttAndroidClient.connect(mMqttConnectOptions, null, iMqttActionListener);
            } catch (MqttException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(getApplicationContext(), "Network error!", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 判断网络是否连接
     */
    private boolean isConnectIsNomarl() {
        ConnectivityManager connectivityManager = (ConnectivityManager) this.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        if (info != null && info.isAvailable()) {
            String name = info.getTypeName();
            Log.i(TAG, "当前网络名称：" + name);
            return true;
        } else {
            /*      Log.i(TAG, "没有可用网络");
             *//*没有可用网络的时候，延迟3秒再尝试重连*//*
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    doClientConnection();
                }
            }, 3000);*/
            return false;
        }
    }


    /**
     * 发布 （模拟其他客户端发布消息）
     *
     * @param message 消息
     */
    public void publish(String message) {
        String topic = PUBLISH_TOPIC;
        Integer qos = 2;
        Boolean retained = false;
        try {
            //参数分别为：主题、消息的字节数组、服务质量、是否在服务器保留断开连接后的最后一条消息
            mqttAndroidClient.publish(topic, message.getBytes(), qos.intValue(), retained.booleanValue());
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /**
     * 响应 （收到其他客户端的消息后，响应给对方告知消息已到达或者消息有问题等）
     *
     * @param message 消息
     */
    public void response(String message) {
        String topic = RESPONSE_TOPIC;
        Integer qos = 1;
        Boolean retained = false;
        try {
            //参数分别为：主题、消息的字节数组、服务质量、是否在服务器保留断开连接后的最后一条消息
            mqttAndroidClient.publish(topic, message.getBytes(), qos.intValue(), retained.booleanValue());
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private static class EsptouchAsyncTask4 extends AsyncTask<byte[], IEsptouchResult, List<IEsptouchResult>> {
        private final WeakReference<EspTouchActivity> mActivity;

        private final Object mLock = new Object();
        private AlertDialog mResultDialog;
        private IEsptouchTask mEsptouchTask;

        EsptouchAsyncTask4(EspTouchActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        void cancelEsptouch() {
            cancel(true);
            EspTouchActivity activity = mActivity.get();
            if (activity != null) {
                activity.showProgress(false);
            }
            if (mResultDialog != null) {
                mResultDialog.dismiss();
            }
            if (mEsptouchTask != null) {
                mEsptouchTask.interrupt();
            }
        }

        @Override
        protected void onPreExecute() {
            EspTouchActivity activity = mActivity.get();
            activity.mBinding.testResult.setText("");
            activity.showProgress(true);
        }

        @Override
        protected void onProgressUpdate(IEsptouchResult... values) {
            EspTouchActivity activity = mActivity.get();
            if (activity != null) {
                IEsptouchResult result = values[0];
                Log.i(TAG, "EspTouchResult: " + result);
                String text = result.getBssid() + " is connected to the wifi";
                Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();

                activity.mBinding.testResult.append(String.format(
                        Locale.ENGLISH,
                        "%s,%s\n",
                        result.getInetAddress().getHostAddress(),
                        result.getBssid()
                ));
            }
        }

        @Override
        protected List<IEsptouchResult> doInBackground(byte[]... params) {
            EspTouchActivity activity = mActivity.get();
            int taskResultCount;
            synchronized (mLock) {
                byte[] apSsid = params[0];
                byte[] apBssid = params[1];
                byte[] apPassword = params[2];
                byte[] deviceCountData = params[3];
                byte[] broadcastData = params[4];
                taskResultCount = deviceCountData.length == 0 ? -1 : Integer.parseInt(new String(deviceCountData));
                Context context = activity.getApplicationContext();
                mEsptouchTask = new EsptouchTask(apSsid, apBssid, apPassword, context);
                mEsptouchTask.setPackageBroadcast(broadcastData[0] == 1);
                mEsptouchTask.setEsptouchListener(this::publishProgress);
            }
            return mEsptouchTask.executeForResults(taskResultCount);
        }

        @Override
        protected void onPostExecute(List<IEsptouchResult> result) {
            EspTouchActivity activity = mActivity.get();
            activity.mTask = null;
            activity.showProgress(false);
            if (result == null) {
                mResultDialog = new AlertDialog.Builder(activity)
                        .setMessage(R.string.esptouch1_configure_result_failed_port)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                mResultDialog.setCanceledOnTouchOutside(false);
                return;
            }

            // check whether the task is cancelled and no results received
            IEsptouchResult firstResult = result.get(0);
            if (firstResult.isCancelled()) {
                return;
            }
            // the task received some results including cancelled while
            // executing before receiving enough results

            if (!firstResult.isSuc()) {
                mResultDialog = new AlertDialog.Builder(activity)
                        .setMessage(R.string.esptouch1_configure_result_failed)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                mResultDialog.setCanceledOnTouchOutside(false);
                return;
            }

            ArrayList<CharSequence> resultMsgList = new ArrayList<>(result.size());
            for (IEsptouchResult touchResult : result) {
                String message = activity.getString(R.string.esptouch1_configure_result_success_item,
                        touchResult.getBssid(), touchResult.getInetAddress().getHostAddress());
                resultMsgList.add(message);
            }
            activity.getSharedPreferences(activity.getPackageName(), MODE_PRIVATE).edit().putString(SP_KEY_CLIENT_ID, firstResult.getBssid()).apply();
            activity.USERNAME = firstResult.getBssid() + "1";
            activity.CLIENTID = activity.USERNAME;
            activity.PUBLISH_TOPIC = firstResult.getBssid();
            activity.RESPONSE_TOPIC = firstResult.getBssid();
            activity.mBinding.tvClientIDText.setText(firstResult.getBssid());
            CharSequence[] items = new CharSequence[resultMsgList.size()];
            mResultDialog = new AlertDialog.Builder(activity)
                    .setTitle(R.string.esptouch1_configure_result_success)
                    .setItems(resultMsgList.toArray(items), null)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            mResultDialog.setCanceledOnTouchOutside(false);
        }
    }
}
