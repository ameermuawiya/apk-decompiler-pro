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

package com.ameermuawiya.decompilers.activities.explorer.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import com.ameermuawiya.decompilers.R
import com.ameermuawiya.decompilers.ui.components.ExpressiveLoadingIndicator
import com.ameermuawiya.decompilers.ui.theme.JadecTheme
import com.ameermuawiya.decompilers.utils.UserPreferences
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Activity displaying source code files with Sora Editor, syntax highlighting, and theme sync.
 */
class CodeViewerActivity : ComponentActivity() {

    /*
     * Sets up Compose content and initializes Edge-to-Edge window handling.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)

        val filePath = intent.extras?.getString("filePath")
        val packageName = intent.extras?.getString("name") ?: ""
        val isIgnoreList = intent.extras?.getBoolean("isIgnoreList", false) ?: false
        val showCopy = intent.extras?.getBoolean("extra_show_copy", !isIgnoreList) ?: !isIgnoreList
        val showShare = intent.extras?.getBoolean("extra_show_share", !isIgnoreList) ?: !isIgnoreList
        val showOpenExternal = intent.extras?.getBoolean("extra_show_open_external", !isIgnoreList) ?: !isIgnoreList
        val startEditMode = intent.extras?.getBoolean("extra_start_edit_mode", isIgnoreList) ?: isIgnoreList

        val userPrefs = UserPreferences(getSharedPreferences(UserPreferences.NAME, Context.MODE_PRIVATE))

        setContent {
            JadecTheme(
                themeMode = userPrefs.themeMode,
                dynamicColor = userPrefs.dynamicColor
            ) {
                CodeViewerScreen(
                    filePath = filePath,
                    packageName = packageName,
                    isIgnoreList = isIgnoreList,
                    showCopy = showCopy,
                    showShare = showShare,
                    showOpenExternal = showOpenExternal,
                    startEditMode = startEditMode,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

/*
 * Formats byte array into hexadecimal dump with byte offset and ASCII character column.
 */
private fun formatHexDump(bytes: ByteArray, maxBytes: Int = 32 * 1024): String {
    val sb = StringBuilder()
    val limit = minOf(bytes.size, maxBytes)
    for (i in 0 until limit step 16) {
        sb.append(String.format(java.util.Locale.US, "%08X  ", i))
        val chunkLen = minOf(16, limit - i)
        for (j in 0 until 16) {
            if (j < chunkLen) {
                sb.append(String.format(java.util.Locale.US, "%02X ", bytes[i + j]))
            } else {
                sb.append("   ")
            }
            if (j == 7) sb.append(" ")
        }
        sb.append(" |")
        for (j in 0 until chunkLen) {
            val b = bytes[i + j].toInt() and 0xFF
            val ch = if (b in 32..126) b.toChar() else '.'
            sb.append(ch)
        }
        sb.append("|\n")
    }
    if (bytes.size > limit) {
        sb.append("\n... [${bytes.size - limit} bytes remaining]\n")
    }
    return sb.toString()
}

/*
 * Renders source code content using Sora Editor with syntax highlighting, mode toggling, and file saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeViewerScreen(
    filePath: String?,
    packageName: String,
    isIgnoreList: Boolean = false,
    showCopy: Boolean = !isIgnoreList,
    showShare: Boolean = !isIgnoreList,
    showOpenExternal: Boolean = !isIgnoreList,
    startEditMode: Boolean = isIgnoreList,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val targetFile = remember(filePath) { if (!filePath.isNullOrBlank()) File(filePath) else null }
    val userPrefs = remember {
        UserPreferences(context.getSharedPreferences(UserPreferences.NAME, Context.MODE_PRIVATE))
    }
    val rawPrefs = remember {
        context.getSharedPreferences(UserPreferences.NAME, Context.MODE_PRIVATE)
    }

    var codeContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isEditMode by remember { mutableStateOf(startEditMode) }
    var editorInstance by remember { mutableStateOf<CodeEditor?>(null) }
    var wordWrapEnabled by remember {
        mutableStateOf(rawPrefs.getBoolean("wordWrapEnabled", false))
    }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(targetFile) {
        if (targetFile != null && targetFile.exists()) {
            withContext(Dispatchers.IO) {
                val binaryExtensions = setOf("dex", "arsc", "bin", "so", "dat", "o", "a", "zip", "jar", "apk")
                val ext = targetFile.extension.lowercase()
                val isBinaryExt = binaryExtensions.contains(ext)
                val content = try {
                    if (isBinaryExt) {
                        val bytes = targetFile.readBytes()
                        formatHexDump(bytes)
                    } else if (targetFile.length() > 5 * 1024 * 1024) {
                        targetFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
                            lines.take(2000).joinToString("\n")
                        } + "\n\n... [Content truncated due to large file size]"
                    } else {
                        val bytes = targetFile.readBytes()
                        val hasNullByte = bytes.take(1024).any { it == 0.toByte() }
                        if (hasNullByte) {
                            formatHexDump(bytes)
                        } else {
                            String(bytes, Charsets.UTF_8)
                        }
                    }
                } catch (e: Exception) {
                    try {
                        val bytes = targetFile.readBytes()
                        formatHexDump(bytes)
                    } catch (ex: Exception) {
                        context.getString(R.string.code_viewer_load_failed) + ": ${e.localizedMessage}"
                    }
                }
                codeContent = content
                isLoading = false
            }
        } else {
            codeContent = context.getString(R.string.code_viewer_file_not_found)
            isLoading = false
        }
    }

    val isDark = when {
        MaterialTheme.colorScheme.surface.toArgb() == android.graphics.Color.BLACK -> true
        else -> isSystemInDarkTheme()
    }
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    val keywordColor = MaterialTheme.colorScheme.primary
    val commentColor = MaterialTheme.colorScheme.outline
    val literalColor = MaterialTheme.colorScheme.tertiary
    val tagColor = MaterialTheme.colorScheme.secondary
    val attrNameColor = MaterialTheme.colorScheme.onSurfaceVariant
    val attrValueColor = MaterialTheme.colorScheme.tertiary
    val operatorColor = MaterialTheme.colorScheme.outline
    val funcColor = MaterialTheme.colorScheme.secondary

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumTopAppBar(
                title = {
                    val headerTitle = if (isIgnoreList && packageName.isNotBlank()) {
                        packageName
                    } else {
                        targetFile?.name ?: stringResource(R.string.code_viewer_title)
                    }
                    Text(
                        text = headerTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (isEditMode) {
                        IconButton(
                            onClick = {
                                val currentText = editorInstance?.text?.toString() ?: codeContent ?: ""
                                if (targetFile != null && targetFile.exists()) {
                                    scope.launch {
                                        val success = withContext(Dispatchers.IO) {
                                            try {
                                                targetFile.writeText(currentText)
                                                true
                                            } catch (e: Exception) {
                                                false
                                            }
                                        }
                                        if (success) {
                                            if (isIgnoreList) {
                                                val lines = currentText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                                                userPrefs.setCustomIgnoredList(lines)
                                            }
                                            codeContent = currentText
                                            if (!isIgnoreList) {
                                                isEditMode = false
                                                editorInstance?.isEditable = false
                                            }
                                            Toast.makeText(context, context.getString(R.string.code_viewer_saved), Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.code_viewer_save_failed), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Save, contentDescription = stringResource(R.string.code_viewer_save_content_desc))
                        }
                    } else {
                        IconButton(
                            onClick = {
                                isEditMode = true
                                editorInstance?.apply {
                                    isEditable = true
                                    requestFocus()
                                    post {
                                        try {
                                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                                            imm?.showSoftInput(this, 0)
                                        } catch (ignored: Exception) {
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.code_viewer_edit_content_desc))
                        }
                    }

                    if (showCopy) {
                        IconButton(
                            onClick = {
                                val text = editorInstance?.text?.toString() ?: codeContent
                                if (!text.isNullOrBlank()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Source Code", text)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, context.getString(R.string.code_viewer_copied), Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.code_viewer_copy_content_desc))
                        }
                    }

                    if (showShare) {
                        IconButton(
                            onClick = {
                                if (targetFile != null && targetFile.exists()) {
                                    val authority = "${context.packageName}.provider"
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, authority, targetFile))
                                        type = "text/plain"
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.code_viewer_share_title)))
                                }
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.code_viewer_share_content_desc))
                        }
                    }

                    var showOverflowMenu by remember { mutableStateOf(false) }

                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            if (showOpenExternal) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.navigator_activity_action_open_external)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        if (targetFile != null && targetFile.exists()) {
                                            val fileUri = FileProvider.getUriForFile(
                                                context,
                                                context.applicationContext.packageName + ".provider",
                                                targetFile
                                            )
                                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(fileUri, "text/plain")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            }
                                            val chooser = Intent.createChooser(viewIntent, context.getString(R.string.navigator_activity_open_with_title)).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            try {
                                                context.startActivity(chooser)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, context.getString(R.string.noSupportedHandlerForFileType), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.code_viewer_menu_wrap_content)) },
                                trailingIcon = {
                                    Checkbox(
                                        checked = wordWrapEnabled,
                                        onCheckedChange = null
                                    )
                                },
                                onClick = {
                                    val newVal = !wordWrapEnabled
                                    wordWrapEnabled = newVal
                                    rawPrefs.edit().putBoolean("wordWrapEnabled", newVal).apply()
                                    editorInstance?.isWordwrap = newVal
                                }
                            )
                            if (isIgnoreList) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.code_viewer_menu_reset_default)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showResetConfirmDialog = true
                                    }
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ExpressiveLoadingIndicator()
                }
            } else {
                val fileExtension = targetFile?.extension?.lowercase() ?: ""

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        CodeEditor(ctx).apply {
                            typefaceText = Typeface.MONOSPACE
                            typefaceLineNumber = Typeface.MONOSPACE
                            setTextSize(14f)
                            isLineNumberEnabled = true
                            isWordwrap = rawPrefs.getBoolean("wordWrapEnabled", false)
                            isEditable = isEditMode

                            val scheme = if (isDark) SchemeDarcula() else SchemeGitHub()
                            scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, surfaceColor.toArgb())
                            scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, surfaceColor.toArgb())
                            scheme.setColor(EditorColorScheme.TEXT_NORMAL, onSurfaceColor.toArgb())
                            scheme.setColor(EditorColorScheme.LINE_NUMBER, onSurfaceColor.copy(alpha = 0.5f).toArgb())
                            scheme.setColor(EditorColorScheme.CURRENT_LINE, primaryColor.copy(alpha = 0.08f).toArgb())
                            scheme.setColor(EditorColorScheme.SELECTION_HANDLE, primaryColor.toArgb())
                            scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, primaryColor.copy(alpha = 0.25f).toArgb())

                            scheme.setColor(EditorColorScheme.KEYWORD, keywordColor.toArgb())
                            scheme.setColor(EditorColorScheme.COMMENT, commentColor.toArgb())
                            scheme.setColor(EditorColorScheme.LITERAL, literalColor.toArgb())
                            scheme.setColor(EditorColorScheme.HTML_TAG, tagColor.toArgb())
                            scheme.setColor(EditorColorScheme.ATTRIBUTE_NAME, attrNameColor.toArgb())
                            scheme.setColor(EditorColorScheme.ATTRIBUTE_VALUE, attrValueColor.toArgb())
                            scheme.setColor(EditorColorScheme.OPERATOR, operatorColor.toArgb())
                            scheme.setColor(EditorColorScheme.FUNCTION_NAME, funcColor.toArgb())

                            colorScheme = scheme

                            if (fileExtension == "java") {
                                setEditorLanguage(JavaLanguage())
                            } else {
                                setEditorLanguage(AppCodeLanguage(fileExtension))
                            }

                            setText(codeContent ?: "")
                            setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                                val dy = (scrollY - oldScrollY).toFloat()
                                if (dy != 0f) {
                                    scrollBehavior.state.contentOffset = (scrollBehavior.state.contentOffset - dy).coerceIn(-1000f, 0f)
                                }
                            }
                            editorInstance = this
                        }
                    },
                    update = { editor ->
                        editor.isEditable = isEditMode
                        val scheme = editor.colorScheme
                        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, surfaceColor.toArgb())
                        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, surfaceColor.toArgb())
                        scheme.setColor(EditorColorScheme.TEXT_NORMAL, onSurfaceColor.toArgb())
                        scheme.setColor(EditorColorScheme.LINE_NUMBER, onSurfaceColor.copy(alpha = 0.5f).toArgb())
                        scheme.setColor(EditorColorScheme.CURRENT_LINE, primaryColor.copy(alpha = 0.08f).toArgb())
                        scheme.setColor(EditorColorScheme.SELECTION_HANDLE, primaryColor.toArgb())
                        scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, primaryColor.copy(alpha = 0.25f).toArgb())
                        scheme.setColor(EditorColorScheme.KEYWORD, keywordColor.toArgb())
                        scheme.setColor(EditorColorScheme.COMMENT, commentColor.toArgb())
                        scheme.setColor(EditorColorScheme.LITERAL, literalColor.toArgb())
                        scheme.setColor(EditorColorScheme.HTML_TAG, tagColor.toArgb())
                        scheme.setColor(EditorColorScheme.ATTRIBUTE_NAME, attrNameColor.toArgb())
                        scheme.setColor(EditorColorScheme.ATTRIBUTE_VALUE, attrValueColor.toArgb())
                        scheme.setColor(EditorColorScheme.OPERATOR, operatorColor.toArgb())
                        scheme.setColor(EditorColorScheme.FUNCTION_NAME, funcColor.toArgb())
                        editor.invalidate()
                    },
                    onRelease = { editor ->
                        editorInstance = null
                        editor.release()
                    }
                )
            }
        }
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text(stringResource(R.string.code_viewer_reset_confirm_title)) },
            text = { Text(stringResource(R.string.code_viewer_reset_confirm_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val defaultText = withContext(Dispatchers.IO) {
                                val assetContent = context.assets.open("ignored.list").bufferedReader().use { it.readText() }
                                targetFile?.writeText(assetContent)
                                assetContent
                            }
                            userPrefs.setCustomIgnoredList(emptyList())
                            codeContent = defaultText
                            editorInstance?.setText(defaultText)
                            showResetConfirmDialog = false
                            Toast.makeText(context, context.getString(R.string.code_viewer_reset_success), Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(stringResource(R.string.settings_reset_default))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
