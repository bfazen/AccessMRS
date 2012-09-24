package org.odk.clinic.android.listeners;


public interface SyncDataListener {
	void sslSetupComplete(String result);
	void uploadComplete(String result);
	void downloadComplete(String result);
	void syncComplete(String result);
	void progressUpdate(String message, int progress, int max);
}
