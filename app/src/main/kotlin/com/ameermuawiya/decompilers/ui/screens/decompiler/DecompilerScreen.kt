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

package com.ameermuawiya.decompilers.ui.screens.decompiler

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.ameermuawiya.decompilers.R
import com.ameermuawiya.decompilers.activities.explorer.navigator.NavigatorActivity
import com.ameermuawiya.decompilers.activities.explorer.viewer.AppCodeLanguage
import com.ameermuawiya.decompilers.data.PackageInfo
import com.ameermuawiya.decompilers.data.SourceInfo
import com.ameermuawiya.decompilers.ui.components.CardGroupPosition
import com.ameermuawiya.decompilers.ui.components.ExpressiveLoadingIndicator
import com.ameermuawiya.decompilers.ui.components.getGroupedCardShape
import com.ameermuawiya.decompilers.utils.UserPreferences
import com.ameermuawiya.decompilers.utils.ZipUtils
import com.ameermuawiya.decompilers.utils.ktx.sourceDir
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils

/*
 * Displays package details, engine settings, progress indicators, memory gauge, and execution logs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecompilerScreen(
    packageInfo: PackageInfo,
    isProcessing: Boolean,
    progressPercentage: Float,
    statusText: String,
    memoryUsage: String = "",
    isCompleted: Boolean,
    isStopped: Boolean = false,
    targetFolderName: String = "",
    logs: String = "",
    showStopConfirmation: Boolean = false,
    onRequestStopConfirmation: () -> Unit = {},
    onDismissStopConfirmation: () -> Unit = {},
    onStopDecompileClick: () -> Unit = {},
    onBackClick: () -> Unit,
    onStartDecompileClick: (decompilerKey: String, index: Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember {
        UserPreferences(context.getSharedPreferences(UserPreferences.NAME, Context.MODE_PRIVATE))
    }

    BackHandler {
        onBackClick()
    }

    var iconDrawable by remember(packageInfo.name) {
        mutableStateOf<Drawable?>(PackageInfo.iconMemoryCache[packageInfo.name])
    }
    val formattedSize = remember(packageInfo.file.length()) {
        FileUtils.byteCountToDisplaySize(packageInfo.file.length())
    }

    val decompilerNames = remember { com.ameermuawiya.decompilers.engines.DecompilerRegistry.getEngineNames() }
    val decompilerValues = remember { com.ameermuawiya.decompilers.engines.DecompilerRegistry.getEngineValues() }
    val decompilerDescriptions = remember { com.ameermuawiya.decompilers.engines.DecompilerRegistry.getEngineDescriptions() }

    var selectedEngineIndex by remember { mutableIntStateOf(prefs.selectedDecompilerIndex) }
    var showEngineBottomSheet by remember { mutableStateOf(false) }
    var isZipping by remember { mutableStateOf(false) }

    var accumulatedLogs by remember { mutableStateOf("") }
    var lastActiveLogLine by remember { mutableStateOf("") }
    var userScrolledUp by remember { mutableStateOf(false) }
    var isUserTouchingLogs by remember { mutableStateOf(false) }
    var lastRenderedLogs by remember { mutableStateOf("") }
    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }

    var liveMemoryText by remember { mutableStateOf("") }
    var dynamicMaxMemoryMb by remember {
        val initMax = if (prefs.maxMemoryMb > 0) prefs.maxMemoryMb else prefs.getEffectiveMaxMemoryMb(packageInfo.file.length())
        mutableIntStateOf(initMax)
    }

    /*
     * Scrolls the embedded Sora CodeEditor to the bottom-most line and column.
     */
    fun scrollEditorToBottom(editor: CodeEditor) {
        val lineCount = editor.text.lineCount
        if (lineCount <= 0) return
        val lastLine = (lineCount - 1).coerceAtLeast(0)
        val lastCol = editor.text.getColumnCount(lastLine)
        editor.setSelection(lastLine, lastCol, true)
        editor.ensureSelectionVisible()
        val maxScrollY = editor.scrollMaxY
        if (maxScrollY > 0 && editor.offsetY < maxScrollY) {
            editor.scroller.forceFinished(true)
            editor.scroller.startScroll(0, editor.offsetY, 0, maxScrollY - editor.offsetY, 0)
            editor.postInvalidateOnAnimation()
        }
    }

    LaunchedEffect(packageInfo.name) {
        if (!isProcessing && !isCompleted && !isStopped) {
            accumulatedLogs = ""
            lastActiveLogLine = ""
            userScrolledUp = false
            lastRenderedLogs = ""
        }
    }

    LaunchedEffect(logs) {
        if (logs.isNotEmpty()) {
            accumulatedLogs = logs
        } else if (!isProcessing && !isCompleted && !isStopped) {
            accumulatedLogs = ""
        }
    }

    LaunchedEffect(isProcessing, isCompleted, isStopped) {
        userScrolledUp = false
        editorRef?.let { editor ->
            editor.post { scrollEditorToBottom(editor) }
            editor.postDelayed({ scrollEditorToBottom(editor) }, 80L)
        }
    }

    LaunchedEffect(isProcessing) {
        if (isProcessing) {
            val packageBytes = if (packageInfo.file.exists()) packageInfo.file.length() else 0L
            dynamicMaxMemoryMb = if (prefs.maxMemoryMb > 0) {
                prefs.maxMemoryMb
            } else {
                prefs.getEffectiveMaxMemoryMb(packageBytes)
            }
            while (true) {
                val runtime = Runtime.getRuntime()
                val usedBytes = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
                val usedMb = (usedBytes / (1024 * 1024)).coerceAtLeast(1L)
                dynamicMaxMemoryMb = prefs.computeDynamicMaxMemoryMb(usedMb, dynamicMaxMemoryMb)
                val safeUsedMb = usedMb.coerceAtMost((dynamicMaxMemoryMb - 1).toLong().coerceAtLeast(1L))
                liveMemoryText = "$safeUsedMb MB / $dynamicMaxMemoryMb MB"
                delay(500)
            }
        } else {
            liveMemoryText = ""
        }
    }

    LaunchedEffect(statusText) {
        if (statusText.isNotBlank()) {
            val lower = statusText.lowercase()
            if (lower.startsWith("memory") || lower.contains("[mem]") || lower.contains("mb /")) {
                return@LaunchedEffect
            }
            lastActiveLogLine = statusText
            if (logs.isEmpty()) {
                val formattedMsg = when {
                    statusText.startsWith("[ERROR]") || statusText.startsWith("[FAIL]") -> statusText
                    statusText.startsWith("[WARN]") || statusText.startsWith("WARNING") -> statusText
                    statusText.startsWith("[STEP]") || statusText.startsWith("[INFO]") -> statusText
                    statusText.contains("error", ignoreCase = true) || statusText.contains("fail", ignoreCase = true) || statusText.contains("exception", ignoreCase = true) -> "[ERROR] $statusText"
                    statusText.contains("warn", ignoreCase = true) -> "[WARN] $statusText"
                    statusText.contains("doWork", ignoreCase = true) -> "[STEP] Decompilation step in progress"
                    statusText.contains("Getting Ready", ignoreCase = true) -> "[STEP] Preparing engine environment"
                    else -> "[INFO] $statusText"
                }
                accumulatedLogs = if (accumulatedLogs.isEmpty()) {
                    "[STEP 1/3] Initializing decompilation engine...\n$formattedMsg"
                } else {
                    "$accumulatedLogs\n$formattedMsg"
                }
            }
        }
    }

    val systemWarningText = stringResource(R.string.systemAppWarning)

    LaunchedEffect(packageInfo) {
        if (iconDrawable == null) {
            iconDrawable = packageInfo.loadIcon(context)
        }
        if (packageInfo.isSystemPackage && !accumulatedLogs.contains(systemWarningText)) {
            val warningMsg = "[WARN] $systemWarningText"
            accumulatedLogs = if (accumulatedLogs.isEmpty()) warningMsg else "$warningMsg\n$accumulatedLogs"
        }
    }

    val typeLabel = remember(packageInfo) {
        when {
            packageInfo.isSplitApk -> "APKS (${packageInfo.splitSourceDirs?.size ?: 1})"
            packageInfo.type == PackageInfo.Type.JAR -> "JAR"
            packageInfo.type == PackageInfo.Type.DEX -> "DEX"
            else -> "APK"
        }
    }

    val resolvedTargetDir = remember(packageInfo.name, targetFolderName, isCompleted, isStopped) {
        if (targetFolderName.isNotBlank()) {
            com.ameermuawiya.decompilers.utils.ktx.getAppStorage(context).resolve("sources/$targetFolderName")
        } else {
            sourceDir(context, packageInfo.name).takeIf { it.exists() }
                ?: com.ameermuawiya.decompilers.utils.ktx.getAppStorage(context).resolve("sources/${packageInfo.name}")
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.decompiler_screen_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.decompiler_screen_back_content_description))
                    }
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category 1: Configuration Group
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Item 1: App Details Card (Always FIRST)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = getGroupedCardShape(CardGroupPosition.FIRST),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (iconDrawable != null) {
                            AsyncImage(
                                model = iconDrawable,
                                contentDescription = packageInfo.label,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = packageInfo.label,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = packageInfo.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = packageInfo.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "v${packageInfo.version} • $formattedSize",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = typeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Item 2: Decompiler Configuration
                val isConfigLast = !isCompleted && !isStopped
                val configShape = getGroupedCardShape(if (isConfigLast) CardGroupPosition.LAST else CardGroupPosition.MIDDLE)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = configShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.decompiler_config_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val selectedEngineFull = decompilerNames.getOrElse(selectedEngineIndex) { "JaDX 1.5.6" }
                        val engineTokens = selectedEngineFull.split(" ", limit = 2)
                        val engineNameOnly = engineTokens.getOrNull(0) ?: selectedEngineFull
                        val engineVersionOnly = engineTokens.getOrNull(1) ?: ""

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !isProcessing) { showEngineBottomSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Code,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = engineNameOnly,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (engineVersionOnly.isNotBlank()) {
                                        Text(
                                            text = "v$engineVersionOnly",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (!isProcessing) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = stringResource(R.string.decompiler_screen_change_button),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = decompilerDescriptions.getOrElse(selectedEngineIndex) { "" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Item 3A: Completed Card (if completed)
                if (isCompleted && !isStopped) {
                    val completedCardShape = getGroupedCardShape(CardGroupPosition.LAST)
                    val targetDir = resolvedTargetDir

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = completedCardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = stringResource(R.string.decompiler_status_completed),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                text = stringResource(R.string.decompiler_decompiled_saved_to, targetDir.absolutePath),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val sourceInfo = SourceInfo.from(targetDir)
                                        val intent = Intent(context, NavigatorActivity::class.java)
                                        intent.putExtra("selectedApp", sourceInfo)
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.decompiler_button_open),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            withContext(Dispatchers.Main) { isZipping = true }
                                            try {
                                                val zipFile = ZipUtils.zipDir(targetDir, packageInfo.name)
                                                withContext(Dispatchers.Main) {
                                                    isZipping = false
                                                    if (zipFile.exists() && zipFile.length() > 0) {
                                                        val uri = FileProvider.getUriForFile(
                                                            context,
                                                            "${context.packageName}.provider",
                                                            zipFile
                                                        )
                                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                            type = "application/zip"
                                                            putExtra(Intent.EXTRA_STREAM, uri)
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        context.startActivity(
                                                            Intent.createChooser(
                                                                shareIntent,
                                                                context.getString(R.string.history_screen_action_share_zip)
                                                            )
                                                        )
                                                    } else {
                                                        Toast.makeText(context, context.getString(R.string.decompiler_screen_toast_zip_failed), Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    isZipping = false
                                                    Toast.makeText(context, context.getString(R.string.decompiler_screen_toast_zip_error, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.decompiler_button_share_zip),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Item 3B: Stopped Card (if stopped by user)
                if (isStopped && !isProcessing) {
                    val stoppedCardShape = getGroupedCardShape(CardGroupPosition.LAST)
                    val targetDir = resolvedTargetDir
                    val hasPartialFiles = targetDir.exists() && (targetDir.list()?.isNotEmpty() == true)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = stoppedCardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(30.dp)
                                )
                                Text(
                                    text = stringResource(R.string.decompiler_status_stopped_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (hasPartialFiles) {
                                        stringResource(R.string.decompiler_stopped_warning_msg)
                                    } else {
                                        stringResource(R.string.decompiler_stopped_no_files_msg)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }

                            if (hasPartialFiles) {
                                Text(
                                    text = stringResource(R.string.decompiler_stopped_saved_to, targetDir.absolutePath),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val sourceInfo = SourceInfo.from(targetDir)
                                            val intent = Intent(context, NavigatorActivity::class.java)
                                            intent.putExtra("selectedApp", sourceInfo)
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            stringResource(R.string.decompiler_button_open),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                withContext(Dispatchers.Main) { isZipping = true }
                                                try {
                                                    val zipFile = ZipUtils.zipDir(targetDir, packageInfo.name)
                                                    withContext(Dispatchers.Main) {
                                                        isZipping = false
                                                        if (zipFile.exists() && zipFile.length() > 0) {
                                                            val uri = FileProvider.getUriForFile(
                                                                context,
                                                                "${context.packageName}.provider",
                                                                zipFile
                                                            )
                                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                                type = "application/zip"
                                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }
                                                            context.startActivity(
                                                                Intent.createChooser(
                                                                    shareIntent,
                                                                    context.getString(R.string.history_screen_action_share_zip)
                                                                )
                                                            )
                                                        } else {
                                                            Toast.makeText(context, context.getString(R.string.decompiler_screen_toast_zip_failed), Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    withContext(Dispatchers.Main) {
                                                        isZipping = false
                                                        Toast.makeText(context, context.getString(R.string.decompiler_screen_toast_zip_error, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            stringResource(R.string.decompiler_button_share_zip),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Independent Standalone Start / Re-decompile / Stop Button
            if (!isProcessing) {
                Button(
                    onClick = {
                        userScrolledUp = false
                        accumulatedLogs = if (packageInfo.isSystemPackage) "[WARN] $systemWarningText\n" else ""
                        lastActiveLogLine = ""
                        lastRenderedLogs = ""
                        val key = decompilerValues.getOrElse(selectedEngineIndex) { "jadx" }
                        onStartDecompileClick(key, selectedEngineIndex)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCompleted || isStopped) {
                            stringResource(R.string.decompiler_screen_re_decompile_button)
                        } else {
                            stringResource(R.string.decompiler_start_button)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onRequestStopConfirmation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.decompiler_screen_stop_button),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Category 2: Status, Memory & Execution Logs Section
            val showMemoryCard = prefs.showMemoryUsage && (isProcessing || liveMemoryText.isNotBlank())
            val showLoadingCard = isProcessing

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Item A: Memory Usage Card
                if (showMemoryCard) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = getGroupedCardShape(CardGroupPosition.FIRST),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.decompiler_screen_memory_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                val displayedMemory = liveMemoryText.ifBlank {
                                    memoryUsage.ifBlank { "1 MB / $dynamicMaxMemoryMb MB" }
                                }
                                Text(
                                    text = displayedMemory,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Item B: Loading / Processing Indicator Card
                if (showLoadingCard) {
                    val loadingShape = getGroupedCardShape(if (showMemoryCard) CardGroupPosition.MIDDLE else CardGroupPosition.FIRST)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = loadingShape,
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
                            ExpressiveLoadingIndicator(
                                size = 28.dp,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                val currentLogLine = if (lastActiveLogLine.isNotBlank()) {
                                    lastActiveLogLine
                                } else {
                                    stringResource(R.string.decompiler_screen_processing_badge)
                                }
                                Text(
                                    text = stringResource(R.string.decompiler_screen_active_step_prefix, currentLogLine),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Item C: Execution Logs Card (Always present)
                val logsShape = when {
                    showMemoryCard || showLoadingCard -> getGroupedCardShape(CardGroupPosition.LAST)
                    else -> getGroupedCardShape(CardGroupPosition.SINGLE)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = logsShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val effectiveLogs = if (logs.isNotEmpty()) logs else accumulatedLogs

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.decompiler_live_logs_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(
                                onClick = {
                                    if (effectiveLogs.isNotBlank()) {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Execution Logs", effectiveLogs)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, context.getString(R.string.logs_copied), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                            }
                        }

                        val primaryColor = MaterialTheme.colorScheme.primary
                        val errorColor = MaterialTheme.colorScheme.error
                        val tertiaryColor = MaterialTheme.colorScheme.tertiary
                        val secondaryColor = MaterialTheme.colorScheme.secondary
                        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
                        val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
                        val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        val isDark = isSystemInDarkTheme()
                        val noLogsText = stringResource(R.string.decompiler_no_logs_yet)
                        val displayLogs = effectiveLogs.ifBlank { noLogsText }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(containerColor)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    CodeEditor(ctx).apply {
                                        editorRef = this
                                        typefaceText = Typeface.MONOSPACE
                                        typefaceLineNumber = Typeface.MONOSPACE
                                        setTextSize(12f)
                                        isLineNumberEnabled = true
                                        isWordwrap = true
                                        isEditable = false
                                        isScalable = true
                                        setLineNumberMarginLeft(ctx.resources.displayMetrics.density * 2f)
                                        setDividerMargin(ctx.resources.displayMetrics.density * 4f, ctx.resources.displayMetrics.density * 6f)

                                        val scheme = if (isDark) SchemeDarcula() else SchemeGitHub()
                                        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, containerColor.toArgb())
                                        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, containerColor.toArgb())
                                        scheme.setColor(EditorColorScheme.TEXT_NORMAL, onSurfaceColor.toArgb())
                                        scheme.setColor(EditorColorScheme.LINE_NUMBER, onSurfaceVariantColor.toArgb())
                                        scheme.setColor(EditorColorScheme.CURRENT_LINE, containerColor.toArgb())
                                        scheme.setColor(EditorColorScheme.SELECTION_HANDLE, primaryColor.toArgb())
                                        scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, primaryColor.copy(alpha = 0.25f).toArgb())
                                        scheme.setColor(EditorColorScheme.KEYWORD, primaryColor.toArgb())
                                        scheme.setColor(EditorColorScheme.LITERAL, secondaryColor.toArgb())
                                        scheme.setColor(EditorColorScheme.HTML_TAG, tertiaryColor.toArgb())
                                        scheme.setColor(EditorColorScheme.ATTRIBUTE_NAME, errorColor.toArgb())
                                        scheme.setColor(EditorColorScheme.FUNCTION_NAME, onSurfaceColor.toArgb())
                                        colorScheme = scheme

                                        setEditorLanguage(AppCodeLanguage("log"))
                                        setText(displayLogs)
                                        lastRenderedLogs = displayLogs
                                        post { scrollEditorToBottom(this) }

                                        setOnTouchListener { v, event ->
                                            v.parent?.requestDisallowInterceptTouchEvent(true)
                                            when (event.actionMasked) {
                                                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                                    isUserTouchingLogs = true
                                                }
                                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                                    isUserTouchingLogs = false
                                                    v.parent?.requestDisallowInterceptTouchEvent(false)
                                                    postDelayed({
                                                        val maxScroll = scrollMaxY
                                                        userScrolledUp = maxScroll > 60 && offsetY < (maxScroll - 60)
                                                    }, 150L)
                                                }
                                            }
                                            false
                                        }
                                    }
                                },
                                update = { editor ->
                                    editorRef = editor
                                    editor.isEditable = false
                                    editor.isWordwrap = true
                                    editor.isLineNumberEnabled = true
                                    val scheme = editor.colorScheme
                                    scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, containerColor.toArgb())
                                    scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, containerColor.toArgb())
                                    scheme.setColor(EditorColorScheme.TEXT_NORMAL, onSurfaceColor.toArgb())
                                    scheme.setColor(EditorColorScheme.LINE_NUMBER, onSurfaceVariantColor.toArgb())
                                    scheme.setColor(EditorColorScheme.KEYWORD, primaryColor.toArgb())
                                    scheme.setColor(EditorColorScheme.LITERAL, secondaryColor.toArgb())
                                    scheme.setColor(EditorColorScheme.HTML_TAG, tertiaryColor.toArgb())
                                    scheme.setColor(EditorColorScheme.ATTRIBUTE_NAME, errorColor.toArgb())

                                    if (lastRenderedLogs != displayLogs) {
                                        if (lastRenderedLogs.isNotEmpty() && lastRenderedLogs != noLogsText && displayLogs.startsWith(lastRenderedLogs)) {
                                            val appended = displayLogs.substring(lastRenderedLogs.length)
                                            val endLine = (editor.text.lineCount - 1).coerceAtLeast(0)
                                            val endCol = editor.text.getColumnCount(endLine)
                                            editor.text.insert(endLine, endCol, appended)
                                        } else {
                                            editor.setText(displayLogs)
                                        }
                                        lastRenderedLogs = displayLogs
                                        if (!userScrolledUp && !isUserTouchingLogs) {
                                            scrollEditorToBottom(editor)
                                            editor.post {
                                                if (!userScrolledUp && !isUserTouchingLogs) {
                                                    scrollEditorToBottom(editor)
                                                }
                                            }
                                            editor.postDelayed({
                                                if (!userScrolledUp && !isUserTouchingLogs) {
                                                    scrollEditorToBottom(editor)
                                                }
                                            }, 70L)
                                        }
                                    }
                                    editor.invalidate()
                                },
                                onRelease = { editor ->
                                    if (editorRef === editor) {
                                        editorRef = null
                                    }
                                    editor.release()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showStopConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissStopConfirmation,
            title = { Text(stringResource(R.string.decompiler_screen_stop_dialog_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.decompiler_screen_stop_dialog_message)) },
            confirmButton = {
                Button(
                    onClick = onStopDecompileClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.decompiler_screen_stop_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissStopConfirmation) {
                    Text(stringResource(R.string.decompiler_screen_stop_dialog_cancel))
                }
            }
        )
    }

    if (isZipping) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.decompiler_zipping_dialog_title)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.decompiler_zipping_dialog_msg))
                }
            },
            confirmButton = {}
        )
    }

    if (showEngineBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEngineBottomSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.decompiler_select_engine_sheet_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    decompilerValues.forEachIndexed { index, _ ->
                        val name = decompilerNames.getOrNull(index) ?: ""
                        val desc = decompilerDescriptions.getOrNull(index) ?: ""
                        val total = decompilerValues.size
                        val pos = when {
                            total == 1 -> CardGroupPosition.SINGLE
                            index == 0 -> CardGroupPosition.FIRST
                            index == total - 1 -> CardGroupPosition.LAST
                            else -> CardGroupPosition.MIDDLE
                        }
                        val itemShape = getGroupedCardShape(pos)
                        val isSelected = selectedEngineIndex == index

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(itemShape)
                                .clickable {
                                    selectedEngineIndex = index
                                    val rawPrefs = context.getSharedPreferences(UserPreferences.NAME, Context.MODE_PRIVATE)
                                    rawPrefs.edit().putInt("selectedDecompilerIndex", index).apply()
                                },
                            shape = itemShape,
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
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedEngineIndex = index
                                        val rawPrefs = context.getSharedPreferences(UserPreferences.NAME, Context.MODE_PRIVATE)
                                        rawPrefs.edit().putInt("selectedDecompilerIndex", index).apply()
                                    }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
