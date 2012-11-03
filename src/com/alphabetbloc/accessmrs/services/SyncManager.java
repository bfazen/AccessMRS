package com.alphabetbloc.accessmrs.services;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import net.sqlcipher.DatabaseUtils.InsertHelper;
import net.sqlcipher.database.SQLiteDatabase;

import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.alphabetbloc.accessmrs.data.Form;
import com.alphabetbloc.accessmrs.providers.DataModel;
import com.alphabetbloc.accessmrs.providers.Db;
import com.alphabetbloc.accessmrs.providers.DbProvider;
import com.alphabetbloc.accessmrs.utilities.App;
import com.alphabetbloc.accessmrs.utilities.UiUtils;
import com.alphabetbloc.accessmrs.utilities.XformUtils;
import com.alphabetbloc.accessmrs.R;

/**
 * A Class to help with all Database interaction during a Sync.
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class SyncManager {

	private static final String TAG = SyncManager.class.getSimpleName();
	public static final String SYNC_MESSAGE = "com.alphabetbloc.accessmrs.utilities.sync_message";
	public static final String TOAST_MESSAGE = "toast_message";
	public static final String TOAST_ERROR = "toast_error";
	public static final String START_NEW_SYNC = "start_new_sync";
	public static final int UPLOAD_FORMS = 1;
	public static final int DOWNLOAD_FORMS = 2;
	public static final int DOWNLOAD_OBS = 3;
	public static final int SYNC_COMPLETE = 4;
	public static final String MANUAL_SYNC = "manual_sync";

	private String mSyncResultString;
	private Context mContext;
	public static int sSyncStep;
	public static int sLoopCount;
	public static int sLoopProgress;
	public static String sSyncTitle;
	public static boolean sEndSync;
	public static boolean sStartSync;
	public static boolean sCancelSync;

	// Android OS does not allow concurrent sync... so static progress int works
	public SyncManager(Context context) {
		mContext = context;
		sSyncTitle = mContext.getString(R.string.sync_in_progress);
		sSyncStep = 0;
		sLoopProgress = 0;
		sLoopCount = 0;
		sEndSync = false;
		Log.i(TAG, "New SyncManager with: Step=" + sSyncStep + " Progress=" + sLoopProgress + " Count=" + sLoopCount);
	}

	// REQUEST MANUAL SYNC
	public static void syncData() {
		Log.i(TAG, "SyncData is Requested");
		AccountManager accountManager = AccountManager.get(App.getApp());
		Account[] accounts = accountManager.getAccountsByType(App.getApp().getString(R.string.app_account_type));
		if (accounts.length > 0) {

			sStartSync = true;

			// TODO! Not sure if this is the best way to do it?
			Bundle bundle = new Bundle();
			bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
			bundle.putBoolean(ContentResolver.SYNC_EXTRAS_FORCE, true);
			// bundle.putBoolean(MANUAL_SYNC, true);
			// bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
			// //this resets the scheduled sync
			ContentResolver.requestSync(accounts[0], App.getApp().getString(R.string.app_provider_authority), bundle);
		} else
			UiUtils.toastAlert(App.getApp().getString(R.string.sync_error), App.getApp().getString(R.string.no_account_setup));
	}

	public void addSyncStep(String title, boolean increment) {

		if (increment)
			sSyncStep++;
		else if (sLoopCount > 0) {
			// If leaving loop, round up and reset counters
			float loop = ((float) SyncManager.sLoopProgress / (float) SyncManager.sLoopCount);
			sSyncStep = (int) ((float) sSyncStep + loop + 0.5F);
			sLoopProgress = 0;
			sLoopCount = 0;
		}

		// Or else just update the title
		sSyncTitle = title;

		Log.i(TAG, "addSyncStep: Step=" + sSyncStep + " Progress=" + sLoopProgress + " Count=" + sLoopCount);
		// if (sSyncStep == 0)
		// sSyncStep++;
		// int roundLoopUp = (int) (syncStepAndLoop + 0.5);
		// if (roundLoopUp <= sSyncStep)
		// roundLoopUp++;
		// sSyncStep = roundLoopUp;
		//
	}

	// UPLOAD SECTION:
	public String[] getInstancesToUpload() {

		ArrayList<String> selectedInstances = new ArrayList<String>();

		Cursor c = Db.open().fetchFormInstancesByStatus(DataModel.STATUS_UNSUBMITTED);
		if (c != null) {
			if (c.moveToFirst()) {
				do {
					String dbPath = c.getString(c.getColumnIndex(DataModel.KEY_PATH));
					selectedInstances.add(dbPath);
				} while (c.moveToNext());
			}
			c.close();
		}

		return selectedInstances.toArray(new String[selectedInstances.size()]);
	}

	public String updateUploadedForms(String[] uploaded, SyncResult syncResult) {

		StringBuilder error = new StringBuilder();
		String e = null;

		sLoopProgress = 0;
		if (uploaded.length > 0) {

			String path;
			sLoopCount = uploaded.length;
			// update the databases with new status submitted
			for (int i = 0; i < uploaded.length; i++) {

				path = uploaded[i];
				Log.v(TAG, "Updating the uploaded instance in db " + path);
				// update AccessMRS
				e = updateAccessMrsInstances(path, syncResult);
				if (e != null)
					error.append(" AccessMRS Form ").append(i).append(": ").append(e);

				// update collect
				e = updateCollectInstances(path, syncResult);
				if (e != null)
					error.append(" Collect Form ").append(i).append(": ").append(e);

				sLoopProgress++;
			}

			// Encrypt the uploaded data with wakelock to ensure it happens!
			WakefulIntentService.sendWakefulWork(mContext, EncryptionService.class);
		}

		return error.toString();

	}

	public String updateAccessMrsInstances(String path, SyncResult syncResult) {

		try {
			// Cursor c = Db.open().fetchFormInstancesByPath(path);
			// if (c != null) {

			// TODO! Make sure we are deleting the submitted instances from
			// Db
			// after encryption!
			Db.open().updateFormInstance(path, DataModel.STATUS_SUBMITTED);
			// c.close();
			// }

		} catch (Exception e) {
			e.printStackTrace();
			++syncResult.stats.numIoExceptions;
			return e.getLocalizedMessage();
		}

		return null;
	}

	public String updateCollectInstances(String path, SyncResult syncResult) {

		try {
			ContentValues insertValues = new ContentValues();
			insertValues.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMITTED);
			String where = InstanceColumns.INSTANCE_FILE_PATH + "=?";
			String whereArgs[] = { path };
			int updatedrows = mContext.getContentResolver().update(InstanceColumns.CONTENT_URI, insertValues, where, whereArgs);

			if (updatedrows > 1) {
				++syncResult.stats.numIoExceptions;
				Log.e(TAG, "Error! updated more than one entry when tyring to update: " + path.toString());
			} else if (updatedrows == 1) {
				Log.i(TAG, "Instance successfully updated to Submitted Status");
			} else {
				++syncResult.stats.numIoExceptions;
				Log.e(TAG, "Error, Instance doesn't exist but we have its path!! " + path.toString());
			}

		} catch (Exception e) {
			e.printStackTrace();
			++syncResult.stats.numIoExceptions;
			return e.getLocalizedMessage();
		}

		return null;
	}

	// DOWNLOAD FORMS SECTION
	/**
	 * Inserts a list of forms into the AccessMRS form list database
	 * 
	 * @param doc
	 *            Document created from parsed input stream
	 * @throws Exception
	 */
	public ArrayList<Form> readFormListStream(InputStream is) throws Exception {

		ArrayList<Form> allForms = new ArrayList<Form>();
		Document doc = null;
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		doc = db.parse(is);
		if (doc == null)
			return allForms;

		// clean existing
		DbProvider dbHelper = DbProvider.openDb();
		dbHelper.delete(DataModel.FORMS_TABLE, null, null);

		// make new form list
		NodeList formElements = doc.getElementsByTagName("xform");
		sLoopCount = formElements.getLength();
		sLoopProgress = 0;
		Form f;
		Element n;
		String formName;
		String formId;
		for (int i = 0; i < sLoopCount; i++) {

			n = (Element) formElements.item(i);
			formName = n.getElementsByTagName("name").item(0).getChildNodes().item(0).getNodeValue();
			formId = n.getElementsByTagName("id").item(0).getChildNodes().item(0).getNodeValue();

			f = new Form();
			f.setName(formName);
			f.setFormId(Integer.valueOf(formId));
			Db.open().createForm(f);
			allForms.add(f);

			sLoopProgress++;
		}

		return allForms;
	}

	public String updateDownloadedForms(Form[] downloaded, SyncResult syncResult) {
		StringBuilder error = new StringBuilder();
		if (downloaded.length > 0) {

			// update the databases with new status submitted
			sLoopProgress = 0;
			sLoopCount = downloaded.length;
			for (int i = 0; i < downloaded.length; i++) {
				try {
					// update AccessMRS
					Db.open().updateFormPath(downloaded[i].getFormId(), downloaded[i].getPath());

					// update collect
					if (!XformUtils.insertSingleForm(downloaded[i].getPath()))
						UiUtils.toastSyncMessage(null, "ODK Collect not initialized.", true);

					sLoopProgress++;

				} catch (Exception e) {
					e.printStackTrace();
					++syncResult.stats.numIoExceptions;
					error.append(" Download Form ").append(i).append(": ").append(e.getLocalizedMessage());
				}
			}
		}

		return error.toString();

	}

	// DOWNLOAD OBS SECTION
	public String readObsFile(File tempFile, SyncResult syncResult) {

		if (tempFile == null)
			return "error";

		try {

			DataInputStream dis = new DataInputStream(new FileInputStream(tempFile));

			if (dis != null) {
				DbProvider dbHelper = DbProvider.openDb();
				// open db and clean entries
				dbHelper.delete(DataModel.PATIENTS_TABLE, DataModel.KEY_CLIENT_CREATED + " IS NULL", null);
				dbHelper.delete(DataModel.OBSERVATIONS_TABLE, null, null);

				insertPatients(dis);
				addSyncStep(mContext.getString(R.string.sync_updating_data), false); // 70%
				insertObservations(dis);

				try {
					addSyncStep(mContext.getString(R.string.sync_updating_data), false); // 90% (doubled due to slow speed)
					insertPatientForms(dis);
				} catch (EOFException e) {
					// do nothing for EOFExceptions in this case
					Log.i(TAG, "No SmartForms available on server");
				}

				dis.close();
			}

			updateAccessMrsObs();
			addSyncStep(mContext.getString(R.string.sync_updating_data), false); // 100%
			
		} catch (Exception e) {
			e.printStackTrace();
			++syncResult.stats.numIoExceptions;
			return e.getLocalizedMessage();
		}

		return null;
	}

	public void updateAccessMrsObs() {
		// sync db

		Db.open().updatePriorityFormNumbers();
		Db.open().updatePriorityFormList();
		Db.open().updateSavedFormNumbers();
		Db.open().updateSavedFormsList();

		// log the event
		Db.open().createDownloadLog();
	}

	private void insertPatients(DataInputStream zdis) throws Exception {

		long start = System.currentTimeMillis();
		SQLiteDatabase db = DbProvider.getDb();

		SimpleDateFormat output = new SimpleDateFormat("MMM dd, yyyy");
		SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd");

		InsertHelper ih = new InsertHelper(db, DataModel.PATIENTS_TABLE);
		int ptIdIndex = ih.getColumnIndex(DataModel.KEY_PATIENT_ID);
		int ptIdentifierIndex = ih.getColumnIndex(DataModel.KEY_IDENTIFIER);
		int ptGivenIndex = ih.getColumnIndex(DataModel.KEY_GIVEN_NAME);
		int ptFamilyIndex = ih.getColumnIndex(DataModel.KEY_FAMILY_NAME);
		int ptMiddleIndex = ih.getColumnIndex(DataModel.KEY_MIDDLE_NAME);
		int ptBirthIndex = ih.getColumnIndex(DataModel.KEY_BIRTH_DATE);
		int ptGenderIndex = ih.getColumnIndex(DataModel.KEY_GENDER);

		db.beginTransaction();
		sLoopProgress = 0;
		try {
			sLoopCount = zdis.readInt();
			Log.i(TAG, "insertPatients icount: " + sLoopCount);
			for (int i = 1; i < sLoopCount + 1; i++) {

				ih.prepareForInsert();
				ih.bind(ptIdIndex, zdis.readInt());
				ih.bind(ptFamilyIndex, zdis.readUTF());
				ih.bind(ptMiddleIndex, zdis.readUTF());
				ih.bind(ptGivenIndex, zdis.readUTF());
				ih.bind(ptGenderIndex, zdis.readUTF());
				ih.bind(ptBirthIndex, parseDate(input, output, zdis.readUTF()));
				ih.bind(ptIdentifierIndex, zdis.readUTF());
				ih.execute();

				sLoopProgress++;
			}
			db.setTransactionSuccessful();
		} finally {
			ih.close();
			db.endTransaction();
		}

		long end = System.currentTimeMillis();
		Log.i("END InsertPts", String.format("InsertPts Speed: %d ms, Records per second: %.2f", (int) (end - start), 1000 * (double) sLoopCount / (double) (end - start)));

	}

	private void insertObservations(DataInputStream zdis) throws Exception {
		long start = System.currentTimeMillis();

		SimpleDateFormat output = new SimpleDateFormat("MMM dd, yyyy");
		SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd");

		SQLiteDatabase db = DbProvider.getDb();
		InsertHelper ih = new InsertHelper(db, DataModel.OBSERVATIONS_TABLE);
		int ptIdIndex = ih.getColumnIndex(DataModel.KEY_PATIENT_ID);
		int obsTextIndex = ih.getColumnIndex(DataModel.KEY_VALUE_TEXT);
		int obsNumIndex = ih.getColumnIndex(DataModel.KEY_VALUE_NUMERIC);
		int obsDateIndex = ih.getColumnIndex(DataModel.KEY_VALUE_DATE);
		int obsIntIndex = ih.getColumnIndex(DataModel.KEY_VALUE_INT);
		int obsFieldIndex = ih.getColumnIndex(DataModel.KEY_FIELD_NAME);
		int obsTypeIndex = ih.getColumnIndex(DataModel.KEY_DATA_TYPE);
		int obsEncDateIndex = ih.getColumnIndex(DataModel.KEY_ENCOUNTER_DATE);

		db.beginTransaction();
		sLoopProgress = 0;
		int count = 0;
		try {
			sLoopCount = zdis.readInt();
			Log.i(TAG, "insertObservations icount: " + sLoopCount);
			for (int i = 1; i < sLoopCount + 1; i++) {

				ih.prepareForInsert();
				ih.bind(ptIdIndex, zdis.readInt());
				ih.bind(obsFieldIndex, zdis.readUTF());
				byte dataType = zdis.readByte();
				if (dataType == DataModel.TYPE_STRING) {
					ih.bind(obsTextIndex, zdis.readUTF());
				} else if (dataType == DataModel.TYPE_INT) {
					ih.bind(obsIntIndex, zdis.readInt());
				} else if (dataType == DataModel.TYPE_DOUBLE) {
					ih.bind(obsNumIndex, zdis.readDouble());
				} else if (dataType == DataModel.TYPE_DATE) {
					ih.bind(obsDateIndex, parseDate(input, output, zdis.readUTF()));
				}

				ih.bind(obsTypeIndex, dataType);
				ih.bind(obsEncDateIndex, parseDate(input, output, zdis.readUTF()));
				ih.execute();
				
				count++;
				sLoopProgress = count * 2;
			}

			db.setTransactionSuccessful();
		} finally {
			ih.close();

			db.endTransaction();
		}

		long end = System.currentTimeMillis();
		Log.i("END InsertObs", String.format("InsertObs Speed: %d ms, Records per second: %.2f", (int) (end - start), 1000 * (double) sLoopCount / (double) (end - start)));

	}

	private void insertPatientForms(final DataInputStream zdis) throws Exception {
		long start = System.currentTimeMillis();

		SimpleDateFormat output = new SimpleDateFormat("MMM dd, yyyy");
		SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd");

		SQLiteDatabase db = DbProvider.getDb();
		InsertHelper ih = new InsertHelper(db, DataModel.OBSERVATIONS_TABLE);

		int ptIdIndex = ih.getColumnIndex(DataModel.KEY_PATIENT_ID);
		int obsTextIndex = ih.getColumnIndex(DataModel.KEY_VALUE_TEXT);
		int obsNumIndex = ih.getColumnIndex(DataModel.KEY_VALUE_NUMERIC);
		int obsDateIndex = ih.getColumnIndex(DataModel.KEY_VALUE_DATE);
		int obsIntIndex = ih.getColumnIndex(DataModel.KEY_VALUE_INT);
		int obsFieldIndex = ih.getColumnIndex(DataModel.KEY_FIELD_NAME);
		int obsTypeIndex = ih.getColumnIndex(DataModel.KEY_DATA_TYPE);
		int obsEncDateIndex = ih.getColumnIndex(DataModel.KEY_ENCOUNTER_DATE);

		db.beginTransaction();
		sLoopProgress = 0;
		try {
			sLoopProgress = zdis.readInt();
			Log.i(TAG, "insertPatientForms icount: " + sLoopCount);
			for (int i = 1; i < sLoopCount + 1; i++) {

				ih.prepareForInsert();
				ih.bind(ptIdIndex, zdis.readInt());
				ih.bind(obsFieldIndex, zdis.readUTF());
				byte dataType = zdis.readByte();
				if (dataType == DataModel.TYPE_STRING) {
					ih.bind(obsTextIndex, zdis.readUTF());
				} else if (dataType == DataModel.TYPE_INT) {
					ih.bind(obsIntIndex, zdis.readInt());
				} else if (dataType == DataModel.TYPE_DOUBLE) {
					ih.bind(obsNumIndex, zdis.readDouble());
				} else if (dataType == DataModel.TYPE_DATE) {
					ih.bind(obsDateIndex, parseDate(input, output, zdis.readUTF()));
				}
				ih.bind(obsTypeIndex, dataType);
				ih.bind(obsEncDateIndex, parseDate(input, output, zdis.readUTF()));
				ih.execute();

				sLoopProgress++;
			}
			db.setTransactionSuccessful();
		} finally {
			ih.close();
			db.endTransaction();
		}

		long end = System.currentTimeMillis();
		Log.i("END InsertPtsForms", String.format("InsertPtsForms Speed: %d ms, Records per second: %.2f", (int) (end - start), 1000 * (double) sLoopCount / (double) (end - start)));

	}

	private String parseDate(SimpleDateFormat input, SimpleDateFormat output, String s) {

		try {
			return output.format(input.parse(s.split("T")[0]));
		} catch (ParseException e) {
			return "Unknown date";
		}
	}

	public void toastSyncResult(int totalErrors, String errorMessage) {

		StringBuilder result = new StringBuilder();
		result.append(mSyncResultString);
		if (totalErrors > 0) {
			result.append(" (").append(mContext.getResources().getQuantityString(R.plurals.errors, totalErrors, totalErrors));
			if (errorMessage != null)
				result.append(" : ").append(errorMessage);
			result.append(")");
		}

		// Send to Activity
		Intent broadcast = new Intent(SYNC_MESSAGE);
		broadcast.putExtra(TOAST_MESSAGE, result.toString());
		broadcast.putExtra(TOAST_ERROR, (totalErrors == 0) ? false : true);
		LocalBroadcastManager.getInstance(mContext).sendBroadcast(broadcast);

	}

	public void toastSyncUpdate(int syncType, int success, int total, int currentErrors, String dbError) {
		Log.v(TAG, "toasting a sync message with parameters=" + syncType + ", " + success + ", " + total + ", " + currentErrors + ", " + dbError);
		String currentToast = createToastString(syncType, success, total, currentErrors);
		if (currentToast != null) {

			StringBuilder result = new StringBuilder();
			result.append(currentToast);
			if (currentErrors > 0) {
				result.append(" (").append(mContext.getResources().getQuantityString(R.plurals.errors, currentErrors, currentErrors));
				if (dbError != null && !dbError.equalsIgnoreCase(""))
					result.append(" : ").append(dbError);
				result.append(")");
			}

			// Send to Activity
			Intent broadcast = new Intent(SYNC_MESSAGE);
			broadcast.putExtra(TOAST_MESSAGE, result.toString());
			broadcast.putExtra(TOAST_ERROR, (currentErrors == 0) ? false : true);
			LocalBroadcastManager.getInstance(mContext).sendBroadcast(broadcast);
		}
	}

	private String createToastString(int syncMethod, int success, int total, int currentErrors) {

		String toast = null;

		// Get string based on sync method
		int successNoSync = 0;
		int successAllSync = 0;
		int failPartialSync = 0;
		int failNoSync = 0;
		switch (syncMethod) {
		case UPLOAD_FORMS:
			failNoSync = R.string.sync_upload_forms_failed;
			failPartialSync = R.plurals.sync_upload_forms_partial;
			successNoSync = R.string.sync_upload_forms_not_needed;
			successAllSync = R.plurals.sync_upload_forms_successful;
			break;
		case DOWNLOAD_FORMS:
			failNoSync = R.string.sync_download_forms_failed;
			failPartialSync = R.plurals.sync_download_forms_partial;
			successNoSync = R.string.sync_download_forms_not_needed;
			successAllSync = R.plurals.sync_download_forms_successful;
			break;
		case DOWNLOAD_OBS:
			failNoSync = R.string.sync_download_obs_failed;
			// don't toast success
			break;
		default:
			return mSyncResultString;
		}

		// ERRORS: Add to Current Toast
		// Error 1: Exception
		if (currentErrors > 0)
			toast = mContext.getString(failNoSync);
		// Error 2: Partial upload/download
		else if (total > 0 && success != total)
			toast = mContext.getResources().getQuantityString(failPartialSync, total, String.valueOf(total - success) + " of " + String.valueOf(total));

		// SUCCESS: Add to SyncResult Toast (Don't show until Sync Complete)
		else {
			StringBuilder sb = new StringBuilder();
			if (mSyncResultString != null) {
				sb.append(mSyncResultString);
				sb.append(". ");
			}
			// Success 1: Nothing to upload/download
			if (total == 0 && currentErrors == 0)
				sb.append(mContext.getString(successNoSync));
			// Success 2: All uploads/downloads were successful
			else if (total > 0 && success == total)
				sb.append(mContext.getResources().getQuantityString(successAllSync, total, String.valueOf(total)));
			mSyncResultString = sb.toString();
		}
		return toast;
	}

}

// private boolean isSyncActiveOrPending() {
// AccountManager accountManager = AccountManager.get(mContext);
// Account[] accounts =
// accountManager.getAccountsByType(mContext.getString(R.string.app_account_type));
//
// if (accounts.length <= 0)
// return false;
// else
// return ContentResolver.isSyncActive(accounts[0],
// getString(R.string.app_provider_authority));
// }

// current = (int) Math.round(((float) i / (float) icount) *
// 2.0);
// if (current != progress) {
// Log.i(TAG, "i=" + i + " current=" + current + " /progress=" +
// progress + " icount=" + icount + " mProgress" +
// sSyncProgress);
// progress = current;
// sSyncProgress = sSyncProgress + current;
// }
