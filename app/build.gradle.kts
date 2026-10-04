import java.util.Base64

// ---------------------------------------------------------------------------
// Keystore bootstrap.
//
// CI may publish `debug.keystore.base64` as a secret; if so, materialise the
// keystore once. Neither file is ever committed (see .gitignore).
// ---------------------------------------------------------------------------
val base64File = file("${rootDir}/debug.keystore.base64")
val keystoreFile = file("${rootDir}/debug.keystore")
if (base64File.exists() && !keystoreFile.exists()) {
    try {
        val base64Text = base64File.readText().replace("\\s".toRegex(), "")
        keystoreFile.writeBytes(Base64.getDecoder().decode(base64Text))
    } catch (e: Exception) {
        println("Keystore decode error: ${e.message}")
    }
}

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.roborazzi)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.smalldeeds.kfkjqo"
    minSdk = 24
    targetSdk = 36
    versionCode = 7
    versionName = "7.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // -------------------------------------------------------------------------
  // Signing.
  //
  //  release : uses the real upload keystore when KEYSTORE_PATH + the two
  //            password env vars are present; falls back to a decoded
  //            debug.keystore (loudly warned) for legacy CI; otherwise leaves
  //            the artefact UNSIGNED so Play App Signing can sign it, instead
  //            of failing the build outright on a clean checkout.
  //  debug   : AGP's built-in debug config, which auto-creates
  //            ~/.android/debug.keystore. No committed keystore required.
  // -------------------------------------------------------------------------
  val uploadKeystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
  val uploadKeystore = file(uploadKeystorePath)
  val storePassword = System.getenv("STORE_PASSWORD")
  val keyPassword = System.getenv("KEY_PASSWORD")
  val haveUploadCredentials = uploadKeystore.exists() && storePassword != null && keyPassword != null
  val haveFallbackKeystore = keystoreFile.exists()

  signingConfigs {
    if (haveUploadCredentials) {
      create("release") {
        storeFile = uploadKeystore
        storePassword = storePassword
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = keyPassword
      }
    } else if (haveFallbackKeystore) {
      logger.warn(
        "No upload keystore/credentials found; signing 'release' with the debug " +
          "keystore at $uploadKeystorePath. This is for CI smoke builds only and " +
          "will be rejected by Google Play."
      )
      create("release") {
        storeFile = keystoreFile
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Null when no keystore is configured => unsigned AAB, sign later.
      signingConfig = signingConfigs.findByName("release")
      if (signingConfig == null) {
        logger.lifecycle(
          "Release build will be UNSIGNED: provide KEYSTORE_PATH, STORE_PASSWORD, " +
            "KEY_ALIAS and KEY_PASSWORD (or drop my-upload-key.jks next to " +
            "settings.gradle.kts) to produce a signed artefact."
        )
      }
    }
    debug {
      // Intentionally no signingConfig: AGP applies its own debug signing and
      // generates ~/.android/debug.keystore on demand.
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      // Let CI opt into Roborazzi screenshot verification / comparison without
      // the default record mode overwriting the committed golden images:
      //   ./gradlew test -Droborazzi.test.verify=true
      all {
        systemProperty(
          "roborazzi.test.verify",
          providers.systemProperty("roborazzi.test.verify").getOrElse("false"),
        )
        systemProperty(
          "roborazzi.test.compare",
          providers.systemProperty("roborazzi.test.compare").getOrElse("false"),
        )
      }
    }
  }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  // implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.work.runtime.ktx)
  // implementation(libs.androidx.navigation.compose)
  // implementation(libs.androidx.room.ktx)
  // implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.androidx.work.testing)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  // "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
