package com.alphabetbloc.clinic.listeners;

import android.content.SyncResult;


public interface SyncDataListener {
	void sslSetupComplete(String result, SyncResult syncResult);
	void uploadComplete(String result);
	void downloadComplete(String result);
	void syncComplete(String result, SyncResult syncResult);
	void progressUpdate(String message, int progress, int max);
}
