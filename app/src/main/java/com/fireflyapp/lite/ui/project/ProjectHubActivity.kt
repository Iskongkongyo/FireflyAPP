package com.fireflyapp.lite.ui.project

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fireflyapp.lite.BuildConfig
import com.fireflyapp.lite.R
import com.fireflyapp.lite.app.AppConfig
import com.fireflyapp.lite.app.AppLanguageManager
import com.fireflyapp.lite.app.AppLanguageMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fireflyapp.lite.data.model.ProjectSummary
import com.fireflyapp.lite.data.model.TemplateType
import com.fireflyapp.lite.ui.config.ConfigEditorActivity
import com.fireflyapp.lite.ui.help.HelpDocumentActivity
import com.fireflyapp.lite.ui.icon.IconCatalogActivity
import com.fireflyapp.lite.ui.icon.IconDesignerActivity
import com.fireflyapp.lite.ui.main.SplashActivity
import com.fireflyapp.lite.ui.pack.PackagerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProjectHubActivity : ComponentActivity() {
    private val viewModel: ProjectHubViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    viewModel.importProject(uri)
                }
            }
            val editLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                viewModel.refresh()
            }

            LaunchedEffect(state.userMessage) {
                val message = state.userMessage ?: return@LaunchedEffect
                Toast.makeText(this@ProjectHubActivity, message, Toast.LENGTH_SHORT).show()
                viewModel.consumeMessage()
            }

            LaunchedEffect(state.pendingOpenProjectId) {
                val projectId = state.pendingOpenProjectId ?: return@LaunchedEffect
                editLauncher.launch(ConfigEditorActivity.createIntent(this@ProjectHubActivity, projectId))
                viewModel.consumePendingOpenProjectId()
            }

            MaterialTheme {
                ProjectHubScreen(
                    state = state,
                    onCreateProject = viewModel::createProject,
                    onImportProject = {
                        importLauncher.launch(
                            arrayOf(
                                "application/zip",
                                "application/octet-stream",
                                "application/json",
                                "text/plain"
                            )
                        )
                    },
                    onEditProject = { projectId ->
                        editLauncher.launch(ConfigEditorActivity.createIntent(this@ProjectHubActivity, projectId))
                    },
                    onLaunchProject = { projectId ->
                        startActivity(SplashActivity.createIntent(this@ProjectHubActivity, projectId))
                    },
                    onPackProject = { project ->
                        startActivity(
                            PackagerActivity.createIntent(
                                context = this@ProjectHubActivity,
                                projectId = project.id,
                                projectName = project.name
                            )
                        )
                    },
                    onOpenIconCatalog = {
                        startActivity(IconCatalogActivity.createIntent(this@ProjectHubActivity))
                    },
                    onOpenHelpDocs = {
                        startActivity(HelpDocumentActivity.createIntent(this@ProjectHubActivity))
                    },
                    onOpenIconDesigner = {
                        startActivity(IconDesignerActivity.createIntent(this@ProjectHubActivity))
                    },
                    onDeleteProject = viewModel::deleteProject,
                    onSetDashboardVisible = viewModel::setDashboardVisible,
                    onSetLanguageMode = { mode ->
                        viewModel.setLanguageMode(mode)
                        recreate()
                    },
                    onAcceptUserAgreement = viewModel::acceptUserAgreement,
                    onRejectUserAgreement = {
                        finishAffinity()
                    },
                    onDismissUpdatePrompt = viewModel::dismissUpdatePrompt,
                    onDismissNoticePrompt = viewModel::dismissNoticePrompt
                )
            }
        }
    }
}

