package com.alphabetbloc.clinic.services;

import org.odk.clinic.android.activities.DashboardActivity;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.Form;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.SystemClock;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;

/**
 * @author Louis.Fazen@gmail.com
 * 
 *         IntentService is called by AlarmListener at periodic intervals.
 *         Decides whether or not to start ongoing service to monitor
 *         SignalStrength and download clients. After decision, this
 *         IntentService finishes. Holds wakelock.
 */

public class WipeDataService extends WakefulIntentService {

	ClinicAdapter mCa;

	public WipeDataService() {
		super("AppService");
	}

	@Override
	protected void doWakefulWork(Intent intent) {
		mCa = new ClinicAdapter();

		deleteAllClientData();

		while (dataPresent()) {
			deleteAllClientData();
		}

	}

	private void deleteAllClientData() {
		mCa.open();

		mCa.deleteAllObservations();
		mCa.deleteAllFormInstances();
		mCa.deleteAllPatients();
		mCa.deleteAllForms();
		mCa.deleteAllCohorts();

		mCa.close();
	}

	private boolean dataPresent() {
		mCa.open();
		Cursor c;
		c = mCa.fetchAllForms();
		if (c != null && c.getCount() >= 0)
			return true;
		c = mCa.fetchAllPatients(DashboardActivity.LIST_ALL);
		if (c != null && c.getCount() >= 0)
			return true;
		c = mCa.fetchAllCohorts();
		if (c != null && c.getCount() >= 0)
			return true;
		c = mCa.fetchAllObservations();
		if (c != null && c.getCount() >= 0)
			return true;
		c = mCa.fetchAllFormInstances();
		if (c != null && c.getCount() >= 0)
			return true;
		
		Log.e("Clinic WipeDataService", "Client Data Successfully Wiped!");
		return false;
	}

}
