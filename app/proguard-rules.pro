# --- UniFFI / JNA -------------------------------------------------------
# The generated UniFFI Kotlin bindings call into the Rust library through JNA
# dynamic proxies. JNA resolves interfaces and method names reflectively, so
# R8 must not rename or strip them. Keep the whole generated package and all
# JNA classes verbatim.
-keep class com.sun.jna.** { *; }
-keep class uniffi.** { *; }

# Preserve the metadata JNA relies on for proxy/record construction.
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Native method names are looked up by symbol name at runtime.
-keepclasseswithmembernames class * {
    native <methods>;
}

-dontwarn com.sun.jna.**
-dontwarn uniffi.**
