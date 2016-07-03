package com.amti.vela.bluetoothlegatt;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.v4.content.LocalBroadcastManager;

public class NotificationService extends NotificationListenerService {

    Context context;
    public static boolean notificationsBound = false;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Intent msgrcv = new Intent("Msg");

        LocalBroadcastManager.getInstance(context).sendBroadcast(msgrcv);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {

    }

    @Override
    public IBinder onBind(Intent intent) {
        notificationsBound = true;
        return super.onBind(intent);
    }
}