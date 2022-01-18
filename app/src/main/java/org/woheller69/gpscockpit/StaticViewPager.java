package org.woheller69.gpscockpit;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.jetbrains.annotations.NotNull;

/**
 * View pager used for a finite, low number of pages, where there is no need for
 * optimization.
 * Taken from https://stackoverflow.com/questions/18710561/can-i-use-view-pager-with-views-not-with-fragments
 */
public class StaticViewPager extends ViewPager {

    /**
     * Initialize the view.
     *
     * @param context
     *            The application context.
     */
    public StaticViewPager(final Context context) {
        super(context);
    }

    /**
     * Initialize the view.
     *
     * @param context
     *            The application context.
     * @param attrs
     *            The requested attributes.
     */
    public StaticViewPager(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Make sure all are loaded at once
        final int childrenCount = getChildCount();
        setOffscreenPageLimit(childrenCount - 1);

        // Attach the adapter
        setAdapter(new PagerAdapter() {

            @NotNull
            @Override
            public Object instantiateItem(final ViewGroup container, final int position) {
                return container.getChildAt(position);
            }

            @Override
            public boolean isViewFromObject(final View arg0, final Object arg1) {
                return arg0 == arg1;

            }

            @Override
            public int getCount() {
                return childrenCount;
            }

            @Override
            public void destroyItem(final ViewGroup container, final int position, final Object object) {}

        });
    }
}
