import time
import math
import os
from dotenv import load_dotenv
import paho.mqtt.client as paho
from paho import mqtt
import board
import busio
import adafruit_ads1x15.ads1115 as ADS
from adafruit_ads1x15.analog_in import AnalogIn

connected = False

def on_connect(client, userdata, flags, rc, properties=None):
    global connected
    if rc == 0:
        print("Connected to MQTT broker")
        connected = True
    else:
        print(f"Failed to connect, return code {rc}")

def on_publish(client, userdata, mid, properties=None):
    print(f"Message published (mid={mid})")

load_dotenv()

username = os.getenv("USER")
password = os.getenv("PASS")
url = os.getenv("URL")
port = int(os.getenv("PORT"))

# ADC Setup
i2c = busio.I2C(board.SCL, board.SDA)
ads = ADS.ADS1115(i2c, gain=1)
chan = AnalogIn(ads, 0)

client = paho.Client(
    client_id="",
    userdata=None,
    protocol=paho.MQTTv5
)

client.on_connect = on_connect
client.on_publish = on_publish

client.tls_set(tls_version=mqtt.client.ssl.PROTOCOL_TLS)
client.username_pw_set(username, password)
client.connect(url, port)
client.loop_start()

while not connected:
    time.sleep(0.1)

topic = "group29/sound"
qos = 1

samples = []
try:
    while True:
        samples.append(chan.value)
        if len(samples) > 2:
            samples.pop(0)

        amplitude = max(samples) - min(samples)

        # Optional: Convert to decibels
        if amplitude > 0:
            decibels = 20 * math.log10(amplitude / 32767.0) + 94
        else:
            decibels = 0

        # Publish amplitude
        client.publish(topic=topic, payload=int(decibels), qos=qos)
        print(f"Amplitude: {amplitude}  Decibels: {int(decibels)}  dB")

        time.sleep(1)

except KeyboardInterrupt:
    print("Stopping...")
    client.loop_stop()
    client.disconnect()