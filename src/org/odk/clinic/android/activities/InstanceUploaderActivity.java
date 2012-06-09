package org.odk.clinic.android.activities;

import java.util.ArrayList;

import org.odk.clinic.android.R;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.listeners.UploadFormListener;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.tasks.UploadInstanceTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author Samuel Mbugua
 *
 */
public class InstanceUploaderActivity extends Activity implements UploadFormListener {

    private final static int PROGRESS_DIALOG = 1;
    private ProgressDialog mProgressDialog;
    private AlertDialog mAlertDialog;

    private UploadInstanceTask mInstanceUploaderTask;
    private int totalCount = -1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.app_name) + " > " + getString(R.string.upload_patients));

        // get instances to upload
        Intent i = getIntent();
        ArrayList<String> instances = i.getStringArrayListExtra(ClinicAdapter.KEY_INSTANCES);
        if (instances == null) {
            // nothing to upload
            return;
        }else
        	uploadInstances(instances);
    }


	
	private void uploadInstances(ArrayList<String> instances) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        StringBuilder url = new StringBuilder(settings.getString(PreferencesActivity.KEY_SERVER, getString(R.string.default_server)));
        url.append(Constants.INSTANCE_UPLOAD_URL);
        url.append("?uname=");
        url.append(settings.getString(PreferencesActivity.KEY_USERNAME, getString(R.string.default_username)));
        url.append("&pw=");
        url.append(settings.getString(PreferencesActivity.KEY_PASSWORD, getString(R.string.default_password)));
        totalCount = instances.size();
        
        if (totalCount < 1){
        	showCustomToast("No Patients to Upload");
        	return;
        }

        // convert array list to an array
        String[] sa = instances.toArray(new String[totalCount]);
        mInstanceUploaderTask = new UploadInstanceTask();
        mInstanceUploaderTask.setUploadListener(this);
        mInstanceUploaderTask.setUploadServer(url.toString());
        mInstanceUploaderTask.execute(sa);
        showDialog(PROGRESS_DIALOG);
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case PROGRESS_DIALOG:
                mProgressDialog = new ProgressDialog(this);
                DialogInterface.OnClickListener uploadButtonListener =
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            mInstanceUploaderTask.setUploadListener(null);
                            mInstanceUploaderTask.cancel(true);
                        }
                    };
                mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
                mProgressDialog.setTitle(getString(R.string.upload_patients));
                mProgressDialog.setMessage(getString(R.string.uploading_patients));
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.setButton(getString(R.string.cancel), uploadButtonListener);
                return mProgressDialog;
        }
        return null;
    }

    private void dismissDialogs() {
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
        }
    }
    
    @Override
    public void progressUpdate(String message, int progress, int max) {
    	 mProgressDialog.setMax(max);
         mProgressDialog.setProgress(progress);
    }

    @Override
    public void uploadComplete(ArrayList<String> result) {
        dismissDialog(PROGRESS_DIALOG);
        int resultSize = result.size();
        if (resultSize == totalCount) {
        	showCustomToast(getString(R.string.upload_all_successful, totalCount));
            
        } else {
            String s = totalCount - resultSize + " of " + totalCount;
        	showCustomToast(getString(R.string.upload_some_failed, s));

        }
        
        // for all successful update status
        ClinicAdapter cla = new ClinicAdapter();
        cla.open();
        Cursor c = null;
        for (int i = 0; i < resultSize; i++) {
        	c = cla.fetchFormInstancesByPath(result.get(i));
        	if ( c != null) {
        		cla.updateFormInstance(result.get(i), ClinicAdapter.STATUS_SUBMITTED);
        	}
        	
        }
        
        if (c != null)
            c.close();
        
        cla.close();
        finish();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        if (mInstanceUploaderTask != null && mInstanceUploaderTask.getStatus() != AsyncTask.Status.FINISHED)
            return mInstanceUploaderTask;

        return null;
    }
    
    @Override
	protected void onPause() {
		dismissDialogs();
		super.onPause();
	}
    
	@Override
	protected void onDestroy() {
		if (mInstanceUploaderTask != null) {
			mInstanceUploaderTask.setUploadListener(null);
            if (mInstanceUploaderTask.getStatus() == AsyncTask.Status.FINISHED) {
            	mInstanceUploaderTask.cancel(true);
            }
        }
		super.onDestroy();
	}

	@Override
	protected void onResume() {
		if (mInstanceUploaderTask != null) {
			mInstanceUploaderTask.setUploadListener(this);
        }
		super.onResume();
	}
	
	private void showCustomToast(String message) {
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.toast_view, null);

		// set the text in the view
		TextView tv = (TextView) view.findViewById(R.id.message);
		tv.setText(message);

		Toast t = new Toast(this);
		t.setView(view);
		t.setDuration(Toast.LENGTH_LONG);
		t.setGravity(Gravity.CENTER, 0, 0);
		t.show();
	}
	
	
}