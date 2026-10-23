package com.android.ty.systemservices;

import android.content.Context;
import android.util.AttributeSet;
import com.android.ty.systemservices.R;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceViewHolder;

/**
 * 卡片化的 EditTextPreference。
 *
 * XML 中引用 <com.android.setup.CardEditTextPreference> 即可，
 * 无需 Fragment 手动分配位置：onBindViewHolder 会自动根据
 * 父分组中兄弟卡片的顺序，计算 TOP/MIDDLE/BOTTOM/SINGLE 圆角。
 */
public class CardEditTextPreference extends EditTextPreference {

    public CardEditTextPreference(@NonNull Context context) {
        super(context);
        init();
    }

    public CardEditTextPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CardEditTextPreference(@NonNull Context context, @Nullable AttributeSet attrs,
                                   int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public CardEditTextPreference(@NonNull Context context, @Nullable AttributeSet attrs,
                                   int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        setLayoutResource(R.layout.card_preference);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        int position = CardPreference.resolvePosition(this);
        CardPreference.applyBackground(holder.itemView, position);
    }
}
