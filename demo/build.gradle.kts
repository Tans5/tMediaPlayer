
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    id("com.google.devtools.ksp")
    id("kotlin-kapt")
}

android {
    namespace = "com.tans.tmediaplayer.demo"
    compileSdk = properties["ANDROID_COMPILE_SDK"].toString().toInt()

    defaultConfig {
        applicationId = "com.tans.tmediaplayer.demo"
        minSdk = properties["ANDROID_MIN_SDK"].toString().toInt()
        targetSdk = properties["ANDROID_TARGET_SDK"].toString().toInt()
        versionCode = 1
        versionName = properties["VERSION_NAME"].toString()
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        viewBinding {
            enable = true
        }
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.core.jvm)
    implementation(libs.coroutines.android)

    implementation(libs.tuiutils)

    implementation(libs.glide)
    kapt(libs.glide.codegen)

    implementation(project(":tmediaplayer"))

    // implementation("io.github.tans5:tmediaplayer:1.0.0-alpha02")

}