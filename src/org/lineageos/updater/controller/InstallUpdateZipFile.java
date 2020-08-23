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

import android.app.Activity;
import android.content.res.Resources;
import android.net.Uri;
import android.os.SystemProperties;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ProgressBar;

import androidx.appcompat.app.AlertDialog;

import org.lineageos.updater.R;
import org.lineageos.updater.UpdatesListActivity;
import org.lineageos.updater.UpdatesListAdapter;
import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.misc.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

public class InstallUpdateZipFile {
    private static final String TAG = "InstallUpdateZipFile";

    private final Activity mActivity;
    private final UpdatesListAdapter mAdapter;
    private final String zipFileName;
    private final Uri updateUri;
    private final File zipFile;

    private AlertDialog progressDialog;

    public InstallUpdateZipFile(Activity activity, UpdatesListAdapter mAdapter, Uri uri) {
        this.mActivity = activity;
        this.mAdapter = mAdapter;
        this.updateUri = uri;
        this.zipFileName = uri.getLastPathSegment();
        this.zipFile = new File(mActivity.getString(R.string.download_path) + "update.zip");
    }

    public void prepareZip() {
        String device = SystemProperties.get(Constants.PROP_DEVICE);

        assert zipFileName != null;
        if (zipFileName.contains(device)) {
            Thread thread = new Thread(() -> {
                try {
                    mActivity.runOnUiThread(() -> {
                        progressDialog = getPreparingUpdateDialog().create();
                        progressDialog.show();
                    });

                    InputStream inputStream = mActivity.getContentResolver().openInputStream(updateUri);
                    OutputStream outStream = new FileOutputStream(zipFile.toString());
                    byte[] buffer = new byte[8192];

                    int length;
                    while ((length = inputStream.read(buffer)) != -1) {
                        outStream.write(buffer, 0, length);
                    }
                } catch (IOException ignore) {}

                mActivity.runOnUiThread(() -> {
                    progressDialog.hide();
                    Objects.requireNonNull(zipInstallDialog()).show();
                });
            });

            thread.start();
        }
    }

    private AlertDialog.Builder zipInstallDialog() {
        if (!mAdapter.isBatteryLevelOk()) {
            Resources resources = mActivity.getResources();
            String message = resources.getString(R.string.dialog_battery_low_message_pct,
                    resources.getInteger(R.integer.battery_ok_percentage_discharging),
                    resources.getInteger(R.integer.battery_ok_percentage_charging));

            return new AlertDialog.Builder(mActivity, R.style.AccentMaterialAlertDialog)
                    .setTitle(R.string.dialog_battery_low_title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> ((UpdatesListActivity) mActivity).finish());
        }

        int resId;
        try {
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

            return new AlertDialog.Builder(mActivity, R.style.AccentMaterialAlertDialog)
                    .setTitle(R.string.zip_file_install_dialog_title)
                    .setMessage(R.string.zip_file_install_dialog_message)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> ((UpdatesListActivity) mActivity).finish());
        }

        return new AlertDialog.Builder(mActivity, R.style.AccentMaterialAlertDialog)
                .setTitle(R.string.apply_update_dialog_title)
                .setMessage(mActivity.getString(resId, zipFileName,
                        mActivity.getString(android.R.string.ok)))
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> triggerZipFileInstall())
                .setNegativeButton(android.R.string.cancel, null);
    }

    public AlertDialog.Builder getPreparingUpdateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);

        builder.setTitle(mActivity.getString(R.string.preparing_update_title));
        builder.setMessage(mActivity.getString(R.string.preparing_update_summary));
        builder.setCancelable(false);

        final ProgressBar progressBar = new ProgressBar(mActivity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.setMargins(50,0, 50, 0);

        progressBar.setLayoutParams(params);
        builder.setView(progressBar, 50, 0 ,50 ,0);
        return builder;
    }

    private void triggerZipFileInstall() {
        UpdaterController mUpdaterController = UpdaterController.getInstance(mActivity);
        try {
            if (Utils.isABUpdate(zipFile)) {
                Log.d(TAG, "Trying to install AB zip file " + zipFileName);
                ABUpdateInstaller installer = ABUpdateInstaller.getInstance(mActivity,
                        mUpdaterController);
                installer.install(zipFile);
            } else {
                progressDialog = getPreparingUpdateDialog().show();
                Log.d(TAG, "Trying to install zip file " + zipFileName);
                UpdateInstaller installer = UpdateInstaller.getInstance(mActivity,
                        mUpdaterController);
                new Thread(() -> {
                    installer.installPackage(mActivity, zipFile);
                    progressDialog.dismiss();
                }).start();
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not install zip", e);
            progressDialog.dismiss();
        }
    }
}
