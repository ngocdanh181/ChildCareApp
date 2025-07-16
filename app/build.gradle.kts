plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    //Add the Google services Gradle plugin
    id("com.google.gms.google-services")
    id("androidx.navigation.safeargs.kotlin")
    id("kotlin-kapt")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.childlocate"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.childlocate"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        dataBinding = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    packagingOptions {
        exclude ("META-INF/DEPENDENCIES")
    }

    flavorDimensions += "version"
    productFlavors {
        create("free") {
            dimension = "version"
            applicationIdSuffix = ".free"
        }
        create("parent") {
            dimension = "version"
            applicationIdSuffix = ".parent"
        }
    }
    secrets {
        // Đây là file mặc định chứa các API key nhạy cảm của bạn
        // PHẢI được thêm vào .gitignore
        propertiesFileName = "secrets.properties"

        // Đây là file tùy chọn cho các giá trị mặc định hoặc không nhạy cảm
        // CÓ THỂ được commit lên Git
        defaultPropertiesFileName = "local.defaults.properties"

        // Các dòng comment hướng dẫn thêm API key vào secrets.properties cũng có thể nằm ở đây,
        // hoặc bạn có thể xóa chúng đi sau khi đã hiểu cách cấu hình
    }


}

dependencies {

    // Room dependencies
    implementation ("androidx.room:room-runtime:2.6.1")
    implementation("androidx.preference:preference-ktx:1.2.1")
    kapt ("androidx.room:room-compiler:2.6.1")
    implementation ("androidx.room:room-ktx:2.6.1")

    //vpn dns filter library
    implementation ("dnsjava:dnsjava:3.5.1")


    //cameraX
    implementation ("androidx.camera:camera-core:1.4.0")
    implementation ("androidx.camera:camera-camera2: 1.4.0")
    implementation ("androidx.camera:camera-lifecycle:1.4.0")
    implementation ("androidx.camera:camera-video:1.4.0")

    implementation ("androidx.camera:camera-view:1.4.0")
    implementation ("androidx.camera:camera-extensions:1.4.0")

    //add gson library
    implementation ("com.google.code.gson:gson:2.10.1")

    // Lifecycle libraries
    implementation ("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
    implementation ("androidx.lifecycle:lifecycle-livedata-ktx:2.8.0")

    implementation(platform("com.google.firebase:firebase-bom:32.8.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation ("com.google.firebase:firebase-database-ktx:20.3.1")
    implementation ("com.google.firebase:firebase-auth-ktx:22.3.1")
    implementation ("com.google.firebase:firebase-core:21.1.1")

    implementation ("com.google.firebase:firebase-messaging:23.4.1")
    implementation ("com.google.firebase:firebase-messaging-directboot:23.4.1")
    implementation("com.google.firebase:firebase-functions-ktx:21.1.1")


    implementation ("androidx.recyclerview:recyclerview:1.3.2")


    implementation ("androidx.work:work-runtime-ktx:2.9.0")
    implementation ("androidx.work:work-multiprocess:2.7.0")

    implementation ("com.google.auth:google-auth-library-oauth2-http:1.2.1")

    implementation ("com.google.android.gms:play-services-maps:18.2.0")
    implementation ("com.google.android.libraries.places:places:3.3.0")


    //navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    //glide
    implementation ("com.github.bumptech.glide:glide:4.12.0")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.12.0")

    implementation ("com.google.code.gson:gson:2.8.9")

    //chart
    implementation ("com.github.PhilJay:MPAndroidChart:v3.0.3")



    //retrofit
    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")

    implementation ("com.google.api-client:google-api-client:1.32.2")
    implementation ("com.squareup.okhttp3:logging-interceptor:4.9.0")

    //circle library
    implementation("de.hdodenhof:circleimageview:3.1.0") // Sử dụng phiên bản mới nhất


    implementation ("com.google.android.gms:play-services-location:21.2.0")
    implementation ("com.google.android.gms:play-services-places:17.0.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.annotation:annotation:1.8.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.fragment:fragment-ktx:1.5.6")
    implementation("androidx.activity:activity:1.8.0")
    implementation("com.google.firebase:firebase-storage-ktx:21.0.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

