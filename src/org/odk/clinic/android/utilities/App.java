package org.odk.clinic.android.utilities;

import javax.crypto.spec.SecretKeySpec;

import net.sqlcipher.database.SQLiteDatabase;

import org.odk.clinic.android.activities.ClinicLauncherActivity;
import org.odk.clinic.android.database.DbAdapter;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class App extends Application {
	private static App mSingleton = null;
	private static DbAdapter.DatabaseHelper mSqlCipherDbHelper;

	@Override
	public void onCreate() {
		super.onCreate();
		mSingleton = this;
		initializeDb();
	}

	public static App getApp() {
		return mSingleton;
	}
	
	private static void initializeDb() {
		SQLiteDatabase.loadLibs(mSingleton);
		mSqlCipherDbHelper = new DbAdapter.DatabaseHelper(mSingleton);
	}
	
	public static void resetDb() {
		mSqlCipherDbHelper = null;
	}

	public static SQLiteDatabase getDb() {
		if (mSqlCipherDbHelper == null)
			initializeDb();	
		String password = getPassword();
		return mSqlCipherDbHelper.getWritableDatabase(password);
	}

	
	/**
	 * We are using one universal password for this application. This holds for
	 * SqlCipherDb in both Clinic and Collect, as well as for both the keystore
	 * and the trustore for SSL Server and Client Authentication.
	 * 
	 * @return The common password for keystore and truststore.
	 */
	public static String getPassword() {
		// Get the key
		KeyStoreUtil ks = KeyStoreUtil.getInstance();
		byte[] keyBytes = ks.get(ClinicLauncherActivity.SQLCIPHER_KEY_NAME);

		// Get the pwd
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		String encryptedPwd = settings.getString(ClinicLauncherActivity.SQLCIPHER_KEY_NAME, null);

		// Decrypt pwd with key
		String decryptedPwd = null;
		if (encryptedPwd != null && keyBytes != null) {
			SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
			decryptedPwd = Crypto.decrypt(encryptedPwd, key);
		} else {
			Log.w("ClinicApp", "Encryption key not found in keystore: " + ClinicLauncherActivity.SQLCIPHER_KEY_NAME);
		}

		return decryptedPwd;
	}
	
	/*
	 * NB: Never close the DB! just ensure always using one DbHelper... its own
	 * class with db creation, lazy loading, which DbAdapter (DA) would call
	 * and return helper. See: (Mark Murphy) Commonsware and Kevin Galligan
	 * (TouchLabs)
	 * (stackoverflow.com/questions/7211941/never-close-android-sqlite
	 * -connection)
	 * 
	 * public class DatabaseHelper extends SQLiteOpenHelper { private static
	 * DatabaseHelper instance; public static synchronized DatabaseHelper
	 * getHelper(Context context) { if (instance == null) instance = new
	 * DatabaseHelper(context); return instance; } }
	 * 
	 * B/C DA wraps Db and has same constants, I just initialized public class
	 * here. We don't even provide instance of openHelper, just Db itself,
	 * because the only function you want is to read/write, not close().
	 */

}
