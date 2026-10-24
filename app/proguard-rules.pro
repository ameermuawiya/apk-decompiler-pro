# R8 / ProGuard optimization rules for release APK size reduction

-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-allowaccessmodification
-repackageclasses ''

# Keep Android entry points & manifest components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Keep Android XML & AttributeSet platform interfaces shadowed by ARSCLib
-keep public interface android.content.res.XmlResourceParser { *; }
-keep public class android.content.res.XmlResourceParser { *; }
-keep public interface android.util.AttributeSet { *; }
-keep public class android.util.AttributeSet { *; }
-keep public interface org.xmlpull.v1.** { *; }
-keep public class org.xmlpull.v1.** { *; }

# Keep Enum methods required for reflection and state restoration
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelables and Serializables
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep custom Application, Workers, Receivers, and Data models
-keep class com.ameermuawiya.decompilers.MainApplication { *; }
-keep class com.ameermuawiya.decompilers.data.** { *; }
-keep class com.ameermuawiya.decompilers.decompilers.** { *; }
-keep class com.ameermuawiya.decompilers.engines.** { *; }
-keep class com.ameermuawiya.decompilers.workers.** { *; }
-keep class com.ameermuawiya.decompilers.receivers.** { *; }
-keep class com.ameermuawiya.decompilers.managers.ReleaseInfo { *; }

# Keep Decompiler Engines, ARSCLib, Dex & Sora Editor classes
-keep class io.github.rosemoe.sora.** { *; }
-keep class com.googlecode.d2j.** { *; }
-keep class com.googlecode.dex2jar.** { *; }
-keep class org.jf.** { *; }
-keep class org.objectweb.asm.** { *; }
-keep class com.reandroid.** { *; }
-keep class jadx.** { *; }
-keep class org.benf.cfr.** { *; }
-keep class com.strobel.** { *; }
-keep class com.beust.jcommander.** { *; }
-keep class org.jetbrains.java.decompiler.** { *; }

# Attributes required for reflection / Compose
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Ignore non-fatal library build warnings
-dontwarn **

