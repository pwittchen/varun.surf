# kotlinx.serialization: keep generated serializers of @Serializable classes (DTOs, routes)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer { *; }

# Retrofit interfaces are used reflectively
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface surf.varun.core.network.VarunApi { *; }
-keep interface surf.varun.core.network.VarunPaidApi { *; }

# MapLibre native bindings
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
