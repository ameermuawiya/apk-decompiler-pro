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

package com.ameermuawiya.decompilers.activities.explorer.navigator

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Javascript
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import com.ameermuawiya.decompilers.R
import com.ameermuawiya.decompilers.activities.explorer.viewer.CodeViewerActivity
import com.ameermuawiya.decompilers.activities.explorer.viewer.ImageViewerActivity
import com.ameermuawiya.decompilers.data.FileItem
import com.ameermuawiya.decompilers.data.SourceInfo
import com.ameermuawiya.decompilers.ui.components.CardGroupPosition
import com.ameermuawiya.decompilers.ui.components.ExpressiveLoadingIndicator
import com.ameermuawiya.decompilers.ui.components.FullScreenLoadingOverlay
import com.ameermuawiya.decompilers.ui.components.getGroupedCardShape
import com.ameermuawiya.decompilers.ui.theme.JadecTheme
import com.ameermuawiya.decompilers.utils.UserPreferences
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import timber.log.Timber
import java.io.File

class NavigatorActivity : ComponentActivity() {

    private lateinit var navigationHandler: NavigatorHandler

    /*
     * Sets up Compose layout for exploring decompiled source files.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)

        val rawPrefs = getSharedPreferences(UserPreferences.NAME, Context.MODE_PRIVATE)
        val initialThemeMode = rawPrefs.getString("themeMode", if (rawPrefs.getBoolean("darkMode", false)) "dark" else "system") ?: "system"
        val initialDynamic = rawPrefs.getBoolean("dynamicColor", true)

        val selectedApp = IntentCompat.getParcelableExtra(intent, "selectedApp", SourceInfo::class.java)
        navigationHandler = NavigatorHandler(this)

        setContent {
            JadecTheme(
                themeMode = initialThemeMode,
                dynamicColor = initialDynamic
            ) {
                NavigatorScreen(
                    selectedApp = selectedApp,
                    navigationHandler = navigationHandler,
                    onFinish = { finish() }
                )
            }
        }
    }
}

/*
 * Displays file system tree with Jetpack Compose, grouped card shapes, search navigation, and file actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigatorScreen(
    selectedApp: SourceInfo?,
    navigationHandler: NavigatorHandler,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rootDir = remember(selectedApp) { selectedApp?.sourceDirectory }
    var currentDirectory by remember { mutableStateOf(rootDir) }
    var fileItems by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isArchiving by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedItemForSheet by remember { mutableStateOf<FileItem?>(null) }
    var itemToDelete by remember { mutableStateOf<FileItem?>(null) }
    val directoryCache = remember { mutableMapOf<String, List<FileItem>>() }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val isAtRoot = remember(currentDirectory, rootDir) {
        currentDirectory?.canonicalPath == rootDir?.canonicalPath
    }

    /*
     * Navigates back up the directory tree or finishes if at root directory.
     */
    fun handleGoBack() {
        if (isAtRoot || currentDirectory == null) {
            onFinish()
        } else {
            currentDirectory?.parentFile?.let {
                if (rootDir != null && it.canonicalPath.startsWith(rootDir.canonicalPath)) {
                    currentDirectory = it
                } else if (isAtRoot) {
                    onFinish()
                } else {
                    currentDirectory = rootDir
                }
            } ?: onFinish()
        }
    }

    BackHandler {
        handleGoBack()
    }

