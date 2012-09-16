package org.odk.clinic.android.activities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.odk.clinic.android.R;
import org.odk.clinic.android.database.DbAdapter;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.Crypto;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.clinic.android.utilities.KeyStore;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.alphabetbloc.clinic.services.WipeDataService;
import com.commonsware.cwac.wakeful.WakefulIntentService;

public class ClinicLauncherActivity extends Activity {

	private Context mContext;
	public static final String TAG = "ClinicLauncherActivity";
	public static final String OLD_UNLOCK_ACTION = "android.credentials.UNLOCK";
	public static final String SQLCIPHER_KEY_NAME = "sqlCipherDbKey";
	public static final String SQLCIPHER_PREFS_NAME = "sqlCipherDbKey.sharedPreferences";
	private static final String CONFIG_FILE = "config.txt";
	private static final String HIDDEN_CONFIG_FILE = ".config.txt";
	private KeyStore ks;

	// strong passphrase config variables
	// private final static int MIN_PASS_LENGTH = 6;
	// private final static int MAX_PASS_ATTEMPTS = 3;
	// private final static int PASS_RETRY_WAIT_TIMEOUT = 30000;
	// private int currentPassAttempts = 0;
	protected static final int VERIFY_ENTRY = 1;
	protected static final int ASK_NEW_ENTRY = 2;
	protected static final int CONFIRM_ENTRY = 3;
	protected static final int ENTRY_ERROR = 4;
	protected static final String REQUEST_SETUP = "request_setup";

	// Views
	private static final int DB_LOCKED_ERROR = 0;
	private static final int LOADING = 1;
	protected static final int REQUEST_PROVIDER_SETUP = 2;
	protected static final int REQUEST_DB_SETUP = 3;
	protected static final int REQUEST_DB_REKEY = 4;
	protected static final int REQUEST_PROVIDER_CHANGE = 5;

