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

package com.ameermuawiya.decompilers.managers

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ameermuawiya.decompilers.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val body: String,
    val htmlUrl: String
)

/*
 * Manages GitHub release checks and renders the update announcement bottom sheet.
 */
object UpdateManager {

    private const val RELEASES_LATEST_API_URL = "https://api.github.com/repos/AmeerMuawiya/APK-Decompiler-Pro/releases/latest"
    private const val RELEASES_LIST_API_URL = "https://api.github.com/repos/AmeerMuawiya/APK-Decompiler-Pro/releases"

    /*
     * Queries latest release metadata from GitHub API and dispatches update, up-to-date, or error callbacks.
     */
    fun checkForUpdates(
        context: Context,
        scope: CoroutineScope,
        isManual: Boolean,
        onChecking: () -> Unit = {},
        onUpdateAvailable: (ReleaseInfo) -> Unit,
        onNoUpdate: (String) -> Unit = {},
        onNoReleaseFound: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (isManual) {
            onChecking()
        }
        scope.launch(Dispatchers.IO) {
            try {
                val currentVersion = try {
                    val pi = context.packageManager.getPackageInfo(context.packageName, 0)
                    pi.versionName?.removePrefix("v")?.removePrefix("V")?.trim() ?: "1.0"
                } catch (e: Exception) {
                    "1.0"
                }

                val latestConn = openGitHubConnection(RELEASES_LATEST_API_URL)
                val responseCode = latestConn.responseCode

                var releaseJson: JSONObject? = null
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = latestConn.inputStream.bufferedReader().use { it.readText() }
                    releaseJson = JSONObject(responseText)
                } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    val listConn = openGitHubConnection(RELEASES_LIST_API_URL)
                    val listCode = listConn.responseCode
                    if (listCode == HttpURLConnection.HTTP_OK) {
                        val listText = listConn.inputStream.bufferedReader().use { it.readText() }
                        val jsonArray = org.json.JSONArray(listText)
                        if (jsonArray.length() > 0) {
                            releaseJson = jsonArray.getJSONObject(0)
                        } else if (isManual) {
                            withContext(Dispatchers.Main) {
                                onNoReleaseFound()
                            }
                            return@launch
                        }
                    } else if (listCode == HttpURLConnection.HTTP_NOT_FOUND) {
                        if (isManual) {
                            withContext(Dispatchers.Main) {
                                onNoReleaseFound()
                            }
                        }
                        return@launch
                    } else if (listCode == HttpURLConnection.HTTP_FORBIDDEN || listCode == 429) {
                        if (isManual) {
                            withContext(Dispatchers.Main) {
                                onError(context.getString(R.string.settings_screen_update_rate_limit_error))
                            }
                        }
                        return@launch
                    } else {
                        if (isManual) {
                            withContext(Dispatchers.Main) {
                                onError(context.getString(R.string.settings_screen_update_http_error, listCode))
                            }
                        }
                        return@launch
                    }
                } else if (responseCode == HttpURLConnection.HTTP_FORBIDDEN || responseCode == 429) {
                    if (isManual) {
                        withContext(Dispatchers.Main) {
                            onError(context.getString(R.string.settings_screen_update_rate_limit_error))
                        }
                    }
                    return@launch
                } else {
                    if (isManual) {
                        withContext(Dispatchers.Main) {
                            onError(context.getString(R.string.settings_screen_update_http_error, responseCode))
                        }
                    }
                    return@launch
                }

                if (releaseJson == null) {
                    if (isManual) {
                        withContext(Dispatchers.Main) {
                            onNoReleaseFound()
                        }
                    }
                    return@launch
                }

                val tagName = releaseJson.optString("tag_name", "").trim()
                if (tagName.isBlank()) {
                    if (isManual) {
                        withContext(Dispatchers.Main) {
                            onNoReleaseFound()
                        }
                    }
                    return@launch
                }

                val htmlUrl = releaseJson.optString("html_url", "https://github.com/AmeerMuawiya/APK-Decompiler-Pro/releases")
                val body = releaseJson.optString("body", "")
                val cleanRemote = tagName.removePrefix("v").removePrefix("V").trim()

                if (isVersionGreater(cleanRemote, currentVersion)) {
                    withContext(Dispatchers.Main) {
                        onUpdateAvailable(
                            ReleaseInfo(
                                tagName = tagName,
                                versionName = cleanRemote,
                                body = body,
                                htmlUrl = htmlUrl
                            )
                        )
                    }
                } else if (isManual) {
                    withContext(Dispatchers.Main) {
                        onNoUpdate(currentVersion)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking for updates from GitHub")
                if (isManual) {
                    val errorMsg = when (e) {
                        is java.net.UnknownHostException, is java.net.ConnectException ->
                            context.getString(R.string.settings_screen_update_network_error)
                        is java.net.SocketTimeoutException ->
                            context.getString(R.string.settings_screen_update_timeout_error)
                        else ->
                            context.getString(
                                R.string.settings_screen_update_error,
                                e.localizedMessage ?: e.javaClass.simpleName
                            )
                    }
                    withContext(Dispatchers.Main) {
                        onError(errorMsg)
                    }
                }
            }
        }
    }

    /*
     * Opens an HTTP connection to the specified GitHub API endpoint with standard headers.
     */
    private fun openGitHubConnection(endpoint: String): HttpURLConnection {
        val url = URL(endpoint)
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github.v3+json")
            setRequestProperty("User-Agent", "APK-Decompiler-Pro")
        }
    }

    /*
     * Compares remote semantic version string against installed version string.
     */
    fun isVersionGreater(remote: String, current: String): Boolean {
        try {
            val remoteParts = remote.split(".").mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() }
            val currentParts = current.split(".").mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() }
            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
        } catch (ignored: Exception) {
        }
        return false
    }
}

/*
 * Bottom sheet displaying latest version release details, markdown notes, and download action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateBottomSheet(
    releaseInfo: ReleaseInfo,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.update_sheet_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.update_sheet_version_available, releaseInfo.versionName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (releaseInfo.body.isNotBlank()) {
                Text(
                    text = stringResource(R.string.update_sheet_release_notes_label),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(14.dp)
                    ) {
                        releaseInfo.body.split("\n").forEach { line ->
                            val trimmed = line.trim()
                            when {
                                trimmed.startsWith("###") -> {
                                    Text(
                                        text = trimmed.removePrefix("###").trim(),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                trimmed.startsWith("##") -> {
                                    Text(
                                        text = trimmed.removePrefix("##").trim(),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                trimmed.startsWith("*") || trimmed.startsWith("-") -> {
                                    val bulletText = trimmed.drop(1).trim().replace("**", "")
                                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text(
                                            text = "•  ",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = bulletText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                trimmed.isNotBlank() -> {
                                    Text(
                                        text = trimmed.replace("**", ""),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            val context = androidx.compose.ui.platform.LocalContext.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.update_sheet_button_later))
                }

                Button(
                    onClick = {
                        onDismiss()
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseInfo.htmlUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Timber.e(e, "Error opening release download link")
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.update_sheet_button_download))
                }
            }
        }
    }
}
