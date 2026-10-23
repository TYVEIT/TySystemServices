package com.android.ty.systemservices.compose.ui.preference

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.android.ty.systemservices.compose.ui.theme.CyanBlue
import com.android.ty.systemservices.compose.ui.theme.TyTheme

/**
 * [ComposeMultiSelectListPreference] 的对话框，等价于系统的
 * `MultiSelectListPreferenceDialogFragmentCompat`，但内容区改为 Compose 渲染，
 * 保证与其它 Compose Preference 的视觉一致（青蓝主色、圆角行、M3 复选框）。
 *
 * 对话框外框仍用 AppCompat 的 [AlertDialog]，与项目里
 * ListPreference / EditTextPreference 的原生对话框保持同一套按钮与标题样式。
 *
 * 需要由 [BasePreferenceFragment.onDisplayPreferenceDialog] 通过
 * `setTargetFragment` 绑定宿主 Fragment 后显示。
 */
class ComposeMultiSelectListPreferenceDialogFragment : PreferenceDialogFragmentCompat() {

    /** Compose 侧直接读写的选中集合，确认时再一次性写回 Preference */
    private val selectedValues = mutableStateListOf<String>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val pref = preference as? MultiSelectListPreference
        val context = requireContext()

        // 每次打开都从 Preference 当前值重建，避免复用到旧的选中状态
        selectedValues.clear()
        pref?.values?.let { selectedValues.addAll(it) }

        val entryValues = pref?.entryValues?.map { it?.toString().orEmpty() }.orEmpty()
        val entries = pref?.entries?.map { it?.toString().orEmpty() }.orEmpty()
        val items = entryValues.mapIndexed { index, value ->
            value to entries.getOrElse(index) { value }
        }

        val content = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TyTheme {
                    MultiSelectPanel(items = items, selected = selectedValues)
                }
            }
        }

        return AlertDialog.Builder(context)
            .setTitle(pref?.dialogTitle ?: pref?.title)
            .setView(content)
            .setPositiveButton(
                pref?.positiveButtonText ?: context.getText(android.R.string.ok),
            ) { _, _ -> onDialogClosed(true) }
            .setNegativeButton(
                pref?.negativeButtonText ?: context.getText(android.R.string.cancel),
            ) { _, _ -> onDialogClosed(false) }
            .create()
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (!positiveResult) return
        val pref = preference as? MultiSelectListPreference ?: return
        val newValues = selectedValues.toSet()
        // 与系统实现一致：先询问监听器，被拒绝则不落库
        if (pref.callChangeListener(newValues)) {
            pref.values = newValues
        }
    }

    companion object {
        private const val ARG_KEY = "key"

        /** 与 [PreferenceDialogFragmentCompat] 约定一致，用 key 找到宿主 Preference */
        @JvmStatic
        fun newInstance(key: String?): ComposeMultiSelectListPreferenceDialogFragment {
            val fragment = ComposeMultiSelectListPreferenceDialogFragment()
            fragment.arguments = Bundle().apply { putString(ARG_KEY, key) }
            return fragment
        }
    }
}

/**
 * 多选列表内容区：整行可点，右侧 M3 复选框；超出高度后滚动。
 */
@Composable
private fun MultiSelectPanel(
    items: List<Pair<String, String>>,
    selected: SnapshotStateList<String>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        items.forEach { (value, label) ->
            val checked = value in selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .toggleable(
                        value = checked,
                        role = Role.Checkbox,
                        onValueChange = {
                            if (checked) selected.remove(value) else selected.add(value)
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = CyanBlue),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
