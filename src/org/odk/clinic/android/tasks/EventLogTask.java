package org.odk.clinic.android.tasks;

import org.odk.clinic.android.database.ClinicAdapter;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;

public abstract class EventLogTask extends AsyncTask<Void, Void, Integer> {
	
	private static final boolean EVENT_LOGGING = true;
	private Integer status = 0;
	
	@Override
	protected Integer doInBackground(Void... params) {
		
		if (EVENT_LOGGING) {
			try {
		
				ClinicAdapter ca = new ClinicAdapter();
//				ca.createEvent(event);
				ca.close();
				status = 1;
			}

			catch (Exception e) {
			}
		}
		return status;
	}

	@Override
	protected void onCancelled() {
		// TODO Auto-generated method stub
		super.onCancelled();
	}



	// this task should be in
	// ODK Collect
	// ODK Clinic should have DB where if they select the priority form, it
	// gets logged
	// if they select a non-priority form it also gets logged
	// col 1: priority form number
	// col 2: form selected
	// col 3: system time in millis()
	//
	// Maps
	// Ushahidi?
	//
	// New Apps:
	// Website?! -> Show everything that they are doing...
	// Videos?! -> Show everything that they are doing...
	// How many times does system time need to be updated by NTP?

}