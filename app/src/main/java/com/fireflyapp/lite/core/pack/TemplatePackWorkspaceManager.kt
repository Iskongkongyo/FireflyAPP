package com.fireflyapp.lite.core.pack

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.DisplayMetrics
import android.util.TypedValue
import com.fireflyapp.lite.data.model.ProjectManifest
import com.fireflyapp.lite.data.model.TemplatePackWorkspace
import com.fireflyapp.lite.data.model.TemplatePackWorkspaceStatus
import com.fireflyapp.lite.data.model.TemplateSourceType
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class TemplatePackWorkspaceManager(
    private val manifestPatcher: BinaryManifestPatcher = BinaryManifestPatcher(),
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }
) {
    fun inspectWorkspace(
        projectId: String,
        projectName: String,
        packRootDir: File
    ): TemplatePackWorkspace {
        val jobFile = packRootDir.resolve(PACK_JOB_FILE_NAME)
        val job = readJob(jobFile)
        val sourceType = job?.sourceType ?: TemplateSourceType.INSTALLED_APP.name
        val artifactFileName = job?.outputApkFileName ?: SIGNED_APK_FILE_NAME

        return TemplatePackWorkspace(
            projectId = projectId,
            projectName = projectName,
            applicationLabel = job?.applicationLabel.orEmpty(),
            versionName = job?.versionName.orEmpty(),
            versionCode = job?.versionCode ?: 0,
            packageName = job?.packageName.orEmpty(),
            artifactFileName = artifactFileName,
            packRootPath = packRootDir.absolutePath,
            templateSourcePath = packRootDir.resolve(TEMPLATE_SOURCE_FILE_NAME).absolutePath,
            unpackedApkPath = packRootDir.resolve(UNPACKED_APK_DIR_NAME).absolutePath,
            packJobPath = jobFile.absolutePath,
            packLogPath = packRootDir.resolve(PACK_LOG_FILE_NAME).absolutePath,
            unsignedApkPath = packRootDir.resolve(UNSIGNED_APK_FILE_NAME).absolutePath,
            alignedApkPath = packRootDir.resolve(ALIGNED_APK_FILE_NAME).absolutePath,
            signedApkPath = packRootDir.resolve(artifactFileName).absolutePath,
            sourceType = runCatching { TemplateSourceType.valueOf(sourceType) }
                .getOrDefault(TemplateSourceType.INSTALLED_APP),
            status = if (job != null) TemplatePackWorkspaceStatus.PREPARED else TemplatePackWorkspaceStatus.IDLE,
            preparedAt = job?.preparedAt
        )
    }

    fun prepareWorkspace(
        context: Context,
        projectId: String,
        projectName: String,
        projectDir: File,
        packRootDir: File,
        rawConfig: String,
        projectManifest: ProjectManifest
    ): TemplatePackWorkspace {
        val templateSourceFile = packRootDir.resolve(TEMPLATE_SOURCE_FILE_NAME)
        val unpackedDir = packRootDir.resolve(UNPACKED_APK_DIR_NAME)
        val jobFile = packRootDir.resolve(PACK_JOB_FILE_NAME)
        val logFile = packRootDir.resolve(PACK_LOG_FILE_NAME)
        val preparedAt = System.currentTimeMillis()
        val artifactFileName = resolveOutputApkFileName(projectId, projectName, projectManifest)

        if (unpackedDir.exists()) {
            unpackedDir.deleteRecursively()
        }
        packRootDir.mkdirs()
        unpackedDir.mkdirs()

        val sourceType = copyTemplateApk(context, templateSourceFile)
        extractZip(templateSourceFile.inputStream(), unpackedDir)
        stripLegacySigningArtifacts(unpackedDir)
        val manifestFile = unpackedDir.resolve(ANDROID_MANIFEST_FILE_NAME)
        val patchedFlagState = manifestPatcher.forceApplicationFlagsFalse(manifestFile)
        val generatedPackageName = generatePackageName(projectId)
        val targetPackageName = resolveOutputPackageName(projectId, projectManifest)
        manifestPatcher.patchPackageIdentity(
            manifestFile = manifestFile,
            originalPackageName = context.packageName,
            targetPackageName = targetPackageName
        )
        manifestPatcher.patchApplicationLabel(
            manifestFile = manifestFile,
            placeholderValue = TEMPLATE_APPLICATION_LABEL_PLACEHOLDER,
            applicationLabel = projectManifest.appIdentity.applicationLabel
        )
        manifestPatcher.patchVersionName(
            manifestFile = manifestFile,
            placeholderValue = TEMPLATE_VERSION_NAME_PLACEHOLDER,
            versionName = projectManifest.appIdentity.versionName
        )
        manifestPatcher.patchVersionCode(
            manifestFile = manifestFile,
            versionCode = projectManifest.appIdentity.versionCode
        )
        val configFile = unpackedDir.resolve(GENERATED_CONFIG_PATH)
        configFile.parentFile?.mkdirs()
        configFile.writeText(rawConfig, Charsets.UTF_8)
        val iconResult = applyLauncherIcons(
            context = context,
            templateSourceFile = templateSourceFile,
            unpackedDir = unpackedDir,
            projectDir = projectDir,
            projectManifest = projectManifest
        )
        val splashResult = applySplashAsset(
            unpackedDir = unpackedDir,
            projectDir = projectDir,
            projectManifest = projectManifest
        )
        val splashConfigResult = applySplashConfigAsset(
            unpackedDir = unpackedDir,
            projectManifest = projectManifest
        )
        val drawerMediaResult = applyDrawerMediaAssets(
            unpackedDir = unpackedDir,
            projectDir = projectDir,
            rawConfig = rawConfig
        )
        unpackedDir.resolve(SOURCE_STAMP_FILE_NAME).delete()

        logFile.writeText(
            buildString {
                appendLine("Template source type: ${sourceType.name}")
                appendLine("Template source path: ${templateSourceFile.absolutePath}")
                appendLine("Unpacked APK path: ${unpackedDir.absolutePath}")
                appendLine(
                    "Patched binary manifest flags: debuggable=${patchedFlagState.debuggable ?: "absent"} testOnly=${patchedFlagState.testOnly ?: "absent"}"
                )
                appendLine("Application label: ${projectManifest.appIdentity.applicationLabel}")
                appendLine("Version name: ${projectManifest.appIdentity.versionName}")
                appendLine("Version code: ${projectManifest.appIdentity.versionCode}")
                appendLine(
                    "Requested package name: ${projectManifest.appIdentity.packageName.ifBlank { "(auto-generate)" }}"
                )
                appendLine("Output package name: $targetPackageName")
                appendLine("Output APK file name: $artifactFileName")
                appendLine("Icon mode: ${projectManifest.branding.iconMode}")
                appendLine("Icon source: ${projectManifest.branding.iconPath.ifBlank { "-" }}")
                appendLine("Icon result: $iconResult")
                appendLine("Splash mode: ${projectManifest.branding.splashMode}")
                appendLine("Splash source: ${projectManifest.branding.splashPath.ifBlank { "-" }}")
                appendLine("Splash result: $splashResult")
                appendLine("Splash config result: $splashConfigResult")
                appendLine("Drawer media result: $drawerMediaResult")
                appendLine("Generated fallback package name: $generatedPackageName")
                appendLine("Injected config path: ${configFile.absolutePath}")
            },
            Charsets.UTF_8
        )

        val workspace = inspectWorkspace(projectId, projectName, packRootDir).copy(
            applicationLabel = projectManifest.appIdentity.applicationLabel,
            versionName = projectManifest.appIdentity.versionName,
            versionCode = projectManifest.appIdentity.versionCode,
            packageName = targetPackageName,
            artifactFileName = artifactFileName,
            sourceType = sourceType,
            status = TemplatePackWorkspaceStatus.PREPARED,
            preparedAt = preparedAt
        )

        jobFile.writeText(
            json.encodeToString(
                TemplatePackJob(
                    projectId = projectId,
                    projectName = projectName,
                    applicationLabel = projectManifest.appIdentity.applicationLabel,
                    versionName = projectManifest.appIdentity.versionName,
                    versionCode = projectManifest.appIdentity.versionCode,
                    packageName = targetPackageName,
                    outputApkFileName = artifactFileName,
                    preparedAt = preparedAt,
                    sourceType = sourceType.name,
                    templateSourcePath = workspace.templateSourcePath,
                    unpackedApkPath = workspace.unpackedApkPath,
                    unsignedApkPath = workspace.unsignedApkPath
                )
            ),
            Charsets.UTF_8
        )

        return workspace
    }

    fun resolveOutputPackageName(
        projectId: String,
        projectManifest: ProjectManifest
    ): String {
        return projectManifest.appIdentity.packageName.ifBlank { generatePackageName(projectId) }
    }

    fun resolveOutputApkFileName(
        projectId: String,
        projectName: String,
        projectManifest: ProjectManifest
    ): String {
        val template = projectManifest.packaging.outputApkNameTemplate.trim()
        val resolvedPackageName = resolveOutputPackageName(projectId, projectManifest)
        val rawName = if (template.isBlank()) {
            "${projectManifest.appIdentity.applicationLabel.ifBlank { projectName }}_${projectManifest.appIdentity.versionName}"
        } else {
            template
                .replace("{projectName}", projectName)
                .replace("{appName}", projectManifest.appIdentity.applicationLabel)
                .replace("{versionName}", projectManifest.appIdentity.versionName)
                .replace("{versionCode}", projectManifest.appIdentity.versionCode.toString())
                .replace("{packageName}", resolvedPackageName)
        }
        val sanitized = rawName
            .replace(Regex("[\\\\/:*?\"<>|]+"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
            .ifBlank { "firefly-output" }
        val withExtension = if (sanitized.lowercase().endsWith(".apk")) sanitized else "$sanitized.apk"
        return if (withExtension in reservedArtifactFileNames) {
            "artifact-$withExtension"
        } else {
            withExtension
        }
    }

    private fun applyLauncherIcons(
        context: Context,
        templateSourceFile: File,
        unpackedDir: File,
        projectDir: File,
        projectManifest: ProjectManifest
    ): String {
        if (projectManifest.branding.iconMode != BRANDING_MODE_CUSTOM) {
            return "Default template icon retained."
        }
        val relativeIconPath = projectManifest.branding.iconPath.trim()
        if (relativeIconPath.isBlank()) {
            return "Custom icon requested but no icon path was set."
        }
        val sourceFile = projectDir.resolve(relativeIconPath)
        if (!sourceFile.exists() || !sourceFile.isFile) {
            return "Custom icon file is missing: ${sourceFile.absolutePath}"
        }

        val sourceBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
            ?: return "Unable to decode custom icon image: ${sourceFile.absolutePath}"
        try {
            val resDir = unpackedDir.resolve(RES_DIR_NAME)
            if (!resDir.exists()) {
                return "Template APK res directory is missing."
            }
            val targetFiles = resolveLauncherIconTargets(
                context = context,
                templateSourceFile = templateSourceFile,
                unpackedDir = unpackedDir,
                resDir = resDir
            )
            if (targetFiles.isEmpty()) {
                return "Launcher icon resources were not found in the template APK."
            }

            targetFiles.forEach { target ->
                val outputBitmap = if (target.isRound) {
                    renderRoundLauncherIcon(sourceBitmap, target.size)
                } else {
                    renderLauncherIcon(sourceBitmap, target.size)
                }
                try {
                    target.file.outputStream().use { output ->
                        outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                    }
                } finally {
                    outputBitmap.recycle()
                }
            }
            return "Applied custom launcher icon to ${targetFiles.size} resource files: ${
                targetFiles.joinToString { it.file.relativeTo(unpackedDir).invariantSeparatorsPath }
            }"
        } finally {
            sourceBitmap.recycle()
        }
    }

    private fun resolveLauncherIconTargets(
        context: Context,
        templateSourceFile: File,
        unpackedDir: File,
        resDir: File
    ): List<LauncherIconTarget> {
        val targets = linkedMapOf<String, LauncherIconTarget>()

        resDir.walkTopDown()
            .filter { it.isFile && it.name in launcherIconFileNames }
            .forEach { file ->
                val size = resolveLauncherIconSize(file.parentFile?.name.orEmpty()) ?: return@forEach
                targets[file.absolutePath] = LauncherIconTarget(
                    file = file,
                    size = size,
                    isRound = file.name == ICON_ROUND_FILE_NAME
                )
            }

        resolvePackagedLauncherIconTargets(context, templateSourceFile).forEach { packagedTarget ->
            val file = unpackedDir.resolve(packagedTarget.entryPath)
            if (!file.exists() || !file.isFile) {
                return@forEach
            }
            targets.putIfAbsent(
                file.absolutePath,
                LauncherIconTarget(
                    file = file,
                    size = packagedTarget.size,
                    isRound = packagedTarget.isRound
                )
            )
        }

        return targets.values.toList()
    }

    private fun resolvePackagedLauncherIconTargets(
        context: Context,
        templateSourceFile: File
    ): List<BundledLauncherIconTarget> {
        return runCatching {
            val assetManager = AssetManager::class.java.getDeclaredConstructor().newInstance()
            val addAssetPathMethod = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
            val cookie = addAssetPathMethod.invoke(assetManager, templateSourceFile.absolutePath) as? Int ?: 0
            if (cookie == 0) {
                emptyList()
            } else {
                val resources = Resources(assetManager, context.resources.displayMetrics, context.resources.configuration)
                val result = mutableListOf<BundledLauncherIconTarget>()
                launcherDensityMappings.forEach { densityMapping ->
                    resolvePackagedLauncherPath(
                        resources = resources,
                        packageName = context.packageName,
                        resourceName = ICON_RESOURCE_NAME,
                        density = densityMapping.density
                    )?.let { entryPath ->
                        result += BundledLauncherIconTarget(
                            entryPath = entryPath,
                            size = densityMapping.size,
                            isRound = false
                        )
                    }
                    resolvePackagedLauncherPath(
                        resources = resources,
                        packageName = context.packageName,
                        resourceName = ICON_ROUND_RESOURCE_NAME,
                        density = densityMapping.density
                    )?.let { entryPath ->
                        result += BundledLauncherIconTarget(
                            entryPath = entryPath,
                            size = densityMapping.size,
                            isRound = true
                        )
                    }
                }
                result.distinctBy { "${it.entryPath}:${it.size}:${it.isRound}" }
            }
        }.getOrDefault(emptyList())
    }

    private fun resolvePackagedLauncherPath(
        resources: Resources,
        packageName: String,
        resourceName: String,
        density: Int
    ): String? {
        val resourceId = resources.getIdentifier(resourceName, "mipmap", packageName)
        if (resourceId == 0) {
            return null
        }
        val typedValue = TypedValue()
        return runCatching {
            resources.getValueForDensity(resourceId, density, typedValue, true)
            typedValue.string?.toString()
        }.getOrNull()
    }

    private fun renderLauncherIcon(source: Bitmap, size: Int): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val destinationRect = Rect(0, 0, size, size)
        canvas.drawBitmap(source, computeCenterCropRect(source), destinationRect, paint)
        return output
    }

    private fun renderRoundLauncherIcon(source: Bitmap, size: Int): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val circlePath = Path().apply {
            addOval(RectF(0f, 0f, size.toFloat(), size.toFloat()), Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(circlePath)
        canvas.drawBitmap(source, computeCenterCropRect(source), Rect(0, 0, size, size), paint)
        canvas.restore()
        return output
    }

    private fun computeCenterCropRect(source: Bitmap): Rect {
        val sourceWidth = source.width
        val sourceHeight = source.height
        val sideLength = minOf(sourceWidth, sourceHeight)
        val left = (sourceWidth - sideLength) / 2
        val top = (sourceHeight - sideLength) / 2
        return Rect(left, top, left + sideLength, top + sideLength)
    }

    private fun applySplashAsset(
        unpackedDir: File,
        projectDir: File,
        projectManifest: ProjectManifest
    ): String {
        val splashAssetFile = unpackedDir.resolve(PACKAGED_SPLASH_ASSET_PATH)
        val splashConfigFile = unpackedDir.resolve(PACKAGED_SPLASH_CONFIG_PATH)
        if (projectManifest.branding.splashMode != BRANDING_MODE_CUSTOM) {
            if (splashAssetFile.exists()) {
                splashAssetFile.delete()
            }
            if (splashConfigFile.exists()) {
                splashConfigFile.delete()
            }
            return "Default runtime splash retained."
        }

        val relativeSplashPath = projectManifest.branding.splashPath.trim()
        if (relativeSplashPath.isBlank()) {
            return "Custom splash requested but no splash path was set."
        }
        val sourceFile = projectDir.resolve(relativeSplashPath)
        if (!sourceFile.exists() || !sourceFile.isFile) {
            return "Custom splash file is missing: ${sourceFile.absolutePath}"
        }

        splashAssetFile.parentFile?.mkdirs()
        sourceFile.inputStream().use { input ->
            splashAssetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return "Injected splash asset at ${splashAssetFile.relativeTo(unpackedDir).invariantSeparatorsPath}"
    }

    private fun applySplashConfigAsset(
        unpackedDir: File,
        projectManifest: ProjectManifest
    ): String {
        val splashConfigFile = unpackedDir.resolve(PACKAGED_SPLASH_CONFIG_PATH)
        if (projectManifest.branding.splashMode != BRANDING_MODE_CUSTOM) {
            if (splashConfigFile.exists()) {
                splashConfigFile.delete()
            }
            return "No custom splash config injected."
        }
        splashConfigFile.parentFile?.mkdirs()
        splashConfigFile.writeText(
            json.encodeToString(
                PackagedSplashConfig(
                    skipEnabled = projectManifest.branding.splashSkipEnabled,
                    skipSeconds = projectManifest.branding.splashSkipSeconds
                )
            ),
            Charsets.UTF_8
        )
        return "Injected splash config at ${splashConfigFile.relativeTo(unpackedDir).invariantSeparatorsPath}"
    }

    private fun applyDrawerMediaAssets(
        unpackedDir: File,
        projectDir: File,
        rawConfig: String
    ): String {
        val config = runCatching {
            json.decodeFromString(PackShellAssetConfig.serializer(), rawConfig)
        }.getOrNull() ?: return "Drawer media skipped: config parse failed."

        val copiedAssets = mutableListOf<String>()
        copyShellAsset(
            unpackedDir = unpackedDir,
            projectDir = projectDir,
            relativePath = config.shell.drawerWallpaperPath,
            copiedAssets = copiedAssets
        )
        copyShellAsset(
            unpackedDir = unpackedDir,
            projectDir = projectDir,
            relativePath = config.shell.drawerAvatarPath,
            copiedAssets = copiedAssets
        )
        if (copiedAssets.isEmpty()) {
            return "No drawer media injected."
        }
        return "Injected drawer media: ${copiedAssets.joinToString()}"
    }

    private fun copyShellAsset(
        unpackedDir: File,
        projectDir: File,
        relativePath: String,
        copiedAssets: MutableList<String>
    ) {
        val normalized = relativePath.trim().replace('\\', '/').trimStart('/')
        if (normalized.isBlank()) {
            return
        }
        val sourceFile = projectDir.resolve(normalized)
        if (!sourceFile.exists() || !sourceFile.isFile) {
            return
        }
        val targetFile = unpackedDir.resolve("assets").resolve(normalized)
        targetFile.parentFile?.mkdirs()
        sourceFile.copyTo(targetFile, overwrite = true)
        copiedAssets += targetFile.relativeTo(unpackedDir).invariantSeparatorsPath
    }

    private fun resolveLauncherIconSize(resourceDirectoryName: String): Int? {
        return when {
            "xxxhdpi" in resourceDirectoryName -> 192
            "xxhdpi" in resourceDirectoryName -> 144
            "xhdpi" in resourceDirectoryName -> 96
            "hdpi" in resourceDirectoryName -> 72
            "mdpi" in resourceDirectoryName -> 48
            "ldpi" in resourceDirectoryName -> 36
            else -> null
        }
    }

    private fun copyTemplateApk(context: Context, targetFile: File): TemplateSourceType {
        targetFile.parentFile?.mkdirs()
        val bundledTemplate = runCatching { context.assets.open(BUNDLED_TEMPLATE_ASSET_PATH) }
            .getOrElse {
                error(
                    "Bundled template APK is missing from host assets. Rebuild the host app so $BUNDLED_TEMPLATE_ASSET_PATH is packaged."
                )
            }
        bundledTemplate.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return TemplateSourceType.BUNDLED_ASSET
    }

    private fun extractZip(input: InputStream, destinationDir: File) {
        val rootCanonicalPath = destinationDir.canonicalFile.absolutePath + File.separator
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val entryName = entry.name.replace('\\', '/').trimStart('/')
                if (entryName.isNotBlank()) {
                    val outputFile = File(destinationDir, entryName)
                    val outputCanonicalPath = outputFile.canonicalFile.absolutePath
                    if (!outputCanonicalPath.startsWith(rootCanonicalPath) &&
                        outputCanonicalPath != destinationDir.canonicalPath
                    ) {
                        error("Unsafe APK entry: $entryName")
                    }
                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()
                        outputFile.outputStream().use { output ->
                            zip.copyTo(output)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun stripLegacySigningArtifacts(unpackedDir: File) {
        val metaInfDir = unpackedDir.resolve(META_INF_DIR_NAME)
        if (metaInfDir.exists()) {
            metaInfDir.listFiles().orEmpty().forEach { file ->
                val name = file.name.uppercase()
                if (
                    name == "MANIFEST.MF" ||
                    name.endsWith(".SF") ||
                    name.endsWith(".RSA") ||
                    name.endsWith(".DSA") ||
                    name.endsWith(".EC")
                ) {
                    file.deleteRecursively()
                }
            }
        }
    }

    private fun generatePackageName(projectId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(projectId.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        val segmentOne = "a" + digest.substring(0, 8)
        val segmentTwo = "b" + digest.substring(8, 13)
        val generated = "ff.$segmentOne.$segmentTwo"
        require(generated.length == GENERATED_PACKAGE_NAME_LENGTH) {
            "Generated package name length mismatch"
        }
        return generated
    }

    private fun readJob(jobFile: File): TemplatePackJob? {
        if (!jobFile.exists()) {
            return null
        }
        return runCatching {
            json.decodeFromString<TemplatePackJob>(jobFile.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    @Serializable
    data class TemplatePackJob(
        val schemaVersion: Int = 1,
        val format: String = "firefly-template-pack",
        val projectId: String,
        val projectName: String,
        val applicationLabel: String = "",
        val versionName: String = "",
        val versionCode: Int = 0,
        val packageName: String,
        val outputApkFileName: String = SIGNED_APK_FILE_NAME,
        val preparedAt: Long,
        val sourceType: String,
        val templateSourcePath: String,
        val unpackedApkPath: String,
        val unsignedApkPath: String
    )

    private data class LauncherIconTarget(
        val file: File,
        val size: Int,
        val isRound: Boolean
    )

    private data class BundledLauncherIconTarget(
        val entryPath: String,
        val size: Int,
        val isRound: Boolean
    )

    @Serializable
    private data class PackagedSplashConfig(
        val skipEnabled: Boolean = true,
        val skipSeconds: Int = 3
    )

    @Serializable
    private data class PackShellAssetConfig(
        val shell: PackShellAssetSection = PackShellAssetSection()
    )

    @Serializable
    private data class PackShellAssetSection(
        val drawerWallpaperPath: String = "",
        val drawerAvatarPath: String = ""
    )

    private data class LauncherDensityMapping(
        val density: Int,
        val size: Int
    )

    companion object {
        const val GENERATED_CONFIG_PATH = "assets/app-config.json"
        const val BUNDLED_TEMPLATE_ASSET_PATH = "template-apk/base-template.apk"
        const val TEMPLATE_APPLICATION_LABEL_PLACEHOLDER = "ff_label_placeholder_000000000000000000000000"
        const val TEMPLATE_VERSION_NAME_PLACEHOLDER = "ff_version_placeholder_000000000"
        const val BRANDING_MODE_CUSTOM = "custom"
        private const val GENERATED_PACKAGE_NAME_LENGTH = 19
        private const val ANDROID_MANIFEST_FILE_NAME = "AndroidManifest.xml"
        private const val RES_DIR_NAME = "res"
        private const val META_INF_DIR_NAME = "META-INF"
        private const val SOURCE_STAMP_FILE_NAME = "stamp-cert-sha256"
        private const val TEMPLATE_SOURCE_FILE_NAME = "template-source.apk"
        private const val UNPACKED_APK_DIR_NAME = "unpacked-apk"
        private const val PACK_JOB_FILE_NAME = "pack-job.json"
        private const val PACK_LOG_FILE_NAME = "pack.log"
        private const val PACKAGED_SPLASH_ASSET_PATH = "assets/branding/splash-image"
        private const val PACKAGED_SPLASH_CONFIG_PATH = "assets/branding/splash-config.json"
        private const val UNSIGNED_APK_FILE_NAME = "unsigned-output.apk"
        private const val ALIGNED_APK_FILE_NAME = "aligned-output.apk"
        private const val SIGNED_APK_FILE_NAME = "signed-output.apk"
        private const val ICON_FILE_NAME = "ic_launcher.png"
        private const val ICON_ROUND_FILE_NAME = "ic_launcher_round.png"
        private const val ICON_RESOURCE_NAME = "ic_launcher"
        private const val ICON_ROUND_RESOURCE_NAME = "ic_launcher_round"
        private val launcherIconFileNames = setOf(ICON_FILE_NAME, ICON_ROUND_FILE_NAME)
        private val reservedArtifactFileNames = setOf(
            TEMPLATE_SOURCE_FILE_NAME,
            UNSIGNED_APK_FILE_NAME,
            ALIGNED_APK_FILE_NAME
        )
        private val launcherDensityMappings = listOf(
            LauncherDensityMapping(DisplayMetrics.DENSITY_MEDIUM, 48),
            LauncherDensityMapping(DisplayMetrics.DENSITY_HIGH, 72),
            LauncherDensityMapping(DisplayMetrics.DENSITY_XHIGH, 96),
            LauncherDensityMapping(DisplayMetrics.DENSITY_XXHIGH, 144),
            LauncherDensityMapping(DisplayMetrics.DENSITY_XXXHIGH, 192)
        )
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    }
}
