# R8 keep-правила для release-сборки.

# Kotlin serialization: сериализаторы генерируются плагином и вызываются рефлексией.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.drwho174.infusioncalc.**$$serializer {
    *;
}
-keepclassmembers class com.drwho174.infusioncalc.** {
    *** Companion;
}
-keepclasseswithmembers class com.drwho174.infusioncalc.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room: реализации базы и DAO создаются/находятся по имени.
-keep class * extends androidx.room3.RoomDatabase {
    <init>();
}
-keep @androidx.room3.Entity class * {
    *;
}
-keep @androidx.room3.Dao interface * {
    *;
}
-keep class com.drwho174.infusioncalc.data.local.**_Impl {
    *;
}

# Koin: контейнер и модули не должны вырезаться.
-keep class org.koin.** {
    *;
}

# Navigation 3: ключи навигации сериализуются и должны сохранять имена.
-keep,allowobfuscation class * implements androidx.navigation3.runtime.NavKey {
    *;
}

# Markwon HTML опционально ссылается на strikethrough-расширение CommonMark.
-dontwarn org.commonmark.ext.gfm.strikethrough.Strikethrough

