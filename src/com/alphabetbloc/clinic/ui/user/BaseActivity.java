package com.alphabetbloc.clinic.ui.user;

import android.accounts.Account;
import android.accounts.AccountManager;
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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.services.RefreshDataService;
import com.alphabetbloc.clinic.ui.admin.ClinicLauncherActivity;
import com.alphabetbloc.clinic.ui.admin.PreferencesActivity;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.FileUtils;

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
	
	public static final int PROGRESS_DIALOG = 1;
	private static final String TAG = BaseActivity.class.getSimpleName();

	protected static ProgressDialog mProgressDialog;
	protected static Object mSyncObserverHandle;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (!FileUtils.storageReady()) {
			showCustomToast(getString(R.string.error, getString(R.string.storage_error)));
			setResult(RESULT_CANCELED);
			finish();
		}
	}


	@Override
	public void onStatusChanged(int which) {

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				checkSyncActivity();
			}
		});

	}

	public boolean checkSyncActivity() {
		boolean syncActive = false;
		AccountManager accountManager = AccountManager.get(App.getApp());
		Account[] accounts = accountManager.getAccountsByType(App.getApp().getString(R.string.app_account_type));

		if (accounts.length <= 0)
			return false;

		syncActive = ContentResolver.isSyncActive(accounts[0], getString(R.string.app_provider_authority));

		if (syncActive) {
			//we are starting a sync
			showDialog(PROGRESS_DIALOG);
			Log.e(TAG, "sync is active");
		} else {

			Log.e(TAG, "sync is not active");
			
			//we have just completed a sync
			if (mProgressDialog != null) {
				mProgressDialog.dismiss();
			}
			
			Intent relaunch = new Intent(this, ClinicLauncherActivity.class);
			relaunch.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			relaunch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(relaunch);
			finish();
		}

		return syncActive;
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		if (mProgressDialog != null && mProgressDialog.isShowing()) {
			mProgressDialog.dismiss();
		}

		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
		mProgressDialog.setTitle(getString(R.string.sync_in_progress_title));
		mProgressDialog.setMessage(getString(R.string.sync_in_progress));
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		mProgressDialog.setIndeterminate(true);
		mProgressDialog.setCancelable(false);
		return mProgressDialog;
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
			refreshData();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	private void refreshData() {
		Intent id = new Intent(getBaseContext(), RefreshDataActivity.class);
		id.putExtra(RefreshDataActivity.DIALOG, RefreshDataActivity.DIRECT_TO_DOWNLOAD);
		startActivity(id);
	}
	
	// LIFECYCLE
	@Override
	protected void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter(RefreshDataService.REFRESH_BROADCAST);
		LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, filter);
		mSyncObserverHandle = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE, this);
		if (mProgressDialog != null && !mProgressDialog.isShowing()) {
			mProgressDialog.show();
		}
	}

	private BroadcastReceiver onNotice = new BroadcastReceiver() {
		public void onReceive(Context ctxt, Intent i) {

			Intent intent = new Intent(getBaseContext(), RefreshDataActivity.class);
			intent.putExtra(RefreshDataActivity.DIALOG, RefreshDataActivity.ASK_TO_DOWNLOAD);
			startActivity(intent);

		}
	};

	@Override
	protected void onPause() {
		super.onPause();
		ContentResolver.removeStatusChangeListener(mSyncObserverHandle);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(onNotice);
	}
	
	protected void showCustomToast(String message) {
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.toast_view, null);

		// set the text in the view
		TextView tv = (TextView) view.findViewById(R.id.message);
		tv.setText(message);

		Toast t = new Toast(this);
		t.setView(view);
		t.setDuration(Toast.LENGTH_LONG);
		t.setGravity(Gravity.CENTER, 0, 0);
		t.show();
	}
}