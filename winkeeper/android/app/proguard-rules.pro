-dontobfuscate
# Sayfadaki JavaScript bu sınıfın yöntemlerini adıyla çağırır.
-keepclassmembers class com.gokhan.winkeeper.Kopru {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
