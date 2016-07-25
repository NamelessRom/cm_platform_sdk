/*
 * Copyright (C) 2016 The CyanogenMod Project
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

package org.cyanogenmod.internal;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.cyanogenmod.platform.internal.R;

/**
 * @hide
 */
public class BootDexoptDialog extends Dialog {
    private static final boolean DEBUG = false;

    public static final int STAGE_STARTING_APPS = Integer.MIN_VALUE;
    public static final int STAGE_FSTRIM = Integer.MIN_VALUE + 1;
    public static final int STAGE_PREPARING_APPS = Integer.MIN_VALUE + 2;
    public static final int STAGE_COMPLETE = Integer.MIN_VALUE + 3;

    private final Context mContext;
    private final PackageManager mPackageManager;
    private final boolean mUseFancyEffects;

    private ImageView mBootDexoptIcon;
    private TextView mBootDexoptMsg;
    private TextView mBootDexoptMsgDetail;
    private ProgressBar mBootDexoptProgress;

    private boolean mWasApk;

    private int mTotal;

    public static BootDexoptDialog create(Context context) {
        return create(context, WindowManager.LayoutParams.TYPE_BOOT_PROGRESS);
    }

    public static BootDexoptDialog create(Context context, int windowType) {
        final PackageManager pm = context.getPackageManager();
        final int theme;
        if (pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
                || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            theme = com.android.internal.R.style.Theme_Micro_Dialog_Alert;
        } else if (pm.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            theme = com.android.internal.R.style.Theme_Leanback_Dialog_Alert;
        } else {
            // set theme to material light to show a full screen dialog
            theme = com.android.internal.R.style.Theme_Material_Light;
        }

        return new BootDexoptDialog(context, theme, windowType);
    }

    private BootDexoptDialog(Context context, int themeResId, int windowType) {
        super(context, themeResId);
        mContext = context;
        mPackageManager = mContext.getPackageManager();
        mUseFancyEffects = mContext.getResources().getBoolean(R.bool.config_bootDexoptIsFancy);

        final LayoutInflater inflater = LayoutInflater.from(mContext);
        final View bootMsgLayout = inflater.inflate(R.layout.dexopt_dialog, null, false);
        mBootDexoptMsg = (TextView) bootMsgLayout.findViewById(R.id.dexopt_message);
        mBootDexoptMsgDetail = (TextView) bootMsgLayout.findViewById(R.id.dexopt_message_detail);
        mBootDexoptIcon = (ImageView) bootMsgLayout.findViewById(R.id.dexopt_icon);
        mBootDexoptProgress = (ProgressBar) bootMsgLayout.findViewById(R.id.dexopt_progress);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(bootMsgLayout);

        if (windowType != 0) {
            getWindow().setType(windowType);
        }
        getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN);

        final WindowManager.LayoutParams lp = getWindow().getAttributes();
        // turn off button lights when dexopting
        lp.buttonBrightness = 0;
        lp.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
        getWindow().setAttributes(lp);
        setCancelable(false);
        show();

        bootMsgLayout.post(new Runnable() {
            @Override public void run() {
                mBootDexoptIcon.setImageDrawable(null);

                // start the marquee
                mBootDexoptMsg.setSelected(true);
                mBootDexoptMsgDetail.setSelected(true);
            }
        });
    }

    public void setProgress(final ApplicationInfo info, final int current, final int total) {
        // if we initialized with an invalid total, get it from the valid dexopt messages
        if (mTotal != total && total > 0) {
            mTotal = total;
            mBootDexoptProgress.setMax(mTotal);
            mBootDexoptProgress.setSecondaryProgress(mTotal);
        }

        boolean isApk = false;
        final String msg;
        if (info == null) {
            if (current == STAGE_STARTING_APPS) {
                msg = mContext.getResources().getString(
                        com.android.internal.R.string.android_upgrading_starting_apps);
            } else if (current == STAGE_FSTRIM) {
                msg = mContext.getResources().getString(
                        com.android.internal.R.string.android_upgrading_fstrim);
            } else if (current == STAGE_COMPLETE) {
                msg = mContext.getResources().getString(
                        com.android.internal.R.string.android_upgrading_complete);
            } else {
                msg = "";
            }
        } else if (current == STAGE_PREPARING_APPS) {
            final CharSequence label = info.loadLabel(mContext.getPackageManager());
            msg = mContext.getResources().getString(
                    com.android.internal.R.string.android_preparing_apk, label);
        } else {
            isApk = true;
            msg = mContext.getResources().getString(
                    com.android.internal.R.string.android_upgrading_apk, current, total);
            mBootDexoptProgress.setProgress(current);
        }

        // check if the state has changed
        if (mWasApk != isApk) {
            mWasApk = isApk;
            if (isApk) {
                mBootDexoptMsgDetail.setVisibility(View.VISIBLE);
                mBootDexoptProgress.setVisibility(View.VISIBLE);
            } else {
                mBootDexoptMsgDetail.setVisibility(View.GONE);
                mBootDexoptProgress.setVisibility(View.INVISIBLE);
            }
        }

        // if we are processing an apk, load its icon and set the message details
        if (isApk) {
            if (mUseFancyEffects) {
                mBootDexoptIcon.setImageDrawable(info.loadIcon(mPackageManager));
            }
            mBootDexoptMsgDetail.setText(String.format("(%s)", info.packageName));
        } else {
            mBootDexoptIcon.setImageDrawable(null);
        }

        mBootDexoptMsg.setText(msg);
    }

    // This dialog will consume all events coming in to
    // it, to avoid it trying to do things too early in boot.

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (DEBUG) {
            return super.dispatchKeyEvent(event);
        }
        return true;
    }

    @Override
    public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return true;
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent ev) {
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        return true;
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        return true;
    }
}
