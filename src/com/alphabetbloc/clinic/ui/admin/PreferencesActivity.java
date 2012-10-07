package com.alphabetbloc.clinic.ui.admin;

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
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.utilities.App;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class PreferencesActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	private static final String TAG = PreferencesActivity.class.getSimpleName();
	private static final Map<String, String> ADMIN_PREFERENCES = createAdminPreferenceMap();
	private static final Map<String, String> USER_PREFERENCES = createUserPreferenceMap();
	// private static final Map<String, String> PREFERENCE_UNCHECKED_STRINGS =
	// createUnCheckedStringsMap();
	// private static final Map<String, String> PREFERENCE_CHECKED_STRINGS =
	// createCheckedStringsMap();
	public static final String ADMIN_PREFERENCE = "admin_preference";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

		boolean admin = getIntent().getBooleanExtra(ADMIN_PREFERENCE, true);
		String[] keys = null;
		if (admin) {
			addPreferencesFromResource(R.xml.preferences_admin);
			keys = ADMIN_PREFERENCES.keySet().toArray(new String[0]);

		} else {
			addPreferencesFromResource(R.xml.preferences_user);
			keys = USER_PREFERENCES.keySet().toArray(new String[0]);
		}
		if (keys != null) {
			for (int i = 0; i < keys.length; i++) {
				Log.e(TAG, "key i=" + i + " key=" + keys[i]);
				setPreferenceSummary(prefs, keys[i]);
			}
		}

	}

	@Override
	protected void onResume() {
		super.onResume();
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	private void setPreferenceSummary(SharedPreferences prefs, String key) {

		Preference changedPref = findPreference(key);
		if (changedPref instanceof EditTextPreference) {
			String newValue = prefs.getString(key, ADMIN_PREFERENCES.get(key));

			// Min Refresh
			if (key.equalsIgnoreCase(getString(R.string.key_min_refresh_seconds)) && isInteger(newValue)) {
				updateSyncMinimum(prefs, changedPref, key, Integer.valueOf(newValue));

				// Max Refresh
			} else if (key.equalsIgnoreCase(getString(R.string.key_max_refresh_seconds)) && isInteger(newValue)) {
				updateSyncInterval(prefs, changedPref, key, Integer.valueOf(newValue));

				// Program
			} else if (key.equalsIgnoreCase(getString(R.string.key_program)) && isInteger(newValue)) {
				changedPref.setSummary(getString(R.string.pref_program_prefix) + " " + newValue);

				// Saved Search
			} else if (key.equalsIgnoreCase(getString(R.string.key_saved_search)) && isInteger(newValue)) {
				changedPref.setSummary(getString(R.string.pref_savedsearch_prefix) + newValue);

				// Server
			} else if (key.equalsIgnoreCase(getString(R.string.key_server))) {
				changedPref.setSummary(newValue);
			}
			// } else if (changedPref instanceof CheckBoxPreference) {
			// boolean status = prefs.getBoolean(key,
			// Boolean.valueOf(DEFAULT_PREFERENCES_VALUE.get(key)));
			// if (status)
			// changedPref.setSummary(PREFERENCE_CHECKED_STRINGS.get(key));
			// else
			// changedPref.setSummary(PREFERENCE_UNCHECKED_STRINGS.get(key));
		}
	}

	private void updateSyncMinimum(SharedPreferences prefs, Preference changedPref, String key, Integer newValue) {

		String compareKey = getString(R.string.key_max_refresh_seconds);
		Log.e(TAG, "compareKey=" + compareKey + "compareValue=" + ADMIN_PREFERENCES.get(compareKey));
		String compareValueS = prefs.getString(compareKey, ADMIN_PREFERENCES.get(compareKey));
		Integer compareValue = Integer.valueOf(compareValueS);

		if (newValue.equals(compareValue)) {
			// nothing has changed, so do nothing
		} else if (newValue < Integer.valueOf(compareValue)) {
			String timeString = getDuration(newValue);
			changedPref.setSummary(timeString);
		} else {
			showCustomToast(getString(R.string.pref_min_requirement));
			prefs.edit().putString(key, ADMIN_PREFERENCES.get(key)).commit();
		}

	}

	private void updateSyncInterval(SharedPreferences prefs, Preference changedPref, String key, Integer newValue) {

		String compareKey = getString(R.string.key_min_refresh_seconds);
		String compareValueS = prefs.getString(compareKey, ADMIN_PREFERENCES.get(compareKey));
		Integer compareValue = Integer.valueOf(compareValueS);

		if (newValue.equals(compareValue)) {
			// nothing has changed, so do nothing
		} else if (newValue > Integer.valueOf(compareValue)) {
			String timeString = getDuration(newValue);
			changedPref.setSummary(timeString);

			AccountManager accountManager = AccountManager.get(App.getApp());
			Account[] accounts = accountManager.getAccountsByType(getString(R.string.app_account_type));

			if (accounts.length > 0) {
				// ContentResolver.removePeriodicSync(accounts[0],
				// getString(R.string.app_provider_authority), null);
				ContentResolver.addPeriodicSync(accounts[0], getString(R.string.app_provider_authority), new Bundle(), newValue);
			}
		} else {
			showCustomToast(getString(R.string.pref_max_requirement));
			prefs.edit().putString(key, ADMIN_PREFERENCES.get(key)).commit();
		}

	}

	private boolean isInteger(String value) {
		int intValue = 0;
		try {
			intValue = Integer.parseInt(value);
			return true;
		} catch (NumberFormatException nfe) {
			showCustomToast(getString(R.string.pref_not_an_int));
			System.out.println("Could not parse " + nfe);
			return false;
		}
	}

	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		setPreferenceSummary(prefs, key);
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

	private void showCustomToast(String message) {
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.toast_view, null);

		// set the text in the view
		TextView tv = (TextView) view.findViewById(R.id.message);
		tv.setText(message);

		Toast t = new Toast(this);
		t.setView(view);
		t.setDuration(Toast.LENGTH_SHORT);
		t.setGravity(Gravity.CENTER, 0, 0);
		t.show();
	}
}