    /*
     * Asynchronously loads files for the specified directory with instant cache retrieval.
     */
    fun loadFiles(directory: File) {
        val pathKey = directory.canonicalPath
        val cached = directoryCache[pathKey]
        if (cached != null) {
            fileItems = cached
            isLoading = false
            navigationHandler.loadFiles(directory)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ items ->
                    directoryCache[pathKey] = items
                    fileItems = items
                }, {
                    Timber.e(it)
                })
            return
        }

        fileItems = emptyList()
        isLoading = true
        navigationHandler.loadFiles(directory)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .onErrorReturn {
                Timber.e(it)
                ArrayList()
            }
            .subscribe { items ->
                directoryCache[pathKey] = items
                fileItems = items
                isLoading = false
            }
    }

    LaunchedEffect(currentDirectory) {
        currentDirectory?.let { loadFiles(it) }
    }

    /*
     * Shares the generated source ZIP archive using FileProvider.
     */
    fun shareArchive(zipFile: File?) {
        if (zipFile == null || !zipFile.exists()) {
            Toast.makeText(context, context.getString(R.string.genericError), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val authority = "${context.packageName}.provider"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, authority, zipFile))
                type = "application/zip"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.sendSourceVia)))
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.genericError), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumTopAppBar(
                title = {
                    Column {
                        Text(
                            text = selectedApp?.packageLabel ?: stringResource(R.string.app_name),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold
                        )
                        val relPath = currentDirectory?.let { dir ->
                            val rootPath = rootDir?.canonicalPath ?: ""
                            val currPath = dir.canonicalPath
                            if (currPath == rootPath) "/" else currPath.removePrefix(rootPath)
                        } ?: "/"
                        Text(
                            text = relPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { handleGoBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (selectedApp != null && rootDir != null) {
                            isArchiving = true
                            navigationHandler.archiveDirectory(rootDir, selectedApp.packageName)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .onErrorReturn { null }
                                .subscribe { file ->
                                    isArchiving = false
                                    shareArchive(file)
                                }
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.shareSource))
                    }

                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.deleteSource))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveLoadingIndicator(size = 52.dp)
                }
            } else if (fileItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.history_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(
                        items = fileItems,
                        key = { index, item -> "${item.file.canonicalPath}_$index" }
                    ) { index, item ->
                        val total = fileItems.size
                        val position = when {
                            total == 1 -> CardGroupPosition.SINGLE
                            index == 0 -> CardGroupPosition.FIRST
                            index == total - 1 -> CardGroupPosition.LAST
                            else -> CardGroupPosition.MIDDLE
                        }

                        FileItemCard(
                            fileItem = item,
                            position = position,
                            onClick = {
                                if (item.file.isDirectory) {
                                    fileItems = emptyList()
                                    isLoading = true
                                    currentDirectory = item.file
                                } else {
                                    openFile(context, item.file, selectedApp?.packageName)
                                }
                            },
                            onLongClick = {
                                selectedItemForSheet = item
                            }
                        )
                    }
                }
            }

            FullScreenLoadingOverlay(visible = isArchiving)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.deleteSource)) },
            text = { Text(stringResource(R.string.deleteSourceConfirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        selectedApp?.let { app ->
                            navigationHandler.deleteDirectory(app.sourceDirectory)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe {
                                    Toast.makeText(context, context.getString(R.string.sourceDeleted), Toast.LENGTH_SHORT).show()
                                    onFinish()
                                }
                        }
                    }
                ) {
                    Text(stringResource(R.string.deleteSource), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    selectedItemForSheet?.let { sheetItem ->
        val isDir = sheetItem.file.isDirectory
        val ext = sheetItem.file.extension.lowercase()
        val (sheetIcon, sheetIconTint, sheetContainerColor) = when {
            isDir -> Triple(Icons.Default.Folder, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer)
            ext == "java" -> Triple(Icons.Default.Code, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer)
            ext == "kt" -> Triple(Icons.Default.Terminal, MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer)
            ext == "xml" || ext == "html" -> Triple(Icons.Default.Html, MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer)
            ext == "json" -> Triple(Icons.Default.DataObject, MaterialTheme.colorScheme.onTertiaryContainer, MaterialTheme.colorScheme.tertiaryContainer)
            ext == "js" || ext == "ts" -> Triple(Icons.Default.Javascript, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer)
            arrayOf("txt", "md", "properties", "sql", "yml", "yaml", "smali", "class").contains(ext) ->
                Triple(Icons.Default.Description, MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer)
            arrayOf("jpeg", "jpg", "png", "webp", "gif").contains(ext) ->
                Triple(Icons.Default.Image, MaterialTheme.colorScheme.onTertiaryContainer, MaterialTheme.colorScheme.tertiaryContainer)
            else ->
                Triple(Icons.AutoMirrored.Filled.InsertDriveFile, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceContainerHighest)
        }

        ModalBottomSheet(
            onDismissRequest = { selectedItemForSheet = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.navigator_activity_sheet_title),
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
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = sheetContainerColor,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = sheetIcon,
                                    contentDescription = null,
                                    tint = sheetIconTint,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sheetItem.file.name,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitle = if (isDir) {
                                stringResource(R.string.navigator_activity_folder_type)
                            } else {
                                FileUtils.byteCountToDisplaySize(sheetItem.file.length())
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!isDir && ext.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = ext.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val openShape = getGroupedCardShape(CardGroupPosition.FIRST)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(openShape)
                            .clickable {
                                selectedItemForSheet = null
                                if (sheetItem.file.isDirectory) {
                                    currentDirectory = sheetItem.file
                                } else {
                                    openFile(context, sheetItem.file, selectedApp?.packageName)
                                }
                            },
                        shape = openShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = stringResource(R.string.navigator_activity_action_open),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (!isDir) {
                        val openExtShape = getGroupedCardShape(CardGroupPosition.MIDDLE)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(openExtShape)
                                .clickable {
                                    selectedItemForSheet = null
                                    openFileExternally(context, sheetItem.file)
                                },
                            shape = openExtShape,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = stringResource(R.string.navigator_activity_action_open_external),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        val shareShape = getGroupedCardShape(CardGroupPosition.MIDDLE)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shareShape)
                                .clickable {
                                    selectedItemForSheet = null
                                    try {
                                        val authority = "${context.packageName}.provider"
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, authority, sheetItem.file))
                                            type = "*/*"
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.navigator_activity_action_share)))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, context.getString(R.string.genericError), Toast.LENGTH_SHORT).show()
                                    }
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
                                Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = stringResource(R.string.navigator_activity_action_share),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    val deleteShape = getGroupedCardShape(CardGroupPosition.LAST)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(deleteShape)
                            .clickable {
                                itemToDelete = sheetItem
                                selectedItemForSheet = null
                            },
                        shape = deleteShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = stringResource(R.string.navigator_activity_action_delete),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(text = stringResource(R.string.navigator_activity_delete_file_title)) },
            text = { Text(text = stringResource(R.string.navigator_activity_delete_file_confirm, item.file.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = item
                        itemToDelete = null
                        scope.launch {
                            val deleted = withContext(Dispatchers.IO) {
                                try {
                                    if (toDelete.file.isDirectory) {
                                        toDelete.file.deleteRecursively()
                                    } else {
                                        toDelete.file.delete()
                                    }
                                } catch (e: Exception) {
                                    false
                                }
                            }
                            if (deleted) {
                                Toast.makeText(context, context.getString(R.string.navigator_activity_item_deleted, toDelete.file.name), Toast.LENGTH_SHORT).show()
                                currentDirectory?.let {
                                    directoryCache.remove(it.canonicalPath)
                                    loadFiles(it)
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.navigator_activity_action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(
                        text = stringResource(R.string.navigator_activity_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

/*
 * Renders file or directory item inside grouped card container.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileItemCard(
    fileItem: FileItem,
    position: CardGroupPosition,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val cardShape = getGroupedCardShape(position)
    val isDir = fileItem.file.isDirectory
    val ext = fileItem.file.extension.lowercase()

    val (icon, iconTint, containerColor) = when {
        isDir -> Triple(Icons.Default.Folder, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer)
        ext == "java" -> Triple(Icons.Default.Code, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer)
        ext == "kt" -> Triple(Icons.Default.Code, MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer)
        ext == "xml" -> Triple(Icons.Default.Code, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer)
        ext == "html" || ext == "htm" -> Triple(Icons.Default.Html, MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer)
        ext == "json" -> Triple(Icons.Default.DataObject, MaterialTheme.colorScheme.onTertiaryContainer, MaterialTheme.colorScheme.tertiaryContainer)
        ext == "js" || ext == "ts" -> Triple(Icons.Default.Javascript, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer)
        ext == "smali" -> Triple(Icons.Default.Terminal, MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer)
        arrayOf("zip", "jar", "apk", "dex", "arsc").contains(ext) ->
            Triple(Icons.Default.FolderZip, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer)
        arrayOf("txt", "md", "properties", "sql", "yml", "yaml", "class", "ini", "cfg", "log").contains(ext) ->
            Triple(Icons.Default.Description, MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer)
        arrayOf("jpeg", "jpg", "png", "webp", "gif", "svg", "bmp").contains(ext) ->
            Triple(Icons.Default.Image, MaterialTheme.colorScheme.onTertiaryContainer, MaterialTheme.colorScheme.tertiaryContainer)
        else ->
            Triple(Icons.AutoMirrored.Filled.InsertDriveFile, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceContainerHighest)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = containerColor,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileItem.file.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val subtitle = if (isDir) {
                    stringResource(R.string.navigator_activity_folder_type)
                } else {
                    FileUtils.byteCountToDisplaySize(fileItem.file.length())
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/*
 * Opens file in code viewer, image viewer, or external system application handler.
 */
private fun openFile(context: Context, file: File, packageName: String?) {
    val ext = file.extension.lowercase()
    when {
        arrayOf("jpeg", "jpg", "png", "webp", "gif", "svg", "bmp").contains(ext) -> {
            val intent = Intent(context, ImageViewerActivity::class.java).apply {
                putExtra("filePath", file.canonicalPath)
                putExtra("name", packageName)
            }
            context.startActivity(intent)
        }
        else -> {
            val intent = Intent(context, CodeViewerActivity::class.java).apply {
                putExtra("filePath", file.canonicalPath)
                putExtra("name", packageName)
            }
            context.startActivity(intent)
        }
    }
}

/*
 * Opens a file using an external installed application or system chooser.
 */
private fun openFileExternally(context: Context, file: File) {
    try {
        val ext = file.extension.lowercase()
        val mimeTypeDetector = MimeTypeMap.getSingleton()
        val mimeType = mimeTypeDetector.getMimeTypeFromExtension(ext) ?: "*/*"
        val fileUri = FileProvider.getUriForFile(
            context,
            context.applicationContext.packageName + ".provider",
            file
        )
        val fileIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val chooser = Intent.createChooser(fileIntent, context.getString(R.string.navigator_activity_open_with_title)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.noSupportedHandlerForFileType), Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.genericError), Toast.LENGTH_SHORT).show()
    }
}
