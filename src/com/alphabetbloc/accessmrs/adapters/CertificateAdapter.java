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
package com.alphabetbloc.accessmrs.adapters;

import java.util.List;

import com.alphabetbloc.accessmrs.data.Certificate;
import com.alphabetbloc.accessmrs.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class CertificateAdapter extends ArrayAdapter<Certificate> {
	private boolean mLocalCert;
	int defaultLayoutId;
	Context mContext;

	public CertificateAdapter(Context context, int textViewResourceId, List<Certificate> items, Boolean local) {
		super(context, textViewResourceId, items);
		mLocalCert = local;
		defaultLayoutId = textViewResourceId;
		mContext = context;
	}

	public View getView(int position, View convertView, ViewGroup parent) {

		View v = convertView;
		LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		v = vi.inflate(defaultLayoutId, null);
		Certificate cert = getItem(position);

		if (cert != null) {
			//keys
			if(defaultLayoutId == R.layout.key_item){
				TextView title = (TextView) v.findViewById(R.id.name_text);
				TextView filename = (TextView) v.findViewById(R.id.file_name_text);
				TextView date = (TextView) v.findViewById(R.id.date_text);
				
				title.setText(cert.getName());
				filename.setText(cert.getAlias());
				date.setText(cert.getDate());
				
				return v;
			}
			
			//certs
			// Name
			setCertificateView(v, -1, R.id.o_text, cert.getO());

			// Division
			setCertificateView(v, R.id.title_ou_text, R.id.ou_text, cert.getOU());

			// Domain Name
			setCertificateView(v, R.id.title_cn_text, R.id.cn_text, cert.getCN());

			// Location
			TextView locTitle = (TextView) v.findViewById(R.id.title_l_text);
			if (cert.hasLocation()) {
				locTitle.setVisibility(View.VISIBLE);
				if (cert.getL() != null && (cert.getST() != null || cert.getC() != null))
					setCertificateView(v, -1, R.id.l_text, cert.getL() + ", ");
				else
					setCertificateView(v, -1, R.id.l_text, cert.getL());
				if (cert.getST() != null && cert.getC() != null)
					setCertificateView(v, -1, R.id.st_text, cert.getST() + ", ");
				else
					setCertificateView(v, -1, R.id.st_text, cert.getST());
				setCertificateView(v, -1, R.id.c_text, cert.getC());
			} else
				locTitle.setVisibility(View.GONE);

			// Email
			setCertificateView(v, R.id.title_e_text, R.id.e_text, cert.getE());

			// Issued By
			setCertificateView(v, R.id.title_io_text, R.id.io_text, cert.getIO());

			// Edit Local Certs on click...:
			ImageView deleteIv = (ImageView) v.findViewById(R.id.delete_image);
			if (mLocalCert)
				deleteIv.setVisibility(View.VISIBLE);
			else
				deleteIv.setVisibility(View.GONE);
		}
		return v;
	}

	
	
	private void setCertificateView(View v, int title, int content, String certString) {
		TextView titleTv = null;
		TextView contentTv = null;
		if (certString != null) {
			contentTv = (TextView) v.findViewById(content);
			contentTv.setText(certString);

			if (title > 0) {
				titleTv = (TextView) v.findViewById(title);
				titleTv.setVisibility(View.VISIBLE);
			}
		} else {
			contentTv = (TextView) v.findViewById(content);
			ViewParent rl = (RelativeLayout) contentTv.getParent();
			((View) rl).setVisibility(View.GONE);
			// contentTv.setVisibility(View.GONE);
			//
			// if (title > 0) {
			// titleTv = (TextView) v.findViewById(title);
			// titleTv.setVisibility(View.GONE);
			// }
		}
	}

	@Override
	public boolean isEnabled(int position) {
		if(mLocalCert)
			return true;
		else 
			return false;
	}
	
	
	
	

}
