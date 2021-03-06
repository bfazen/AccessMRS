/*******************************************************************************
 * Copyright 2010 Sam Steele 
 * Copyright 2012 Louis Fazen
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.alphabetbloc.accessmrs.services;

import com.alphabetbloc.accessmrs.ui.admin.SetupAccountActivity;
import com.alphabetbloc.accessmrs.utilities.App;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

/**
 * @author Sam Steele
 * @author Louis Fazen (louis.fazen@gmail.com)
 *
 */
public class AccountAuthenticatorService extends Service {
	private static final String TAG = AccountAuthenticatorService.class.getSimpleName();
	private static AccountAuthenticatorImpl mAccountAuthenticator = null;

	public AccountAuthenticatorService() {
		super();
	}

	@Override
	public IBinder onBind(Intent intent) {
		IBinder ret = null;
		if (intent.getAction().equals(android.accounts.AccountManager.ACTION_AUTHENTICATOR_INTENT))
			ret = getAuthenticator().getIBinder();
		return ret;
	}

	private AccountAuthenticatorImpl getAuthenticator() {
		if (mAccountAuthenticator == null)
			mAccountAuthenticator = new AccountAuthenticatorImpl(this);
		return mAccountAuthenticator;
	}

	private static class AccountAuthenticatorImpl extends AbstractAccountAuthenticator {
		private Context mContext;

		public AccountAuthenticatorImpl(Context context) {
			super(context);
			mContext = context;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * android.accounts.AbstractAccountAuthenticator#addAccount(android.
		 * accounts.AccountAuthenticatorResponse, java.lang.String,
		 * java.lang.String, java.lang.String[], android.os.Bundle)
		 */
		@Override
		public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType, String[] requiredFeatures, Bundle options)
				throws NetworkErrorException {
			Bundle result = new Bundle();
			Intent i = new Intent(mContext, SetupAccountActivity.class);
			i.setAction("com.alphabetbloc.accessmrs.sync.LOGIN");
			i.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
			i.putExtra(SetupAccountActivity.LAUNCHED_FROM_ACCT_MGR, true);
			result.putParcelable(AccountManager.KEY_INTENT, i);
			return result;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * android.accounts.AbstractAccountAuthenticator#confirmCredentials(
		 * android.accounts.AccountAuthenticatorResponse,
		 * android.accounts.Account, android.os.Bundle)
		 */
		@Override
		public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) {
			if (App.DEBUG) Log.v(TAG, "confirmCredentials");
			return null;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * android.accounts.AbstractAccountAuthenticator#editProperties(android
		 * .accounts.AccountAuthenticatorResponse, java.lang.String)
		 */
		@Override
		public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
			if (App.DEBUG) Log.v(TAG, "editProperties");
			return null;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * android.accounts.AbstractAccountAuthenticator#getAuthToken(android
		 * .accounts.AccountAuthenticatorResponse, android.accounts.Account,
		 * java.lang.String, android.os.Bundle)
		 */
		@Override
		public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {
			if (App.DEBUG) Log.v(TAG, "getAuthToken");
			return null;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * android.accounts.AbstractAccountAuthenticator#getAuthTokenLabel(java
		 * .lang.String)
		 */
		@Override
		public String getAuthTokenLabel(String authTokenType) {
			if (App.DEBUG) Log.v(TAG, "getAuthTokenLabel");
			return null;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * android.accounts.AbstractAccountAuthenticator#hasFeatures(android
		 * .accounts.AccountAuthenticatorResponse, android.accounts.Account,
		 * java.lang.String[])
		 */
		@Override
		public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features) throws NetworkErrorException {
//			if (App.DEBUG) Log.v(TAG, "hasFeatures: " + features);
			return null;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * android.accounts.AbstractAccountAuthenticator#updateCredentials(android
		 * .accounts.AccountAuthenticatorResponse, android.accounts.Account,
		 * java.lang.String, android.os.Bundle)
		 */
		@Override
		public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) {
			if (App.DEBUG) Log.v(TAG, "updateCredentials");
			return null;
		}
	}
}
