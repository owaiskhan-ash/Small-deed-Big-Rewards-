# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep data models
-keep class com.example.model.** { *; }

# WorkManager ProGuard rules
-keep class androidx.work.** { *; }
-keep class com.example.worker.** { *; }

# Preserve line numbers for stack traces in release
-keepattributes SourceFile,LineNumberTable
