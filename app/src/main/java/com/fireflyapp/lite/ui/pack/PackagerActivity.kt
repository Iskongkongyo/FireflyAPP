package com.fireflyapp.lite.ui.pack

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import androidx.core.content.FileProvider
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fireflyapp.lite.R
import com.fireflyapp.lite.app.AppLanguageManager
import com.fireflyapp.lite.core.pack.ApkInstallManager
import com.fireflyapp.lite.data.model.TemplatePackArtifactCheck
import com.fireflyapp.lite.data.model.TemplatePackArtifactCheckStatus
import com.fireflyapp.lite.data.model.TemplatePackExecutionResult
import com.fireflyapp.lite.data.model.TemplatePackHistoryEntry
import com.fireflyapp.lite.data.model.TemplatePackInstallState
import com.fireflyapp.lite.data.model.TemplatePackExecutionStatus
import com.fireflyapp.lite.data.model.TemplatePackWorkspaceStatus
import com.fireflyapp.lite.data.model.TemplateSourceType
import com.fireflyapp.lite.data.model.TemplateType
import java.io.File
import java.text.DateFormat
import java.util.Date

class PackagerActivity : ComponentActivity() {
    private val viewModel: PackagerViewModel by viewModels()
    private val installManager = ApkInstallManager()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty()
        val projectName = intent.getStringExtra(EXTRA_PROJECT_NAME).orEmpty()
        if (projectId.isBlank()) {
            finish()
            return
        }

        viewModel.loadProject(projectId, projectName)
        enableEdgeToEdge()
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                PackagerScreen(
                    uiState = uiState,
                    onPrepareWorkspace = viewModel::prepareWorkspace,
                    onExecuteBuild = viewModel::executeBuild,
                    onDeleteHistoryEntry = viewModel::deleteHistoryEntry,
                    onInstallArtifact = ::installArtifact,
                    onShareArtifact = ::shareArtifact,
                    onCopyArtifactPath = ::copyArtifactPath,
                    onConsumeMessage = viewModel::consumeMessage,
                    onBack = ::finish
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPreflight()
    }

    companion object {
        private const val EXTRA_PROJECT_ID = "project_id"
        private const val EXTRA_PROJECT_NAME = "project_name"

        fun createIntent(context: Context, projectId: String, projectName: String): Intent {
            return Intent(context, PackagerActivity::class.java).apply {
                putExtra(EXTRA_PROJECT_ID, projectId)
                putExtra(EXTRA_PROJECT_NAME, projectName)
            }
        }
    }

