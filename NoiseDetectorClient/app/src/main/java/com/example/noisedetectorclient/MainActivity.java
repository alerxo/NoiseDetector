package com.example.noisedetectorclient;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
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
import android.widget.Button;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

public class MainActivity extends AppCompatActivity implements MyEventListener {
    private static final String TAG = "NoiseDetectorMain";

    private Handler handler;
    private Runnable runnable;
    private final int UPDATE_MS_SLEEP = 10000;

    public static final String CHANNEL_ID = "NoiseDetectorChannelID";
    private final int PERMISSION_REQUEST_CODE = 101;

    private MqttHandler mqttHandler;

    private TextView messageTextView = null;
    private TextView sensorTextiView = null;
    private TextView aiSummaryTextView = null;
    private Button analyzeButton = null;

    private ArrayList<Integer> sensorReadings = new ArrayList<>();
    private static final int MAX_READINGS = 20;

    private OkHttpClient httpClient = new OkHttpClient();


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
        aiSummaryTextView = findViewById(R.id.ai_summary_text_view);
        analyzeButton = findViewById(R.id.analyze_button);

        //  button click listener config
        analyzeButton.setOnClickListener(v -> callClaudeAPI());

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
        // få Ui och data upd att hända på Ui tråd
        runOnUiThread(() -> {
            sensorReadings.add(db);

            // beta rolling window
            if (sensorReadings.size() > MAX_READINGS) {
                sensorReadings.remove(0);
            }

            if(IsSafe(db)) {
                sensorTextiView.setText("Safe noise levels (" + db + " db)!");
            } else {
                sensorTextiView.setText("DANGEROUS NOISE LEVELS (" + db + " db)!");
            }
        });
    }

    public static boolean IsSafe(int db) {
        return db < 25;
    }

    private void callClaudeAPI() {
        if (sensorReadings.isEmpty()) {
            Log.w(TAG, "No sensor data available");
            runOnUiThread(() -> aiSummaryTextView.setText("No sensor data available yet. Please wait for data collection."));
            return;
        }

        // stäng av knappen när req görs
        analyzeButton.setEnabled(false);
        runOnUiThread(() -> aiSummaryTextView.setText("Analyzing data..."));

        try {
            // bygga prompten och lägga till sensordata
            String prompt = "Summarize these noise levels in dB: " + sensorReadings.toString() +
                          ". Provide a brief analysis indicating if the levels are generally safe (safe is below 25 dB). Keep the response concise (2-3 sentences).";

            // skapa JSON request body till Claude
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "claude-haiku-4-5-20251001");
            requestBody.put("max_tokens", 150);

            JSONArray messages = new JSONArray();
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", prompt);
            messages.put(message);
            requestBody.put("messages", messages);

            // våran HTTP request till claude
            RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", BuildConfig.CLAUDE_API_KEY)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(body)
                .build();

            // async req
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "API request failed", e);
                    runOnUiThread(() -> {
                        aiSummaryTextView.setText("Error: " + e.getMessage());
                        analyzeButton.setEnabled(true);
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {

                        if (!response.isSuccessful()) {
                            String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                            Log.e(TAG, "API Error: " + response.code() + " - " + errorBody);
                            runOnUiThread(() -> {
                                aiSummaryTextView.setText("API Error: " + response.code() + " - " + errorBody);
                                analyzeButton.setEnabled(true);
                            });
                            return;
                        }

                        String responseBody = response.body().string();

                        JSONObject jsonResponse = new JSONObject(responseBody);
                        JSONArray content = jsonResponse.getJSONArray("content");
                        String aiResponse = content.getJSONObject(0).getString("text");


                        runOnUiThread(() -> {
                            aiSummaryTextView.setText("AI Summary: " + aiResponse);
                            analyzeButton.setEnabled(true);
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            aiSummaryTextView.setText("Error parsing response: " + e.getMessage());
                            analyzeButton.setEnabled(true);
                        });
                    }
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                aiSummaryTextView.setText("Error creating request: " + e.getMessage());
                analyzeButton.setEnabled(true);
            });
        }
    }
}