import time
import random
import os
from dotenv import load_dotenv
import paho.mqtt.client as paho
from paho import mqtt

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

def get_db():
    #Här kan du lägga sensor data koden
    return f"{random.randint(0, 99)} db"

load_dotenv()

username = os.getenv("USER")
password = os.getenv("PASS")
url = os.getenv("URL")
port = int(os.getenv("PORT"))

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

topic = "sensor"
qos = 1

while True:
    message = get_db()
    client.publish(topic=topic, payload=message, qos=qos)
    time.sleep(10)