    private fun installArtifact(apkPath: String) {
        if (!installManager.canRequestPackageInstalls(this)) {
            val intent = installManager.createUnknownSourcesIntent(this)
            if (intent != null) {
                startActivity(intent)
                Toast.makeText(
                    this,
                    getString(R.string.packager_enable_install_permission),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this, getString(R.string.packager_install_permission_unavailable), Toast.LENGTH_SHORT).show()
            }
            return
        }

        installManager.launchInstall(this, apkPath)
            .onFailure { throwable ->
                Toast.makeText(
                    this,
                    throwable.message ?: getString(R.string.packager_installer_launch_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun copyArtifactPath(apkPath: String) {
        if (apkPath.isBlank()) {
            return
        }
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboardManager.setPrimaryClip(ClipData.newPlainText("apk_path", apkPath))
        Toast.makeText(this, getString(R.string.packager_apk_path_copied), Toast.LENGTH_SHORT).show()
    }

    private fun shareArtifact(apkPath: String) {
        if (apkPath.isBlank()) {
            return
        }
        val artifactFile = File(apkPath)
        if (!artifactFile.exists()) {
            Toast.makeText(this, getString(R.string.packager_apk_missing), Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            artifactFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("", uri)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.packager_share_apk)))
    }
}

@Composable
private fun PackagerScreen(
    uiState: PackagerUiState,
    onPrepareWorkspace: () -> Unit,
    onExecuteBuild: () -> Unit,
    onDeleteHistoryEntry: (TemplatePackHistoryEntry) -> Unit,
    onInstallArtifact: (String) -> Unit,
    onShareArtifact: (String) -> Unit,
    onCopyArtifactPath: (String) -> Unit,
    onConsumeMessage: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val projectSummary = uiState.projectSummary
    val installableArtifactPath = uiState.buildExecution.result
        ?.takeIf { it.status == TemplatePackExecutionStatus.SUCCEEDED }
        ?.artifactPath
        .orEmpty()

    LaunchedEffect(uiState.userMessage) {
        val message = uiState.userMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        onConsumeMessage()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = projectSummary?.name?.ifBlank { stringResource(R.string.packager_title) }
                        ?: stringResource(R.string.packager_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            TextButton(
                onClick = onPrepareWorkspace,
                enabled = !uiState.isLoading && !uiState.isPreparing && !uiState.isBuilding
            ) {
                Text(stringResource(R.string.packager_prepare))
            }
            TextButton(
                onClick = onExecuteBuild,
                enabled = !uiState.isLoading &&
                    !uiState.isPreparing &&
                    !uiState.isBuilding &&
                    (uiState.preflight.installState != TemplatePackInstallState.NOT_READY) &&
                    (uiState.workspace?.status == TemplatePackWorkspaceStatus.PREPARED)
            ) {
                Text(stringResource(R.string.packager_pack))
            }
            TextButton(
                onClick = { onInstallArtifact(installableArtifactPath) },
                enabled = installableArtifactPath.isNotBlank() &&
                    !uiState.isLoading &&
                    !uiState.isPreparing &&
                    !uiState.isBuilding
            ) {
                Text(stringResource(R.string.packager_install))
            }
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.isPreparing || uiState.isBuilding) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            PackPageCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.packager_workspace_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.packager_workspace_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                text = when (uiState.workspace?.status) {
                                    TemplatePackWorkspaceStatus.PREPARED -> stringResource(R.string.packager_workspace_ready)
                                    else -> stringResource(R.string.packager_workspace_pending)
                                }
                            )
                        }
                    )
                }
            }

            projectSummary?.let { summary ->
                PackPageCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.packager_project_summary),
                            style = MaterialTheme.typography.titleMedium
                        )
                        SummaryRow(label = stringResource(R.string.packager_label_project), value = summary.name)
                        SummaryRow(label = stringResource(R.string.packager_label_application), value = summary.applicationLabel)
                        SummaryRow(label = stringResource(R.string.packager_label_version_name), value = summary.versionName)
                        SummaryRow(label = stringResource(R.string.packager_label_version_code), value = summary.versionCode.toString())
                        SummaryRow(
                            label = stringResource(R.string.packager_label_package),
                            value = summary.packageName.ifBlank { stringResource(R.string.packager_auto_generate_package) }
                        )
                        SummaryRow(
                            label = stringResource(R.string.packager_label_output_apk),
                            value = uiState.outputApkName.ifBlank { stringResource(R.string.packager_pending) }
                        )
                        SummaryRow(label = stringResource(R.string.packager_label_signer), value = uiState.signingSummary)
                        if (uiState.signingKeystorePath.isNotBlank()) {
                            SummaryRow(label = stringResource(R.string.packager_label_keystore), value = uiState.signingKeystorePath)
                        }
                        SummaryRow(label = stringResource(R.string.packager_label_project_id), value = summary.id)
                        SummaryRow(label = stringResource(R.string.packager_label_default_url), value = summary.defaultUrl)
                        SummaryRow(label = stringResource(R.string.packager_label_template), value = formatTemplateLabel(summary.template))
                        SummaryRow(label = stringResource(R.string.packager_label_updated), value = formatTimestamp(summary.updatedAt))
                    }
                }
            }

            PackPageCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.packager_preflight_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.packager_preflight_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(preflightStateLabel(uiState.preflight.installState)) }
                    )
                    SummaryRow(
                        label = stringResource(R.string.packager_label_output_package),
                        value = uiState.preflight.outputPackageName.ifBlank { stringResource(R.string.packager_pending) }
                    )
                    SummaryRow(
                        label = stringResource(R.string.packager_label_output_apk),
                        value = uiState.outputApkName.ifBlank { stringResource(R.string.packager_pending) }
                    )
                    SummaryRow(
                        label = stringResource(R.string.packager_label_signer),
                        value = uiState.preflight.signerSummary.ifBlank { stringResource(R.string.packager_pending) }
                    )
                    if (uiState.preflight.signerFingerprint.isNotBlank()) {
                        SummaryRow(label = stringResource(R.string.packager_label_signer_sha256), value = uiState.preflight.signerFingerprint)
                    }
                    if (uiState.preflight.installedVersionName.isNotBlank() || uiState.preflight.installedVersionCode > 0) {
                        SummaryRow(
                            label = stringResource(R.string.packager_label_installed_version),
                            value = buildString {
                                append(uiState.preflight.installedVersionName.ifBlank { stringResource(R.string.packager_dash) })
                                if (uiState.preflight.installedVersionCode > 0) {
                                    append(" (${uiState.preflight.installedVersionCode})")
                                }
                            }
                        )
                    }
                    if (uiState.preflight.installedSignerFingerprint.isNotBlank()) {
                        SummaryRow(
                            label = stringResource(R.string.packager_label_installed_signer_sha256),
                            value = uiState.preflight.installedSignerFingerprint
                        )
                    }
                    SummaryRow(
                        label = stringResource(R.string.packager_label_result),
                        value = uiState.preflight.message.ifBlank { stringResource(R.string.packager_preflight_pending_message) }
                    )
                }
            }

            PackHistoryCard(
                history = uiState.packHistory,
                onDeleteHistoryEntry = onDeleteHistoryEntry,
                onInstallArtifact = onInstallArtifact,
                onShareArtifact = onShareArtifact,
                onCopyArtifactPath = onCopyArtifactPath
            )

            uiState.workspace?.let { workspace ->
                WorkspaceCard(
                    title = stringResource(R.string.packager_status_title),
                    icon = Icons.Filled.TaskAlt,
                    rows = listOf(
                        stringResource(R.string.packager_label_status) to when (workspace.status) {
                            TemplatePackWorkspaceStatus.PREPARED -> stringResource(R.string.packager_status_prepared)
                            TemplatePackWorkspaceStatus.IDLE -> stringResource(R.string.packager_status_not_prepared)
                        },
                        stringResource(R.string.packager_label_prepared_at) to workspace.preparedAt?.let(::formatTimestamp).orEmpty(),
                        stringResource(R.string.packager_label_application) to workspace.applicationLabel,
                        stringResource(R.string.packager_label_version_name) to workspace.versionName,
                        stringResource(R.string.packager_label_version_code) to workspace.versionCode.takeIf { it > 0 }?.toString().orEmpty(),
                        stringResource(R.string.packager_label_output_package) to workspace.packageName,
                        stringResource(R.string.packager_label_output_apk) to workspace.artifactFileName,
                        stringResource(R.string.packager_label_template_source) to when (workspace.sourceType) {
                            TemplateSourceType.BUNDLED_ASSET -> stringResource(R.string.packager_template_source_bundled)
                            TemplateSourceType.INSTALLED_APP -> stringResource(R.string.packager_template_source_installed)
                        }
                    )
                )
                uiState.buildExecution.result?.let { result ->
                    WorkspaceCard(
                        title = stringResource(R.string.packager_execution_title),
                        icon = Icons.Filled.Build,
                        rows = listOf(
                            stringResource(R.string.packager_label_state) to when (result.status) {
                                TemplatePackExecutionStatus.IDLE -> stringResource(R.string.packager_execution_idle)
                                TemplatePackExecutionStatus.RUNNING -> stringResource(R.string.packager_execution_running)
                                TemplatePackExecutionStatus.SUCCEEDED -> stringResource(R.string.packager_execution_succeeded)
                                TemplatePackExecutionStatus.FAILED -> stringResource(R.string.packager_execution_failed)
                                TemplatePackExecutionStatus.BLOCKED -> stringResource(R.string.packager_execution_blocked)
                            },
                            stringResource(R.string.packager_label_message) to result.message,
                            stringResource(R.string.packager_label_artifact) to result.artifactPath.orEmpty()
                        )
                    )
                    result.artifactCheck?.let { artifactCheck ->
                        ArtifactSelfCheckCard(artifactCheck = artifactCheck)
                    }
                    ArtifactSummaryCard(
                        result = result,
                        onInstallArtifact = onInstallArtifact,
                        onShareArtifact = onShareArtifact,
                        onCopyArtifactPath = onCopyArtifactPath
                    )
                }
                WorkspaceCard(
                    title = stringResource(R.string.packager_workspace_paths_title),
                    icon = Icons.Filled.Folder,
                    rows = listOf(
                        stringResource(R.string.packager_label_pack_root) to workspace.packRootPath,
                        stringResource(R.string.packager_label_template_apk) to workspace.templateSourcePath,
                        stringResource(R.string.packager_label_unpacked_apk) to workspace.unpackedApkPath
                    )
                )
                WorkspaceCard(
                    title = stringResource(R.string.packager_inputs_title),
                    icon = Icons.Filled.Description,
                    rows = listOf(
                        stringResource(R.string.packager_label_pack_job) to workspace.packJobPath,
                        stringResource(R.string.packager_label_pack_log) to workspace.packLogPath
                    )
                )
                WorkspaceCard(
                    title = stringResource(R.string.packager_outputs_title),
                    icon = Icons.Filled.Inventory2,
                    rows = listOf(
                        stringResource(R.string.packager_label_unsigned_apk) to workspace.unsignedApkPath,
                        stringResource(R.string.packager_label_aligned_apk) to workspace.alignedApkPath,
                        stringResource(R.string.packager_label_signed_apk) to workspace.signedApkPath
                    )
                )
                PackPageCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.packager_log_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedTextField(
                            value = uiState.buildLogPreview.ifBlank { stringResource(R.string.packager_no_log_yet) },
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            minLines = 10,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                }
            }

            PackPageCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.packager_pipeline_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = stringResource(R.string.packager_pipeline_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PackPageCard(
    content: @Composable () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large
    ) {
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackHistoryCard(
    history: List<TemplatePackHistoryEntry>,
    onDeleteHistoryEntry: (TemplatePackHistoryEntry) -> Unit,
    onInstallArtifact: (String) -> Unit,
    onShareArtifact: (String) -> Unit,
    onCopyArtifactPath: (String) -> Unit
) {
    var selectedEntryKey by remember(history) {
        mutableStateOf(history.firstOrNull()?.historySelectionKey())
    }
    var pendingDeleteEntry by remember { mutableStateOf<TemplatePackHistoryEntry?>(null) }
    val selectedEntry = history.firstOrNull { it.historySelectionKey() == selectedEntryKey }
        ?: history.firstOrNull()

    PackPageCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = stringResource(R.string.packager_history_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.packager_history_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (history.isEmpty()) {
                Text(
                    text = stringResource(R.string.packager_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    history.forEach { entry ->
                        FilterChip(
                            selected = entry.historySelectionKey() == selectedEntry?.historySelectionKey(),
                            onClick = { selectedEntryKey = entry.historySelectionKey() },
                            label = { Text("${entry.versionName} (${entry.versionCode})") }
                        )
                    }
                }
                selectedEntry?.let { entry ->
                    PackHistoryEntryCard(
                        entry = entry,
                        onDelete = { pendingDeleteEntry = entry },
                        onInstallArtifact = onInstallArtifact,
                        onShareArtifact = onShareArtifact,
                        onCopyArtifactPath = onCopyArtifactPath
                    )
                }
            }
        }
    }

    pendingDeleteEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDeleteEntry = null },
            title = { Text(stringResource(R.string.packager_delete_record_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.packager_delete_record_message,
                        entry.artifactFileName.ifBlank { entry.artifactPath }
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteHistoryEntry(entry)
                        pendingDeleteEntry = null
                    }
                ) {
                    Text(stringResource(R.string.packager_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteEntry = null }) {
                    Text(stringResource(R.string.project_hub_cancel_action))
                }
            }
        )
    }
}

