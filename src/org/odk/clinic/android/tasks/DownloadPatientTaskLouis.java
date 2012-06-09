package org.odk.clinic.android.tasks;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.Observation;
import org.odk.clinic.android.openmrs.Patient;

import android.os.Environment;

public class DownloadPatientTaskLouis extends DownloadTask {

	public static final String KEY_ERROR = "error";

	public static final String KEY_PATIENTS = "patients";

	public static final String KEY_OBSERVATIONS = "observations";

	@Override
	protected String doInBackground(String... values) {

		String url = values[0];
		String username = values[1];
		String password = values[2];
		boolean savedSearch = Boolean.valueOf(values[3]);
		int cohort = Integer.valueOf(values[4]);
		int program = Integer.valueOf(values[5]);

		try {
			DataInputStream zdis = connectToServer(url, username, password, savedSearch, cohort, program);
			if (zdis != null) {

				File zipFile = downloadStreamToZip(zdis);
//				FileInputStream fileIS = new FileInputStream(zipFile);
//				GZIPInputStream zis = new GZIPInputStream(fileIS);
//				DataInputStream zdisLocal = new DataInputStream(zis);
				DataInputStream zdisLocal = new DataInputStream(new GZIPInputStream(new FileInputStream(zipFile)));
				
//				int size = fileIS.available();
//				byte[] buffer2 = new byte[size];
//				fileIS.read(buffer2);
//				fileIS.close();
//				String totalS = new String(buffer2);

				// open db and clean entries
				mPatientDbAdapter.open();
				mPatientDbAdapter.deleteAllPatients();
				mPatientDbAdapter.deleteAllObservations();
				mPatientDbAdapter.deleteAllFormInstances();

				// download and insert patients and obs
				insertPatients(zdisLocal);
				insertObservations(zdisLocal);
				insertPatientForms(zdisLocal);

				// close db and stream
				mPatientDbAdapter.close();
				zdisLocal.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return e.getLocalizedMessage();
		}

		return null;
	}

	private File downloadStreamToZip(final DataInputStream zdis) throws Exception {
		File tempFile = null;
		try {
			File odkRoot = new File(Environment.getExternalStorageDirectory() + File.separator + "odk");
			tempFile = File.createTempFile("pts", ".zip", odkRoot);
			GZIPOutputStream zout = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile)));
			
//			lengthier code:
//			OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile));
//			FileOutputStream fout = new FileOutputStream(tempFile);

			// the following is only used for ZipOutputStream where there are
			// multiple entries (i.e. multiple files going into a zip) GZIP is
			// compression for only one file...
			// ZipEntry entry = new ZipEntry(zdis_entry_name);
			// zout.putNextEntry(entry);

			byte[] buffer = new byte[1024];
			int count = 0;
			int totalSize = zdis.readInt();
			
//			int count = zdis.read(buffer, 0, 1024);
			
			 while((count = zdis.read(buffer)) > 0) {
				zout.write(buffer, 0, count);
				count = zdis.read(buffer, 0, 1024);
			
				 publishProgress("Downloading Data", Integer.valueOf(count).toString(), Integer
							.valueOf(totalSize).toString());
					
			}
			
			// as above... there is no other entry
			// zout.closeEntry();
			
