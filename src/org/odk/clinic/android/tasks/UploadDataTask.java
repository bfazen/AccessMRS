package org.odk.clinic.android.tasks;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.odk.clinic.android.R;
import org.odk.clinic.android.database.DbAdapter;
import org.odk.clinic.android.listeners.UploadFormListener;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.ODKLocalKeyStore;
import org.odk.clinic.android.utilities.WebUtils;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;

public class UploadDataTask extends AsyncTask<Void, String, String> {
	private static String tag = "UploadDataTask";

	private static final int CONNECTION_TIMEOUT = 60000;

	protected UploadFormListener mStateListener;
	private int mTotalCount = -1;
	private String[] mInstancesToUpload;

	@Override
	protected String doInBackground(Void... values) {

		String uploadResult = "No Completed Forms to Upload";

		if (dataToUpload()) {
			//TODO! CHECK does this verify uploaded?
			ArrayList<String> uploaded = uploadInstances(WebUtils.getFormUploadUrl());

			// Encrypt all the instances successfully uploaded
			if (!uploaded.isEmpty() && uploaded.size() > 0) {
				// update the databases with new status submitted
				for (int i = 0; i < uploaded.size(); i++) {
					String path = uploaded.get(i);
					updateClinicDbPath(path);
					updateCollectDb(path);
				}
			}

			int resultSize = uploaded.size();
			if (resultSize == mTotalCount)
				uploadResult = App.getApp().getString(R.string.upload_all_successful, resultSize);
			else
				uploadResult = App.getApp().getString(R.string.upload_all_successful, (mTotalCount - resultSize) + " of " + mTotalCount);
		}
		return uploadResult;
	}

	public boolean dataToUpload() {

		boolean dataToUpload = true;
		ArrayList<String> selectedInstances = new ArrayList<String>();

		Cursor c = DbAdapter.openDb().fetchFormInstancesByStatus(DbAdapter.STATUS_UNSUBMITTED);
		if (c != null && c.getCount() > 0) {
			String s = c.getString(c.getColumnIndex(DbAdapter.KEY_PATH));
			selectedInstances.add(s);
			c.close();
		}

		if (!selectedInstances.isEmpty()) {
			mTotalCount = selectedInstances.size();
			if (mTotalCount < 1)
				dataToUpload = false;
			else
				mInstancesToUpload = selectedInstances.toArray(new String[mTotalCount]);
		} else
			dataToUpload = false;

		return dataToUpload;
	}

	private ArrayList<String> uploadInstances(String url) {
		ArrayList<String> uploadedInstances = new ArrayList<String>();
		int instanceCount = mInstancesToUpload.length;
		for (int i = 0; i < instanceCount; i++) {

			// configure connection
			HttpParams httpParams = new BasicHttpParams();
			// HTTPS ADDITION: Next 3 lines
			HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);
			HttpProtocolParams.setContentCharset(httpParams, HTTP.UTF_8);
			HttpProtocolParams.setUseExpectContinue(httpParams, false);
			HttpConnectionParams.setConnectionTimeout(httpParams, CONNECTION_TIMEOUT);
			HttpConnectionParams.setSoTimeout(httpParams, CONNECTION_TIMEOUT);
			HttpClientParams.setRedirecting(httpParams, false);

			// HTTPS ADDITION
			ConnManagerParams.setTimeout(httpParams, CONNECTION_TIMEOUT);
			ConnManagerParams.setMaxConnectionsPerRoute(httpParams, new ConnPerRouteBean(20));
			ConnManagerParams.setMaxTotalConnections(httpParams, 20);
			SchemeRegistry schemeRegistry = new SchemeRegistry();
			schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));

			try {
				schemeRegistry.register(new Scheme("https", new SSLSocketFactory(ODKLocalKeyStore.getKeyStore()), 8443));
			} catch (Exception e) {
			}

			ClientConnectionManager connectionManager = new ThreadSafeClientConnManager(httpParams, schemeRegistry);
			// HTTPS ADDITION END

			// setup client (HTTPS added the connection manager)
			DefaultHttpClient httpclient = new DefaultHttpClient(connectionManager, httpParams);
			HttpPost httppost = new HttpPost(url);

			// get instance file
			File file = new File(mInstancesToUpload[i]);

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

			try {
				httpclient.execute(httppost);
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

			uploadedInstances.add(mInstancesToUpload[i]);

		}
		return uploadedInstances;
	}

	// TODO: do we no longer have a check on these things? ORIGINAL VERSION OF
	// THE ABOVE:
	// prepare response and return uploaded
	// HttpResponse response = null;
	// try {
	// response = httpclient.execute(httppost);
	// } catch (ClientProtocolException e) {
	// e.printStackTrace();
	// return uploadedInstances;
	// } catch (IOException e) {
	// e.printStackTrace();
	// return uploadedInstances;
	// } catch (IllegalStateException e) {
	// e.printStackTrace();
	// return uploadedInstances;
	// }
	//
	// // check response.
	// // TODO This isn't handled correctly.
	// String serverLocation = null;
	// Header[] h = response.getHeaders("Location");
	// if (h != null && h.length > 0) {
	// serverLocation = h[0].getValue();
	// } else {
	// // something should be done here...
	// Log.e(tag, "Location header was absent");
	// }
	// int responseCode = response.getStatusLine().getStatusCode();
	// Log.d(tag, "Response code:" + responseCode);
	//
	// // verify that your response came from a known server
	// if (serverLocation != null && mUrl.contains(serverLocation)) {
	// uploadedInstances.add(mInstancesToUpload[i]);
	// }

	private boolean updateCollectDb(String path) {
		boolean updated = false;
		try {
			ContentValues insertValues = new ContentValues();
			insertValues.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMITTED);
			String where = InstanceColumns.INSTANCE_FILE_PATH + "=?";
			String whereArgs[] = { path };
			int updatedrows = App.getApp().getContentResolver().update(InstanceColumns.CONTENT_URI, insertValues, where, whereArgs);

			if (updatedrows > 1) {
				Log.e(tag, "Error! updated more than one entry when tyring to update: " + path.toString());
			} else if (updatedrows == 1) {
				Log.i(tag, "Instance successfully updated to Submitted Status");
				updated = true;
			} else {
				Log.e(tag, "Error, Instance doesn't exist but we have its path!! " + path.toString());
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return updated;
	}

	private void updateClinicDbPath(String path) {
		// TODO! WHAT HAPPENED HERE? we should simply be deleting these in the
		// FormInstances Table,
		// not updating them, no?
		Cursor c = DbAdapter.openDb().fetchFormInstancesByPath(path);
		if (c != null) {
			DbAdapter.openDb().updateFormInstance(path, DbAdapter.STATUS_SUBMITTED);
			c.close();
		}
	}

	@Override
	protected void onProgressUpdate(String... values) {
		Log.e(tag, "UploadInstanceTask.onProgressUpdate=" + values[0] + ", " + values[1] + ", " + values[2] + ", ");
		synchronized (this) {
			if (mStateListener != null)
				mStateListener.progressUpdate(values[0], Integer.valueOf(values[1]), Integer.valueOf(values[2]));
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
