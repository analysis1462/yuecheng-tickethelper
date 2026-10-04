# 悦程购票 R8 规则
# JSON 解析全部走 JsonParser 手工映射(无 Gson 反射建 bean),数据类字段名可混淆;
# 以下为防御性保留与三方库必要规则。

# Kotlin/协程/OkHttp/Okio/Gson 均自带 consumer rules,此处只补 app 自有代码:
# org.json 为平台 API;Compose 无反射。保留崩溃堆栈可读性:
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
