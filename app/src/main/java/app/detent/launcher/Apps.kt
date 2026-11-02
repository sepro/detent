package app.detent.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.concurrent.Executors

class AppEntry(
    /** Stable id: component plus user serial. */
    val key: String,
    /** The name Detent shows: the user's own name for the app, or else [systemLabel]. */
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    /** Package plus user serial. Unread dots and "last opened" times use this id. */
    val pkgUser: String,
    /** The name Android gives the app. Search still finds an app by this name after a rename. */
    val systemLabel: String = label,
) {
    val renamed get() = label != systemLabel

    fun withName(name: String?) =
        if (name == null || name == label) this else AppEntry(key, name, component, user, pkgUser, systemLabel)

    /** The name on the dial. Long names lose their tail so they fit inside the rim. */
    val display = if (label.length > 17) label.take(16).trimEnd() + "…" else label
    val initial: String = if (display.isEmpty()) "?" else String(Character.toChars(display.codePointAt(0)))
}

class AppsRepository(private val ctx: Context) {
    private val la = ctx.getSystemService(LauncherApps::class.java)
    private val um = ctx.getSystemService(UserManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    /** Launchable activities for all profiles, with the user's names applied, sorted by label. */
    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    /** The apps as Android names them. Kept so a rename can relabel and resort without a new query. */
    private var installed: List<AppEntry> = emptyList()

    /** The user's own app names by app key. Setting it relabels and resorts [apps] at once. */
    var names: Map<String, String> = emptyMap()
        set(value) {
            field = value
            apps = withNames(installed)
        }
    var loaded by mutableStateOf(false)
        private set
    var onLoaded: (() -> Unit)? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    fun start() {
        la.registerCallback(callback, main)
        reload()
    }

    fun stop() {
        la.unregisterCallback(callback)
        io.shutdownNow()
    }

    fun reload() {
        if (io.isShutdown) return
        io.execute {
            val list = runCatching { query() }.getOrDefault(emptyList())
            main.post {
                installed = list
                apps = withNames(list)
                loaded = true
                onLoaded?.invoke()
            }
        }
    }

    // A renamed app sorts by its new name, so it moves to where that name belongs.
    private fun withNames(list: List<AppEntry>) =
        list.map { it.withName(names[it.key]) }.sortedWith(compareBy({ it.label }, { it.key }))

    fun serial(user: UserHandle): Long = um.getSerialNumberForUser(user)

    private fun query(): List<AppEntry> {
        val out = ArrayList<AppEntry>()
        for (user in la.profiles) {
            val serial = serial(user)
            for (info in la.getActivityList(null, user)) {
                val cn = info.componentName
                if (cn.packageName == ctx.packageName) continue
                val label = cleanName(info.label?.toString().orEmpty()).ifEmpty { cn.packageName }
                out += AppEntry("${cn.flattenToShortString()}#$serial", label, cn, user, "${cn.packageName}#$serial")
            }
        }
        return out
    }

    /** Up to 12 everyday apps for a first dial: the default handler for each common task. */
    fun defaults(): Set<String> {
        val pm = ctx.packageManager
        val main = { category: String -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category) }
        val intents = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            main(Intent.CATEGORY_APP_CALENDAR),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            main(Intent.CATEGORY_APP_EMAIL),
            main(Intent.CATEGORY_APP_MAPS),
            main(Intent.CATEGORY_APP_MESSAGING),
            main(Intent.CATEGORY_APP_MUSIC),
            Intent(Intent.ACTION_DIAL),
            main(Intent.CATEGORY_APP_GALLERY),
            Intent(Settings.ACTION_SETTINGS),
            Intent(AlarmClock.ACTION_SHOW_ALARMS),
            main(Intent.CATEGORY_APP_CONTACTS),
        )
        val me = Process.myUserHandle()
        val keys = LinkedHashSet<String>()
        for (intent in intents) {
            val pkg = runCatching {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            }.getOrNull() ?: continue
            if (pkg == "android") continue
            apps.firstOrNull { it.component.packageName == pkg && it.user == me }?.let { keys += it.key }
        }
        for (app in apps) {
            if (keys.size >= 3) break
            keys += app.key
        }
        return keys
    }

    /** Manifest and dynamic shortcuts. Android returns them only to the default home app. */
    fun shortcuts(app: AppEntry): List<ShortcutInfo> = try {
        if (!la.hasShortcutHostPermission()) emptyList()
        else {
            val q = LauncherApps.ShortcutQuery()
                .setPackage(app.component.packageName)
                .setActivity(app.component)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC)
            la.getShortcuts(q, app.user).orEmpty()
                .filter { it.isEnabled }
                .sortedWith(compareBy({ if (it.isDeclaredInManifest) 0 else 1 }, { it.rank }))
                .take(4)
        }
    } catch (e: Exception) {
        emptyList()
    }
}

/** Detent shows every app name on one line and in lower case. Android's names and the user's own names both pass through here. */
fun cleanName(raw: String) = raw.replace(Regex("\\s+"), " ").trim().lowercase(Locale.getDefault())
