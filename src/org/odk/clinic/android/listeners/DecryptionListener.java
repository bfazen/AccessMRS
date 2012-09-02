package org.odk.clinic.android.listeners;

public interface DecryptionListener {
	void decryptComplete(Boolean alldone);
	void setDeleteDecryptedFilesAlarm(boolean anyFileDecrypted);
}
