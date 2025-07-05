# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-ignorewarnings
-keep class * {
    public private *;
}
#glide proguard
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

-assumenosideeffects class com.maxwai.nclientv3.utility.LogUtility {
    public static void d(...);
    public static void i(...);
    public static void e(...);
}
-keep public class * implements com.bumptech.glide.module.GlideModule
-dontwarn com.bumptech.glide.load.resource.bitmap.VideoDecoder

# Suppress warnings about missing classes from annotation processing and code generation tools,
# as they are only used at compile time and are not needed at runtime.

-dontwarn com.squareup.javapoet.**
-dontwarn javax.lang.model.**
-dontwarn javax.annotation.processing.**
-dontwarn javax.tools.**

# The 'auto-common' and 'auto-service' libraries also reference Guava classes,
# which might not be in the final runtime if you don't use Guava directly.
# It's safe to suppress these warnings as well for annotation processors.
-dontwarn com.google.common.base.**
-dontwarn com.google.common.collect.**
-dontwarn com.google.common.util.**
-dontwarn org.checkerframework.checker.nullness.**
