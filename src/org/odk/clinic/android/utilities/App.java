package org.odk.clinic.android.utilities;

import org.odk.clinic.android.database.ClinicAdapter;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;

/*
 * NB: Never close the DB! just ensure always using one DbHelper... its own
 * class with db creation, lazy loading, which ClinicAdapter (CA) would call and
 * return helper. See: (Mark Murphy) Commonsware and Kevin Galligan (TouchLabs)
 * (stackoverflow.com/questions/7211941/never-close-android-sqlite-connection)
 * 
 * public class DatabaseHelper extends SQLiteOpenHelper { private static
 * DatabaseHelper instance; public static synchronized DatabaseHelper
 * getHelper(Context context) { if (instance == null) instance = new
 * DatabaseHelper(context); return instance; } }
 * 
 * B/C CA wraps Db and has same constants, I just initialized public class here.
 * We don't even provide instance of openHelper, just Db itself, because the
 * only function you want is to read/write, not close().
 */

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

}
