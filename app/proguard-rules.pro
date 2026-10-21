# Gson 模型（内容 JSON）字段名必须保留
-keep class com.littlestar.app.data.content.model.** { *; }
# progress.json 数据模型
-keep class com.littlestar.app.data.store.ProgressState** { *; }
# ContentRepository 的私有包装类（Gson 反射实例化：M8 release 崩溃修复）
-keep class com.littlestar.app.data.content.ContentRepository$* { *; }

# Media3 / Guava 常规噪音
-dontwarn com.google.common.**
-dontwarn org.checkerframework.**
