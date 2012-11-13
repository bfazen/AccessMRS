package com.alphabetbloc.accessmrs.services;

import java.io.File;
import java.util.List;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import com.alphabetbloc.accessforms.provider.InstanceProviderAPI;
import com.alphabetbloc.accessforms.provider.InstanceProviderAPI.InstanceColumns;
import com.alphabetbloc.accessmrs.providers.DataModel;
import com.alphabetbloc.accessmrs.providers.DbProvider;
import com.alphabetbloc.accessmrs.utilities.App;
import com.alphabetbloc.accessmrs.utilities.EncryptionUtil;
import com.alphabetbloc.accessmrs.utilities.FileUtils;
import com.alphabetbloc.accessmrs.utilities.UiUtils;
import com.alphabetbloc.accessmrs.R;

/**
 * @author Louis.Fazen@gmail.com
 * 
 *         IntentService is called by Alarm Listener at periodic intervals.
 *         Decides whether or not to start ongoing service to monitor
 *         SignalStrength and download clients. After decision, this
 *         IntentService finishes. Holds wakelock.
 */

public class WipeDataService extends WakefulIntentService {

	private static final String TAG = WipeDataService.class.getSimpleName();
	public static final String WIPE_DATA_COMPLETE = "com.alphabetbloc.android.settings.WIPE_DATA_SERVICE_COMPLETE";
	public static final String WIPE_ACCESS_MRS_DATA = "wipe_access_mrs_data";
	public static final String WIPE_DATA_REQUESTED = "wipe_data_requested";
	private Context mAccessFormsCtx;
	private final Handler myHandler = new Handler();
	private AccountManagerFuture<Boolean> myFuture = null;
	private boolean removedAccount = false;
	private boolean removingComplete = false;

	public WipeDataService() {
		super("AppService");
	}

	@Override
	protected void doWakefulWork(Intent intent) {
		SharedPreferences prefs = getSharedPreferences(WakefulIntentService.NAME, 0);
		prefs.edit().putBoolean(WIPE_DATA_REQUESTED, true).commit();

		boolean allDeleted = true;
		int attempts = 0;
		boolean wipeAccessMrs = intent.getBooleanExtra(WIPE_ACCESS_MRS_DATA, true);
		boolean wipeAccessForms = isAccessFormsInstalled();
		Log.w(TAG, "Wiping Data! \n\t AccessForms = " + wipeAccessForms + "\n\t AccessMRS = " + wipeAccessMrs);

		do {

			// ACCESS-FORMS
			if (wipeAccessForms) {
				try {
					// delete most insecure files first:
					File internalInstancesDir = FileUtils.getInternalInstanceDirectory();
					allDeleted = allDeleted & deleteDirectory(internalInstancesDir);

					// get context
					mAccessFormsCtx = App.getApp().createPackageContext("com.alphabetbloc.accessforms", Context.CONTEXT_RESTRICTED);
					if (mAccessFormsCtx == null)
						allDeleted = false;

					// delete cache
					File AccessFormsInternalCache = mAccessFormsCtx.getCacheDir();
					File AccessFormsExternalCache = mAccessFormsCtx.getExternalCacheDir();
					allDeleted = allDeleted & deleteDirectory(AccessFormsExternalCache);
					allDeleted = allDeleted & deleteDirectory(AccessFormsInternalCache);

					// delete instances db
					allDeleted = allDeleted & deleteAccessFormsInstancesDb();

				} catch (Exception e) {
					allDeleted = false;
					Log.e(TAG, "Error wiping AccessForms data!");
					e.printStackTrace();
				}
			}

			// ACCESS-MRS
			if (wipeAccessMrs) {
				try {
					// delete cache
					File accessMrsInternalCache = getApplicationContext().getCacheDir();
					File accessMrsExternalCache = getApplicationContext().getExternalCacheDir();
					allDeleted = allDeleted & deleteDirectory(accessMrsExternalCache);
					allDeleted = allDeleted & deleteDirectory(accessMrsInternalCache);

					// close the db to prevent errors
					closeClinicDb();

					// delete db keys and pwds
					allDeleted = allDeleted & deleteSqlCipherDbKeys();

					// delete the accessMrs account & password
					deleteAccounts();

					// delete accessMrs db
					allDeleted = allDeleted & deleteAccessMrsDb();

					// Delete the external instances dir (which is encrypted)
					String instancePath = FileUtils.getExternalInstancesPath();
					File externalInstanceDir = new File(instancePath);
					allDeleted = allDeleted & deleteDirectory(externalInstanceDir);

					// Delete the local trust and key store
					File trustStore = new File(App.getApp().getFilesDir(), FileUtils.MY_TRUSTSTORE);
					allDeleted = allDeleted & FileUtils.deleteFile(trustStore.getAbsolutePath());
					File keyStore = new File(App.getApp().getFilesDir(), FileUtils.MY_KEYSTORE);
					allDeleted = allDeleted & FileUtils.deleteFile(keyStore.getAbsolutePath());

					// reset helper to null
					App.resetDb();

					// next AccessMrs run, treat it as a fresh setup
					SharedPreferences accessMrsPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
					accessMrsPrefs.edit().putBoolean(getString(R.string.key_first_run), true).commit();

					// wait for accounts to be deleted (at most 30s)
					int count = 0;
					while (!removingComplete && count < 30) {
						android.os.SystemClock.sleep(1000);
						count++;
					}

					allDeleted = allDeleted & removedAccount;
				} catch (Exception e) {
					allDeleted = false;
					Log.e(TAG, "Error wiping AccessMRS data!");
					e.printStackTrace();
				}
			}
			attempts++;
			if(!allDeleted)
				Log.e(TAG, "Error wiping data! Have now tried to wipe data on " + attempts + " attempts.");
		} while (!allDeleted && (attempts < 4));

		if (allDeleted) {
			if (App.DEBUG)
				Log.v(TAG, "Successfully wiped all sensitive data.  Ending service and canceling alarms.");
			UiUtils.toastAlert("Databases Reset", "All Databases have been successfully reset to default values");
			prefs.edit().putBoolean(WIPE_DATA_REQUESTED, false).commit();
			cancelAlarms(WakefulIntentService.WIPE_DATA, getApplicationContext());
		} else {
			Log.w(TAG, "There was an error wiping data. Alarm is set to try again at a later time.");
		}

		Intent i = new Intent(WIPE_DATA_COMPLETE);
		sendBroadcast(i);
	}

