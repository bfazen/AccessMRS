package org.odk.clinic.android.utilities;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;

import org.odk.clinic.android.R;
import org.odk.clinic.android.activities.PreferencesActivity;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class WebUtils {

	public static final String USER_DOWNLOAD_URL = "/moduleServlet/xforms/userDownload";

	public static final String COHORT_DOWNLOAD_URL = "/module/odkconnector/download/cohort.form";

	public static final String PATIENT_DOWNLOAD_URL = "/module/odkconnector/download/patients.form";

	public static final String FORMLIST_DOWNLOAD_URL = "/moduleServlet/xformshelper/xfhFormList?type=odk_clinic";

	public static final String FORM_DOWNLOAD_URL = "/moduleServlet/xformshelper/xfhFormDownload?type=odk_clinic";

	public static final String INSTANCE_UPLOAD_URL = "/moduleServlet/xformshelper/xfhFormUpload";
//	public static final String INSTANCE_UPLOAD_URL = "/moduleServlet/xformshelper/fileUpload"; //old

	private static String mServerUrl;
	private static String mUserNamePwd;
	private static String mUserNamePwdUpload;

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
		mServerUrl = settings.getString(App.getApp().getString(R.string.key_server), App.getApp().getString(R.string.default_server));
		String username = settings.getString(App.getApp().getString(R.string.key_username), App.getApp().getString(R.string.default_username));
		String password = settings.getString(App.getApp().getString(R.string.key_password), App.getApp().getString(R.string.default_password));
		mUserNamePwd = "&uname=" + username + "&pw=" + password;
		mUserNamePwdUpload = "?uname=" + username + "&pw=" + password;
	}

	public static String getFormUploadUrl() {
		buildUrls();
        
		StringBuilder uploadUrl = new StringBuilder(mServerUrl);
		uploadUrl.append(INSTANCE_UPLOAD_URL);
		uploadUrl.append(mUserNamePwdUpload);
		return uploadUrl.toString();
	}

	public static String getPatientDownloadUrl() {
		buildUrls();
		return mServerUrl + PATIENT_DOWNLOAD_URL;
	}

	public static String getFormListDownloadUrl() {
		buildUrls();
		StringBuilder formlistUrl = new StringBuilder(mServerUrl);
		formlistUrl.append(FORMLIST_DOWNLOAD_URL);
		formlistUrl.append(mUserNamePwd);
		return formlistUrl.toString();
	}

	public static String getFormDownloadUrl() {
		buildUrls();
		StringBuilder formUrl = new StringBuilder(mServerUrl);
		formUrl.append(FORM_DOWNLOAD_URL);
		formUrl.append(mUserNamePwd);
		return formUrl.toString();
	}

}
