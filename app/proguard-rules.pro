# Conectx ProGuard rules

# ── Firebase ────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*

# Firebase Auth
-keep class com.google.firebase.auth.** { *; }

# Firebase Realtime Database — keep model classes for snapshot parsing
-keep class app.conectx.data.local.db.entity.** { *; }
-keep class app.conectx.domain.model.** { *; }

# ── Google Play Services / Nearby ────────────────────────────────────
-keep class com.google.android.gms.nearby.** { *; }
-keep class com.google.android.gms.common.** { *; }

# ── Protobuf ─────────────────────────────────────────────────────────
-keep class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
    <methods>;
}

# ── Room ──────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ── Hilt ──────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ── Tink (Crypto) ────────────────────────────────────────────────────
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# ── WorkManager ───────────────────────────────────────────────────────
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Kotlin ────────────────────────────────────────────────────────────
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# ── Misc ──────────────────────────────────────────────────────────────
# Remove Log.d and Log.v in release builds
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
