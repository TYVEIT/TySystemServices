package com.android.ty.systemservices;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceViewHolder;

import com.android.ty.systemservices.R;

import java.util.ArrayList;
import java.util.List;

public class CardPreference extends Preference {

    public static final int POSITION_TOP = 0;
    public static final int POSITION_MIDDLE = 1;
    public static final int POSITION_BOTTOM = 2;
    public static final int POSITION_SINGLE = 3;

    public CardPreference(Context context) {
        super(context);
        setLayoutResource(R.layout.card_preference);
    }

    public CardPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.card_preference);
    }

    public CardPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayoutResource(R.layout.card_preference);
    }

    /**
     * 根据当前 Preference 在父分组中的位置，自动计算卡片圆角位置。
     * 无需 Fragment 手动调用，onBindViewHolder 时自动执行。
     *
     * @param target 需要计算位置的卡片 Preference
     * @return POSITION_TOP / MIDDLE / BOTTOM / SINGLE
     */
    public static int resolvePosition(Preference target) {
        PreferenceGroup parent = target.getParent();
        if (parent == null) {
            return POSITION_SINGLE;
        }

        List<Preference> cardItems = new ArrayList<>();
        for (int i = 0; i < parent.getPreferenceCount(); i++) {
            Preference child = parent.getPreference(i);
            if (child instanceof CardPreference
                    || child instanceof CardSwitchPreference
                    || child instanceof CardEditTextPreference
                    || child instanceof CardListPreference) {
                cardItems.add(child);
            }
        }

        int size = cardItems.size();
        if (size <= 1) {
            return POSITION_SINGLE;
        }

        int index = -1;
        for (int i = 0; i < size; i++) {
            if (cardItems.get(i) == target) {
                index = i;
                break;
            }
        }

        if (index == -1) {
            return POSITION_SINGLE;
        }
        if (index == 0) {
            return POSITION_TOP;
        } else if (index == size - 1) {
            return POSITION_BOTTOM;
        } else {
            return POSITION_MIDDLE;
        }
    }

    /**
     * 将计算出的位置应用到 itemView 背景。
     */
    static void applyBackground(View itemView, int position) {
        switch (position) {
            case POSITION_TOP:
                itemView.setBackgroundResource(R.drawable.card_style_top);
                break;
            case POSITION_MIDDLE:
                itemView.setBackgroundResource(R.drawable.card_style_middle);
                break;
            case POSITION_BOTTOM:
                itemView.setBackgroundResource(R.drawable.card_style_bottom);
                break;
            case POSITION_SINGLE:
                itemView.setBackgroundResource(R.drawable.card_style_single);
                break;
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        int position = resolvePosition(this);
        applyBackground(holder.itemView, position);
    }
}
