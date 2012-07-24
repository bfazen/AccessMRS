package org.odk.clinic.android.utilities;

import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.database.ClinicAdapter.DatabaseHelper;
import org.odk.clinic.android.database.ODKSQLiteOpenHelper;

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
	
	public static DatabaseHelper getHelper() {
		return openHelper;
	}

	public static SQLiteDatabase getDB() {
		return openHelper.getWritableDatabase();
	}
}
