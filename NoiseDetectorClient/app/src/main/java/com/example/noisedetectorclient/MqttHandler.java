package com.example.noisedetectorclient;

import static com.hivemq.client.mqtt.MqttGlobalPublishFilter.ALL;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.example.noisedetectorclient.BuildConfig;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;

import java.nio.charset.StandardCharsets;
import java.util.Random;

public class MqttHandler {
    private final MyEventListener eventListener;
    private Mqtt5BlockingClient client;
    private final Random random = new Random();

    public MqttHandler(MyEventListener eventListener)
    {
        this.eventListener = eventListener;
        Connect();
    }

    private void Connect() {
        String user = BuildConfig.MQTT_USER;
        String pass = BuildConfig.MQTT_PASS;
        String url = BuildConfig.MQTT_URL;
        int port = BuildConfig.MQTT_PORT;
        final String identifier = "android" + random.nextInt(0, 1000000);

        OnMessage("log", "Attempting to connect...");

        try{
            client = MqttClient.builder()
                    .useMqttVersion5()
                    .serverHost(url)
                    .identifier(identifier)
                    .serverPort(port)
                    .sslWithDefaultConfig()
                    .buildBlocking();

            client.connectWith()
                    .simpleAuth()
                    .username(user)
                    .password(UTF_8.encode(pass))
                    .applySimpleAuth()
                    .send();

            client.subscribeWith().topicFilter("#").qos(MqttQos.AT_LEAST_ONCE).send();

            client.toAsync().publishes(ALL, publish -> {
                try {
                    if(publish.getPayload().isPresent())
                    {
                        String topic = publish.getTopic().toString();
                        String message = String.valueOf(StandardCharsets.UTF_8.decode(publish.getPayload().get()));
                        OnMessage(topic, message);
                    }
                }catch (Exception e){
                    OnMessage("error", e.toString());
                }
            });

            OnMessage("log", "Connected successfully!");
        }catch (Exception e){
            OnMessage("error", e.toString());
        }
    }

    public void Publish(String topic, String message) {
        try {
            client.publishWith()
                    .topic(topic)
                    .payload(UTF_8.encode(message))
                    .send();
        } catch (Exception e){
            OnMessage("error", e.toString());
        }
    }

    public void Destroy() {
        try {
            client.disconnect();
        }catch (Exception e) {
            OnMessage("error", e.toString());
        }
    }

    private void OnMessage(String topic, String message)
    {
        switch (topic) {
            case "sensor":
                String cleaned = message.replaceAll("[^0-9]", "");

                if(!cleaned.isEmpty()) {
                    int value = Integer.parseInt(cleaned);
                    eventListener.onSensor(value);
                }
                break;
            case "test":
                break;
            default:
                eventListener.onMessage(message);

        }
    }
}