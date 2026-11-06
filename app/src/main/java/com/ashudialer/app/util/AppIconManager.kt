/*
 * Ashu Phone
 * Copyright (C) 2026 Ashutosh Nishad
 *
 * This file is part of Ashu Phone, licensed under the GNU General Public
 * License, version 3 or (at your option) any later version.
 * See the LICENSE file in the project root. This program comes with ABSOLUTELY NO WARRANTY.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.ashudialer.app.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import com.ashudialer.app.R

/** One selectable home-screen icon. [aliasName] is the simple class name of its activity-alias in AndroidManifest.xml. */
data class AppIconOption(
    val id: String,
    val label: String,
    val aliasName: String,
    @DrawableRes val preview: Int
)

/**
 * Switches the launcher icon. The manifest has one activity-alias per icon and exactly one of them is enabled; changing the
 * icon is just enabling one alias and disabling the other three (no restart, no permission needed).
 *
 * The new alias is enabled FIRST and the others are disabled afterwards, so the app never ends up without a launcher entry
 * even if something is interrupted half-way. Some launchers need a few seconds to redraw the icon.
 */
object AppIconManager {

    val options: List<AppIconOption> = listOf(
        AppIconOption("classic", "Classic Blue", "LauncherPhone", R.drawable.ic_appicon_classic),
        AppIconOption("graphite", "Graphite", "LauncherGraphite", R.drawable.ic_appicon_graphite),
        AppIconOption("pearl", "Pearl", "LauncherPearl", R.drawable.ic_appicon_pearl),
        AppIconOption("keypad", "Keypad", "LauncherKeypad", R.drawable.ic_appicon_keypad)
    )

    private fun component(context: Context, aliasName: String): ComponentName =
        ComponentName(context.packageName, "com.ashudialer.app.$aliasName")

    /** The icon that is on the home screen now. Classic is the manifest default, so it wins when nothing was ever changed. */
    fun current(context: Context): AppIconOption {
        val pm = context.packageManager
        for (option in options) {
            val state = try {
                pm.getComponentEnabledSetting(component(context, option.aliasName))
            } catch (_: Exception) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            }
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return option
        }
        return options.first()
    }

    /** Returns true when the switch went through. */
    fun apply(context: Context, option: AppIconOption): Boolean {
        val pm = context.packageManager
        return try {
            pm.setComponentEnabledSetting(
                component(context, option.aliasName),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            for (other in options) {
                if (other.id == option.id) continue
                pm.setComponentEnabledSetting(
                    component(context, other.aliasName),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
