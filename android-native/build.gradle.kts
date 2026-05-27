buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.0.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.3.8")
    }
}
