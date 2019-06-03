/*
 * Copyright (C) 2012-2018 Paul Watts (paulcwatts@gmail.com), Sean J. Barbeau (sjbarbeau@gmail.com)
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
package com.transerve.locationservices.manager.gps.util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;

import com.transerve.locationservices.R;

/**
 * A class containing utility methods related to preferences
 */
public class PreferenceUtils {

    public static final int CAPABILITY_UNKNOWN = -1;
    public static final int CAPABILITY_NOT_SUPPORTED = 0;
    public static final int CAPABILITY_SUPPORTED = 1;
    public static final int CAPABILITY_LOCATION_DISABLED = 2;

    private Activity context;

    public PreferenceUtils(Activity context) {
        this.context = context;
    }

    /**
     * Gets the string description of a CAPABILITY_* constant
     * @param capability CAPABILITY_* constant defined in this class
     * @return a string description of the CAPABILITY_* constant
     */
    public String getCapabilityDescription(int capability) {
        switch (capability) {
            case CAPABILITY_UNKNOWN:
                return context.getString(R.string.capability_value_unknown);
            case CAPABILITY_NOT_SUPPORTED:
                return context.getString(R.string.capability_value_not_supported);
            case CAPABILITY_SUPPORTED:
                return context.getString(R.string.capability_value_supported);
            case CAPABILITY_LOCATION_DISABLED:
                return context.getString(R.string.capability_value_location_disabled);
            default:
                return context.getString(R.string.capability_value_unknown);
        }
    }

    @TargetApi(9)
    public static void saveString(SharedPreferences prefs, String key, String value) {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString(key, value);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            edit.apply();
        } else {
            edit.commit();
        }
    }

    public void saveString(String key, String value) {
        saveString(context.getPreferences(Context.MODE_PRIVATE), key, value);
    }

    @TargetApi(9)
    public static void saveInt(SharedPreferences prefs, String key, int value) {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putInt(key, value);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            edit.apply();
        } else {
            edit.commit();
        }
    }

    public  void saveInt(String key, int value) {
        saveInt(context.getPreferences(Context.MODE_PRIVATE), key, value);
    }

    @TargetApi(9)
    public static void saveLong(SharedPreferences prefs, String key, long value) {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putLong(key, value);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            edit.apply();
        } else {
            edit.commit();
        }
    }

    public void saveLong(String key, long value) {
        saveLong(context.getPreferences(Context.MODE_PRIVATE), key, value);
    }

    @TargetApi(9)
    public static void saveBoolean(SharedPreferences prefs, String key, boolean value) {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean(key, value);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            edit.apply();
        } else {
            edit.commit();
        }
    }

    public void saveBoolean(String key, boolean value) {
        saveBoolean(context.getPreferences(Context.MODE_PRIVATE), key, value);
    }

    @TargetApi(9)
    public static void saveFloat(SharedPreferences prefs, String key, float value) {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putFloat(key, value);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            edit.apply();
        } else {
            edit.commit();
        }
    }

    public void saveFloat(String key, float value) {
        saveFloat(context.getPreferences(Context.MODE_PRIVATE), key, value);
    }

    @TargetApi(9)
    public static void saveDouble(SharedPreferences prefs, String key, double value) {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putLong(key, Double.doubleToRawLongBits(value));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            edit.apply();
        } else {
            edit.commit();
        }
    }

    @TargetApi(9)
    public void saveDouble(String key, double value) {
        saveDouble(context.getPreferences(Context.MODE_PRIVATE), key, value);
    }

    /**
     * Gets a double for the provided key from preferences, or the default value if the preference
     * doesn't currently have a value
     *
     * @param key          key for the preference
     * @param defaultValue the default value to return if the key doesn't have a value
     * @return a double from preferences, or the default value if it doesn't exist
     */
    public Double getDouble(String key, double defaultValue) {
        if (!context.getPreferences(Context.MODE_PRIVATE).contains(key)) {
            return defaultValue;
        }
        return Double.longBitsToDouble(context.getPreferences(Context.MODE_PRIVATE).getLong(key, 0));
    }

    public String getString(String key) {
        return context.getPreferences(Context.MODE_PRIVATE).getString(key, null);
    }

    public long getLong(String key, long defaultValue) {
        return context.getPreferences(Context.MODE_PRIVATE).getLong(key, defaultValue);
    }

    public float getFloat(String key, float defaultValue) {
        return context.getPreferences(Context.MODE_PRIVATE).getFloat(key, defaultValue);
    }

    /**
     * Returns the currently selected satellite sort order as the index in R.array.sort_sats
     *
     * @return the currently selected satellite sort order as the index in R.array.sort_sats
     */
    public int getSatSortOrderFromPreferences() {
        Resources r = context.getResources();
        SharedPreferences settings = context.getPreferences(Context.MODE_PRIVATE);
        String[] sortOptions = r.getStringArray(R.array.sort_sats);
        String sortPref = settings.getString(r.getString(
                R.string.pref_key_default_sat_sort), sortOptions[0]);
        for (int i = 0; i < sortOptions.length; i++) {
            if (sortPref.equalsIgnoreCase(sortOptions[i])) {
                return i;
            }
        }
        return 0;  // Default to the first option
    }
}
