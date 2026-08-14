package com.wdtt.client.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wdtt.client.TunnelManager
import com.wdtt.client.UpdateChecker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current

    val currentVersion = remember { "v${com.wdtt.client.BuildConfig.VERSION_NAME.removePrefix("v")}" }

    var actionsExpanded by rememberSaveable { mutableStateOf(true) }
    var aboutExpanded by rememberSaveable { mutableStateOf(true) }
    var updateInfo by remember { mutableStateOf<UpdateChecker.ReleaseInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }

    fun openLink(url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Информация",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.primary
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "qWDTT",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = currentVersion,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                Text(
                    text = "WIREGUARD DTLS TURN TUNNEL",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Button(
                    onClick = {
                        openLink("https://yoomoney.ru/quickpay/confirm?receiver=4100117804891098&quickpay-form=button&paymentType=AC&sum=100")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Поддержать проект",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        ExpandableInfoSection(
            title = "Действия",
            subtitle = "2 пункта",
            icon = Icons.Default.Info,
            expanded = actionsExpanded,
            onExpandToggle = { actionsExpanded = !actionsExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoActionButton(
                    icon = Icons.Default.Refresh,
                    text = if (checkingUpdate) "Проверка обновлений…" else "Проверить обновления",
                    enabled = !checkingUpdate,
                    onClick = {
                        if (checkingUpdate) return@InfoActionButton
                        checkingUpdate = true
                        scope.launch {
                            val info = UpdateChecker.fetchLatestRelease(onlyIfNewer = false)
                            checkingUpdate = false
                            val current = com.wdtt.client.BuildConfig.VERSION_NAME
                            when {
                                info == null -> Toast.makeText(context, "Не удалось проверить обновления", Toast.LENGTH_SHORT).show()
                                UpdateChecker.compareVersions(info.version, current) > 0 -> updateInfo = info
                                else -> Toast.makeText(context, "У вас установлена последняя версия", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                InfoActionButton(
                    icon = Icons.Default.ContentCopy,
                    text = "Скопировать системный отчёт",
                    onClick = {
                        val reportText = """
                            Приложение: qWDTT
                            Версия: $currentVersion
                            Android API: ${Build.VERSION.SDK_INT}
                            Архитектура (ABI): ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}
                            Устройство: ${Build.MANUFACTURER} ${Build.MODEL}
                        """.trimIndent()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("qWDTT Report", reportText))
                        Toast.makeText(context, "Отчёт о системе скопирован!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        ExpandableInfoSection(
            title = "О проекте",
            subtitle = "3 ссылки",
            icon = Icons.Default.Code,
            expanded = aboutExpanded,
            onExpandToggle = { aboutExpanded = !aboutExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoActionButton(
                    icon = Icons.Default.Code,
                    text = "Исходный код на GitHub",
                    onClick = { openLink("https://github.com/jewbsv/proxy-turn-vk-android") }
                )

                InfoActionButton(
                    icon = Icons.Default.Favorite,
                    text = "Поддержать проект (100 ₽)",
                    onClick = { openLink("https://yoomoney.ru/quickpay/confirm?receiver=4100117804891098&quickpay-form=button&paymentType=AC&sum=100") }
                )

                InfoActionButton(
                    icon = Icons.Default.Refresh,
                    text = "Проверить обновления",
                    onClick = {
                        if (checkingUpdate) return@InfoActionButton
                        checkingUpdate = true
                        scope.launch {
                            val info = UpdateChecker.fetchLatestRelease(onlyIfNewer = false)
                            checkingUpdate = false
                            val current = com.wdtt.client.BuildConfig.VERSION_NAME
                            when {
                                info == null -> Toast.makeText(context, "Не удалось проверить обновления", Toast.LENGTH_SHORT).show()
                                UpdateChecker.compareVersions(info.version, current) > 0 -> updateInfo = info
                                else -> Toast.makeText(context, "У вас установлена последняя версия", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("Доступна новая версия") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Версия ${info.version}", fontWeight = FontWeight.SemiBold)
                    if (info.body.isNotBlank()) {
                        Text(
                            text = info.body,
                            fontSize = 13.sp,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        UpdateChecker.downloadAndInstall(context, info)
                    } catch (e: Exception) {
                        Log.w("WDTT", "Update download failed: ${e.message}", e)
                        Toast.makeText(
                            context,
                            "Не удалось начать загрузку обновления: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    updateInfo = null
                }) {
                    Text("Обновить")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    try {
                        uriHandler.openUri(info.releaseUrl)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Не удалось открыть браузер", Toast.LENGTH_SHORT).show()
                    }
                    updateInfo = null
                }) {
                    Text("Открыть релиз")
                }
            }
        )
    }
}

@Composable
private fun ExpandableInfoSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onExpandToggle() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(44.dp)
                            .padding(10.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun InfoActionButton(
    icon: ImageVector,
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
