package com.alphabetbloc.clinic.services;

import org.odk.clinic.android.database.DbAdapter;
import org.odk.clinic.android.openmrs.Constants;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;

/**
 *  IntentService is called by Alarm Listener at periodic intervals.
 *         Decides whether or not to start ongoing service to monitor
 *         SignalStrength and download clients. After decision, this
 *         IntentService finishes. Holds wakelock.
 *         
 * @author Louis.Fazen@gmail.com
 *
 */

//TODO!: is this class necessary?
public class AlarmIntentService extends WakefulIntentService {

	private Context mContext;
	private static final String TAG = "RefreshClientService";

	public AlarmIntentService() {
		super("AppService");
	}

	@Override
	protected void doWakefulWork(Intent intent) {
		mContext = this;

		// Find the most recent download time
		long recentDownload = DbAdapter.openDb().fetchMostRecentDownload();
		long timeSinceRefresh = System.currentTimeMillis() - recentDownload;
		Log.e(TAG, "Minutes since last refresh: " + timeSinceRefresh / (1000 * 60));

		if (timeSinceRefresh > Constants.MINIMUM_REFRESH_TIME) {
			Log.e(TAG, "RefreshClientService about to start SS service");
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

		//TODO!: totalhack to wait for SignalStrengthService to acquire a wakelock
		SystemClock.sleep(1000);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.e(TAG, "RefreshClientService OnDestroy is called");
	}

}
