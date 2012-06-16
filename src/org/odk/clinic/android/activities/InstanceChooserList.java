/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.clinic.android.activities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.odk.clinic.android.R;
import org.odk.clinic.android.adapters.FormAdapter;
import org.odk.clinic.android.adapters.MergeAdapter;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.openmrs.ActivityLog;
import org.odk.clinic.android.openmrs.Form;
import org.odk.clinic.android.utilities.App;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Responsible for displaying all the valid instances in the instance directory.
 * 
 * @author Yaw Anokwa (yanokwa@gmail.com)
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class InstanceChooserList extends ListActivity {

	private ArrayList<Form> mTotalForms = new ArrayList<Form>();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.example_cw_main);
		setTitle(getString(R.string.app_name) + " > " + "Previous Encounters");
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
	}

	/**
	 * Stores the path of selected instance in the parent class and finishes.
	 */
	@Override
	protected void onListItemClick(ListView listView, View view, int position, long id) {
		Form f = (Form) getListAdapter().getItem(position);
		String formIdStr = f.getFormId().toString();
		launchFormEntry(formIdStr);
	}

	private void refreshView() {
		MergeAdapter adapter = new MergeAdapter();

		// Cursor c = run query here...
		String selection = InstanceColumns.STATUS + " != ?";
		String[] selectionArgs = { InstanceProviderAPI.STATUS_SUBMITTED };
		Cursor c = managedQuery(InstanceColumns.CONTENT_URI, null, selection, selectionArgs, InstanceColumns.STATUS + " desc");
		
		Cursor mCursor = App.getApp().getContentResolver().query(FormsColumns.CONTENT_URI, null, null, null, null);
		mCursor.moveToPosition(-1);
		while (mCursor.moveToNext()) {

			int dbid = mCursor.getInt(mCursor.getColumnIndex(FormsColumns._ID));
			String dbjrFormId = mCursor.getString(mCursor.getColumnIndex(FormsColumns.JR_FORM_ID));

			formPath = mCursor.getString(mCursor.getColumnIndex(FormsColumns.FORM_FILE_PATH));

			if (jrFormId.equalsIgnoreCase(dbjrFormId)) {
				id = dbid;
				break;
			}
		}
		if (mCursor != null) {
			mCursor.close();
		}
		
		
		
		
		
		String[] data = new String[] { InstanceColumns.DISPLAY_NAME, InstanceColumns.DISPLAY_SUBTEXT };
		int[] view = new int[] { R.id.text1, R.id.text2 };

		Uri instanceUri = ContentUris.withAppendedId(InstanceColumns.CONTENT_URI, c.getLong(c.getColumnIndex(InstanceColumns._ID)));

		// adds the forms to the list
		if (c != null && c.getCount() >= 0) {
			mTotalForms.clear();
			int formIdIndex = c.getColumnIndex(ClinicAdapter.KEY_FORM_ID);
			int nameIndex = c.getColumnIndex(ClinicAdapter.KEY_NAME);
			int pathIndex = c.getColumnIndex(ClinicAdapter.KEY_PATH);

			if (c.getCount() > 0) {
				Form form;
				do {
					if (!c.isNull(formIdIndex)) {
						form = new Form();
						form.setFormId(c.getInt(formIdIndex));
						form.setName(c.getString(nameIndex));
						form.setPath(c.getString(pathIndex));
						mTotalForms.add(form);
					}
				} while (c.moveToNext());
			}
		}

		if (c != null)
			c.close();

		Collections.shuffle(mTotalForms);
		FormAdapter formAdapter = new FormAdapter(this, android.R.layout.simple_list_item_1, mTotalForms, false);
		adapter.addAdapter(formAdapter);
		// add some section titles in here for different dates...
		setListAdapter(adapter);

		String action = getIntent().getAction();
		if (Intent.ACTION_PICK.equals(action)) {
			// caller is waiting on a picked form
			setResult(RESULT_OK, new Intent().setData(instanceUri));
		} else {
			// the form can be edited only if it is marked incomplete in Collect
			// Db (not worrying about encryption here)
			String status = c.getString(c.getColumnIndex(InstanceColumns.STATUS));
			boolean canEdit = status.equals(InstanceProviderAPI.STATUS_INCOMPLETE);

			// caller wants to view/edit a form, so launch formentryactivity
			startActivity(new Intent(Intent.ACTION_EDIT, instanceUri));
			// Need to somehow start the activity in Collect but prevent it from
			// saving the form... or maybe allow it to work, but not allow it to
			// save?

		}
		finish();
	}

	private void launchFormEntry(String jrFormId) {

		
//		louis.fazen is adding this one line
		intent.putExtra("edit_form", false);
		
//		then need to figure out how to do the db query etc....
		
		String formPath = null;
		int id = -1;
		try {
			// louis.fazen notes... this queries all the columns in the collect?
			// Db and searches for an Id that is the same as the requesting id
			Cursor mCursor = App.getApp().getContentResolver().query(FormsColumns.CONTENT_URI, null, null, null, null);
			mCursor.moveToPosition(-1);
			while (mCursor.moveToNext()) {

				int dbid = mCursor.getInt(mCursor.getColumnIndex(FormsColumns._ID));
				String dbjrFormId = mCursor.getString(mCursor.getColumnIndex(FormsColumns.JR_FORM_ID));

				formPath = mCursor.getString(mCursor.getColumnIndex(FormsColumns.FORM_FILE_PATH));

				if (jrFormId.equalsIgnoreCase(dbjrFormId)) {
					id = dbid;
					break;
				}
			}
			if (mCursor != null) {
				mCursor.close();
			}
			// louis.fazen notes... if it finds a match, then call
			// createFormInstance and inject some values into the form...
			// if it that fails, then it still needs to find a uri for the form
			// to launch, so it allows opportunity to edit the uri?
			//

			if (id != -1) {

				// create instance
				int instanceId = createFormInstance(formPath, jrFormId);

				if (instanceId != -1) {
					Intent intent = new Intent();
					intent.setComponent(new ComponentName("org.odk.collect.android", "org.odk.collect.android.activities.FormEntryActivity"));
					intent.setAction(Intent.ACTION_EDIT);
					intent.setData(Uri.parse(InstanceColumns.CONTENT_URI + "/" + instanceId));

					startActivity(intent);

				} else {
					Uri formUri = ContentUris.withAppendedId(FormsColumns.CONTENT_URI, id);
					startActivity(new Intent(Intent.ACTION_EDIT, formUri));
				}

			}

		} catch (ActivityNotFoundException e) {
			showCustomToast(getString(R.string.error, getString(R.string.odk_collect_error)));
		}
	}
}