@Composable
private fun PackHistoryEntryCard(
    entry: TemplatePackHistoryEntry,
    onDelete: () -> Unit,
    onInstallArtifact: (String) -> Unit,
    onShareArtifact: (String) -> Unit,
    onCopyArtifactPath: (String) -> Unit
) {
    val artifactPath = entry.artifactPath
    val artifactFile = artifactPath.takeIf { it.isNotBlank() }?.let(::File)
    val artifactExists = artifactFile?.exists() == true

    PackPageCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryRow(label = stringResource(R.string.packager_label_packed), value = formatTimestamp(entry.packedAt))
            SummaryRow(
                label = stringResource(R.string.packager_label_file_name),
                value = entry.artifactFileName.ifBlank { artifactFile?.name.orEmpty() }
            )
            SummaryRow(label = stringResource(R.string.packager_label_application), value = entry.applicationLabel)
            SummaryRow(label = stringResource(R.string.packager_label_version), value = "${entry.versionName} (${entry.versionCode})")
            SummaryRow(label = stringResource(R.string.packager_label_package), value = entry.packageName)
            SummaryRow(label = stringResource(R.string.packager_label_template), value = formatTemplateLabel(entry.template))
            SummaryRow(label = stringResource(R.string.packager_label_signer), value = entry.signingSummary)
            SummaryRow(label = stringResource(R.string.packager_label_path), value = artifactPath)
            SummaryRow(
                label = stringResource(R.string.packager_label_artifact),
                value = if (artifactExists) {
                    formatFileSize(artifactFile!!.length())
                } else {
                    buildString {
                        append(stringResource(R.string.packager_missing_file))
                        if (entry.artifactSizeBytes > 0) {
                            append(" (${stringResource(R.string.packager_last_size_prefix)} ")
                            append(formatFileSize(entry.artifactSizeBytes))
                            append(')')
                        }
                    }
                }
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = { onInstallArtifact(artifactPath) },
                    enabled = artifactExists,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(stringResource(R.string.packager_install))
                }
                TextButton(
                    onClick = { onShareArtifact(artifactPath) },
                    enabled = artifactExists,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(stringResource(R.string.packager_share))
                }
                TextButton(
                    onClick = { onCopyArtifactPath(artifactPath) },
                    enabled = artifactPath.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(stringResource(R.string.packager_copy_path))
                }
                TextButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(stringResource(R.string.packager_delete))
                }
            }
        }
    }
}

