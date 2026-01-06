import java.util.Properties

val env = Properties()
val envFile = rootProject.file(".env")

if (envFile.exists()) {
    envFile.inputStream().use {
        env.load(it)
    }
}

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.noisedetectorclient"

    buildFeatures {
        buildConfig = true
    }

    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.noisedetectorclient"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "MQTT_USER", "\"${env["USER"]}\"")
        buildConfigField("String", "MQTT_PASS", "\"${env["PASS"]}\"")
        buildConfigField("String", "MQTT_URL", "\"${env["URL"]}\"")
        buildConfigField("int", "MQTT_PORT", env["PORT"].toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA"
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(files("libs/ssh.jar"))
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.runtime)
    implementation(libs.navigation.ui)
    implementation(libs.work.runtime)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("com.hivemq:hivemq-mqtt-client:1.3.9")
}