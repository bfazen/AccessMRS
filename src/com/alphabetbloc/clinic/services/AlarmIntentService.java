package com.alphabetbloc.clinic.services;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.providers.Db;

/**
 * IntentService is called by Alarm Listener at periodic intervals. Decides
 * whether or not to start ongoing service to monitor SignalStrength and
 * download clients. After decision, this IntentService finishes. Holds
 * wakelock.
 * 
 * @author Louis.Fazen@gmail.com
 * 
 */

// TODO!: is this class necessary?
public class AlarmIntentService extends WakefulIntentService {

	private Context mContext;
	private static final String TAG = AlarmIntentService.class.getSimpleName();

	public AlarmIntentService() {
		super("AppService");
	}

	@Override
	protected void doWakefulWork(Intent intent) {
		mContext = this;
		Log.v(TAG, "alarmintent service is now running");
		// Find the most recent download time
		long recentDownload = Db.open().fetchMostRecentDownload();
		long timeSinceRefresh = System.currentTimeMillis() - recentDownload;
		Log.v(TAG, "Minutes since last refresh: " + timeSinceRefresh / (1000 * 60));
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		String minRefreshSeconds = prefs.getString(getString(R.string.key_min_refresh_seconds), getString(R.string.default_min_refresh_seconds));
		long minRefreshMs = 1000L * Long.valueOf(minRefreshSeconds);
		if (timeSinceRefresh > minRefreshMs) {
			Log.v(TAG, "RefreshClientService about to start SS service");
			ComponentName comp = new ComponentName(mContext.getPackageName(), RefreshDataService.class.getName());
			Intent i = new Intent();
			i.setComponent(comp);
			ComponentName service = mContext.startService(i);
			if (null == service)
				Log.e(TAG, "Could not start service: " + comp.toString());
		}
		// Don't refresh if <MIN: save battery and data costs b/c:
		// 1. alarm just after refresh (manually or via power_connected)
		// 2. power_connected just after refresh (manually or via alarm)

		// TODO!: totalhack to wait for SignalStrengthService to acquire a
		// wakelock
		SystemClock.sleep(1000);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.v(TAG, "RefreshClientService OnDestroy is called");
	}

}
