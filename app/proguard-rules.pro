# Bestboard ProGuard / R8 rules
# Minification is ON (was off: Compose ViewTree owners were missing — fixed via ComposeIMEHelper).

# Keep crash-readable stacks.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# IME entry point — referenced from AndroidManifest, never from code.
-keep public class handboard.app.ime.HandBoardService { *; }
-keep public class handboard.app.MainActivity { *; }
-keep public class handboard.app.HandBoardApplication { *; }

# Compose runtime (compiler-generated, reflection-adjacent).
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keep class androidx.lifecycle.ViewModelStoreOwner { *; }
-keep class androidx.savedstate.SavedStateRegistryOwner { *; }
-keep class androidx.lifecycle.LifecycleOwner { *; }

# DataStore Preferences serializer (proto-free, but keep the delegate wiring).
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# Emoji2 + picker (font loading via reflection).
-keep class androidx.emoji2.** { *; }
-dontwarn androidx.emoji2.**

# Coroutines service loaders.
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# org.json (currency parser) — no shrink surprises.
-keep class org.json.** { *; }

# JNI gesture decoder bridge (native methods resolved at runtime).
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class handboard.app.prediction.glide.** { *; }

# Settings/activity-result contracts referenced via strings.
-keep class androidx.activity.result.** { *; }
