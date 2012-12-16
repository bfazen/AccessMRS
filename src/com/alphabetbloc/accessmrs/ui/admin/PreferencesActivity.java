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
	// intent
	public static final String ADMIN_PREFERENCE = "admin_preference";
	// Preference Types
	private static final Map<String, Integer> PREFERENCE_TYPE = getPreferenceTypeMap();
	private static final int POSITIVE_INTEGER = 1;
	private static final int BOOLEAN = 2;
	private static final int FREE_TEXT = 3;
	private static final int SYNC_MIN_REFRESH_TIME = 4;
	private static final int SYNC_INTERVAL_TIME = 5;
	// Preferences
	private static Map<String, String> mCurrentAdminPrefs;
	private static Map<String, String> mCurrentUserPrefs;
	private static boolean mValueError;
	private static boolean mShowToast = false;

	/**
	 * Public method for updating the preference. Uses the same methods as
	 * default PreferenceActivity with onPreferenceChangedListener, so the error
	 * checking happens after the preference has been changed, and changes are
	 * reverted.
	 * 
	 * @param key
	 *            The preference key
	 * @param value
	 *            The new value for the preference
	 */
	public static void updatedPreference(String key, String value) {

		setCurrentPreferences();
		
		// Search for matching key
		boolean matchingKey = false;
		String[] keys = mCurrentAdminPrefs.keySet().toArray(new String[0]);
		if (keys != null) {
			for (int i = 0; i < keys.length; i++) {
				if (key.equalsIgnoreCase(keys[i])) {
					key = keys[i];
					matchingKey = true;
					break;
				}
			}
		}

		if (!matchingKey) {
			if (App.DEBUG) Log.v(TAG, "Could not find a matching key to " + key);
			return;
		}

		// Get the Current Preference Value
		String originalValue = mCurrentAdminPrefs.get(key);

		// Update the Preference
		boolean foundKey = true;
		mValueError = false;
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		switch (PREFERENCE_TYPE.get(key)) {
		case BOOLEAN:
			if (isBooleanValue(value))
				prefs.edit().putBoolean(key, Boolean.parseBoolean(value)).commit();
			else
				mValueError = true;
			break;
		case POSITIVE_INTEGER:
		case SYNC_INTERVAL_TIME:
		case SYNC_MIN_REFRESH_TIME:
			if (isPositiveInteger(value))
				prefs.edit().putString(key, value).commit();
			else
				mValueError = true;
			break;
		case FREE_TEXT:
			prefs.edit().putString(key, value).commit();
			break;
		default:
			foundKey = false;
			break;
		}

		// Revert the Preference if it does not follow restrictions
		verifyUpdatedPreference(prefs, null, key);

		// Log the Update Attempt
		if (foundKey) {
			String currentValue = mCurrentAdminPrefs.get(key);
			if (currentValue != originalValue)
				if (App.DEBUG) Log.v(TAG, "Updated Preference " + key + " to a new value");
			else if (mValueError)
				if (App.DEBUG) Log.v(TAG, "Preference Not Updated: Value not allowed for Preference " + key);
			else
				if (App.DEBUG) Log.v(TAG, "No Change Required for Current Preference " + key);
		} else {
			if (App.DEBUG) Log.v(TAG, "Preference " + key + " was not found, and could not be updated.");
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);
		setCurrentPreferences();

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
				if (App.DEBUG) Log.v(TAG, "key i=" + i + " key=" + keys[i]);
				Preference changedPref = findPreference(keys[i]);
				setSummary(changedPref, keys[i]);
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		mShowToast = true;
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	private static void setCurrentPreferences() {
		mCurrentUserPrefs = createUserPreferenceMap();
		mCurrentAdminPrefs = createAdminPreferenceMap();
	}

	private static void verifyUpdatedPreference(SharedPreferences prefs, Preference changedPref, String key) {

		switch (PREFERENCE_TYPE.get(key)) {
		case POSITIVE_INTEGER:
			if (isPositiveInteger(prefs.getString(key, mCurrentAdminPrefs.get(key))))
				setCurrentPreferences();
			else
				prefs.edit().putString(key, mCurrentAdminPrefs.get(key)).commit();
			break;
		case SYNC_MIN_REFRESH_TIME:
			updateSyncMinimum(prefs, key, prefs.getString(key, mCurrentAdminPrefs.get(key)));
			break;
		case SYNC_INTERVAL_TIME:
			updateSyncInterval(prefs, key, prefs.getString(key, mCurrentAdminPrefs.get(key)));
			break;

		// no need to evaluate these:
		case BOOLEAN:
		case FREE_TEXT:
		default:
			setCurrentPreferences();
			break;
		}

		setSummary(changedPref, key);
	}

	private static void setSummary(Preference changedPref, String key) {

		if (changedPref != null && changedPref instanceof EditTextPreference) {
			EditTextPreference editText = (EditTextPreference) changedPref;
			editText.setText(mCurrentAdminPrefs.get(key));

			String summary = null;
			if (key.equalsIgnoreCase(App.getApp().getString(R.string.key_program)))
				summary = App.getApp().getString(R.string.pref_program_prefix) + " " + mCurrentAdminPrefs.get(key);
			else if (key.equalsIgnoreCase(App.getApp().getString(R.string.key_saved_search)))
				summary = App.getApp().getString(R.string.pref_savedsearch_prefix) + " " + mCurrentAdminPrefs.get(key);
			else if (key.equalsIgnoreCase(App.getApp().getString(R.string.key_provider)))
				summary = App.getApp().getString(R.string.pref_provider_prefix) + " " + mCurrentAdminPrefs.get(key);
			else if (key.equalsIgnoreCase(App.getApp().getString(R.string.key_location)))
				summary = App.getApp().getString(R.string.pref_location_prefix) + " " + mCurrentAdminPrefs.get(key);
			else if (key.equalsIgnoreCase(App.getApp().getString(R.string.key_min_refresh_seconds)))
				summary = UiUtils.getTimeString(Integer.valueOf(mCurrentAdminPrefs.get(key)));
			else if (key.equalsIgnoreCase(App.getApp().getString(R.string.key_max_refresh_seconds)))
				summary = UiUtils.getTimeString(Integer.valueOf(mCurrentAdminPrefs.get(key)));
			else if (key.equalsIgnoreCase(App.getApp().getString(R.string.key_max_consent_time)))
				summary = UiUtils.getTimeString(Integer.valueOf(mCurrentAdminPrefs.get(key)));
			else if (key.equalsIgnoreCase(App.getApp().getString(R.string.key_clear_consent_time)))
				summary = UiUtils.getTimeString(Integer.valueOf(mCurrentAdminPrefs.get(key)));
			else
				summary = mCurrentAdminPrefs.get(key);

			changedPref.setSummary(summary);
		}
	}

	private static void updateSyncMinimum(SharedPreferences prefs, String key, String newValue) {

		if (!isPositiveInteger(newValue)) {
			prefs.edit().putString(key, mCurrentAdminPrefs.get(key)).commit();
			if(App.DEBUG) Log.w(TAG, "New Value was not an Integer, so adding back in value of=" + mCurrentAdminPrefs.get(key));
			return;
		}

		Integer newMinValue = Integer.valueOf(newValue);
		String syncMaxKey = App.getApp().getString(R.string.key_max_refresh_seconds);
		Integer syncMaxValue = Integer.valueOf(prefs.getString(syncMaxKey, mCurrentAdminPrefs.get(syncMaxKey)));

		if (newMinValue < syncMaxValue) {
			prefs.edit().putString(key, String.valueOf(newMinValue)).commit();
			setCurrentPreferences();
		} else {
			UiUtils.toastAlert(App.getApp().getString(R.string.pref_not_allowed), App.getApp().getString(R.string.pref_min_requirement));
			prefs.edit().putString(key, mCurrentAdminPrefs.get(key)).commit();
			mValueError = true;
		}

	}

	private static void updateSyncInterval(SharedPreferences prefs, String key, String newValue) {

		if (!isPositiveInteger(newValue)) {
			prefs.edit().putString(key, mCurrentAdminPrefs.get(key)).commit();
			if(App.DEBUG) Log.w(TAG, "New Value was not an Integer, so adding back in value of=" + mCurrentAdminPrefs.get(key));
			return;
		}

		Integer newSyncValue = Integer.valueOf(newValue);
		String syncMinKey = App.getApp().getString(R.string.key_min_refresh_seconds);
		Integer syncMinValue = Integer.valueOf(prefs.getString(syncMinKey, mCurrentAdminPrefs.get(syncMinKey)));

		if (newSyncValue > syncMinValue) {
			AccountManager accountManager = AccountManager.get(App.getApp());
			Account[] accounts = accountManager.getAccountsByType(App.getApp().getString(R.string.app_account_type));

			if (accounts.length > 0) {
				ContentResolver.removePeriodicSync(accounts[0], App.getApp().getString(R.string.app_provider_authority), new Bundle());
				ContentResolver.addPeriodicSync(accounts[0], App.getApp().getString(R.string.app_provider_authority), new Bundle(), newSyncValue);
			}
			setCurrentPreferences();
		} else {
			UiUtils.toastAlert(App.getApp().getString(R.string.pref_not_allowed), App.getApp().getString(R.string.pref_max_requirement));
			prefs.edit().putString(key, mCurrentAdminPrefs.get(key)).commit();
			mValueError = true;
		}
	}

	private static boolean isPositiveInteger(String value) {

		boolean isPositiveInt = false;
		try {
			Integer intValue = Integer.parseInt(value);
			if (intValue >= 0)
				isPositiveInt = true;
		} catch (NumberFormatException nfe) {
			Log.w(TAG, "Could not parse " + nfe);
		}

		if (!isPositiveInt && mShowToast)
			UiUtils.toastAlert(App.getApp().getString(R.string.pref_not_allowed), App.getApp().getString(R.string.pref_not_a_positive_int));
		return isPositiveInt;
	}

	private static boolean isBooleanValue(String newValue) {
		boolean isBoolean = false;
		String trueString = "true";
		String falseString = "false";
		if (newValue.equalsIgnoreCase(trueString) || newValue.equalsIgnoreCase(falseString))
			isBoolean = true;
		if(App.DEBUG) Log.v(TAG, "isBooleanValue=" + isBoolean + " for value=" + newValue + "=");
		return isBoolean;
	}

	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		Preference changedPref = findPreference(key);
		verifyUpdatedPreference(prefs, changedPref, key);
	}

	

	private static Map<String, String> createAdminPreferenceMap() {
		Map<String, String> map = new HashMap<String, String>();
		Context c = App.getApp();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);

		// Set current key/value pairs:
		map.put(c.getString(R.string.key_max_refresh_seconds), prefs.getString(c.getString(R.string.key_max_refresh_seconds), c.getString(R.string.default_max_refresh_seconds)));
		map.put(c.getString(R.string.key_min_refresh_seconds), prefs.getString(c.getString(R.string.key_min_refresh_seconds), c.getString(R.string.default_min_refresh_seconds)));
		map.put(c.getString(R.string.key_max_consent_time), prefs.getString(c.getString(R.string.key_max_consent_time), c.getString(R.string.default_max_consent_time)));
		map.put(c.getString(R.string.key_clear_consent_time), prefs.getString(c.getString(R.string.key_clear_consent_time), c.getString(R.string.default_clear_consent_time)));
		map.put(c.getString(R.string.key_program), prefs.getString(c.getString(R.string.key_program), c.getString(R.string.default_program)));
		map.put(c.getString(R.string.key_provider), prefs.getString(c.getString(R.string.key_provider), c.getString(R.string.default_provider)));
		map.put(c.getString(R.string.key_location), prefs.getString(c.getString(R.string.key_location), c.getString(R.string.default_location)));
		map.put(c.getString(R.string.key_server), prefs.getString(c.getString(R.string.key_server), c.getString(R.string.default_server)));
		map.put(c.getString(R.string.key_username), prefs.getString(c.getString(R.string.key_username), c.getString(R.string.default_username)));
		map.put(c.getString(R.string.key_password), prefs.getString(c.getString(R.string.key_password), c.getString(R.string.default_password)));
		map.put(c.getString(R.string.key_saved_search), prefs.getString(c.getString(R.string.key_saved_search), c.getString(R.string.default_saved_search)));

		map.put(c.getString(R.string.key_client_auth), String.valueOf(prefs.getBoolean(c.getString(R.string.key_client_auth), Boolean.parseBoolean(c.getString(R.string.default_use_client_auth)))));
		map.put(c.getString(R.string.key_enable_activity_log), String.valueOf(prefs.getBoolean(c.getString(R.string.key_enable_activity_log), Boolean.parseBoolean(c.getString(R.string.default_enable_activity_logging)))));
		map.put(c.getString(R.string.key_show_settings_menu), String.valueOf(prefs.getBoolean(c.getString(R.string.key_show_settings_menu), Boolean.parseBoolean(c.getString(R.string.default_show_settings_menu)))));
		map.put(c.getString(R.string.key_use_saved_searches), String.valueOf(prefs.getBoolean(c.getString(R.string.key_use_saved_searches), Boolean.parseBoolean(c.getString(R.string.default_use_saved_searches)))));
		map.put(c.getString(R.string.key_show_form_prompt), String.valueOf(prefs.getBoolean(c.getString(R.string.key_show_form_prompt), Boolean.parseBoolean(c.getString(R.string.default_show_form_prompt)))));
		map.put(c.getString(R.string.key_request_consent), String.valueOf(prefs.getBoolean(c.getString(R.string.key_request_consent), Boolean.parseBoolean(c.getString(R.string.default_request_consent)))));
		return Collections.unmodifiableMap(map);
	}

	/**
	 * Provides more fine-grained detail of the preference type than simply
	 * instance of EditText or instanceof CheckBox. Allows preferences to have
	 * complex error checking before being set.
	 * 
	 * @return
	 */
	private static final Map<String, Integer> getPreferenceTypeMap() {
		Map<String, Integer> map = new HashMap<String, Integer>();
		Context c = App.getApp();

		// Strings with current values:
		map.put(c.getString(R.string.key_max_refresh_seconds), SYNC_INTERVAL_TIME);
		map.put(c.getString(R.string.key_min_refresh_seconds), SYNC_MIN_REFRESH_TIME);
		map.put(c.getString(R.string.key_max_consent_time), POSITIVE_INTEGER);
		map.put(c.getString(R.string.key_clear_consent_time), POSITIVE_INTEGER);
		map.put(c.getString(R.string.key_program), POSITIVE_INTEGER);
		map.put(c.getString(R.string.key_provider), POSITIVE_INTEGER);
		map.put(c.getString(R.string.key_location), POSITIVE_INTEGER);
		map.put(c.getString(R.string.key_server), FREE_TEXT);
		map.put(c.getString(R.string.key_username), FREE_TEXT);
		map.put(c.getString(R.string.key_password), FREE_TEXT);
		map.put(c.getString(R.string.key_saved_search), POSITIVE_INTEGER);
		map.put(c.getString(R.string.key_client_auth), BOOLEAN);
		map.put(c.getString(R.string.key_enable_activity_log), BOOLEAN);
		map.put(c.getString(R.string.key_show_settings_menu), BOOLEAN);
		map.put(c.getString(R.string.key_use_saved_searches), BOOLEAN);
		map.put(c.getString(R.string.key_show_form_prompt), BOOLEAN);
		map.put(c.getString(R.string.key_request_consent), BOOLEAN);
		return Collections.unmodifiableMap(map);
	}

	private static Map<String, String> createUserPreferenceMap() {
		Map<String, String> map = new HashMap<String, String>();
		Context c = App.getApp();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
//		map.put(c.getString(R.string.key_provider), prefs.getString(c.getString(R.string.key_provider), c.getString(R.string.default_provider)));
//		map.put(c.getString(R.string.key_location), prefs.getString(c.getString(R.string.key_location), c.getString(R.string.default_location)));
//		map.put(c.getString(R.string.key_program), prefs.getString(c.getString(R.string.key_program), c.getString(R.string.default_program)));
//		map.put(c.getString(R.string.key_saved_search), prefs.getString(c.getString(R.string.key_saved_search), c.getString(R.string.default_saved_search)));
//		map.put(c.getString(R.string.key_use_saved_searches), String.valueOf(prefs.getBoolean(c.getString(R.string.key_use_saved_searches), Boolean.parseBoolean(c.getString(R.string.default_use_saved_searches)))));
		map.put(c.getString(R.string.key_show_form_prompt), String.valueOf(prefs.getBoolean(c.getString(R.string.key_show_form_prompt), Boolean.parseBoolean(c.getString(R.string.default_show_form_prompt)))));

		return Collections.unmodifiableMap(map);
	}

	@Override
	protected void onPause() {
		super.onPause();
		mShowToast = false;
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	}

}

// private static String getCurrentPreferenceValue(String key) {
// SharedPreferences prefs =
// PreferenceManager.getDefaultSharedPreferences(App.getApp());
// String currentValue = null;
// if (PREFERENCE_TYPE.get(key) == BOOLEAN)
// currentValue = String.valueOf(prefs.getBoolean(key,
// Boolean.getBoolean(mCurrentAdminPrefs.get(key))));
// else
// currentValue = prefs.getString(key, mCurrentAdminPrefs.get(key));
// return currentValue;
// }
