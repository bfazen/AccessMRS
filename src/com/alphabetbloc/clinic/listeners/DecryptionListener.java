package com.alphabetbloc.clinic.listeners;

public interface DecryptionListener {
	void decryptComplete(Boolean alldone);
	void setDeleteDecryptedFilesAlarm(boolean anyFileDecrypted);
}
