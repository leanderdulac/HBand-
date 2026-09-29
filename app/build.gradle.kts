import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

// Explicit test-only build. Never use this package as a pilot update.
val storageLab = providers.gradleProperty("storageLab").orNull == "true"
val bleLab = providers.gradleProperty("bleLab").orNull == "true"
require(!(storageLab && bleLab)) { "Choose either storageLab or bleLab, never both." }
if (bleLab) {
  // Refuse private configuration, rather than copying credentials into a lab APK.
  val labConfig = Properties().apply {
    val configFile = rootProject.file(if (rootProject.file(".env").exists()) ".env" else ".env.example")
    configFile.inputStream().use { load(it) }
  }
  val allowedKeys = mapOf(
    "HEALTHTECH_INGEST_API_KEY" to setOf("", "YOUR_HEALTHTECH_API_KEY_HERE"),
    "GEMINI_API_KEY" to setOf("", "YOUR_GEMINI_API_KEY", "YOUR_GEMINI_API_KEY_HERE"),
  )
  require(allowedKeys.all { (key, placeholders) -> labConfig.getProperty(key, "") in placeholders }) {
    "BLE lab requires placeholder keys only. Preserve private configuration and use an isolated checkout."
  }
  // Secrets Plugin also reads ".properties" when the variant has no flavor.
  // Refuse every root overlay, including unnamed and future variant overlays.
  val buildInfrastructure = setOf("gradle.properties", "local.properties")
  require(rootProject.projectDir.listFiles().orEmpty().none {
    it.isFile && it.name.endsWith(".properties", ignoreCase = true) && it.name !in buildInfrastructure
  }) {
    "BLE lab forbids root secret overlays. Use an isolated checkout."
  }
  require(fileTree(projectDir) { include("**/google-services.json"); exclude("build/**") }.isEmpty) {
    "BLE lab must not include google-services.json."
  }
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.hbandhealthtech.pxq97m"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    buildConfigField("boolean", "BLE_LAB", "false")

    // sqlcipher-android AAR ships libsqlcipher.so for these ABIs; arm64-v8a is
    // required on current VE30 companion phones. Do not drop it from the APK.
    ndk {
      abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
    }
  }

  packaging {
    jniLibs {
      keepDebugSymbols += setOf("**/libsqlcipher.so")
    }
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
      if (storageLab) applicationIdSuffix = ".storagelab"
      if (bleLab) {
        applicationIdSuffix = ".blelab"
        buildConfigField("boolean", "BLE_LAB", "true")
      }
    }
  }
  if (storageLab) {
    sourceSets.getByName("debug").manifest.srcFile("src/storageLab/AndroidManifest.xml")
  }
  if (bleLab) {
    sourceSets.getByName("debug").apply {
      manifest.srcFile("src/bleLab/AndroidManifest.xml")
      res.directories.add("src/bleLab/res")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

val healthtechIngestKeyPlaceholder = "YOUR_HEALTHTECH_API_KEY_HERE"
fun readHealthtechIngestKey(): String {
  val envFile = rootProject.file(".env")
  val exampleFile = rootProject.file(".env.example")
  val file = if (envFile.exists()) envFile else exampleFile
  if (!file.exists()) return ""
  return file.readLines()
    .firstOrNull { it.trim().startsWith("HEALTHTECH_INGEST_API_KEY=") }
    ?.substringAfter("=")
    ?.trim()
    .orEmpty()
}

val resolvedIngestKey = readHealthtechIngestKey()
val ingestKeyIsPlaceholder =
  resolvedIngestKey.isEmpty() || resolvedIngestKey.equals(healthtechIngestKeyPlaceholder, ignoreCase = true)
if (ingestKeyIsPlaceholder) {
  logger.warn(
    "HEALTHTECH_INGEST_API_KEY is empty or placeholder. Debug builds refuse to call ingest; " +
      "release assemble/bundle will fail. Set the key in .env (gitignored).",
  )
}

gradle.taskGraph.whenReady {
  if (bleLab && allTasks.any { it.name.contains("Release", ignoreCase = true) }) {
    throw GradleException("BLE lab is debug-only; release tasks are forbidden with -PbleLab=true.")
  }
  val runningRelease = allTasks.any { task ->
    val name = task.name
    name.contains("Release", ignoreCase = true) &&
      (name.startsWith("assemble") || name.startsWith("bundle") || name.contains("assembleRelease") || name.contains("bundleRelease"))
  }
  if (runningRelease && ingestKeyIsPlaceholder) {
    throw GradleException(
      "Release build blocked: HEALTHTECH_INGEST_API_KEY is placeholder or empty. " +
        "Set a real ingest key in .env (gitignored). Never commit the key.",
    )
  }
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  constraints {
    implementation(libs.androidx.fragment) {
      because("Google Play services brings Fragment 1.1.0; ActivityResult requires 1.3.0 or newer")
    }
  }
  // SDK oficial Veepoo/HBand (VPOperateManager) para o VE30 — ver app/libs/THIRD_PARTY_NOTICE.md
  // gson-2.2.4.jar excluído: o projeto já traz Gson 2.10.1 (via Firebase/Retrofit), que
  // cobre a API que o vpprotocol usa e evita "duplicate class" no dexing.
  implementation(fileTree("libs") { include("*.aar"); exclude("gson-2.2.4.jar") })
  // com.veepoo.protocol.nordic.McuMgrOtaManager (usado internamente pelo VPOperateManager em
  // TODA conexão bem-sucedida, não só para OTA) precisa dessas classes em runtime, senão
  // NoClassDefFoundError derruba o app assim que o GATT conecta.
  implementation("no.nordicsemi.android:mcumgr-core:2.7.4")
  implementation("no.nordicsemi.android:mcumgr-ble:2.7.4")
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.sqlcipher.android)
  implementation(libs.androidx.work.runtime.ktx)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation("org.json:json:20240303")
  testImplementation(libs.kotlinx.coroutines.test)
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
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