			zdis.close();
			zout.close();
			
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return tempFile;
	}

	private void insertPatientForms(final DataInputStream zdis)
			throws Exception {

		// for every patient
		int icount = zdis.readInt();

		for (int i = 1; i < icount + 1; i++) {

			Observation o = new Observation();
			o.setPatientId(zdis.readInt());
			o.setFieldName(zdis.readUTF());

			byte dataType = zdis.readByte();
			if (dataType == Constants.TYPE_STRING) {
				o.setValueText(zdis.readUTF());
			} else if (dataType == Constants.TYPE_INT) {
				o.setValueInt(zdis.readInt());
			} else if (dataType == Constants.TYPE_DOUBLE) {
				o.setValueNumeric(zdis.readDouble());
			} else if (dataType == Constants.TYPE_DATE) {
				o.setValueDate(parseDate(zdis.readUTF()));
			}

			o.setDataType(dataType);
			o.setEncounterDate(parseDate(zdis.readUTF()));
			mPatientDbAdapter.createObservation(o);

			publishProgress("Processing Forms", Integer.valueOf(i).toString(), Integer
					.valueOf(icount).toString());
		}
	}

	private void insertPatients(DataInputStream zdis) throws Exception {

		int c = zdis.readInt();
		// List<Patient> patients = new ArrayList<Patient>(c);
		for (int i = 1; i < c + 1; i++) {
			Patient p = new Patient();
			int id = zdis.readInt();
			p.setPatientId(id);
			p.setFamilyName(zdis.readUTF());
			p.setMiddleName(zdis.readUTF());
			p.setGivenName(zdis.readUTF());
			p.setGender(zdis.readUTF());
			p.setBirthDate(parseDate(zdis.readUTF()));
			p.setIdentifier(zdis.readUTF());
			mPatientDbAdapter.createPatient(p);

			publishProgress("Processing Patients", Integer.valueOf(i).toString(), Integer
					.valueOf(c).toString());
		}

	}

	private void insertObservations(DataInputStream zdis) throws Exception {

		// for every patient
		int icount = zdis.readInt();
		for (int i = 1; i < icount + 1; i++) {

			Observation o = new Observation();
			o.setPatientId(zdis.readInt());
			o.setFieldName(zdis.readUTF());

			byte dataType = zdis.readByte();
			if (dataType == Constants.TYPE_STRING) {
				o.setValueText(zdis.readUTF());
			} else if (dataType == Constants.TYPE_INT) {
				o.setValueInt(zdis.readInt());
			} else if (dataType == Constants.TYPE_DOUBLE) {
				o.setValueNumeric(zdis.readDouble());
			} else if (dataType == Constants.TYPE_DATE) {
				o.setValueDate(parseDate(zdis.readUTF()));
			}

			o.setDataType(dataType);
			o.setEncounterDate(parseDate(zdis.readUTF()));
			mPatientDbAdapter.createObservation(o);

			publishProgress("Processing Observations", Integer.valueOf(i).toString(),
					Integer.valueOf(icount).toString());
		}

	}

	private static String parseDate(String s) {
		String date = s.split("T")[0];
		SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy");
		SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd");
		try {
			return outputFormat.format(inputFormat.parse(date));
		} catch (ParseException e) {
			return "Unknown date";
		}
	}

}

