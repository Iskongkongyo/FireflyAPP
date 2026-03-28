import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.Copy
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val generatedPackTemplateAssetsDir = layout.buildDirectory.dir("generated/assets/pack-template")
val generatedLocalHelpAssetsDir = layout.buildDirectory.dir("generated/assets/local-help")
val signingPropertiesFile = rootProject.file("signing.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.exists()) {
        signingPropertiesFile.inputStream().use { input -> load(input) }
    }
}
val releaseKeystoreFile = signingProperties.getProperty("storeFile")
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?.let { rootProject.file(it) }
val releaseStorePassword = signingProperties.getProperty("storePassword")?.trim().orEmpty()
val releaseKeyAlias = signingProperties.getProperty("keyAlias")?.trim().orEmpty()
val releaseKeyPassword = signingProperties.getProperty("keyPassword")?.trim().orEmpty()
val hasWorkspaceSigning = releaseKeystoreFile?.exists() == true &&
    releaseStorePassword.isNotBlank() &&
    releaseKeyAlias.isNotBlank() &&
    releaseKeyPassword.isNotBlank()
val copyBundledTemplateApk by tasks.registering(Copy::class) {
    dependsOn(":runtimeTemplate:assembleRelease")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(project(":runtimeTemplate").layout.buildDirectory.dir("outputs/apk/release")) {
        include("*-release-unsigned.apk")
    }
    from(project(":runtimeTemplate").layout.buildDirectory.dir("intermediates/apk/release")) {
        include("*-release-unsigned.apk")
    }
    into(generatedPackTemplateAssetsDir.map { it.dir("template-apk") })
    rename { "base-template.apk" }
    doFirst {
        val outputDir = project(":runtimeTemplate").layout.buildDirectory.get().asFile
        val candidates = listOf(
            outputDir.resolve("outputs/apk/release"),
            outputDir.resolve("intermediates/apk/release")
        ).flatMap { dir ->
            if (!dir.exists()) emptyList()
            else dir.listFiles()?.filter { it.isFile && it.name.endsWith("-release-unsigned.apk") }.orEmpty()
        }
        require(candidates.isNotEmpty()) {
            "runtimeTemplate unsigned APK was not found after assembleRelease. Checked: ${
                listOf(
                    outputDir.resolve("outputs/apk/release/runtimeTemplate-release-unsigned.apk"),
                    outputDir.resolve("intermediates/apk/release/runtimeTemplate-release-unsigned.apk")
                ).joinToString { it.absolutePath }
            }"
        }
    }
}
val generateRuntimeShellTemplate by tasks.registering(Zip::class) {
    archiveFileName.set("runtime-shell-template.zip")
    destinationDirectory.set(generatedPackTemplateAssetsDir)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(rootProject.projectDir) {
        include(
            "build.gradle.kts",
            "settings.gradle.kts",
            "gradle.properties",
            "gradlew",
            "gradlew.bat",
            "gradle/**"
        )
    }
    from(rootProject.projectDir) {
        include(
            "app/build.gradle.kts",
            "app/proguard-rules.pro",
            "app/src/main/**"
        )
    }
}
val copyLocalHelpDocs by tasks.registering(Copy::class) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(rootProject.file("help"))
    into(generatedLocalHelpAssetsDir)
    doFirst {
        require(rootProject.file("help/index.html").exists()) {
            "Local help docs were not found. Expected: ${rootProject.file("help/index.html").absolutePath}"
        }
    }
}

android {
    namespace = "com.fireflyapp.lite"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fireflyapp.lite"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("boolean", "IS_WORKSPACE_HOST_APP", "true")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasWorkspaceSigning) {
            create("workspaceRelease") {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (hasWorkspaceSigning) {
                signingConfig = signingConfigs.getByName("workspaceRelease")
            }
        }
        release {
            isMinifyEnabled = false
            if (hasWorkspaceSigning) {
                signingConfig = signingConfigs.getByName("workspaceRelease")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    sourceSets.getByName("main").assets.srcDir(generatedPackTemplateAssetsDir)
    sourceSets.getByName("main").assets.srcDir(generatedLocalHelpAssetsDir)
}

tasks.named("preBuild").configure {
    dependsOn(generateRuntimeShellTemplate)
    dependsOn(copyBundledTemplateApk)
    dependsOn(copyLocalHelpDocs)
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.android.tools.build:apksig:8.5.2")
    implementation("com.github.yalantis:ucrop:2.2.10")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
