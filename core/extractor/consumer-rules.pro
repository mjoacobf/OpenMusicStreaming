# Reglas de R8 para NewPipeExtractor (tomadas de NewPipe)
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.* { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.javascript.engine.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# Interfaz JavaScript del WebView que genera poTokens
-keepclassmembers class me.mjoacobf.openmusicstreaming.core.extractor.potoken.PoTokenWebView {
    @android.webkit.JavascriptInterface <methods>;
}
