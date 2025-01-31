package com.transerve.locationservices.manager;

import android.app.Activity;
import android.content.Context;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.StringRes;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.common.util.SharedPreferencesUtils;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;

import static android.content.ContentValues.TAG;

public class ActivityCallbackProvider {

    private Activity activity;
    private SettingsClient settingsClient;

    public ActivityCallbackProvider(Activity activity) {
        if (activity != null) {
            this.activity = activity;
            settingsClient = LocationServices.getSettingsClient(activity);
        }
    }

    public static ActivityCallbackProvider getMocker() {
        return new MockActivityCallbackProvider();
    }

    public Task checkLocationSettings(LocationSettingsRequest mLocationSettingsRequest) {
        return settingsClient.checkLocationSettings(mLocationSettingsRequest);
    }

    public void requestPermissions(String[] strings, int requestPermissionsRequestCode) {
        ActivityCompat.requestPermissions(activity, strings, requestPermissionsRequestCode);
    }

    public boolean checkSelfPermission(String accessFineLocation) {
        int result = ContextCompat.checkSelfPermission(activity
                , accessFineLocation);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public void showGPSSettingDialog(Exception e, int data) {
        ResolvableApiException rae = (ResolvableApiException) e;
        try {
            rae.startResolutionForResult(activity, data);
        } catch (IntentSender.SendIntentException e1) {
            Log.i(TAG, "PendingIntent unable to execute request.");
        }
    }

    public boolean isAttached() {
        return true;
    }

    public Object getSystemService(String serviceName) {
        return activity.getSystemService(serviceName);
    }

    public String getString(@StringRes int resId) {
        return activity.getString(resId);
    }

    public SharedPreferences getPrefs() {
        return activity.getPreferences(Context.MODE_PRIVATE);
    }

    public int getRotation() {
        return 0;
    }

    public void saveInt(String key, int data) {
        /// TODO: 3/6/19
    }
}
