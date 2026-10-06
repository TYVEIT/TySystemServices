package com.android.ty.systemservices.compose.ui

import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import com.android.ty.systemservices.compose.preference.SettingsFragment

/**
 * 在 Compose 树中承载 PreferenceFragment 的包装页。
 *
 * 通过 AndroidView + FragmentContainerView 把 [SettingsFragment] 嵌入 Compose，
 * Fragment 内部的自定义 Preference 再用 Compose 渲染，
 * 形成 "Compose → Fragment → Preference → Compose" 的嵌套结构。
 */
@Composable
fun PreferencePage(
    fragmentManager: FragmentManager,
    onBack: () -> Unit,
) {
    val containerId = rememberContainerId()

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            FragmentContainerView(ctx).apply { id = containerId }
        },
        update = { container ->
            // 确保 Fragment 事务在容器附着到窗口后执行，避免时序崩溃
            container.post {
                val existing = fragmentManager.findFragmentById(container.id)
                if (existing == null && container.id != View.NO_ID) {
                    fragmentManager.commit {
                        replace(container.id, SettingsFragment())
                    }
                }
            }
        },
    )

    // 页面退出时移除 Fragment，避免泄漏
    DisposableEffect(containerId) {
        onDispose {
            val fragment = fragmentManager.findFragmentById(containerId)
            if (fragment != null) {
                fragmentManager.commit { remove(fragment) }
            }
        }
    }
}

/** 生成一个稳定的容器 View ID（避免重复创建冲突） */
@Composable
private fun rememberContainerId(): Int {
    return androidx.compose.runtime.remember { View.generateViewId() }
}
