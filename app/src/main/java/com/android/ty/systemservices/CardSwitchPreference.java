package com.android.ty.systemservices;

import android.content.Context;
import android.util.AttributeSet;
import com.android.ty.systemservices.R;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;

public class CardSwitchPreference extends SwitchPreferenceCompat {

    public CardSwitchPreference(Context context) {
        super(context);
        setLayoutResource(R.layout.card_preference);
        setWidgetLayoutResource(R.layout.widget_switch_android16);
    }

    public CardSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.card_preference);
        setWidgetLayoutResource(R.layout.widget_switch_android16);
    }

    public CardSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayoutResource(R.layout.card_preference);
        setWidgetLayoutResource(R.layout.widget_switch_android16);
    }

    public CardSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setLayoutResource(R.layout.card_preference);
        setWidgetLayoutResource(R.layout.widget_switch_android16);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        int position = CardPreference.resolvePosition(this);
        CardPreference.applyBackground(holder.itemView, position);
    }
}
