package org.odk.clinic.android.tasks;

import org.odk.clinic.android.database.DbAdapter;
import org.odk.clinic.android.openmrs.ActivityLog;

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
				DbAdapter.openDb().createActivityLog(newActivity);
			}

			catch (Exception e) {
			}
		}
		Log.d("ActivityLogTask", "newactivity added to db!");
		
		return null;
		
	}

	@Override
	protected void onCancelled() {
		super.onCancelled();
	}



	@Override
	protected void onPostExecute(Void result) {
		super.onPostExecute(result);
	}
	
}