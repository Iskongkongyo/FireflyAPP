package com.fireflyapp.lite.core.config

import com.fireflyapp.lite.data.model.AppConfig
import kotlinx.serialization.json.Json

class ConfigParser {
    private val parserJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }
    private val serializerJson = Json {
        prettyPrint = true
        explicitNulls = false
    }

    fun parse(rawConfig: String): AppConfig {
        return parserJson.decodeFromString(AppConfig.serializer(), rawConfig)
    }

    fun stringify(config: AppConfig): String {
        return serializerJson.encodeToString(AppConfig.serializer(), config)
    }
}
