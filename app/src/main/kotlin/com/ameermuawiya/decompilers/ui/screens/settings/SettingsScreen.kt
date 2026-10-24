/*
 * Apk Decompiler Pro
 * Copyright (C) 2026 Ameer Muawiya
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.ameermuawiya.decompilers.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import com.ameermuawiya.decompilers.R
import com.ameermuawiya.decompilers.activities.explorer.viewer.CodeViewerActivity
import com.ameermuawiya.decompilers.activities.settings.SettingsHandler
import com.ameermuawiya.decompilers.managers.ReleaseInfo
import com.ameermuawiya.decompilers.managers.UpdateBottomSheet
import com.ameermuawiya.decompilers.managers.UpdateManager
import com.ameermuawiya.decompilers.ui.components.CardGroupPosition
import com.ameermuawiya.decompilers.ui.components.getGroupedCardShape
import com.ameermuawiya.decompilers.utils.UserPreferences
import com.ameermuawiya.decompilers.utils.ktx.defaultAppStorage
import com.ameermuawiya.decompilers.utils.ktx.getPathFromTreeUri
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/*
 * Displays application settings grouped into clean categories with rounded circular icon badges.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentThemeMode: String,
    onThemeModeChange: (String) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember {
        UserPreferences(context.getSharedPreferences(UserPreferences.NAME, Context.MODE_PRIVATE))
    }
    val rawPrefs = remember {
        context.getSharedPreferences(UserPreferences.NAME, Context.MODE_PRIVATE)
    }

    var ignoreLibraries by remember { mutableStateOf(prefs.ignoreLibraries) }
    var keepIntermediate by remember { mutableStateOf(prefs.keepIntermediateFiles) }
    var showMemoryUsage by remember { mutableStateOf(prefs.showMemoryUsage) }
    var workingDirectoryPath by remember {
        mutableStateOf(prefs.workingDirectoryPath.ifBlank { defaultAppStorage.absolutePath })
    }

    val scope = rememberCoroutineScope()
    var availableUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }

    var showMemoryLimitDialog by remember { mutableStateOf(false) }
    var maxMemoryMbState by remember { mutableIntStateOf(prefs.maxMemoryMb) }
    val currentVersionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (ignored: Exception) {
            }
            val resolved = getPathFromTreeUri(context, uri) ?: uri.path ?: ""
            if (resolved.isNotBlank()) {
                rawPrefs.edit()
                    .putString("workingDirectoryUri", uri.toString())
                    .putString("workingDirectoryPath", resolved)
                    .apply()
                workingDirectoryPath = resolved
            }
        }
    }

    var showClearSourceDialog by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings),
                        fontWeight = FontWeight.Bold
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            SectionHeader(title = stringResource(R.string.settings_screen_cat_appearance))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(getGroupedCardShape(CardGroupPosition.FIRST)),
                    shape = getGroupedCardShape(CardGroupPosition.FIRST),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(
                                icon = Icons.Rounded.Brightness4,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = stringResource(R.string.settings_screen_theme_mode),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val themes = listOf(
                                "system" to stringResource(R.string.settings_screen_theme_system),
                                "light" to stringResource(R.string.settings_screen_theme_light),
                                "dark" to stringResource(R.string.settings_screen_theme_dark)
                            )
                            themes.forEach { (mode, label) ->
                                val selected = currentThemeMode == mode
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        onThemeModeChange(mode)
                                        when (mode) {
                                            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                                            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                                            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                                        }
                                    },
                                    label = { Text(label) },
                                    shape = RoundedCornerShape(8.dp),
                                    border = null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                }

                SettingCardRow(
                    title = stringResource(R.string.settings_screen_dynamic_color),
                    subtitle = stringResource(R.string.settings_screen_dynamic_color_sub),
                    checked = dynamicColor,
                    onCheckedChange = { onDynamicColorChange(it) },
                    position = CardGroupPosition.LAST,
                    icon = Icons.Rounded.ColorLens,
                    badgeColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            SectionHeader(title = stringResource(R.string.settings_cat_performance))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SettingCardRow(
                    title = stringResource(R.string.speedUpDecompile),
                    subtitle = stringResource(R.string.ignoreLibrariesPreferenceSummary),
                    checked = ignoreLibraries,
                    onCheckedChange = {
                        ignoreLibraries = it
                        rawPrefs.edit().putBoolean("ignoreLibraries", it).apply()
                    },
                    position = if (ignoreLibraries) CardGroupPosition.FIRST else CardGroupPosition.SINGLE,
                    icon = Icons.Rounded.Bolt,
                    badgeColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )

                AnimatedVisibility(
                    visible = ignoreLibraries,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    SettingClickableCardRow(
                        title = stringResource(R.string.settings_ignored_libs_title),
                        subtitle = stringResource(R.string.settings_ignored_libs_summary),
                        onClick = {
                            val file = context.filesDir.resolve("ignored_packages.txt")
                            if (!file.exists()) {
                                try {
                                    val content = if (prefs.customIgnoredLibsString.isNotBlank()) {
                                        prefs.customIgnoredLibsString
                                    } else {
                                        context.assets.open("ignored.list").bufferedReader().use { it.readText() }
                                    }
                                    file.writeText(content)
                                } catch (e: Exception) {
                                }
                            }
                            val intent = Intent(context, CodeViewerActivity::class.java).apply {
                                putExtra("filePath", file.absolutePath)
                                putExtra("name", context.getString(R.string.settings_ignored_libs_title))
                                putExtra("isIgnoreList", true)
                                putExtra("extra_show_copy", false)
                                putExtra("extra_show_share", false)
                                putExtra("extra_show_open_external", false)
                                putExtra("extra_start_edit_mode", true)
                            }
                            context.startActivity(intent)
                        },
                        position = CardGroupPosition.LAST,
                        icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                        badgeColor = MaterialTheme.colorScheme.primaryContainer,
                        iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            SectionHeader(title = stringResource(R.string.settings_cat_artifacts))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SettingCardRow(
                    title = stringResource(R.string.keepIntermediateFiles),
                    subtitle = stringResource(R.string.keepIntermediateFilesSummary),
                    checked = keepIntermediate,
                    onCheckedChange = {
                        keepIntermediate = it
                        rawPrefs.edit().putBoolean("keepIntermediateFiles", it).apply()
                    },
                    position = CardGroupPosition.SINGLE,
                    icon = Icons.Rounded.FolderZip,
                    badgeColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            SectionHeader(title = stringResource(R.string.settings_cat_memory))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SettingCardRow(
                    title = stringResource(R.string.showMemoryUsage),
                    subtitle = stringResource(R.string.showMemoryUsageSummary),
                    checked = showMemoryUsage,
                    onCheckedChange = {
                        showMemoryUsage = it
                        rawPrefs.edit().putBoolean("showMemoryUsage", it).apply()
                    },
                    position = CardGroupPosition.FIRST,
                    icon = Icons.Rounded.Memory,
                    badgeColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )

                val memorySummary = if (maxMemoryMbState == 0) {
                    stringResource(R.string.settings_screen_memory_auto_summary)
                } else {
                    stringResource(R.string.settings_memory_limit_summary, maxMemoryMbState)
                }
                SettingClickableCardRow(
                    title = stringResource(R.string.settings_memory_limit_title),
                    subtitle = memorySummary,
                    onClick = { showMemoryLimitDialog = true },
                    position = CardGroupPosition.LAST,
                    icon = Icons.Rounded.SdCard,
                    badgeColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            SectionHeader(title = stringResource(R.string.settings_cat_storage))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SettingClickableCardRow(
                    title = stringResource(R.string.settings_working_dir_title),
                    subtitle = workingDirectoryPath,
                    onClick = { folderPickerLauncher.launch(null) },
                    position = CardGroupPosition.FIRST,
                    icon = Icons.Rounded.Folder,
                    badgeColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )

                SettingClickableCardRow(
                    title = stringResource(R.string.settings_reset_dir_title),
                    subtitle = stringResource(R.string.settings_reset_dir_summary),
                    onClick = {
                        rawPrefs.edit()
                            .remove("workingDirectoryUri")
                            .remove("workingDirectoryPath")
                            .apply()
                        workingDirectoryPath = defaultAppStorage.absolutePath
                        Toast.makeText(context, context.getString(R.string.settings_dir_reset_toast), Toast.LENGTH_SHORT).show()
                    },
                    position = CardGroupPosition.LAST,
                    icon = Icons.Rounded.RestartAlt,
                    badgeColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            SectionHeader(title = stringResource(R.string.settings_cat_data))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SettingClickableCardRow(
                    title = stringResource(R.string.clearSourceHistory),
                    subtitle = stringResource(R.string.clearSourceHistorySummary),
                    onClick = { showClearSourceDialog = true },
                    position = CardGroupPosition.SINGLE,
                    icon = Icons.Rounded.Delete,
                    badgeColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            SectionHeader(title = stringResource(R.string.settings_screen_community_title))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SettingClickableCardRow(
                    title = stringResource(R.string.settings_screen_github_title),
                    subtitle = stringResource(R.string.settings_screen_github_subtitle),
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AmeerMuawiya/APK-Decompiler-Pro"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.settings_screen_toast_open_browser_error), Toast.LENGTH_SHORT).show()
                        }
                    },
                    position = CardGroupPosition.FIRST,
                    iconRes = R.drawable.ic_github,
                    badgeColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )

                SettingClickableCardRow(
                    title = stringResource(R.string.settings_screen_telegram_title),
                    subtitle = stringResource(R.string.settings_screen_telegram_subtitle),
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/itx__muawiya"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.settings_screen_toast_open_telegram_error), Toast.LENGTH_SHORT).show()
                        }
                    },
                    position = CardGroupPosition.MIDDLE,
                    iconRes = R.drawable.ic_telegram,
                    badgeColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )

                SettingClickableCardRow(
                    title = stringResource(R.string.settings_screen_coffee_title),
                    subtitle = stringResource(R.string.settings_screen_coffee_subtitle),
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.patreon.com/ameermuawiyapk/posts/buy-me-coffee-16891048"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.settings_screen_toast_open_link_error), Toast.LENGTH_SHORT).show()
                        }
                    },
                    position = CardGroupPosition.MIDDLE,
                    icon = Icons.Rounded.Coffee,
                    badgeColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )

                SettingClickableCardRow(
                    title = stringResource(R.string.settings_screen_check_update_title),
                    subtitle = stringResource(R.string.settings_screen_check_update_subtitle, currentVersionName),
                    onClick = {
                        var checkingToast: Toast? = null
                        UpdateManager.checkForUpdates(
                            context = context,
                            scope = scope,
                            isManual = true,
                            onChecking = {
                                checkingToast = Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_screen_checking_update),
                                    Toast.LENGTH_SHORT
                                )
                                checkingToast?.show()
                            },
                            onUpdateAvailable = { releaseInfo ->
                                checkingToast?.cancel()
                                availableUpdate = releaseInfo
                            },
                            onNoUpdate = { currentVer ->
                                checkingToast?.cancel()
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_screen_app_up_to_date_version, currentVer),
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            onNoReleaseFound = {
                                checkingToast?.cancel()
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_screen_update_no_release),
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            onError = { errorMsg ->
                                checkingToast?.cancel()
                                Toast.makeText(
                                    context,
                                    errorMsg,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    },
                    position = CardGroupPosition.LAST,
                    icon = Icons.Rounded.RestartAlt,
                    badgeColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showClearSourceDialog) {
        AlertDialog(
            onDismissRequest = { showClearSourceDialog = false },
            title = { Text(stringResource(R.string.deleteSourceHistory)) },
            text = { Text(stringResource(R.string.deleteSourceHistoryConfirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearSourceDialog = false
                        SettingsHandler(context).deleteHistory()
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                { Toast.makeText(context, R.string.sourceHistoryDeleted, Toast.LENGTH_SHORT).show() },
                                { err -> Toast.makeText(context, context.getString(R.string.settings_screen_toast_delete_failed, err.localizedMessage ?: ""), Toast.LENGTH_SHORT).show() }
                            )
                    }
                ) {
                    Text(stringResource(R.string.deleteSource), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSourceDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }



    if (showMemoryLimitDialog) {
        val memoryOptions = listOf(
            0 to stringResource(R.string.settings_screen_memory_auto),
            512 to stringResource(R.string.settings_screen_memory_512mb),
            1024 to stringResource(R.string.settings_screen_memory_1gb),
            2048 to stringResource(R.string.settings_screen_memory_2gb),
            4096 to stringResource(R.string.settings_screen_memory_4gb),
            8192 to stringResource(R.string.settings_screen_memory_8gb)
        )

        ModalBottomSheet(
            onDismissRequest = { showMemoryLimitDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_memory_limit_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    memoryOptions.forEachIndexed { index, pair ->
                        val (mb, label) = pair
                        val total = memoryOptions.size
                        val pos = when {
                            total == 1 -> CardGroupPosition.SINGLE
                            index == 0 -> CardGroupPosition.FIRST
                            index == total - 1 -> CardGroupPosition.LAST
                            else -> CardGroupPosition.MIDDLE
                        }
                        val itemShape = getGroupedCardShape(pos)
                        val isSelected = maxMemoryMbState == mb

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(itemShape)
                                .clickable {
                                    maxMemoryMbState = mb
                                    rawPrefs.edit().putInt("maxMemoryMb", mb).apply()
                                },
                            shape = itemShape,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        maxMemoryMbState = mb
                                        rawPrefs.edit().putInt("maxMemoryMb", mb).apply()
                                    }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (mb == 0) {
                                        Text(
                                            text = stringResource(R.string.settings_screen_memory_auto_summary),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    availableUpdate?.let { updateInfo ->
        UpdateBottomSheet(
            releaseInfo = updateInfo,
            onDismiss = { availableUpdate = null }
        )
    }
}

/*
 * Displays a category header for grouping preference cards.
 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

/*
 * Displays a circular badge container containing an icon.
 */
@Composable
private fun IconBadge(
    icon: ImageVector? = null,
    iconRes: Int? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/*
 * Renders a switch row inside a grouped card container.
 */
@Composable
private fun SettingCardRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    position: CardGroupPosition,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val cardShape = getGroupedCardShape(position)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable { onCheckedChange(!checked) },
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = icon,
                iconRes = iconRes,
                containerColor = badgeColor,
                iconColor = iconColor
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

/*
 * Renders a clickable action row inside a grouped card container.
 */
@Composable
private fun SettingClickableCardRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    position: CardGroupPosition,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val cardShape = getGroupedCardShape(position)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(onClick = onClick),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = icon,
                iconRes = iconRes,
                containerColor = badgeColor,
                iconColor = iconColor
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