	private TextView mInstructionText;
	private EditText mEditText;
	private String mFirstEntry;
	private int mStep;
	private String mDecryptedPwd;
	private String mProviderId;
	private boolean isDecrypting = false;
	private boolean isFreshInstall = false;
	private boolean isPrefsSet = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;
		requestWindowFeature(Window.FEATURE_NO_TITLE);
	}

	@Override
	protected void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter(WipeDataService.WIPE_DATA_COMPLETE);
		LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, filter);

		// intent to change provider id w/o access to all settings
		int intent = getIntent().getIntExtra(REQUEST_SETUP, 0);
		if (intent > 0)
			createView(intent);
		else
			refreshView();

	}

	private void refreshView() {

		createView(LOADING);
		if (isCollectSetup()) {

			if (DbAdapter.getInstance().isOpen()) {
				// we are all setup
				Log.e(TAG, "all set up in refreshView");
				startActivity(new Intent(mContext, DashboardActivity.class));
				finish();
			} else {
				Log.e(TAG, "Not all set up in refreshView");
				// try setup
				boolean setupComplete = isClinicSetup();

				if (setupComplete) {
					// try to unlock
					if (!DbAdapter.getInstance().isOpen()) {
						// try to unlock
						unlockDb(SQLCIPHER_KEY_NAME);
					} else {
						// we are all setup
						startActivity(new Intent(mContext, DashboardActivity.class));
						finish();
					}
				}
			}
		} else {
			showCustomToast("Collect Must Be Installed To Use This Software!");
			Log.e(TAG, "Collect is not installed");
			finish();
		}
	}

	private boolean isCollectSetup() {
		boolean setupComplete = true;

		// check if collect installed
		if (!isAppInstalled("org.odk.collect.android"))
			return !setupComplete;

		// get db
		File db = null;
		try {
			Context collectCtx = App.getApp().createPackageContext("org.odk.collect.android", Context.CONTEXT_RESTRICTED);
			db = collectCtx.getDatabasePath(InstanceProviderAPI.DATABASE_NAME);
		} catch (Exception e) {
			e.printStackTrace();
			return !setupComplete;
		}

		// check db password works
		if (db.exists()) {
			try {
				Cursor c = App.getApp().getContentResolver().query(InstanceColumns.CONTENT_URI, null, null, null, null);
				if (c == null) {
					Log.e(TAG, "nothing in db");
				}
			} catch (Exception e) {
				// Db exisits but lost key! (clinic reinstalled?) CATASTROPHE...
				// SO RESET COLLECT
				Log.e(TAG, getString(R.string.error_lost_db_key));
				showCustomToast(getString(R.string.error_lost_db_key));
				Intent i = new Intent(mContext, WipeDataService.class);
				i.putExtra(WipeDataService.WIPE_CLINIC_DATA, false);
				WakefulIntentService.sendWakefulWork(mContext, i);
				createView(LOADING);
			}
		}

		// database does not exist yet, or was setup correctly, or is being
		// reset
		return setupComplete;
	}

	private void toastCurrentSettings() {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
		showCustomToast("Encrypting with  Current Settings:" + "\n  PASSWORD=" + settings.getString(getString(R.string.key_provider), "Unknown") + "\n  PROVIDER=" + settings.getString(getString(R.string.key_provider), "Unknown") + "\n USERNAME="
				+ settings.getString(getString(R.string.key_username), "Z") + "\n PASSWORD=" + settings.getString(getString(R.string.key_password), "Z") + "\n SERVER=" + settings.getString(getString(R.string.key_server), "Z") + "\n COHORT="
				+ settings.getString(getString(R.string.key_cohort), "Z") + "\n SAVED SEARCH=" + settings.getString(getString(R.string.key_saved_search), "Z") + "\n USE SAVED SEARCH=" + settings.getBoolean(getString(R.string.key_use_saved_searches), true) + "\n CLIENT AUTH="
				+ settings.getBoolean(getString(R.string.key_client_auth), true) + "\n ACTIVITY LOG=" + settings.getBoolean(getString(R.string.key_enable_activity_log), true) + "\n SHOW MENU=" + settings.getBoolean(getString(R.string.key_show_settings_menu), false)
				+ "\n FIRST RUN=" + settings.getBoolean(getString(R.string.key_first_run), true));
	}

	public boolean isAppInstalled(String packageName) {

		PackageManager pm = getPackageManager();
		try {
			pm.getPackageInfo(packageName, PackageManager.GET_META_DATA);
		} catch (NameNotFoundException e) {
			return false;
		}
		return true;
	}

	/**
	 * setupClinic assesses and then launches setup for credential storage
	 * password, Db password, and provider id. NB. Users want to change provider
	 * on fly to accommodate looking at neighboring clients (but always should
	 * be same user/name)... so we only have wizard for providerId.
	 * 
	 * @return
	 */
	private boolean isClinicSetup() {
		boolean setupComplete = true;

		// Step 1: check keystore is unlocked -> setup keystore pwd
		ks = KeyStore.getInstance();
		if (ks.state() != KeyStore.State.UNLOCKED) {
			// request keystore setup
			startActivity(new Intent(OLD_UNLOCK_ACTION));
			return !setupComplete;
		}

		// Step 2: check first use -> launch setup
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		boolean firstRun = settings.getBoolean(getString(R.string.key_first_run), true);
		if (firstRun) {
			if (!isPrefsSet)
				setDefaultPreferences();
			else
				createView(REQUEST_DB_SETUP);
			return !setupComplete;
		}

		// SHOULD NEVER HAPPEN: check Db exists, it has a key and pwd
		File db = App.getApp().getDatabasePath(DbAdapter.DATABASE_NAME);
		String pwd = settings.getString(SQLCIPHER_KEY_NAME, "");
		SecretKeySpec key = getKey(SQLCIPHER_KEY_NAME);
		if (db == null || !db.exists() || pwd.equals("") || key == null) {
			// not first run, but have lost Db info! CATASTROPHE... SO RESET.
			Log.e(TAG, getString(R.string.error_lost_db_key));
			showCustomToast(getString(R.string.error_lost_db_key));
			WakefulIntentService.sendWakefulWork(this, WipeDataService.class);
			createView(LOADING);
			return !setupComplete;
		}

		return setupComplete;
	}

	@Override
	protected void onPause() {
		super.onPause();
		LocalBroadcastManager.getInstance(this).unregisterReceiver(onNotice);
	}

	protected BroadcastReceiver onNotice = new BroadcastReceiver() {
		public void onReceive(Context ctxt, Intent i) {
			refreshView();
		}
	};

	private void setDefaultPreferences() {
		// App Default Prefs
		PreferenceManager.setDefaultValues(this, R.xml.server_preferences, false);

		// Overwrite app defaults from config file or launch PrefsActivity
		if (!importConfigFile()) {
			isPrefsSet = true;
			startActivity(new Intent(mContext, PreferencesActivity.class));
		}
	}

	private boolean importConfigFile() {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
		File configFile = new File(FileUtils.getExternalRootDirectory(), CONFIG_FILE);
		File hiddenConfigFile = new File(FileUtils.getExternalRootDirectory(), HIDDEN_CONFIG_FILE);
		if (!configFile.exists())
			configFile = hiddenConfigFile;

		String[] booleanPrefs = { getString(R.string.key_client_auth), getString(R.string.key_use_saved_searches), getString(R.string.key_enable_activity_log), getString(R.string.key_show_settings_menu) };

		boolean success = false;
		if (configFile.exists()) {
			// Read text from file
			try {
				BufferedReader br = new BufferedReader(new FileReader(configFile));
				String line;

				while ((line = br.readLine()) != null) {

					int equal = line.indexOf("=");
					String prefName = line.substring(0, equal);
					String prefValue = line.substring(equal + 1);

					boolean booleanPref = false;
					for (String currentPref : booleanPrefs) {
						if (currentPref.equals(prefName))
							booleanPref = true;
					}
					if (booleanPref)
						settings.edit().putBoolean(prefName, Boolean.parseBoolean(prefValue)).commit();
					else
						settings.edit().putString(prefName, prefValue).commit();

					Log.e(TAG, "line is = " + line);
					Log.e(TAG, "prefName= " + prefName + " value=" + prefValue + " boolean=" + booleanPref);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			success = configFile.delete();
		}

		return success;
	}

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
		case REQUEST_PROVIDER_SETUP:
			mStep = ASK_NEW_ENTRY;
			mInstructionText.setText(R.string.provider_id);
			submitButton.setOnClickListener(mProviderListener);
			break;

		case REQUEST_PROVIDER_CHANGE:
			mStep = VERIFY_ENTRY;
			mInstructionText.setText(R.string.provider_id);
			submitButton.setOnClickListener(mProviderListener);
			break;

		// TODO! does not work yet b/c also have to rekey collectDb
		case REQUEST_DB_REKEY:
			mStep = VERIFY_ENTRY;
			mInstructionText.setText(R.string.sql_verify_pwd);
			submitButton.setOnClickListener(mSqlCipherPwdListener);
			break;

		case DB_LOCKED_ERROR:
		case REQUEST_DB_SETUP:
			mStep = ASK_NEW_ENTRY;
			isFreshInstall = true;
			mInstructionText.setText(R.string.set_sqlcipher_pwd);
			submitButton.setOnClickListener(mSqlCipherPwdListener);
			break;

		default:
			break;
		}
	}

	// TODO! Consider setting provider ID into its own class...
	private OnClickListener mProviderListener = new OnClickListener() {

		@Override
		public void onClick(View v) {

			String userEntry = mEditText.getText().toString();
			mEditText.setText("");
			if (userEntry.equals(""))
				mStep = ENTRY_ERROR;

			switch (mStep) {
			case VERIFY_ENTRY:
				if (userEntry.equals(mProviderId)) {
					mStep = ASK_NEW_ENTRY;
					mInstructionText.setText(R.string.change_provider_id);
				} else {
					showCustomToast("Incorrect Password");
				}
				break;
			case ASK_NEW_ENTRY:
				if (isAcceptable(userEntry)) {
					mFirstEntry = userEntry;
					userEntry = "";
					mInstructionText.setText(R.string.confirm_id);
					mStep = CONFIRM_ENTRY;
				} else {
					showCustomToast(mEditText.getText().toString() + "is not a valid Provider ID.  Please enter an ID with less than 10 numeric digits.");
				}
				break;
			case CONFIRM_ENTRY:
				if (mFirstEntry.equals(userEntry)) {
					setProviderId(userEntry);
				} else {
					mStep = ASK_NEW_ENTRY;
					mInstructionText.setText(R.string.provider_id);
					showCustomToast("Provider IDs do not match. Please enter a new Provider ID");
				}
				break;
			case ENTRY_ERROR:
			default:
				showCustomToast("Please Click on White Box to Enter a Provider ID.");
				mStep = VERIFY_ENTRY;
				break;
			}
		}
	};

	private void setProviderId(String userEntry) {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		settings.edit().putString(getString(R.string.key_provider), userEntry).commit();
		showCustomToast("Success. Provider ID has been set to:" + userEntry);

		refreshView();
	}

	private OnClickListener mSqlCipherPwdListener = new OnClickListener() {

		@Override
		public void onClick(View v) {

			String userEntry = mEditText.getText().toString();
			mEditText.setText("");
			if (isDecrypting || userEntry.equals(""))
				mStep = ENTRY_ERROR;

			switch (mStep) {
			case VERIFY_ENTRY:
				if (userEntry.equals(mDecryptedPwd)) {
					mStep = ASK_NEW_ENTRY;
					mInstructionText.setText(R.string.change_sqlcipher_pwd);
				} else {
					showCustomToast("Incorrect Password");
				}
				break;
			case ASK_NEW_ENTRY:
				if (isSecure(userEntry)) {
					mFirstEntry = userEntry;
					userEntry = "";
					mInstructionText.setText(R.string.confirm_sqlcipher_pwd);
					mStep = CONFIRM_ENTRY;
				} else {
					showCustomToast("Passwords must include a mix of numbers, upper and lower case letters and be at least six characters long.");
				}
				break;
			case CONFIRM_ENTRY:

				if (mFirstEntry.equals(userEntry)) {
					createView(LOADING);
					encryptDb(userEntry);
					toastCurrentSettings();
				} else {
					mStep = ASK_NEW_ENTRY;
					mInstructionText.setText(R.string.set_sqlcipher_pwd);
					showCustomToast("Passwords do not match. Please enter a new password");
				}
				break;
			case ENTRY_ERROR:
			default:
				showCustomToast(" Could not verify password. Please Click To Enter Password.");
				mStep = VERIFY_ENTRY;
				break;
			}
		}
	};

	private String unlockDb(final String keyName) {

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		final String encryptedPwd = settings.getString(SQLCIPHER_KEY_NAME, null);

		new AsyncTask<Void, Void, String>() {
			Exception error;

			@Override
			protected String doInBackground(Void... params) {
				try {

					isDecrypting = true;
					SecretKeySpec key = getKey(keyName);
					mDecryptedPwd = Crypto.decrypt(encryptedPwd, key);
					isDecrypting = false;
					return mDecryptedPwd;

				} catch (Exception e) {
					error = e;
					Log.e(TAG, "Error: " + e.getMessage(), e);
					return null;
				}
			}

			@Override
			protected void onPostExecute(String result) {
				if (error != null) {
					Log.e(TAG, "Error! Saved Password is unable to open the database!");
					// Toast.makeText(ClinicLauncherActivity.this, "Error: " +
					// error.getMessage(), Toast.LENGTH_LONG).show();
					finish();
				}

				// unlock the db
				boolean unlocked = DbAdapter.getInstance().open().unlock(result);
				if (!unlocked) {
					finish();
				} else {
					startActivity(new Intent(mContext, DashboardActivity.class));
					finish();
				}
			}

		}.execute();

		return mDecryptedPwd;
	}

	private void encryptDb(final String userEntry) {

		new AsyncTask<Void, Void, Boolean>() {
			Exception error;

			@Override
			protected Boolean doInBackground(Void... params) {

				try {
					// create new key
					SecretKey key = Crypto.generateKey();
					boolean success = ks.put(SQLCIPHER_KEY_NAME, key.getEncoded());
					Log.d(TAG, "Adding new key to keystore... success: " + success);

					// encrypt the userEntry
					String encryptedPwd = Crypto.encrypt(userEntry, key);

					// save the encryptedPwd to clinic
					SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
					Editor editor = settings.edit();
					editor.putString(SQLCIPHER_KEY_NAME, encryptedPwd);
					editor.commit();

					// save the encryptedPwd to collect
					Context mCollectCtx = null;
					try {
						mCollectCtx = App.getApp().createPackageContext("org.odk.collect.android", Context.CONTEXT_RESTRICTED);
						SharedPreferences collectPrefs = mCollectCtx.getSharedPreferences(SQLCIPHER_PREFS_NAME, MODE_PRIVATE);
						collectPrefs.edit().putString(SQLCIPHER_KEY_NAME, encryptedPwd).commit();
					} catch (NameNotFoundException e) {
						e.printStackTrace();
					}

					// encrypt the Db
					if (isFreshInstall)
						success = success & DbAdapter.getInstance().open().unlock(userEntry);
					else
						success = success & DbAdapter.getInstance().open().rekeyDb(userEntry);

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
					Log.d(TAG, "Successfully encrypted database with new password.  Database is now open.");
					final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
					settings.edit().putBoolean(getString(R.string.key_first_run), false).commit();

					startActivity(new Intent(mContext, DashboardActivity.class));
					finish();

				} else {
					if (error != null)
						Log.e(TAG, "Error adding new SQLCipher key to the Keystore!" + error.getMessage());
					else
						Log.e(TAG, "Failed to encrypt database with new password. Please try again.");

					// can only reopen at this point...
					finish();
				}

			}
		}.execute();
	}

	private SecretKeySpec getKey(String keyName) {
		if (ks == null)
			ks = KeyStore.getInstance();
		byte[] keyBytes = ks.get(keyName);
		if (keyBytes == null) {
			Log.w(TAG, "Encryption key not found in keystore: " + keyName);
			return null;
		}
		SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
		Log.d(TAG, String.format("\t%s: %s", keyName, new BigInteger(keyBytes).toString()));
		return key;
	}

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

	private static boolean isAcceptable(String userEntry) {

		// CHANGED: accepting all characters now
		// long l = Long.valueOf(userEntry);
		// if (l < 0 || l > Integer.MAX_VALUE)
		// return false;

		return true;
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

	// case VERIFY_PWD: //technically, this should never happen?
	// if (userEntry.equals(mDecryptedPwd)) {
	// mCla.unlock(userEntry);
	// System.gc();
	// } else {
	// showCustomToast("Incorrect Password");
	// }
	// break;

}
