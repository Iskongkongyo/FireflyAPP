package com.fireflyapp.lite.app

object AppConfig {
    const val HOST_APP_SPLASH_ASSET_PATH = "host-app/splash_firefly.jpg"
    const val HOST_APP_SPLASH_SKIP_ENABLED = true
    const val HOST_APP_SPLASH_SKIP_SECONDS = 3
    const val HOST_APP_DRAWER_WALLPAPER_ASSET_PATH = "host-app/drawer_wallpaper.jpg"
    const val HOST_APP_DRAWER_WIDTH_DP = 240
    const val HOST_APP_DRAWER_HEADER_HEIGHT_DP = 196
    const val HOST_APP_DRAWER_CORNER_RADIUS_DP = 28

    // Host app product services.
    const val UPDATE_API_URL = "https://firefly.202132.xyz/fireflyapp/update"
    const val NOTIFICATION_API_URL = "https://firefly.202132.xyz/fireflyapp/notice"
    const val HOST_API_CONNECT_TIMEOUT_MS = 8000
    const val HOST_API_READ_TIMEOUT_MS = 8000
    const val HOST_API_MAX_ATTEMPTS = 2

    const val RELEASE_NOTES_URL = "https://github.com/Iskongkongyo/FireflyApp/releases"
    const val SOURCE_CODE_URL = "https://github.com/Iskongkongyo/FireflyApp"
}
