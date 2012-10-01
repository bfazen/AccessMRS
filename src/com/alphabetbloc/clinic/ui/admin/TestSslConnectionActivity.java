package com.alphabetbloc.clinic.ui.admin;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.listeners.SyncDataListener;
import com.alphabetbloc.clinic.tasks.CheckConnectivityTask;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.WebUtils;
/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 *
 */
public class TestSslConnectionActivity extends Activity implements SyncDataListener {

	// private static final String TAG =
	// TestSslConnectionActivity.class.getSimpleName();
	protected String mStorePassword;
	private TextView resultText;
	private EditText serverText;
	private EditText userText;
	private EditText passwordText;
	private ProgressBar mProgress;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.test_ssl);
		resultText = (TextView) findViewById(R.id.result_text);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		serverText = (EditText) findViewById(R.id.server_edittext);
		serverText.setText(settings.getString(App.getApp().getString(R.string.key_server), App.getApp().getString(R.string.default_server)));
		userText = (EditText) findViewById(R.id.username_edittext);
		userText.setText(WebUtils.getServerUsername());
		passwordText = (EditText) findViewById(R.id.password_edittext);
		passwordText.setText(WebUtils.getServerPassword());
		
		mProgress = (ProgressBar) findViewById(R.id.progress_wheel);

		Button httpClientConnectButton = (Button) findViewById(R.id.http_client_connect_button);
		httpClientConnectButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				testSslConnection();
			}
		});
	}

	private void testSslConnection() {
		
		mProgress.setVisibility(View.VISIBLE);
		String server = serverText.getText().toString() + WebUtils.PATIENT_DOWNLOAD_URL;
		String username = userText.getText().toString();
		String password = passwordText.getText().toString();

		CheckConnectivityTask verifyWithServer = new CheckConnectivityTask();

		verifyWithServer.setServerCredentials(server, username, password);
		verifyWithServer.setSyncListener(this);
		verifyWithServer.execute(new SyncResult());
	}

	@Override
	public void syncComplete(String result, SyncResult syncResult) {

		mProgress.setVisibility(View.GONE);
		
		if (Boolean.valueOf(result)) {
			resultText.setTextColor(getResources().getColor(R.color.completed));
			resultText.setText(getString(R.string.ssl_test_success));
		} else {
			resultText.setText(getResources().getColor(R.color.priority));
			resultText.setText(getString(R.string.ssl_test_failure));
		}
	}

	@Override
	public void sslSetupComplete(String result, SyncResult syncResult) {
		// Do Nothing

	}

	@Override
	public void uploadComplete(String result) {
		// Do Nothing

	}

	@Override
	public void downloadComplete(String result) {
		// Do Nothing

	}

	@Override
	public void progressUpdate(String message, int progress, int max) {
		// Do Nothing

	}
}
