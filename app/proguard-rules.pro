# Project-specific proguard rules.
# Keep SSHJ and BouncyCastle to avoid reflection-related issues during minification.
-keep class com.hierynomus.** { *; }
-keep class net.schmizz.sshj.** { *; }
-keep class org.bouncycastle.** { *; }

# Keep Hilt generated classes.
-keep class dagger.hilt.internal.** { *; }
-keep class hilt_aggregated_deps.** { *; }

# Keep Kotlin metadata for Compose.
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable <methods>;
}

# Suppress warnings for optional Java SE classes referenced by SSHJ/BouncyCastle.
-dontwarn javax.naming.**
-dontwarn javax.security.auth.login.**
-dontwarn org.ietf.jgss.**
-dontwarn sun.security.x509.**
