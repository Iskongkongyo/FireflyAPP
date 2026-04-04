package com.fireflyapp.lite.ui.template

import androidx.annotation.DrawableRes
import com.fireflyapp.lite.R

data class TemplateIconSpec(
    val id: String,
    val label: String,
    val category: String,
    @DrawableRes val drawableRes: Int,
    val aliases: Set<String> = emptySet()
)

object TemplateIconCatalog {
    private val specs = listOf(
        TemplateIconSpec("back", "Back", "system", R.drawable.ic_template_back, setOf("up")),
        TemplateIconSpec("close", "Close", "system", R.drawable.ic_template_close),
        TemplateIconSpec("home", "Home", "system", R.drawable.ic_template_home),
        TemplateIconSpec("search", "Search", "system", R.drawable.ic_template_search),
        TemplateIconSpec("refresh", "Refresh", "system", R.drawable.ic_template_refresh, setOf("reload", "sync")),
        TemplateIconSpec("settings", "Settings", "system", R.drawable.ic_template_settings),
        TemplateIconSpec("menu", "Menu", "system", R.drawable.ic_template_menu),
        TemplateIconSpec("profile", "Profile", "people", R.drawable.ic_template_profile, setOf("user", "account")),
        TemplateIconSpec("location", "Location", "travel", R.drawable.ic_template_location),
        TemplateIconSpec("map", "Map", "travel", R.drawable.ic_template_map),
        TemplateIconSpec("camera", "Camera", "media", R.drawable.ic_template_camera, setOf("photo")),
        TemplateIconSpec("upload", "Upload", "files", R.drawable.ic_template_upload),
        TemplateIconSpec("download", "Download", "files", R.drawable.ic_template_download),
        TemplateIconSpec("info", "Info", "content", R.drawable.ic_template_info),
        TemplateIconSpec("docs", "Docs", "content", R.drawable.ic_template_document, setOf("document")),
        TemplateIconSpec("favorite", "Favorite", "social", R.drawable.ic_template_favorite, setOf("heart")),
        TemplateIconSpec("star", "Star", "social", R.drawable.ic_template_star),
        TemplateIconSpec("shop", "Shop", "commerce", R.drawable.ic_template_shop, setOf("store")),
        TemplateIconSpec("news", "News", "content", R.drawable.ic_template_news),
        TemplateIconSpec("video", "Video", "media", R.drawable.ic_template_video),
        TemplateIconSpec("music", "Music", "media", R.drawable.ic_template_music),
        TemplateIconSpec("chat", "Chat", "social", R.drawable.ic_template_chat, setOf("message")),
        TemplateIconSpec("calendar", "Calendar", "productivity", R.drawable.ic_template_calendar, setOf("date")),
        TemplateIconSpec("mail", "Mail", "communication", R.drawable.ic_template_mail, setOf("email")),
        TemplateIconSpec("phone", "Phone", "communication", R.drawable.ic_template_phone, setOf("call")),
        TemplateIconSpec("bookmark", "Bookmark", "productivity", R.drawable.ic_template_bookmark, setOf("save")),
        TemplateIconSpec("bell", "Bell", "communication", R.drawable.ic_template_bell, setOf("notification")),
        TemplateIconSpec("cart", "Cart", "commerce", R.drawable.ic_template_cart),
        TemplateIconSpec("gift", "Gift", "commerce", R.drawable.ic_template_gift),
        TemplateIconSpec("wallet", "Wallet", "commerce", R.drawable.ic_template_wallet, setOf("payment")),
        TemplateIconSpec("shield", "Shield", "security", R.drawable.ic_template_shield, setOf("security", "safe")),
        TemplateIconSpec("lock", "Lock", "security", R.drawable.ic_template_lock, setOf("password")),
        TemplateIconSpec("folder", "Folder", "files", R.drawable.ic_template_folder),
        TemplateIconSpec("link", "Link", "content", R.drawable.ic_template_link, setOf("url")),
        TemplateIconSpec("globe", "Globe", "travel", R.drawable.ic_template_globe, setOf("world", "web")),
        TemplateIconSpec("edit", "Edit", "productivity", R.drawable.ic_template_edit, setOf("pen", "pencil")),
        TemplateIconSpec("share", "Share", "system", R.drawable.ic_template_share),
        TemplateIconSpec("help", "Help", "system", R.drawable.ic_template_help, setOf("support")),
        TemplateIconSpec("code", "Code", "developer", R.drawable.ic_template_code),
        TemplateIconSpec("terminal", "Terminal", "developer", R.drawable.ic_template_terminal, setOf("console"))
    )

    private val aliasIndex: Map<String, TemplateIconSpec> = mutableMapOf<String, TemplateIconSpec>().apply {
        specs.forEach { spec ->
            put(spec.id, spec)
            spec.aliases.forEach { alias -> put(alias, spec) }
        }
    }

    val allSpecs: List<TemplateIconSpec>
        get() = specs

    fun find(iconName: String?): TemplateIconSpec? {
        val normalized = iconName?.trim()?.lowercase().orEmpty()
        if (normalized.isBlank()) {
            return null
        }
        return aliasIndex[normalized]
    }

    fun resolveIdOrDefault(iconName: String?, fallbackId: String): String {
        return find(iconName)?.id
            ?: find(fallbackId)?.id
            ?: "home"
    }

    @DrawableRes
    fun resolveOrDefault(iconName: String?, fallbackId: String): Int {
        return find(resolveIdOrDefault(iconName, fallbackId))?.drawableRes
            ?: R.drawable.ic_template_home
    }
}
