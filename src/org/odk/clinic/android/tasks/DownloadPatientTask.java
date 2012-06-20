package org.odk.clinic.android.tasks;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.Observation;
import org.odk.clinic.android.openmrs.Patient;

import android.os.Environment;
import android.util.Log;

public class DownloadPatientTask extends DownloadTask {

	public static final String KEY_ERROR = "error";

	public static final String KEY_PATIENTS = "patients";

	public static final String KEY_OBSERVATIONS = "observations";

	private static final String TAG = "Clinic.DownloadPatientTask";

	@Override
	protected String doInBackground(String... values) {

		String url = values[0];
		String username = values[1];
		String password = values[2];
		boolean savedSearch = Boolean.valueOf(values[3]);
		int cohort = Integer.valueOf(values[4]);
		int program = Integer.valueOf(values[5]);
		
		int step = 0;
		int totalstep = 10;
		File zipFile = null;

		// Louis (louis.fazen@gmail.com) is changing how things download by
		// adding the downloadStreamToZip Method
		try {
			
			DataInputStream zdisServer = connectToServer(url, username, password, savedSearch, cohort, program);
			publishProgress("Downloading", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());
			if (zdisServer != null) {
				zipFile = downloadStreamToZip(zdisServer);
				zdisServer.close();
			}
			publishProgress("Downloading", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());
			if (zipFile != null) {
				DataInputStream zdis = new DataInputStream(new BufferedInputStream(new FileInputStream(zipFile)));

				if (zdis != null) {
					publishProgress("Processing", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());
					// open db and clean entries
					mPatientDbAdapter.open();
					mPatientDbAdapter.deleteAllPatients();
					mPatientDbAdapter.deleteAllObservations();
					mPatientDbAdapter.deleteAllFormInstances();

					// download and insert patients and obs
					publishProgress("Processing Patients", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());
					mPatientDbAdapter.insertPatients(zdis);
					publishProgress("Processing Observations", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());
					mPatientDbAdapter.insertObservations(zdis);
					publishProgress("Processing Forms", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());
					mPatientDbAdapter.insertPatientForms(zdis);

					// close zip stream
					zdis.close();
					zipFile.delete();
					publishProgress("Processing Forms", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());

					// TODO: louis.fazen is adding this...it is a bit of hack of bringing the various db into sync					
					mPatientDbAdapter.updatePriorityFormNumbers();
					mPatientDbAdapter.updatePriorityFormList();
					
					publishProgress("Processing Forms", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());
					mPatientDbAdapter.updateSavedFormNumbers();
					mPatientDbAdapter.updateSavedFormsList();
					
					publishProgress("Processing Forms", Integer.valueOf(step++).toString(), Integer.valueOf(totalstep).toString());
					
					// close db and stream
					mPatientDbAdapter.createDownloadLog();
					mPatientDbAdapter.close();
					
					
				}
			}

			else {
				Log.e(TAG, "FileInputStream Could not Retrieve Data from ZipFile");
			}

		} catch (Exception e) {
			e.printStackTrace();
			return e.getLocalizedMessage();
		}

		return null;
	}

	private File downloadStreamToZip(final DataInputStream inputStream) throws Exception {
		File tempFile = null;
		try {
			File odkRoot = new File(Environment.getExternalStorageDirectory() + File.separator + "odk");
			tempFile = File.createTempFile("pts", ".zip", odkRoot);
			BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(tempFile));

			// TODO: louis.fazen this next may cause problems:?
			// int totalSize = inputStream.readInt();

			byte[] buffer = new byte[1024];
			int count = 0;
			int progress = 0;
			while ((count = inputStream.read(buffer)) > 0) {
				stream.write(buffer, 0, count);
				progress++;
				Log.i("ODK clinic", "progresss:" + progress);
				// publishProgress("Downloading Data", Integer.valueOf(progress)
				// .toString(), Integer.valueOf(totalSize).toString());

			}
			stream.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return tempFile;
	}

	private void insertPatientForms(final DataInputStream zdis) throws Exception {

		// for every patient
		int icount = zdis.readInt();
		Log.e(TAG, "insertPatientForms icount: " + icount);

		for (int i = 1; i < icount + 1; i++) {
			Log.e(TAG, "insertPatientForms i: " + i);
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

			publishProgress("Processing Forms", Integer.valueOf(i).toString(), Integer.valueOf(icount).toString());
		}
	}

	private void insertPatients(DataInputStream zdis) throws Exception {

		int c = zdis.readInt();
		// List<Patient> patients = new ArrayList<Patient>(c);
		Log.i("ODK Clinic", "insertPatients called, and c or  is:" + c);


			for (int i = 1; i < c + 1; i++) {
				Patient p = new Patient();

				int id = zdis.readInt();
				p.setPatientId(id);
				Log.i("ODK Clinic", "insertPatients called, and i is:" + i + ", id is: " + id);
				p.setFamilyName(zdis.readUTF());
				Log.i("ODK Clinic", "insertPatients called, and readUTF FamilyName works...	");
				p.setMiddleName(zdis.readUTF());
				p.setGivenName(zdis.readUTF());
				p.setGender(zdis.readUTF());
				p.setBirthDate(parseDate(zdis.readUTF()));
				p.setIdentifier(zdis.readUTF());

				mPatientDbAdapter.createPatient(p);
				
				publishProgress("Processing Patients", Integer.valueOf(i).toString(), Integer.valueOf(c).toString());
			}
			
	}

	private void insertObservations(DataInputStream zdis) throws Exception {
		
	
		// for every patient
		int icount = zdis.readInt();
		Log.e(TAG, "insertObservations icount: " + icount);
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

			publishProgress("Processing Observations", Integer.valueOf(i).toString(), Integer.valueOf(icount).toString());
		
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