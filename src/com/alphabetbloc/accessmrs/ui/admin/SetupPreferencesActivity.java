package com.alphabetbloc.accessmrs.ui.admin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import javax.crypto.SecretKey;

import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.alphabetbloc.accessmrs.providers.DataModel;
import com.alphabetbloc.accessmrs.providers.DbProvider;
import com.alphabetbloc.accessmrs.services.WakefulIntentService;
import com.alphabetbloc.accessmrs.services.WipeDataService;
import com.alphabetbloc.accessmrs.utilities.App;
import com.alphabetbloc.accessmrs.utilities.Crypto;
import com.alphabetbloc.accessmrs.utilities.EncryptionUtil;
import com.alphabetbloc.accessmrs.utilities.FileUtils;
import com.alphabetbloc.accessmrs.utilities.KeyStoreUtil;
import com.alphabetbloc.accessmrs.utilities.UiUtils;
import com.alphabetbloc.accessmrs.R;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class SetupPreferencesActivity extends BaseAdminActivity {

	private Context mContext;
	public static final String TAG = SetupPreferencesActivity.class.getSimpleName();
	public static final String SQLCIPHER_KEY_NAME = "sqlCipherDbKey";
	private static final String CONFIG_FILE = "config.txt";
	private static final String HIDDEN_CONFIG_FILE = ".config.txt";

	// intents
	public static final String SETUP_INTENT = "setup_intent";
	public static final int FIRST_RUN = 0;
	public static final int RESET_COLLECT = 1;
	public static final int RESET_ACCESS_MRS = 2;
	public static final int ACCOUNT_SETUP = 4;

	// TODO! strong passphrase config variables
	// private final static int MIN_PASS_LENGTH = 6;
	// private final static int MAX_PASS_ATTEMPTS = 3;
	// private final static int PASS_RETRY_WAIT_TIMEOUT = 30000;
	// private int currentPassAttempts = 0;

	// Views
	private static final int LOADING = 1;
	protected static final int REQUEST_DB_SETUP = 2;
	protected static final int REQUEST_DB_REKEY = 3;

	// Buttons
	private static final int VERIFY_ENTRY = 1;
	private static final int ASK_NEW_ENTRY = 2;
	private static final int CONFIRM_ENTRY = 3;

	private TextView mInstructionText;
	private EditText mEditText;
	private String mFirstEntry;
	private int mStep;
	private String mDecryptedPwd;
	private boolean isFreshInstall = true;
	private int mSetupType;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		mSetupType = getIntent().getIntExtra(SETUP_INTENT, 0);
		refreshView();
	}

	@Override
	protected void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter(WipeDataService.WIPE_DATA_COMPLETE);
		registerReceiver(mWipeDataComplete, filter);
	}

	private void refreshView() {
		Log.v(TAG, "Refreshing view with mSetupType=" + mSetupType);
		switch (mSetupType) {

		case FIRST_RUN:
			if (!FileUtils.isDataWiped()) {
				mSetupType = RESET_ACCESS_MRS;
				refreshView();
			}
			FileUtils.setupDefaultSslStore(FileUtils.MY_KEYSTORE);
			FileUtils.setupDefaultSslStore(FileUtils.MY_TRUSTSTORE);
			createView(REQUEST_DB_SETUP);
			break;

		case RESET_ACCESS_MRS:
			UiUtils.toastAlert(mContext, getString(R.string.sql_error_lost_db_key_title), getString(R.string.sql_error_lost_db_key));
			Log.e(TAG, "RESETTING ACCESS MRS: " + getString(R.string.sql_error_lost_db_key));
			WakefulIntentService.sendWakefulWork(mContext, WipeDataService.class);
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
			settings.edit().putBoolean(getString(R.string.key_first_run), true).commit();
			mSetupType = FIRST_RUN;
			createView(LOADING);
			break;

		case RESET_COLLECT:
			UiUtils.toastAlert(mContext, getString(R.string.sql_error_lost_db_key_title), getString(R.string.sql_error_lost_db_key));
			Log.e(TAG, "RESETTING COLLECT: " + getString(R.string.sql_error_lost_db_key));
			Intent i = new Intent(mContext, WipeDataService.class);
			i.putExtra(WipeDataService.WIPE_ACCESS_MRS_DATA, false);
			WakefulIntentService.sendWakefulWork(mContext, i);
			createView(LOADING);
			break;

		default:
			break;
		}

	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(mWipeDataComplete);
	}

	protected BroadcastReceiver mWipeDataComplete = new BroadcastReceiver() {
		public void onReceive(Context ctxt, Intent i) {
			finish();
		}
	};

	private void createView(int view) {
		setContentView(R.layout.pwd_setup);
		mInstructionText = (TextView) findViewById(R.id.instruction);
		Button submitButton = (Button) findViewById(R.id.submit_button);
		mEditText = (EditText) findViewById(R.id.text_password);
		ProgressBar progressWheel = (ProgressBar) findViewById(R.id.progress_wheel);

		// loading vs. not loading
		switch (view) {
		case LOADING:
			mInstructionText.setText(R.string.unlocking_db);
			submitButton.setVisibility(View.GONE);
			mEditText.setVisibility(View.GONE);
			progressWheel.setVisibility(View.VISIBLE);
			break;
		default:
			submitButton.setVisibility(View.VISIBLE);
			mEditText.setVisibility(View.VISIBLE);
			progressWheel.setVisibility(View.GONE);
			mEditText.setHint("Click to Enter Text");
			mFirstEntry = "";
			break;
		}

		// if not loading, set appropriate buttons/text
		switch (view) {
		// TODO! does not work yet b/c also have to rekey collectDb
		case REQUEST_DB_REKEY:
			mStep = VERIFY_ENTRY;
			isFreshInstall = false;
			mInstructionText.setText(R.string.sql_verify_pwd);
			submitButton.setOnClickListener(mSqlCipherPwdListener);
			mDecryptedPwd = EncryptionUtil.getPassword();
			break;

		case REQUEST_DB_SETUP:
			mStep = ASK_NEW_ENTRY;
			isFreshInstall = true;
			mInstructionText.setText(R.string.sql_set_sqlcipher_pwd);
			submitButton.setOnClickListener(mSqlCipherPwdListener);
			break;

		default:
			break;
		}
	}

	// STEP 1: make a password
	private OnClickListener mSqlCipherPwdListener = new OnClickListener() {

		@Override
		public void onClick(View v) {

			String userEntry = mEditText.getText().toString();
			mEditText.setText("");
			if (userEntry.equals(""))
				UiUtils.toastAlert(mContext, getString(R.string.sql_error_title), getString(R.string.sql_error_empty_password));

			switch (mStep) {
			case VERIFY_ENTRY:
				if (userEntry.equals(mDecryptedPwd)) {
					mStep = ASK_NEW_ENTRY;
					mInstructionText.setText(R.string.sql_change_sqlcipher_pwd);
				} else {
					UiUtils.toastAlert(mContext, getString(R.string.sql_error_title), getString(R.string.sql_error_verify_pwd));
				}
				break;
			case ASK_NEW_ENTRY:
				if (isSecure(userEntry)) {
					mFirstEntry = userEntry;
					userEntry = "";
					mInstructionText.setText(R.string.sql_confirm_sqlcipher_pwd);
					mStep = CONFIRM_ENTRY;
				} else {
					UiUtils.toastAlert(mContext, getString(R.string.sql_error_title), getString(R.string.sql_error_new_pwd));
				}
				break;
			case CONFIRM_ENTRY:

				if (mFirstEntry.equals(userEntry)) {
					createView(LOADING);
					encryptAccessMrsDb(userEntry);
				} else {
					mStep = ASK_NEW_ENTRY;
					mInstructionText.setText(R.string.sql_set_sqlcipher_pwd);
					UiUtils.toastAlert(mContext, getString(R.string.sql_error_title), getString(R.string.sql_error_confirm_pwd));
				}
				break;
			default:
				break;
			}
		}
	};

	private static boolean isSecure(String str) {
		boolean alpha = false;
		boolean num = false;
		boolean lower = false;
		boolean upper = false;
		int count = 0;
		for (char c : str.toCharArray()) {
			if (Character.isLetter(c))
				alpha = true;
			if (Character.isDigit(c))
				num = true;
			if (Character.isLowerCase(c))
				lower = true;
			if (Character.isUpperCase(c))
				upper = true;
			count++;
		}

		if (alpha && num && upper && lower && count > 7)
			return true;
		else
			return false;
	}

	// STEP 2: Encrypt the AccessMRS Db
	private void encryptAccessMrsDb(final String userEntry) {

		new AsyncTask<Void, Void, Boolean>() {
			Exception error;

			@Override
			protected Boolean doInBackground(Void... params) {

				try {
					// create new key
					SecretKey key = Crypto.generateKey();
					KeyStoreUtil ks = KeyStoreUtil.getInstance();
					boolean success = ks.put(SQLCIPHER_KEY_NAME, key.getEncoded());
					Log.d(TAG, "Adding new key to keystore... success: " + success);

					// encrypt the userEntry
					String encryptedPwd = Crypto.encrypt(userEntry, key);

					// save the encryptedPwd to AccessMRS
					SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
					prefs.edit().putString(SQLCIPHER_KEY_NAME, encryptedPwd).commit();

					if (isFreshInstall) {
						// encrypt a new AccessMRS Db
						File db = App.getApp().getDatabasePath(DataModel.DATABASE_NAME);
						if (db.exists())
							db.delete();
						DbProvider.createDb();

					} else
						DbProvider.rekeyDb(userEntry);

					return success;

				} catch (Exception e) {
					error = e;
					Log.e(TAG, "Error: " + e.getMessage(), e);
					return false;
				}
			}

			@Override
			protected void onPostExecute(Boolean success) {
				if (success) {
					// encrypt a new Collect instances Db
					SetupPreferencesActivity.this.encryptCollectDb();
				} else {
					if (error != null)
						Log.e(TAG, "Error adding new SQLCipher key to the Keystore!" + error.getMessage());
					else
						Log.e(TAG, "Failed to encrypt database with new password. Please try again.");

					refreshView();
				}

			}
		}.execute();

	}

	// STEP 3: Encrypt the Collect Db
	private void encryptCollectDb() {
		boolean isCollectSetup = true;

		try {
			// Simply opening the db should force it to start a new encrypted db
			Cursor c = App.getApp().getContentResolver().query(Uri.parse(InstanceColumns.CONTENT_URI + "/reset"), null, null, null, null);
			if (c != null)
				c.close();
			isCollectSetup = true;

		} catch (Exception e) {

			e.printStackTrace();
			isCollectSetup = false;
		}

		if (isCollectSetup) {
			Log.d(TAG, "Successfully encrypted Collect db with new password.");
			setupPreferences();
		} else {
			mSetupType = RESET_COLLECT;
			refreshView();
		}
	}

	// STEP 4: Setup Preferences
	private void setupPreferences() {
		// Setup Default Prefs
		PreferenceManager.setDefaultValues(this, R.xml.preferences_admin, false);

		// Overwrite default prefs from config file
		boolean imported = importConfigFile();

		launcAccountSetup(imported);
	}
	
	// STEP 5: Setup Android Account
	private void launcAccountSetup(boolean useConfigFile) {
		// launch AccountSetupActivity
		Intent i = new Intent(mContext, SetupAccountActivity.class);
		i.putExtra(SetupAccountActivity.LAUNCHED_FROM_ACCT_MGR, false);
		i.putExtra(SetupAccountActivity.USE_CONFIG_FILE, useConfigFile);
		startActivity(i);

		// Finished First Run Db and Preferences Setup
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		prefs.edit().putBoolean(getString(R.string.key_first_run), false).commit();
		finish();
	}

	private boolean importConfigFile() {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
		File configFile = new File(FileUtils.getExternalRootDirectory(), CONFIG_FILE);
		File hiddenConfigFile = new File(FileUtils.getExternalRootDirectory(), HIDDEN_CONFIG_FILE);
		if (!configFile.exists())
			configFile = hiddenConfigFile;

		boolean success = false;
		if (configFile.exists()) {
			// Read text from file
			try {
				String[] booleanPrefs = { getString(R.string.key_client_auth), getString(R.string.key_use_saved_searches), getString(R.string.key_enable_activity_log), getString(R.string.key_show_settings_menu) };
				String password = getString(R.string.key_password);
				BufferedReader br = new BufferedReader(new FileReader(configFile));
				String line;

				while ((line = br.readLine()) != null) {

					int equal = line.indexOf("=");
					String prefName = line.substring(0, equal);
					String prefValue = line.substring(equal + 1);

					if (prefName.equalsIgnoreCase(password))
						prefValue = EncryptionUtil.encryptString(prefValue);

					boolean booleanPref = false;
					for (String currentPref : booleanPrefs) {
						if (currentPref.equals(prefName))
							booleanPref = true;
					}

					if (booleanPref)
						settings.edit().putBoolean(prefName, Boolean.parseBoolean(prefValue)).commit();
					else
						settings.edit().putString(prefName, prefValue).commit();

					Log.v(TAG, "Imported Preference #" + line + " :" + prefName + " boolean=" + booleanPref);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			success = configFile.delete();
		}

		return success;
	}

}