@Composable
private fun ProjectHubScreen(
    state: ProjectHubUiState,
    onCreateProject: () -> Unit,
    onImportProject: () -> Unit,
    onEditProject: (String) -> Unit,
    onLaunchProject: (String) -> Unit,
    onPackProject: (ProjectSummary) -> Unit,
    onOpenIconCatalog: () -> Unit,
    onOpenHelpDocs: () -> Unit,
    onOpenIconDesigner: () -> Unit,
    onDeleteProject: (String) -> Unit,
    onSetDashboardVisible: (Boolean) -> Unit,
    onSetLanguageMode: (AppLanguageMode) -> Unit,
    onAcceptUserAgreement: () -> Unit,
    onRejectUserAgreement: () -> Unit,
    onDismissUpdatePrompt: () -> Unit,
    onDismissNoticePrompt: () -> Unit
) {
    val context = LocalContext.current
    var pendingDeleteProject by remember { mutableStateOf<ProjectSummary?>(null) }
    var actionsExpanded by remember { mutableStateOf(false) }
    var aboutVisible by remember { mutableStateOf(false) }
    var languageDialogVisible by remember { mutableStateOf(false) }
    val drawerState = androidx.compose.material3.rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val showInitialLoading = state.isLoading && state.projects.isEmpty()

    if (pendingDeleteProject != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteProject = null },
            title = { Text(stringResource(R.string.project_hub_delete_title)) },
            text = {
                Text(stringResource(R.string.project_hub_delete_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val projectId = pendingDeleteProject?.id
                        pendingDeleteProject = null
                        if (projectId != null) {
                            onDeleteProject(projectId)
                        }
                    }
                ) {
                    Text(stringResource(R.string.project_hub_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteProject = null }) {
                    Text(stringResource(R.string.project_hub_cancel_action))
                }
            }
        )
    }

    if (aboutVisible) {
        AboutDialog(onDismiss = { aboutVisible = false })
    }

    if (languageDialogVisible) {
        LanguageSelectionDialog(
            selectedMode = state.languageMode,
            onDismiss = { languageDialogVisible = false },
            onSelectMode = { mode ->
                languageDialogVisible = false
                onSetLanguageMode(mode)
            }
        )
    }

    if (!state.isAgreementAccepted) {
        UserAgreementDialog(
            onAccept = onAcceptUserAgreement,
            onReject = onRejectUserAgreement
        )
    }

    state.updatePrompt?.let { updatePrompt ->
        ProjectDashboardDialog(
            onDismissRequest = {
                if (!updatePrompt.isForce) {
                    onDismissUpdatePrompt()
                }
            },
            title = stringResource(
                if (updatePrompt.isForce) {
                    R.string.project_hub_update_force_title
                } else {
                    R.string.project_hub_update_title
                }
            ),
            dismissOnBackPress = !updatePrompt.isForce,
            dismissOnClickOutside = !updatePrompt.isForce,
            confirmButton = {
                TextButton(
                    onClick = {
                        openBrowserUrl(context, updatePrompt.downloadUrl)
                        if (!updatePrompt.isForce) {
                            onDismissUpdatePrompt()
                        }
                    }
                ) {
                    Text(stringResource(R.string.project_hub_update_now))
                }
            },
            dismissButton = if (!updatePrompt.isForce) {
                {
                    TextButton(onClick = onDismissUpdatePrompt) {
                        Text(stringResource(R.string.project_hub_update_later))
                    }
                }
            } else {
                null
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        R.string.project_hub_update_current,
                        updatePrompt.currentVersionName,
                        updatePrompt.currentVersionCode
                    )
                )
                Text(
                    stringResource(
                        R.string.project_hub_update_latest,
                        updatePrompt.version,
                        updatePrompt.versionCode
                    )
                )
                if (updatePrompt.changelog.isNotBlank()) {
                    Text(updatePrompt.changelog)
                }
            }
        }
    }

    state.noticePrompt?.let { noticePrompt ->
        ProjectDashboardDialog(
            onDismissRequest = onDismissNoticePrompt,
            title = noticePrompt.title,
            confirmButton = {
                TextButton(onClick = onDismissNoticePrompt) {
                    Text(stringResource(R.string.project_hub_close))
                }
            }
        ) {
            Text(
                text = noticePrompt.content.ifBlank { stringResource(R.string.project_hub_notice_default) }
            )
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = state.isAgreementAccepted,
        drawerContent = {
            ProjectHubDrawer(
                isDashboardVisible = state.isDashboardVisible,
                languageMode = state.languageMode,
                onOpenHelpDocs = {
                    onOpenHelpDocs()
                    scope.launch { drawerState.close() }
                },
                onOpenIconDesigner = {
                    onOpenIconDesigner()
                    scope.launch { drawerState.close() }
                },
                onOpenIconCatalog = {
                    onOpenIconCatalog()
                    scope.launch { drawerState.close() }
                },
                onOpenLanguage = {
                    languageDialogVisible = true
                    scope.launch { drawerState.close() }
                },
                onToggleDashboardVisibility = {
                    onSetDashboardVisible(!state.isDashboardVisible)
                    scope.launch { drawerState.close() }
                },
                onOpenAbout = {
                    aboutVisible = true
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            if (!showInitialLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch { drawerState.open() }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.project_hub_menu_open_drawer)
                            )
                        }
                        Text(
                            text = stringResource(R.string.project_hub_title),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Box {
                            IconButton(onClick = { actionsExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.project_hub_menu_actions)
                                )
                            }
                            DropdownMenu(
                                expanded = actionsExpanded,
                                onDismissRequest = { actionsExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.project_hub_new_project)) },
                                    onClick = {
                                        actionsExpanded = false
                                        onCreateProject()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.project_hub_import_project)) },
                                    onClick = {
                                        actionsExpanded = false
                                        onImportProject()
                                    }
                                )
                            }
                        }
                    }
                    if (state.isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            if (showInitialLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@ModalNavigationDrawer
            }

            val latestProjectName = state.projects.maxByOrNull { it.updatedAt }?.name.orEmpty()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp)
            ) {
                if (state.isDashboardVisible) {
                    item {
                        WorkspaceHeroCard(
                            projectCount = state.projects.size,
                            latestProjectName = latestProjectName,
                            onCreateProject = onCreateProject,
                            onImportProject = onImportProject
                        )
                    }
                }

                if (state.projects.isEmpty()) {
                    item {
                        EmptyProjectsCard(
                            onCreateProject = onCreateProject,
                            onImportProject = onImportProject
                        )
                    }
                    return@LazyColumn
                }

                items(items = state.projects, key = { it.id }) { project ->
                    ProjectWorkspaceCard(
                        project = project,
                        onEdit = { onEditProject(project.id) },
                        onLaunch = { onLaunchProject(project.id) },
                        onPack = { onPackProject(project) },
                        onDelete = { pendingDeleteProject = project }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectHubDrawer(
    isDashboardVisible: Boolean,
    languageMode: AppLanguageMode,
    onOpenHelpDocs: () -> Unit,
    onOpenIconDesigner: () -> Unit,
    onOpenIconCatalog: () -> Unit,
    onOpenLanguage: () -> Unit,
    onToggleDashboardVisibility: () -> Unit,
    onOpenAbout: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
    ) {
        Surface(
            modifier = Modifier
                .width(AppConfig.HOST_APP_DRAWER_WIDTH_DP.dp)
                .fillMaxHeight(),
            shape = RoundedCornerShape(
                topEnd = AppConfig.HOST_APP_DRAWER_CORNER_RADIUS_DP.dp,
                bottomEnd = AppConfig.HOST_APP_DRAWER_CORNER_RADIUS_DP.dp
            ),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(workspaceDashboardGradient())
            ) {
                DrawerWallpaperHeader()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DrawerActionRow(
                        icon = Icons.Filled.Description,
                        title = stringResource(R.string.project_hub_help_docs),
                        onClick = onOpenHelpDocs
                    )
                    DrawerActionRow(
                        icon = Icons.Filled.Palette,
                        title = stringResource(R.string.project_hub_icon_designer),
                        onClick = onOpenIconDesigner
                    )
                    DrawerActionRow(
                        icon = Icons.Filled.Apps,
                        title = stringResource(R.string.project_hub_icon_catalog),
                        onClick = onOpenIconCatalog
                    )
                    DrawerActionRow(
                        icon = Icons.Filled.Translate,
                        title = "${stringResource(R.string.project_hub_language)} - ${languageMode.displayName()}",
                        onClick = onOpenLanguage
                    )
                    DrawerActionRow(
                        icon = if (isDashboardVisible) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                        title = if (isDashboardVisible) {
                            stringResource(R.string.project_hub_hide_dashboard)
                        } else {
                            stringResource(R.string.project_hub_show_dashboard)
                        },
                        onClick = onToggleDashboardVisibility
                    )
                    DrawerActionRow(
                        icon = Icons.Filled.Info,
                        title = stringResource(R.string.project_hub_about),
                        onClick = onOpenAbout
                    )
                }
                Text(
                    text = stringResource(R.string.project_hub_version_label, BuildConfig.VERSION_NAME),
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DrawerWallpaperHeader() {
    val wallpaper = rememberAssetImageBitmap(AppConfig.HOST_APP_DRAWER_WALLPAPER_ASSET_PATH)

    if (wallpaper != null) {
        Image(
            bitmap = wallpaper,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(AppConfig.HOST_APP_DRAWER_HEADER_HEIGHT_DP.dp),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppConfig.HOST_APP_DRAWER_HEADER_HEIGHT_DP.dp)
                .background(workspaceDashboardGradient())
        )
    }
}

@Composable
private fun DrawerActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun rememberAssetImageBitmap(assetPath: String): ImageBitmap? {
    val context = LocalContext.current
    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, assetPath, context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(assetPath).use { input ->
                    BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    return imageBitmap
}

@Composable
private fun WorkspaceHeroCard(
    projectCount: Int,
    latestProjectName: String,
    onCreateProject: () -> Unit,
    onImportProject: () -> Unit
) {
    ElevatedCard(shape = RoundedCornerShape(28.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(workspaceDashboardGradient())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                Text(
                    text = stringResource(R.string.project_hub_dashboard_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
//                Text(
//                    text = "Turn web projects into polished Android apps.",
//                    style = MaterialTheme.typography.headlineSmall,
//                    fontWeight = FontWeight.SemiBold
//                )
//                Text(
//                    text = "Manage branding, templates, events, packing, and release outputs from one workspace.",
//                    style = MaterialTheme.typography.bodyMedium,
//                    color = MaterialTheme.colorScheme.onSurfaceVariant
//                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricPill(
                    label = stringResource(R.string.project_hub_metric_projects),
                    value = projectCount.toString()
                )
                MetricPill(
                    label = stringResource(R.string.project_hub_metric_latest_project),
                    value = latestProjectName.ifBlank { stringResource(R.string.project_hub_empty_value) }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onCreateProject) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.project_hub_new_project))
                }
                TextButton(onClick = onImportProject) {
                    Text(stringResource(R.string.project_hub_import_package))
                }
            }
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        ElevatedCard(
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(workspaceDashboardGradient())
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.app_launcher_firefly),
                    contentDescription = null,
                    modifier = Modifier
                        .width(72.dp)
                        .height(72.dp)
                )
                Text(
                    text = context.getString(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.project_hub_about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.project_hub_about_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                AboutLinkButton(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.project_hub_about_version_title, BuildConfig.VERSION_NAME),
                    action = stringResource(R.string.project_hub_about_release_notes),
                    onClick = {
                        openExternalUrl(context, AppConfig.RELEASE_NOTES_URL)
                    }
                )
                AboutLinkButton(
                    icon = Icons.Filled.Code,
                    title = stringResource(R.string.project_hub_about_source_code),
                    action = stringResource(R.string.project_hub_github),
                    onClick = {
                        openExternalUrl(context, AppConfig.SOURCE_CODE_URL)
                    }
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.project_hub_close))
                }
            }
        }
    }
}

