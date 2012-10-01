package com.alphabetbloc.clinic.ui.admin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.Window;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.utilities.App;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 *
 */
public class PreferencesActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	// private static final String TAG =
	// PreferencesActivity.class.getSimpleName();
	private static final Map<String, String> DEFAULT_PREFERENCES_VALUE = createDefaultValueMap();
	private static final Map<String, String> PREFERENCE_UNCHECKED_STRINGS = createUnCheckedStringsMap();
	private static final Map<String, String> PREFERENCE_CHECKED_STRINGS = createCheckedStringsMap();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.admin_preferences);
	}

	@Override
	protected void onResume() {
		super.onResume();
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {

		Preference changedPref = findPreference(key);
		String newValue = prefs.getString(key, DEFAULT_PREFERENCES_VALUE.get(key));

		if (changedPref instanceof EditTextPreference) {
			if (key.equalsIgnoreCase(App.getApp().getString(R.string.key_max_refresh_seconds)) || key.equalsIgnoreCase(App.getApp().getString(R.string.key_max_refresh_seconds))) {
				int seconds = Integer.valueOf(newValue);
				String time = getDuration(seconds);
				
				changedPref.setSummary(time);
				
			} else {
				
				changedPref.setSummary(newValue);
			}

		} else if (changedPref instanceof CheckBoxPreference) {
			boolean status = Boolean.valueOf(newValue);
			if (status)
				changedPref.setSummary(PREFERENCE_CHECKED_STRINGS.get(key));
			else
				changedPref.setSummary(PREFERENCE_UNCHECKED_STRINGS.get(key));
		}

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
		seconds -= (minutes * (1000 * 60));

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
			sb.append(" Minutes ");
		}
		sb.append(seconds);
		sb.append(" Seconds");

		return (sb.toString());
	}

	private static Map<String, String> createDefaultValueMap() {
		Map<String, String> map = new HashMap<String, String>();
		Context c = App.getApp();
		map.put(c.getString(R.string.key_client_auth), c.getString(R.string.default_use_client_auth));
		map.put(c.getString(R.string.key_enable_activity_log), c.getString(R.string.default_enable_activity_logging));
		map.put(c.getString(R.string.key_max_refresh_seconds), c.getString(R.string.default_max_refresh_seconds));
		map.put(c.getString(R.string.key_min_refresh_seconds), c.getString(R.string.default_min_refresh_seconds));
		map.put(c.getString(R.string.key_program), c.getString(R.string.default_program));
		map.put(c.getString(R.string.key_provider), c.getString(R.string.default_provider));
		map.put(c.getString(R.string.key_saved_search), c.getString(R.string.default_saved_search));
		map.put(c.getString(R.string.key_server), c.getString(R.string.default_server));
		map.put(c.getString(R.string.key_show_settings_menu), c.getString(R.string.default_show_settings_menu));
		map.put(c.getString(R.string.key_use_saved_searches), c.getString(R.string.default_use_saved_searches));
		map.put(c.getString(R.string.key_username), c.getString(R.string.default_username));
		map.put(c.getString(R.string.key_password), c.getString(R.string.default_password));
		return Collections.unmodifiableMap(map);
	}

	private static Map<String, String> createUnCheckedStringsMap() {
		Map<String, String> map = new HashMap<String, String>();
		Context c = App.getApp();
		map.put(c.getString(R.string.key_client_auth), c.getString(R.string.default_use_client_auth));
		map.put(c.getString(R.string.key_enable_activity_log), c.getString(R.string.default_enable_activity_logging));
		map.put(c.getString(R.string.key_show_settings_menu), c.getString(R.string.default_show_settings_menu));
		map.put(c.getString(R.string.key_use_saved_searches), c.getString(R.string.default_use_saved_searches));
		return Collections.unmodifiableMap(map);
	}

	private static Map<String, String> createCheckedStringsMap() {
		Map<String, String> map = new HashMap<String, String>();
		Context c = App.getApp();
		map.put(c.getString(R.string.key_client_auth), c.getString(R.string.default_use_client_auth));
		map.put(c.getString(R.string.key_enable_activity_log), c.getString(R.string.default_enable_activity_logging));
		map.put(c.getString(R.string.key_show_settings_menu), c.getString(R.string.default_show_settings_menu));
		map.put(c.getString(R.string.key_use_saved_searches), c.getString(R.string.default_use_saved_searches));
		return Collections.unmodifiableMap(map);
	}

	@Override
	protected void onPause() {
		super.onPause();
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	}
}
