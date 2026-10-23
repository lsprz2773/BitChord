import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * The Kotlin half of the iPhone app: a YouTube Music client small enough to run
 * on Kotlin/Native, an AVPlayer-backed player and a Compose screen, linked into
 * the static framework `BitChordKit` that iosApp/ embeds.
 *
 * The jvm target exists for the parser tests alone, so they run on any machine;
 * the iOS targets only compile on macOS.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val ktor = "3.5.2"
val coil = "3.3.0"

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "BitChordKit"
            isStatic = true
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation("io.ktor:ktor-client-core:$ktor")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            // The Compose compiler plugin runs on every target, the test-only jvm
            // one included, and refuses to without the runtime on its classpath.
            implementation(compose.runtime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:$ktor")
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("io.coil-kt.coil3:coil-compose:$coil")
            implementation("io.coil-kt.coil3:coil-network-ktor3:$coil")
        }
    }
}
