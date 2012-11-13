package com.alphabetbloc.accessmrs.utilities;

import java.io.File;

import javax.crypto.spec.SecretKeySpec;


import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.alphabetbloc.accessforms.provider.InstanceProviderAPI.InstanceColumns;
import com.alphabetbloc.accessmrs.providers.DataModel;
import com.alphabetbloc.accessmrs.providers.DbProvider;
import com.alphabetbloc.accessmrs.ui.admin.SetupAccountActivity;
import com.alphabetbloc.accessmrs.ui.admin.SetupPreferencesActivity;
import com.alphabetbloc.accessmrs.ui.user.DashboardActivity;
import com.alphabetbloc.accessmrs.R;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class LauncherUtil {

	public static final String TAG = LauncherUtil.class.getSimpleName();
	public static final String OLD_UNLOCK_ACTION = "android.credentials.UNLOCK";
	public static final String UNLOCK_ACTION = "com.android.credentials.UNLOCK";
	public static final String ACCESS_FORMS_NOT_INSTALLED = "com.alphabetbloc.accessforms.access_forms_not_installed";
	public static Intent sLaunchIntent;
	
	public static boolean isSetupComplete() {
		boolean isSetupComplete = false;

		if (!isAccessFormsInstalled()) {
			Log.w(TAG, "AccessForms is NOT installed");
		} else if (!isAccessMrsSetup()) {
			setupAccessMrs();
			Log.w(TAG, "AccessMRS is NOT setup");
		} else if (!isAccessFormsSetup()) {
			setupAccessForms();
			Log.w(TAG, "AccessForms is NOT setup");
		} else {
			// if we made it here, we are all setup!
			isSetupComplete = true;
		}

		return isSetupComplete;
	}
	
	public static Intent getLaunchIntent() {

		if (!isAccessFormsInstalled()) {
			Log.w(TAG, "AccessForms is NOT installed");
		} else if (!isAccessMrsSetup()) {
			setupAccessMrs();
			Log.w(TAG, "AccessMRS is NOT setup");
		} else if (!isAccessFormsSetup()) {
			setupAccessForms();
			Log.w(TAG, "AccessForms is NOT setup");
		} else {
			// if we made it here, we are all setup!
			sLaunchIntent = new Intent(App.getApp(), DashboardActivity.class);
		}

		return sLaunchIntent;
	}

	// Step 1: check for AccessForms -> fail
	private static boolean isAccessFormsInstalled() {
		try {
			App.getApp().getPackageManager().getPackageInfo("com.alphabetbloc.accessforms", PackageManager.GET_META_DATA);
		} catch (NameNotFoundException e) {
			sLaunchIntent = new Intent(ACCESS_FORMS_NOT_INSTALLED);
			return false;
		}

		return true;
	}

	// Step 2: check AccessMRS -> create the setup intent
	private static boolean isAccessMrsSetup() {
		boolean setupComplete = true;
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

	// Step 2: check AccessMRS -> create the setup intent
	private static void setupAccessMrs() {

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
			return true;

		} else {
			try {
				if (Build.VERSION.SDK_INT < 11) {
					sLaunchIntent = new Intent(OLD_UNLOCK_ACTION);
				} else {
					sLaunchIntent = new Intent(UNLOCK_ACTION);
				}
			} catch (ActivityNotFoundException e) {
				Log.e(TAG, "No UNLOCK activity Found: " + e.getMessage(), e);
				Toast.makeText(App.getApp(), "No keystore unlock activity found.", Toast.LENGTH_SHORT).show();
			}
			return false;
		}
	}

	private static boolean setupFirstInstall() {
		// Step 2: check previous use -> launch initial setup if first run
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		boolean firstRun = settings.getBoolean(App.getApp().getString(R.string.key_first_run), true);
		if (!firstRun) {
			// already setup
			return true;
		} else {
			Log.w(TAG, "Running AccessMRS for the first time.");
			Intent i = new Intent(App.getApp(), SetupPreferencesActivity.class);
			i.putExtra(SetupPreferencesActivity.SETUP_INTENT, SetupPreferencesActivity.FIRST_RUN);
			sLaunchIntent = i;
			return false;
		}

	}

	private static boolean setupDatabases() {
		// Step 3: check Db exists, has a key and pwd -> reset AccessMrs if missing
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		File db = App.getApp().getDatabasePath(DataModel.DATABASE_NAME);
		String pwd = settings.getString(EncryptionUtil.SQLCIPHER_KEY_NAME, "");
		SecretKeySpec key = EncryptionUtil.getKey(EncryptionUtil.SQLCIPHER_KEY_NAME);

		// TODO! Fix this...
		if (db != null && db.exists() && !pwd.equals("") && key != null) {
			// already setup
			return true;
		} else {
			// not first run, but have lost Db info! CATASTROPHE... SO RESET.
			Intent i = new Intent(App.getApp(), SetupPreferencesActivity.class);
			i.putExtra(SetupPreferencesActivity.SETUP_INTENT, SetupPreferencesActivity.RESET_ACCESS_MRS);
			sLaunchIntent = i;
			return false;
		}

	}

	private static boolean setupAccount() {
		// Step 4: Check for sync account -> setup new account if none
		AccountManager accountManager = AccountManager.get(App.getApp());
		Account[] accounts = accountManager.getAccountsByType(App.getApp().getString(R.string.app_account_type));
		if (accounts.length > 0) {
			// already setup
			if (App.DEBUG) Log.v(TAG, "there is an account numer=" + accounts.length + "username=" + accounts[0].name);
			return true;
		} else {
			Intent i = new Intent(App.getApp(), SetupAccountActivity.class);
			i.putExtra(SetupAccountActivity.LAUNCHED_FROM_ACCT_MGR, false);
			sLaunchIntent = i;
			return false;
		}
	}

	// Step 3: Open or create the AccessForms db -> reset ACCESS_FORMS db if fail
	private static boolean isAccessFormsSetup() {
		try {
			Cursor c = App.getApp().getContentResolver().query(InstanceColumns.CONTENT_URI, null, null, null, null);
			if (c != null)
				c.close();
			else {
				// TODO! try to open AccessForms? / needs to run through install...

			}
		} catch (Exception e) {
			Log.w(TAG, "AccessForms db does not exist!");
			return false;
		}

		return true;
	}

	private static void setupAccessForms() {
		// Lost key! (AccessMrs reinstalled?) CATASTROPHE... SO RESET AccessForms
		Intent i = new Intent(App.getApp(), SetupPreferencesActivity.class);
		i.putExtra(SetupPreferencesActivity.SETUP_INTENT, SetupPreferencesActivity.RESET_ACCESS_FORMS);
		sLaunchIntent = i;
	}

}
