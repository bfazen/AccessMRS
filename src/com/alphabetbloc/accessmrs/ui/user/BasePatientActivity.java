/*
 * Copyright (C) 2012 Louis Fazen
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
package com.alphabetbloc.accessmrs.ui.user;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.alphabetbloc.accessmrs.R;
import com.alphabetbloc.accessmrs.data.Patient;
import com.alphabetbloc.accessmrs.providers.Db;

/**
 * Displays ArrayList<Form> in a Time-Separated List with patient header
 * 
 * @author Louis Fazen (louis.fazen@gmail.com) 
 * 
 */

public abstract class BasePatientActivity extends BaseUserActivity  {

	// intent extras
	public static final String KEY_PATIENT_ID = "PATIENT_ID";
	public static final String KEY_OBSERVATION_FIELD_NAME = "KEY_OBSERVATION_FIELD_NAME";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

	}

	protected void createPatientHeader(Integer patientId) {

		Patient focusPt = Db.open().getPatient(patientId);

		TextView textView = (TextView) findViewById(R.id.identifier_text);
		if (textView != null) {
			textView.setText(focusPt.getIdentifier());
		}

		textView = (TextView) findViewById(R.id.name_text);
		if (textView != null) {
			StringBuilder nameBuilder = new StringBuilder();
			nameBuilder.append(focusPt.getGivenName());
			nameBuilder.append(' ');
			nameBuilder.append(focusPt.getMiddleName());
			nameBuilder.append(' ');
			nameBuilder.append(focusPt.getFamilyName());
			textView.setText(nameBuilder.toString());
		}

		textView = (TextView) findViewById(R.id.birthdate_text);
		if (textView != null) {
			textView.setText(focusPt.getBirthdate());
		}

		ImageView imageView = (ImageView) findViewById(R.id.gender_image);
		if (imageView != null) {
			if (focusPt.getGender().equals("M")) {
				imageView.setImageResource(R.drawable.male_gray);
			} else if (focusPt.getGender().equals("F")) {
				imageView.setImageResource(R.drawable.female_gray);
			}
		}

	}

	protected View buildSectionLabel(String section, boolean icon) {
		View v;
		LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		v = vi.inflate(R.layout.section_label, null);
		v.setBackgroundResource(R.color.medium_gray);
		TextView textView = (TextView) v.findViewById(R.id.name_text);
		ImageView sectionImage = (ImageView) v.findViewById(R.id.section_image);
		if (icon)
			sectionImage.setVisibility(View.VISIBLE);
		else
			sectionImage.setVisibility(View.GONE);
		textView.setText(section);
		return (v);
	}

	// BUTTONS: Dont show the menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return false;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return false;
	}

	// LIFECYCLE
	@Override
	protected void onPause() {
		super.onPause();
	}
}