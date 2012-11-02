package com.alphabetbloc.clinic.ui.admin;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import com.alphabetbloc.clinic.R;

/**
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */

public class DeviceAdminPreference extends BaseAdminActivity {

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// get the intent
		Bundle extra = getIntent().getExtras();
		Boolean toggleMenu = extra.getBoolean(getString(R.string.key_show_settings_menu));
		Boolean toggleLog = extra.getBoolean(getString(R.string.key_enable_activity_log));
		Log.e("ChvSetMenuPreference", "Resetting the menu with toggle= " + toggleMenu);

		// update the prefs
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		settings.edit().putBoolean(getString(R.string.key_show_settings_menu), toggleMenu).commit();
		settings.edit().putBoolean(getString(R.string.key_enable_activity_log), toggleLog).commit();

		finish();
	}

}
