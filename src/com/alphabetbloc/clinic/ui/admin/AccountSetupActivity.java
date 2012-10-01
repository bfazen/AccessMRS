package com.alphabetbloc.clinic.ui.admin;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.listeners.SyncDataListener;
import com.alphabetbloc.clinic.tasks.CheckConnectivityTask;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.EncryptionUtil;
import com.alphabetbloc.clinic.utilities.WebUtils;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.Bundle;
import android.preference.PreferenceManager;
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

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 *
 */
public class AccountSetupActivity extends Activity implements SyncDataListener {

	// setAccountAuthenticatorResult(android.os.Bundle);
	private static final String TAG = AccountSetupActivity.class.getSimpleName();

	// Intents
	public static final String USE_CONFIG_FILE = "use_config_file_defaults";
	public static final String LAUNCHED_FROM_ACCT_MGR = "launched_from_account_manager";

	// views
	protected static final int REQUEST_CREDENTIAL_CHANGE = 1;
	protected static final int REQUEST_CREDENTIAL_SETUP = 2;
	protected static final int CREDENTIAL_ENTRY_ERROR = 3;
	protected static final int LOADING = 4;
	protected static final int FINISHED = 5;

	// buttons
	protected static final int VERIFY_ENTRY = 1;
	protected static final int ASK_NEW_ENTRY = 2;
	protected static final int ENTRY_ERROR = 3;

