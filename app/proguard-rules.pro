# R8 (builds release et github, voir app/build.gradle.kts).
#
# Choix prudent : le code de l'app est gardé tel quel (pas de renommage ni de suppression), seules les
# bibliothèques sont réduites — c'est là qu'est le gain (material-icons-extended, kotlin-reflect, Compose…).
# Raison : Moshi (KotlinJsonAdapterFactory) lit nos DTO par réflexion, et l'app n'a pas de tests sur appareil
# qui attraperaient une classe retirée à tort. Les bibliothèques (Retrofit, OkHttp, Room, WorkManager, Glance,
# Compose, Moshi) fournissent leurs propres règles.
-keep class com.davidgcd.backlog.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, SourceFile, LineNumberTable

# Mises à jour intégrées (bibliothèque vendorée libs/lielugit-maven) : gardée entière par sécurité.
-keep class com.lielu.** { *; }

# Retrofit : interfaces d'API et types génériques des fonctions suspend.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Moshi réflexion Kotlin : métadonnées Kotlin nécessaires à KotlinJsonAdapterFactory.
-keep class kotlin.Metadata { *; }

-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
