package com.fireflyapp.lite.core.config

import android.content.Context
import android.net.Uri
import com.fireflyapp.lite.data.model.AppConfig

class AppConfigManager(
    private val parser: ConfigParser = ConfigParser(),
    private val validator: ConfigValidator = ConfigValidator()
) {
    fun load(context: Context): AppConfig {
        return parseAndSanitize(loadRaw(context))
    }

    fun loadFromAssets(context: Context, fileName: String = DEFAULT_CONFIG_FILE): AppConfig {
        val rawConfig = context.assets.open(fileName).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parseAndSanitize(rawConfig)
    }

    fun loadRaw(context: Context): String {
        val userConfigFile = context.filesDir.resolve(USER_CONFIG_FILE)
        if (userConfigFile.exists()) {
            return userConfigFile.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        return context.assets.open(DEFAULT_CONFIG_FILE).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun save(context: Context, config: AppConfig): AppConfig {
        val sanitized = validator.sanitize(config)
        saveRawToUserStorage(context, parser.stringify(sanitized))
        return sanitized
    }

    fun saveRaw(context: Context, rawConfig: String): AppConfig {
        val sanitized = parseAndSanitize(rawConfig)
        saveRawToUserStorage(context, parser.stringify(sanitized))
        return sanitized
    }

    fun reset(context: Context) {
        val userConfigFile = context.filesDir.resolve(USER_CONFIG_FILE)
        if (userConfigFile.exists()) {
            userConfigFile.delete()
        }
    }

    fun export(context: Context, uri: Uri, rawConfig: String) {
        context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
            writer.write(rawConfig)
            writer.flush()
        } ?: error("Unable to open export target")
    }

    fun import(context: Context, uri: Uri): AppConfig {
        val rawConfig = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("Unable to read imported config")
        return saveRaw(context, rawConfig)
    }

    fun stringify(config: AppConfig): String {
        return parser.stringify(validator.sanitize(config))
    }

    fun defaultConfig(): AppConfig = validator.defaultConfig()

    fun parseAndSanitize(rawConfig: String): AppConfig {
        return validator.sanitize(parser.parse(rawConfig))
    }

    private fun saveRawToUserStorage(context: Context, rawConfig: String) {
        val userConfigFile = context.filesDir.resolve(USER_CONFIG_FILE)
        userConfigFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(rawConfig)
            writer.flush()
        }
    }

    private companion object {
        const val DEFAULT_CONFIG_FILE = "app-config.json"
        const val USER_CONFIG_FILE = "app-config.user.json"
    }
}
