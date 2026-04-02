package com.fireflyapp.lite.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.fireflyapp.lite.R
import com.fireflyapp.lite.app.AppLanguageManager
import com.fireflyapp.lite.core.config.AppConfigManager
import com.fireflyapp.lite.core.webview.FullscreenViewHost
import com.fireflyapp.lite.databinding.ActivityMainBinding
import com.fireflyapp.lite.ui.template.BackPressHandler
import com.fireflyapp.lite.ui.template.RuntimeSystemBarMode
import com.fireflyapp.lite.ui.template.TemplateFactory
import com.fireflyapp.lite.ui.template.TemplateSystemBarPolicy
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), FullscreenViewHost {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private var fullscreenView: View? = null
    private var renderedConfigVersion: Int = -1

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        resolveInitialNightMode()?.let { delegate.localNightMode = it }
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty()
        onBackPressedDispatcher.addCallback(this) {
            val handled = (supportFragmentManager.findFragmentById(R.id.mainFragmentContainer) as? BackPressHandler)
                ?.handleBackPressed() == true
            if (!handled) {
                finish()
            }
        }

        observeConfig()
        if (projectId.isBlank()) {
            viewModel.loadStandaloneConfig()
        } else {
            viewModel.loadProject(projectId)
        }
    }

    private fun observeConfig() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                val config = state.config ?: return@collect
                title = config.app.name
                applySystemBarMode(TemplateSystemBarPolicy.resolve(config))

                if (state.configVersion == renderedConfigVersion) {
                    return@collect
                }

                renderedConfigVersion = state.configVersion
                supportFragmentManager.beginTransaction()
                    .replace(
                        R.id.mainFragmentContainer,
                        TemplateFactory.create(config.app.template)
                    )
                    .commitAllowingStateLoss()
            }
        }
    }

    private fun resolveInitialNightMode(): Int? {
        if (intent.hasExtra(EXTRA_LOCAL_NIGHT_MODE)) {
            return intent.getIntExtra(EXTRA_LOCAL_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        return resolveProjectNightMode(
            context = applicationContext,
            projectId = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty()
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || fullscreenView != null) {
            return
        }
        applySystemBarMode(
            viewModel.uiState.value.config?.let(TemplateSystemBarPolicy::resolve)
                ?: RuntimeSystemBarMode.DEFAULT
        )
    }

    override fun showFullscreenView(view: View): Boolean {
        if (fullscreenView != null) {
            return false
        }

        fullscreenView = view
        binding.fullscreenContainer.addView(view)
        binding.fullscreenContainer.visibility = View.VISIBLE

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        return true
    }

    override fun hideFullscreenView() {
        val view = fullscreenView ?: return
        binding.fullscreenContainer.removeView(view)
        binding.fullscreenContainer.visibility = View.GONE
        fullscreenView = null

        applySystemBarMode(
            viewModel.uiState.value.config?.let(TemplateSystemBarPolicy::resolve)
                ?: RuntimeSystemBarMode.DEFAULT
        )
    }

    private fun applySystemBarMode(mode: RuntimeSystemBarMode) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            when (mode) {
                RuntimeSystemBarMode.DEFAULT -> {
                    show(WindowInsetsCompat.Type.systemBars())
                }

                RuntimeSystemBarMode.STATUS_ONLY_IMMERSIVE -> {
                    hide(WindowInsetsCompat.Type.statusBars())
                    show(WindowInsetsCompat.Type.navigationBars())
                }

                RuntimeSystemBarMode.FULLSCREEN_IMMERSIVE -> {
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        private const val EXTRA_PROJECT_ID = "project_id"
        private const val EXTRA_LOCAL_NIGHT_MODE = "local_night_mode"
        private const val PROJECTS_DIR_NAME = "projects"
        private const val PROJECT_CONFIG_FILE = "app-config.json"

        fun createIntent(context: Context, projectId: String): Intent {
            return Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_PROJECT_ID, projectId)
                resolveProjectNightMode(context.applicationContext, projectId)?.let { resolvedNightMode ->
                    putExtra(EXTRA_LOCAL_NIGHT_MODE, resolvedNightMode)
                }
            }
        }

        private fun resolveProjectNightMode(context: Context, projectId: String): Int? {
            val configManager = AppConfigManager()
            val rawNightMode = runCatching {
                if (projectId.isBlank()) {
                    configManager.load(context).browser.nightMode
                } else {
                    val configFile = context.filesDir
                        .resolve(PROJECTS_DIR_NAME)
                        .resolve(projectId)
                        .resolve(PROJECT_CONFIG_FILE)
                    if (!configFile.exists() || !configFile.isFile) {
                        return@runCatching null
                    }
                    configManager.parseAndSanitize(
                        configFile.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    ).browser.nightMode
                }
            }.getOrNull() ?: return null

            return when (rawNightMode.trim().lowercase()) {
                "on" -> AppCompatDelegate.MODE_NIGHT_YES
                "off" -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        }
    }
}
