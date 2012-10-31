package com.alphabetbloc.clinic.listeners;

import android.content.SyncResult;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public interface SyncDataListener {
	void syncComplete(String result, SyncResult syncResult);
}
