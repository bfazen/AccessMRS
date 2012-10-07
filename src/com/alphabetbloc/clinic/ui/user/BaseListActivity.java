package com.alphabetbloc.clinic.ui.user;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.app.ListActivity;
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
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
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
 * @author Louis Fazen (louis.fazen@gmail.com) (All Methods except where noted
 *         otherwise)
 * 
 * @author Carl Hartung (I think... Wrote the ShowCustomToast methods for
 *         Collect)
 */
public class BaseListActivity extends ListActivity implements SyncStatusObserver {

	// Swiping Parameters
	protected static final int SWIPE_MIN_DISTANCE = 120;
	protected static final int SWIPE_MAX_OFF_PATH = 250;
	protected static final int SWIPE_THRESHOLD_VELOCITY = 200;

	// Menu ID's
	private static final int MENU_REFRESH = Menu.FIRST;
	private static final int MENU_USER_PREFERENCES = Menu.FIRST + 1;
	private static final int MENU_ADMIN_PREFERENCES = Menu.FIRST + 2;

	//Dialog
	private static final int PROGRESS_DIALOG = 1;

	private static Object mSyncObserverHandle;
	private static ProgressDialog mSyncActiveDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		if (!FileUtils.storageReady()) {
			showCustomToast(getString(R.string.error, R.string.storage_error));
			finish();
		}

	}

	@Override
	protected void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter(RefreshDataService.REFRESH_BROADCAST);
		LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, filter);
		mSyncObserverHandle = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE, this);
		if (mSyncActiveDialog != null && !mSyncActiveDialog.isShowing()) {
			mSyncActiveDialog.show();
		}

	}

	protected class myGestureListener extends SimpleOnGestureListener {

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			try {
				if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH)
					return false;
				if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
					finish();
				}
			} catch (Exception e) {
				// nothing
			}
			return false;
		}

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			return false;
		}

		@Override
		public boolean onDown(MotionEvent e) {
			return false;
		}

	}

	// LIFECYCLE
	@Override
	protected void onPause() {
		super.onPause();
		ContentResolver.removeStatusChangeListener(mSyncObserverHandle);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(onNotice);
	}

	protected BroadcastReceiver onNotice = new BroadcastReceiver() {
		public void onReceive(Context ctxt, Intent i) {
			savePosition();
			Intent intent = new Intent(getBaseContext(), RefreshDataActivity.class);
			intent.putExtra(RefreshDataActivity.DIALOG, RefreshDataActivity.ASK_TO_DOWNLOAD);
			startActivity(intent);

		}
	};

	protected void savePosition() {
		// TODO Fill in this method if you want to save the position of the item
		// in the scroll list..
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
			// we are starting a sync
			showDialog(PROGRESS_DIALOG);

		} else {

			// we have just completed a sync
			if (mSyncActiveDialog != null) {
				mSyncActiveDialog.dismiss();
				mSyncActiveDialog = null;
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
		if (mSyncActiveDialog != null && mSyncActiveDialog.isShowing()) {
			mSyncActiveDialog.dismiss();
		}

		mSyncActiveDialog = new ProgressDialog(this);
		mSyncActiveDialog.setIcon(android.R.drawable.ic_dialog_info);
		mSyncActiveDialog.setTitle(getString(R.string.sync_in_progress_title));
		mSyncActiveDialog.setMessage(getString(R.string.sync_in_progress));
		mSyncActiveDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		mSyncActiveDialog.setIndeterminate(true);
		mSyncActiveDialog.setCancelable(false);
		return mSyncActiveDialog;
	}

	protected void showCustomToast(String message) {
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.toast_view, null);

		// set the text in the view
		TextView tv = (TextView) view.findViewById(R.id.message);
		tv.setText(message);

		Toast t = new Toast(this);
		t.setView(view);
		t.setDuration(Toast.LENGTH_SHORT);
		t.setGravity(Gravity.CENTER, 0, 0);
		t.show();
	}

	// BUTTONS
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
		savePosition();

		switch (item.getItemId()) {
		case MENU_USER_PREFERENCES:
			Intent user = new Intent(getApplicationContext(), PreferencesActivity.class);
			user.putExtra(PreferencesActivity.ADMIN_PREFERENCE, false);
			startActivity(user);
			return true;
		case MENU_ADMIN_PREFERENCES:
			Intent admin = new Intent(getApplicationContext(), PreferencesActivity.class);
			admin.putExtra(PreferencesActivity.ADMIN_PREFERENCE, true);
			startActivity(admin);
			return true;
		case MENU_REFRESH:
			Intent id = new Intent(getApplicationContext(), RefreshDataActivity.class);
			id.putExtra(RefreshDataActivity.DIALOG, RefreshDataActivity.DIRECT_TO_DOWNLOAD);
			startActivity(id);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
}
