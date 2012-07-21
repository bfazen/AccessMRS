package com.alphabetbloc.clinic.receivers;

import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.openmrs.Constants;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.util.Log;
import com.alphabetbloc.clinic.services.SignalStrengthService;

/**
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 * On Connection to AC or USB power is a convenient time to check for an update:
 * 1. it may mean you are closer to a power source and signal is better
 * 2. it means we do not need to worry about battery dying mid-update
 * 3. the user is probably not planning on using the phone while connected
 * the more updates occur on battery connection, the fewer occur when not connected (saving battery later)
 */
public class UpdateOnPower extends BroadcastReceiver {
	private static final String TAG = "UpdateOnPower";

	public UpdateOnPower() {
		// TODO Auto-generated constructor stub
	}

	// A convenient time to check on status of Clinic Upload / Downloads
	@Override
	public void onReceive(Context context, Intent intent) {

		if ("android.intent.action.ACTION_POWER_CONNECTED".equals(intent.getAction())) {
			
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

				ComponentName comp = new ComponentName(context.getPackageName(), SignalStrengthService.class.getName());
				Intent i = new Intent();
				i.setComponent(comp);

				if (timeSinceRefresh >= Constants.MAXIMUM_REFRESH_TIME)
					i.putExtra("maximum", true);
				else
					i.putExtra("maximum", false);

				ComponentName service = context.startService(i);
				if (null == service)
					Log.e(TAG, "Could not start service: " + comp.toString());
			}

		}
	}
}