@Composable
private fun UserAgreementDialog(
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    ProjectDashboardDialog(
        onDismissRequest = {},
        title = stringResource(R.string.project_hub_user_agreement_title),
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(stringResource(R.string.project_hub_user_agreement_agree))
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text(stringResource(R.string.project_hub_user_agreement_disagree))
            }
        },
        dismissOnBackPress = false,
        dismissOnClickOutside = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.project_hub_user_agreement_intro),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.project_hub_user_agreement_item_1),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.project_hub_user_agreement_item_2),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.project_hub_user_agreement_item_3),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            Text(
                text = stringResource(R.string.project_hub_user_agreement_exit_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LanguageSelectionDialog(
    selectedMode: AppLanguageMode,
    onDismiss: () -> Unit,
    onSelectMode: (AppLanguageMode) -> Unit
) {
    ProjectDashboardDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.project_hub_language_dialog_title),
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.project_hub_close))
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DrawerActionRow(
                icon = Icons.Filled.Translate,
                title = selectedModeLabel(
                    mode = AppLanguageMode.FOLLOW_SYSTEM,
                    selectedMode = selectedMode
                ),
                onClick = { onSelectMode(AppLanguageMode.FOLLOW_SYSTEM) }
            )
            DrawerActionRow(
                icon = Icons.Filled.Translate,
                title = selectedModeLabel(
                    mode = AppLanguageMode.CHINESE_SIMPLIFIED,
                    selectedMode = selectedMode
                ),
                onClick = { onSelectMode(AppLanguageMode.CHINESE_SIMPLIFIED) }
            )
            DrawerActionRow(
                icon = Icons.Filled.Translate,
                title = selectedModeLabel(
                    mode = AppLanguageMode.ENGLISH,
                    selectedMode = selectedMode
                ),
                onClick = { onSelectMode(AppLanguageMode.ENGLISH) }
            )
            Text(
                text = stringResource(R.string.project_hub_language_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProjectDashboardDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside
        )
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(workspaceDashboardGradient())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                content()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

@Composable
private fun AboutLinkButton(
    icon: ImageVector,
    title: String,
    action: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "$action ->",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun workspaceDashboardGradient(): Brush {
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFFE8F5E9),
            Color(0xFFF4F0E8),
            Color(0xFFE7F0FF)
        )
    )
}

