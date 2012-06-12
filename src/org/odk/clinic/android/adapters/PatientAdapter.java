package org.odk.clinic.android.adapters;

import java.util.List;

import org.odk.clinic.android.R;
import org.odk.clinic.android.openmrs.Patient;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class PatientAdapter extends ArrayAdapter<Patient> {
	Context mContext;

	public PatientAdapter(Context context, int textViewResourceId, List<Patient> items) {
		super(context, textViewResourceId, items);
		mContext = context;
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		View v = convertView;
		Resources res = mContext.getResources();
		if (v == null) {
			LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			v = vi.inflate(R.layout.patient_list_item, null);
		}
		Patient p = getItem(position);
		if (p != null) {

			TextView textView = (TextView) v.findViewById(R.id.identifier_text);
			if (textView != null) {
				textView.setText(p.getIdentifier());
			}

			TextView nameView = (TextView) v.findViewById(R.id.name_text);
			if (nameView != null) {
				StringBuilder nameBuilder = new StringBuilder();
				nameBuilder.append(p.getGivenName());
				nameBuilder.append(' ');
				nameBuilder.append(p.getMiddleName());
				nameBuilder.append(' ');
				nameBuilder.append(p.getFamilyName());
				nameView.setText(nameBuilder.toString());
			}

			textView = (TextView) v.findViewById(R.id.birthdate_text);
			if (textView != null) {
				textView.setText(p.getAge());
			}

			ImageView imageView = (ImageView) v.findViewById(R.id.gender_image);
			if (imageView != null) {
				if (p.getGender().equals("M")) {
					imageView.setImageResource(R.drawable.male_gray);
				} else if (p.getGender().equals("F")) {
					imageView.setImageDrawable(res.getDrawable(R.drawable.female_gray));
				}
			}

			ImageView priorityArrow = (ImageView) v.findViewById(R.id.arrow_image);
			ImageView priorityImage = (ImageView) v.findViewById(R.id.priority_image);
			TextView priorityNumber = (TextView) v.findViewById(R.id.priority_number);
			
			priorityImage.setImageDrawable(res.getDrawable(R.drawable.priority_icon_blank));



			if (priorityArrow != null && nameView != null) {
				if (p.getPriority()) {
					priorityArrow.setImageResource(R.drawable.arrow_red);
//					nameView.setTextColor(res.getColor(R.color.priority));
					nameView.setTextColor(res.getColor(R.color.dark_gray));

					if (priorityNumber != null && priorityImage != null) {
						priorityNumber.setText(p.getPriorityNumber().toString());
						priorityImage.setVisibility(View.VISIBLE);
						
					}

				} else {
					priorityArrow.setImageResource(R.drawable.arrow_gray);
					nameView.setTextColor(res.getColor(R.color.dark_gray));
					
					if (priorityNumber != null && priorityImage != null) {
						priorityNumber.setText(null);
						priorityImage.setVisibility(View.INVISIBLE);

					}
				}
			}
		}
		return v;
	}
}