@Composable
private fun WorkspaceCard(
    title: String,
    icon: ImageVector,
    rows: List<Pair<String, String>>
) {
    PackPageCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            rows.filter { it.second.isNotBlank() }.forEach { (label, value) ->
                SummaryRow(label = label, value = value)
            }
        }
    }
}

@Composable
private fun ArtifactSummaryCard(
    result: TemplatePackExecutionResult,
    onInstallArtifact: (String) -> Unit,
    onShareArtifact: (String) -> Unit,
    onCopyArtifactPath: (String) -> Unit
) {
    val artifactPath = result.artifactPath.orEmpty()
    val artifactFile = artifactPath.takeIf { it.isNotBlank() }?.let(::File)
    val artifactExists = artifactFile?.exists() == true

    PackPageCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.packager_artifact_summary_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            SummaryRow(label = stringResource(R.string.packager_label_state), value = executionStatusLabel(result.status))
            if (artifactPath.isNotBlank()) {
                SummaryRow(label = stringResource(R.string.packager_label_file_name), value = artifactFile?.name.orEmpty())
                SummaryRow(label = stringResource(R.string.packager_label_path), value = artifactPath)
                if (artifactExists) {
                    SummaryRow(label = stringResource(R.string.packager_label_file_size), value = formatFileSize(artifactFile!!.length()))
                    SummaryRow(label = stringResource(R.string.packager_label_updated), value = formatTimestamp(artifactFile.lastModified()))
                }
            } else {
                SummaryRow(label = stringResource(R.string.packager_label_artifact), value = stringResource(R.string.packager_no_apk_output_yet))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onInstallArtifact(artifactPath) },
                    enabled = artifactExists
                ) {
                    Text(stringResource(R.string.packager_install))
                }
                TextButton(
                    onClick = { onShareArtifact(artifactPath) },
                    enabled = artifactExists
                ) {
                    Text(stringResource(R.string.packager_share))
                }
                TextButton(
                    onClick = { onCopyArtifactPath(artifactPath) },
                    enabled = artifactPath.isNotBlank()
                ) {
                    Text(stringResource(R.string.packager_copy_path))
                }
            }
        }
    }
}

