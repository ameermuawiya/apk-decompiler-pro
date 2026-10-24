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

package com.ameermuawiya.decompilers.ui.screens.home

import android.content.Intent
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import com.ameermuawiya.decompilers.activities.explorer.navigator.NavigatorActivity
import com.ameermuawiya.decompilers.data.SourceInfo
import com.ameermuawiya.decompilers.utils.ktx.hasDecompiledSourcesForPackage
import com.ameermuawiya.decompilers.utils.ktx.findLatestSourceDirForPackage
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ameermuawiya.decompilers.R
import com.ameermuawiya.decompilers.activities.decompiler.DecompilerActivity
import com.ameermuawiya.decompilers.data.PackageInfo
import com.ameermuawiya.decompilers.managers.DecompilerTaskManager
import com.ameermuawiya.decompilers.ui.components.CardGroupPosition
import com.ameermuawiya.decompilers.ui.components.ExpressiveLoadingIndicator
import com.ameermuawiya.decompilers.ui.components.ExpressivePullToRefreshBox
import com.ameermuawiya.decompilers.ui.components.getGroupedCardShape
import com.ameermuawiya.decompilers.utils.ApkUtils
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils

/*
 * Displays installed app lists, segmented category tabs, and option bottom sheets matching reference UI.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onPickFileClick: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedAppForSheet by remember { mutableStateOf<PackageInfo?>(null) }
    var isExtractingApk by remember { mutableStateOf(false) }
    var showTasksBottomSheet by remember { mutableStateOf(false) }
    var collisionRunningTask by remember { mutableStateOf<DecompilerTaskManager.TrackedTask?>(null) }

    val userAppsListState = rememberLazyListState()
    val systemAppsListState = rememberLazyListState()

    val trackedTasks by DecompilerTaskManager.trackedTasks.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadInstalledApps(context)
    }

    val currentApps = if (uiState.selectedTab == 0) uiState.userApps else uiState.systemApps
    val filteredApps = if (uiState.searchQuery.isBlank()) {
        currentApps
    } else {
        currentApps.filter {
            it.label.contains(uiState.searchQuery, ignoreCase = true) ||
                    it.name.contains(uiState.searchQuery, ignoreCase = true)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumTopAppBar(
                title = { Text(text = stringResource(R.string.home_screen_title), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                actions = {
                    if (trackedTasks.isNotEmpty()) {
                        val hasRunning = trackedTasks.any { it.state == DecompilerTaskManager.TaskState.RUNNING }
                        FilledTonalButton(
                            onClick = { showTasksBottomSheet = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            if (hasRunning) {
                                ExpressiveLoadingIndicator(
                                    size = 18.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.home_screen_task_processing),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = stringResource(R.string.home_screen_task_completed),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.home_screen_task_completed),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onPickFileClick,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.home_screen_select_file)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 16.dp)
        ) {
            TextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text(text = stringResource(R.string.home_screen_search_hint)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            PrimaryTabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(uiState.selectedTab),
                        width = 36.dp,
                        height = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                    )
                },
                divider = {}
            ) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.onTabSelected(0) },
                    text = { Text(text = stringResource(R.string.home_screen_tab_user_apps)) }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.onTabSelected(1) },
                    text = { Text(text = stringResource(R.string.home_screen_tab_system_apps)) }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            ExpressivePullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refreshInstalledApps(context) },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxSize()
            ) {
                if (uiState.isLoading && filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ExpressiveLoadingIndicator()
                    }
                } else if (filteredApps.isEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(80.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Apps,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                Text(
                                    text = stringResource(R.string.home_screen_empty_title),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = stringResource(R.string.home_screen_empty_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = if (uiState.selectedTab == 0) userAppsListState else systemAppsListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 2.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(
                            items = filteredApps,
                            key = { index, app -> "${app.name}_${app.filePath}_$index" }
                        ) { index, app ->
                            val total = filteredApps.size
                            val position = when {
                                total == 1 -> CardGroupPosition.SINGLE
                                index == 0 -> CardGroupPosition.FIRST
                                index == total - 1 -> CardGroupPosition.LAST
                                else -> CardGroupPosition.MIDDLE
                            }

                            AppCardItem(
                                app = app,
                                position = position,
                                onClick = {
                                    val runningTask = trackedTasks.firstOrNull { it.state == DecompilerTaskManager.TaskState.RUNNING }
                                    if (runningTask != null && runningTask.packageInfo.name != app.name) {
                                        collisionRunningTask = runningTask
                                    } else {
                                        val intent = Intent(context, DecompilerActivity::class.java).apply {
                                            putExtra("packageInfo", app)
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                                onLongClick = { selectedAppForSheet = app }
                            )
                        }
                    }
                }
            }
        }
    }

    selectedAppForSheet?.let { app ->
        val formattedSize = remember(app.file.length()) {
            FileUtils.byteCountToDisplaySize(app.file.length())
        }
        val typeName = if (app.isSplitApk) {
            stringResource(R.string.apps_type_apks)
        } else {
            stringResource(R.string.apps_type_apk)
        }

        ModalBottomSheet(
            onDismissRequest = { selectedAppForSheet = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_options_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(14.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var iconDrawable by remember { mutableStateOf<Drawable?>(null) }
                        LaunchedEffect(app) {
                            iconDrawable = app.loadIcon(context)
                        }

                        if (iconDrawable != null) {
                            AsyncImage(
                                model = iconDrawable,
                                contentDescription = app.label,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = app.label,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = app.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "v${app.version} • $formattedSize",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val firstPosShape = getGroupedCardShape(CardGroupPosition.FIRST)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(firstPosShape)
                            .clickable {
                                selectedAppForSheet = null
                                val intent = Intent(context, DecompilerActivity::class.java)
                                intent.putExtra("packageInfo", app)
                                context.startActivity(intent)
                            },
                        shape = firstPosShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = stringResource(R.string.apps_action_decompile),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    val extractShape = getGroupedCardShape(CardGroupPosition.MIDDLE)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(extractShape)
                            .clickable {
                                selectedAppForSheet = null
                                isExtractingApk = true
                                scope.launch {
                                    val file = ApkUtils.extractPackage(context, app)
                                    isExtractingApk = false
                                    if (file != null) {
                                        Toast.makeText(context, context.getString(R.string.apps_extract_success, typeName), Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.apps_extract_error, typeName), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        shape = extractShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = stringResource(R.string.apps_action_extract, typeName),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    val shareShape = getGroupedCardShape(CardGroupPosition.LAST)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shareShape)
                            .clickable {
                                selectedAppForSheet = null
                                ApkUtils.sharePackage(context, app)
                            },
                        shape = shareShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = stringResource(R.string.apps_action_share, typeName),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (isExtractingApk) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.home_screen_extracting_title)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.home_screen_extracting_msg))
                }
            },
            confirmButton = {}
        )
    }

    if (showTasksBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTasksBottomSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_screen_active_tasks_sheet_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (trackedTasks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.home_screen_active_tasks_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(
                            items = trackedTasks,
                            key = { index, t -> "${t.packageInfo.name}_$index" }
                        ) { index, task ->
                            val total = trackedTasks.size
                            val pos = when {
                                total == 1 -> CardGroupPosition.SINGLE
                                index == 0 -> CardGroupPosition.FIRST
                                index == total - 1 -> CardGroupPosition.LAST
                                else -> CardGroupPosition.MIDDLE
                            }
                            val cardShape = getGroupedCardShape(pos)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(cardShape)
                                    .clickable {
                                        showTasksBottomSheet = false
                                        if (task.state == DecompilerTaskManager.TaskState.COMPLETED) {
                                            DecompilerTaskManager.markTaskAsViewed(task.packageInfo.name)
                                        }
                                        val intent = Intent(context, DecompilerActivity::class.java).apply {
                                            putExtra("packageInfo", task.packageInfo)
                                            if (task.state == DecompilerTaskManager.TaskState.COMPLETED) {
                                                putExtra("fromNotification", true)
                                            }
                                        }
                                        context.startActivity(intent)
                                    },
                                shape = cardShape,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    var iconDrawable by remember(task.packageInfo.name) {
                                        mutableStateOf(PackageInfo.iconMemoryCache[task.packageInfo.name])
                                    }
                                    LaunchedEffect(task.packageInfo) {
                                        if (iconDrawable == null) {
                                            iconDrawable = task.packageInfo.loadIcon(context)
                                        }
                                    }

                                    if (iconDrawable != null) {
                                        AsyncImage(
                                            model = iconDrawable,
                                            contentDescription = task.packageInfo.label,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Android,
                                            contentDescription = task.packageInfo.label,
                                            modifier = Modifier.size(44.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.packageInfo.label,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = task.packageInfo.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Engine: ${task.engineKey.uppercase()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (task.state == DecompilerTaskManager.TaskState.RUNNING) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                ExpressiveLoadingIndicator(
                                                    size = 14.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = stringResource(R.string.home_screen_task_processing),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = stringResource(R.string.home_screen_task_completed),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    collisionRunningTask?.let { running ->
        AlertDialog(
            onDismissRequest = { collisionRunningTask = null },
            title = { Text(stringResource(R.string.home_screen_active_task_dialog_title)) },
            text = {
                Text(stringResource(R.string.home_screen_active_task_dialog_message, running.packageInfo.label))
            },
            dismissButton = {
                TextButton(onClick = { collisionRunningTask = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = Intent(context, DecompilerActivity::class.java).apply {
                            putExtra("packageInfo", running.packageInfo)
                        }
                        collisionRunningTask = null
                        context.startActivity(intent)
                    }
                ) {
                    Text(stringResource(R.string.home_screen_active_task_dialog_view_button))
                }
            }
        )
    }

}

/*
 * Fast item card component for installed application in user or system tabs.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun AppCardItem(
    app: PackageInfo,
    position: CardGroupPosition,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val cardShape = getGroupedCardShape(position)

    var iconDrawable by remember(app.name) { mutableStateOf(PackageInfo.iconMemoryCache[app.name]) }
    val formattedSize = remember(app.file.length()) {
        FileUtils.byteCountToDisplaySize(app.file.length())
    }

    LaunchedEffect(app) {
        if (iconDrawable == null) {
            iconDrawable = app.loadIcon(context)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                if (iconDrawable != null) {
                    AsyncImage(
                        model = iconDrawable,
                        contentDescription = app.label,
                        modifier = Modifier.size(44.dp)
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = app.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.height(22.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "v${app.version}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.height(22.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = formattedSize,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }

                    if (app.isSplitApk) {
                        val count = app.splitSourceDirs?.size ?: 1
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.height(22.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = "Split ($count)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
