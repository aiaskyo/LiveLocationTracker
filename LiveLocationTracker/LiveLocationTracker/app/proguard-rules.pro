# Add project specific ProGuard rules here.

# Keep Firestore model classes and their no-arg constructors so automatic
# (de)serialization via toObject()/set() keeps working in release builds.
-keepclassmembers class com.example.livelocationtracker.data.model.** {
    *;
}
-keep class com.example.livelocationtracker.data.model.** { *; }

# Firebase / Play Services generally ship consumer ProGuard rules, but a
# couple of extra keeps make stack traces from crash reports readable.
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
