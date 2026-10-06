package com.android.ty.systemservices;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.android.ty.systemservices.R;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;

/**
 * 模仿 Android 12 Settings 的“大标题”基类。
 *
 * 头部（AppBarLayout + CollapsingToolbarLayout）始终浮在内容上方，
 * 并通过 canDrag() = false 关闭随列表滚动自动折叠的行为，
 * 展开/收起由子类通过 {@link #setToolbarExpanded} 主动控制（例如跳转二级页时收起）。
 */
public abstract class CollapsingToolbarBaseActivity extends AppCompatActivity {

    private FrameLayout contentFrame;
    private CollapsingToolbarLayout collapsingToolbarLayout;
    private AppBarLayout appBarLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 先加载头部布局本身，子类再把内容放进 content_frame
        super.setContentView(R.layout.activity_collapsing_toolbar);

        contentFrame = findViewById(R.id.content_frame);
        collapsingToolbarLayout = findViewById(R.id.collapsing_toolbar);
        appBarLayout = findViewById(R.id.app_bar);

        Toolbar toolbar = findViewById(R.id.action_bar);
        if (getSupportActionBar() == null && toolbar != null) {
            setSupportActionBar(toolbar);
        } else if (toolbar != null) {
            toolbar.setVisibility(View.GONE);
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (appBarLayout != null) {
            disableAutoCollapse(appBarLayout);
        }
    }

    /** 子类调用 setContentView 时，把内容放到 content_frame，头部保持不变 */
    @Override
    public void setContentView(int layoutResID) {
        if (contentFrame != null) {
            contentFrame.removeAllViews();
            LayoutInflater.from(this).inflate(layoutResID, contentFrame, true);
        }
    }

    /** 设置展开态的大标题 */
    public void setLargeTitle(CharSequence title) {
        if (collapsingToolbarLayout != null) {
            collapsingToolbarLayout.setTitle(title);
        }
    }

    public void setLargeTitle(int titleRes) {
        setLargeTitle(getString(titleRes));
    }

    /** 主动展开 / 收起头部（Android 12 顶层页跳二级页时收起） */
    public void setToolbarExpanded(boolean expanded) {
        setToolbarExpanded(expanded, true);
    }

    public void setToolbarExpanded(boolean expanded, boolean animate) {
        if (appBarLayout != null) {
            appBarLayout.setExpanded(expanded, animate);
        }
    }
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void disableAutoCollapse(AppBarLayout appBar) {
        AppBarLayout.Behavior behavior = new AppBarLayout.Behavior();
        behavior.setDragCallback(new AppBarLayout.Behavior.DragCallback() {
            @Override
            public boolean canDrag(AppBarLayout appBarLayout) {
                return false;
            }
        });
        CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) appBar.getLayoutParams();
        lp.setBehavior(behavior);
    }
}
