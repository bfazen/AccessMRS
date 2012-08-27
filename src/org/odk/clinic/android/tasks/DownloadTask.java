package org.odk.clinic.android.tasks;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import android.os.AsyncTask;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.listeners.DownloadListener;
import org.odk.clinic.android.openmrs.Constants;

public abstract class DownloadTask extends
		AsyncTask<String, String, String> {
	
	private static final int CONNECTION_TIMEOUT = 60000;
	protected DownloadListener mStateListener;
	protected ClinicAdapter mPatientDbAdapter = new ClinicAdapter();

	@Override
	protected void onProgressUpdate(String... values) {
		synchronized (this) {
			if (mStateListener != null) {
				// update progress and total
				mStateListener.progressUpdate(values[0], new Integer(values[1]), new Integer(values[2]));
			}
		}

	}

	@Override
	protected void onPostExecute(String result) {
		synchronized (this) {
			if (mStateListener != null)
				mStateListener.downloadComplete(result);
		}
	}

	public void setDownloadListener(DownloadListener sl) {
		synchronized (this) {
			mStateListener = sl;
		}
	}

	// url, username, password, serializer, locale, action, cohort
	protected DataInputStream connectToServer(String url, String username, String password, boolean savedSearch, int cohort, int program) throws Exception {

		// compose url
		URL u = new URL(url);

		// setup http url connection
		HttpURLConnection c = (HttpURLConnection) u.openConnection();
		c.setDoOutput(true);
		c.setRequestMethod("POST");
		c.setConnectTimeout(CONNECTION_TIMEOUT);
		c.setReadTimeout(CONNECTION_TIMEOUT);
		c.addRequestProperty("Content-type", "application/octet-stream");
		// write auth details to connection
		DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(c.getOutputStream()));
		dos.writeUTF(username); // username
		dos.writeUTF(password); // password
		dos.writeBoolean(savedSearch);
		if (cohort > 0)
			dos.writeInt(cohort);
		//if (program > 0)
			dos.writeInt(program);

		dos.flush();
		dos.close();

		// read connection status
		DataInputStream zdis = new DataInputStream(new GZIPInputStream(c.getInputStream()));
		int status = zdis.readInt();

		if (status == Constants.STATUS_FAILURE) {
			zdis.close();
			throw new IOException("Connection failed. Please try again.");
		} else if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
			zdis.close();
			throw new IOException(
					"Access denied. Check your username and password.");
		} else {
			assert (status == HttpURLConnection.HTTP_OK); // success
			return zdis;
		}
	}
}
