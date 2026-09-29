import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.time.Year

// Compose reads this root at packaging time for every distributable target
// (createDistributable and runDistributable), merging the per-OS subfolder
// into that build's app/resources. The Windows installer is our own WiX
// project (target/desktop/wix), built directly from createDistributable's
// output rather than through a jpackage exe/msi task, so this only needs to
// cover createDistributable/runDistributable now, not a jpackage bundler
// task too.
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
    // Updater.exe: applies a downloaded update by waiting for cleanmeter.exe
    // to exit, extracting the new release over the install directory, and
    // relaunching. compileMonitor's dotnet publish runs from the solution
    // directory and AOT-publishes every project in it, Updater included.
    from("../../HardwareMonitor/Updater/bin/Release/net8.0/win-x64/native")
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
            // appResourcesRootDir into the distributable image. Gradle's own
            // task validation caught an earlier, looser hook here: it read
            // copyMonitorFiles' output with no declared dependency.
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

            // Windows no longer uses jpackage's own exe/msi bundler; the
            // installer is built by our own WiX project instead (see
            // target/desktop/wix/main.wxs for why). Deb packaging for Linux
            // is unaffected and still goes through jpackage normally.
            targetFormats(TargetFormat.Deb)

            packageName = "cleanmeter"
            packageVersion = projectVersion
            vendor = "Kkthnx"
            description = "Lightweight hardware monitor overlay for gaming"
            copyright = "Copyright (c) ${Year.now()} Kkthnx"
            licenseFile.set(project.file("../../LICENSE"))
            appResourcesRootDir.set(layout.buildDirectory.dir("monitorResources"))

            includeAllModules = true

            linux {
                iconFile.set(project.file("src/main/resources/imgs/logo.png"))
            }
        }
    }
}
