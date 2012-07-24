package org.odk.clinic.android.utilities;

import org.odk.clinic.android.database.ClinicAdapter;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;

public class App extends Application {
	private static App mSingleton = null;
	private static ClinicAdapter.DatabaseHelper openHelper;

	@Override
	public void onCreate() {
		super.onCreate();

		mSingleton = this;
		openHelper = new ClinicAdapter.DatabaseHelper(this);

	}

	public static App getApp() {
		return mSingleton;
	}

	public static SQLiteDatabase getDB() {
		return openHelper.getWritableDatabase();
	}

	// as per Commonsware and Kevin Galligan
	// (http://stackoverflow.com/questions/7211941/never-close-android-sqlite-connection),
	// we are never going to close the Db!!! so we don't even provide an instance of openHelper to anyone!
	// just give them the DB, because the only function you want is to read/write, not close()
	
	// public static DatabaseHelper getHelper() {
	// return openHelper;
	// }
}

/*
 * original suggestion was to do this in its own class as below, currently CA
 * wraps it, so either: 1. make inner class public, initialize only once here,
 * and pass it OR 2. make new class that does db creation etc., lazy loading, CA
 * calls and then return the helper. #2 would entail duplicating all the code to
 * make the table create statements etc. in another class
 * 
 * public class DatabaseHelper extends ODKSQLiteOpenHelper { private static
 * DatabaseHelper instance;
 * 
 * public static synchronized DatabaseHelper getHelper(Context context) { if
 * (instance == null) instance = new DatabaseHelper(context);
 * 
 * return instance; } }
 */
