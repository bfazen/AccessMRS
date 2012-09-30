package com.alphabetbloc.clinic.services;

import java.util.Iterator;
import java.util.List;

import org.odk.clinic.android.openmrs.Constants;

import android.accounts.Account;
import android.accounts.OperationCanceledException;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.data.DbAdapter;
import com.alphabetbloc.clinic.listeners.SyncDataListener;
import com.alphabetbloc.clinic.tasks.DownloadDataTask;
import com.alphabetbloc.clinic.tasks.SyncDataTask;
import com.alphabetbloc.clinic.tasks.UploadDataTask;
import com.alphabetbloc.clinic.ui.user.CreatePatientActivity;
import com.alphabetbloc.clinic.ui.user.DashboardActivity;
import com.alphabetbloc.clinic.ui.user.ListPatientActivity;
import com.alphabetbloc.clinic.ui.user.RefreshDataActivity;
import com.alphabetbloc.clinic.utilities.App;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com) This checks the user activity
 *         before refreshing the patient list as background service or
 *         foreground activity.
 */
public class RefreshDataService extends Service implements SyncDataListener {

	private static final String TAG = RefreshDataService.class.getSimpleName();
	private static NotificationManager mNM;
	private static int NOTIFICATION = 1;
	public static final String REFRESH_BROADCAST = "com.alphabetbloc.clinic.services.SignalStrengthService";
	private static ActivityManager mActivityManager;
	private static boolean sRequestUserToSync;
	private static SyncAdapterImpl mSyncAdapter = null;

	private static class SyncAdapterImpl extends AbstractThreadedSyncAdapter {
		private Context mSyncAdapterContext;

		public SyncAdapterImpl(Context context) {
			super(context, true);
			mSyncAdapterContext = context;
		}

		@Override
		public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
			// Can't send SyncResult asynchronously to syncManager!?
			// so we manage sync by checking previous download time

			// boolean isSyncActive = ContentResolver.isSyncActive(account,
			// authority);
			// if (isSyncActive){
			// syncResult = SyncResult.ALREADY_IN_PROGRESS;
			// Log.e(TAG, "Sync is already in progress!");
			// return;
			// }

