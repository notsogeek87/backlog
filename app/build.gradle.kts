plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// versionCode = numéro de build CI (strictement croissant), versionName = <appVersionBase>.<numéro> :
// le tag de release v<versionName> est ainsi toujours supérieur à la version installée (mises à jour via lielugit-updater).
val buildNumber = (System.getenv("BUILD_NUMBER") ?: providers.gradleProperty("buildNumber").orNull)?.toIntOrNull() ?: 1
val appVersionBase = providers.gradleProperty("appVersionBase").get()

// Clé d'upload Play Store fournie par la CI depuis les secrets du dépôt.
// Absente en local : Gradle retombe sur la signature debug (même logique que
// trusti/swipernews, voir play-store-bundle.yml).
val releaseKeystore = System.getenv("ANDROID_KEYSTORE_FILE")
val hasReleaseKeystore = releaseKeystore != null && file(releaseKeystore).exists()

android {
    namespace = "com.davidgcd.backlog"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.davidgcd.backlog"
        minSdk = 26
        targetSdk = 35
        // CI (android-build.yml) : BUILD_NUMBER = github.run_number. play-store-bundle.yml impose sa propre
        // version (-PversionCode/-PversionName, versionCode décalé de 1000 : canal Play distinct).
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: buildNumber
        versionName = (project.findProperty("versionName") as String?) ?: "$appVersionBase.$buildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Clé debug FIXE, committée dans le repo (app/debug.keystore). Sans ça,
    // chaque runner CI neuf régénère sa propre clé debug aléatoire
    // (~/.android/debug.keystore n'existe pas encore) : deux builds
    // successifs sont alors signés différemment, et Android refuse
    // d'installer la mise à jour par-dessus l'ancienne ("app non installée")
    // tant qu'on n'a pas désinstallé à la main. Une clé debug n'a rien de
    // secret (mot de passe "android" documenté par Google) — la committer
    // est la pratique standard pour des builds CI reproductibles.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            // APK distribué par GitHub Releases : mises à jour automatiques actives.
            buildConfigField("boolean", "SELF_UPDATE", "true")
        }
        release {
            // Build Play Store : Google Play gère les mises à jour (et REQUEST_INSTALL_PACKAGES y est retiré,
            // voir src/release/AndroidManifest.xml).
            buildConfigField("boolean", "SELF_UPDATE", "false")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Lets Room's DAO tests run under Robolectric in the fast JVM unit-test task
        // (testDebugUnitTest, the one CI runs) instead of needing a connected device.
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    // material-icons-core only ships a small default subset (Add, Search, Settings…);
    // Sort/FilterList and future icons need the extended set.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Room (local persistence, equivalent to SwiftData store on iOS)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Networking (IGDB / Metacritic / Steam)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    // Image loading (equivalent to ImageCacheService)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // DataStore (small local prefs, equivalent to UserDefaults)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Mises à jour automatiques depuis les GitHub Releases (dépôt Maven vendoré : libs/lielugit-maven)
    implementation("com.lielu:lielugit-updater:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // Couleur dominante de la jaquette (fond des fiches) et widget d'écran d'accueil (Glance).
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.glance:glance-appwidget:1.1.0")

    // Background release-day check (equivalent to the iOS nightly BGTask)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for the TMDB parsers: the android.jar one is a stub that returns defaults in JVM unit tests.
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    // Smoke tests of the Compose components under Robolectric (themes, semantics, drag): no emulator needed.
    testImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
