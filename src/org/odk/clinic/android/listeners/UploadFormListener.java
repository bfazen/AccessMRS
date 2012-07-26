package org.odk.clinic.android.listeners;


public interface UploadFormListener {
    void uploadComplete(String resultString);
    void progressUpdate(String message, int progress, int max);
}
