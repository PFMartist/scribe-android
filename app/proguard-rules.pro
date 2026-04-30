# Moshi
-keep class com.scribe.app.data.model.** { *; }
-keep class com.squareup.moshi.** { *; }

# Strip Log calls in release builds
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase

# Tink / security-crypto: ignore missing compile-time annotations
-dontwarn com.google.errorprone.annotations.**
