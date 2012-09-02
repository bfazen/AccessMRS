package org.odk.clinic.android.utilities;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;

import org.odk.clinic.android.R;
import org.odk.clinic.android.activities.PreferencesActivity;
import org.odk.clinic.android.openmrs.Constants;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class WebUtils {

	private static String mServerUrl;
	private static String mUserNamePwd;

	/**
	 * Taken from ODK Collect UrlUtils
	 * 
	 * @param url
	 * @return true if url is of x-www-form-urlencoded MIME type with utf-8
	 *         encoding
	 */
	public static boolean isValidUrl(String url) {

		try {
			new URL(URLDecoder.decode(url, "utf-8"));
			return true;
		} catch (MalformedURLException e) {
			return false;
		} catch (UnsupportedEncodingException e) {
			return false;
		}

	}

	private static void buildUrls() {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		mServerUrl = settings.getString(PreferencesActivity.KEY_SERVER, App.getApp().getString(R.string.default_server));
		String username = settings.getString(PreferencesActivity.KEY_USERNAME, App.getApp().getString(R.string.default_username));
		String password = settings.getString(PreferencesActivity.KEY_PASSWORD, App.getApp().getString(R.string.default_password));
		mUserNamePwd = "&uname=" + username + "&pw=" + password;
	}

	public static String getFormUploadUrl() {
		if (mServerUrl == null || mUserNamePwd == null)
			buildUrls();

		StringBuilder uploadUrl = new StringBuilder(mServerUrl);
		uploadUrl.append(Constants.INSTANCE_UPLOAD_URL);
		uploadUrl.append(mUserNamePwd);
		return uploadUrl.toString();
	}

	public static String getPatientDownloadUrl() {
		if (mServerUrl == null)
			buildUrls();

		return mServerUrl + Constants.PATIENT_DOWNLOAD_URL;
	}

	public static String getFormListDownloadUrl() {
		if (mServerUrl == null || mUserNamePwd == null)
			buildUrls();

		StringBuilder formlistUrl = new StringBuilder(mServerUrl);
		formlistUrl.append(Constants.FORMLIST_DOWNLOAD_URL);
		formlistUrl.append(mUserNamePwd);
		return formlistUrl.toString();
	}

	public static String getFormDownloadUrl() {
		if (mServerUrl == null || mUserNamePwd == null)
			buildUrls();

		StringBuilder formUrl = new StringBuilder(mServerUrl);
		formUrl.append(Constants.FORM_DOWNLOAD_URL);
		formUrl.append(mUserNamePwd);
		return formUrl.toString();
	}

}