private fun openExternalUrl(context: Context, url: String) {
    val target = url.trim()
    if (target.isBlank()) {
        Toast.makeText(context, context.getString(R.string.project_hub_link_unavailable), Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.project_hub_link_open_failed), Toast.LENGTH_SHORT).show()
    }
}

private fun openBrowserUrl(context: Context, url: String) {
    val target = url.trim()
    if (target.isBlank()) {
        Toast.makeText(context, context.getString(R.string.project_hub_download_unavailable), Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.project_hub_browser_open_failed), Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
private fun EmptyProjectsCard(
    onCreateProject: () -> Unit,
    onImportProject: () -> Unit
) {
    ElevatedCard(shape = RoundedCornerShape(28.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(workspaceDashboardGradient())
                .padding(24.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.project_hub_start_first_project),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.project_hub_start_first_project_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onCreateProject) {
                    Text(stringResource(R.string.project_hub_create_project))
                }
                TextButton(onClick = onImportProject) {
                    Text(stringResource(R.string.project_hub_import_existing))
                }
            }
        }
    }
}

@Composable
private fun ProjectWorkspaceCard(
    project: ProjectSummary,
    onEdit: () -> Unit,
    onLaunch: () -> Unit,
    onPack: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(workspaceDashboardGradient())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = project.defaultUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(formatTemplateLabel(project.template)) }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailPill(
                    label = stringResource(R.string.project_hub_detail_version),
                    value = "${project.versionName} (${project.versionCode})"
                )
                DetailPill(
                    label = stringResource(R.string.project_hub_detail_package),
                    value = project.packageName.ifBlank { stringResource(R.string.project_hub_auto_package) }
                )
                DetailPill(
                    label = stringResource(R.string.project_hub_detail_updated),
                    value = formatTimestamp(project.updatedAt)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = onEdit) {
                    Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.project_hub_action_edit))
                }
                TextButton(onClick = onLaunch) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.project_hub_action_preview))
                }
                TextButton(onClick = onPack) {
                    Icon(imageVector = Icons.Filled.Build, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.project_hub_action_pack))
                }
                TextButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.project_hub_delete_action))
                }
            }
        }
    }
}

