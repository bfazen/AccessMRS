package com.alphabetbloc.clinic.ui.admin;

import java.io.File;

import javax.crypto.spec.SecretKeySpec;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.data.DbAdapter;
import com.alphabetbloc.clinic.ui.user.DashboardActivity;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.EncryptionUtil;
import com.alphabetbloc.clinic.utilities.KeyStoreUtil;

import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

public class ClinicLauncherActivity extends Activity {

	public static final String TAG = ClinicLauncherActivity.class.getSimpleName();
	public static final String SQLCIPHER_KEY_NAME = "sqlCipherDbKey";
	public static final String OLD_UNLOCK_ACTION = "android.credentials.UNLOCK";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.loading);
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.e(TAG, "resuming ClinicLAuncher!");
		if (isClinicSetup()) {
			if (isCollectSetup())
				startActivity(new Intent(this, DashboardActivity.class));
			finish();
		}
	}

	private boolean isClinicSetup() {
		boolean setupComplete = true;

		// if db open, then it must be setup
		if (getDatabasePath(DbAdapter.DATABASE_NAME).exists() && DbAdapter.isOpen())
			return setupComplete;

		// Step 1: check keystore is unlocked -> setup keystore pwd
		KeyStoreUtil ks = KeyStoreUtil.getInstance();
		if (ks.state() != KeyStoreUtil.State.UNLOCKED) {
			startActivity(new Intent(OLD_UNLOCK_ACTION));
			return !setupComplete;
		}

		// Step 2: check first use -> launch setup
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		boolean firstRun = settings.getBoolean(getString(R.string.key_first_run), true);
		if (firstRun) {
			Intent i = new Intent(this, InitialSetupActivity.class);
			i.putExtra(InitialSetupActivity.SETUP_INTENT, InitialSetupActivity.FIRST_RUN);
			startActivity(i);
			return !setupComplete;
		}

		// check Db exists, it has a key and pwd
		File db = App.getApp().getDatabasePath(DbAdapter.DATABASE_NAME);
		String pwd = settings.getString(SQLCIPHER_KEY_NAME, "");
		SecretKeySpec key = EncryptionUtil.getKey(SQLCIPHER_KEY_NAME);
		if (db == null || !db.exists() || pwd.equals("") || key == null) {
			// not first run, but have lost Db info! CATASTROPHE... SO RESET.
			Intent i = new Intent(this, InitialSetupActivity.class);
			i.putExtra(InitialSetupActivity.SETUP_INTENT, InitialSetupActivity.RESET_CLINIC);
			startActivity(i);
			return !setupComplete;
		}

		return setupComplete;
	}

	private boolean isCollectSetup() {
		boolean setupComplete = true;

		// Step 1: check if collect installed
		try {
			getPackageManager().getPackageInfo("org.odk.collect.android", PackageManager.GET_META_DATA);
		} catch (NameNotFoundException e) {
			showCustomToast("Collect Must Be Installed To Use This Software!");
			return !setupComplete;
		}

		// Step 2: Try to open or create the db
		try {
			Log.e(TAG, "testing collect");
			App.getApp().getContentResolver().query(Uri.parse(InstanceColumns.CONTENT_URI + "/reset"), null, null, null, null);
		} catch (Exception e) {
			// Lost key! (clinic reinstalled?) CATASTROPHE... SO RESET COLLECT
			Intent i = new Intent(this, InitialSetupActivity.class);
			i.putExtra(InitialSetupActivity.SETUP_INTENT, InitialSetupActivity.RESET_COLLECT);
			startActivity(i);
			return !setupComplete;
		}

		return setupComplete;
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
