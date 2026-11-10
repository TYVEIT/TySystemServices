package com.android.ty.systemservices.compose.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.android.ty.systemservices.R
import com.android.ty.systemservices.compose.ContributorsDbActivity

/**
 * 贡献者条目的展示模型。
 *
 * 顶部区域是 16:9 的整块画布：
 * - [imageRes] 有值时按 `ContentScale.Crop` 铺满（自定义图，建议 1080×608）；
 * - 为 null 时回落到 [icon] 的内置矢量图标，居中显示。
 *
 * [dbQuery] 是点击卡片后到贡献者云里查找的关键字，留空则用卡片标题本身。
 * 想精确指向数据库里的某个人时填它的 `name` 或 `username`。
 */
private data class Contributor(
    @StringRes val nameRes: Int,
    @StringRes val roleRes: Int,
    @StringRes val summaryRes: Int,
    @DrawableRes val imageRes: Int? = null,
    val icon: ImageVector? = null,
    val dbQuery: String? = null,
)

/** 示例数据，按实际贡献者替换即可 */
private val contributors = listOf(
    Contributor(
        nameRes = R.string.contributor_name_teyun,
        roleRes = R.string.contributor_role_maintainer,
        summaryRes = R.string.contributor_summary_maintainer,
        imageRes = R.drawable.contributor_mig_tyt,
    ),
    Contributor(
        nameRes = R.string.contributor_name_supporter,
        roleRes = R.string.contributor_role_supporter,
        summaryRes = R.string.contributor_summary_supporter,
        imageRes = R.drawable.contributor_name,
    ),
    Contributor(
        nameRes = R.string.contributor_name_lineageos,
        roleRes = R.string.contributor_role_reference,
        summaryRes = R.string.contributor_summary_reference,
        icon = Icons.Filled.Code,
    ),
    Contributor(
        nameRes = R.string.contributor_name_deepseek,
        roleRes = R.string.contributor_role_assistant,
        summaryRes = R.string.contributor_summary_assistant,
        icon = Icons.Filled.Extension,
    ),
)

/**
 * 贡献者列表页。
 *
 * 宿主 [com.android.ty.systemservices.compose.ContributorsActivity] 走大标题体系，
 * 本页不再自带 `TopAppBar`；`LazyColumn` 通过 nestedScroll 与折叠头联动。
 * 卡片为「大图标区 + 底部标题条」，整卡可点，带 M3 水波纹。
 */
@Composable
fun ContributorsScreen() {
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                // 与 CollapsingToolbar 的 AppBar 联动
                .nestedScroll(rememberNestedScrollInteropConnection())
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(contributors) { contributor ->
                // stringResource 只能在 composable 作用域里取，先取出来再交给点击回调
                val displayName = stringResource(contributor.nameRes)
                ContributorCard(
                    contributor = contributor,
                    onClick = {
                        context.startActivity(
                            ContributorsDbActivity.newIntent(
                                context,
                                contributor.dbQuery ?: displayName,
                            ),
                        )
                    },
                )
            }
        }
    }
}

/**
 * 单个贡献者卡片：顶部 16:9 大图标区，底部名称 / 角色 / 描述。
 *
 * `Card(onClick = ...)` 自带 M3 水波纹，点击反馈裁在 16dp 圆角内。
 */
@Composable
private fun ContributorCard(contributor: Contributor, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright,
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                val imageRes = contributor.imageRes
                if (imageRes != null) {
                    // 自定义图按 16:9 裁切铺满，避免变形
                    Image(
                        painter = painterResource(imageRes),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = contributor.icon ?: Icons.Filled.Build,
                        contentDescription = null,
                        modifier = Modifier.size(88.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(contributor.nameRes),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(contributor.roleRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = stringResource(contributor.summaryRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
