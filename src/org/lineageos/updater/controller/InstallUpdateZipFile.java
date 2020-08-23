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
import org.lineageos.updater.misc.PermissionsUtils;
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
    private final String mZipFileName;
    private final Uri mUpdateUri;

    private File mZipFile;
    private AlertDialog progressDialog;

    public InstallUpdateZipFile(Activity activity, UpdatesListAdapter adapter, Uri uri) {
        mActivity = activity;
        mAdapter = adapter;
        mUpdateUri = uri;
        mZipFileName = uri.getLastPathSegment();
        mZipFile = new File(Utils.isABDevice() ?
            Utils.getExportPath(mActivity).toString() : Utils.getDownloadPath(mActivity).toString());
    }

    public void beginInstall() {
        /*
        * Copy the ZIP to install to the proper place
        * If the device is not A/B, copy to /data/arrowos_updates,
        * so the file can be read from the recovery without decrypting
        * If the phone is A/B, copy to the 'ArrowOS Updates' directory
        * in the internal storage
        *
        * After copying the file, continue with the installation
        */
        Thread thread = new Thread(() -> {
            try {
                byte[] buffer = new byte[8192];
                int length;
                mZipFile = new File(mZipFile.toString() + "/update.zip");

                mActivity.runOnUiThread(() -> {
                    progressDialog = getPreparingUpdateDialog().create();
                    progressDialog.show();
                });

                InputStream inputStream = mActivity.getContentResolver().openInputStream(mUpdateUri);
                OutputStream outStream = new FileOutputStream(mZipFile.toString());

                while ((length = inputStream.read(buffer)) != -1) {
                    outStream.write(buffer, 0, length);
                }
            } catch (IOException ignore) {}

            mActivity.runOnUiThread(() -> {
                if (progressDialog != null) {
                    progressDialog.hide();
                    Objects.requireNonNull(getZipInstallDialog()).show();
                }
            });
        });
        thread.start();
    }

    private AlertDialog.Builder getZipInstallDialog() {
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
            if (mZipFile.isFile()) {
                if (mZipFile.canRead()) {
                    if (Utils.isABUpdate(mZipFile)) {
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
                .setMessage(mActivity.getString(resId, mZipFileName,
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
            if (Utils.isABUpdate(mZipFile)) {
                Log.d(TAG, "Trying to install AB zip file " + mZipFileName);
                ABUpdateInstaller installer = ABUpdateInstaller.getInstance(mActivity,
                        mUpdaterController);
                installer.install(mZipFile);
            } else {
                progressDialog = getPreparingUpdateDialog().show();
                Log.d(TAG, "Trying to install zip file " + mZipFileName);
                UpdateInstaller installer = UpdateInstaller.getInstance(mActivity,
                        mUpdaterController);
                new Thread(() -> {
                    installer.installPackage(mActivity, mZipFile);
                    progressDialog.dismiss();
                }).start();
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not install zip", e);
            progressDialog.dismiss();
        }
    }
}
