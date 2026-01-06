package com.example.noisedetectorclient;

public interface MyEventListener {
    void onMessage(String message);
    void onSensor(int db);
}
