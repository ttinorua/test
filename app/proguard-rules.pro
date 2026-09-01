# Apache POI / xmlbeans do runtime reflection and ship service files; keep them intact.
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.logging.log4j.**
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep interface org.apache.poi.** { *; }

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
