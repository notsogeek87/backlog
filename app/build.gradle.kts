plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

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
        // Surchargeable en CI via -PbacklogVersionCode=N -PbacklogVersionName=X.Y.N
        // (android.yml) pour que chaque release GitHub porte une version unique et
        // croissante, ou -PversionCode/-PversionName (play-store-bundle.yml).
        versionCode = when {
            project.hasProperty("versionCode") -> (project.property("versionCode") as String).toInt()
            project.hasProperty("backlogVersionCode") -> (project.property("backlogVersionCode") as String).toInt()
            else -> 1
        }
        versionName = when {
            project.hasProperty("versionName") -> project.property("versionName") as String
            project.hasProperty("backlogVersionName") -> project.property("backlogVersionName") as String
            else -> "0.1.0"
        }

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
        }
        release {
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

    // Background release-day check (equivalent to the iOS nightly BGTask)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
