package com.alphabetbloc.clinic.services;

import java.io.File;
import java.util.List;

import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.providers.DataModel;
import com.alphabetbloc.clinic.providers.DbProvider;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.EncryptionUtil;
import com.alphabetbloc.clinic.utilities.FileUtils;

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
	public static final String WIPE_CLINIC_DATA = "wipe_clinic_data";
	public static final String WIPE_DATA_REQUESTED = "wipe_data_requested";
	private Context mCollectCtx;
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
		boolean wipeClinic = intent.getBooleanExtra(WIPE_CLINIC_DATA, true);
		Log.w(TAG, "Wiping Data Collect = true and Clinic = " + wipeClinic);

		do {

			// COLLECT
			try {
				// delete most insecure files first:
				File internalInstancesDir = FileUtils.getInternalInstanceDirectory();
				allDeleted = allDeleted & deleteDirectory(internalInstancesDir);

				// get context
				mCollectCtx = App.getApp().createPackageContext("org.odk.collect.android", Context.CONTEXT_RESTRICTED);
				if (mCollectCtx == null)
					allDeleted = false;

				// delete cache
				File collectInternalCache = mCollectCtx.getCacheDir();
				File collectExternalCache = mCollectCtx.getExternalCacheDir();
				allDeleted = allDeleted & deleteDirectory(collectExternalCache);
				allDeleted = allDeleted & deleteDirectory(collectInternalCache);

				// delete instances db
				allDeleted = allDeleted & deleteCollectInstancesDb();

			} catch (Exception e) {
				e.printStackTrace();
			}

			// CLINIC
			if (wipeClinic) {
				try {
					// delete cache
					File clinicInternalCache = getApplicationContext().getCacheDir();
					File clinicExternalCache = getApplicationContext().getExternalCacheDir();
					allDeleted = allDeleted & deleteDirectory(clinicExternalCache);
					allDeleted = allDeleted & deleteDirectory(clinicInternalCache);

					// close the db to prevent errors
					DbProvider.lock();
					
					// delete db keys and pwds
					allDeleted = allDeleted & deleteSqlCipherDbKeys();

					// delete the clinic account & password
					deleteAccounts();

					// delete clinic db
					allDeleted = allDeleted & deleteClinicDb();

					// Delete the external instances dir (which is encrypted)
					String instancePath = FileUtils.getExternalInstancesPath();
					File externalInstanceDir = new File(instancePath);
					allDeleted = allDeleted & deleteDirectory(externalInstanceDir);

					// Delete the local trust and key store
					File trustStore = new File(App.getApp().getFilesDir(), FileUtils.MY_TRUSTSTORE);
					allDeleted = allDeleted & trustStore.delete();
					File keyStore = new File(App.getApp().getFilesDir(), FileUtils.MY_KEYSTORE);
					allDeleted = allDeleted & keyStore.delete();

					// reset helper to null
					App.resetDb();

					// next clinic run, treat it as a fresh setup
					SharedPreferences clinicPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
					clinicPrefs.edit().putBoolean(getString(R.string.key_first_run), true).commit();

					// wait for accounts to be deleted (at most 30s)
					int count = 0;
					while (!removingComplete && count < 30) {
						android.os.SystemClock.sleep(1000);
						count++;
					}

					allDeleted = allDeleted & removedAccount;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			attempts++;

		} while (!allDeleted && (attempts < 4));

		if (allDeleted) {
			Log.v(TAG, "Successfully wiped all sensitive data.  Ending service and canceling alarms.");
			prefs.edit().putBoolean(WIPE_DATA_REQUESTED, false).commit();
			cancelAlarms(WakefulIntentService.WIPE_DATA, getApplicationContext());
		} else {
			Log.w(TAG, "There was an error wiping data. Alarm is set to try again at a later time.");
		}
		
		Intent i = new Intent(WIPE_DATA_COMPLETE);
		sendBroadcast(i);
	}

	private boolean deleteDirectory(File dir) {
		if (!dir.exists())
			return true;

		boolean success = false;

		try {
			// first try
			success = FileUtils.deleteAllFiles(dir.getAbsolutePath());

			// second try (if e.g. memory runs out on recursive looping, try
			// again)
			if (!success) {
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

			}

			if (success)
				Log.i(TAG, "All insecure files have been deleted!");

		} catch (Exception e) {
			e.printStackTrace();
		}

		return success;
	}

	private boolean deleteSqlCipherDbKeys() {
		boolean success = false;

		try {
			// first try
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getApp());
			prefs.edit().putString(EncryptionUtil.SQLCIPHER_KEY_NAME, null).commit();
			prefs.edit().putString(getString(R.string.key_password), null).commit();
			prefs.edit().putString(getString(R.string.key_username), null).commit();
			success = checkSqlCipherPref(prefs);

			// second try
			if (!success) {
				prefs.edit().clear();
				success = checkSqlCipherPref(prefs);
			}
		} catch (Exception e) {
			e.printStackTrace();
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

	private boolean deleteClinicDb() {
		boolean success = false;
		try {
			// first try
			success = this.deleteDatabase(DataModel.DATABASE_NAME);

			// second try
			if (!success) {
				File db = this.getDatabasePath(DataModel.DATABASE_NAME);
				success = db.delete();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return success;
	}

	private boolean deleteCollectInstancesDb() {
		boolean success = false;
		try {
			Cursor c = App.getApp().getContentResolver().query(Uri.parse(InstanceColumns.CONTENT_URI + "/close"), null, null, null, null);
			if (c != null)
				c.close();

			// first try
			success = mCollectCtx.deleteDatabase(InstanceProviderAPI.DATABASE_NAME);

			// second try
			if (!success) {
				File db = mCollectCtx.getDatabasePath(InstanceProviderAPI.DATABASE_NAME);
				success = db.delete();
			}
		} catch (Exception e) {
			e.printStackTrace();
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
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	};

}
