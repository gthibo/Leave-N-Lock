# Keep Kotlinx Serialization metadata for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class io.github.gthibo.leavenlock.data.bluelink.model.** {
    *** Companion;
}
-keep,includedescriptorclasses class io.github.gthibo.leavenlock.data.bluelink.model.**$$serializer { *; }

# Ktor / OkHttp
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# Hilt / Dagger generated components are handled by their own consumer rules.
