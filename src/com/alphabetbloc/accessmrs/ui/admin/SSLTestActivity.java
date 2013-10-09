/*
 * Copyright (C) 2012 Louis Fazen
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.alphabetbloc.accessmrs.ui.admin;

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

import com.alphabetbloc.accessmrs.listeners.SyncDataListener;
import com.alphabetbloc.accessmrs.tasks.CheckConnectivityTask;
import com.alphabetbloc.accessmrs.utilities.App;
import com.alphabetbloc.accessmrs.utilities.NetworkUtils;
import com.alphabetbloc.accessmrs.R;
/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 *
 */
public class SSLTestActivity extends BaseAdminActivity implements SyncDataListener {

	// private static final String TAG =
	// TestSslConnectionActivity.class.getSimpleName();
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
		userText.setText(NetworkUtils.getServerUsername());
		passwordText = (EditText) findViewById(R.id.password_edittext);
		passwordText.setText(NetworkUtils.getServerPassword());
		
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
		resultText.setVisibility(View.GONE);
		mProgress.setVisibility(View.VISIBLE);
		String server = serverText.getText().toString() + NetworkUtils.PATIENT_DOWNLOAD_URL;
		String username = userText.getText().toString();
		String password = passwordText.getText().toString();

		CheckConnectivityTask verifyWithServer = new CheckConnectivityTask();

		verifyWithServer.setServerCredentials(server, username, password);
		verifyWithServer.setSyncListener(this);
		verifyWithServer.execute(new SyncResult());
	}

	@Override
	public void syncComplete(String result, SyncResult syncResult) {
		resultText.setVisibility(View.VISIBLE);
		mProgress.setVisibility(View.GONE);
		
		if (Boolean.valueOf(result)) {
			resultText.setTextColor(getResources().getColor(R.color.completed));
			resultText.setText(getString(R.string.ssl_test_success));
		} else {
			resultText.setTextColor(getResources().getColor(R.color.priority));
			resultText.setText(getString(R.string.ssl_test_failure));
		}
	}
}
