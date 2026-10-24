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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.ameermuawiya.decompilers.R
import com.ameermuawiya.decompilers.ui.theme.JadecTheme
import com.ameermuawiya.decompilers.utils.UserPreferences
import java.io.File

/*
 * Displays image source files in Jetpack Compose with theme sync and action options.
 */
class ImageViewerActivity : ComponentActivity() {

    /*
     * Initializes edge-to-edge display and loads the target image file into Compose.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)

        val filePath = intent.extras?.getString("filePath")
        val packageName = intent.extras?.getString("name") ?: ""

        val userPrefs = UserPreferences(getSharedPreferences(UserPreferences.NAME, Context.MODE_PRIVATE))

        setContent {
            JadecTheme(
                themeMode = userPrefs.themeMode,
                dynamicColor = userPrefs.dynamicColor
            ) {
                ImageViewerScreen(
                    filePath = filePath,
                    packageName = packageName,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

/*
 * Renders full image content with Compose Coil AsyncImage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    filePath: String?,
    packageName: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val imageFile = if (!filePath.isNullOrBlank()) File(filePath) else null
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumTopAppBar(
                title = {
                    Column {
                        Text(
                            text = imageFile?.name ?: stringResource(R.string.image_viewer_activity_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold
                        )
                        if (packageName.isNotBlank()) {
                            Text(
                                text = packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (imageFile != null && imageFile.exists()) {
                                try {
                                    val authority = "${context.packageName}.provider"
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, authority, imageFile))
                                        type = "image/*"
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.image_viewer_activity_share_chooser)))
                                } catch (ignored: Exception) {
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.image_viewer_activity_share_desc))
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
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (imageFile != null && imageFile.exists()) {
                AsyncImage(
                    model = imageFile,
                    contentDescription = imageFile.name,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = stringResource(R.string.image_viewer_activity_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
