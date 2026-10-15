import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

val onMac = System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)

kotlin {
    jvm()
    if (onMac) {
        iosArm64()
        iosSimulatorArm64()
        targets.withType<KotlinNativeTarget>().configureEach {
            if (name.startsWith("ios")) {
                binaries.framework {
                    baseName = "shared"
                    isStatic = true
                }
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        // iosMain is an intermediate set from the default hierarchy. When the
        // iOS targets are registered inside `if (onMac)`, that set is not
        // created by the time `sourceSets { }` runs — getByName("iosMain")
        // fails on a Mac. Create and wire it ourselves; Linux never enters here.
        if (onMac) {
            val iosMain = maybeCreate("iosMain")
            iosMain.dependsOn(getByName("commonMain"))
            iosMain.dependencies {
                implementation(libs.ktor.client.darwin)
            }
            getByName("iosArm64Main").dependsOn(iosMain)
            getByName("iosSimulatorArm64Main").dependsOn(iosMain)
        }
    }
}
