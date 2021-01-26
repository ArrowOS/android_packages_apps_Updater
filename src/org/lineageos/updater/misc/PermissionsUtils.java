/*
 * Copyright (C) 2017 The LineageOS Project
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
package org.lineageos.updater.misc;

import android.Manifest;
import android.app.Activity;
import android.os.Environment;

public class PermissionsUtils {

    /**
     * Check and request the manage external storage permission
     * To save OTA packages inside root of internal storage
     */
    public static boolean checkAndRequestStoragePermission(Activity activity, int requestCode) {

        if (!Environment.isExternalStorageManager()) {
            activity.requestPermissions(new String[]{Manifest.permission.MANAGE_EXTERNAL_STORAGE},
                    requestCode);
            return false;
        } else
            return true;
    }
}
