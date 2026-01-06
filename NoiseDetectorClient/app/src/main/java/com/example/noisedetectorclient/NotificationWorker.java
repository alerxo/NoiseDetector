package com.example.noisedetectorclient;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import androidx.work.impl.utils.futures.SettableFuture;

import com.google.common.util.concurrent.ListenableFuture;

public class NotificationWorker extends ListenableWorker implements MyEventListener {
    private SettableFuture<Result> future;
    private MqttHandler mqttHandler;

    public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @Override
    public @org.jspecify.annotations.NonNull ListenableFuture<Result> startWork() {
        future = SettableFuture.create();
        mqttHandler = new MqttHandler(this);
        return future;
    }

    @Override
    public void onMessage(String message) { }

    @Override
    public void onSensor(int db) {
        if(MainActivity.IsSafe(db)) return;

        final String NOTIFICATION_TITLE = "Dangerous noise levels near you!";
        final String NOTIFICATION_TEXT = String.format("NoiseDetector has detected dangerously high noise levels (%s db) near you! Take cover immediately", db);
        CreateNotification(NOTIFICATION_TITLE, NOTIFICATION_TEXT);
        future.set(Result.success());
    }

    private void CreateNotification(String title, String text)
    {
        final int NOTIFICATION_ID = 12345;
        final String CHANNEL_NAME = "NoiseDetector";
        final String CHANNEL_DESCRIPTION = "NoiseDetector notification channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(MainActivity.CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(CHANNEL_DESCRIPTION);
            NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(getApplicationContext(), MainActivity.CHANNEL_ID)
                .setSmallIcon(R.drawable.decibel)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat.from(getApplicationContext()).notify(NOTIFICATION_ID, builder.build());
    }

    @Override
    public void onStopped() {
        mqttHandler.Destroy();
    }
}
