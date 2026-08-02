# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /tools/proguard/proguard-android.txt

# Keep source file names and line numbers so stack traces in crash reports stay
# symbolicated/readable against the R8 mapping.txt (app/build/outputs/mapping/release/),
# which should be uploaded to Play Console alongside the AAB for deobfuscation.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# WorkManager instantiates workers by class name via reflection.
-keep class com.example.kwh.reminders.MeterReminderWorker { *; }
-keep class com.example.kwh.reminders.SwitchReminderWorker { *; }

# Room entities/DAOs are referenced by the generated (KAPT) implementation; keep them
# and their no-arg/component constructors intact for the generated code to bind to.
-keep class com.example.kwh.data.** { *; }

# Kotlin metadata (needed for kotlin-reflect-adjacent tooling and clean stack traces).
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
