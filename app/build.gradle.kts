
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.inchekyandroid"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.inchekyandroid"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"http://10.0.2.2:8080/api/\""
            )

            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }

        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false

            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://incheky-backend-ckg6haa0bwe3bkdv.mexicocentral-01.azurewebsites.net/api/\""
            )

            manifestPlaceholders["usesCleartextTraffic"] = "false"

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.navigation.fragment)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.glide)
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