	private void closeClinicDb() {
		try {
			DbProvider.lock();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static boolean isAccessFormsInstalled() {
		try {
			App.getApp().getPackageManager().getPackageInfo("com.alphabetbloc.accessforms", PackageManager.GET_META_DATA);
		} catch (NameNotFoundException e) {
			return false;
		}
		return true;
	}

	private boolean deleteDirectory(File dir) {
		if (!dir.exists())
			return true;

		boolean success = false;

		// first try
		try {
			success = FileUtils.deleteAllFiles(dir.getAbsolutePath());

		} catch (Exception e) {
			e.printStackTrace();
		}

		if (success)
			return success;

		// second try (if e.g. memory runs out on recursive looping, try
		// again)
		try {
			List<File> allFiles = FileUtils.findAllFiles(dir.getAbsolutePath());
			if (allFiles.isEmpty())
				return true;

			for (File f : allFiles) {
				if (f.exists()) {
					try {
						success = success & FileUtils.deleteFile(f.getAbsolutePath());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return success;
	}

	private boolean deleteSqlCipherDbKeys() {
		boolean success = false;
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getApp());

		try {
			// first try
			prefs.edit().putString(EncryptionUtil.SQLCIPHER_KEY_NAME, null).commit();
			prefs.edit().putString(getString(R.string.key_password), null).commit();
			prefs.edit().putString(getString(R.string.key_username), null).commit();
			success = checkSqlCipherPref(prefs);

		} catch (Exception e) {
			e.printStackTrace();
		}

		// second try
		if (!success) {
			try {
				prefs.edit().clear();
				success = checkSqlCipherPref(prefs);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return success;
	}

	private boolean checkSqlCipherPref(SharedPreferences prefs) {
		String testKey = prefs.getString(EncryptionUtil.SQLCIPHER_KEY_NAME, null);
		String testPwd = prefs.getString(getString(R.string.key_password), null);
		String testUser = prefs.getString(getString(R.string.key_username), null);
		if (testKey == null && testPwd == null && testUser == null)
			return true;
		else
			return false;
	}

	private boolean deleteAccessMrsDb() {
		boolean success = false;
		try {
			// first try
			success = this.deleteDatabase(DataModel.DATABASE_NAME);

		} catch (Exception e) {
			e.printStackTrace();
		}

		// second try
		if (!success) {
			try {
				File db = this.getDatabasePath(DataModel.DATABASE_NAME);
				if (!db.exists())
					success = true;
				else
					success = db.delete();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return success;
	}

	private boolean deleteAccessFormsInstancesDb() {
		boolean success = false;
		try {
			Cursor c = App.getApp().getContentResolver().query(Uri.parse(InstanceColumns.CONTENT_URI + "/close"), null, null, null, null);
			if (c != null)
				c.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			Cursor c = App.getApp().getContentResolver().query(Uri.parse(InstanceColumns.CONTENT_URI + "/close"), null, null, null, null);
			if (c != null)
				c.close();

			// first try
			success = mAccessFormsCtx.deleteDatabase(InstanceProviderAPI.DATABASE_NAME);

		} catch (Exception e) {
			e.printStackTrace();
		}

		// second try
		if (!success) {
			try {
				File db = mAccessFormsCtx.getDatabasePath(InstanceProviderAPI.DATABASE_NAME);
				success = db.delete();
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

		return success;
	}

	private void deleteAccounts() {
		AccountManager am = AccountManager.get(this);
		Account[] accounts = am.getAccountsByType(getString(R.string.app_account_type));

		if (accounts.length > 0) {
			for (Account a : accounts) {
				myFuture = am.removeAccount(a, myCallback, myHandler);
			}
		} else {
			if(App.DEBUG) Log.v(TAG, "No current AccessMRS user accounts.");
			removedAccount = true;
		}
	}

	// STEP 4:
	private AccountManagerCallback<Boolean> myCallback = new AccountManagerCallback<Boolean>() {
		@Override
		public void run(final AccountManagerFuture<Boolean> amf) {
			if (amf != null) {
				try {
					removedAccount = myFuture.getResult();
					removingComplete = true;
					if(App.DEBUG) Log.v(TAG, "Successfully wiped the AccessMRS user account!");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	};

}
