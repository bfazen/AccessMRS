package org.odk.clinic.android.tasks;

import android.os.AsyncTask;
import org.odk.clinic.android.database.ClinicAdapter;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.util.Log;

public class ActivityLogTask extends AsyncTask<Void, Void, Void> {
	
	private static final boolean ACTIVITY_LOGGING = true;
	private ActivityLog newActivity;
	
	  public ActivityLogTask(ActivityLog activitylogged) {
		  super();
	        newActivity = activitylogged;

	    }

	  
	  
	protected Void doInBackground(Void... params) {
		
		if (ACTIVITY_LOGGING) {
			try {
		
				ClinicAdapter ca = new ClinicAdapter();
				ca.createActivityLog(newActivity);
				ca.close();
			}

			catch (Exception e) {
			}
		}
		Log.e("ActivityLogTask", "newactivity added to db!");
		return null;
	}

	@Override
	protected void onCancelled() {
		// TODO Auto-generated method stub
		super.onCancelled();
	}

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
