/*
 * Copyright (C) 2008-2018 The Android Open Source Project,
 * Sean J. Barbeau
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

package com.transerve.locationservices.manager.gps;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.location.OnNmeaMessageListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.widget.Switch;

import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.core.view.MenuItemCompat;

import com.transerve.locationservices.R;
import com.transerve.locationservices.manager.gps.util.GpsTestUtil;
import com.transerve.locationservices.manager.gps.util.MathUtils;
import com.transerve.locationservices.manager.gps.util.PreferenceUtils;
import com.transerve.locationservices.manager.ActivityCallbackProvider;

import java.util.ArrayList;

import static com.transerve.locationservices.manager.gps.util.GpsTestUtil.writeGnssMeasurementToLog;
import static com.transerve.locationservices.manager.gps.util.GpsTestUtil.writeNavMessageToLog;
import static com.transerve.locationservices.manager.gps.util.GpsTestUtil.writeNmeaToLog;

@SuppressLint("MissingPermission")
public class GpsInfoProvider implements LocationListener, SensorEventListener {

    private static final String TAG = "GpsTestActivity";

    private static final int SECONDS_TO_MILLISECONDS = 1000;

    private static final String GPS_STARTED = "gps_started";

    private static final int LOCATION_PERMISSION_REQUEST = 1;

    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    public ActivityCallbackProvider mActivity;

    // Holds sensor data
    private static float[] mRotationMatrix = new float[16];

    private static float[] mRemappedMatrix = new float[16];

    private static float[] mValues = new float[3];

    private static float[] mTruncatedRotationVector = new float[4];

    private static boolean mTruncateVector = false;

    boolean mStarted;

    boolean mFaceTrueNorth;

    boolean mWriteGnssMeasurementToLog;

    boolean mLogNmea;

    boolean mWriteNmeaTimestampToLog;

    private Switch mSwitch;  // GPS on/off switch

    private LocationManager mLocationManager;

    private LocationProvider mProvider;

    /**
     * Android M (6.0.1) and below status and listener
     */
    private GpsStatus mLegacyStatus;

    private GpsStatus.Listener mLegacyStatusListener;

    private GpsStatus.NmeaListener mLegacyNmeaListener;

    /**
     * Android N (7.0) and above status and listeners
     */
    private GnssStatus mGnssStatus;

    private GnssStatus.Callback mGnssStatusListener;

    private GnssMeasurementsEvent.Callback mGnssMeasurementsListener;

    private OnNmeaMessageListener mOnNmeaMessageListener;

    private GnssNavigationMessage.Callback mGnssNavMessageListener;

    // Listeners for Fragments
    private ArrayList<GpsTestListener> mGpsTestListeners = new ArrayList<GpsTestListener>();

    private Location mLastLocation;

    private GeomagneticField mGeomagneticField;

    private long minTime; // Min Time between location updates, in milliseconds

    private float minDistance; // Min Distance between location updates, in meters

    private SensorManager mSensorManager;

    Bundle mLastSavedInstanceState;


    public void init() {
        mLocationManager = (LocationManager) mActivity.getSystemService(Context.LOCATION_SERVICE);
        mProvider = mLocationManager.getProvider(LocationManager.GPS_PROVIDER);
        if (mProvider == null) {
            Log.e(TAG, "Unable to get GPS_PROVIDER");
        }

        mSensorManager = (SensorManager) mActivity.getSystemService(Context.SENSOR_SERVICE);

        setupStartState(mLastSavedInstanceState);

        addStatusListener();

        addOrientationSensorListener();

        addNmeaListener();

        /**
         * Check preferences to see how these componenets should be initialized
         */
        SharedPreferences settings = mActivity.getPrefs();

        checkTimeAndDistance(settings);

        checkTrueNorth(settings);

        checkNmeaLog(settings);

        if (GpsTestUtil.isGnssStatusListenerSupported()) {
            checkGnssMeasurementOutput(settings);
        }

        if (GpsTestUtil.isGnssStatusListenerSupported()) {
            checkNavMessageOutput(settings);
        }

    }

    protected void onPause() {
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }

        // Remove status listeners
        removeStatusListener();
        removeNmeaListener();
        if (GpsTestUtil.isGnssStatusListenerSupported()) {
            removeNavMessageListener();
        }
        if (GpsTestUtil.isGnssStatusListenerSupported()) {
            removeGnssMeasurementsListener();
        }
        // Check if the user has chosen to stop GNSS whenever app is in background
        if (mActivity.getPrefs().getBoolean(getString(R.string.pref_key_stop_gnss_in_background), false)) {
            gpsStop();
        }
    }

    private void setupStartState(Bundle savedInstanceState) {
        // Apply start state settings from preferences
        SharedPreferences settings = mActivity.getPrefs();

        double tempMinTime = Double.valueOf(
                settings.getString(getString(R.string.pref_key_gps_min_time),
                        getString(R.string.pref_gps_min_time_default_sec))
        );
        minTime = (long) (tempMinTime * SECONDS_TO_MILLISECONDS);
        minDistance = Float.valueOf(
                settings.getString(getString(R.string.pref_key_gps_min_distance),
                        getString(R.string.pref_gps_min_distance_default_meters))
        );

        if (savedInstanceState != null) {
            // Activity is being restarted and has previous state (e.g., user rotated device)
            boolean gpsWasStarted = savedInstanceState.getBoolean(GPS_STARTED, true);
            if (gpsWasStarted) {
                gpsStart();
            }
        } else {
            // Activity is starting without previous state - use "Auto-start GNSS" setting
            if (settings.getBoolean(getString(R.string.pref_key_auto_start_gps), true)) {
                gpsStart();
            }
        }
    }

    private void deleteAidingData() {
        // If GPS is currently running, stop it
        boolean lastStartState = mStarted;
        if (mStarted) {
            gpsStop();
        }

        if (lastStartState) {
            Handler h = new Handler();
            // Restart the GPS, if it was previously started, with a slight delay,
            // to refresh the assistance data
            h.postDelayed(new Runnable() {
                public void run() {
                    gpsStart();
                }
            }, 500);
        }
    }

    public void addListener(GpsTestListener listener) {
        mGpsTestListeners.add(listener);
    }

    private synchronized void gpsStart() {
        if (mLocationManager == null || mProvider == null) {
            return;
        }

        if (!mStarted) {
            mLocationManager
                    .requestLocationUpdates(mProvider.getName(), minTime, minDistance, this);
            mStarted = true;

            // Show Toast only if the user has set minTime or minDistance to something other than default values
            if (minTime != (long) (Double.valueOf(getString(R.string.pref_gps_min_time_default_sec))
                    * SECONDS_TO_MILLISECONDS) ||
                    minDistance != Float
                            .valueOf(getString(R.string.pref_gps_min_distance_default_meters))) {
            }
        }
        for (GpsTestListener listener : mGpsTestListeners) {
            listener.gpsStart();
        }
    }

    private synchronized void gpsStop() {
        if (mLocationManager == null) {
            return;
        }
        if (mStarted) {
            mLocationManager.removeUpdates(this);
            mStarted = false;
        }
        for (GpsTestListener listener : mGpsTestListeners) {
            listener.gpsStop();
        }
    }

    private boolean sendExtraCommand(String command) {
        return mLocationManager.sendExtraCommand(LocationManager.GPS_PROVIDER, command, null);
    }

    private void addOrientationSensorListener() {
        // TODO: 21/5/19 Add sensor information here
        /*if (GpsTestUtil.isRotationVectorSensorSupported(this)) {
            // Use the modern rotation vector sensors
            Sensor vectorSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            mSensorManager.registerListener(this, vectorSensor, 16000); // ~60hz
        } else {
            // Use the legacy orientation sensors
            Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
            if (sensor != null) {
                mSensorManager.registerListener(this, sensor,
                        SensorManager.SENSOR_DELAY_GAME);
            }
        }*/
    }

    private void addStatusListener() {
        SharedPreferences settings = mActivity.getPrefs();
        boolean useGnssApis = settings.getBoolean(getString(R.string.pref_key_use_gnss_apis), true);

        if (GpsTestUtil.isGnssStatusListenerSupported() && useGnssApis) {
            addGnssStatusListener();
        } else {
            addLegacyStatusListener();
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private void addGnssStatusListener() {
        mGnssStatusListener = new GnssStatus.Callback() {
            @Override
            public void onStarted() {
                for (GpsTestListener listener : mGpsTestListeners) {
                    listener.onGnssStarted();
                }
            }

            @Override
            public void onStopped() {
                for (GpsTestListener listener : mGpsTestListeners) {
                    listener.onGnssStopped();
                }
            }

            @Override
            public void onFirstFix(int ttffMillis) {
                for (GpsTestListener listener : mGpsTestListeners) {
                    listener.onGnssFirstFix(ttffMillis);
                }
            }

            @Override
            public void onSatelliteStatusChanged(GnssStatus status) {
                mGnssStatus = status;

                // Stop progress bar after the first status information is obtained
//                setSupportProgressBarIndeterminateVisibility(Boolean.FALSE);

                for (GpsTestListener listener : mGpsTestListeners) {
                    listener.onSatelliteStatusChanged(mGnssStatus);
                }
            }
        };
        mLocationManager.registerGnssStatusCallback(mGnssStatusListener);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void addGnssMeasurementsListener() {
        mGnssMeasurementsListener = new GnssMeasurementsEvent.Callback() {
            @Override
            public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
                for (GpsTestListener listener : mGpsTestListeners) {
                    listener.onGnssMeasurementsReceived(event);
                }
                if (mWriteGnssMeasurementToLog) {
                    for (GnssMeasurement m : event.getMeasurements()) {
                        GpsTestUtil.writeGnssMeasurementToLog(m);
                    }
                }
            }

            @Override
            public void onStatusChanged(int status) {
                final String statusMessage;
                switch (status) {
                    case STATUS_LOCATION_DISABLED:
                        statusMessage = getString(R.string.gnss_measurement_status_loc_disabled);
                        mActivity.saveInt(mActivity.getString(R.string.capability_key_raw_measurements), PreferenceUtils.CAPABILITY_LOCATION_DISABLED);
                        break;
                    case STATUS_NOT_SUPPORTED:
                        statusMessage = getString(R.string.gnss_measurement_status_not_supported);
                        mActivity.saveInt(mActivity.getString(R.string.capability_key_raw_measurements), PreferenceUtils.CAPABILITY_NOT_SUPPORTED);
                        break;
                    case STATUS_READY:
                        statusMessage = getString(R.string.gnss_measurement_status_ready);
                        mActivity.saveInt(mActivity.getString(R.string.capability_key_raw_measurements), PreferenceUtils.CAPABILITY_SUPPORTED);
                        break;
                    default:
                        statusMessage = getString(R.string.gnss_status_unknown);
                        mActivity.saveInt(mActivity.getString(R.string.capability_key_raw_measurements), PreferenceUtils.CAPABILITY_UNKNOWN);
                }
                Log.d(TAG, "GnssMeasurementsEvent.Callback.onStatusChanged() - " + statusMessage);
            }
        };
        mLocationManager.registerGnssMeasurementsCallback(mGnssMeasurementsListener);
    }

    private void addLegacyStatusListener() {
        mLegacyStatusListener = new GpsStatus.Listener() {
            @Override
            public void onGpsStatusChanged(int event) {
                mLegacyStatus = mLocationManager.getGpsStatus(mLegacyStatus);

                switch (event) {
                    case GpsStatus.GPS_EVENT_STARTED:
                        break;
                    case GpsStatus.GPS_EVENT_STOPPED:
                        break;
                    case GpsStatus.GPS_EVENT_FIRST_FIX:
                        break;
                    case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                        // Stop progress bar after the first status information is obtained
                        break;
                }

                for (GpsTestListener listener : mGpsTestListeners) {
                    listener.onGpsStatusChanged(event, mLegacyStatus);
                }
            }
        };
        mLocationManager.addGpsStatusListener(mLegacyStatusListener);
    }

    private void removeStatusListener() {
        SharedPreferences settings = mActivity.getPrefs();
        boolean useGnssApis = settings.getBoolean(getString(R.string.pref_key_use_gnss_apis), true);

        if (GpsTestUtil.isGnssStatusListenerSupported() && useGnssApis) {
            removeGnssStatusListener();
        } else {
            removeLegacyStatusListener();
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private void removeGnssStatusListener() {
        if (mLocationManager != null) {
            mLocationManager.unregisterGnssStatusCallback(mGnssStatusListener);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void removeGnssMeasurementsListener() {
        if (mLocationManager != null && mGnssMeasurementsListener != null) {
            mLocationManager.unregisterGnssMeasurementsCallback(mGnssMeasurementsListener);
        }
    }

    private void removeLegacyStatusListener() {
        if (mLocationManager != null && mLegacyStatusListener != null) {
            mLocationManager.removeGpsStatusListener(mLegacyStatusListener);
        }
    }

    private void addNmeaListener() {
        if (GpsTestUtil.isGnssStatusListenerSupported()) {
            addNmeaListenerAndroidN();
        } else {
            addLegacyNmeaListener();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void addNmeaListenerAndroidN() {
        if (mOnNmeaMessageListener == null) {
            mOnNmeaMessageListener = new OnNmeaMessageListener() {
                @Override
                public void onNmeaMessage(String message, long timestamp) {
                    for (GpsTestListener listener : mGpsTestListeners) {
                        listener.onNmeaMessage(message, timestamp);
                    }
                    if (mLogNmea) {
                        GpsTestUtil.writeNmeaToLog(message,
                                mWriteNmeaTimestampToLog ? timestamp : Long.MIN_VALUE);
                    }
                    mActivity.saveInt(mActivity.getString(R.string.capability_key_nmea), PreferenceUtils.CAPABILITY_SUPPORTED);
                }
            };
        }
        mLocationManager.addNmeaListener(mOnNmeaMessageListener);
    }

    private void addLegacyNmeaListener() {
        if (mLegacyNmeaListener == null) {
            mLegacyNmeaListener = new GpsStatus.NmeaListener() {
                @Override
                public void onNmeaReceived(long timestamp, String nmea) {
                    for (GpsTestListener listener : mGpsTestListeners) {
                        listener.onNmeaMessage(nmea, timestamp);
                    }
                    if (mLogNmea) {
                        GpsTestUtil.writeNmeaToLog(nmea, mWriteNmeaTimestampToLog ? timestamp : Long.MIN_VALUE);
                    }
                    mActivity.saveInt(mActivity.getString(R.string.capability_key_nmea), PreferenceUtils.CAPABILITY_SUPPORTED);
                }
            };
        }
        mLocationManager.addNmeaListener(mLegacyNmeaListener);
    }

    private void removeNmeaListener() {
        if (GpsTestUtil.isGnssStatusListenerSupported()) {
            if (mLocationManager != null && mOnNmeaMessageListener != null) {
                mLocationManager.removeNmeaListener(mOnNmeaMessageListener);
            }
        } else {
            if (mLocationManager != null && mLegacyNmeaListener != null) {
                mLocationManager.removeNmeaListener(mLegacyNmeaListener);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void addNavMessageListener() {
        if (mGnssNavMessageListener == null) {
            mGnssNavMessageListener = new GnssNavigationMessage.Callback() {
                @Override
                public void onGnssNavigationMessageReceived(GnssNavigationMessage event) {
                    GpsTestUtil.writeNavMessageToLog(event);
                }

                @Override
                public void onStatusChanged(int status) {
                    final String statusMessage;
                    switch (status) {
                        case STATUS_LOCATION_DISABLED:
                            statusMessage = getString(R.string.gnss_nav_msg_status_loc_disabled);
                            mActivity.saveInt(mActivity.getString(R.string.capability_key_nav_messages), PreferenceUtils.CAPABILITY_LOCATION_DISABLED);
                            break;
                        case STATUS_NOT_SUPPORTED:
                            statusMessage = getString(R.string.gnss_nav_msg_status_not_supported);
                            mActivity.saveInt(mActivity.getString(R.string.capability_key_nav_messages), PreferenceUtils.CAPABILITY_NOT_SUPPORTED);
                            break;
                        case STATUS_READY:
                            statusMessage = getString(R.string.gnss_nav_msg_status_ready);
                            mActivity.saveInt(mActivity.getString(R.string.capability_key_nav_messages), PreferenceUtils.CAPABILITY_SUPPORTED);
                            break;
                        default:
                            statusMessage = getString(R.string.gnss_status_unknown);
                    }
                    Log.d(TAG, "GnssNavigationMessage.Callback.onStatusChanged() - " + statusMessage);
                }
            };
        }
        mLocationManager.registerGnssNavigationMessageCallback(mGnssNavMessageListener);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void removeNavMessageListener() {
        if (mLocationManager != null && mGnssNavMessageListener != null) {
            mLocationManager.unregisterGnssNavigationMessageCallback(mGnssNavMessageListener);
        }
    }


    private void checkTimeAndDistance(SharedPreferences settings) {
        double tempMinTimeDouble = Double
                .valueOf(settings.getString(getString(R.string.pref_key_gps_min_time), "1"));
        long minTimeLong = (long) (tempMinTimeDouble * SECONDS_TO_MILLISECONDS);

        if (minTime != minTimeLong ||
                minDistance != Float.valueOf(
                        settings.getString(getString(R.string.pref_key_gps_min_distance), "0"))) {
            // User changed preference values, get the new ones
            minTime = minTimeLong;
            minDistance = Float.valueOf(
                    settings.getString(getString(R.string.pref_key_gps_min_distance), "0"));
            // If the GPS is started, reset the location listener with the new values
            if (mStarted && mProvider != null) {
//                mLocationManager
//                        .requestLocationUpdates(mProvider.getName(), minTime, minDistance, this);
//                Toast.makeText(this, String.format(getString(R.string.gps_set_location_listener),
//                        String.valueOf(tempMinTimeDouble), String.valueOf(minDistance)),
//                        Toast.LENGTH_SHORT
//                ).show();
            }
        }
    }

    private void checkTrueNorth(SharedPreferences settings) {
        mFaceTrueNorth = settings.getBoolean(getString(R.string.pref_key_true_north), true);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void checkGnssMeasurementOutput(SharedPreferences settings) {
        mWriteGnssMeasurementToLog = settings
                .getBoolean(getString(R.string.pref_key_measurement_output), false);

        if (mWriteGnssMeasurementToLog) {
            addGnssMeasurementsListener();
        }
    }

    private void checkNmeaLog(SharedPreferences settings) {
        mLogNmea = settings.getBoolean(getString(R.string.pref_key_nmea_output), true);
        mWriteNmeaTimestampToLog = settings
                .getBoolean(getString(R.string.pref_key_nmea_timestamp_output), true);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void checkNavMessageOutput(SharedPreferences settings) {
        boolean logNavMessage = settings
                .getBoolean(getString(R.string.pref_key_navigation_message_output), false);

        if (logNavMessage) {
            addNavMessageListener();
        } else {
            removeNavMessageListener();
        }
    }

    private void initGpsSwitch(Menu menu) {
        MenuItem item = menu.findItem(R.id.gps_switch_item);
        if (item != null) {
            mSwitch = MenuItemCompat.getActionView(item).findViewById(R.id.gps_switch);
            if (mSwitch != null) {
                // Initialize state of GPS switch before we set the listener, so we don't double-trigger start or stop
                mSwitch.setChecked(mStarted);

                // Set up listener for GPS on/off switch
                mSwitch.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Turn GPS on or off
                        if (!mSwitch.isChecked() && mStarted) {
                            gpsStop();
                        } else {
                            if (mSwitch.isChecked() && !mStarted) {
                                gpsStart();
                            }
                        }
                    }
                });
            }
        }
    }

    public void onLocationChanged(Location location) {
        mLastLocation = location;

        updateGeomagneticField();

        for (GpsTestListener listener : mGpsTestListeners) {
            listener.onLocationChanged(location);
        }
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
        for (GpsTestListener listener : mGpsTestListeners) {
            listener.onStatusChanged(provider, status, extras);
        }
    }

    public void onProviderEnabled(String provider) {
        for (GpsTestListener listener : mGpsTestListeners) {
            listener.onProviderEnabled(provider);
        }
    }

    public void onProviderDisabled(String provider) {
        for (GpsTestListener listener : mGpsTestListeners) {
            listener.onProviderDisabled(provider);
        }
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    @Override
    public void onSensorChanged(SensorEvent event) {

        double orientation = Double.NaN;
        double tilt = Double.NaN;

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ROTATION_VECTOR:
                // Modern rotation vector sensors
                if (!mTruncateVector) {
                    try {
                        SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values);
                    } catch (IllegalArgumentException e) {
                        // On some Samsung devices, an exception is thrown if this vector > 4 (see #39)
                        // Truncate the array, since we can deal with only the first four values
                        Log.e(TAG, "Samsung device error? Will truncate vectors - " + e);
                        mTruncateVector = true;
                        // Do the truncation here the first time the exception occurs
                        getRotationMatrixFromTruncatedVector(event.values);
                    }
                } else {
                    // Truncate the array to avoid the exception on some devices (see #39)
                    getRotationMatrixFromTruncatedVector(event.values);
                }

                int rot = mActivity.getRotation();
                switch (rot) {
                    case Surface.ROTATION_0:
                        // No orientation change, use default coordinate system
                        SensorManager.getOrientation(mRotationMatrix, mValues);
                        // Log.d(TAG, "Rotation-0");
                        break;
                    case Surface.ROTATION_90:
                        // Log.d(TAG, "Rotation-90");
                        SensorManager.remapCoordinateSystem(mRotationMatrix, SensorManager.AXIS_Y,
                                SensorManager.AXIS_MINUS_X, mRemappedMatrix);
                        SensorManager.getOrientation(mRemappedMatrix, mValues);
                        break;
                    case Surface.ROTATION_180:
                        // Log.d(TAG, "Rotation-180");
                        SensorManager
                                .remapCoordinateSystem(mRotationMatrix, SensorManager.AXIS_MINUS_X,
                                        SensorManager.AXIS_MINUS_Y, mRemappedMatrix);
                        SensorManager.getOrientation(mRemappedMatrix, mValues);
                        break;
                    case Surface.ROTATION_270:
                        // Log.d(TAG, "Rotation-270");
                        SensorManager
                                .remapCoordinateSystem(mRotationMatrix, SensorManager.AXIS_MINUS_Y,
                                        SensorManager.AXIS_X, mRemappedMatrix);
                        SensorManager.getOrientation(mRemappedMatrix, mValues);
                        break;
                    default:
                        // This shouldn't happen - assume default orientation
                        SensorManager.getOrientation(mRotationMatrix, mValues);
                        // Log.d(TAG, "Rotation-Unknown");
                        break;
                }
                orientation = Math.toDegrees(mValues[0]);  // azimuth
                tilt = Math.toDegrees(mValues[1]);
                break;
            case Sensor.TYPE_ORIENTATION:
                // Legacy orientation sensors
                orientation = event.values[0];
                break;
            default:
                // A sensor we're not using, so return
                return;
        }

        // Correct for true north, if preference is set
        if (mFaceTrueNorth && mGeomagneticField != null) {
            orientation += mGeomagneticField.getDeclination();
            // Make sure value is between 0-360
            orientation = MathUtils.mod((float) orientation, 360.0f);
        }

        for (GpsTestListener listener : mGpsTestListeners) {
            listener.onOrientationChanged(orientation, tilt);
        }
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private void getRotationMatrixFromTruncatedVector(float[] vector) {
        System.arraycopy(vector, 0, mTruncatedRotationVector, 0, 4);
        SensorManager.getRotationMatrixFromVector(mRotationMatrix, mTruncatedRotationVector);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void updateGeomagneticField() {
        mGeomagneticField = new GeomagneticField((float) mLastLocation.getLatitude(),
                (float) mLastLocation.getLongitude(), (float) mLastLocation.getAltitude(),
                mLastLocation.getTime());
    }

    String getString(@StringRes int res) {
        return mActivity.getString(res);
    }
}
