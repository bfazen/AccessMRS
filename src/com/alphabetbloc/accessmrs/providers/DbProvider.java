package com.alphabetbloc.accessmrs.providers;

import net.sqlcipher.DatabaseUtils;
import net.sqlcipher.database.SQLiteConstraintException;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.alphabetbloc.accessmrs.utilities.App;
import com.alphabetbloc.accessmrs.utilities.EncryptionUtil;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * @author Yaw Anokwa (I think the only one... but could be Sam Mbugua?)
 */
public class DbProvider extends ContentProvider {

	private final static String TAG = DbProvider.class.getSimpleName();
	private static DbProvider instance;
	private static SQLiteDatabase sDb;

	public static class DatabaseHelper extends SQLiteOpenHelper {

		// we create this once from the App singleton
		public DatabaseHelper(Context context) {
			super(context, DataModel.DATABASE_NAME, null, DataModel.DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			if (App.DEBUG) Log.v(TAG, "creating tables");
			db.execSQL(DataModel.CREATE_PATIENTS_TABLE);
			db.execSQL(DataModel.CREATE_OBSERVATIONS_TABLE);
			db.execSQL(DataModel.CREATE_FORMS_TABLE);
			db.execSQL(DataModel.CREATE_FORMINSTANCES_TABLE);
			db.execSQL(DataModel.CREATE_FORM_LOG_TABLE);
			db.execSQL(DataModel.CREATE_DOWNLOAD_LOG_TABLE);
			db.execSQL(DataModel.CREATE_SYNC_TABLE);
		}

		@Override
		// upgrading will destroy all old data
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL("DROP TABLE IF EXISTS " + DataModel.PATIENTS_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + DataModel.OBSERVATIONS_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + DataModel.FORMS_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + DataModel.FORMINSTANCES_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + DataModel.FORM_LOG_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + DataModel.DOWNLOAD_LOG_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + DataModel.SYNC_TABLE);
			onCreate(db);
		}
	}

	public static void createDb() {
		String password = EncryptionUtil.getPassword();
		if (password != null)
			sDb = App.getDb(password);
		else {
			// we lost our db password! : ask user for it...
			Log.e(TAG, "We lost the DB Password!");
		}

	}

	public static DbProvider openDb() {
		if (!isOpen()) {
			if(App.DEBUG) Log.v(TAG, "Database is not open! Opening Now!");
			createDb();
		}

		return getInstance();
	}

	public static synchronized DbProvider getInstance() {
		if (instance == null) {
			instance = new DbProvider();
		}
		return instance;
	}

	public static boolean isOpen() {
		if (sDb != null && sDb.isOpen())
			return true;
		else
			return false;
	}

	public static boolean rekeyDb(String password) {
		Exception error = null;

		try {
			sDb.execSQL("PRAGMA rekey = '" + password + "'");
			System.gc();
		} catch (Exception e) {
			error = e;
			Log.e(TAG, "Error rekeying the database: " + e.getMessage());
		}

		if (error == null)
			return true;
		else
			return false;
	}

	public static void lock() {
		sDb.close();
		sDb = null;
	}

	public static SQLiteDatabase getDb() {
		if (sDb == null)
			createDb();

		return sDb;
	}

	// TODO Remove the need to pass in Patient/Observation objects. Saves object
	// creation
	/**
	 * Generate text for the first level of the row display.
	 * 
	 * @param path
	 *            path to the file
	 * @param type
	 *            type of the file
	 * @return name of the file formatted as human readable string
	 */
	/*
	 * private String generateDisplay(String path) { String filename =
	 * path.substring(path.lastIndexOf("/") + 1);
	 * 
	 * //remove the form id filename=filename.substring(filename.indexOf('_') +
	 * 1);
	 * 
	 * // remove time stamp from instance String r =
	 * "\\_[0-9]{4}\\-[0-9]{2}\\-[0-9]{2}\\_[0-9]{2}\\-[0-9]{2}\\-[0-9]{2}\\.xml$"
	 * ; Pattern p = Pattern.compile(r); return "Patient (" +
	 * p.split(filename)[0] + ")"; }
	 */

	/**
	 * Remove all patients from the database.
	 * 
	 * @return number of affected rows
	 */
	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		// TODO Auto-generated method stub
		return null;
	}

	public long insert(String table, ContentValues cv) {
		long id = -1;
		try {
			id = sDb.insert(table, null, cv);
		} catch (SQLiteConstraintException e) {
			Log.e(TAG, "Caught SQLiteConstraitException: " + e);
		}

		return id;
	}

	@Override
	public boolean onCreate() {
		// From AccessForms (this gets called before the app is created, so we do
		// nothing) AccessForms has it set to true though?
		return false;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

	public boolean update(String table, ContentValues values, String selection, String[] selectionArgs) {
		return sDb.update(table, values, selection, selectionArgs) > 0;
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

	public boolean delete(String table, String selection, String[] selectionArgs) {
		if (App.DEBUG) Log.v(TAG, "DB Delete FROM " + table + " WHERE " + selection + " = " + selectionArgs);
		return sDb.delete(table, selection, selectionArgs) > 0;
	}

	// QUERY
	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		// TODO Auto-generated method stub
		return null;
	}

	public Cursor queryDistinct(String table, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		return sDb.query(true, table, projection, selection, selectionArgs, null, null, sortOrder, null);
	}

	public Cursor query(String table, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		return sDb.query(table, projection, selection, selectionArgs, null, null, sortOrder, null);
	}

	public Cursor query(boolean distinct, String table, String[] projection, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
		return sDb.query(distinct, table, projection, selection, selectionArgs, groupBy, having, orderBy, limit);
	}

	public Cursor query(String table, String[] projection, String selection, String[] selectionArgs, String groupBy, String having, String orderBy) {
		return sDb.query(table, projection, selection, selectionArgs, groupBy, having, orderBy);
	}
	
	public Cursor rawQuery(String sql, String[] args) {
		return sDb.rawQuery(sql, args);
	}

	public int countRows(String table) {
		long count = 0;
		count = DatabaseUtils.queryNumEntries(sDb, table);
		int intcount = safeLongToInt(count);
		return intcount;
	}

	private static int safeLongToInt(long l) {
		if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
			throw new IllegalArgumentException(l + "is outside the bounds of int");
		}
		return (int) l;
	}

}