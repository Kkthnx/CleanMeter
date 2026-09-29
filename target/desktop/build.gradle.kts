import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.time.Year

// Compose reads this root at packaging time for every distributable target
// (createDistributable, runDistributable, and every jpackage exe/msi task),
// merging the per-OS subfolder into that build's app/resources. Writing into
// a build output folder directly, like these tasks used to, only reaches
// createDistributable's own output and never the installer's, since jpackage
// builds its own app image from this root rather than from that folder.
val monitorResourcesDir = layout.buildDirectory.dir("monitorResources/windows")

val compileMonitor = tasks.register<Exec>("compileMonitor") {
    workingDir("../../HardwareMonitor/")
    commandLine("dotnet", "publish", "-c", "Release", "-r", "win-x64", "-p:PublishAot=true")
}

val copyMonitorFiles = tasks.register<Copy>("copyMonitorFiles") {
    // presentmon.exe lands in this same folder too: HardwareMonitor.csproj's
    // own CopyPresentMon AfterBuild target puts it there from repo-root
    // presentmon/ on every publish.
    dependsOn(compileMonitor)
    from("../../HardwareMonitor/HardwareMonitor/bin/Release/net8.0/win-x64/native")
    into(monitorResourcesDir)
}

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(libs.jnativehook)
    implementation(libs.kotlinx.serialization)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.material)
    implementation(libs.viewModel)

    implementation(projects.core.common)
    implementation(projects.core.native)
    implementation(projects.core.updater)
    implementation(projects.core.designSystem)
}

sourceSets {
    main {
        java {
            srcDir("src/main/kotlin")
        }
    }
}

compose.desktop {
    application {

        afterEvaluate {
            // prepareAppResources is Compose's own task that Syncs
            // appResourcesRootDir into every distributable image, so hooking
            // it here (rather than createDistributable/packageExe, which
            // both depend on it) covers the zip and every jpackage exe/msi
            // task from the one place that actually reads this output.
            // Gradle's own task validation caught the earlier, looser hook:
            // it read copyMonitorFiles' output with no declared dependency.
            tasks.matching { it.name == "prepareAppResources" }.configureEach {
                dependsOn(copyMonitorFiles)
            }
        }

        mainClass = "app.cleanmeter.target.desktop.DesktopMainKt"

        buildTypes.release.proguard {
            version.set("7.5.0")
        }

        nativeDistributions {
            val projectVersion: String by project

            targetFormats(TargetFormat.Exe, TargetFormat.Deb)

            packageName = "cleanmeter"
            packageVersion = projectVersion
            vendor = "Kkthnx"
            description = "Lightweight hardware monitor overlay for gaming"
            copyright = "Copyright (c) ${Year.now()} Kkthnx"
            licenseFile.set(project.file("../../LICENSE"))
            appResourcesRootDir.set(layout.buildDirectory.dir("monitorResources"))

            includeAllModules = true

            windows {
                iconFile.set(project.file("installer-resources/windows/cleanmeter.ico"))
                shortcut = true
                menu = true
                menuGroup = "CleanMeter"
                // Fixed so jpackage upgrades the existing install instead of
                // giving each build a random id and installing alongside it.
                upgradeUuid = "262f1d90-90b9-4781-9a99-98d18a07cafc"
            }

            linux {
                iconFile.set(project.file("src/main/resources/imgs/logo.png"))
            }
        }
    }
}
