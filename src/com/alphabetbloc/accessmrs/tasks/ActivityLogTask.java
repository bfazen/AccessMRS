package com.alphabetbloc.accessmrs.tasks;


import android.os.AsyncTask;
import android.util.Log;

import com.alphabetbloc.accessmrs.data.ActivityLog;
import com.alphabetbloc.accessmrs.providers.Db;
import com.alphabetbloc.accessmrs.utilities.App;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 *
 */
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
				Db.open().createActivityLog(newActivity);
			}

			catch (Exception e) {
			}
		}
		if (App.DEBUG) Log.v("ActivityLogTask", "newactivity added to db!");
		
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