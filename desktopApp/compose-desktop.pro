-keep class org.cef.** { *; }
-keep class org.apache.thrift.** { *; }
-keep class io.ktor.serialization.kotlinx.** { *; }
-keep class io.ktor.serialization.kotlinx.json.** { *; }
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.Library { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keep class kotlinx.coroutines.swing.SwingDispatcherFactory

# Desktop release shrinking sees optional integrations from JCEF/KCEF, JOGL, Commons
# Compress Pack200, and OkHttp platform probes. These references are not bundled for
# the Windows MSI path, so keep ProGuard from treating them as release blockers.
-dontwarn com.jetbrains.cef.**
-dontwarn com.jetbrains.JBR
-dontwarn org.cef.**
-dontwarn com.jogamp.**
-dontwarn jogamp.**
-dontwarn org.apache.commons.compress.harmony.pack200.**
-dontwarn org.objectweb.asm.**
-dontwarn org.apache.thrift.**
-dontwarn io.ktor.serialization.kotlinx.**
-dontwarn com.sun.jna.**
-dontwarn org.eclipse.swt.**
-dontwarn javafx.**
-dontwarn com.sun.javafx.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn android.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
