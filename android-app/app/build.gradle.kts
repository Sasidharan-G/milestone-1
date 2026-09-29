import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kadaikutty.pos"
    compileSdk = 35

    // Release configuration (backend URL, Sentry, signing) comes from release.properties (see release.properties.template) or CI env vars.
    val releaseProps = Properties().apply {
        val f = rootProject.file("release.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun releaseValue(name: String): String? = System.getenv(name) ?: releaseProps.getProperty(name)
    defaultConfig {
        applicationId = "com.kadaikutty.pos"
        // 24 = Android 7.0. Budget phones still shipping in shops run 7 and 8; below the minSdk
        // the installer only says "problem parsing the package".
        minSdk = 24
        targetSdk = 35
        versionCode = 37 // Google Play needs this to go up on every upload
        versionName = "0.1.0"
        testInstrumentationRunner = "com.kadaikutty.pos.HiltTestRunner"
        buildConfigField("String", "SENTRY_DSN", "\"${releaseValue("SENTRY_DSN") ?: ""}\"")

        val backendBaseUrl = releaseValue("BACKEND_BASE_URL")
            ?: (project.findProperty("BACKEND_BASE_URL") as? String)
            ?: "http://10.0.2.2:3000/"
        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendBaseUrl\"")

        val masterSupportPhone = releaseValue("MASTER_SUPPORT_PHONE")
            ?: (project.findProperty("MASTER_SUPPORT_PHONE") as? String)
            ?: "+919789418144"
        buildConfigField("String", "MASTER_SUPPORT_PHONE", "\"$masterSupportPhone\"")

    }

    // Release signing certificate SHA-256, colon-separated. Blank disables the integrity check
    // (see SecurityShield.verifyBinaryIntegrity) - set it in release.properties to enforce.
    // Only the release build type carries it: a debug build is signed with the debug key, so a
    // value in defaultConfig would make every debug install fail its own integrity check.
    val signingCertSha256 = releaseValue("SIGNING_CERT_SHA256")
        ?: (project.findProperty("SIGNING_CERT_SHA256") as? String)
        ?: ""

    signingConfigs {
        create("release") {
            val storePath = releaseValue("KADAIKUTTY_KEYSTORE_FILE")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = releaseValue("KADAIKUTTY_STORE_PASSWORD")
                keyAlias = releaseValue("KADAIKUTTY_KEY_ALIAS")
                keyPassword = releaseValue("KADAIKUTTY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without a keystore (CI) the release build stays unsigned instead of failing, so CI still
            // runs the real R8/minified release path; a local or release build with the keystore signs.
            if (releaseValue("KADAIKUTTY_KEYSTORE_FILE") != null) signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "SIGNING_CERT_SHA256", "\"$signingCertSha256\"")
        }
        debug {
            buildConfigField("String", "SIGNING_CERT_SHA256", "\"\"")
        }
    }

    splits {
        abi {
            isEnable = false
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        // java.time and friends exist natively only from Android 8; this backports them to 7.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/ASL2.0"
        }
    }

    sourceSets {
        getByName("androidTest") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }
}

// Single distributable APK workflow: build a clean release and move its only APK
// outside Gradle's transient build directory after the build succeeds.
val distributionApkName = "kadaikutty-pos-v0.1.0-release.apk"

tasks.register("releaseApk") {
    group = "distribution"
    description = "Cleanly rebuilds the signed release APK and places the only distributable copy in ../apk-releases."
    dependsOn("clean", "assembleRelease")

    doLast {
        val releaseDirectory = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val apks = releaseDirectory.listFiles { file -> file.isFile && file.extension == "apk" }?.toList().orEmpty()
        check(apks.size == 1) { "Expected one release APK in ${releaseDirectory.absolutePath}, found ${apks.size}" }
        val source = apks.single()

        val destinationDirectory = rootProject.file("../apk-releases")
        destinationDirectory.mkdirs()
        val destination = destinationDirectory.resolve(distributionApkName)
        source.copyTo(destination, overwrite = true)
        check(source.delete()) { "Could not remove transient APK: ${source.absolutePath}" }
    }
}

tasks.configureEach {
    // Android creates assembleRelease after this Kotlin build script has been evaluated.
    // Configure it lazily so this works with the project's AGP task lifecycle.
    if (name == "assembleRelease") {
        mustRunAfter("clean")
    }
}

kotlin { jvmToolchain(21) }

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.okhttp3.logging.interceptor)
    implementation(libs.sentry.android)
    testImplementation(libs.junit)
    // org.json is a stub in the Android JVM test runtime; real impl needed for BackupManager/SyncManager tests
    testImplementation("org.json:json:20240303")
    testImplementation(libs.mockito.core)
    testImplementation(libs.kotlinx.coroutines.test)

    implementation(libs.kotlinx.serialization.json)
    
    // Paging 3
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.room.paging)


    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    // Security Dependencies
    implementation(libs.androidx.biometric)
    implementation(libs.rootbeer.lib)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)

    // CameraX & Google ML Kit Barcode Scanner
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.guava)
    implementation(libs.zxing.android.embedded)

    // WebSockets (Phase 2 Sync)
    implementation("io.socket:socket.io-client:2.1.1") {
        exclude(group = "org.json", module = "json")
    }

    // Android Test dependencies
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.room.testing)
    
    // Compose UI Testing
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    
    // Hilt Testing
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
