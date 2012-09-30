package com.alphabetbloc.clinic.ui.admin;

import java.io.File;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.view.Window;

import com.alphabetbloc.clinic.R;

public class PreferencesActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

//	private static final String TAG = PreferencesActivity.class.getSimpleName();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.admin_preferences);

		updateServer();
		updateSavedSearch();
		updateProgram();

	}

	@Override
	protected void onPause() {
		super.onPause();
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(getString(R.string.key_server))) {
			updateServer();
		} else if (key.equals(getString(R.string.key_saved_search))) {
			updateSavedSearch();
		} else if (key.equals(getString(R.string.key_program))) {
			updateProgram();
		}
	}

	private void updateServer() {
		EditTextPreference etp = (EditTextPreference) this.getPreferenceScreen().findPreference(getString(R.string.key_server));
		String s = etp.getText();
		if (s.endsWith(File.separator)) {
			s = s.substring(0, s.lastIndexOf(File.separator));
		}
		etp.setSummary(s);
		// TODO: When server changes, should wipe out cohort list...
		// mPatientDbAdapter.openDb().deleteAllCohorts();
	}

	private void updateSavedSearch() {
		EditTextPreference etp = (EditTextPreference) this.getPreferenceScreen().findPreference(getString(R.string.key_saved_search));
		etp.setSummary(etp.getText());
	}

	private void updateProgram() {
		EditTextPreference etp = (EditTextPreference) this.getPreferenceScreen().findPreference(getString(R.string.key_program));
		etp.setSummary(etp.getText());
	}
}
