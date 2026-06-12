package net.corn.softyk

import android.content.Context

object ApprovedApps {

    private const val PREFS_NAME = "softyk_prefs"
    private const val KEY_APPROVED = "approved_packages"

    fun isApproved(context: Context, packageName: String): Boolean =
        getAll(context).contains(packageName)

    fun approve(context: Context, packageName: String) {
        val set = getAll(context).toMutableSet().also { it.add(packageName) }
        save(context, set)
    }

    fun revoke(context: Context, packageName: String) {
        val set = getAll(context).toMutableSet().also { it.remove(packageName) }
        save(context, set)
    }

    fun getAll(context: Context): Set<String> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_APPROVED, emptySet())
            ?.toSet()   // defensive copy — never mutate the prefs-returned set directly
            ?: emptySet()

    private fun save(context: Context, set: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_APPROVED, set)
            .apply()
    }
}