/*
From Win..............................................................................................

package org.openmrs.module.odkconnector.serialization.web;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.openmrs.module.odkconnector.serialization.serializer.openmrs.ObsSerializer;
import org.openmrs.test.BaseModuleContextSensitiveTest;





try {
	URL url = new URL("http://somewhere.com/some/webhosted/file");
	HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

	//set up some things on the connection
	urlConnection.setRequestMethod("GET");
	urlConnection.setDoOutput(true);

	//and connect!
	urlConnection.connect();

	//set the path where we want to save the file
	//in this case, going to save it on the root directory of the
	//sd card.
	File SDCardRoot = Environment.getExternalStorageDirectory();
	//create a new file, specifying the path, and the filename
	//which we want to save the file as.
	File file = new File(SDCardRoot,"somefile.ext");

	//this will be used to write the downloaded data into the file we created
	FileOutputStream fileOutput = new FileOutputStream(file);

	//this will be used in reading the data from the internet
	InputStream inputStream = urlConnection.getInputStream();

	//this is the total size of the file
	int totalSize = urlConnection.getContentLength();
	//variable to store total downloaded bytes
	int downloadedSize = 0;

	//create a buffer...
	byte[] buffer = new byte[1024];
	int bufferLength = 0; //used to store a temporary size of the buffer

	//now, read through the input buffer and write the contents to the file
	while ( (bufferLength = inputStream.read(buffer)) > 0 ) {
		//add the data in the buffer to the file in the file output stream (the file on the sd card
		fileOutput.write(buffer, 0, bufferLength);
		//add up the size so we know how much is downloaded
		downloadedSize += bufferLength;
		//this is where you would do something to report the prgress, like this maybe
		updateProgress(downloadedSize, totalSize);

	}
	//close the output stream when done
	fileOutput.close();

//catch some possible errors...
} catch (MalformedURLException e) {
	e.printStackTrace();
} catch (IOException e) {
	e.printStackTrace();
}
// see http://androidsnippets.com/download-an-http-file-to-sdcard-with-progress-notification



public class PatientWebConnectorTest extends BaseModuleContextSensitiveTest {

	private static final Log log = LogFactory.getLog(PatientWebConnectorTest.class);

	private static final String SERVER_URL = "http://localhost:8080/openmrs";

	@Test
	public void serialize_shouldDisplayAllPatientInformation() throws Exception {

		// compose url
		URL u = new URL(SERVER_URL + "/module/odkconnector/download/patients.form");

		// setup http url connection
		HttpURLConnection connection = (HttpURLConnection) u.openConnection();
		connection.setDoOutput(true);
		connection.setRequestMethod("POST");
		connection.setConnectTimeout(10000);
		connection.setReadTimeout(10000);
		connection.addRequestProperty("Content-type", "application/octet-stream");

		// write auth details to connection
		DataOutputStream outputStream = new DataOutputStream(new GZIPOutputStream(connection.getOutputStream()));
		outputStream.writeUTF("admin");
		outputStream.writeUTF("test");
		outputStream.writeBoolean(false);
		outputStream.writeInt(2);
		outputStream.writeInt(1);
		outputStream.close();

		DataInputStream inputStream = new DataInputStream(new GZIPInputStream(connection.getInputStream()));
		Integer responseStatus = inputStream.readInt();

		if (responseStatus == HttpURLConnection.HTTP_OK) {

			// total number of patients
			Integer patientCounter = inputStream.readInt();
			System.out.println("Patient Counter: " + patientCounter);
			for (int j = 0; j < patientCounter; j++) {
				System.out.println("=================Patient=====================");
				System.out.println("Patient Id: " + inputStream.readInt());
				System.out.println("Family Name: " + inputStream.readUTF());
				System.out.println("Middle Name: " + inputStream.readUTF());
				System.out.println("Last Name: " + inputStream.readUTF());
				System.out.println("Gender: " + inputStream.readUTF());
				System.out.println("Birth Date: " + inputStream.readUTF());
				System.out.println("Identifier: " + inputStream.readUTF());
                System.out.println("Patients: " + j + " out of " + patientCounter);
			}

			Integer obsCounter = inputStream.readInt();
			System.out.println("Observation Counter: " + obsCounter);
			for (int j = 0; j < obsCounter; j++) {
				System.out.println("==================Observation=================");
				System.out.println("Patient Id: " + inputStream.readInt());
				System.out.println("Concept Name: " + inputStream.readUTF());

				byte type = inputStream.readByte();
				if (type == ObsSerializer.TYPE_STRING)
					System.out.println("Value: " + inputStream.readUTF());
				else if (type == ObsSerializer.TYPE_INT)
					System.out.println("Value: " + inputStream.readInt());
				else if (type == ObsSerializer.TYPE_DOUBLE)
					System.out.println("Value: " + inputStream.readDouble());
				else if (type == ObsSerializer.TYPE_DATE)
					System.out.println("Value: " + inputStream.readUTF());
				System.out.println("Time: " + inputStream.readUTF());
                System.out.println("Obs: " + j + " out of: " + obsCounter);
			}
			Integer formCounter = inputStream.readInt();
			System.out.println("Form Counter: " + formCounter);
			for (int j = 0; j < formCounter; j++) {
				System.out.println("==================Observation=================");
				System.out.println("Patient Id: " + inputStream.readInt());
				System.out.println("Concept Name: " + inputStream.readUTF());

				byte type = inputStream.readByte();
				if (type == ObsSerializer.TYPE_STRING)
					System.out.println("Value: " + inputStream.readUTF());
				else if (type == ObsSerializer.TYPE_INT)
					System.out.println("Value: " + inputStream.readInt());
				else if (type == ObsSerializer.TYPE_DOUBLE)
					System.out.println("Value: " + inputStream.readDouble());
				else if (type == ObsSerializer.TYPE_DATE)
					System.out.println("Value: " + inputStream.readUTF());
				System.out.println("Time: " + inputStream.readUTF());
                System.out.println("Form: " + j + " out of: " + formCounter);
			}
		}
		inputStream.close();
	}

} 
*/