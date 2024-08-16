plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
}

group = "funn.j2k"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")

    if (!hostOs.startsWith("Windows")) {
        throw GradleException("Host OS is not supported")
    }

    mingwX64("native").apply {
        binaries {
            executable("media-controls-mapper", listOf(RELEASE, DEBUG)) {
                entryPoint = "main"
            }
        }
    }


    sourceSets {
        nativeMain.dependencies {
            implementation(libs.kotlinxSerializationJson)
        }
    }
}
