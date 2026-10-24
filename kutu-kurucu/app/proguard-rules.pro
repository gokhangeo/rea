# Kod küçültülür ama adlar korunur: hata ekran görüntüsüyle gelirse okunabilsin.
-dontobfuscate
-keep class fi.iki.elonen.** { *; }
-keep class dadb.** { *; }
-dontwarn dadb.**
-dontwarn okio.**
-dontwarn org.jetbrains.annotations.**
