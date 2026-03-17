import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = "com.github.f2koi"
version = "0.1.0"

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    compileOnly("net.java.dev.jna:jna:5.16.0")
    compileOnly("net.java.dev.jna:jna-platform:5.16.0")

    intellijPlatform {
        intellijIdea("2025.3")

        plugin("IdeaVIM", "2.30.0")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    patchPluginXml {
        sinceBuild.set("253")  // 2025.3+ (IdeaVim 2.30.0 요구사항)
    }

    buildSearchableOptions {
        enabled = false
    }
}
