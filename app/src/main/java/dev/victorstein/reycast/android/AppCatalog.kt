package dev.victorstein.reycast.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.AtomicFile
import dev.victorstein.reycast.core.CatalogCache
import dev.victorstein.reycast.core.CatalogEntry
import java.io.File
import java.io.IOException

class AppCatalog(private val context: Context) {
    private val packageManager = context.packageManager
    private val cacheFile = AtomicFile(File(context.filesDir, "catalog.tsv"))

    fun cached(): List<CatalogEntry> = try {
        CatalogCache.parse(String(cacheFile.readFully()))
    } catch (e: IOException) {
        emptyList()
    }

    fun refresh(previous: List<CatalogEntry>): List<CatalogEntry> {
        val known = previous.associateBy { it.component }
        val lastUpdates = HashMap<String, Long>()
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val fresh = packageManager
            .queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
            .filter { it.activityInfo.packageName != context.packageName }
            .map { info ->
                val activity = info.activityInfo
                val component = ComponentName(activity.packageName, activity.name).flattenToShortString()
                val lastUpdate = lastUpdates.getOrPut(activity.packageName) {
                    packageManager.getPackageInfo(activity.packageName, 0).lastUpdateTime
                }
                val label = known[component]?.takeIf { it.lastUpdate == lastUpdate }?.label
                    ?: CatalogCache.cleanLabel(info.loadLabel(packageManager).toString())
                CatalogEntry(component, label, activity.packageName, lastUpdate)
            }
        write(fresh)
        return fresh
    }

    fun loadIcon(component: String): Drawable? = try {
        packageManager.getActivityIcon(ComponentName.unflattenFromString(component)!!)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun write(entries: List<CatalogEntry>) {
        val out = cacheFile.startWrite()
        try {
            out.write(CatalogCache.serialize(entries).toByteArray())
            cacheFile.finishWrite(out)
        } catch (e: IOException) {
            cacheFile.failWrite(out)
        }
    }
}
