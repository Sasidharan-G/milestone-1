# Top-Tier Production Proguard Rules for Kadaikutty POS

# Kotlin Coroutines & Serialization
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class kotlinx.coroutines.** { *; }
-keepclassmembers class * implements kotlinx.serialization.KSerializer { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# AndroidX Room Database
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }

# Google Hilt & Dagger
-keep class * extends androidx.lifecycle.ViewModel
-keep class * extends androidx.lifecycle.AndroidViewModel
-keep class dagger.hilt.** { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-keep class * implements dagger.hilt.internal.TestSingletonComponent { *; }

# Firebase & Play Services
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Jetpack Compose & Material Icons (Shrink 15k unused icons)
-keep class androidx.compose.material.icons.** { *; }
-dontwarn androidx.compose.**

# Sentry & Crash Analytics
-dontwarn io.sentry.**
-keep class io.sentry.** { *; }

# Strip System Logging in Release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
