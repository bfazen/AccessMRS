package com.alphabetbloc.clinic.services;

import java.util.Iterator;
import java.util.List;

import org.odk.clinic.android.R;
import org.odk.clinic.android.activities.DashboardActivity;
import org.odk.clinic.android.activities.ListPatientActivity;
import org.odk.clinic.android.activities.RefreshDataActivity;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com) (excerpts from curioustechizen
 *         from stackoverflow)
 * 
 *         This checks the signal strength, data connectivity and user activity
 *         before refreshing the patient list as background service.
 */
public class SignalStrengthService extends Service {

	private ActivityManager mActivityManager;
	private TelephonyManager mTelephonyManager;
	private PhoneStateListener mPhoneStateListener;
	private static final String TAG = "SignalStrengthService";

	private static volatile PowerManager.WakeLock lockStatic = null;
	static final String NAME = "com.alphabetbloc.clinic.android.RefreshDataActivity";

	private NotificationManager mNM;
	private int NOTIFICATION = 1;
	private static int countN;
	private static int countS;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		Log.e(TAG, "SignalStrengthService Created");
		// Display a notification about us starting. We put an icon in the
		// status bar.
		mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		// showNotification();

		CharSequence text = getText(R.string.ss_service_started);
		Notification notification = new Notification(R.drawable.icon, text, System.currentTimeMillis());
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, DashboardActivity.class), Intent.FLAG_ACTIVITY_NEW_TASK);
		notification.setLatestEventInfo(this, getText(R.string.ss_service_label), text, contentIntent);
		mNM.notify(NOTIFICATION, notification);

		Log.e(TAG, "text=" + text);
		if (notification != null) {
			// startForeground(NOTIFICATION, notification);
			Log.e(TAG, "SignalStrengthService Started in Foreground");
		}

		mTelephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
		mPhoneStateListener = new PhoneStateListener() {
			@Override
			public void onSignalStrengthsChanged(SignalStrength signalStrength) {
				int asu = signalStrength.getGsmSignalStrength();

				if (asu >= 7 && asu < 32) {
					if (networkAvailable())
						refreshClientsNow();
					else if (countN++ > 5)
						updateService();
				} else if (asu < 1 || asu > 32 || countS++ > 8)
					stopSelf();

				// if (countN < 10 && countN > 5)
				// updateService();
				// if (count > 10)
				// stopSelf();

				Log.e(TAG, "asu=" + asu + " countN=" + countN + " countS=" + countS);
				super.onSignalStrengthsChanged(signalStrength);
			}

			@Override
			public void onServiceStateChanged(ServiceState serviceState) {
				// do something
				Log.d("louis.fazen", "Service State changed! New state = " + serviceState.getState());
				super.onServiceStateChanged(serviceState);
			}
		};
		super.onCreate();
	}

	/*
	 * int iconLevel = -1; if (asu <= 2 || asu == 99) iconLevel = 0; // 0 or 99
	 * = no signal else if (asu >= 12) iconLevel = 4; // very good signal else
	 * if (asu >= 8) iconLevel = 3; // good signal else if (asu >= 5) iconLevel
	 * = 2; // poor signal else iconLevel = 1; // <5 is very poor signal
	 */

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.e(TAG, "SignalStrengthService Started with start id " + startId + ": " + intent);

		// Register the listener, which effectively starts the service
		mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS | PhoneStateListener.LISTEN_SERVICE_STATE);

		return super.onStartCommand(intent, flags, startId);
	}

	private boolean networkAvailable() {
		boolean dataNetwork = false;
		ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
		if (activeNetworkInfo != null)
			dataNetwork = true;
		return dataNetwork;
	}

	private void updateService() {
		// if 2G, then update to 3G?
		int nt = mTelephonyManager.getNetworkType();
		if (nt < 3)
			Log.d(TAG, "network type =" + nt);

		/*
		 * switch (nt) { case 1: return GPRS; case 2: return EDGE; case 3:
		 * return UMTS; case 8: return HSDPA; case 9: return HSUPA; case 10:
		 * return HSPA; default: return UNKNOWN; }
		 */

	}

	private void showNotification() {
		// In this sample, we'll use the same text for the ticker and the
		// expanded notification
		CharSequence text = getText(R.string.ss_service_started);

		// Set the icon, scrolling text and timestamp
		Notification notification = new Notification(R.drawable.icon, text, System.currentTimeMillis());

		// The PendingIntent to launch our activity if the user selects this
		// notification
		// TODO: check this, as I am creating a new, and potentially second
		// dashboard activity... maybe need to return a singleton?
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, DashboardActivity.class), Intent.FLAG_ACTIVITY_NEW_TASK);

		// Set the info for the views that show in the notification panel.
		notification.setLatestEventInfo(this, getText(R.string.ss_service_label), text, contentIntent);

		// Send the notification.
		mNM.notify(NOTIFICATION, notification);
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "Shutting down the Service" + TAG);
		mNM.cancel(NOTIFICATION);
		mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
		countS = 0;
		countN = 0;
		super.onDestroy();
	}

	private void refreshClientsNow() {
		Log.e(TAG, "RefreshNow Called");
		RunningAppProcessInfo info = null;
		boolean refreshClients = true;
		int aDialog = RefreshDataActivity.DONT_SHOW_PROGRESS;
		String collectPackage = "org.odk.collect.android";
		String clinicPackage = "org.odk.clinic.android";
		String createPatientClass = "org.odk.clinic.android.activities.CreatePatientActivity";
		String listPatientClass = "org.odk.clinic.android.activities.ListPatientActivity";

		if (mActivityManager == null)
			mActivityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningAppProcessInfo> l = mActivityManager.getRunningAppProcesses();
		Iterator<RunningAppProcessInfo> i = l.iterator();
		while (i.hasNext()) {
			info = i.next();
			// If Collect is visible, set refreshClients to false
			if (info.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {

				if (info.processName.equals(collectPackage))
					refreshClients = false;

				if (info.processName.equals(clinicPackage)) {
					ComponentName foreGround = getActivityForApp(info);
					// if (isStillActive(info, foreGround)) {
					if (foreGround != null) {
						if (foreGround.getClassName().equals(createPatientClass))
							refreshClients = false;
						else if (foreGround.getClassName().equals(listPatientClass) && ListPatientActivity.mListType == DashboardActivity.LIST_SIMILAR_CLIENTS)
							refreshClients = false;
						else
							aDialog = RefreshDataActivity.ASK_TO_DOWNLOAD;
					} else
						aDialog = RefreshDataActivity.ASK_TO_DOWNLOAD;

				}
			}

		}

		if (refreshClients) {
			Log.e(TAG, "RefreshClient starting RefreshDataActivity with dialog=" + aDialog);
			Intent id = new Intent(getApplicationContext(), RefreshDataActivity.class);
			id.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			id.putExtra(RefreshDataActivity.DIALOG, aDialog);
			startActivity(id);
		}
	}

	private RunningAppProcessInfo getForegroundApp() {
		RunningAppProcessInfo result = null, info = null;

		if (mActivityManager == null)
			mActivityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		// mContext.getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningAppProcessInfo> l = mActivityManager.getRunningAppProcesses();
		Iterator<RunningAppProcessInfo> i = l.iterator();
		while (i.hasNext()) {
			info = i.next();
			if (info.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
				result = info;
				break;
			}
		}
		return result;
	}

	private ComponentName getActivityForApp(RunningAppProcessInfo target) {
		ComponentName result = null;
		ActivityManager.RunningTaskInfo info;

		if (target == null)
			return null;

		if (mActivityManager == null)
			mActivityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		// mContext.getSystemService(Context.ACTIVITY_SERVICE);
		List<ActivityManager.RunningTaskInfo> l = mActivityManager.getRunningTasks(9999);
		Iterator<ActivityManager.RunningTaskInfo> i = l.iterator();

		while (i.hasNext()) {
			info = i.next();
			if (info.baseActivity.getPackageName().equals(target.processName)) {
				result = info.topActivity;
				break;
			}
		}

		return result;
	}

	private boolean isStillActive(RunningAppProcessInfo process, ComponentName activity) {
		// activity can be null in cases, where one app starts another. for
		// example, astro
		// starting rock player when a move file was clicked. we dont have an
		// activity then,
		// but the package exits as soon as back is hit. so we can ignore the
		// activity
		// in this case
		if (process == null)
			return false;

		RunningAppProcessInfo currentFg = getForegroundApp();
		ComponentName currentActivity = getActivityForApp(currentFg);

		if (currentFg != null && currentFg.processName.equals(process.processName) && (activity == null || currentActivity.compareTo(activity) == 0))
			return true;

		Log.i("RefreshClientServive", "isStillActive returns false - CallerProcess: " + process.processName + " CurrentProcess: " + (currentFg == null ? "null" : currentFg.processName) + " CallerActivity:" + (activity == null ? "null" : activity.toString())
				+ " CurrentActivity: " + (currentActivity == null ? "null" : currentActivity.toString()));
		return false;
	}

	private boolean isRunningService(String processname) {
		// if(processname==null || processname.isEmpty())
		if (processname == null || processname.length() < 1)
			return false;

		RunningServiceInfo service;

		if (mActivityManager == null)
			mActivityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		// mContext.getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningServiceInfo> l = mActivityManager.getRunningServices(9999);
		Iterator<RunningServiceInfo> i = l.iterator();
		while (i.hasNext()) {
			service = i.next();
			if (service.process.equals(processname))
				return true;
		}

		return false;
	}

	private void createWakeLock() {
		// first call:
		if (lockStatic == null) {
			PowerManager mgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
			lockStatic = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, NAME);
			lockStatic.setReferenceCounted(true);
			lockStatic.acquire();

			Log.e("louis.fazen", "lockStatic.acquire()=" + lockStatic.toString());

			// PowerManager pm = (PowerManager)
			// getSystemService(Context.POWER_SERVICE);
			// lockStatic =
			// mgr.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,
			// "bbbb");
			// lockStatic.acquire();

			// may need:
			// getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN |
			// WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
			// WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
		}

		// then call:
		if (lockStatic.isHeld()) {

			lockStatic.release();
			Log.e("louis.fazen", "Called lockStatic.release()=" + lockStatic.toString());
		}
	}

}