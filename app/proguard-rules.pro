# Room keeps entities/DAOs referenced from generated code.
-keep class com.mathapplocker.foss.data.db.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }

# Keep Compose runtime metadata for reflective composition paths.
-dontwarn org.jetbrains.annotations.**
