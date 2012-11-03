package com.alphabetbloc.accessmrs.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.alphabetbloc.accessmrs.providers.Db;
import com.alphabetbloc.accessmrs.ui.admin.AccessMrsLauncherActivity;
import com.alphabetbloc.accessmrs.ui.user.DashboardActivity;
import com.alphabetbloc.accessmrs.utilities.App;
import com.alphabetbloc.accessmrs.utilities.AccessMrsLauncher;
import com.alphabetbloc.accessmrs.R;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com) This checks the user activity
 *         before refreshing the patient list as background service or
 *         foreground activity.
 */
public class RefreshDataService extends Service {
	public static final String REFRESH_BROADCAST = "com.alphabetbloc.accessmrs.services.SignalStrengthService";
	private static final int NOTIFICATION = 1;
	private static final String TAG = RefreshDataService.class.getSimpleName();
	private static final Object sSyncAdapterLock = new Object();
	private static SyncAdapter sSyncAdapter = null;
	private static NotificationManager mNM;
	public static boolean isSyncActive = false;

	@Override
	public void onCreate() {
		Log.v(TAG, "Sync is now Active! Creating a new service");
		Thread.currentThread().setName(TAG);

		// Dont Sync if Setup is Incomplete
		if (!AccessMrsLauncher.isSetupComplete()) {
			if (!AccessMrsLauncherActivity.sLaunching) {
				Log.v(TAG, "AccessMRS is Not Setup... and not currently active... so RefreshDataService is requesting setup");
				Intent i = new Intent(App.getApp(), AccessMrsLauncherActivity.class);
				i.putExtra(AccessMrsLauncherActivity.LAUNCH_DASHBOARD, false);
				i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(i);
			}
			Log.v(TAG, "AccessMRS is Not Setup. Cancelling sync until AccessMRS setup is complete.");
			SyncManager.sCancelSync = true;

		// Dont AutoSync if Manual Sync just completed...
		} else if (!SyncManager.sStartSync) {
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getApp());
			String minRefresh = prefs.getString(getString(R.string.key_min_refresh_seconds), getString(R.string.default_min_refresh_seconds));
			int minRefreshMs = Integer.valueOf(minRefresh) * 1000;
			int delta = (int) (System.currentTimeMillis() - Db.open().fetchMostRecentDownload());
			
			if (delta < minRefreshMs) {
				Log.v(TAG, "Sync was recently completed. Not performing sync at this time.");
				SyncManager.sCancelSync = true;
			}
		}

		if (!SyncManager.sCancelSync)
			showNotification();

		isSyncActive = true;
		synchronized (sSyncAdapterLock) {
			if (sSyncAdapter == null) {
				sSyncAdapter = new SyncAdapter(getApplicationContext(), true);
			}
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return sSyncAdapter.getSyncAdapterBinder();
	}

	@Override
	public void onDestroy() {
		isSyncActive = false;
		Log.v(TAG, "syncNotActive, Shutting down the Service");
		if (mNM != null)
			mNM.cancel(NOTIFICATION);
		super.onDestroy();
	}

	private void showNotification() {

		mNM = (NotificationManager) App.getApp().getSystemService(NOTIFICATION_SERVICE);
		CharSequence text = App.getApp().getText(R.string.ss_service_started);
		Notification notification = new Notification(R.drawable.icon, text, System.currentTimeMillis());
		PendingIntent contentIntent = PendingIntent.getActivity(App.getApp(), 0, new Intent(App.getApp(), DashboardActivity.class), Intent.FLAG_ACTIVITY_NEW_TASK);
		notification.setLatestEventInfo(App.getApp(), App.getApp().getText(R.string.ss_service_label), text, contentIntent);
		mNM.notify(NOTIFICATION, notification);

	}
}
