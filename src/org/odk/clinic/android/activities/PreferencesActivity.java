package org.odk.clinic.android.activities;

import org.odk.clinic.android.R;
import org.odk.clinic.android.database.ClinicAdapter;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.view.Window;

public class PreferencesActivity extends PreferenceActivity implements
		OnSharedPreferenceChangeListener {

	public static String KEY_SERVER = "server";
	public static String KEY_USERNAME = "username";
	public static String KEY_PASSWORD = "password";
	public static String KEY_SAVED_SEARCH = "saved_search";
	public static String KEY_PROGRAM = "program";

	public static String KEY_PROVIDER = "provider";
	public static String KEY_COHORT = "cohort";
	public static String KEY_FIRST_RUN = "firstRun";
	public static String KEY_INFO = "info";
	public static String KEY_USE_SAVED_SEARCHES = "use_saved_searches";

	private ClinicAdapter mPatientDbAdapter = new ClinicAdapter();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.server_preferences);

		// // Display app version number for user to know
		// PreferenceScreen ps = (PreferenceScreen) findPreference(KEY_INFO);
		// ps.setTitle(getString(R.string.app_name) + " v"
		// + getString(R.string.app_version));
		//
		// setTitle(getString(R.string.app_name) + " > "
		// + getString(R.string.server_preferences));
		updateServer();
		updateUsername();
		updatePassword();
		updateSavedSearch();
		updateProvider();
		updateProgram();

	}

	@Override
	protected void onPause() {
		super.onPause();
		getPreferenceScreen().getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		getPreferenceScreen().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(this);
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.equals(KEY_SERVER)) {
			updateServer();
		} else if (key.equals(KEY_USERNAME)) {
			updateUsername();
		} else if (key.equals(KEY_PASSWORD)) {
			updatePassword();
		} else if (key.equals(KEY_SAVED_SEARCH)) {
			updateSavedSearch();
		} else if (key.equals(KEY_PROVIDER)) {
			updateProvider();
		} else if (key.equals(KEY_PROGRAM)) {
			updateProgram();
		}
	}

	private void updateServer() {
		EditTextPreference etp = (EditTextPreference) this
				.getPreferenceScreen().findPreference(KEY_SERVER);
		String s = etp.getText();
		if (s.endsWith("/")) {
			s = s.substring(0, s.lastIndexOf("/"));
		}
		etp.setSummary(s);

		// when you change servers, you should wipe the cohort list.
		mPatientDbAdapter.open();
		mPatientDbAdapter.deleteAllCohorts();
		mPatientDbAdapter.close();

	}

	private void updateUsername() {
		EditTextPreference etp = (EditTextPreference) this
				.getPreferenceScreen().findPreference(KEY_USERNAME);
		etp.setSummary(etp.getText());
	}

	private void updatePassword() {
		EditTextPreference etp = (EditTextPreference) this
				.getPreferenceScreen().findPreference(KEY_PASSWORD);
		etp.setSummary(etp.getText().replaceAll(".", "*"));

	}

	private void updateSavedSearch() {
		EditTextPreference etp = (EditTextPreference) this
				.getPreferenceScreen().findPreference(KEY_SAVED_SEARCH);
		etp.setSummary(etp.getText());
	}

	private void updateProvider() {
		EditTextPreference etp = (EditTextPreference) this
				.getPreferenceScreen().findPreference(KEY_PROVIDER);
		etp.setSummary(etp.getText());
	}


	private void updateProgram() {
		EditTextPreference etp = (EditTextPreference) this
				.getPreferenceScreen().findPreference(KEY_PROGRAM);
		etp.setSummary(etp.getText());
	}
}
