package com.android.ty.systemservices

import android.app.Activity

/**
 * 返回按钮显隐判断：是否由本应用内的其它 Activity 启动。
 *
 * [Activity.isTaskRoot] 为 false 表示任务栈里还有上一个 Activity（即从本应用其它页面进入），
 * 此时应显示返回按钮；为 true 表示它是任务栈入口（从桌面/外部启动），隐藏返回按钮。
 */
class BackButtonHelper {
    companion object {
        @JvmStatic
        fun isFromOwnActivity(activity: Activity): Boolean = !activity.isTaskRoot
    }
}
