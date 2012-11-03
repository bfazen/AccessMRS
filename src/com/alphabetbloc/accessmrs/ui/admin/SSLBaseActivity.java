package com.alphabetbloc.accessmrs.ui.admin;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.alphabetbloc.accessmrs.data.Certificate;
import com.alphabetbloc.accessmrs.R;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 *
 */

public abstract class SSLBaseActivity extends BaseAdminListActivity {
//	private static final String TAG = ManageSSLActivity.class.getSimpleName();
	
	// Override
	private String mStoreString;
	private String mStoreTitleString;
	private String mImportFormat;
	
	protected void refreshView() {
		findViewById(android.R.id.content).invalidate();
		showStoreItems();
	}

	protected void setStoreString(String storeString){
		mStoreString = storeString;
	}
	
	protected void setStoreTitleString(String storeTitleString){
		mStoreTitleString = storeTitleString;
	}
	
	protected void setImportFormat(String importFormat){
		mImportFormat = importFormat;
	}
	
	protected String getStoreString(){
		return mStoreString;
	}
	
	protected String getStoreTitleString(){
		return mStoreTitleString;
	}
	
	protected String getImportFormat(){
		return mImportFormat;
	}
	
	protected abstract ArrayList<Certificate> getStoreFiles();

	protected abstract void showStoreItems();

	protected abstract void remove(String alias);

	protected abstract void addFromExternalStroage();

	protected View buildSectionLabel(int layoutId, String label) {
		LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = vi.inflate(layoutId, null);
		TextView textView = (TextView) v.findViewById(R.id.name_text);
		textView.setText(label);
		v.setBackgroundResource(R.color.medium_gray);
		if (layoutId == R.layout.section_label) {
			ImageView sectionImage = (ImageView) v.findViewById(R.id.section_image);
			sectionImage.setVisibility(View.GONE);
		} else {
			Button addBtn = (Button) v.findViewById(R.id.add_cert);
			addBtn.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					showAddDialog();
				}
			});
		}
		return (v);
	}

	protected void showAddDialog() {
		final AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle(String.format(getString(R.string.ssl_add_alert_title), mStoreTitleString));
		alert.setIcon(android.R.drawable.ic_dialog_info);
		alert.setMessage(String.format(getString(R.string.ssl_add_alert_body), mStoreString, mStoreString, mImportFormat));
		alert.setPositiveButton("Add", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				addFromExternalStroage();
				dialog.cancel();
			}
		});

		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				dialog.cancel();
			}
		});
		alert.show();
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		Object o = l.getItemAtPosition(position);
		if (o instanceof Certificate) {
			showRemoveDialog((Certificate) o);
		}
	}
	
	protected void showRemoveDialog(final Certificate cert) {
		final AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle(String.format(getString(R.string.ssl_remove_alert_title), mStoreTitleString));
		alert.setIcon(android.R.drawable.ic_dialog_info);
		alert.setMessage(String.format(getString(R.string.ssl_remove_alert_body), mStoreString) + cert.getO());
		alert.setPositiveButton("Remove", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				remove(cert.getAlias());
				dialog.cancel();
			}
		});

		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				dialog.cancel();
			}
		});
		alert.show();
	}
}
