package com.alphabetbloc.clinic.services;

import java.io.IOException;
import java.util.List;

import org.apache.http.ParseException;
import org.apache.http.auth.AuthenticationException;
import org.json.JSONException;

import com.alphabetbloc.clinic.listeners.SyncDataListener;
import com.alphabetbloc.clinic.tasks.DownloadDataTask;
import com.alphabetbloc.clinic.tasks.SyncDataTask;
import com.alphabetbloc.clinic.tasks.UploadDataTask;
import com.alphabetbloc.clinic.utilities.App;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;


public class SyncAdapter extends AbstractThreadedSyncAdapter implements SyncDataListener{

    private static final String TAG = "SyncAdapter";
    private static final String SYNC_MARKER_KEY = "com.example.android.samplesync.marker";
    private static final boolean NOTIFY_AUTH_FAILURE = true;

    private final AccountManager mAccountManager;

    private final Context mContext;

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context;
        mAccountManager = AccountManager.get(context);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, final SyncResult syncResult) {

        try {
            
        	
        		Log.e(TAG, "perform sync with authority=" + authority + " provider=" + provider + " account=" + account);
        		try {
        				Handler h = new Handler(Looper.getMainLooper());
        				h.post(new Runnable() {
        					public void run() {
        						try {
        							SyncDataTask syncTask = new SyncDataTask();
        							syncTask.setSyncListener(SyncAdapter.this);
        							syncTask.execute(syncResult);
        						} catch (Exception e) {
        							e.printStackTrace();
        						}
        					}
        				});

        			

        		} catch (Exception e) {
        			// TODO: handle exception
        			++syncResult.stats.numIoExceptions;
        			Log.e(TAG, "Error occurred from inside Service... we are stopping the service.  syncResult=" + syncResult);
        			Intent i = new Intent(App.getApp(), RefreshDataService.class);
        			App.getApp().stopService(i);
        			// user is actively entering data, so sync later
        			// TODO return false to sync manager
        		}

        	
        		Log.e(TAG, "No Errors occurred, but we are at the end of the onPerformSync with syncResult=" + syncResult);
        } catch (final ParseException e) {
            Log.e(TAG, "ParseException", e);
            syncResult.stats.numParseExceptions++;
        }
        
        Log.e(TAG, "Last line of onPerformSync with syncResult=" + syncResult);
        
    }

    /**
     * This helper function fetches the last known high-water-mark
     * we received from the server - or 0 if we've never synced.
     * @param account the account we're syncing
     * @return the change high-water-mark
     */
    private long getServerSyncMarker(Account account) {
        String markerString = mAccountManager.getUserData(account, SYNC_MARKER_KEY);
        if (!TextUtils.isEmpty(markerString)) {
            return Long.parseLong(markerString);
        }
        return 0;
    }

    /**
     * Save off the high-water-mark we receive back from the server.
     * @param account The account we're syncing
     * @param marker The high-water-mark we want to save.
     */
    private void setServerSyncMarker(Account account, long marker) {
        mAccountManager.setUserData(account, SYNC_MARKER_KEY, Long.toString(marker));
    }

	@Override
	public void sslSetupComplete(String result, SyncResult syncResult) {
		Log.e(TAG, "sslSetupComplete is called with result=" + result + " and syncResult=" + syncResult);

		if (Boolean.valueOf(result)) {
			Log.e(TAG, "successfully setup connection, about to create a new upload task:");
			UploadDataTask uploadTask = new UploadDataTask();
			uploadTask.setSyncListener(this);
			uploadTask.execute(syncResult);

			DownloadDataTask downloadTask = new DownloadDataTask();
			downloadTask.setSyncListener(this);
			downloadTask.execute(syncResult);
		} else {

			Log.e(TAG, "sslSetup Failed!  Finishing Service... with syncResult=" + syncResult);
//			stopSelf();
		}
		
	}

	@Override
	public void uploadComplete(String result) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void downloadComplete(String result) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void syncComplete(String result, SyncResult syncResult) {
		// TODO Auto-generated method stub
		Log.e(TAG, "RefreshDataService Calling stopself! after syncComplete! with syncResult=" + syncResult);
	}

	@Override
	public void progressUpdate(String message, int progress, int max) {
		// TODO Auto-generated method stub
		
	}
}
