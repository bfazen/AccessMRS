package org.odk.clinic.android.tasks;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.zip.ZipInputStream;

import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.Observation;
import org.odk.clinic.android.openmrs.Patient;

import android.os.Environment;

public class DownloadPatientTaskYawOriginal extends DownloadTask {

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
		
			
				// open db and clean entries
				mPatientDbAdapter.open();
				mPatientDbAdapter.deleteAllPatients();
				mPatientDbAdapter.deleteAllObservations();
				mPatientDbAdapter.deleteAllFormInstances();

				// download and insert patients and obs
				insertPatients(zdis);
				insertObservations(zdis);
				insertPatientForms(zdis);

				// close db and stream
				mPatientDbAdapter.close();
				zdis.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return e.getLocalizedMessage();
		}

		return null;
	}

	private void insertPatientForms(final DataInputStream zdis) throws Exception {

		
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

			publishProgress("mappings", Integer.valueOf(i).toString(),
					Integer.valueOf(icount).toString());
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

			publishProgress("patients", Integer.valueOf(i).toString(), Integer
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

			publishProgress("observations", Integer.valueOf(i).toString(),
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
