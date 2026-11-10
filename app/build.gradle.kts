plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.huijui.catgirl"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.huijui.catgirl"
        minSdk = 26
        targetSdk = 35
        versionCode = 201
        versionName = "v2.0pro"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "META-INF/*" }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
