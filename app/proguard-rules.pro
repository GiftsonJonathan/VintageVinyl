# Release builds are not minified by default (see app/build.gradle.kts).
# If you enable minification, Media3 and Compose ship their own consumer rules.
-keep class com.vintagevinyl.dsp.** { *; }
