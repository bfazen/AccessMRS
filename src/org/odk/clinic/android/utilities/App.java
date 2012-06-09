package org.odk.clinic.android.utilities;

import android.app.Application;

public class App extends Application {
	private static App mSingleton = null;

	@Override
	public void onCreate() {
		super.onCreate();

		mSingleton = this;

	}
	public static App getApp() {
		return mSingleton;
	}
}
