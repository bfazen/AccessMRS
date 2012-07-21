package com.alphabetbloc.clinic.services;

import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.openmrs.Constants;

import android.app.AlarmManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;

/**
 * @author Louis.Fazen@gmail.com
 * 
 *         IntentService is called by AlarmListener at periodic intervals. Decides
 *         whether or not to start ongoing service to monitor SignalStrength
 *         and download clients. After decision, this IntentService finishes.
 */

//TODO:  However, this structure seems to work, 
//but the whole point of this RefreshClientsService is in order to prevent the device from going to sleep until it runs through the whole of the Service
//Loading the other intent will allow it to run in the background... I think I need to bind this service to the SignalStrengthService Intent if it is maximum
// if not maximum, just let it go to sleep as it wishes...
public class RefreshClientsService extends WakefulIntentService {

	private Context mContext;
	private static final String TAG = "RefreshClientService";

	public RefreshClientsService() {
		super("AppService");
	}

	@Override
	protected void doWakefulWork(Intent intent) {
		mContext = this;

		// Find the most recent download time
		ClinicAdapter ca = new ClinicAdapter();
		ca.open();
		long recentDownload = ca.fetchMostRecentDownload();
		ca.close();
		long timeSinceRefresh = System.currentTimeMillis() - recentDownload;
		Log.e(TAG, "Minutes since last refresh: " + timeSinceRefresh / (1000 * 60));

		if (timeSinceRefresh > Constants.MINIMUM_REFRESH_TIME) {
			// Don't refresh if not: save battery and data costs. <MIN b/c:
			// 1. alarm just after refresh (manually or via power_connected)
			// 2. power_connected just after refresh (manually or via alarm)

			ComponentName comp = new ComponentName(mContext.getPackageName(), SignalStrengthService.class.getName());
			Intent i = new Intent();
			i.setComponent(comp);
			
			if (timeSinceRefresh >= Constants.MAXIMUM_REFRESH_TIME)
				i.putExtra("maximum", true);
			else
				i.putExtra("maximum", false);
			
			ComponentName service = mContext.startService(i);
			
			if (null == service)
				Log.e(TAG, "Could not start service: " + comp.toString());
		}

	}
}
