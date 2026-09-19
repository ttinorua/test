# Apache POI / xmlbeans do runtime reflection and ship service files; keep them intact.
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep interface org.apache.poi.** { *; }

# commons-compress (zip handling for OOXML's underlying zip archive format) had the
# same -dontwarn-without-a-real-keep gap as log4j2 above: its archivers.zip package
# picks extra-field/compression-method implementations by reflectively calling a
# specific class's no-arg constructor. R8 stripped one such constructor as apparently
# unused, which failed with "NoSuchMethodException: <init> []" the moment a real
# .xlsx (an actual zip archive, unlike a trivial test file) was opened.
-dontwarn org.apache.commons.compress.**
-keep class org.apache.commons.compress.** { *; }
-keep interface org.apache.commons.compress.** { *; }

# log4j2 (org.apache.logging.log4j) is POI's internal logging facade, pulled in
# transitively — not used by the app directly. Its ThreadContextMapFactory picks a
# ThreadContextMap implementation (e.g. CopyOnWriteSortedArrayThreadContextMap) and
# instantiates it reflectively by its original hardcoded class name. -dontwarn alone
# doesn't stop R8 from renaming that class; without an actual -keep, the reflective
# newInstance() call fails with "Class<...> cannot be instantiated" the moment any POI
# code first touches its logger — which is what broke real-file .xlsx import here.
-dontwarn org.apache.logging.log4j.**
-keep class org.apache.logging.log4j.** { *; }
-keep interface org.apache.logging.log4j.** { *; }

# xmlbeans compiles each OOXML XSD schema into its own auto-generated "type system"
# bookkeeping package (schemaorg_apache_xmlbeans.system.sNN.*), plus the schema-derived
# interfaces/impls themselves live under these separate top-level packages — none of
# which are covered by the org.apache.xmlbeans keep above, since they're not nested
# under it. xmlbeans looks these classes up by exact name via reflection
# (Class.forName(...).newInstance()), so if R8 renames or strips them the lookup fails
# with "Class<...> cannot be instantiated" wrapped in ExceptionInInitializerError the
# first time a real .xlsx is parsed.
-keep class schemaorg_apache_xmlbeans.** { *; }
-dontwarn schemaorg_apache_xmlbeans.**
-keep class org.openxmlformats.schemas.** { *; }
-dontwarn org.openxmlformats.schemas.**
-keep class org.etsi.uri.** { *; }
-dontwarn org.etsi.uri.**
-keep class org.w3.x2000.x09.xmldsig.** { *; }
-dontwarn org.w3.x2000.x09.xmldsig.**
-keep class com.microsoft.schemas.** { *; }
-dontwarn com.microsoft.schemas.**

# POI's optional shape-drawing code (curvesapi, and any java.awt/imageio usage it
# pulls in) touches java.awt classes that don't exist on Android. We only read/write
# cell values, never shapes or images, so these code paths are never reached at
# runtime; silence the shrinker's warnings about them instead of keeping them.
-dontwarn com.graphbuilder.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**

-keepattributes *Annotation*
-keep class com.financetracker.app.data.db.entity.** { *; }

# describeError() (used for import and Enable Banking sync error messages) reports
# t.javaClass.simpleName for the deepest cause. Without this, R8 renames these custom
# exception classes to single-letter names in release builds, so a real error like
# "continuation_key parameter is invalid" showed up to the user prefixed with "e:"
# instead of "EnableBankingRequestException:" — still correct, just unreadable.
-keepnames class com.financetracker.app.data.enablebanking.EnableBankingRequestException
-keepnames class com.financetracker.app.data.enablebanking.EnableBankingNotConfiguredException
-keepnames class com.financetracker.app.data.enablebanking.EnableBankingApiException

# WorkManager's default WorkerFactory instantiates a Worker subclass by its fully-qualified
# class name via reflection (the name is stored as a string in the persisted WorkSpec).
# Without a real -keep here, R8 renames the class and that lookup fails at runtime with
# ClassNotFoundException — the exact same failure mode that broke POI, log4j2 and
# commons-compress earlier in this project when only -dontwarn was in place.
-keep class com.financetracker.app.data.ai.AiCategorizationWorker { *; }

# Anthropic Java SDK serializes/deserializes its request and response model classes (Tool,
# CacheControlEphemeral, JsonMissing/JsonField wrapper types, etc.) via Jackson reflecting over
# their fields and methods at runtime, not via any compile-time codegen R8 can see — the same
# reflection-vs-shrinker gap that broke POI, log4j2, commons-compress and WorkManager above.
# The SDK ships its own consumer proguard rules (anthropic-java-core.pro, auto-applied), but
# those only keep classes/members carrying specific Jackson annotations — JsonMissing itself
# (the "field not set" sentinel referenced directly by the SDK's own hand-written serializer
# code, not annotation-driven) isn't covered by them, so a real signed build failed every AI
# chat/tool-use call with "JsonMissing cannot be serialized" the moment R8 renamed one of these
# classes' fields, even though the identical call worked fine in an unminified debug
# build/unit test. Scoped to com.anthropic.** only (not a blanket Jackson keep, which is a much
# larger library and was enough on its own to make R8 run out of heap during minification).
-dontwarn com.anthropic.**
-keep class com.anthropic.** { *; }
-keepclassmembers class com.anthropic.** { *; }

# The SDK's "structured outputs" helper (com.anthropic.core.StructuredOutputsKt, unused by this
# app — tool schemas are built by hand via Tool.InputSchema.builder(), never auto-generated from
# a type) pulls in com.github.victools:jsonschema-generator, which references JDK reflection
# types (AnnotatedType/AnnotatedParameterizedType) not present on Android's platform classpath.
# That code path is never reached at runtime, but the -keep above forces R8 to still fully
# verify it, which otherwise hard-fails the build — exactly the two lines R8 itself suggested
# in missing_rules.txt.
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType
