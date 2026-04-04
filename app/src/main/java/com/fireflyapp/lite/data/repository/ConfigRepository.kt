package com.fireflyapp.lite.data.repository

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import com.fireflyapp.lite.core.config.AppConfigManager
import com.fireflyapp.lite.core.icon.ProjectCustomIconReference
import com.fireflyapp.lite.core.pack.AndroidBuildProjectManager
import com.fireflyapp.lite.core.pack.LocalBuildExecutor
import com.fireflyapp.lite.core.pack.LocalBuildWorkspaceManager
import com.fireflyapp.lite.core.pack.TemplateApkPackager
import com.fireflyapp.lite.core.pack.TemplateApkSigner
import com.fireflyapp.lite.core.pack.TemplatePackWorkspaceManager
import com.fireflyapp.lite.core.project.ProjectPackageManager
import com.fireflyapp.lite.data.model.AppConfig
import com.fireflyapp.lite.data.model.LocalBuildExecutionResult
import com.fireflyapp.lite.data.model.LocalBuildWorkspace
import com.fireflyapp.lite.data.model.ProjectAppIdentity
import com.fireflyapp.lite.data.model.ProjectManifest
import com.fireflyapp.lite.data.model.ProjectPackaging
import com.fireflyapp.lite.data.model.ProjectSigning
import com.fireflyapp.lite.data.model.ProjectSummary
import com.fireflyapp.lite.data.model.TemplatePackExecutionResult
import com.fireflyapp.lite.data.model.TemplatePackExecutionStatus
import com.fireflyapp.lite.data.model.TemplatePackHistoryEntry
import com.fireflyapp.lite.data.model.TemplatePackInstallState
import com.fireflyapp.lite.data.model.TemplatePackPreflight
import com.fireflyapp.lite.data.model.TemplatePackWorkspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.BufferedInputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale

