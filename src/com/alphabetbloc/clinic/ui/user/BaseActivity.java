package com.alphabetbloc.clinic.ui.user;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SyncStatusObserver;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.services.RefreshDataService;
import com.alphabetbloc.clinic.services.SyncManager;
import com.alphabetbloc.clinic.ui.admin.PreferencesActivity;
import com.alphabetbloc.clinic.utilities.FileUtils;
import com.alphabetbloc.clinic.utilities.UiUtils;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class BaseActivity extends Activity implements SyncStatusObserver {
	// Menu ID's
	private static final int MENU_REFRESH = Menu.FIRST;
	private static final int MENU_USER_PREFERENCES = Menu.FIRST + 1;
	private static final int MENU_ADMIN_PREFERENCES = Menu.FIRST + 2;

	private static final int PROGRESS_DIALOG = 1;
	private static final String TAG = BaseActivity.class.getSimpleName();

	private static ProgressDialog mSyncActiveDialog;
	private static Object mSyncObserverHandle;
	private Context mToastCtx;
	private ScheduledExecutorService mExecutor = Executors.newScheduledThreadPool(5);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (!FileUtils.storageReady()) {
			UiUtils.toastAlert(this, getString(R.string.error_storage_title), getString(R.string.error_storage));
			setResult(RESULT_CANCELED);
			finish();
		}
		mToastCtx = this;
	}

	@Override
	public void onStatusChanged(int which) {
		Log.e(TAG, "SyncStatusObserver Status has Changed");
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (!RefreshDataService.isSyncActive) {
					// Sync is not yet active, so we must be starting a sync
					Log.e(TAG, "SyncStatusChanged: starting a Sync");
					updateSyncProgress();
					// showDialog(PROGRESS_DIALOG);

				} else {
					// we are just completing a sync (whether success or not)
					Log.e(TAG, "SyncStatusChanged: completing sync");
					// dismiss dialog
					if (mSyncActiveDialog != null) {
						Log.e(TAG, "SyncStatusChanged: getting tid of syncDialog");
						mSyncActiveDialog.dismiss();
					}

					// refreshView
					// Intent relaunch = new Intent(App.getApp(),
					// ClinicLauncherActivity.class);
					// relaunch.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					// relaunch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					// startActivity(relaunch);
				}
			}
		});

	}

	@Override
	protected Dialog onCreateDialog(int id) {
		mSyncActiveDialog = new ProgressDialog(this);
		mSyncActiveDialog.setIcon(android.R.drawable.ic_dialog_info);
		mSyncActiveDialog.setTitle(getString(R.string.sync_in_progress_title));
		mSyncActiveDialog.setMessage(getString(R.string.sync_in_progress));
		mSyncActiveDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		mSyncActiveDialog.setCancelable(false);
		return mSyncActiveDialog;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, MENU_REFRESH, MENU_REFRESH, getString(R.string.download_patients)).setIcon(R.drawable.ic_menu_refresh);
		menu.add(0, MENU_USER_PREFERENCES, MENU_USER_PREFERENCES, getString(R.string.pref_settings)).setIcon(android.R.drawable.ic_menu_preferences);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		boolean showMenu = prefs.getBoolean(getString(R.string.key_show_settings_menu), false);
		if (showMenu)
			menu.add(0, MENU_ADMIN_PREFERENCES, MENU_ADMIN_PREFERENCES, getString(R.string.pref_admin_settings)).setIcon(android.R.drawable.ic_lock_lock);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_USER_PREFERENCES:
			Intent user = new Intent(this, PreferencesActivity.class);
			user.putExtra(PreferencesActivity.ADMIN_PREFERENCE, false);
			startActivity(user);
			return true;
		case MENU_ADMIN_PREFERENCES:
			Intent admin = new Intent(this, PreferencesActivity.class);
			admin.putExtra(PreferencesActivity.ADMIN_PREFERENCE, true);
			startActivity(admin);
			return true;
		case MENU_REFRESH:
			SyncManager.syncData();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	// LIFECYCLE
	@Override
	protected void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter(SyncManager.TOAST_SYNC_MESSAGE);
		LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, filter);
		mSyncObserverHandle = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE, this);

		if (RefreshDataService.isSyncActive) {

			Log.i(TAG, "onResume and Sync is active!");
			if (mSyncActiveDialog != null && !mSyncActiveDialog.isShowing())
				mSyncActiveDialog.show();
			else if (mSyncActiveDialog == null)
				showDialog(PROGRESS_DIALOG);

		} else
			Log.i(TAG, "onResume and Sync is NOT active!");

	}

	private void updateSyncProgress() {
		Log.i(TAG, "Updating Progress! with mSyncActiveDialog=" + mSyncActiveDialog);
		SyncManager.sSyncComplete = false;
		if (mSyncActiveDialog != null) {
			Log.e(TAG, "mSyncActiveDialog is not null and SyncManager.sLoopProgress=" + SyncManager.sLoopProgress + " SyncManager.sSyncStep=" + SyncManager.sSyncStep);
			mSyncActiveDialog.setProgress(0);
			SyncManager.sSyncStep = 0;
			SyncManager.sLoopProgress = 0;
			SyncManager.sLoopCount = 0;
		} else
			Log.e(TAG, "mSyncActiveDialog is NULL and SyncManager.sLoopProgress=" + SyncManager.sLoopProgress + " SyncManager.sSyncStep=" + SyncManager.sSyncStep);

		showDialog(PROGRESS_DIALOG);

		mExecutor.schedule(new Runnable() {
			public void run() {

				if (!SyncManager.sSyncComplete) {
					mExecutor.schedule(this, 800, TimeUnit.MILLISECONDS);
					BaseActivity.this.runOnUiThread(new Runnable() {

						@Override
						public void run() {
							// Log.i(TAG, "Updating Progress: Title=" +
							// SyncManager.sSyncTitle + " Step=" +
							// SyncManager.sSyncStep + " Progress=" +
							// SyncManager.sLoopProgress + " Count=" +
							// SyncManager.sLoopCount);
							int loop = (SyncManager.sLoopProgress == SyncManager.sLoopCount) ? 0 : ((int) Math.round(((float) SyncManager.sLoopProgress / (float) SyncManager.sLoopCount) * 20F));
							mSyncActiveDialog.setProgress((SyncManager.sSyncStep * 10) + loop);
							mSyncActiveDialog.setMessage(SyncManager.sSyncTitle);
						}
					});

				}
			}
		}, 0, TimeUnit.MILLISECONDS);

	}

	private BroadcastReceiver onNotice = new BroadcastReceiver() {
		public void onReceive(Context ctxt, Intent i) {

			boolean error = i.getBooleanExtra(SyncManager.TOAST_ERROR, false);
			String toast = i.getStringExtra(SyncManager.TOAST_MESSAGE);

			UiUtils.toastSyncMessage(mToastCtx, toast, error);

			// if (mSyncActiveDialog != null && mSyncActiveDialog.isShowing()) {
			// mSyncActiveDialog.dismiss();
			// mSyncActiveDialog = null;
			// }
			//
			// Intent intent = new Intent(getBaseContext(),
			// RefreshDataActivity.class);
			// intent.putExtra(RefreshDataActivity.DIALOG,
			// RefreshDataActivity.ASK_TO_DOWNLOAD);
			// startActivity(intent);

		}
	};

	@Override
	protected void onPause() {
		super.onPause();
		if (mSyncActiveDialog != null) {
			mSyncActiveDialog.dismiss();
		}
		ContentResolver.removeStatusChangeListener(mSyncObserverHandle);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(onNotice);
	}

	// private void updateSyncProgressOriginal() {
	// SyncManager.sSyncComplete = false;
	// if (mSyncActiveDialog != null)
	// mSyncActiveDialog.setProgress(0);
	// showDialog(PROGRESS_DIALOG);
	// mExecutor.schedule(new ProgressRunnable(), 0, TimeUnit.MILLISECONDS);
	// Log.i(TAG, "Updating Progress! with mSyncActiveDialog=" +
	// mSyncActiveDialog);
	// }
	// class ProgressRunnable implements Runnable {
	// public void run() {
	//
	// if (!SyncManager.sSyncComplete) {
	// mExecutor.schedule(this, 800, TimeUnit.MILLISECONDS);
	// BaseActivity.this.runOnUiThread(new Runnable() {
	//
	// @Override
	// public void run() {
	// // Log.i(TAG, "Updating Progress: Title=" +
	// // SyncManager.sSyncTitle + " Step=" +
	// // SyncManager.sSyncStep + " Progress=" +
	// // SyncManager.sLoopProgress + " Count=" +
	// // SyncManager.sLoopCount);
	// int loop = (SyncManager.sLoopProgress == SyncManager.sLoopCount) ? 0 :
	// ((int) Math.round(((float) SyncManager.sLoopProgress / (float)
	// SyncManager.sLoopCount) * 20F));
	// mSyncActiveDialog.setProgress((SyncManager.sSyncStep * 10) + loop);
	// mSyncActiveDialog.setMessage(SyncManager.sSyncTitle);
	// }
	// });
	//
	// }
	// }
	// }
	//
	// private void updateProgress() {
	// Log.i(TAG, "Updating Progress: Title=" + SyncManager.sSyncTitle +
	// " Step=" + SyncManager.sSyncStep + " Progress=" +
	// SyncManager.sLoopProgress + " Count=" + SyncManager.sLoopCount);
	// int loop = (SyncManager.sLoopProgress == SyncManager.sLoopCount) ? 0 :
	// ((int) Math.round(((float) SyncManager.sLoopProgress / (float)
	// SyncManager.sLoopCount) * 20F));
	// Log.i(TAG, "Updating Progress: Add value=" + loop);
	// mSyncActiveDialog.setProgress((SyncManager.sSyncStep * 10) + loop);
	// mSyncActiveDialog.setMessage(SyncManager.sSyncTitle);
	// // mSyncActiveDialog.setTitle(SyncManager.sSyncTitle);
	//
	// Log.e(TAG, "just updated the Title to=" + SyncManager.sSyncTitle);
	// }

}