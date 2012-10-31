package com.alphabetbloc.clinic.ui.admin;

import java.io.IOException;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.listeners.SyncDataListener;
import com.alphabetbloc.clinic.tasks.CheckConnectivityTask;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.EncryptionUtil;
import com.alphabetbloc.clinic.utilities.NetworkUtils;
import com.alphabetbloc.clinic.utilities.UiUtils;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class SetupAccountActivity extends Activity implements SyncDataListener {

	// setAccountAuthenticatorResult(android.os.Bundle);
	private static final String TAG = SetupAccountActivity.class.getSimpleName();

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
	private Button mOfflineSetupButton;
	private ImageView mCenterImage;
	private Context mContext;
	private boolean mExit = false;

	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.account_setup);
		mContext = this;

		// dynamic views
		mInstructionText = (TextView) findViewById(R.id.instruction);
		mSubmitButton = (Button) findViewById(R.id.submit_button);
		mSubmitButton.setText(getString(R.string.submit));
		mSubmitButton.setOnClickListener(mSubmitListener);
		mUserText = (EditText) findViewById(R.id.edittext_username);
		mPwdText = (EditText) findViewById(R.id.edittext_password);
		mCenterImage = (ImageView) findViewById(R.id.center_image);
		mOfflineSetupButton = (Button) findViewById(R.id.offline_setup_button);
		mOfflineSetupButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				mExit = false;
				setupNewAccount();
				// addAccount(mNewUser, mNewPwd, false);
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean firstRun = prefs.getBoolean(getString(R.string.key_first_run), true);
		boolean importFromConfig = getIntent().getBooleanExtra(USE_CONFIG_FILE, false);
		if (firstRun)
			createView(REQUEST_CREDENTIAL_SETUP);
		else if (importFromConfig) {
			mNewUser = prefs.getString(getString(R.string.key_username), getString(R.string.default_username));
			mNewPwd = prefs.getString(getString(R.string.key_password), getString(R.string.default_password));
			mExit = true;
			setupNewAccount();
			// addAccount(username, password, true);
		} else if (NetworkUtils.getServerUsername() != null)
			createView(REQUEST_CREDENTIAL_CHANGE);
		else
			createView(REQUEST_CREDENTIAL_SETUP);
	}

	// private void importFromConfigFile() {
	// SharedPreferences prefs =
	// PreferenceManager.getDefaultSharedPreferences(this);
	// String username = prefs.getString(getString(R.string.key_username),
	// getString(R.string.default_username));
	// String password = prefs.getString(getString(R.string.key_password),
	// getString(R.string.default_password));
	// addAccount(username, password);
	// }

	private void createView(int view) {
		// if not loading, set appropriate buttons/text
		switch (view) {
		// changing credentials
		case REQUEST_CREDENTIAL_CHANGE:
			mStep = VERIFY_ENTRY;
			mInstructionText.setText(R.string.auth_server_verify_account);
			mUserText.setVisibility(View.VISIBLE);
			mCurrentUser = NetworkUtils.getServerUsername();
			mUserText.setText(mCurrentUser);
			mPwdText.setVisibility(View.VISIBLE);
			mCurrentPwd = NetworkUtils.getServerPassword();
			mSubmitButton.setVisibility(View.VISIBLE);
			mSubmitButton.setText(getString(R.string.submit));
			mOfflineSetupButton.setVisibility(View.GONE);
			mCenterImage.setVisibility(View.GONE);
			break;

		// setting up new credentials
		case REQUEST_CREDENTIAL_SETUP:
			mStep = ASK_NEW_ENTRY;
			mInstructionText.setText(R.string.auth_server_account_setup);
			mUserText.setVisibility(View.VISIBLE);
			mPwdText.setVisibility(View.VISIBLE);
			mSubmitButton.setVisibility(View.VISIBLE);
			mSubmitButton.setText(getString(R.string.submit));
			mOfflineSetupButton.setVisibility(View.GONE);
			mCenterImage.setVisibility(View.GONE);
			((ProgressBar) findViewById(R.id.progress_wheel)).setVisibility(View.GONE);
			break;

		case CREDENTIAL_ENTRY_ERROR:
			mStep = ASK_NEW_ENTRY;
			mInstructionText.setText(getString(R.string.auth_server_error_login));
			mUserText.setVisibility(View.VISIBLE);
			mUserText.setText(mCurrentUser);
			mPwdText.setVisibility(View.VISIBLE);
			mSubmitButton.setVisibility(View.VISIBLE);
			mSubmitButton.setText(R.string.auth_try_again);
			mOfflineSetupButton.setVisibility(View.VISIBLE);
			mOfflineSetupButton.setText(R.string.auth_dont_verify);
			mCenterImage.setVisibility(View.INVISIBLE);
			((ProgressBar) findViewById(R.id.progress_wheel)).setVisibility(View.GONE);
			break;

		case LOADING:
			mInstructionText.setText(getString(R.string.auth_verifying_server_account));
			mUserText.setVisibility(View.GONE);
			mPwdText.setVisibility(View.GONE);
			mSubmitButton.setVisibility(View.GONE);
			mOfflineSetupButton.setVisibility(View.GONE);
			mCenterImage.setVisibility(View.GONE);
			((ProgressBar) findViewById(R.id.progress_wheel)).setVisibility(View.VISIBLE);
			break;

		case FINISHED:
			mStep = FINISHED;
			mInstructionText.setText(getString(R.string.auth_server_setup_complete));
			mUserText.setVisibility(View.GONE);
			mPwdText.setVisibility(View.GONE);
			mSubmitButton.setVisibility(View.VISIBLE);
			mSubmitButton.setText(getString(R.string.finish));
			mOfflineSetupButton.setVisibility(View.GONE);
			mCenterImage.setVisibility(View.GONE);
			((ProgressBar) findViewById(R.id.progress_wheel)).setVisibility(View.GONE);
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
					UiUtils.toastAlert(mContext, getString(R.string.auth_error_title), getString(R.string.auth_server_verify_error));
				break;

			case ASK_NEW_ENTRY:
				if (isAcceptable(userEntry))
					checkServerCredentials(userEntry, pwdEntry);
				else
					// TODO! does this work, or do you need String.format()?
					UiUtils.toastAlert(mContext, getString(R.string.auth_error_title), getString((R.string.auth_invalid_username), mUserText.getText().toString()));
				break;
			case FINISHED:
				setResult(RESULT_OK);
				finish();
				break;
			case ENTRY_ERROR:
			default:
				UiUtils.toastAlert(mContext, getString(R.string.auth_error_title), getString(R.string.auth_empty_entry));
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

	private void setupNewAccount() {
		createView(LOADING);
		AccountManager am = AccountManager.get(this);
		// STEP 1: if old account exists, delete and replace with new account
		Account[] accounts = am.getAccountsByType(getString(R.string.app_account_type));
		Log.e(TAG, "about to remove old accounts number=" + accounts.length);

		if (accounts.length > 0) {
			for (Account a : accounts) {
				myFuture = am.removeAccount(a, myCallback, myHandler);
			}
		} else {
			removedAccount = true;
			myCallback.run(null);
		}

	}

	private final Handler myHandler = new Handler();
	private AccountManagerFuture<Boolean> myFuture = null;
	private boolean removedAccount = false;
	private AccountManagerCallback<Boolean> myCallback = new AccountManagerCallback<Boolean>() {
		@Override
		public void run(final AccountManagerFuture<Boolean> amf) {
			if (amf != null) {
				try {
					removedAccount = myFuture.getResult();
					Log.e(TAG, "We got the future! with removedAccount=" + removedAccount);
				} catch (Exception e) {
					// handle error
				}
			}
			if (removedAccount) {

				addAccount(mNewUser, mNewPwd, mExit);

			} else {

				Log.e(TAG, "Could not delete the account for some reason...");
				if (mExit)
					finish();
				else
					createView(REQUEST_CREDENTIAL_SETUP);
			}
		}
	};

	private boolean addAccount(String username, String password, boolean exit) {
		// STEP 2: AddNewAccount
		Log.e(TAG, "about to and add new account with u=" + username + " p=" + password);

		final Account account = new Account(username, getString(R.string.app_account_type));
		String encPwd = EncryptionUtil.encryptString(password);
		AccountManager am = AccountManager.get(this);
		boolean accountCreated = am.addAccountExplicitly(account, encPwd, null);

		// TODO? is this necessary... does Android ever delete credentials?
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		prefs.edit().putString(getString(R.string.key_username), username).commit();
		prefs.edit().putString(getString(R.string.key_password), encPwd).commit();

		if (accountCreated) {
			Log.e(TAG, "account was created");

			String authority = getString(R.string.app_provider_authority);

			// Set up sync (IF global settings background data & auto-sync)
			ContentResolver.setIsSyncable(account, authority, 1);
			ContentResolver.setSyncAutomatically(account, authority, true);

			String interval = prefs.getString(getString(R.string.key_max_refresh_seconds), getString(R.string.default_max_refresh_seconds));
			ContentResolver.addPeriodicSync(account, authority, new Bundle(), Integer.valueOf(interval));

			// Pass account back to account manager
			Bundle extras = getIntent().getExtras();
			boolean launchedFromAccountMgr = getIntent().getBooleanExtra(LAUNCHED_FROM_ACCT_MGR, false);
			if (extras != null && launchedFromAccountMgr) {

				Log.e(TAG, "launched from the account manager...");
				AccountAuthenticatorResponse response = extras.getParcelable(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);
				Bundle result = new Bundle();
				result.putString(AccountManager.KEY_ACCOUNT_NAME, username);
				result.putString(AccountManager.KEY_ACCOUNT_TYPE, getString(R.string.app_account_type));
				response.onResult(result);

			} else {
				Log.e(TAG, "not launched from the account manager");
				setResult(RESULT_OK);
			}

			if (exit) {
				Log.e(TAG, "Exit called... so exiting now");
				UiUtils.toastMessage(mContext, null, getString(R.string.auth_server_setup_complete), 0, Gravity.CENTER, Toast.LENGTH_SHORT);
				finish();
			} else {
				Log.e(TAG, "Exit NOT called... so creating the finished view");
				createView(FINISHED);
			}
		} else {
			Log.e(TAG, "Account Setup Failed");
			createView(REQUEST_CREDENTIAL_SETUP);
		}

		return accountCreated;
	}

	@Override
	public void syncComplete(String result, SyncResult syncResult) {
		Log.e(TAG, "syncComplete...! but result=" + result);
		if (Boolean.valueOf(result)) {
			mExit = false;
			setupNewAccount();
			// addAccount(mNewUser, mNewPwd, false);
		} else {
			Log.e(TAG, "Credential could not be verified");
			createView(CREDENTIAL_ENTRY_ERROR);
		}
	}

	private static boolean isAcceptable(String userEntry) {
		// CHANGED: accepting all characters now...
		// long l = Long.valueOf(userEntry);
		// if (l < 0 || l > Integer.MAX_VALUE)
		// return false;
		return true;
	}

	// Bundle params = new Bundle();
	// params.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, false);
	// params.putBoolean(ContentResolver.SYNC_EXTRAS_DO_NOT_RETRY,
	// false);
	// params.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
	// params.putBoolean(ContentResolver.SYNC_EXTRAS_FORCE, true);
	// ContentResolver.requestSync(account, authority, params);
}
