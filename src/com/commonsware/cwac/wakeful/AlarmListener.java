/***
  Copyright (c) 2009-11 CommonsWare, LLC
  
  Licensed under the Apache License, Version 2.0 (the "License"); you may
  not use this file except in compliance with the License. You may obtain
  a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package com.commonsware.cwac.wakeful;

import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.openmrs.Constants;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import com.alphabetbloc.clinic.services.AlarmIntentService;
import com.commonsware.cwac.wakeful.WakefulIntentService;

public class AlarmListener implements WakefulIntentService.AlarmListener {

	public void scheduleAlarms(AlarmManager mgr, PendingIntent pi, Context ctxt) {
		// set the alarm to wake up the device at interval of 1 day, and go off
		// at inexact times (for better performance)
		Log.e("louis.fazen", "WakefulIntentService.scheduleAlarms  is called");

		// this is called
		// 1. whenever the app starts if forced is true
		// 2. if there were no Alarms programmed due to boot (e.g. after boot,
		// then receiver calls it)
		// 3. if no alarms programmed due to force close of app (then,
		// regardless of 1, it reschedules)

		// FREQUENCY:
		// If 1 = false as suggested, then alarms are only rescheduled on boot
		// if 1 = true, then alarms are rescheduled on every load (but if both
		// loads and boots are sporadic, always have a slow alarm)
		// Can enhance this by calling it on power charge... which increases it
		// to every day or two but may not resched alarms... may just do work
		// Can also increase this by calling it after completing a task

		// Find the most recent download time, determine the best interval for
		// the alarms...
		ClinicAdapter ca = new ClinicAdapter();
		ca.open();
		long recentDownload = ca.fetchMostRecentDownload();
		ca.close();
		long timeSinceRefresh = System.currentTimeMillis() - recentDownload;
		Log.e("louis.fazen", "Minutes since last refresh: " + timeSinceRefresh / (1000 * 60));

		//establishes threshold for Setting Alarm Frequency
		if (timeSinceRefresh < Constants.MAXIMUM_REFRESH_TIME) {
			mgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 100000, 300000, pi);
		} else {
			mgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 100000, 300000, pi);
		}
		

	}

	public void sendWakefulWork(Context ctxt) {
		// to run the business logic in a background thread, call service
		Log.e("louis.fazen", "sendWakefulWork is called");
		WakefulIntentService.sendWakefulWork(ctxt, AlarmIntentService.class);
	}

	public long getMaxAge() {
		// if interval between alarms is > maxAge, then alarm probably was reset
		// by force-stopping application in settings,
		// so we need to reestablish the Alarm. This logic is taken care of by
		// WakefulIntentService scheduleAlarms()
		// Here we only set the maxAge paramater, suggested to be 2xInterval by
		// CommonsWare
		return (AlarmManager.INTERVAL_FIFTEEN_MINUTES * 2);
	}
}