			// establish threshold for syncing (i.e. do not sync continuously)
			long recentDownload = DbAdapter.openDb().fetchMostRecentDownload();
			long timeSinceRefresh = System.currentTimeMillis() - recentDownload;
			Log.e("louis.fazen", "Minutes since last refresh: " + timeSinceRefresh / (1000 * 60));
			if (timeSinceRefresh < Constants.MAXIMUM_REFRESH_TIME) {

				long timeToNextSync = Constants.MAXIMUM_REFRESH_TIME - timeSinceRefresh;
				syncResult.delayUntil = timeToNextSync;
				Log.e(TAG, "Synced recently... lets delay the sync until ! timetosync=" + timeToNextSync);

			} else {

				try {
					showNotification();
					RefreshDataService.performSync(mSyncAdapterContext, account, extras, authority, provider, syncResult);

				} catch (OperationCanceledException e) {
					e.printStackTrace();
					Intent i = new Intent(App.getApp(), RefreshDataService.class);
					App.getApp().stopService(i);
				}
			}

		}

	}

	@Override
	public IBinder onBind(Intent intent) {
		IBinder ret = null;
		ret = getSyncAdapter().getSyncAdapterBinder();
		return ret;
	}

	private SyncAdapterImpl getSyncAdapter() {
		if (mSyncAdapter == null)
			mSyncAdapter = new SyncAdapterImpl(this);
		return mSyncAdapter;

	}

	private static void performSync(Context context, Account account, Bundle extras, String authority, ContentProviderClient provider, final SyncResult syncResult) throws OperationCanceledException {

		Log.e(TAG, "perform sync with authority=" + authority + " provider=" + provider + " account=" + account);
		if (!enteringClientData()) {

			if (sRequestUserToSync) {
				// ask user if they wish to start sync
				Intent broadcast = new Intent(REFRESH_BROADCAST);
				LocalBroadcastManager.getInstance(App.getApp()).sendBroadcast(broadcast);

				Intent i = new Intent(App.getApp(), RefreshDataService.class);
				Log.e(TAG, "about to kill RefreshDataService!");
				App.getApp().stopService(i);

			} else {

				Handler h = new Handler(Looper.getMainLooper());
				h.post(new Runnable() {
					public void run() {
						try {
							SyncDataTask syncTask = new SyncDataTask();
							syncTask.setSyncListener(getInstance());
							syncTask.execute(syncResult);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});

			}

		} else {

			++syncResult.stats.numIoExceptions;
			Log.e(TAG, "User Entering Data... so do nothing.  syncResult=" + syncResult);
			Intent i = new Intent(App.getApp(), RefreshDataService.class);
			App.getApp().stopService(i);
			// user is actively entering data, so sync later
			// TODO return false to sync manager
		}

	}

	private static void showNotification() {

		mNM = (NotificationManager) App.getApp().getSystemService(NOTIFICATION_SERVICE);
		CharSequence text = App.getApp().getText(R.string.ss_service_started);
		Notification notification = new Notification(R.drawable.icon, text, System.currentTimeMillis());
		PendingIntent contentIntent = PendingIntent.getActivity(App.getApp(), 0, new Intent(App.getApp(), DashboardActivity.class), Intent.FLAG_ACTIVITY_NEW_TASK);
		notification.setLatestEventInfo(App.getApp(), App.getApp().getText(R.string.ss_service_label), text, contentIntent);
		mNM.notify(NOTIFICATION, notification);

		// if (notification != null) {
		// startForeground(NOTIFICATION, notification);
		// Log.e(TAG, "SignalStrengthService Started in Foreground");
		// }
	}

	private static RefreshDataService instance;

	public static synchronized RefreshDataService getInstance() {
		if (instance == null) {
			instance = new RefreshDataService();
		}
		return instance;
	}

	private static boolean enteringClientData() {
		boolean enteringData = true;
		sRequestUserToSync = false;

		RunningAppProcessInfo foregroundApp = getForegroundApp();
		String collectPackage = "org.odk.collect.android";

		// Collect: Never Sync
		if (foregroundApp.processName.equals(collectPackage)) {
			return enteringData;
		}

		// Clinic: Depends on Current Activity
		if (foregroundApp.processName.equals(App.getApp().getPackageName())) {
			ComponentName foreGround = getActivityForApp(foregroundApp);
			if (foreGround != null) {

				String createPatientClass = CreatePatientActivity.class.getSimpleName();
				String listPatientClass = ListPatientActivity.class.getSimpleName();
				String refreshDataClass = RefreshDataActivity.class.getSimpleName();
				if (foreGround.getClassName().equals(createPatientClass) || foreGround.getClassName().equals(refreshDataClass) || (foreGround.getClassName().equals(listPatientClass) && ListPatientActivity.mListType == DashboardActivity.LIST_SIMILAR_CLIENTS)) {

					// do nothing for these clinic activities
					return enteringData;

				} else {
					// request user to sync otherwise
					sRequestUserToSync = true;
				}
			}

		}

		return !enteringData;
	}

	private static RunningAppProcessInfo getForegroundApp() {
		RunningAppProcessInfo result = null, info = null;

		if (mActivityManager == null)
			mActivityManager = (ActivityManager) App.getApp().getSystemService(Context.ACTIVITY_SERVICE);
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

	private static ComponentName getActivityForApp(RunningAppProcessInfo target) {
		ComponentName result = null;
		ActivityManager.RunningTaskInfo info;

		if (target == null)
			return null;

		if (mActivityManager == null)
			mActivityManager = (ActivityManager) App.getApp().getSystemService(Context.ACTIVITY_SERVICE);
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

	@Override
	public void sslSetupComplete(String success, SyncResult syncResult) {
		Log.e(TAG, "sslSetupComplete is called");

		if (Boolean.valueOf(success)) {
			Log.e(TAG, "successfully setup connection, about to create a new upload task:");
			UploadDataTask uploadTask = new UploadDataTask();
			uploadTask.setSyncListener(getInstance());
			uploadTask.execute(syncResult);

			DownloadDataTask downloadTask = new DownloadDataTask();
			downloadTask.setSyncListener(getInstance());
			downloadTask.execute(syncResult);
		} else {

			Log.e(TAG, "sslSetup Failed!  Finishing Service... with syncResult=" + syncResult);
			stopSelf();
		}
	}

	@Override
	public void progressUpdate(String message, int progress, int max) {
		// don't really need to do anything here, b/c not updating UI
	}

	@Override
	public void uploadComplete(String resultString) {
		// don't really need to do anything here, b/c not updating UI
	}

	@Override
	public void downloadComplete(String result) {
		// don't really need to do anything here, b/c not updating UI
	}

	@Override
	public void syncComplete(String result, SyncResult syncResult) {
		// TODO return success to syncmanager
		// update the time?!
		Log.e(TAG, "RefreshDataService Calling stopself! after syncComplete! with syncResult=" + syncResult);
		stopSelf();
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "Shutting down the Service" + TAG);
		if (mNM != null)
			mNM.cancel(NOTIFICATION);

		super.onDestroy();
	}

	// //// Not using the following:

	// private boolean isStillActive(RunningAppProcessInfo process,
	// ComponentName activity) {
	// // activity can be null in cases, where one app starts another. for
	// // example, astro
	// // starting rock player when a move file was clicked. we dont have an
	// // activity then,
	// // but the package exits as soon as back is hit. so we can ignore the
	// // activity
	// // in this case
	// if (process == null)
	// return false;
	//
	// RunningAppProcessInfo currentFg = getForegroundApp();
	// ComponentName currentActivity = getActivityForApp(currentFg);
	//
	// if (currentFg != null &&
	// currentFg.processName.equals(process.processName) && (activity == null ||
	// currentActivity.compareTo(activity) == 0))
	// return true;
	//
	// Log.i("RefreshClientServive",
	// "isStillActive returns false - CallerProcess: " + process.processName +
	// " CurrentProcess: " + (currentFg == null ? "null" :
	// currentFg.processName) + " CallerActivity:" + (activity == null ? "null"
	// : activity.toString())
	// + " CurrentActivity: " + (currentActivity == null ? "null" :
	// currentActivity.toString()));
	// return false;
	// }
	//
	// private boolean isRunningService(String processname) {
	// // if(processname==null || processname.isEmpty())
	// if (processname == null || processname.length() < 1)
	// return false;
	//
	// RunningServiceInfo service;
	//
	// if (mActivityManager == null)
	// mActivityManager = (ActivityManager)
	// getSystemService(Context.ACTIVITY_SERVICE);
	// // mContext.getSystemService(Context.ACTIVITY_SERVICE);
	// List<RunningServiceInfo> l = mActivityManager.getRunningServices(9999);
	// Iterator<RunningServiceInfo> i = l.iterator();
	// while (i.hasNext()) {
	// service = i.next();
	// if (service.process.equals(processname))
	// return true;
	// }
	//
	// return false;
	// }

}