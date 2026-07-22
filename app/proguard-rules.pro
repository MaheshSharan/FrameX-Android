-keep class com.framex.app.shizuku.** { *; }
-keep interface com.framex.app.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# Strip verbose, debug, and info logs from release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
