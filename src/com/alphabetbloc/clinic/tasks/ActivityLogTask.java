package com.alphabetbloc.clinic.tasks;


import com.alphabetbloc.clinic.data.ActivityLog;
import com.alphabetbloc.clinic.providers.DbProvider;

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
				DbProvider.openDb().createActivityLog(newActivity);
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