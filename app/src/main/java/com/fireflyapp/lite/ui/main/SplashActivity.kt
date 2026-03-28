package com.fireflyapp.lite.ui.main

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.fireflyapp.lite.BuildConfig
import com.fireflyapp.lite.R
import com.fireflyapp.lite.app.AppConfig
import com.fireflyapp.lite.app.AppLanguageManager
import com.fireflyapp.lite.data.repository.ConfigRepository
import com.fireflyapp.lite.databinding.ActivitySplashBinding
import com.fireflyapp.lite.ui.project.ProjectHubActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding
    private val repository by lazy { ConfigRepository(applicationContext) }
    private val json = Json { ignoreUnknownKeys = true }
    private var launchedMain = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyImmersiveMode()

        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty()
        lifecycleScope.launch {
            val splashConfig = loadSplashConfig(projectId)
            if (splashConfig?.bitmap == null) {
                launchMain(projectId)
                return@launch
            }

            binding.splashImage.setImageBitmap(splashConfig.bitmap)
            if (splashConfig.skipEnabled) {
                binding.skipButton.text = getString(R.string.splash_skip_countdown, splashConfig.skipSeconds)
                binding.skipButton.visibility = View.VISIBLE
                binding.skipButton.setOnClickListener { launchMain(projectId) }
            } else {
                binding.skipButton.visibility = View.GONE
                binding.skipButton.setOnClickListener(null)
            }

            for (remainingSeconds in splashConfig.skipSeconds downTo 1) {
                if (launchedMain) {
                    return@launch
                }
                if (splashConfig.skipEnabled) {
                    binding.skipButton.text = getString(R.string.splash_skip_countdown, remainingSeconds)
                }
                delay(1000L)
            }
            launchMain(projectId)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode()
        }
    }

    override fun onDestroy() {
        if (::binding.isInitialized) {
            binding.splashImage.setImageDrawable(null)
        }
        super.onDestroy()
    }

    private suspend fun loadSplashConfig(projectId: String): LoadedSplashConfig? {
        if (projectId.isBlank()) {
            return withContext(Dispatchers.IO) {
                if (BuildConfig.IS_WORKSPACE_HOST_APP) {
                    decodeHostAppSplash()
                } else {
                    decodePackagedSplash()
                }
            }
        }
        val projectManifest = repository.loadProjectManifest(projectId).getOrNull() ?: return null
        return withContext(Dispatchers.IO) {
            val bitmap = decodeProjectSplash(
                projectId = projectId,
                splashMode = projectManifest.branding.splashMode,
                splashPath = projectManifest.branding.splashPath
            ) ?: return@withContext null
            LoadedSplashConfig(
                bitmap = bitmap,
                skipEnabled = projectManifest.branding.splashSkipEnabled,
                skipSeconds = projectManifest.branding.splashSkipSeconds.coerceIn(
                    MIN_SPLASH_SKIP_SECONDS,
                    MAX_SPLASH_SKIP_SECONDS
                )
            )
        }
    }

    private fun decodeProjectSplash(projectId: String, splashMode: String, splashPath: String): Bitmap? {
        if (splashMode != BRANDING_MODE_CUSTOM) {
            return null
        }
        val relativePath = splashPath.trim()
        if (relativePath.isBlank()) {
            return null
        }
        val splashFile = applicationContext.filesDir
            .resolve(PROJECTS_DIR_NAME)
            .resolve(projectId)
            .resolve(relativePath)
        if (!splashFile.exists() || !splashFile.isFile) {
            return null
        }
        return BitmapFactory.decodeFile(splashFile.absolutePath)
    }

    private fun decodePackagedSplash(): LoadedSplashConfig? {
        val bitmap = runCatching {
            assets.open(PACKAGED_SPLASH_ASSET_PATH).use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull() ?: return null

        val splashConfig = runCatching {
            assets.open(PACKAGED_SPLASH_CONFIG_PATH).use { input ->
                json.decodeFromString<PackagedSplashConfig>(
                    input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                )
            }
        }.getOrDefault(PackagedSplashConfig())

        return LoadedSplashConfig(
            bitmap = bitmap,
            skipEnabled = splashConfig.skipEnabled,
            skipSeconds = splashConfig.skipSeconds.coerceIn(MIN_SPLASH_SKIP_SECONDS, MAX_SPLASH_SKIP_SECONDS)
        )
    }

    private fun decodeHostAppSplash(): LoadedSplashConfig? {
        val bitmap = runCatching {
            assets.open(AppConfig.HOST_APP_SPLASH_ASSET_PATH).use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull() ?: return null

        return LoadedSplashConfig(
            bitmap = bitmap,
            skipEnabled = AppConfig.HOST_APP_SPLASH_SKIP_ENABLED,
            skipSeconds = AppConfig.HOST_APP_SPLASH_SKIP_SECONDS.coerceIn(
                MIN_SPLASH_SKIP_SECONDS,
                MAX_SPLASH_SKIP_SECONDS
            )
        )
    }

    private fun launchMain(projectId: String) {
        if (launchedMain || isFinishing) {
            return
        }
        launchedMain = true
        if (projectId.isBlank() && BuildConfig.IS_WORKSPACE_HOST_APP) {
            showWorkspaceSystemBars()
            startActivity(Intent(this, ProjectHubActivity::class.java))
        } else {
            startActivity(MainActivity.createIntent(this, projectId))
        }
        finish()
        overridePendingTransition(0, 0)
    }

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    private fun showWorkspaceSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            show(WindowInsetsCompat.Type.statusBars())
            show(WindowInsetsCompat.Type.navigationBars())
        }
    }

    companion object {
        private const val EXTRA_PROJECT_ID = "project_id"
        private const val PROJECTS_DIR_NAME = "projects"
        private const val PACKAGED_SPLASH_ASSET_PATH = "branding/splash-image"
        private const val PACKAGED_SPLASH_CONFIG_PATH = "branding/splash-config.json"
        private const val BRANDING_MODE_CUSTOM = "custom"
        private const val DEFAULT_SPLASH_SKIP_SECONDS = 3
        private const val MIN_SPLASH_SKIP_SECONDS = 1
        private const val MAX_SPLASH_SKIP_SECONDS = 15

        fun createIntent(context: Context, projectId: String): Intent {
            return Intent(context, SplashActivity::class.java).apply {
                putExtra(EXTRA_PROJECT_ID, projectId)
            }
        }
    }

    private data class LoadedSplashConfig(
        val bitmap: Bitmap,
        val skipEnabled: Boolean,
        val skipSeconds: Int
    )

    @Serializable
    private data class PackagedSplashConfig(
        val skipEnabled: Boolean = true,
        val skipSeconds: Int = DEFAULT_SPLASH_SKIP_SECONDS
    )
}