@Composable
private fun ArtifactSelfCheckCard(artifactCheck: TemplatePackArtifactCheck) {
    PackPageCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = stringResource(R.string.packager_artifact_check_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.packager_artifact_check_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            SummaryRow(
                label = stringResource(R.string.packager_label_self_check_status),
                value = artifactCheckStatusLabel(artifactCheck.status)
            )
            SummaryRow(
                label = stringResource(R.string.packager_label_manifest_check),
                value = artifactCheck.manifestCheck
            )
            SummaryRow(
                label = stringResource(R.string.packager_label_signature_check),
                value = artifactCheck.signatureCheck
            )
            SummaryRow(
                label = stringResource(R.string.packager_label_package_parser_check),
                value = artifactCheck.packageParserCheck
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(timestamp))
}

@Composable
private fun preflightStateLabel(state: TemplatePackInstallState): String {
    return when (state) {
        TemplatePackInstallState.NOT_READY -> stringResource(R.string.packager_preflight_not_ready)
        TemplatePackInstallState.NOT_INSTALLED -> stringResource(R.string.packager_preflight_fresh_install_ready)
        TemplatePackInstallState.UPDATE_COMPATIBLE -> stringResource(R.string.packager_preflight_update_compatible)
        TemplatePackInstallState.SIGNATURE_CONFLICT -> stringResource(R.string.packager_preflight_signature_conflict)
        TemplatePackInstallState.VERSION_DOWNGRADE -> stringResource(R.string.packager_preflight_version_downgrade)
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}

@Composable
private fun formatTemplateLabel(template: TemplateType): String {
    return when (template) {
        TemplateType.BROWSER -> stringResource(R.string.project_hub_template_browser)
        TemplateType.IMMERSIVE_SINGLE_PAGE -> stringResource(R.string.packager_template_immersive_single_page)
        TemplateType.SIDE_DRAWER -> stringResource(R.string.project_hub_template_side_drawer)
        TemplateType.TOP_BAR_TABS -> stringResource(R.string.packager_template_top_bar_tabs)
        TemplateType.TOP_BAR_BOTTOM_TABS -> stringResource(R.string.packager_template_top_bar_bottom_tabs)
        TemplateType.TOP_BAR -> stringResource(R.string.project_hub_template_top_bar)
        TemplateType.BOTTOM_BAR -> stringResource(R.string.project_hub_template_bottom_bar)
    }
}

@Composable
private fun executionStatusLabel(status: TemplatePackExecutionStatus): String {
    return when (status) {
        TemplatePackExecutionStatus.IDLE -> stringResource(R.string.packager_execution_idle)
        TemplatePackExecutionStatus.RUNNING -> stringResource(R.string.packager_execution_running)
        TemplatePackExecutionStatus.SUCCEEDED -> stringResource(R.string.packager_execution_succeeded)
        TemplatePackExecutionStatus.FAILED -> stringResource(R.string.packager_execution_failed)
        TemplatePackExecutionStatus.BLOCKED -> stringResource(R.string.packager_execution_blocked)
    }
}

@Composable
private fun artifactCheckStatusLabel(status: TemplatePackArtifactCheckStatus): String {
    return when (status) {
        TemplatePackArtifactCheckStatus.PASSED -> stringResource(R.string.packager_artifact_check_passed)
        TemplatePackArtifactCheckStatus.WARNING -> stringResource(R.string.packager_artifact_check_warning)
        TemplatePackArtifactCheckStatus.FAILED -> stringResource(R.string.packager_artifact_check_failed)
    }
}

private fun TemplatePackHistoryEntry.historySelectionKey(): String {
    return "$packedAt|$artifactPath|$artifactFileName"
}
