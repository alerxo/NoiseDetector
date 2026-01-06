package com.example.noisedetectorclient;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.work.BackoffPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements MyEventListener {
    private Handler handler;
    private Runnable runnable;
    private final int UPDATE_MS_SLEEP = 10000;

    public static final String CHANNEL_ID = "NoiseDetectorChannelID";
    private final int PERMISSION_REQUEST_CODE = 101;

    private MqttHandler mqttHandler;

    private TextView messageTextView = null;
    private TextView sensorTextiView = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        Start();
        Setup();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mqttHandler.Destroy();
        handler.removeCallbacks(runnable);
    }

    private void Setup()
    {
        handler = new Handler(Looper.getMainLooper());
        runnable = new Runnable() {
            @Override
            public void run() {
                Update();
                handler.postDelayed(this, UPDATE_MS_SLEEP);
            }
        };

        handler.post(runnable);
    }

    private void Start()
    {
        messageTextView = findViewById(R.id.message_text_view);
        sensorTextiView = findViewById(R.id.sensor_text_view);
        TrySubmitNotificationRequest();

        mqttHandler = new MqttHandler(this);
        mqttHandler.Publish("test", "Hello world!");
    }

    private void TrySubmitNotificationRequest()
    {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE);
            return;
        }

        WorkRequest workRequest = new OneTimeWorkRequest.Builder(NotificationWorker.class)
                .setBackoffCriteria(BackoffPolicy.LINEAR, PeriodicWorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(this).enqueue(workRequest);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode != PERMISSION_REQUEST_CODE) {
            return;
        }

        TrySubmitNotificationRequest();
    }

    private void Update() { }

    @Override
    public void onMessage(String message) {
        messageTextView.setText(message);
    }

    @Override
    public void onSensor(int db) {
        if(IsSafe(db)) {
            sensorTextiView.setText("Safe noise levels (" + db + " db)!");
        } else {
            sensorTextiView.setText("DANGEROUS NOISE LEVELS (" + db + " db)!");
        }
    }

    public static boolean IsSafe(int db) {
        return db < 25;
    }
}