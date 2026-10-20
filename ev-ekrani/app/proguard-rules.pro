# Shrink and optimise, but keep class and method names: the size win comes from dropping unused
# AndroidX code, not from renaming, and readable stack traces matter more in a sideloaded app
# where crash reports arrive as screenshots.
-dontobfuscate

# Ev Ekranı: ONNX Runtime calls back into its Java classes from native code, and the widget page talks
# to MainActivity through a JavaScript interface; R8 must keep both.
-keep class ai.onnxruntime.** { *; }
-keep class fi.iki.elonen.** { *; }
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-dontwarn ai.onnxruntime.**
