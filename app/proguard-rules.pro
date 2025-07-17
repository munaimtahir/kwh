# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\user\AppData\Local\Android\Sdk\tools\proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If you use reflection, typically to serialize or deserialize classes from
# network protocols, you need to tell ProGuard what classes to keep.
-keepattributes Signature

# For using GSON with ProGuard, if you use it
-keep class com.google.gson.annotations.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep a class that is used by an XML layout.
#-keep public class com.example.yourpackage.MyView

# Keep a class and its members, if it is referenced from native code.
#-keep public class com.example.yourpackage.MyJniClass {
#    native <methods>;
#}

# Keep a class and its members, if it is referenced from an annotation.
#-keepclasseswithmembernames class * {
#    @com.example.yourpackage.MyAnnotation <methods>;
#}

# Keep a class that is referenced from a library.
#-keep public class com.example.yourpackage.MyClass

# Keep the R class, if it is referenced from a library.
#-keep class **.R$* {
#    <fields>;
#}

# Keep the Parcelable creator, if it is referenced from a library.
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
