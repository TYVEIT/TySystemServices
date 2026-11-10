package com.android.ty.systemservices.compose

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import com.android.ty.systemservices.BackButtonHelper
import com.android.ty.systemservices.CollapsingToolbarBaseActivity
import com.android.ty.systemservices.R
import com.android.ty.systemservices.TitleStyle
import com.android.ty.systemservices.compose.ui.screen.ContributorsDbScreen
import com.android.ty.systemservices.compose.ui.theme.TyTheme

/**
 * 贡献者云宿主：复刻 LineageParts 的 `ContributorsCloudFragment`。
 *
 * 与 [ContributorsActivity] 同一套大标题体系，内容区放 [ContributorsDbScreen]。
 *
 * 入口参数 [EXTRA_QUERY] 是贡献者名字：从卡片点进来时带上，进入后自动在云图里定位；
 * 直接打开（不带参数）就只是浏览全部名字云。
 */
class ContributorsDbActivity : CollapsingToolbarBaseActivity(TitleStyle.MAIN) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setBackButton(BackButtonHelper.isFromOwnActivity(this))
        setLargeTitle(R.string.contributors_cloud_title)

        val query = intent?.getStringExtra(EXTRA_QUERY)

        val contentFrame = findViewById<FrameLayout>(R.id.content_frame)
        val composeView = ComposeView(this).apply {
            setContent {
                TyTheme {
                    ContributorsDbScreen(initialQuery = query)
                }
            }
        }
        contentFrame.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    companion object {
        /** 要定位的贡献者名字（对应 db 的 `name` 或 `username`） */
        const val EXTRA_QUERY = "extra_query"

        fun newIntent(context: Context, query: String?): Intent =
            Intent(context, ContributorsDbActivity::class.java).apply {
                if (!query.isNullOrBlank()) putExtra(EXTRA_QUERY, query)
            }
    }
}
