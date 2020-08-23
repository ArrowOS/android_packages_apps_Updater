/*
 * Copyright (C) 2020 ArrowOS
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
package org.lineageos.updater.controller;

import android.content.Context;
import android.content.res.Resources;
import android.os.SystemProperties;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import android.widget.ProgressBar;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import org.lineageos.updater.R;
import org.lineageos.updater.UpdatesListActivity;
import org.lineageos.updater.UpdatesListAdapter;
import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.misc.FileUtils;
import org.lineageos.updater.misc.PermissionsUtils;
import org.lineageos.updater.misc.Utils;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class InstallUpdateZipFile {
    private static final String TAG = "InstallUpdateZipFile";

    private final Context mContext;
    private final UpdatesListAdapter mAdapter;
    private final String zipFilePath;
    private final String zipFileName;
    private final File genericUpdatePath;

    private File zipFile;
    private AlertDialog progressDialog;

    public InstallUpdateZipFile(Context context, UpdatesListAdapter mAdapter, String zipFilePath, String zipFileName) {
        this.mContext = context;
        this.mAdapter = mAdapter;
        this.zipFilePath = zipFilePath;
        this.zipFileName = zipFileName;
        this.genericUpdatePath = new File(mContext.getString(R.string.download_path) + "update.zip");
    }

    public void prepareZip() {
        String zipFileNamePattern = "";
        String device = SystemProperties.get(Constants.PROP_DEVICE);

        assert zipFileName != null;
        if (zipFileName.contains(device)) {
            Objects.requireNonNull(zipInstallDialog()).show();
        }
    }

    private AlertDialog.Builder zipInstallDialog() {
        if (!mAdapter.isBatteryLevelOk()) {
            Resources resources = mContext.getResources();
            String message = resources.getString(R.string.dialog_battery_low_message_pct,
                    resources.getInteger(R.integer.battery_ok_percentage_discharging),
                    resources.getInteger(R.integer.battery_ok_percentage_charging));

            return new AlertDialog.Builder(mContext, R.style.AccentMaterialAlertDialog)
                    .setTitle(R.string.dialog_battery_low_title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> ((UpdatesListActivity) mContext).finish());
        }

        int resId;
        try {
            zipFile = new File("/sdcard/" + zipFilePath.split(":")[1]);
            Log.d(TAG, zipFile.toString());

            if (zipFile.isFile()) {
                if (zipFile.canRead()) {
                    if (Utils.isABUpdate(zipFile)) {
                        resId = R.string.apply_update_dialog_message_ab;
                    } else {
                        resId = R.string.apply_update_dialog_message;
                    }
                } else {
                    Log.e(TAG, "Provided file is not readable");
                    throw new IOException();
                }
            } else {
                Log.e(TAG, "Provided is not a valid zip file");
                throw new IOException();
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not determine the type of the update from provided zip file", e);

            return new AlertDialog.Builder(mContext, R.style.AccentMaterialAlertDialog)
                    .setTitle(R.string.zip_file_install_dialog_title)
                    .setMessage(R.string.zip_file_install_dialog_message)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> ((UpdatesListActivity) mContext).finish());
        }

        return new AlertDialog.Builder(mContext, R.style.AccentMaterialAlertDialog)
                .setTitle(R.string.apply_update_dialog_title)
                .setMessage(mContext.getString(resId, zipFileName,
                        mContext.getString(android.R.string.ok)))
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> triggerZipFileInstall())
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> ((UpdatesListActivity) mContext).finish());
    }

    public AlertDialog.Builder getPreparingUpdateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext, R.style.AccentMaterialAlertDialog);

        builder.setTitle(mContext.getString(R.string.preparing_update_title));
        builder.setMessage(mContext.getString(R.string.preparing_update_summary));
        builder.setCancelable(false);

        final ProgressBar progressBar = new ProgressBar(mContext, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        progressBar.setLayoutParams(params);
        builder.setView(progressBar, 50, 0, 50, 0);
        return builder;
    }

    private void triggerZipFileInstall() {
        UpdaterController mUpdaterController = UpdaterController.getInstance(mContext);
        try {
            if (Utils.isABUpdate(zipFile)) {
                Log.d(TAG, "Trying to install AB zip file " + zipFileName);
                ABUpdateInstaller installer = ABUpdateInstaller.getInstance(mContext,
                        mUpdaterController);
                installer.install(zipFile);
            } else {
                progressDialog = getPreparingUpdateDialog().show();
                Log.d(TAG, "Trying to install zip file " + zipFileName);
                UpdateInstaller installer = UpdateInstaller.getInstance(mContext,
                        mUpdaterController);
                new Thread() {
                    public void run() {
                        try {
                            if (genericUpdatePath.exists())
                                genericUpdatePath.delete();
                            FileUtils.copyFile(zipFile, genericUpdatePath);
                            installer.installPackage(mContext, genericUpdatePath);
                            progressDialog.dismiss();
                        } catch (IOException e) {
                            Log.e(TAG, "Could not install update", e);
                        }
                    }
                }.start();
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not install zip", e);
            progressDialog.dismiss();
        }
    }
}
