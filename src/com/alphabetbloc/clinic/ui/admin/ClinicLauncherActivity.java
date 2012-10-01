package com.alphabetbloc.clinic.ui.admin;

import java.io.File;

import javax.crypto.spec.SecretKeySpec;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.providers.DbProvider;
import com.alphabetbloc.clinic.ui.user.DashboardActivity;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.EncryptionUtil;
import com.alphabetbloc.clinic.utilities.KeyStoreUtil;

import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

public class ClinicLauncherActivity extends Activity {

	public static final String TAG = ClinicLauncherActivity.class.getSimpleName();
	public static final String SQLCIPHER_KEY_NAME = "sqlCipherDbKey";
	public static final String OLD_UNLOCK_ACTION = "android.credentials.UNLOCK";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.loading);
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.e(TAG, "resuming ClinicLauncher!");
		if (isClinicSetup()) {
			if (isCollectSetup())
				startActivity(new Intent(this, DashboardActivity.class));
			finish();
		}
	}

	private boolean isClinicSetup() {
		boolean setupComplete = true;

		// if db open, then it must be setup
		if (getDatabasePath(DbProvider.DATABASE_NAME).exists() && DbProvider.isOpen())
			return setupComplete;

		// Step 1: check keystore is unlocked -> unlock or setup if necessary
		KeyStoreUtil ks = KeyStoreUtil.getInstance();
		if (ks.state() != KeyStoreUtil.State.UNLOCKED) {
			startActivity(new Intent(OLD_UNLOCK_ACTION));
			return !setupComplete;
		}

		// Step 2: check first use -> launch initial setup if first run
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		boolean firstRun = settings.getBoolean(getString(R.string.key_first_run), true);
		if (firstRun) {
			Intent i = new Intent(this, InitialSetupActivity.class);
			i.putExtra(InitialSetupActivity.SETUP_INTENT, InitialSetupActivity.FIRST_RUN);
			startActivity(i);
			return !setupComplete;
		}

		// Step 3: Check for sync account -> setup new account if none
		AccountManager accountManager = AccountManager.get(App.getApp());
		Account[] accounts = accountManager.getAccountsByType(App.getApp().getString(R.string.app_account_type));
		if (accounts.length <= 0) {
			Intent i = new Intent(Settings.ACTION_ADD_ACCOUNT);
			i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
			startActivity(i);
			return !setupComplete;
		}

		// check Db exists, it has a key and pwd -> reset clinic if missing
		File db = App.getApp().getDatabasePath(DbProvider.DATABASE_NAME);
		String pwd = settings.getString(SQLCIPHER_KEY_NAME, "");
		SecretKeySpec key = EncryptionUtil.getKey(SQLCIPHER_KEY_NAME);
		if (db == null || !db.exists() || pwd.equals("") || key == null) {
			// not first run, but have lost Db info! CATASTROPHE... SO RESET.
			Intent i = new Intent(this, InitialSetupActivity.class);
			i.putExtra(InitialSetupActivity.SETUP_INTENT, InitialSetupActivity.RESET_CLINIC);
			startActivity(i);
			return !setupComplete;
		}

		return setupComplete;
	}

	private boolean isCollectSetup() {
		boolean setupComplete = true;

		// Step 1: check for collect -> fail
		try {
			getPackageManager().getPackageInfo("org.odk.collect.android", PackageManager.GET_META_DATA);
		} catch (NameNotFoundException e) {
			showCustomToast("Collect Must Be Installed To Use This Software!");
			return !setupComplete;
		}

		// Step 2: Open or create the collect db -> reset collect db if fail
		try {
			Log.e(TAG, "testing collect");
			App.getApp().getContentResolver().query(Uri.parse(InstanceColumns.CONTENT_URI + "/reset"), null, null, null, null);
		} catch (Exception e) {
			// Lost key! (clinic reinstalled?) CATASTROPHE... SO RESET COLLECT
			Intent i = new Intent(this, InitialSetupActivity.class);
			i.putExtra(InitialSetupActivity.SETUP_INTENT, InitialSetupActivity.RESET_COLLECT);
			startActivity(i);
			return !setupComplete;
		}

		return setupComplete;
	}

	private void showCustomToast(String message) {
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

}
