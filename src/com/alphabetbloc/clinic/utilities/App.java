package com.alphabetbloc.clinic.utilities;

import net.sqlcipher.database.SQLiteDatabase;
import android.app.Application;

import com.alphabetbloc.clinic.data.DbAdapter;

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
		String password = EncryptionUtil.getPassword();
		return mSqlCipherDbHelper.getWritableDatabase(password);
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