class ConfigRepository(
    private val context: Context,
    private val configManager: AppConfigManager = AppConfigManager(),
    private val projectPackageManager: ProjectPackageManager = ProjectPackageManager(),
    private val localBuildWorkspaceManager: LocalBuildWorkspaceManager = LocalBuildWorkspaceManager(),
    private val androidBuildProjectManager: AndroidBuildProjectManager = AndroidBuildProjectManager(),
    private val localBuildExecutor: LocalBuildExecutor = LocalBuildExecutor(),
    private val templatePackWorkspaceManager: TemplatePackWorkspaceManager = TemplatePackWorkspaceManager(),
    private val templateApkPackager: TemplateApkPackager = TemplateApkPackager(),
    private val templateApkSigner: TemplateApkSigner = TemplateApkSigner()
) {
    suspend fun listProjects(): Result<List<ProjectSummary>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                projectRootDir()
                    .listFiles()
                    .orEmpty()
                    .filter { it.isDirectory }
                    .mapNotNull(::readProjectSummary)
                    .sortedByDescending { it.updatedAt }
            }
        }
    }

    suspend fun createProject(projectName: String? = null): Result<ProjectSummary> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val baseName = projectName?.trim().orEmpty().ifBlank { "New Project" }
                val defaultConfig = configManager.defaultConfig()
                val config = defaultConfig.copy(
                    app = defaultConfig.app.copy(name = baseName)
                )
                val projectId = createUniqueProjectId(baseName)
                saveProjectConfig(projectId, config)
                checkNotNull(readProjectSummary(projectDir(projectId)))
            }
        }
    }

    suspend fun importProject(uri: Uri): Result<ProjectSummary> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                context.contentResolver.openInputStream(uri)?.use { rawInput ->
                    val bufferedInput = BufferedInputStream(rawInput)
                    if (projectPackageManager.isZipArchive(bufferedInput)) {
                        importProjectPackage(bufferedInput)
                    } else {
                        importProjectJson(bufferedInput)
                    }
                } ?: error("Unable to read imported project")
            }
        }
    }

    suspend fun loadConfig(projectId: String): Result<AppConfig> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                configManager.parseAndSanitize(loadProjectRawConfigInternal(projectId))
            }
        }
    }

    suspend fun loadRawConfig(projectId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                loadProjectRawConfigInternal(projectId)
            }
        }
    }

    suspend fun loadProjectSummary(projectId: String): Result<ProjectSummary> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                checkNotNull(readProjectSummary(projectDir(projectId))) { "Project not found" }
            }
        }
    }

    suspend fun loadProjectManifest(projectId: String): Result<ProjectManifest> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val config = configManager.parseAndSanitize(loadProjectRawConfigInternal(projectId))
                readProjectManifest(projectId, config)
            }
        }
    }

    suspend fun saveConfig(projectId: String, config: AppConfig): Result<AppConfig> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                saveProjectConfig(projectId, config)
            }
        }
    }

    suspend fun saveRawConfig(projectId: String, rawConfig: String): Result<AppConfig> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val sanitized = configManager.parseAndSanitize(rawConfig)
                saveProjectConfig(projectId, sanitized)
            }
        }
    }

    suspend fun resetProjectConfig(projectId: String): Result<AppConfig> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val summary = checkNotNull(readProjectSummary(projectDir(projectId)))
                val defaultConfig = configManager.defaultConfig()
                val resetConfig = defaultConfig.copy(
                    app = defaultConfig.app.copy(name = summary.name)
                )
                saveProjectConfig(projectId, resetConfig)
            }
        }
    }

    suspend fun deleteProject(projectId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val directory = projectDir(projectId)
                if (directory.exists() && !directory.deleteRecursively()) {
                    error("Unable to delete project")
                }
            }
        }
    }

    suspend fun saveProjectManifest(projectId: String, projectManifest: ProjectManifest): Result<ProjectManifest> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val config = configManager.parseAndSanitize(loadProjectRawConfigInternal(projectId))
                writeProjectManifest(
                    projectId,
                    sanitizeProjectManifest(
                        projectId = projectId,
                        projectManifest = projectManifest,
                        config = config,
                        strictPackageName = true
                    )
                )
            }
        }
    }

    suspend fun importProjectIcon(projectId: String, uri: Uri): Result<ProjectManifest> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val config = configManager.parseAndSanitize(loadProjectRawConfigInternal(projectId))
                val currentManifest = readProjectManifest(projectId, config)
                deleteProjectIconArtifact(projectId, currentManifest)

                val extension = resolveImageExtension(uri)
                val relativePath = "$PROJECT_BRANDING_DIR/${PROJECT_ICON_FILE_PREFIX}.$extension"
                val targetFile = projectDir(projectId).resolve(relativePath)
                targetFile.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: error("Unable to read icon file")

                writeProjectManifest(
                    projectId,
                    currentManifest.copy(
                        branding = currentManifest.branding.copy(
                            iconMode = PROJECT_BRANDING_MODE_CUSTOM,
                            iconPath = relativePath
                        )
                    )
                )
            }
        }
    }

    suspend fun clearProjectIcon(projectId: String): Result<ProjectManifest> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val config = configManager.parseAndSanitize(loadProjectRawConfigInternal(projectId))
                val currentManifest = readProjectManifest(projectId, config)
                deleteProjectIconArtifact(projectId, currentManifest)
                writeProjectManifest(
                    projectId,
                    currentManifest.copy(
                        branding = currentManifest.branding.copy(
                            iconMode = PROJECT_BRANDING_MODE_DEFAULT,
                            iconPath = ""
                        )
                    )
                )
            }
        }
    }

    suspend fun importProjectSplash(projectId: String, uri: Uri): Result<ProjectManifest> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val config = configManager.parseAndSanitize(loadProjectRawConfigInternal(projectId))
                val currentManifest = readProjectManifest(projectId, config)
                deleteProjectBrandingArtifact(projectId, currentManifest.branding.splashPath)

                val extension = resolveImageExtension(uri)
                val relativePath = "$PROJECT_BRANDING_DIR/${PROJECT_SPLASH_FILE_PREFIX}.$extension"
                val targetFile = projectDir(projectId).resolve(relativePath)
                targetFile.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: error("Unable to read splash file")

                writeProjectManifest(
                    projectId,
                    currentManifest.copy(
                        branding = currentManifest.branding.copy(
                            splashMode = PROJECT_BRANDING_MODE_CUSTOM,
                            splashPath = relativePath,
                            splashSkipEnabled = currentManifest.branding.splashSkipEnabled,
                            splashSkipSeconds = currentManifest.branding.splashSkipSeconds
                        )
                    )
                )
            }
        }
    }

    suspend fun clearProjectSplash(projectId: String): Result<ProjectManifest> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val config = configManager.parseAndSanitize(loadProjectRawConfigInternal(projectId))
                val currentManifest = readProjectManifest(projectId, config)
                deleteProjectBrandingArtifact(projectId, currentManifest.branding.splashPath)
                writeProjectManifest(
                    projectId,
                    currentManifest.copy(
                        branding = currentManifest.branding.copy(
                            splashMode = PROJECT_BRANDING_MODE_DEFAULT,
                            splashPath = "",
                            splashSkipEnabled = currentManifest.branding.splashSkipEnabled,
                            splashSkipSeconds = currentManifest.branding.splashSkipSeconds
                        )
                    )
                )
            }
        }
    }

    suspend fun importDrawerWallpaper(projectId: String, uri: Uri): Result<AppConfig> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val currentConfig = configManager.parseAndSanitize(loadProjectRawConfigInternal(projectId))
                deleteProjectBrandingArtifact(projectId, currentConfig.shell.drawerWallpaperPath)

                val extension = resolveImageExtension(uri)
                val relativePath = uniqueProjectBrandingPath(PROJECT_DRAWER_WALLPAPER_FILE_PREFIX, extension)
                val targetFile = projectDir(projectId).resolve(relativePath)
                targetFile.parentFile?.mkdirs()
                openInputStreamForUri(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: error("Unable to read drawer wallpaper file")

                saveProjectConfig(
                    projectId,
                    currentConfig.copy(
                        shell = currentConfig.shell.copy(
                            drawerWallpaperEnabled = true,
                            drawerWallpaperPath = relativePath
                        )
                    )
                )
            }
        }
    }

    suspend fun clearDrawerWallpaper(projectId: String): Result<AppConfig> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val currentConfig = configManager.parseAndSanitize(loadProjectRawConfigInternal(projectId))
                deleteProjectBrandingArtifact(projectId, currentConfig.shell.drawerWallpaperPath)
                saveProjectConfig(
                    projectId,
                    currentConfig.copy(
                        shell = currentConfig.shell.copy(
                            drawerWallpaperEnabled = false,
                            drawerWallpaperPath = ""
                        )
                    )
                )
            }
        }
    }

    suspend fun importDrawerAvatar(projectId: String, uri: Uri): Result<AppConfig> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val currentConfig = configManager.parseAndSanitize(loadProjectRawConfigInternal(projectId))
                deleteProjectBrandingArtifact(projectId, currentConfig.shell.drawerAvatarPath)

                val extension = resolveImageExtension(uri)
                val relativePath = uniqueProjectBrandingPath(PROJECT_DRAWER_AVATAR_FILE_PREFIX, extension)
                val targetFile = projectDir(projectId).resolve(relativePath)
                targetFile.parentFile?.mkdirs()
                openInputStreamForUri(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: error("Unable to read drawer avatar file")

                saveProjectConfig(
                    projectId,
                    currentConfig.copy(
                        shell = currentConfig.shell.copy(
                            drawerAvatarEnabled = true,
                            drawerAvatarPath = relativePath
                        )
                    )
                )
            }
        }
    }

    suspend fun clearDrawerAvatar(projectId: String): Result<AppConfig> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val currentConfig = configManager.parseAndSanitize(loadProjectRawConfigInternal(projectId))
                deleteProjectBrandingArtifact(projectId, currentConfig.shell.drawerAvatarPath)
                saveProjectConfig(
                    projectId,
                    currentConfig.copy(
                        shell = currentConfig.shell.copy(
                            drawerAvatarEnabled = false,
                            drawerAvatarPath = ""
                        )
                    )
                )
            }
        }
    }

    suspend fun importProjectCustomIcon(
        projectId: String,
        slotName: String,
        uri: Uri
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val extension = resolveImageExtension(uri)
                val relativePath = resolveProjectCustomIconRelativePath(slotName, extension)
                val targetFile = projectDir(projectId).resolve(relativePath)
                deleteProjectCustomIconSlotFiles(projectId, slotName)
                targetFile.parentFile?.mkdirs()
                openInputStreamForUri(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: error("Unable to read custom icon file")
                ProjectCustomIconReference.create(relativePath)
            }
        }
    }

    suspend fun deleteProjectCustomIcon(projectId: String, iconReference: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val relativePath = ProjectCustomIconReference.relativePathOrNull(iconReference)
                    ?: return@runCatching
                deleteProjectCustomIconFile(projectId, relativePath)
            }
        }
    }

    suspend fun importProjectKeystore(projectId: String, uri: Uri): Result<ProjectManifest> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val config = configManager.parseAndSanitize(loadProjectRawConfigInternal(projectId))
                val currentManifest = readProjectManifest(projectId, config)
                deleteProjectSigningArtifact(projectId, currentManifest)

                val extension = resolveKeystoreExtension(uri)
                val relativePath = "$PROJECT_SIGNING_DIR/${PROJECT_KEYSTORE_FILE_PREFIX}.$extension"
                val targetFile = projectDir(projectId).resolve(relativePath)
                targetFile.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: error("Unable to read keystore file")

                writeProjectManifest(
                    projectId,
                    currentManifest.copy(
                        signing = currentManifest.signing.copy(
                            mode = PROJECT_SIGNING_MODE_CUSTOM,
                            keystorePath = relativePath
                        )
                    )
                )
            }
        }
    }

    suspend fun clearProjectKeystore(projectId: String): Result<ProjectManifest> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val config = configManager.parseAndSanitize(loadProjectRawConfigInternal(projectId))
                val currentManifest = readProjectManifest(projectId, config)
                deleteProjectSigningArtifact(projectId, currentManifest)
                writeProjectManifest(
                    projectId,
                    currentManifest.copy(
                        signing = ProjectSigning()
                    )
                )
            }
        }
    }

    suspend fun resetProjectBranding(projectId: String): Result<ProjectManifest> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val config = configManager.parseAndSanitize(loadProjectRawConfigInternal(projectId))
                val currentManifest = readProjectManifest(projectId, config)
                deleteProjectBrandingArtifact(projectId, currentManifest.branding.iconPath)
                deleteProjectBrandingArtifact(projectId, currentManifest.branding.splashPath)
                writeProjectManifest(
                    projectId,
                    currentManifest.copy(
                        branding = currentManifest.branding.copy(
                            iconMode = PROJECT_BRANDING_MODE_DEFAULT,
                            iconPath = "",
                            splashMode = PROJECT_BRANDING_MODE_DEFAULT,
                            splashPath = "",
                            splashSkipEnabled = DEFAULT_SPLASH_SKIP_ENABLED,
                            splashSkipSeconds = DEFAULT_SPLASH_SKIP_SECONDS
                        )
                    )
                )
            }
        }
    }

    suspend fun resetProjectBuildMetadata(projectId: String): Result<ProjectManifest> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val config = configManager.parseAndSanitize(loadProjectRawConfigInternal(projectId))
                val currentManifest = readProjectManifest(projectId, config)
                deleteProjectSigningArtifact(projectId, currentManifest)
                writeProjectManifest(
                    projectId,
                    currentManifest.copy(
                        appIdentity = ProjectAppIdentity(
                            applicationLabel = currentManifest.projectName.ifBlank { projectId },
                            versionName = DEFAULT_VERSION_NAME,
                            versionCode = DEFAULT_VERSION_CODE,
                            packageName = ""
                        ),
                        signing = ProjectSigning(),
                        packaging = ProjectPackaging()
                    )
                )
            }
        }
    }

    suspend fun resetProjectManifest(projectId: String): Result<ProjectManifest> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val config = configManager.parseAndSanitize(loadProjectRawConfigInternal(projectId))
                writeProjectManifest(
                    projectId,
                    defaultProjectManifest(
                        projectId = projectId,
                        projectName = config.app.name.ifBlank { projectId }
                    )
                )
            }
        }
    }

    suspend fun exportProjectPackage(
        projectId: String,
        uri: Uri,
        rawConfig: String? = null,
        projectManifest: ProjectManifest? = null
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val projectDirectory = projectDir(projectId)
                val exportConfig = rawConfig
                    ?.let { configManager.stringify(configManager.parseAndSanitize(it)) }
                    ?: loadProjectRawConfigInternal(projectId)
                val config = configManager.parseAndSanitize(exportConfig)
                val exportManifest = sanitizeProjectManifest(
                    projectId = projectId,
                    projectManifest = projectManifest ?: readProjectManifest(projectId, config),
                    config = config,
                    strictPackageName = true
                )
                context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    projectPackageManager.exportPackage(
                        projectId = projectId,
                        projectDir = projectDirectory,
                        rawConfig = exportConfig,
                        projectManifest = exportManifest,
                        outputStream = output
                    )
                } ?: error("Unable to open export target")
            }
        }
    }

    suspend fun inspectLocalBuildWorkspace(projectId: String): Result<LocalBuildWorkspace> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val summary = checkNotNull(readProjectSummary(projectDir(projectId))) { "Project not found" }
                localBuildWorkspaceManager.inspectWorkspace(
                    projectId = projectId,
                    projectName = summary.name,
                    buildRootDir = buildRootDir(projectId)
                )
            }
        }
    }

    suspend fun prepareLocalBuildWorkspace(projectId: String): Result<LocalBuildWorkspace> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val projectDirectory = projectDir(projectId)
                val summary = checkNotNull(readProjectSummary(projectDirectory)) { "Project not found" }
                val rawConfig = loadProjectRawConfigInternal(projectId)
                val config = configManager.parseAndSanitize(rawConfig)
                val projectManifest = readProjectManifest(projectId, config)
                val preparedWorkspace = localBuildWorkspaceManager.prepareWorkspace(
                    projectId = projectId,
                    projectName = config.app.name.ifBlank { summary.name },
                    buildRootDir = buildRootDir(projectId)
                ) { packageFile ->
                    packageFile.outputStream().use { output ->
                        projectPackageManager.exportPackage(
                            projectId = projectId,
                            projectDir = projectDirectory,
                            rawConfig = rawConfig,
                            projectManifest = projectManifest,
                            outputStream = output
                        )
                    }
                }
                val generatedWorkspace = androidBuildProjectManager.generateProject(
                    context = context,
                    workspace = preparedWorkspace,
                    rawConfig = rawConfig
                )
                localBuildWorkspaceManager.saveWorkspace(generatedWorkspace, buildRootDir(projectId))
                generatedWorkspace
            }
        }
    }

    suspend fun executeLocalBuild(projectId: String): Result<LocalBuildExecutionResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val summary = checkNotNull(readProjectSummary(projectDir(projectId))) { "Project not found" }
                val workspace = localBuildWorkspaceManager.inspectWorkspace(
                    projectId = projectId,
                    projectName = summary.name,
                    buildRootDir = buildRootDir(projectId)
                )
                localBuildExecutor.execute(workspace)
            }
        }
    }

    suspend fun loadBuildLogPreview(projectId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val summary = checkNotNull(readProjectSummary(projectDir(projectId))) { "Project not found" }
                val workspace = localBuildWorkspaceManager.inspectWorkspace(
                    projectId = projectId,
                    projectName = summary.name,
                    buildRootDir = buildRootDir(projectId)
                )
                localBuildExecutor.readLogPreview(workspace)
            }
        }
    }

    suspend fun inspectTemplatePackWorkspace(projectId: String): Result<TemplatePackWorkspace> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val summary = checkNotNull(readProjectSummary(projectDir(projectId))) { "Project not found" }
                templatePackWorkspaceManager.inspectWorkspace(
                    projectId = projectId,
                    projectName = summary.name,
                    packRootDir = templatePackRootDir(projectId)
                )
            }
        }
    }

    suspend fun resolveTemplatePackOutputApkName(projectId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val summary = checkNotNull(readProjectSummary(projectDir(projectId))) { "Project not found" }
                val config = configManager.parseAndSanitize(loadProjectRawConfigInternal(projectId))
                val manifest = readProjectManifest(projectId, config)
                templatePackWorkspaceManager.resolveOutputApkFileName(
                    projectId = projectId,
                    projectName = summary.name,
                    projectManifest = manifest
                )
            }
        }
    }

    suspend fun loadTemplatePackHistory(projectId: String): Result<List<TemplatePackHistoryEntry>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                checkNotNull(readProjectSummary(projectDir(projectId))) { "Project not found" }
                readTemplatePackHistory(projectId)
            }
        }
    }

    suspend fun deleteTemplatePackHistoryEntry(
        projectId: String,
        entry: TemplatePackHistoryEntry
    ): Result<List<TemplatePackHistoryEntry>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val projectDirectory = projectDir(projectId)
                checkNotNull(readProjectSummary(projectDirectory)) { "Project not found" }
                val remainingEntries = readTemplatePackHistory(projectId)
                    .filterNot { it.matchesHistoryEntry(entry) }
                deleteTemplatePackArtifactIfUnreferenced(
                    projectDirectory = projectDirectory,
                    artifactPath = entry.artifactPath,
                    retainedEntries = remainingEntries
                )
                writeTemplatePackHistory(projectId, remainingEntries)
                remainingEntries
            }
        }
    }

    suspend fun prepareTemplatePackWorkspace(projectId: String): Result<TemplatePackWorkspace> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                prepareTemplatePackWorkspaceInternal(projectId)
            }
        }
    }

    suspend fun inspectTemplatePackPreflight(projectId: String): Result<TemplatePackPreflight> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val rawConfig = loadProjectRawConfigInternal(projectId)
                val config = configManager.parseAndSanitize(rawConfig)
                val projectManifest = readProjectManifest(projectId, config)
                val outputPackageName = templatePackWorkspaceManager.resolveOutputPackageName(
                    projectId = projectId,
                    projectManifest = projectManifest
                )
                val signingInspection = templateApkSigner.inspectSigningConfiguration(
                    projectDir = projectDir(projectId),
                    projectManifest = projectManifest
                )
                if (!signingInspection.isReady) {
                    return@runCatching TemplatePackPreflight(
                        outputPackageName = outputPackageName,
                        signerSummary = signingInspection.summary,
                        signerFingerprint = signingInspection.fingerprintSummary,
                        installState = TemplatePackInstallState.NOT_READY,
                        message = signingInspection.message
                    )
                }

                val installedPackage = inspectInstalledPackage(outputPackageName)
                if (installedPackage == null) {
                    return@runCatching TemplatePackPreflight(
                        outputPackageName = outputPackageName,
                        signerSummary = signingInspection.summary,
                        signerFingerprint = signingInspection.fingerprintSummary,
                        installState = TemplatePackInstallState.NOT_INSTALLED,
                        message = "No installed app is currently using $outputPackageName. Fresh install is ready."
                    )
                }

                if (installedPackage.signerFingerprints.isEmpty()) {
                    return@runCatching TemplatePackPreflight(
                        outputPackageName = outputPackageName,
                        signerSummary = signingInspection.summary,
                        signerFingerprint = signingInspection.fingerprintSummary,
                        installState = TemplatePackInstallState.NOT_READY,
                        installedVersionName = installedPackage.versionName,
                        installedVersionCode = installedPackage.versionCode,
                        message = "Installed package signature information is unavailable on this device."
                    )
                }

                val installState: TemplatePackInstallState
                val message: String
                if (signingInspection.fingerprints.sorted() != installedPackage.signerFingerprints.sorted()) {
                    installState = TemplatePackInstallState.SIGNATURE_CONFLICT
                    message =
                        "Installed app uses a different signing certificate. Uninstall it first or reuse the same keystore."
                } else if (projectManifest.appIdentity.versionCode.toLong() < installedPackage.versionCode) {
                    installState = TemplatePackInstallState.VERSION_DOWNGRADE
                    message =
                        "Installed versionCode ${installedPackage.versionCode} is newer than the Pack output ${projectManifest.appIdentity.versionCode}. Increase versionCode or uninstall before installing."
                } else {
                    installState = TemplatePackInstallState.UPDATE_COMPATIBLE
                    message =
                        "Installed app can be updated in place with the current package name and signer."
                }

                TemplatePackPreflight(
                    outputPackageName = outputPackageName,
                    signerSummary = signingInspection.summary,
                    signerFingerprint = signingInspection.fingerprintSummary,
                    installState = installState,
                    installedVersionName = installedPackage.versionName,
                    installedVersionCode = installedPackage.versionCode,
                    installedSignerFingerprint = installedPackage.signerFingerprints.joinToString(),
                    message = message
                )
            }
        }
    }

    suspend fun executeTemplatePack(projectId: String): Result<TemplatePackExecutionResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val workspace = prepareTemplatePackWorkspaceInternal(projectId)
                val config = configManager.parseAndSanitize(loadProjectRawConfigInternal(projectId))
                val projectManifest = readProjectManifest(projectId, config)
                val projectSummary = checkNotNull(readProjectSummary(projectDir(projectId))) { "Project not found" }
                val packResult = templateApkPackager.packageUnsignedApk(workspace)
                if (packResult.status == TemplatePackExecutionStatus.FAILED ||
                    packResult.status == TemplatePackExecutionStatus.BLOCKED
                ) {
                    packResult
                } else {
                    val signedResult = templateApkSigner.alignAndSign(
                        context = context,
                        workspace = workspace,
                        projectDir = projectDir(projectId),
                        projectManifest = projectManifest
                    )
                    if (signedResult.status == TemplatePackExecutionStatus.SUCCEEDED) {
                        appendTemplatePackHistory(
                            projectId = projectId,
                            projectSummary = projectSummary,
                            projectManifest = projectManifest,
                            workspace = workspace,
                            result = signedResult
                        )
                    }
                    signedResult
                }
            }
        }
    }

    suspend fun loadTemplatePackLogPreview(projectId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureProjectWorkspace()
                val summary = checkNotNull(readProjectSummary(projectDir(projectId))) { "Project not found" }
                val workspace = templatePackWorkspaceManager.inspectWorkspace(
                    projectId = projectId,
                    projectName = summary.name,
                    packRootDir = templatePackRootDir(projectId)
                )
                templateApkPackager.readLogPreview(workspace)
            }
        }
    }

    fun stringify(config: AppConfig): String {
        return configManager.stringify(config)
    }

    private fun ensureProjectWorkspace() {
        val rootDir = projectRootDir()
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }

        val initializedMarker = rootDir.resolve(WORKSPACE_MARKER_FILE)
        val hasProjects = rootDir.listFiles()?.any { it.isDirectory && it.resolve(PROJECT_CONFIG_FILE).exists() } == true
        if (hasProjects) {
            if (!initializedMarker.exists()) {
                initializedMarker.writeText("initialized", Charsets.UTF_8)
            }
            return
        }

        if (initializedMarker.exists()) {
            return
        }

        val defaultProjectDir = projectDir(DEFAULT_PROJECT_ID)
        defaultProjectDir.mkdirs()
        val bootstrappedConfig = configManager.parseAndSanitize(configManager.loadRaw(context))
        saveProjectRawConfig(DEFAULT_PROJECT_ID, configManager.stringify(bootstrappedConfig))
        writeProjectManifest(
            DEFAULT_PROJECT_ID,
            defaultProjectManifest(
                projectId = DEFAULT_PROJECT_ID,
                projectName = bootstrappedConfig.app.name.ifBlank { DEFAULT_PROJECT_ID }
            )
        )
        initializedMarker.writeText("initialized", Charsets.UTF_8)
    }

    private fun saveProjectConfig(projectId: String, config: AppConfig): AppConfig {
        val sanitized = configManager.parseAndSanitize(configManager.stringify(config))
        saveProjectRawConfig(projectId, configManager.stringify(sanitized))
        val currentManifest = runCatching {
            readProjectManifest(projectId, sanitized)
        }.getOrElse {
            defaultProjectManifest(projectId, sanitized.app.name.ifBlank { projectId })
        }
        writeProjectManifest(
            projectId,
            currentManifest.copy(
                projectName = sanitized.app.name.ifBlank { currentManifest.projectName.ifBlank { projectId } }
            )
        )
        return sanitized
    }

    private fun saveProjectRawConfig(projectId: String, rawConfig: String) {
        val directory = projectDir(projectId)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        directory.resolve(PROJECT_CONFIG_FILE).bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(rawConfig)
            writer.flush()
        }
    }

    private fun writeProjectManifest(projectId: String, projectManifest: ProjectManifest): ProjectManifest {
        val sanitized = sanitizeProjectManifest(
            projectId = projectId,
            projectManifest = projectManifest,
            config = runCatching {
                configManager.parseAndSanitize(loadProjectRawConfigInternal(projectId))
            }.getOrElse { configManager.defaultConfig() }
        )
        val directory = projectDir(projectId)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        directory.resolve(PROJECT_MANIFEST_FILE).bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(projectPackageManagerJson().encodeToString(ProjectManifest.serializer(), sanitized))
            writer.flush()
        }
        return sanitized
    }

    private fun appendTemplatePackHistory(
        projectId: String,
        projectSummary: ProjectSummary,
        projectManifest: ProjectManifest,
        workspace: TemplatePackWorkspace,
        result: TemplatePackExecutionResult
    ) {
        val artifactPath = result.artifactPath.orEmpty().trim()
        if (artifactPath.isBlank()) {
            return
        }
        val projectDirectory = projectDir(projectId)
        val artifactFile = File(artifactPath)
        val packedAt = System.currentTimeMillis()
        val resolvedArtifactFileName = workspace.artifactFileName
            .trim()
            .ifBlank { artifactFile.name.ifBlank { "output.apk" } }
        val archivedArtifactFile = archiveTemplatePackArtifact(
            projectId = projectId,
            artifactFile = artifactFile,
            preferredFileName = resolvedArtifactFileName
        )
        val entry = TemplatePackHistoryEntry(
            packedAt = packedAt,
            artifactPath = archivedArtifactFile.absolutePath,
            artifactFileName = resolvedArtifactFileName,
            artifactSizeBytes = archivedArtifactFile.length(),
            applicationLabel = workspace.applicationLabel.ifBlank { projectManifest.appIdentity.applicationLabel },
            versionName = workspace.versionName.ifBlank { projectManifest.appIdentity.versionName },
            versionCode = workspace.versionCode.takeIf { it > 0 } ?: projectManifest.appIdentity.versionCode,
            packageName = workspace.packageName.ifBlank {
                templatePackWorkspaceManager.resolveOutputPackageName(
                    projectId = projectId,
                    projectManifest = projectManifest
                )
            },
            template = projectSummary.template,
            signingSummary = projectManifest.toSigningSummary()
        )
        val updatedHistory = listOf(entry) + readTemplatePackHistory(projectId)
        val retainedHistory = updatedHistory.take(MAX_TEMPLATE_PACK_HISTORY_ENTRIES)
        writeTemplatePackHistory(
            projectId = projectId,
            entries = retainedHistory
        )
        updatedHistory.drop(MAX_TEMPLATE_PACK_HISTORY_ENTRIES).forEach { droppedEntry ->
            deleteTemplatePackArtifactIfUnreferenced(
                projectDirectory = projectDirectory,
                artifactPath = droppedEntry.artifactPath,
                retainedEntries = retainedHistory
            )
        }
    }

    private fun archiveTemplatePackArtifact(
        projectId: String,
        artifactFile: File,
        preferredFileName: String
    ): File {
        require(artifactFile.exists() && artifactFile.isFile) {
            "Pack output APK is missing and cannot be added to history."
        }
        val archiveDirectory = templatePackHistoryArtifactsDir(projectId).apply { mkdirs() }
        val archivedFile = resolveUniqueHistoryArtifactFile(
            archiveDirectory = archiveDirectory,
            preferredFileName = preferredFileName
        )
        artifactFile.copyTo(archivedFile, overwrite = true)
        return archivedFile
    }

    private fun resolveUniqueHistoryArtifactFile(
        archiveDirectory: File,
        preferredFileName: String
    ): File {
        val normalizedFileName = preferredFileName.trim().ifBlank { "output.apk" }
        val extension = normalizedFileName.substringAfterLast('.', "")
        val baseName = normalizedFileName.substringBeforeLast('.')
            .ifBlank { normalizedFileName.removeSuffix(".$extension").ifBlank { "output" } }

        val firstCandidate = archiveDirectory.resolve(normalizedFileName)
        if (!firstCandidate.exists()) {
            return firstCandidate
        }

        var index = 1
        while (true) {
            val candidateName = if (extension.isBlank()) {
                "$baseName($index)"
            } else {
                "$baseName($index).$extension"
            }
            val candidate = archiveDirectory.resolve(candidateName)
            if (!candidate.exists()) {
                return candidate
            }
            index += 1
        }
    }

    private fun readTemplatePackHistory(projectId: String): List<TemplatePackHistoryEntry> {
        val historyFile = templatePackHistoryFile(projectId)
        if (!historyFile.exists()) {
            return emptyList()
        }
        return runCatching {
            projectPackageManagerJson().decodeFromString(
                ListSerializer(TemplatePackHistoryEntry.serializer()),
                historyFile.readText(Charsets.UTF_8)
            )
        }.getOrDefault(emptyList())
    }

    private fun writeTemplatePackHistory(projectId: String, entries: List<TemplatePackHistoryEntry>) {
        val historyFile = templatePackHistoryFile(projectId)
        historyFile.parentFile?.mkdirs()
        historyFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(
                projectPackageManagerJson().encodeToString(
                    ListSerializer(TemplatePackHistoryEntry.serializer()),
                    entries.take(MAX_TEMPLATE_PACK_HISTORY_ENTRIES)
                )
            )
            writer.flush()
        }
    }

    private fun TemplatePackHistoryEntry.matchesHistoryEntry(other: TemplatePackHistoryEntry): Boolean {
        return packedAt == other.packedAt &&
            artifactPath == other.artifactPath &&
            artifactFileName == other.artifactFileName
    }

    private fun deleteTemplatePackArtifactIfUnreferenced(
        projectDirectory: File,
        artifactPath: String,
        retainedEntries: List<TemplatePackHistoryEntry>
    ) {
        val normalizedArtifactPath = artifactPath.trim()
        if (normalizedArtifactPath.isBlank()) {
            return
        }
        if (retainedEntries.any { it.artifactPath.trim() == normalizedArtifactPath }) {
            return
        }
        val artifactFile = File(normalizedArtifactPath)
        if (!artifactFile.exists()) {
            return
        }
        val projectRootPath = projectDirectory.canonicalFile.absolutePath + File.separator
        val artifactCanonicalPath = artifactFile.canonicalFile.absolutePath
        if (!artifactCanonicalPath.startsWith(projectRootPath)) {
            error("Refusing to delete an APK outside this project workspace.")
        }
        if (!artifactFile.delete()) {
            error("Unable to delete the selected APK file.")
        }
    }

    private fun loadProjectRawConfigInternal(projectId: String): String {
        return projectDir(projectId).resolve(PROJECT_CONFIG_FILE)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    private fun readProjectManifest(projectId: String, config: AppConfig): ProjectManifest {
        val manifestFile = projectDir(projectId).resolve(PROJECT_MANIFEST_FILE)
        val parsed = if (manifestFile.exists()) {
            runCatching {
                projectPackageManagerJson().decodeFromString(
                    ProjectManifest.serializer(),
                    manifestFile.readText(Charsets.UTF_8)
                )
            }.getOrNull()
        } else {
            null
        }
        return sanitizeProjectManifest(
            projectId = projectId,
            projectManifest = parsed ?: defaultProjectManifest(projectId, config.app.name.ifBlank { projectId }),
            config = config
        )
    }

    private fun readProjectSummary(directory: File): ProjectSummary? {
        val configFile = directory.resolve(PROJECT_CONFIG_FILE)
        if (!configFile.exists()) {
            return null
        }
        val config = runCatching {
            configManager.parseAndSanitize(configFile.bufferedReader(Charsets.UTF_8).use { it.readText() })
        }.getOrNull() ?: return null
        val projectId = directory.name
        val manifest = readProjectManifest(projectId, config)
        val updatedAt = maxOf(
            configFile.lastModified(),
            directory.resolve(PROJECT_MANIFEST_FILE).takeIf { it.exists() }?.lastModified() ?: 0L
        )

        return ProjectSummary(
            id = projectId,
            name = manifest.projectName.ifBlank { config.app.name.ifBlank { directory.name } },
            defaultUrl = config.app.defaultUrl,
            template = config.app.template,
            updatedAt = updatedAt,
            applicationLabel = manifest.appIdentity.applicationLabel,
            versionName = manifest.appIdentity.versionName,
            versionCode = manifest.appIdentity.versionCode,
            packageName = manifest.appIdentity.packageName
        )
    }

    private fun createUniqueProjectId(name: String): String {
        val normalized = name.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "project" }
        var candidate = normalized
        var index = 1
        while (projectDir(candidate).exists()) {
            candidate = "$normalized-$index"
            index += 1
        }
        return candidate
    }

    private fun importProjectJson(inputStream: BufferedInputStream): ProjectSummary {
        val rawConfig = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val config = configManager.parseAndSanitize(rawConfig)
        val projectId = createUniqueProjectId(config.app.name)
        saveProjectRawConfig(projectId, configManager.stringify(config))
        writeProjectManifest(
            projectId,
            defaultProjectManifest(
                projectId = projectId,
                projectName = config.app.name.ifBlank { projectId }
            )
        )
        return checkNotNull(readProjectSummary(projectDir(projectId)))
    }

    private fun importProjectPackage(inputStream: BufferedInputStream): ProjectSummary {
        val tempDir = context.cacheDir.resolve("import_${System.currentTimeMillis()}")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }

        return try {
            projectPackageManager.importPackage(inputStream, tempDir)
            val importedConfigFile = tempDir.resolve(PROJECT_CONFIG_FILE)
            require(importedConfigFile.exists()) { "Project package is missing app-config.json" }
            val config = configManager.parseAndSanitize(importedConfigFile.readText(Charsets.UTF_8))
            val manifest = projectPackageManager.readManifest(tempDir)
            val projectName = manifest?.projectName?.ifBlank { config.app.name } ?: config.app.name
            val projectId = createUniqueProjectId(projectName)
            copyDirectoryContents(sourceDir = tempDir, targetDir = projectDir(projectId))
            saveProjectRawConfig(projectId, configManager.stringify(config))
            writeProjectManifest(
                projectId,
                sanitizeProjectManifest(
                    projectId = projectId,
                    projectManifest = manifest ?: defaultProjectManifest(projectId, projectName),
                    config = config
                )
            )
            checkNotNull(readProjectSummary(projectDir(projectId)))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun copyDirectoryContents(sourceDir: File, targetDir: File) {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        sourceDir.walkTopDown()
            .filter { it != sourceDir }
            .forEach { source ->
                val relativePath = source.relativeTo(sourceDir).path
                val target = targetDir.resolve(relativePath)
                if (source.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    source.copyTo(target, overwrite = true)
                }
            }
    }

    private fun projectRootDir(): File = context.filesDir.resolve(PROJECTS_DIR_NAME)

    private fun projectDir(projectId: String): File = projectRootDir().resolve(projectId)

    private fun buildRootDir(projectId: String): File = projectDir(projectId)
        .resolve(PROJECT_BUILD_DIR_NAME)
        .resolve(LOCAL_PACKAGER_DIR_NAME)

    private fun templatePackRootDir(projectId: String): File = projectDir(projectId)
        .resolve(PROJECT_BUILD_DIR_NAME)
        .resolve(TEMPLATE_PACKAGER_DIR_NAME)

    private fun templatePackHistoryFile(projectId: String): File = templatePackRootDir(projectId)
        .resolve(TEMPLATE_PACK_HISTORY_FILE)

    private fun templatePackHistoryArtifactsDir(projectId: String): File = templatePackRootDir(projectId)
        .resolve(TEMPLATE_PACK_HISTORY_ARTIFACTS_DIR)

    private fun prepareTemplatePackWorkspaceInternal(projectId: String): TemplatePackWorkspace {
        val summary = checkNotNull(readProjectSummary(projectDir(projectId))) { "Project not found" }
        val rawConfig = loadProjectRawConfigInternal(projectId)
        val config = configManager.parseAndSanitize(rawConfig)
        val projectManifest = readProjectManifest(projectId, config)
        return templatePackWorkspaceManager.prepareWorkspace(
            context = context,
            projectId = projectId,
            projectName = summary.name,
            projectDir = projectDir(projectId),
            packRootDir = templatePackRootDir(projectId),
            rawConfig = rawConfig,
            projectManifest = projectManifest
        )
    }

    private fun defaultProjectManifest(projectId: String, projectName: String): ProjectManifest {
        val normalizedProjectName = projectName.trim().ifBlank { "New Project" }
        return ProjectManifest(
            projectId = projectId,
            projectName = normalizedProjectName,
            appIdentity = ProjectAppIdentity(
                applicationLabel = normalizedProjectName,
                versionName = DEFAULT_VERSION_NAME,
                versionCode = DEFAULT_VERSION_CODE
            ),
            signing = ProjectSigning()
        )
    }

    private fun sanitizeProjectManifest(
        projectId: String,
        projectManifest: ProjectManifest,
        config: AppConfig,
        strictPackageName: Boolean = false
    ): ProjectManifest {
        val projectName = projectManifest.projectName.trim()
            .ifBlank { config.app.name.trim().ifBlank { projectId } }
        val applicationLabel = truncateToUtf8Bytes(
            value = projectManifest.appIdentity.applicationLabel.trim()
                .ifBlank { projectName },
            maxBytes = MAX_APPLICATION_LABEL_BYTES
        )
        val versionName = truncateToUtf8Bytes(
            value = projectManifest.appIdentity.versionName.trim()
                .ifBlank { DEFAULT_VERSION_NAME },
            maxBytes = MAX_VERSION_NAME_BYTES
        )
        val packageName = sanitizePackageName(
            packageName = projectManifest.appIdentity.packageName,
            strict = strictPackageName
        )
        val keystorePath = sanitizeRelativeProjectPath(projectManifest.signing.keystorePath)
        val signingMode = if (keystorePath.isBlank()) {
            PROJECT_SIGNING_MODE_DEFAULT
        } else {
            projectManifest.signing.mode.trim().ifBlank { PROJECT_SIGNING_MODE_CUSTOM }
        }
        val iconPath = sanitizeRelativeProjectPath(projectManifest.branding.iconPath)
        val iconMode = if (iconPath.isBlank()) {
            PROJECT_BRANDING_MODE_DEFAULT
        } else {
            projectManifest.branding.iconMode.trim().ifBlank { PROJECT_BRANDING_MODE_CUSTOM }
        }
        val splashPath = sanitizeRelativeProjectPath(projectManifest.branding.splashPath)
        val splashMode = if (splashPath.isBlank()) {
            PROJECT_BRANDING_MODE_DEFAULT
        } else {
            projectManifest.branding.splashMode.trim().ifBlank { PROJECT_BRANDING_MODE_CUSTOM }
        }
        return projectManifest.copy(
            projectId = projectId,
            projectName = projectName,
            exportedAt = null,
            appConfigPath = PROJECT_CONFIG_FILE,
            appIdentity = projectManifest.appIdentity.copy(
                applicationLabel = applicationLabel,
                versionName = versionName,
                versionCode = projectManifest.appIdentity.versionCode.coerceAtLeast(1),
                packageName = packageName
            ),
            signing = projectManifest.signing.copy(
                mode = signingMode,
                keystorePath = keystorePath,
                storePassword = projectManifest.signing.storePassword,
                keyAlias = projectManifest.signing.keyAlias.trim(),
                keyPassword = projectManifest.signing.keyPassword
            ),
            packaging = projectManifest.packaging.copy(
                outputApkNameTemplate = projectManifest.packaging.outputApkNameTemplate.trim()
            ),
            branding = projectManifest.branding.copy(
                iconMode = iconMode,
                iconPath = iconPath,
                splashMode = splashMode,
                splashPath = splashPath,
                splashSkipEnabled = projectManifest.branding.splashSkipEnabled,
                splashSkipSeconds = projectManifest.branding.splashSkipSeconds.coerceIn(
                    MIN_SPLASH_SKIP_SECONDS,
                    MAX_SPLASH_SKIP_SECONDS
                )
            )
        )
    }

    private fun sanitizePackageName(packageName: String, strict: Boolean): String {
        val normalized = packageName.trim().lowercase()
        if (normalized.isBlank()) {
            return ""
        }
        val isValid = normalized.length <= MAX_PACKAGE_NAME_LENGTH &&
            PACKAGE_NAME_PATTERN.matches(normalized) &&
            normalized != context.packageName
        if (isValid) {
            return normalized
        }
        if (strict) {
            error(
                "Package name must use lowercase letters, digits, or underscores, contain at least two segments, stay within $MAX_PACKAGE_NAME_LENGTH characters, and must not equal ${context.packageName}."
            )
        }
        return ""
    }

    private fun deleteProjectIconArtifact(projectId: String, projectManifest: ProjectManifest) {
        deleteProjectBrandingArtifact(projectId, projectManifest.branding.iconPath)
    }

    private fun deleteProjectSigningArtifact(projectId: String, projectManifest: ProjectManifest) {
        deleteProjectBrandingArtifact(projectId, projectManifest.signing.keystorePath)
    }

    private fun deleteProjectBrandingArtifact(projectId: String, relativePath: String) {
        if (relativePath.isBlank()) {
            return
        }
        val iconFile = projectDir(projectId).resolve(relativePath)
        if (iconFile.exists()) {
            iconFile.delete()
        }
    }

    private fun resolveImageExtension(uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        val extensionFromMime = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType)
            ?.lowercase()
            .orEmpty()
        if (extensionFromMime in SUPPORTED_IMAGE_EXTENSIONS) {
            return extensionFromMime
        }
        val fallback = uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase()
            .orEmpty()
        return fallback.takeIf { it in SUPPORTED_IMAGE_EXTENSIONS } ?: DEFAULT_IMAGE_EXTENSION
    }

    private fun resolveKeystoreExtension(uri: Uri): String {
        val fallback = uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase()
            .orEmpty()
        return fallback.takeIf { it in SUPPORTED_KEYSTORE_EXTENSIONS } ?: DEFAULT_KEYSTORE_EXTENSION
    }

    private fun openInputStreamForUri(uri: Uri) = when (uri.scheme?.lowercase()) {
        "file" -> uri.path?.let(::File)?.takeIf(File::exists)?.inputStream()
        else -> context.contentResolver.openInputStream(uri)
    }

    private fun uniqueProjectBrandingPath(prefix: String, extension: String): String {
        return "$PROJECT_BRANDING_DIR/${prefix}_${System.currentTimeMillis()}.$extension"
    }

    private fun resolveProjectCustomIconRelativePath(slotName: String, extension: String): String {
        val normalizedSlot = slotName.trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .ifBlank { "icon" }
        return "${ProjectCustomIconReference.DIRECTORY}/$normalizedSlot.$extension"
    }

    private fun deleteProjectCustomIconSlotFiles(projectId: String, slotName: String) {
        val normalizedSlot = slotName.trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .ifBlank { "icon" }
        val customIconsDir = projectDir(projectId).resolve(ProjectCustomIconReference.DIRECTORY)
        if (!customIconsDir.exists() || !customIconsDir.isDirectory) {
            return
        }
        customIconsDir.walkTopDown()
            .maxDepth(1)
            .filter { it.isFile && it.nameWithoutExtension.equals(normalizedSlot, ignoreCase = true) }
            .forEach { file ->
                deleteProjectCustomIconFile(
                    projectId = projectId,
                    relativePath = file.relativeTo(projectDir(projectId)).invariantSeparatorsPath
                )
            }
    }

    private fun deleteProjectCustomIconFile(projectId: String, relativePath: String) {
        val normalizedRelativePath = sanitizeRelativeProjectPath(relativePath)
        if (normalizedRelativePath.isBlank() || !normalizedRelativePath.startsWith("${ProjectCustomIconReference.DIRECTORY}/")) {
            return
        }
        val targetFile = projectDir(projectId).resolve(normalizedRelativePath)
        if (!targetFile.exists()) {
            return
        }
        val projectRootPath = projectDir(projectId).canonicalFile.absolutePath + File.separator
        val targetCanonicalPath = targetFile.canonicalFile.absolutePath
        if (!targetCanonicalPath.startsWith(projectRootPath)) {
            error("Refusing to delete a custom icon outside this project workspace.")
        }
        if (!targetFile.delete()) {
            error("Unable to delete the selected custom icon file.")
        }
    }

    private fun truncateToUtf8Bytes(value: String, maxBytes: Int): String {
        if (value.toByteArray(Charsets.UTF_8).size <= maxBytes) {
            return value
        }
        val builder = StringBuilder()
        value.forEach { character ->
            val next = builder.toString() + character
            if (next.toByteArray(Charsets.UTF_8).size > maxBytes) {
                return builder.toString()
            }
            builder.append(character)
        }
        return builder.toString()
    }

    private fun sanitizeRelativeProjectPath(path: String): String {
        val normalized = path.trim()
            .replace('\\', '/')
            .trimStart('/')
        if (normalized.isBlank()) {
            return ""
        }
        if (normalized == ".." || normalized.startsWith("../") || normalized.contains("/../")) {
            return ""
        }
        return normalized
    }

    private fun inspectInstalledPackage(packageName: String): InstalledPackageInfo? {
        val packageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }

        return InstalledPackageInfo(
            versionName = packageInfo.versionName.orEmpty(),
            versionCode = resolveInstalledVersionCode(packageInfo),
            signerFingerprints = extractInstalledSignerFingerprints(packageInfo)
        )
    }

    private fun resolveInstalledVersionCode(packageInfo: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    private fun extractInstalledSignerFingerprints(packageInfo: PackageInfo): List<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return emptyList()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners.orEmpty()
            } else {
                signingInfo.signingCertificateHistory.orEmpty()
            }
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures.orEmpty()
        }
        return signatures
            .map(Signature::toByteArray)
            .map(::sha256Fingerprint)
            .distinct()
            .sorted()
    }

    private fun sha256Fingerprint(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = ":") { byte -> "%02X".format(byte) }
    }

    private fun ProjectManifest.toSigningSummary(): String {
        return if (signing.mode == PROJECT_SIGNING_MODE_CUSTOM && signing.keystorePath.isNotBlank()) {
            val alias = signing.keyAlias.ifBlank { "(alias pending)" }
            "Custom keystore ($alias)"
        } else {
            "Firefly local AndroidKeyStore"
        }
    }

    private fun projectPackageManagerJson() = kotlinx.serialization.json.Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private data class InstalledPackageInfo(
        val versionName: String,
        val versionCode: Long,
        val signerFingerprints: List<String>
    )

    private companion object {
        const val PROJECTS_DIR_NAME = "projects"
        const val PROJECT_CONFIG_FILE = "app-config.json"
        const val PROJECT_MANIFEST_FILE = "project-manifest.json"
        const val PROJECT_BRANDING_DIR = "branding"
        const val PROJECT_SIGNING_DIR = "signing"
        const val PROJECT_ICON_FILE_PREFIX = "app-icon"
        const val PROJECT_SPLASH_FILE_PREFIX = "splash-image"
        const val PROJECT_DRAWER_WALLPAPER_FILE_PREFIX = "drawer-wallpaper"
        const val PROJECT_DRAWER_AVATAR_FILE_PREFIX = "drawer-avatar"
        const val PROJECT_KEYSTORE_FILE_PREFIX = "custom-keystore"
        const val PROJECT_BRANDING_MODE_DEFAULT = "default"
        const val PROJECT_BRANDING_MODE_CUSTOM = "custom"
        const val PROJECT_SIGNING_MODE_DEFAULT = "default"
        const val PROJECT_SIGNING_MODE_CUSTOM = "custom"
        const val DEFAULT_PROJECT_ID = "default"
        const val WORKSPACE_MARKER_FILE = ".workspace-initialized"
        const val PROJECT_BUILD_DIR_NAME = "build"
        const val LOCAL_PACKAGER_DIR_NAME = "local-packager"
        const val TEMPLATE_PACKAGER_DIR_NAME = "template-packager"
        const val TEMPLATE_PACK_HISTORY_FILE = "pack-history.json"
        const val TEMPLATE_PACK_HISTORY_ARTIFACTS_DIR = "history-artifacts"
        const val DEFAULT_VERSION_NAME = "1.0.0"
        const val DEFAULT_VERSION_CODE = 1
        const val DEFAULT_SPLASH_SKIP_ENABLED = true
        const val DEFAULT_SPLASH_SKIP_SECONDS = 3
        const val MIN_SPLASH_SKIP_SECONDS = 1
        const val MAX_SPLASH_SKIP_SECONDS = 15
        const val MAX_PACKAGE_NAME_LENGTH = 180
        const val MAX_APPLICATION_LABEL_BYTES = 45
        const val MAX_VERSION_NAME_BYTES = 32
        const val DEFAULT_IMAGE_EXTENSION = "png"
        const val DEFAULT_KEYSTORE_EXTENSION = "jks"
        val SUPPORTED_IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
        val SUPPORTED_KEYSTORE_EXTENSIONS = setOf("jks", "keystore", "p12", "pfx")
        val PACKAGE_NAME_PATTERN = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
        const val MAX_TEMPLATE_PACK_HISTORY_ENTRIES = 15
    }
}
