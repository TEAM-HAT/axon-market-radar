plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "inc.axon.radar"
    compileSdk = 34

    defaultConfig {
        applicationId = "inc.axon.radar"
        minSdk = 26
        targetSdk = 34
        versionCode = 11
        versionName = "3.7"
    }

    signingConfigs {
        create("release") {
            // The signing key is kept outside this public repository.
            storeFile = System.getenv("RADAR_KEYSTORE")?.let { file(it) }
            storePassword = System.getenv("RADAR_KEYSTORE_PASSWORD")
            keyAlias = "radar"
            keyPassword = System.getenv("RADAR_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

    testOptions { unitTests { isIncludeAndroidResources = true } }
}

tasks.withType<Test>().configureEach {
    systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
    maxHeapSize = "2g"
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.work:work-testing:2.9.1")
}
