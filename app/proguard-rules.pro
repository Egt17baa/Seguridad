# kotlinx.serialization: keep generated serializers of the app's @Serializable DTOs.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, RuntimeVisibleAnnotations, AnnotationDefault
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class app.seguridad.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.seguridad.**$$serializer { *; }
-keepclasseswithmembers class app.seguridad.** {
    kotlinx.serialization.KSerializer serializer(...);
}