@Composable
private fun DetailPill(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) {
        return "-"
    }
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

@Composable
private fun formatTemplateLabel(template: TemplateType): String {
    return when (template) {
        TemplateType.BROWSER -> stringResource(R.string.project_hub_template_browser)
        TemplateType.IMMERSIVE_SINGLE_PAGE -> stringResource(R.string.project_hub_template_immersive)
        TemplateType.SIDE_DRAWER -> stringResource(R.string.project_hub_template_side_drawer)
        TemplateType.TOP_BAR_BOTTOM_TABS -> stringResource(R.string.project_hub_template_top_tabs)
        TemplateType.TOP_BAR -> stringResource(R.string.project_hub_template_top_bar)
        TemplateType.BOTTOM_BAR -> stringResource(R.string.project_hub_template_bottom_bar)
    }
}

@Composable
private fun AppLanguageMode.displayName(): String {
    return when (this) {
        AppLanguageMode.FOLLOW_SYSTEM -> stringResource(R.string.project_hub_language_follow_system)
        AppLanguageMode.CHINESE_SIMPLIFIED -> stringResource(R.string.project_hub_language_chinese)
        AppLanguageMode.ENGLISH -> stringResource(R.string.project_hub_language_english)
    }
}

@Composable
private fun selectedModeLabel(
    mode: AppLanguageMode,
    selectedMode: AppLanguageMode
): String {
    val suffix = if (mode == selectedMode) " *" else ""
    return mode.displayName() + suffix
}
