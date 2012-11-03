package com.alphabetbloc.accessmrs.ui.admin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Window;

import com.alphabetbloc.accessmrs.utilities.App;
import com.alphabetbloc.accessmrs.utilities.UiUtils;
import com.alphabetbloc.accessmrs.R;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class PreferencesActivity extends BasePreferenceActivity implements OnSharedPreferenceChangeListener {

	private static final String TAG = PreferencesActivity.class.getSimpleName();
	private static Map<String, String> mCurrentAdminPrefs;
	private static Map<String, String> mCurrentUserPrefs;
	public static final String ADMIN_PREFERENCE = "admin_preference";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		updateCurrentPreferences();
		
		boolean admin = getIntent().getBooleanExtra(ADMIN_PREFERENCE, true);
		String[] keys = null;
		if (admin) {
			addPreferencesFromResource(R.xml.preferences_admin);
			keys = mCurrentAdminPrefs.keySet().toArray(new String[0]);

		} else {
			addPreferencesFromResource(R.xml.preferences_user);
			keys = mCurrentUserPrefs.keySet().toArray(new String[0]);
		}
		
		if (keys != null) {
			for (int i = 0; i < keys.length; i++) {
				Log.v(TAG, "key i=" + i + " key=" + keys[i]);
				updatePreferences(prefs, keys[i]);
			}
		}

	}

	@Override
	protected void onResume() {
		super.onResume();
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	private void updateCurrentPreferences() {
		mCurrentUserPrefs = createUserPreferenceMap();
		mCurrentAdminPrefs = createAdminPreferenceMap();
	}

	private void updatePreferences(SharedPreferences prefs, String key) {

		Preference changedPref = findPreference(key);
		if (changedPref instanceof EditTextPreference) {
			String newValue = prefs.getString(key, mCurrentAdminPrefs.get(key));

			// Min Refresh (Positive Integer with Restrictions)
			if (key.equalsIgnoreCase(getString(R.string.key_min_refresh_seconds)))
				updateSyncMinimum(prefs, changedPref, key, newValue);

			// Max Refresh (Positive Integer with Restrictions)
			else if (key.equalsIgnoreCase(getString(R.string.key_max_refresh_seconds)))
				updateSyncInterval(prefs, changedPref, key, newValue);

			// Program (Positive Integer)
			else if (key.equalsIgnoreCase(getString(R.string.key_program)))
				updateInteger(prefs, changedPref, key, newValue, getString(R.string.pref_program_prefix));

			// Saved Search (Positive Integer)
			else if (key.equalsIgnoreCase(getString(R.string.key_saved_search)))
				updateInteger(prefs, changedPref, key, newValue, getString(R.string.pref_savedsearch_prefix));

			// Server (No Restrictions)
			else if (key.equalsIgnoreCase(getString(R.string.key_server))) {
				changedPref.setSummary(newValue);
			}
		}
	}

	private void updateInteger(SharedPreferences prefs, Preference changedPref, String key, String newValue, String prefix) {

		if (isPositiveInteger(newValue)) {
			changedPref.setSummary(prefix + " " + newValue);
			updateCurrentPreferences();
		} else {
			prefs.edit().putString(key, mCurrentAdminPrefs.get(key)).commit();
			changedPref.setSummary(prefix + " " + mCurrentAdminPrefs.get(key));
		}
	}

	private void updateSyncMinimum(SharedPreferences prefs, Preference changedPref, String key, String newValue) {

		if (!isPositiveInteger(newValue)) {
			prefs.edit().putString(key, mCurrentAdminPrefs.get(key)).commit();
			Log.e(TAG, "New Value was not an Integer, so adding back in value of=" + mCurrentAdminPrefs.get(key));
			return;
		}

		Integer newMinValue = Integer.valueOf(newValue);
		String syncMaxKey = getString(R.string.key_max_refresh_seconds);
		Integer syncMaxValue = Integer.valueOf(prefs.getString(syncMaxKey, mCurrentAdminPrefs.get(syncMaxKey)));
		Log.v(TAG, "compareKey=" + syncMaxKey + "compareValue=" + syncMaxValue);
		Integer oldValue = Integer.valueOf(mCurrentAdminPrefs.get(key));

		if (newValue.equals(oldValue)) {
			// nothing has changed, so do nothing
			changedPref.setSummary(getDuration(oldValue));
		} else if (newMinValue < syncMaxValue) {
			changedPref.setSummary(getDuration(newMinValue));
			prefs.edit().putString(key, String.valueOf(newMinValue)).commit();
			updateCurrentPreferences();
		} else {
			UiUtils.toastAlert(this, getString(R.string.pref_not_allowed), getString(R.string.pref_min_requirement));
			prefs.edit().putString(key, mCurrentAdminPrefs.get(key)).commit();
			changedPref.setSummary(getDuration(oldValue));
		}

	}

	private void updateSyncInterval(SharedPreferences prefs, Preference changedPref, String key, String newValue) {

		if (!isPositiveInteger(newValue)) {
			prefs.edit().putString(key, mCurrentAdminPrefs.get(key)).commit();
			Log.e(TAG, "New Value was not an Integer, so adding back in value of=" + mCurrentAdminPrefs.get(key));
			return;
		}

		Integer newSyncValue = Integer.valueOf(newValue);
		String syncMinKey = getString(R.string.key_min_refresh_seconds);
		Integer syncMinValue = Integer.valueOf(prefs.getString(syncMinKey, mCurrentAdminPrefs.get(syncMinKey)));
		Integer oldValue = Integer.valueOf(mCurrentAdminPrefs.get(key));

		if (newValue.equals(oldValue)) {
			// nothing has changed, so do nothing
			changedPref.setSummary(getDuration(oldValue));
		} else if (newSyncValue > syncMinValue) {
			changedPref.setSummary(getDuration(newSyncValue));

			AccountManager accountManager = AccountManager.get(App.getApp());
			Account[] accounts = accountManager.getAccountsByType(getString(R.string.app_account_type));

			if (accounts.length > 0) {
				// ContentResolver.removePeriodicSync(accounts[0],
				// getString(R.string.app_provider_authority), null);
				ContentResolver.addPeriodicSync(accounts[0], getString(R.string.app_provider_authority), new Bundle(), newSyncValue);
			}
			updateCurrentPreferences();
		} else {
			UiUtils.toastAlert(this, getString(R.string.pref_not_allowed), getString(R.string.pref_max_requirement));
			prefs.edit().putString(key, mCurrentAdminPrefs.get(key)).commit();
			changedPref.setSummary(getDuration(oldValue));
		}

	}

	private boolean isPositiveInteger(String value) {

		boolean isInt = false;
		try {
			Integer intValue = Integer.parseInt(value);
			if (intValue >= 0)
				isInt = true;
		} catch (NumberFormatException nfe) {
			UiUtils.toastAlert(this, getString(R.string.pref_not_allowed), getString(R.string.pref_not_an_int));
			System.out.println("Could not parse " + nfe);
		}

		return isInt;
	}

	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		updatePreferences(prefs, key);
	}

	/**
	 * Convert a second duration to a string format
	 * 
	 * @param millis
	 *            A duration to convert to a string form
	 * @return A string of the form "X Days Y Hours Z Minutes A Seconds".
	 */
	public static String getDuration(long seconds) {
		if (seconds < 0) {
			// throw new
			// IllegalArgumentException("Duration must be greater than zero!");
			return "requested time is negative";
		}

		int years = (int) (seconds / (60 * 60 * 24 * 365.25));
		seconds -= (years * (60 * 60 * 24 * 365.25));
		int days = (int) ((seconds / (60 * 60 * 24)) % 365.25);
		seconds -= (days * (60 * 60 * 24));
		int hours = (int) ((seconds / (60 * 60)) % 24);
		seconds -= (hours * (60 * 60));
		int minutes = (int) ((seconds / 60) % 60);
		seconds -= (minutes * (60));

		StringBuilder sb = new StringBuilder(64);
		if (years > 0) {
			sb.append(years);
			sb.append(" Years ");
		}
		if (days > 0 || years > 0) {
			sb.append(days);
			sb.append(" Days ");
		}
		if (hours > 0 || days > 0 || years > 0) {
			sb.append(hours);
			sb.append(" Hours ");
		}
		if (minutes > 0 || hours > 0 || days > 0 || years > 0) {
			sb.append(minutes);
			sb.append(" Min ");
		}
		sb.append(seconds);
		sb.append(" Sec");

		return (sb.toString());
	}

	private static Map<String, String> createAdminPreferenceMap() {
		Map<String, String> map = new HashMap<String, String>();
		Context c = App.getApp();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);

		// Strings with current values:
		map.put(c.getString(R.string.key_max_refresh_seconds), prefs.getString(c.getString(R.string.key_max_refresh_seconds), c.getString(R.string.default_max_refresh_seconds)));
		map.put(c.getString(R.string.key_min_refresh_seconds), prefs.getString(c.getString(R.string.key_min_refresh_seconds), c.getString(R.string.default_min_refresh_seconds)));
		map.put(c.getString(R.string.key_program), prefs.getString(c.getString(R.string.key_program), c.getString(R.string.default_program)));
		map.put(c.getString(R.string.key_provider), prefs.getString(c.getString(R.string.key_provider), c.getString(R.string.default_provider)));
		map.put(c.getString(R.string.key_server), prefs.getString(c.getString(R.string.key_server), c.getString(R.string.default_server)));
		map.put(c.getString(R.string.key_username), prefs.getString(c.getString(R.string.key_username), c.getString(R.string.default_username)));
		map.put(c.getString(R.string.key_password), prefs.getString(c.getString(R.string.key_password), c.getString(R.string.default_password)));

		// Booleans with default values
		map.put(c.getString(R.string.key_saved_search), c.getString(R.string.default_saved_search));
		map.put(c.getString(R.string.key_client_auth), c.getString(R.string.default_use_client_auth));
		map.put(c.getString(R.string.key_enable_activity_log), c.getString(R.string.default_enable_activity_logging));
		map.put(c.getString(R.string.key_show_settings_menu), c.getString(R.string.default_show_settings_menu));
		map.put(c.getString(R.string.key_use_saved_searches), c.getString(R.string.default_use_saved_searches));
		map.put(c.getString(R.string.key_show_form_prompt), c.getString(R.string.default_show_form_prompt));
		return Collections.unmodifiableMap(map);
	}

	private static Map<String, String> createUserPreferenceMap() {
		Map<String, String> map = new HashMap<String, String>();
		Context c = App.getApp();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
		map.put(c.getString(R.string.key_provider), prefs.getString(c.getString(R.string.key_provider), c.getString(R.string.default_provider)));
		map.put(c.getString(R.string.key_program), prefs.getString(c.getString(R.string.key_program), c.getString(R.string.default_program)));
		map.put(c.getString(R.string.key_saved_search), prefs.getString(c.getString(R.string.key_saved_search), c.getString(R.string.default_saved_search)));
		map.put(c.getString(R.string.key_use_saved_searches), c.getString(R.string.default_use_saved_searches));
		map.put(c.getString(R.string.key_show_form_prompt), c.getString(R.string.default_show_form_prompt));
		return Collections.unmodifiableMap(map);
	}

	@Override
	protected void onPause() {
		super.onPause();
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	}

}
