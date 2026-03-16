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
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Fix for Firestore crash in release builds.
# This prevents ProGuard from removing the default constructor and fields
# that Firestore needs for data serialization and deserialization.
-keepattributes Signature
-keep class com.google.firebase.firestore.** { *; }
-keepnames class com.google.protobuf.** { *; }

# IMPORTANT: Keep all your data model classes that you use with Firestore.
# The following rule covers all classes in your 'data' package.
-keep class com.aryan.reader.data.** {
    <init>();
    *;
}

# Keep MobiParser inner classes that are accessed from JNI.
# ProGuard/R8 can't detect this usage, so we must keep them explicitly
# to prevent renaming/removal in release builds.
-keep class com.aryan.reader.epub.MobiParser$* {
    <init>(...);
    *;
}

# Keep classes for Google Sign In and Credential Manager
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class com.google.android.gms.auth.api.identity.** { *; }
-keep class androidx.credentials.** { *; }

# Keep classes for Firebase Auth
-keep class com.google.firebase.auth.** { *; }

# These rules can help prevent gRPC-related issues in release builds.
-keep class io.grpc.** { *; }
-dontwarn com.squareup.okhttp.**

#noinspection ShrinkerUnresolvedReference
-dontwarn com.google.protobuf.GeneratedMessageV3
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageV3 {
    <fields>;
}

# Keep GSON TypeToken for proper JSON serialization/deserialization in release builds.
# This prevents R8 from stripping generic type information needed by Gson.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

-keep class com.aryan.reader.paginatedreader.Woff2Converter { *; }

-dontwarn com.gemalto.jp2.**

# Flexmark Markdown parser rules
-keep class com.vladsch.flexmark.** { *; }
-keepnames class com.vladsch.flexmark.** { *; }
-keepclassmembers class com.vladsch.flexmark.** { *; }

-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn javax.imageio.**

-keepattributes Signature, EnclosingMethod, InnerClasses, *Annotation*

-keep class com.aryan.reader.pdf.NativePdfiumBridge {
    *;
}