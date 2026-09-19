# ---- kotlinx.serialization（R8 需要保留 serializer 与被序列化模型）----
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.brainquest.game.**$$serializer { *; }
-keepclassmembers class com.brainquest.game.** { *** Companion; }
-keepclasseswithmembers class com.brainquest.game.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class com.brainquest.game.** { <fields>; }

# ---- Java-WebSocket ----
-keep class org.java_websocket.** { *; }
-dontwarn org.java_websocket.**

# ---- OkHttp / Okio ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- 保留 Question 模型（跨设备 JSON 传输）----
-keep class com.brainquest.game.data.question.Question { *; }
-keep class com.brainquest.game.data.question.SubjectBankFile { *; }
