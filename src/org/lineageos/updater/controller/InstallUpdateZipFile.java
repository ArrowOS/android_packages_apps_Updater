package org.lineageos.updater.controller;

import android.content.Context;
import android.content.res.Resources;
import android.os.SystemProperties;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import org.lineageos.updater.R;
import org.lineageos.updater.UpdatesListAdapter;
import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.misc.Utils;

import java.io.File;
import java.io.IOException;

public class InstallUpdateZipFile {
    private static final String TAG = "InstallUpdateZipFile";

    private final Context mContext;
    private final UpdatesListAdapter mAdapter;
    private final String zipFilePath;
    private final String zipFileName;

    private File zipFile;

    public InstallUpdateZipFile(Context context, UpdatesListAdapter mAdapter, String zipFilePath, String zipFileName) {
        this.mContext = context;
        this.mAdapter = mAdapter;
        this.zipFilePath = zipFilePath;
        this.zipFileName = zipFileName;
    }

    public void prepareZip() {
        String zipFileNamePattern = "";
        String device = SystemProperties.get(Constants.PROP_DEVICE);

        assert zipFileName != null;
        if (zipFileName.contains(device)) {
            zipInstallDialog().show();
        }
    }

    private AlertDialog.Builder zipInstallDialog() {
        if (!mAdapter.isBatteryLevelOk()) {
            Resources resources = mContext.getResources();
            String message = resources.getString(R.string.dialog_battery_low_message_pct,
                    resources.getInteger(R.integer.battery_ok_percentage_discharging),
                    resources.getInteger(R.integer.battery_ok_percentage_charging));

            return new AlertDialog.Builder(mContext)
                    .setTitle(R.string.dialog_battery_low_title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null);
        }

        int resId;
        try {
            zipFile = new File(zipFilePath);
            if (zipFile.isFile()) {
                if (Utils.isABUpdate(zipFile)) {
                    resId = R.string.apply_update_dialog_message_ab;
                } else {
                    resId = R.string.apply_update_dialog_message;
                }
            } else {
                Log.e(TAG, "Provided is not a valid zip file");
                throw new IOException();
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not determine the type of the update from provided zip file", e);
            return null;
        }

        return new AlertDialog.Builder(mContext)
                .setTitle(R.string.apply_update_dialog_title)
                .setMessage(mContext.getString(resId, zipFileName,
                        mContext.getString(android.R.string.ok)))
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> triggerZipFileInstall())
                .setNegativeButton(android.R.string.cancel, null);
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
                Log.d(TAG, "Trying to install zip file " + zipFileName);
                UpdateInstaller installer = UpdateInstaller.getInstance(mContext,
                        mUpdaterController);
                installer.installPackage(mContext, zipFile);
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not install zip", e);
        }
    }
}
