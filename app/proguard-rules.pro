# ConfigHub R8 rules.
# Minification is currently DISABLED (see app/build.gradle.kts). These rules are kept
# so that enabling R8 later does not silently break SQLCipher or kotlinx.serialization.

# SQLCipher
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }

# kotlinx.serialization generated serializers
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.confighub.app.**$$serializer { *; }
-keepclassmembers class com.confighub.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.confighub.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
