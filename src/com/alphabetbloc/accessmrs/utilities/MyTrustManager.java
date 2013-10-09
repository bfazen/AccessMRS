/*
 * Copyright 2012 Timelappse
 * Copyright 2012 Andlytics Project
 * Copyright 2012 Louis Fazen
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

package com.alphabetbloc.accessmrs.utilities;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import android.util.Log;

/**
 * 
 * @author Nikolay Nelenkov (? https://github.com/nelenkov/custom-cert-https I think... though license is different author?
 * "loosely based on org.apache.http.conn.ssl.SSLSocketFactory")
 * @author Louis Fazen
 */
public class MyTrustManager implements X509TrustManager {

	private static final String TAG = MyTrustManager.class.getSimpleName();

//	 LocalStore X509TrustManager finds local certs for MyTrustManager
	static class LocalStoreX509TrustManager implements X509TrustManager {

		private X509TrustManager trustManager;

		LocalStoreX509TrustManager(KeyStore localTrustStore) {
			try {
				TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
				tmf.init(localTrustStore);

				trustManager = findX509TrustManager(tmf);
				if (trustManager == null) {
					throw new IllegalStateException("Couldn't find X509TrustManager");
				} 
			} catch (GeneralSecurityException e) {
				throw new RuntimeException(e);
			}

		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			trustManager.checkClientTrusted(chain, authType);
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			trustManager.checkServerTrusted(chain, authType);
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return trustManager.getAcceptedIssuers();
		}
	}

	// Method used exclusively by the inner LocalStore X509TrustManager
	static X509TrustManager findX509TrustManager(TrustManagerFactory tmf) {
		TrustManager tms[] = tmf.getTrustManagers();
		for (int i = 0; i < tms.length; i++) {
			if (tms[i] instanceof X509TrustManager) {
				return (X509TrustManager) tms[i];
			} 
		}

		return null;
	}

	private X509TrustManager defaultTrustManager;
	private X509TrustManager localTrustManager;
	private X509Certificate[] acceptedIssuers;

	//combines localManager (above) and defaultManagers into one manager
	public MyTrustManager(KeyStore localKeyStore) {
		
		try {
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init((KeyStore) null);

			defaultTrustManager = findX509TrustManager(tmf);
			if (defaultTrustManager == null) {
				throw new IllegalStateException("Couldn't find X509TrustManager");
			}

			localTrustManager = new LocalStoreX509TrustManager(localKeyStore);

			List<X509Certificate> allIssuers = new ArrayList<X509Certificate>();
			
			for (X509Certificate cert : localTrustManager.getAcceptedIssuers()) {
				allIssuers.add(cert);
			}
			for (X509Certificate cert : defaultTrustManager.getAcceptedIssuers()) {
				allIssuers.add(cert);
			}
			acceptedIssuers = allIssuers.toArray(new X509Certificate[allIssuers.size()]);
		} catch (GeneralSecurityException e) {
			Log.e(TAG, "We have caught an exception in creating a trust manager!");
			throw new RuntimeException(e);
		}

	}

	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		try {
			if (App.DEBUG) Log.v(TAG, "checkClientTrusted () with default trust manager...");
			defaultTrustManager.checkClientTrusted(chain, authType);
		} catch (CertificateException ce) {
			if (App.DEBUG) Log.v(TAG, "checkClientTrusted () with local trust manager...");
			localTrustManager.checkClientTrusted(chain, authType);
		}
	}

	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		try {
			if (App.DEBUG) Log.v(TAG, "checkServerTrusted () with local trust manager...");
			localTrustManager.checkServerTrusted(chain, authType);
		} catch (CertificateException ce) {
			if (App.DEBUG) Log.v(TAG, "checkServerTrusted () with default trust manager...");
			defaultTrustManager.checkServerTrusted(chain, authType);
		}
	}

	public X509Certificate[] getAcceptedIssuers() {
		return acceptedIssuers;
	}

}
