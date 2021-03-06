/*
 * Copyright (C) 2010 The Android Open Source Project
 * This code has been modified. Portions copyright (C) 2012, ParanoidAndroid Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy;

import java.util.ArrayList;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff;
import android.os.BatteryManager;
import android.os.Handler;
import android.provider.Settings;
import android.util.ColorUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;

public class BatteryController extends BroadcastReceiver {
    private static final String TAG = "StatusBar.BatteryController";

    private Context mContext;
    private ArrayList<ImageView> mIconViews = new ArrayList<ImageView>();
    private ArrayList<TextView> mLabelViews = new ArrayList<TextView>();

    public static final int BATTERY_STYLE_NORMAL         = 0;
    /***
     * BATTERY_STYLE_CIRCLE* cannot be handled in this controller, since we cannot get views from
     * statusbar here. Yet it is listed for completion and not to confuse at future updates
     * See CircleBattery.java for more info
     *
     * set to public to be reused by CircleBattery
     */
    public static final int BATTERY_STYLE_CIRCLE         = 1;
    public static final int BATTERY_STYLE_PERCENT        = 2; // Not Used
    public static final int BATTERY_STYLE_CIRCLE_PERCENT = 3; // Not Used
    public static final int BATTERY_STYLE_GONE           = 4; // Not Used


    private static final int BATTERY_TEXT_STYLE_NORMAL  = R.string.status_bar_settings_battery_meter_format;

    protected int mLevel;
    private boolean mBatteryPlugged = false;
    private int mBatteryStatus = BatteryManager.BATTERY_STATUS_UNKNOWN;
    private int mBatteryStyle;
    private ColorUtils.ColorSettingInfo mColorInfo;

    Handler mHandler;

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CIRCLE_BATTERY), false, this);
        }

        @Override public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    private ArrayList<BatteryStateChangeCallback> mChangeCallbacks =
            new ArrayList<BatteryStateChangeCallback>();

    public interface BatteryStateChangeCallback {
        public void onBatteryLevelChanged(int level, boolean pluggedIn);
    }

    public BatteryController(Context context) {
        mContext = context;
        mHandler = new Handler();

        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
        updateSettings();

        mColorInfo = ColorUtils.getColorSettingInfo(context, Settings.System.STATUS_ICON_COLOR);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        mContext.registerReceiver(this, filter);
    }

    public void addIconView(ImageView v) {
        mIconViews.add(v);
    }

    public void addLabelView(TextView v) {
        mLabelViews.add(v);
    }

    public void addStateChangedCallback(BatteryStateChangeCallback cb) {
        mChangeCallbacks.add(cb);
    }

    public void setColor(ColorUtils.ColorSettingInfo colorInfo) {
        mColorInfo = colorInfo;
        updateBatteryLevel();
    }

    // Allow override battery icons
    public int getIconStyleUnknown() {
        return R.drawable.stat_sys_battery;
    }
    public int getIconStyleNormal() {
        return R.drawable.stat_sys_battery;
    }
    public int getIconStyleCharge() {
        return R.drawable.stat_sys_battery_charge;
    }
    public int getIconStyleNormalMin() {
        return R.drawable.stat_sys_battery_min;
    }
    public int getIconStyleChargeMin() {
        return R.drawable.stat_sys_battery_charge_min;
    }

    protected int getBatteryStyle() {
        return mBatteryStyle;
    }

    protected int getBatteryStatus() {
        return mBatteryStatus;
    }

    protected boolean isBatteryPlugged() {
        return mBatteryPlugged;
    }

    protected boolean isBatteryPresent() {
        // the battery widget always is shown.
        return true;
    }

    private boolean isBatteryStatusUnknown() {
        return getBatteryStatus() == BatteryManager.BATTERY_STATUS_UNKNOWN;
    }

    private boolean isBatteryStatusCharging() {
        return getBatteryStatus() == BatteryManager.BATTERY_STATUS_CHARGING;
    }

    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
            mLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            mBatteryPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            mBatteryStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                                                BatteryManager.BATTERY_STATUS_UNKNOWN);
            updateViews(mLevel);
            updateBattery();
        }
    }

    protected void updateViews(int level) {
        final int icon = mBatteryPlugged ? R.drawable.stat_sys_battery_charge
                : R.drawable.stat_sys_battery;
        int N = mIconViews.size();
        for (int i=0; i<N; i++) {
            ImageView v = mIconViews.get(i);
            Drawable batteryBitmap = mContext.getResources().getDrawable(icon);
            if (mColorInfo.isLastColorNull) {
                batteryBitmap.clearColorFilter();
            } else {
                batteryBitmap.setColorFilter(mColorInfo.lastColor, PorterDuff.Mode.SRC_IN);
            }
            v.setImageDrawable(batteryBitmap);
            v.setImageLevel(level);
            v.setContentDescription(mContext.getString(R.string.accessibility_battery_level,
                    level));
        }
        N = mLabelViews.size();
        for (int i=0; i<N; i++) {
            TextView v = mLabelViews.get(i);
            v.setText(mContext.getString(BATTERY_TEXT_STYLE_NORMAL,
                    level));
        }

        for (BatteryStateChangeCallback cb : mChangeCallbacks) {
            cb.onBatteryLevelChanged(level, isBatteryStatusCharging());
        }
    }

    protected void updateBattery() {
        int mIcon = View.GONE;
        int mText = View.GONE;
        int mIconStyle = getIconStyleNormal();

        if (isBatteryPresent()) {
            if ( isBatteryStatusUnknown() &&
                (mBatteryStyle == BATTERY_STYLE_NORMAL || mBatteryStyle == BATTERY_STYLE_PERCENT)) {
                // Unknown status doesn't relies on any style
                mIcon = (View.VISIBLE);
                mIconStyle = getIconStyleUnknown();
            } else if (mBatteryStyle == BATTERY_STYLE_NORMAL) {
                mIcon = (View.VISIBLE);
                mIconStyle = isBatteryStatusCharging() ?
                                getIconStyleCharge() : getIconStyleNormal();
            } else if (mBatteryStyle == BATTERY_STYLE_PERCENT) {
                mIcon = (View.VISIBLE);
                mText = (View.VISIBLE);
                mIconStyle = isBatteryStatusCharging() ?
                                getIconStyleChargeMin() : getIconStyleNormalMin();
            }
        }

        int N = mIconViews.size();
        for (int i=0; i<N; i++) {
            ImageView v = mIconViews.get(i);
            v.setVisibility(mIcon);
            Drawable batteryBitmap = mContext.getResources().getDrawable(mIconStyle);
            if (mColorInfo.isLastColorNull) {
                batteryBitmap.clearColorFilter();
            } else {
                batteryBitmap.setColorFilter(mColorInfo.lastColor, PorterDuff.Mode.SRC_IN);
            }
            v.setImageDrawable(batteryBitmap);
        }
        N = mLabelViews.size();
        for (int i=0; i<N; i++) {
            TextView v = mLabelViews.get(i);
            v.setVisibility(mText);
        }
    }

    protected void updateBatteryLevel() {
        updateViews(mLevel);
        updateBattery();
    }

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        mBatteryStyle = (Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_CIRCLE_BATTERY, BATTERY_STYLE_NORMAL));
        updateBattery();
    }
}
