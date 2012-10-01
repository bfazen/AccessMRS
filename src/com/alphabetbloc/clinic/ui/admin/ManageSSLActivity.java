package com.alphabetbloc.clinic.ui.admin;

import java.io.File;
import java.util.ArrayList;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.data.Certificate;
import com.alphabetbloc.clinic.utilities.EncryptionUtil;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 *
 */

public abstract class ManageSSLActivity extends ListActivity {
//	private static final String TAG = ManageSSLActivity.class.getSimpleName();
	// Common to Key/Trust Store
	protected File mLocalStoreFile;
	protected String mStorePassword;
	protected String trustStorePropDefault;

	// Override
	protected String mLocalStoreFileName;
	protected int mLocalStoreResourceId;
	protected String mStoreString;
	protected String mStoreTitleString;
	protected String mImportFormat;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.add_certificates);
		trustStorePropDefault = System.getProperty("javax.net.ssl.trustStore"); // System
		mStorePassword = EncryptionUtil.getPassword();
	}

	@Override
	protected void onResume() {
		super.onResume();

		// View
		TextView title = (TextView) findViewById(R.id.store_title);
		title.setText(String.format(getString(R.string.ssl_store_title), mStoreTitleString));
		setProgressBarIndeterminateVisibility(false);
		showStoreItems();
	}

	protected void refreshView() {
		findViewById(android.R.id.content).invalidate();
		showStoreItems();
	}

	protected abstract ArrayList<Certificate> getStoreFiles();

	protected abstract void showStoreItems();

	protected abstract void remove(String alias);

	protected abstract void addFromExternalStroage();

	@Override
	protected void onPause() {
		if (trustStorePropDefault != null)
			System.setProperty("javax.net.ssl.trustStore", trustStorePropDefault);
		else
			System.clearProperty("javax.net.ssl.trustStore");
		super.onPause();
	}

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
