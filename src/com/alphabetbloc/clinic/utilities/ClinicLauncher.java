package com.alphabetbloc.clinic.utilities;

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
import com.alphabetbloc.clinic.ui.admin.SetupAccountActivity;
import com.alphabetbloc.clinic.ui.admin.SetupPreferencesActivity;
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
public class ClinicLauncher {

	public static final String TAG = ClinicLauncher.class.getSimpleName();
	public static final String SQLCIPHER_KEY_NAME = "sqlCipherDbKey";
	public static final String OLD_UNLOCK_ACTION = "android.credentials.UNLOCK";
	public static final String UNLOCK_ACTION = "com.android.credentials.UNLOCK";
	public static boolean sIsCollectInstalled = true;
	public static Intent sLaunchIntent;
	
	public static boolean isSetupComplete() {
		boolean isSetupComplete = false;

		if (!isCollectInstalled()) {
			Log.e(TAG, "Collect is NOT installed");
		} else if (!isClinicSetup()) {
			setupClinic();
			Log.e(TAG, "Clinic is NOT setup");
		} else if (!isCollectSetup()) {
			setupCollect();
			Log.e(TAG, "Collect is NOT setup");
		} else {
			// if we made it here, we are all setup!
			isSetupComplete = true;
		}

		return isSetupComplete;
	}
	
	public static Intent getLaunchIntent() {

		if (!isCollectInstalled()) {
			sLaunchIntent = null;
			Log.e(TAG, "Collect is NOT installed");
		} else if (!isClinicSetup()) {
			setupClinic();
			Log.e(TAG, "Clinic is NOT setup");
		} else if (!isCollectSetup()) {
			setupCollect();
			Log.e(TAG, "Collect is NOT setup");
		} else {
			// if we made it here, we are all setup!
			sLaunchIntent = new Intent(App.getApp(), DashboardActivity.class);
		}

		return sLaunchIntent;
	}

	// Step 1: check for collect -> fail
	private static boolean isCollectInstalled() {
		Log.e(TAG, "isCollectInstalled");
		try {
			App.getApp().getPackageManager().getPackageInfo("org.odk.collect.android", PackageManager.GET_META_DATA);
		} catch (NameNotFoundException e) {
			UiUtils.toastAlert(App.getApp(), App.getApp().getString(R.string.installation_error), App.getApp().getString(R.string.collect_not_installed));
			return false;
		}

		return true;
	}

	// Step 2: check clinic -> create the setup intent
	private static boolean isClinicSetup() {
		boolean setupComplete = true;
		Log.e(TAG, "isClinicSetup");
		// Shortcut: if db open & have an account, we are setup
		AccountManager accountManager = AccountManager.get(App.getApp());
		Account[] accounts = accountManager.getAccountsByType(App.getApp().getString(R.string.app_account_type));
		String password = EncryptionUtil.getPassword();
		if (App.getApp().getDatabasePath(DataModel.DATABASE_NAME).exists() && accounts.length > 0 && password != null) {
			// make sure Db is open
			DbProvider.openDb();
			if (DbProvider.isOpen())
				return setupComplete;
		}

		return !setupComplete;
	}

	// Step 2: check clinic -> create the setup intent
	private static void setupClinic() {

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

	private static boolean setupCredentialStorage() {
		// Step 1: check keystore is unlocked -> unlock or setup if necessary
		KeyStoreUtil ks = KeyStoreUtil.getInstance();
		if (ks.state() == KeyStoreUtil.State.UNLOCKED) {
			// already setup
			Log.e(TAG, "Credential Storage is Setup");
			return true;

		} else {
			try {
				if (Build.VERSION.SDK_INT < 11) {
					sLaunchIntent = new Intent(OLD_UNLOCK_ACTION);
				} else {
					sLaunchIntent = new Intent(UNLOCK_ACTION);
				}
			} catch (ActivityNotFoundException e) {
				Log.e(TAG, "No UNLOCK activity: " + e.getMessage(), e);
				Toast.makeText(App.getApp(), "No keystore unlock activity found.", Toast.LENGTH_SHORT).show();
			}
			return false;
		}
	}

	private static boolean setupFirstInstall() {
		Log.e(TAG, "setupFirstInstall");
		// Step 2: check previous use -> launch initial setup if first run
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		boolean firstRun = settings.getBoolean(App.getApp().getString(R.string.key_first_run), true);
		if (!firstRun) {
			// already setup
			return true;
		} else {
			Log.e(TAG, "it is considered the first run now!");
			Intent i = new Intent(App.getApp(), SetupPreferencesActivity.class);
			i.putExtra(SetupPreferencesActivity.SETUP_INTENT, SetupPreferencesActivity.FIRST_RUN);
			sLaunchIntent = i;
			return false;
		}

	}

	private static boolean setupDatabases() {
		Log.e(TAG, "setupDatabases");
		// Step 3: check Db exists, has a key and pwd -> reset clinic if missing
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		File db = App.getApp().getDatabasePath(DataModel.DATABASE_NAME);
		String pwd = settings.getString(SQLCIPHER_KEY_NAME, "");
		SecretKeySpec key = EncryptionUtil.getKey(SQLCIPHER_KEY_NAME);

		// TODO! Fix this...
		if (db != null && db.exists() && !pwd.equals("") && key != null) {
			// already setup
			return true;
		} else {
			// not first run, but have lost Db info! CATASTROPHE... SO RESET.
			Intent i = new Intent(App.getApp(), SetupPreferencesActivity.class);
			i.putExtra(SetupPreferencesActivity.SETUP_INTENT, SetupPreferencesActivity.RESET_CLINIC);
			sLaunchIntent = i;
			return false;
		}

	}

	private static boolean setupAccount() {
		Log.e(TAG, "setupAccount");
		// Step 4: Check for sync account -> setup new account if none
		AccountManager accountManager = AccountManager.get(App.getApp());
		Account[] accounts = accountManager.getAccountsByType(App.getApp().getString(R.string.app_account_type));
		if (accounts.length > 0) {
			// already setup
			Log.e(TAG, "there is an account numer=" + accounts.length + "username=" + accounts[0].name);
			return true;
		} else {
			UiUtils.toastAlert(App.getApp(), App.getApp().getString(R.string.installation_error), App.getApp().getString(R.string.auth_no_account));
			Intent i = new Intent(App.getApp(), SetupAccountActivity.class);
			i.putExtra(SetupAccountActivity.LAUNCHED_FROM_ACCT_MGR, false);
			sLaunchIntent = i;
			return false;
		}
	}

	// Step 3: Open or create the collect db -> reset collect db if fail
	private static boolean isCollectSetup() {
		Log.e(TAG, "isCollectSetup");
		try {
			Cursor c = App.getApp().getContentResolver().query(InstanceColumns.CONTENT_URI, null, null, null, null);
			if (c != null)
				c.close();
			else {
				// TODO! try to open collect? / needs to run through install...

			}
		} catch (Exception e) {
			Log.e(TAG, "collect db does not exist?!");
			return false;
		}

		return true;
	}

	private static void setupCollect() {
		Log.e(TAG, "setupCollect");
		// Lost key! (clinic reinstalled?) CATASTROPHE... SO RESET COLLECT
		Intent i = new Intent(App.getApp(), SetupPreferencesActivity.class);
		i.putExtra(SetupPreferencesActivity.SETUP_INTENT, SetupPreferencesActivity.RESET_COLLECT);
		sLaunchIntent = i;
	}

}
