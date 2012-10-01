package com.alphabetbloc.clinic.utilities;

import com.alphabetbloc.clinic.R;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 *
 */
public class WebUtils {
	public static final String TAG = WebUtils.class.getSimpleName();
	// POST
	public static final String INSTANCE_UPLOAD_URL = "/moduleServlet/xformshelper/xfhFormUpload";

	// SECURE GET
	public static final String PATIENT_DOWNLOAD_URL = "/module/odkconnector/download/patients.form";

	// INSECURE GET
	public static final String FORMLIST_DOWNLOAD_URL = "/moduleServlet/xformshelper/xfhFormList?type=odk_clinic";
	public static final String FORM_DOWNLOAD_URL = "/moduleServlet/xformshelper/xfhFormDownload?type=odk_clinic";

	private static String mServerUrl;
//	private static String mUserNamePwd;
//	private static String mUserNamePwdUpload;

	private static String mUsername;
	private static String mPassword;

	private static void buildUrls() {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		mServerUrl = settings.getString(App.getApp().getString(R.string.key_server), App.getApp().getString(R.string.default_server));
//		String username = settings.getString(App.getApp().getString(R.string.key_username), App.getApp().getString(R.string.default_username));
//		String password = settings.getString(App.getApp().getString(R.string.key_password), App.getApp().getString(R.string.default_password));
//		mUserNamePwd = "&uname=" + username + "&pw=" + password;
//		mUserNamePwdUpload = "?uname=" + username + "&pw=" + password;
	}

	public static String getFormUploadUrl() {
		buildUrls();

		StringBuilder uploadUrl = new StringBuilder(mServerUrl);
		uploadUrl.append(INSTANCE_UPLOAD_URL);
		uploadUrl.append("?uname=").append(getServerUsername());
		uploadUrl.append("&pw=").append(getServerPassword());
		
		// uploadUrl.append(mUserNamePwdUpload);
		Log.d("WebUtils", "FormUpload URL= " + uploadUrl.toString());
		return uploadUrl.toString();
	}

	public static String getPatientDownloadUrl() {
		buildUrls();

		Log.d("WebUtils", "PatientDownload URL= " + mServerUrl + PATIENT_DOWNLOAD_URL);
		// SharedPreferences settings =
		// PreferenceManager.getDefaultSharedPreferences(App.getApp());
		// String username =
		// settings.getString(App.getApp().getString(R.string.key_username),
		// App.getApp().getString(R.string.default_username));
		// String password =
		// settings.getString(App.getApp().getString(R.string.key_password),
		// App.getApp().getString(R.string.default_password));
		// boolean savedSearch =
		// settings.getBoolean(App.getApp().getString(R.string.key_use_saved_searches),
		// false);
		// int cohort =
		// Integer.valueOf(settings.getString(App.getApp().getString(R.string.key_saved_search),
		// "0"));
		// int program =
		// Integer.valueOf(settings.getString(App.getApp().getString(R.string.key_program),
		// "0"));
		//
		// String url = mServerUrl + PATIENT_DOWNLOAD_URL + username + password
		// + savedSearch + cohort + program;
		return mServerUrl + PATIENT_DOWNLOAD_URL;
	}

	public static String getFormListDownloadUrl() {
		buildUrls();
		StringBuilder formlistUrl = new StringBuilder(mServerUrl);
		formlistUrl.append(FORMLIST_DOWNLOAD_URL);
		// formlistUrl.append(mUserNamePwd);
		Log.d("WebUtils", "FormList URL= " + formlistUrl.toString());
		return formlistUrl.toString();
	}

	public static String getFormDownloadUrl() {
		buildUrls();
		StringBuilder formUrl = new StringBuilder(mServerUrl);
		formUrl.append(FORM_DOWNLOAD_URL);
		// formUrl.append(mUserNamePwd);
		Log.d("WebUtils", "FormDownload URL= " + formUrl.toString());
		return formUrl.toString();
	}

	public static String getServerUsername() {
		if (mUsername == null)
			getServerCredentials();

		return mUsername;
	}

	public static String getServerPassword() {
		if (mPassword == null)
			getServerCredentials();

		return mPassword;
	}

	private static void getServerCredentials() {
		final AccountManager am = AccountManager.get(App.getApp());
		Account[] accounts = am.getAccountsByType(App.getApp().getString(R.string.app_account_type));

		Log.e(TAG, "accounts.length =" + accounts.length);
		if (accounts.length <= 0) {
			
			Log.e(TAG, "no accounts have been set up");
		} else {

			mUsername = accounts[0].name;
			String encPwd = am.getPassword(accounts[0]);
			mPassword = EncryptionUtil.decryptString(encPwd);
		}

	}

}
