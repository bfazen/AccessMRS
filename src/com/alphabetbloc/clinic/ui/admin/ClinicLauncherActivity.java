package com.alphabetbloc.clinic.ui.admin;

import java.io.File;

import javax.crypto.spec.SecretKeySpec;

import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.providers.DataModel;
import com.alphabetbloc.clinic.providers.DbProvider;
import com.alphabetbloc.clinic.ui.user.DashboardActivity;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.EncryptionUtil;
import com.alphabetbloc.clinic.utilities.KeyStoreUtil;
import com.alphabetbloc.clinic.utilities.UiUtils;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class ClinicLauncherActivity extends Activity {

	public static final String TAG = ClinicLauncherActivity.class.getSimpleName();
	public static final String SQLCIPHER_KEY_NAME = "sqlCipherDbKey";
	public static final String OLD_UNLOCK_ACTION = "android.credentials.UNLOCK";
	public static final String UNLOCK_ACTION = "com.android.credentials.UNLOCK";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.loading);
	}

	@Override
	protected void onResume() {
		super.onResume();
		refreshView();
	}

	private void refreshView() {
		
		if (!isCollectInstalled())
			finish();

		else if (!isClinicSetup())
			setupClinic();

		else if (!isCollectSetup())
			setupCollect();

		else {
			// if we made it here, we are all setup!
			startActivity(new Intent(this, DashboardActivity.class));
			finish();
		}
	}

	// Step 1: check for collect -> fail
	private boolean isCollectInstalled() {

		try {
			getPackageManager().getPackageInfo("org.odk.collect.android", PackageManager.GET_META_DATA);
		} catch (NameNotFoundException e) {
			UiUtils.toastAlert(this, getString(R.string.installation_error), getString(R.string.collect_not_installed));
			return false;
		}

		return true;
	}

	// Step 2: check clinic -> create the setup intent
	private boolean isClinicSetup() {
		boolean setupComplete = true;

		// Shortcut: if db open & have an account, we are setup
		AccountManager accountManager = AccountManager.get(App.getApp());
		Account[] accounts = accountManager.getAccountsByType(App.getApp().getString(R.string.app_account_type));
		String password = EncryptionUtil.getPassword();
		if (getDatabasePath(DataModel.DATABASE_NAME).exists() && accounts.length > 0 && password != null) {
			// make sure Db is open
			DbProvider.openDb();
			if (DbProvider.isOpen())
				return setupComplete;
		}

		return !setupComplete;
	}

	// Step 2: check clinic -> create the setup intent
	private void setupClinic() {

		boolean setupComplete = false;

		setupComplete = setupCredentialStorage();

		if (setupComplete)
			setupComplete = setupFirstInstall();

		if (setupComplete)
			setupComplete = setupDatabases();
		
		if (setupComplete)
			setupComplete = setupAccount();

		return;

	}

	private boolean setupCredentialStorage() {
		// Step 1: check keystore is unlocked -> unlock or setup if necessary
		KeyStoreUtil ks = KeyStoreUtil.getInstance();
		if (ks.state() == KeyStoreUtil.State.UNLOCKED) {
			//already setup
			return true;
		} else {
			try {
				if (Build.VERSION.SDK_INT < 11) {
					startActivity(new Intent(OLD_UNLOCK_ACTION));
				} else {
					startActivity(new Intent(UNLOCK_ACTION));
				}
			} catch (ActivityNotFoundException e) {
				Log.e(TAG, "No UNLOCK activity: " + e.getMessage(), e);
				Toast.makeText(this, "No keystore unlock activity found.", Toast.LENGTH_SHORT).show();
			}
			return false;
		}
	}

	private boolean setupFirstInstall() {
		// Step 2: check previous use -> launch initial setup if first run
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		boolean firstRun = settings.getBoolean(getString(R.string.key_first_run), true);
		if (!firstRun) {
			//already setup
			return true;
		} else {
			Log.e(TAG, "it is considered the first run now!");
			Intent i = new Intent(this, SetupPreferencesActivity.class);
			i.putExtra(SetupPreferencesActivity.SETUP_INTENT, SetupPreferencesActivity.FIRST_RUN);
			startActivity(i);
			return false;
		}

	}

	private boolean setupDatabases() {
		// Step 3: check Db exists, has a key and pwd -> reset clinic if missing
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		File db = App.getApp().getDatabasePath(DataModel.DATABASE_NAME);
		String pwd = settings.getString(SQLCIPHER_KEY_NAME, "");
		SecretKeySpec key = EncryptionUtil.getKey(SQLCIPHER_KEY_NAME);

		if (db != null && db.exists() && !pwd.equals("") && key != null) {
			//already setup
			return true;
		} else {
			// not first run, but have lost Db info! CATASTROPHE... SO RESET.
			Intent i = new Intent(this, SetupPreferencesActivity.class);
			i.putExtra(SetupPreferencesActivity.SETUP_INTENT, SetupPreferencesActivity.RESET_CLINIC);
			startActivity(i);
			return false;
		}

	}

	private boolean setupAccount() {
		// Step 4: Check for sync account -> setup new account if none
		AccountManager accountManager = AccountManager.get(App.getApp());
		Account[] accounts = accountManager.getAccountsByType(App.getApp().getString(R.string.app_account_type));
		if (accounts.length > 0) {
			//already setup
			Log.e(TAG, "there is an account numer=" + accounts.length + "username=" + accounts[0].name);
			return true;
		} else {
			UiUtils.toastAlert(this, getString(R.string.installation_error), getString(R.string.auth_no_account));
			Intent i = new Intent(this, SetupAccountActivity.class);
			i.putExtra(SetupAccountActivity.LAUNCHED_FROM_ACCT_MGR, false);
			startActivity(i);
			return false;
		}
	}

	// Step 3: Open or create the collect db -> reset collect db if fail
	private boolean isCollectSetup() {

		try {
			Cursor c = App.getApp().getContentResolver().query(InstanceColumns.CONTENT_URI, null, null, null, null);
			if(c != null)
				c.close();
		} catch (Exception e) {
			Log.e(TAG, "collect db does not exist?!");
			return false;
		}

		return true;
	}

	private void setupCollect() {
		// Lost key! (clinic reinstalled?) CATASTROPHE... SO RESET COLLECT
		Intent i = new Intent(this, SetupPreferencesActivity.class);
		i.putExtra(SetupPreferencesActivity.SETUP_INTENT, SetupPreferencesActivity.RESET_COLLECT);
		startActivity(i);
	}

}