	private TextView mInstructionText;
	private EditText mUserText;
	private EditText mPwdText;
	private String mCurrentUser;
	private String mCurrentPwd;
	private int mStep;
	private Button mSubmitButton;
	private boolean mImportFromConfig;
	private String mNewUser;
	private String mNewPwd;

	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.account_setup);
		mImportFromConfig = getIntent().getBooleanExtra(USE_CONFIG_FILE, false);

		// dynamic views
		mInstructionText = (TextView) findViewById(R.id.instruction);
		mSubmitButton = (Button) findViewById(R.id.submit_button);
		mSubmitButton.setText(getString(R.string.submit));
		mSubmitButton.setOnClickListener(mSubmitListener);
		mUserText = (EditText) findViewById(R.id.edittext_username);
		mPwdText = (EditText) findViewById(R.id.edittext_password);
	}

	@Override
	protected void onResume() {
		super.onResume();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean firstRun = prefs.getBoolean(getString(R.string.key_first_run), true);
		mCurrentUser = WebUtils.getServerUsername();
		mCurrentPwd = WebUtils.getServerPassword();

		if (mImportFromConfig)
			importFromConfigFile();
		else if (firstRun)
			createView(REQUEST_CREDENTIAL_SETUP);
		else if (mCurrentUser != null && mCurrentPwd != null)
			createView(REQUEST_CREDENTIAL_CHANGE);
		else
			createView(REQUEST_CREDENTIAL_SETUP);
	}

	private void importFromConfigFile() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String username = prefs.getString(getString(R.string.key_username), getString(R.string.default_username));
		String password = prefs.getString(getString(R.string.key_password), getString(R.string.default_password));
		addAccount(username, password);
	}

	private void createView(int view) {
		// if not loading, set appropriate buttons/text
		switch (view) {
		// changing credentials
		case REQUEST_CREDENTIAL_CHANGE:
			mStep = VERIFY_ENTRY;
			mUserText.setText(mCurrentUser);
			mInstructionText.setText(R.string.auth_server_verify_account);
			break;

		// setting up new credentials
		case REQUEST_CREDENTIAL_SETUP:
			mStep = ASK_NEW_ENTRY;
			mInstructionText.setText(R.string.auth_server_account_setup);
			break;

		case CREDENTIAL_ENTRY_ERROR:
			mStep = ASK_NEW_ENTRY;
			((ProgressBar) findViewById(R.id.progress_wheel)).setVisibility(View.GONE);
			mSubmitButton.setVisibility(View.VISIBLE);
			mSubmitButton.setText(R.string.submit);
			mUserText.setVisibility(View.VISIBLE);
			mPwdText.setVisibility(View.VISIBLE);
			mInstructionText.setText(getString(R.string.auth_server_error_login));
			break;

		case LOADING:
			((ProgressBar) findViewById(R.id.progress_wheel)).setVisibility(View.VISIBLE);
			mSubmitButton.setVisibility(View.GONE);
			mUserText.setVisibility(View.GONE);
			mPwdText.setVisibility(View.GONE);
			mInstructionText.setText(getString(R.string.auth_verifying_server_account));
			break;

		case FINISHED:
			mStep = FINISHED;
			mSubmitButton.setVisibility(View.VISIBLE);
			mSubmitButton.setText(getString(R.string.finish));
			((ProgressBar) findViewById(R.id.progress_wheel)).setVisibility(View.GONE);
			mInstructionText.setText(getString(R.string.auth_server_setup_complete));
			break;

		default:
			break;
		}
	}

	private OnClickListener mSubmitListener = new OnClickListener() {

		@Override
		public void onClick(View v) {

			String userEntry = mUserText.getText().toString();
			String pwdEntry = mPwdText.getText().toString();
			mPwdText.setText("");

			if ((userEntry.equals("") || pwdEntry.equals("")) && (mStep != FINISHED))
				mStep = ENTRY_ERROR;

			switch (mStep) {
			case VERIFY_ENTRY:
				if (userEntry.equals(mCurrentUser) && pwdEntry.equals(mCurrentPwd))
					createView(REQUEST_CREDENTIAL_SETUP);
				else
					showCustomToast(getString(R.string.auth_server_verify_error));
				break;

			case ASK_NEW_ENTRY:
				if (isAcceptable(userEntry))
					checkServerCredentials(userEntry, pwdEntry);
				else
					showCustomToast(mUserText.getText().toString() + "is not a valid Username.  Please enter an id with only letters and numbers.");
				break;
			case FINISHED:
				finish();
				break;
			case ENTRY_ERROR:
			default:
				showCustomToast("Please Click on White Box to Enter Username and Password.");
				break;
			}
		}
	};

	private void checkServerCredentials(String username, String password) {
		createView(LOADING);

		mNewUser = username;
		mNewPwd = password;

		CheckConnectivityTask verifyWithServer = new CheckConnectivityTask();
		verifyWithServer.setServerCredentials(username, password);
		verifyWithServer.setSyncListener(this);
		verifyWithServer.execute(new SyncResult());
	}

	private void addAccount(String username, String password) {

		AccountManager am = AccountManager.get(this);

		//if old account exists, delete and replace with new account
		Account[] accounts = am.getAccountsByType(getString(R.string.app_account_type));
		for(Account a : accounts){
			am.removeAccount(a, null, null);
		}

		final Account account = new Account(username, getString(R.string.app_account_type));
		String encPwd = EncryptionUtil.encryptString(password);

		// TODO! is this necessary... does Android ever delete credentials?
		// saving it here just in case?
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		prefs.edit().putString(getString(R.string.key_username), encPwd).commit();
		prefs.edit().putString(getString(R.string.key_password), encPwd).commit();

		boolean accountCreated = am.addAccountExplicitly(account, encPwd, null);

		Bundle extras = getIntent().getExtras();
		if (extras != null && accountCreated) {

			ContentResolver.setIsSyncable(account, getString(R.string.app_provider_authority), 1);
			ContentResolver.setSyncAutomatically(account, getString(R.string.app_provider_authority), true);
			// TODO! do i always need to do this or just if launched from
			// AcctMgr?
			// Pass the new account back to the account mgr
			boolean launchedFromAccountMgr = getIntent().getBooleanExtra(LAUNCHED_FROM_ACCT_MGR, true);
			if (launchedFromAccountMgr) {
				AccountAuthenticatorResponse response = extras.getParcelable(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);
				Bundle result = new Bundle();
				result.putString(AccountManager.KEY_ACCOUNT_NAME, username);
				result.putString(AccountManager.KEY_ACCOUNT_TYPE, getString(R.string.app_account_type));
				response.onResult(result);
			} else {
				Log.e(TAG, "about to set the result to OK and finish");
				setResult(RESULT_OK);
			}

			// finish();
		}
	}

	private static boolean isAcceptable(String userEntry) {

		// CHANGED: accepting all characters now, but could have this be just
		// numbers etc.
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

	@Override
	public void syncComplete(String result, SyncResult syncResult) {

		if (Boolean.valueOf(result)) {
			addAccount(mNewUser, mNewPwd);
			createView(FINISHED);

		} else {
			mNewUser = null;
			mNewPwd = null;
			createView(CREDENTIAL_ENTRY_ERROR);
		}
	}

	@Override
	public void sslSetupComplete(String result, SyncResult syncResult) {
		// do nothing
	}

	@Override
	public void uploadComplete(String result) {
		// do nothing

	}

	@Override
	public void downloadComplete(String result) {
		// do nothing

	}

	@Override
	public void progressUpdate(String message, int progress, int max) {
		// do nothing

	}

}
