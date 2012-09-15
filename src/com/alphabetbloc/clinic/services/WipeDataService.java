package com.alphabetbloc.clinic.services;

import java.io.File;
import java.util.List;

import org.odk.clinic.android.activities.ClinicLauncherActivity;
import org.odk.clinic.android.database.DbAdapter;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.collect.android.provider.InstanceProviderAPI;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;

/**
 * @author Louis.Fazen@gmail.com
 * 
 *         IntentService is called by Alarm Listener at periodic intervals.
 *         Decides whether or not to start ongoing service to monitor
 *         SignalStrength and download clients. After decision, this
 *         IntentService finishes. Holds wakelock.
 */

public class WipeDataService extends WakefulIntentService {

	private static final String TAG = "WipeDataService";
	public static final String WIPE_DATA_COMPLETE = "com.alphabetbloc.android.settings.WIPE_DATA_SERVICE_COMPLETE";
	private Context mCollectCtx;

	public WipeDataService() {
		super("AppService");
	}

	@Override
	protected void doWakefulWork(Intent intent) {
		boolean allDeleted = true;
		int attempts = 0;

		do {

			// COLLECT
			try {
				// most insecure files:
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

				// delete db keys
				SharedPreferences collectPrefs = mCollectCtx.getSharedPreferences(ClinicLauncherActivity.SQLCIPHER_PREFS_NAME, MODE_PRIVATE);
				allDeleted = allDeleted & deleteSqlCipherDbKeys(collectPrefs);

				// delete instances db
				allDeleted = allDeleted & deleteCollectInstancesDb();

			} catch (Exception e) {
				e.printStackTrace();
			}

			// CLINIC
			try {
				// delete cache
				File clinicInternalCache = getApplicationContext().getCacheDir();
				File clinicExternalCache = getApplicationContext().getExternalCacheDir();
				allDeleted = allDeleted & deleteDirectory(clinicExternalCache);
				allDeleted = allDeleted & deleteDirectory(clinicInternalCache);

				// delete db keys
				SharedPreferences clinicPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
				allDeleted = allDeleted & deleteSqlCipherDbKeys(clinicPrefs);

				// delete clinic db
				allDeleted = allDeleted & deleteClinicDb();

				// Finally
				// Delete the entire external instances dir (which is encrypted)
				String instancePath = FileUtils.getExternalInstancesPath();
				File externalInstanceDir = new File(instancePath);
				allDeleted = allDeleted & deleteDirectory(externalInstanceDir);

			} catch (Exception e) {
				e.printStackTrace();
			}

			attempts++;

		} while (!allDeleted && (attempts < 4));

		if (allDeleted) {
			Intent i = new Intent(WIPE_DATA_COMPLETE);
			sendBroadcast(i);
			cancelAlarms(WakefulIntentService.WIPE_DATA, getApplicationContext());
		}
	}

	private boolean deleteDirectory(File dir) {

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
				Log.e(TAG, "All insecure files have been deleted!");

		} catch (Exception e) {
			e.printStackTrace();
		}

		return success;
	}

	private boolean deleteSqlCipherDbKeys(SharedPreferences prefs) {
		boolean success = false;

		try {
			// first try
			prefs.edit().putString(ClinicLauncherActivity.SQLCIPHER_KEY_NAME, null).commit();
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
		String test = prefs.getString(ClinicLauncherActivity.SQLCIPHER_KEY_NAME, null);
		if (test == null)
			return true;
		else
			return false;
	}

	private boolean deleteClinicDb() {
		boolean success = false;
		try {
			// first try
			success = this.deleteDatabase(DbAdapter.DATABASE_NAME);

			// second try
			if (!success) {
				File db = this.getDatabasePath(DbAdapter.DATABASE_NAME);
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

}
