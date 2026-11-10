package com.android.ty.systemservices.compose.ui.screen

import android.app.ActivityManager
import android.content.Context
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.ty.systemservices.R
import com.android.ty.systemservices.compose.db.ContributorEntry
import com.android.ty.systemservices.compose.db.ContributorsDb
import com.android.ty.systemservices.compose.db.ContributorsStats
import com.android.ty.systemservices.compose.ui.widget.CloudRenderResult
import com.android.ty.systemservices.compose.ui.widget.ContributorCloudRenderer
import com.android.ty.systemservices.compose.ui.widget.ContributorCloudView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

/** 持有云图 View 的引用，避免把 View 塞进 Compose 的 state 里 */
private class CloudHolder {
    var view: ContributorCloudView? = null
}

/**
 * 贡献者云界面，复刻 LineageParts 的 `ContributorsCloudFragment`。
 *
 * 布局自上而下：搜索框 + 工具按钮、整块云图、底部选中信息卡。
 * 搜索命中后点列表项，云图会缩放到该人名并把名字改成强调色。
 *
 * [initialQuery] 来自贡献者卡片：进入后自动查找并定位；库里没有这个人时只提示，不影响浏览。
 */
@Composable
fun ContributorsDbScreen(initialQuery: String? = null) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    val holder = remember { CloudHolder() }
    val db = remember { ContributorsDb(context) }
    DisposableEffect(Unit) {
        onDispose {
            holder.view?.clear()
            db.close()
        }
    }

    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val accentColor = MaterialTheme.colorScheme.primary.toArgb()
    val backgroundArgb = MaterialTheme.colorScheme.surfaceContainer.toArgb()

    val bitmapSize = remember { preferredBitmapSize(context) }
    val designFallback = remember { DEFAULT_DESIGN_SIZE }

    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<ContributorEntry>>(emptyList()) }
    var designSize by remember { mutableIntStateOf(designFallback) }
    var stats by remember { mutableStateOf<ContributorsStats?>(null) }

    var query by remember { mutableStateOf(initialQuery.orEmpty()) }
    var results by remember { mutableStateOf<List<ContributorEntry>>(emptyList()) }
    var selected by remember { mutableStateOf<ContributorEntry?>(null) }
    var missingName by remember { mutableStateOf<String?>(null) }
    var showStats by remember { mutableStateOf(false) }

    var cloudVersion by remember { mutableIntStateOf(0) }
    var pendingFocus by remember { mutableStateOf<Int?>(null) }

    /** 渲染一次云图：主线程之外画，画完直接换给 View */
    suspend fun renderToView(selectedId: Int?): CloudRenderResult = withContext(Dispatchers.Default) {
        ContributorCloudRenderer.render(
            entries = entries,
            designSize = designSize,
            selectedId = selectedId,
            bitmapSize = bitmapSize,
            textColor = textColor,
            selectedColor = accentColor,
            typeface = Typeface.DEFAULT,
        )
    }

    // 首次加载：解析 assets 里的 db → 读全量 → 画第一张图
    LaunchedEffect(Unit) {
        val ready = withContext(Dispatchers.IO) {
            if (!db.open()) return@withContext false
            entries = db.all()
            designSize = db.sourceSize()
            stats = db.stats()
            entries.isNotEmpty()
        }
        loading = false
        failed = !ready
        if (!ready) return@LaunchedEffect

        val first = renderToView(selectedId = null)
        holder.view?.setCloud(first, keepTransform = false)
        cloudVersion++

        val target = initialQuery?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        val entry = withContext(Dispatchers.IO) { db.find(target) }
        if (entry != null) {
            selected = entry
            pendingFocus = entry.id
        } else {
            missingName = target
        }
    }

    // 搜索：输入停顿 180ms 再查，避免每次按键都扫库
    LaunchedEffect(query) {
        val keyword = query.trim()
        if (keyword.isEmpty() || entries.isEmpty()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(180)
        results = withContext(Dispatchers.IO) { db.search(keyword) }
    }

    // 选中项变化 → 重画（换强调色）
    LaunchedEffect(selected?.id, entries.size) {
        val id = selected?.id ?: return@LaunchedEffect
        if (entries.isEmpty()) return@LaunchedEffect
        val rendered = renderToView(selectedId = id)
        holder.view?.setCloud(rendered, keepTransform = true)
        cloudVersion++
    }

    // 图换好之后再聚焦，保证 View 已经拿到最新的人名中心点
    LaunchedEffect(cloudVersion) {
        val id = pendingFocus ?: return@LaunchedEffect
        holder.view?.focusOn(id)
        pendingFocus = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CloudSearchBar(
                query = query,
                onQueryChange = {
                    query = it
                    missingName = null
                },
                onClear = {
                    query = ""
                    results = emptyList()
                    missingName = null
                },
                onResetView = { holder.view?.resetView() },
                onShowStats = { showStats = true },
            )

            missingName?.let { name ->
                MissingBanner(
                    name = name,
                    onDismiss = { missingName = null },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                AndroidView(
                    factory = { ctx ->
                        ContributorCloudView(ctx).apply {
                            canvasColor = backgroundArgb
                            holder.view = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                if (loading) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = stringResource(R.string.contributors_cloud_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (failed) {
                    Text(
                        text = stringResource(R.string.contributors_cloud_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }

                if (results.isNotEmpty()) {
                    SearchResults(
                        results = results,
                        onSelect = { entry ->
                            selected = entry
                            pendingFocus = entry.id
                            results = emptyList()
                            missingName = null
                            keyboard?.hide()
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                // 显式指定顶层版本：外层是 Column，隐式 receiver 会优先匹配 ColumnScope 的重载
                androidx.compose.animation.AnimatedVisibility(
                    visible = selected != null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    selected?.let { entry ->
                        ContributorInfoCard(
                            entry = entry,
                            onLocate = { holder.view?.focusOn(entry.id) },
                            onDismiss = { selected = null },
                        )
                    }
                }
            }
        }
    }

    if (showStats) {
        ContributorsStatsDialog(stats = stats, onDismiss = { showStats = false })
    }
}

/** 顶部：搜索框 + 重置视图 + 统计信息 */
@Composable
private fun CloudSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onResetView: () -> Unit,
    onShowStats: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            placeholder = { Text(stringResource(R.string.contributors_cloud_search_hint)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.contributors_cloud_clear),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        IconButton(onClick = onResetView) {
            Icon(
                imageVector = Icons.Filled.ZoomOutMap,
                contentDescription = stringResource(R.string.contributors_cloud_reset),
            )
        }
        IconButton(onClick = onShowStats) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = stringResource(R.string.contributors_cloud_stats_title),
            )
        }
    }
}

/** 搜索结果列表：浮在云图上方，最多占 320dp 高 */
@Composable
private fun SearchResults(
    results: List<ContributorEntry>,
    onSelect: (ContributorEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(results, key = { it.id }) { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        entry.username?.takeIf { it.isNotBlank() }?.let { username ->
                            Text(
                                text = "@$username",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = stringResource(
                                R.string.contributors_cloud_commits,
                                NumberFormat.getIntegerInstance().format(entry.commits),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
            }
        }
    }
}

/** 从卡片跳进来但库里没有这个名字时的提示条 */
@Composable
private fun MissingBanner(name: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.SearchOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.contributors_cloud_not_found, name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.contributors_cloud_clear),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** 底部选中信息卡：名字、用户名、提交数 + 重新定位 */
@Composable
private fun ContributorInfoCard(
    entry: ContributorEntry,
    onLocate: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.contributors_cloud_close),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            entry.username?.takeIf { it.isNotBlank() }?.let { username ->
                Text(
                    text = "@$username",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(
                    R.string.contributors_cloud_commits,
                    NumberFormat.getIntegerInstance(Locale.getDefault()).format(entry.commits),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onLocate) {
                    Text(stringResource(R.string.contributors_cloud_locate))
                }
            }
        }
    }
}

/** 统计信息：贡献者数 / 提交数 / 数据日期 */
@Composable
private fun ContributorsStatsDialog(stats: ContributorsStats?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val numberFormat = remember { NumberFormat.getIntegerInstance() }

    val contributors = stats?.contributors?.takeIf { it >= 0 }?.let { numberFormat.format(it) }
        ?: "-"
    val commits = stats?.commits?.takeIf { it >= 0 }?.let { numberFormat.format(it) } ?: "-"
    val updated = stats?.updatedAt?.takeIf { it > 0 }?.let { formatDateTime(context, it) } ?: "-"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contributors_cloud_stats_title)) },
        text = {
            Text(
                stringResource(
                    R.string.contributors_cloud_stats_message,
                    contributors,
                    commits,
                    updated,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.contributors_cloud_close))
            }
        },
    )
}

private fun formatDateTime(context: Context, millis: Long): String {
    val date = android.text.format.DateFormat.getLongDateFormat(context).format(millis)
    val time = android.text.format.DateFormat.getTimeFormat(context).format(millis)
    return "$date $time"
}

/**
 * 位图边长：与 LineageParts 一样按 heap 选。
 * 2048² 的 ARGB_8888 占 16MB，低内存设备降到 1024²。
 */
private fun preferredBitmapSize(context: Context): Int {
    val am = context.getSystemService(ActivityManager::class.java)
    return if ((am?.memoryClass ?: 0) >= 96) 2048 else 1024
}

/** db 里 orig_size 缺失时的兜底设计稿尺寸 */
private const val DEFAULT_DESIGN_SIZE = 2056
