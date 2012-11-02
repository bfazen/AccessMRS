package com.alphabetbloc.clinic.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.ui.admin.ClinicLauncherActivity;
import com.alphabetbloc.clinic.ui.user.DashboardActivity;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.ClinicLauncher;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com) This checks the user activity
 *         before refreshing the patient list as background service or
 *         foreground activity.
 */
public class RefreshDataService extends Service {
	public static final String REFRESH_BROADCAST = "com.alphabetbloc.clinic.services.SignalStrengthService";
	private static final int NOTIFICATION = 1;
	private static final String TAG = RefreshDataService.class.getSimpleName();
	private static final Object sSyncAdapterLock = new Object();
	private static SyncAdapter sSyncAdapter = null;
	private static NotificationManager mNM;
	public static boolean isSyncActive = false;

	@Override
	public void onCreate() {
		Thread.currentThread().setName(TAG);
		
		if (!ClinicLauncher.isSetupComplete()) {
			if (!ClinicLauncherActivity.sLaunching) {
				Log.e(TAG, "Clinic is Not Setup... and not currently active... so RefreshDataService is requesting setup");
				Intent i = new Intent(App.getApp(), ClinicLauncherActivity.class);
				i.putExtra(ClinicLauncherActivity.LAUNCH_DASHBOARD, false);
				i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(i);
			}
			Log.e(TAG, "Clinic is Not Setup... so RefreshDataService is ending");
			stopSelf();
			return;
		}
		
		isSyncActive = true;
		showNotification();
		Log.i(TAG, "syncActive! Creating a new service");
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
		Log.i(TAG, "syncNotActive, Shutting down the Service");
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
