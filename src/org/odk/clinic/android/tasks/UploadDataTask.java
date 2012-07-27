package org.odk.clinic.android.tasks;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.listeners.UploadFormListener;

import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;

public class UploadDataTask extends AsyncTask<String, String, String> {
	private static String tag = "UploadDataTask";

	private static final int CONNECTION_TIMEOUT = 30000;

	protected UploadFormListener mStateListener;
	private ClinicAdapter mCla;
	private String mUrl;
	private int mTotalCount = -1;
	private String[] mParams;

	@Override
	protected String doInBackground(String... values) {
		mUrl = values[0];
		String uploadResult = "No Completed Forms to Upload";

		if (dataToUpload()) {
			ArrayList<String> uploaded = uploadInstances();
			
			if (!uploaded.isEmpty() && uploaded.size() > 0) {
				updateDbPath(uploaded);
			}
			
			//no context, so manually make string:
			int resultSize = uploaded.size();
			String s = " ";
			if (resultSize == mTotalCount) {
				if ((resultSize)>1) s = "s ";
				uploadResult = resultSize + " form " + s + "uploaded successfully.";

			} else {
				if ((mTotalCount - resultSize)>1) s = "s ";
				String failedforms = mTotalCount - resultSize + " of " + mTotalCount + " form" + s;
				uploadResult = "Sorry, " + failedforms + "failed to upload!";
			}
		}

		return uploadResult;

	}

	public boolean dataToUpload() {

		boolean dataToUpload = true;

		if (mCla != null) {
			mCla.open();
		} else {
			mCla = new ClinicAdapter();
			mCla.open();
		}
		Cursor c = mCla.fetchFormInstancesByStatus(ClinicAdapter.STATUS_UNSUBMITTED);
		ArrayList<String> selectedInstances = new ArrayList<String>();

		if (c != null && c.getCount() > 0) {
			String s = c.getString(c.getColumnIndex(ClinicAdapter.KEY_PATH));
			selectedInstances.add(s);
		}

		if (c != null)
			c.close();

		mCla.close();

		if (!selectedInstances.isEmpty()) {

			mTotalCount = selectedInstances.size();
			if (mTotalCount < 1) {
				dataToUpload = false;
			} else {

				mParams = selectedInstances.toArray(new String[mTotalCount]);
			}
		} else {
			dataToUpload = false;
		}

		return dataToUpload;
	}

	private ArrayList<String> uploadInstances() {
		ArrayList<String> uploadedInstances = new ArrayList<String>();
		int instanceCount = mParams.length;
		for (int i = 0; i < instanceCount; i++) {

			// configure connection
			HttpParams httpParams = new BasicHttpParams();
			HttpConnectionParams.setConnectionTimeout(httpParams, CONNECTION_TIMEOUT);
			HttpConnectionParams.setSoTimeout(httpParams, CONNECTION_TIMEOUT);
			HttpClientParams.setRedirecting(httpParams, false);

			// setup client
			DefaultHttpClient httpclient = new DefaultHttpClient(httpParams);
			HttpPost httppost = new HttpPost(mUrl);

			// get instance file
			File file = new File(mParams[i]);

			// find all files in parent directory
			File[] files = file.getParentFile().listFiles();
			System.out.println(file.getAbsolutePath());
			if (files == null) {
				Log.e(tag, "no files to upload in instance");
				continue;
			}

			// mime post
			MultipartEntity entity = new MultipartEntity();
			for (int j = 0; j < files.length; j++) {
				File f = files[j];
				FileBody fb;
				if (f.getName().endsWith(".xml")) {
					fb = new FileBody(f, "text/xml");
					entity.addPart("xml_submission_file", fb);
					Log.i(tag, "added xml file " + f.getName());
				} else if (f.getName().endsWith(".jpg")) {
					fb = new FileBody(f, "image/jpeg");
					entity.addPart(f.getName(), fb);
					Log.i(tag, "added image file " + f.getName());
				} else if (f.getName().endsWith(".3gpp")) {
					fb = new FileBody(f, "audio/3gpp");
					entity.addPart(f.getName(), fb);
					Log.i(tag, "added audio file " + f.getName());
				} else if (f.getName().endsWith(".3gp")) {
					fb = new FileBody(f, "video/3gpp");
					entity.addPart(f.getName(), fb);
					Log.i(tag, "added video file " + f.getName());
				} else if (f.getName().endsWith(".mp4")) {
					fb = new FileBody(f, "video/mp4");
					entity.addPart(f.getName(), fb);
					Log.i(tag, "added video file " + f.getName());
				} else {
					Log.w(tag, "unsupported file type, not adding file: " + f.getName());
				}
			}
			httppost.setEntity(entity);

			// prepare response and return uploaded
			HttpResponse response = null;
			try {
				response = httpclient.execute(httppost);
			} catch (ClientProtocolException e) {
				e.printStackTrace();
				return uploadedInstances;
			} catch (IOException e) {
				e.printStackTrace();
				return uploadedInstances;
			} catch (IllegalStateException e) {
				e.printStackTrace();
				return uploadedInstances;
			}

			// check response.
			// TODO: This isn't handled correctly.
			String serverLocation = null;
			Header[] h = response.getHeaders("Location");
			if (h != null && h.length > 0) {
				serverLocation = h[0].getValue();
			} else {
				// something should be done here...
				Log.e(tag, "Location header was absent");
			}
			int responseCode = response.getStatusLine().getStatusCode();
			Log.d(tag, "Response code:" + responseCode);

			// verify that your response came from a known server
			if (serverLocation != null && mUrl.contains(serverLocation)) {
				uploadedInstances.add(mParams[i]);
			}
		}
		return uploadedInstances;
	}

	private void updateDbPath(ArrayList<String> uploadInstance) {
		if (mCla != null) {
			mCla.open();
		} else {
			mCla = new ClinicAdapter();
			mCla.open();
		}
		Cursor c = null;
		for (int i = 0; i < uploadInstance.size(); i++) {
			c = mCla.fetchFormInstancesByPath(uploadInstance.get(i));
			if (c != null) {
				mCla.updateFormInstance(uploadInstance.get(i), ClinicAdapter.STATUS_SUBMITTED);
			}

		}

		if (c != null)
			c.close();

		mCla.close();
	}

	@Override
	protected void onProgressUpdate(String... values) {
		Log.e(tag, "UploadInstanceTask.onProgressUpdate=" + values[0] + ", " + values[1] + ", " + values[2] + ", ");
		synchronized (this) {
			if (mStateListener != null) {
				mStateListener.progressUpdate(values[0], new Integer(values[1]).intValue(), new Integer(values[2]).intValue());
			}
		}

	}

	@Override
    protected void onPostExecute(String result) {
    	Log.e(tag, "UploadInstanceTask.onPostExecute Result=" + result);
        synchronized (this) {
            if (mStateListener != null)
                mStateListener.uploadComplete(result);
        }
    }

	public void setUploadListener(UploadFormListener sl) {
		synchronized (this) {
			mStateListener = sl;
		}
	}
}
