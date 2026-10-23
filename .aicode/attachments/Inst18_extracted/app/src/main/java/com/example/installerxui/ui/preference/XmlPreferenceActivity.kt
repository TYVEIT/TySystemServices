package com.example.installerxui.ui.preference

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.installerxui.R
import com.example.installerxui.ui.widget.setting.BigTitleImpl
import com.example.installerxui.ui.widget.setting.ExpressiveCollapsingToolbarLayout
import com.example.installerxui.ui.widget.setting.ExpressiveCollapsingToolbarLayout_Two
import com.example.installerxui.ui.widget.setting.loadBigTitleImpl
import com.example.installerxui.ui.widget.setting.rootImplementation

class XmlPreferenceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 恢复持久化的大标题栏实现选择（进程重启后仍保留）
        rootImplementation = loadBigTitleImpl(this)

        // 按 DropDownMenuWidget 的选择切换 One / Two / Original 实现：
        // ONE -> activity_preference（200dp 自动注入，无手写容器）
        // TWO -> activity_preference_two（200dp 手写容器）
        // ORIGINAL -> activity_preference_original（普通固定顶部栏）
        val layoutRes = when (rootImplementation) {
            BigTitleImpl.ONE -> R.layout.activity_preference
            BigTitleImpl.TWO -> R.layout.activity_preference_two
            BigTitleImpl.ORIGINAL -> R.layout.activity_preference_original
        }
        setContentView(layoutRes)

        val toolbar = findViewById<Toolbar>(R.id.action_bar)
        setSupportActionBar(toolbar)

        // 大标题文字：CollapsingToolbarLayout 展开态显示
        when (rootImplementation) {
            BigTitleImpl.ONE -> findViewById<ExpressiveCollapsingToolbarLayout>(R.id.collapsing_toolbar)
                .setTitle(getString(R.string.app_name))
            BigTitleImpl.TWO -> findViewById<ExpressiveCollapsingToolbarLayout_Two>(R.id.collapsing_toolbar)
                .setTitle(getString(R.string.app_name))
            BigTitleImpl.ORIGINAL -> supportActionBar?.title = getString(R.string.app_name)
        }

        // 通过子类控制返回按钮：true 开启，false 关闭（两个子类接口一致）
        when (rootImplementation) {
            BigTitleImpl.ONE -> findViewById<ExpressiveCollapsingToolbarLayout>(R.id.collapsing_toolbar)
                .setBackButtonEnabled(true)
            BigTitleImpl.TWO -> findViewById<ExpressiveCollapsingToolbarLayout_Two>(R.id.collapsing_toolbar)
                .setBackButtonEnabled(true)
            BigTitleImpl.ORIGINAL -> supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.content_frame, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
