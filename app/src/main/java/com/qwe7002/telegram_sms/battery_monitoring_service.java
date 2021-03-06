package com.qwe7002.telegram_sms;

import android.Manifest;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.IBinder;
import android.support.annotation.NonNull;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.content.Context.BATTERY_SERVICE;
import static android.support.v4.content.PermissionChecker.checkSelfPermission;

public class battery_monitoring_service extends Service {
    battery_receiver receiver = null;
    static String bot_token;
    static String chat_id;
    static Boolean fallback;
    static String trusted_phone_number;
    Context context;
    SharedPreferences sharedPreferences;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = public_func.get_notification_obj(context, getString(R.string.Battery_monitoring));
        startForeground(1, notification);
        if (!sharedPreferences.getBoolean("initialized", false)) {
            public_func.write_log(context, "Battery Monitoring:Uninitialized");
            stopSelf();
        }
        if (!sharedPreferences.getBoolean("battery_monitoring_switch", false)) {
            stopSelf();
        }
        chat_id = sharedPreferences.getString("chat_id", "");
        bot_token = sharedPreferences.getString("bot_token", "");
        fallback = sharedPreferences.getBoolean("fallback_sms", false);
        trusted_phone_number = sharedPreferences.getString("trusted_phone_number", null);
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        receiver = new battery_receiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(receiver, filter);

    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        unregisterReceiver(receiver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}

class battery_receiver extends BroadcastReceiver {
    OkHttpClient okhttp_client = public_func.get_okhttp_obj();

    @Override
    public void onReceive(final Context context, final Intent intent) {
        String request_uri = public_func.get_url(battery_monitoring_service.bot_token, "sendMessage");
        final request_json request_body = new request_json();
        request_body.chat_id = battery_monitoring_service.chat_id;
        StringBuilder prebody = new StringBuilder(context.getString(R.string.system_message_head) + "\n");
        final String action = intent.getAction();
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
        switch (Objects.requireNonNull(action)) {
            case Intent.ACTION_BATTERY_OKAY:
                prebody = prebody.append(context.getString(R.string.low_battery_status_end));
                break;
            case Intent.ACTION_BATTERY_LOW:
                prebody = prebody.append(context.getString(R.string.battery_low));
                break;
            case Intent.ACTION_POWER_CONNECTED:
                prebody = prebody.append(context.getString(R.string.ac_connect));
                break;
            case Intent.ACTION_POWER_DISCONNECTED:
                prebody = prebody.append(context.getString(R.string.ac_disconnect));
                break;
        }
        request_body.text = prebody.append("\n").append(context.getString(R.string.current_battery_level)).append(batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)).append("%").toString();
        Gson gson = new Gson();
        String request_body_raw = gson.toJson(request_body);
        RequestBody body = RequestBody.create(public_func.JSON, request_body_raw);
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                String error_message = "Send battery info error:" + e.getMessage();
                public_func.write_log(context, error_message);
                if (action.equals(Intent.ACTION_BATTERY_LOW)) {
                    if (checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                        if (battery_monitoring_service.fallback) {
                            String msg_send_to = battery_monitoring_service.trusted_phone_number;
                            String msg_send_content = request_body.text;
                            if (msg_send_to != null) {
                                public_func.send_sms(context, msg_send_to, msg_send_content, -1);
                            }
                        }
                    }
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.code() != 200) {
                    assert response.body() != null;
                    String error_message = "Send battery info error:" + response.body().string();
                    public_func.write_log(context, error_message);
                }
            }
        });


    }
}
