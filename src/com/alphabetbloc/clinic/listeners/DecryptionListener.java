package com.alphabetbloc.clinic.listeners;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 *
 */
public interface DecryptionListener {
	void decryptComplete(Boolean alldone);
	void setDeleteDecryptedFilesAlarm(boolean anyFileDecrypted);
}
