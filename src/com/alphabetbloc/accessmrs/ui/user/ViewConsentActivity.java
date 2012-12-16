package com.alphabetbloc.accessmrs.ui.user;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;

import com.alphabetbloc.accessmrs.R;
import com.alphabetbloc.accessmrs.data.Patient;
import com.alphabetbloc.accessmrs.providers.DataModel;
import com.alphabetbloc.accessmrs.providers.Db;
import com.alphabetbloc.accessmrs.utilities.UiUtils;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class ViewConsentActivity extends BasePatientActivity {

	private static final String TAG = ViewConsentActivity.class.getSimpleName();
	private static Patient mPatient;
	private Context mContext;
	private Bitmap mConsentBitmap;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.view_consent);
		mContext = this;

		String patientIdStr = getIntent().getStringExtra(BasePatientListActivity.KEY_PATIENT_ID);
		Integer patientId = Integer.valueOf(patientIdStr);
		mPatient = Db.open().getPatient(patientId);
		if (mPatient == null) {
			UiUtils.toastAlert(mContext, getString(R.string.error_db), getString(R.string.error, R.string.no_patient));
			finish();
		}
		createPatientHeader(mPatient.getPatientId());

		byte[] blob = Db.open().getPriorConsentImage(patientId);
		if (blob != null) {
			mConsentBitmap = BitmapFactory.decodeByteArray(blob, 0, blob.length);

			ImageView iv = (ImageView) findViewById(R.id.consent_bitmap);
			iv.setImageBitmap(mConsentBitmap);
		} else {
			Log.e(TAG, "ERROR! Could not find patient consent in the database");
		}
		Button returnButton = (Button) findViewById(R.id.consent_return);
		returnButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				setResult(RESULT_CANCELED);
				finish();
			}
		});

		Button revokeButton = (Button) findViewById(R.id.consent_revoke);
		revokeButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Db.open().updateConsent(mPatient.getPatientId(), DataModel.CONSENT_DECLINED, null);
				Bundle b = new Bundle();
				b.putInt(DataModel.CONSENT_VALUE, DataModel.CONSENT_DECLINED);
				Intent i = new Intent();
				i.putExtras(b);
				setResult(RESULT_OK, i);
				finish();
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		refreshView();
	}

	protected void refreshView() {
	}

	// Alternatively:
	class BitmapView extends ImageView {
		private Bitmap bmp = null;
		private Paint paint = null;
		private Rect bmprect = null;

		public BitmapView(Context context, Bitmap mybmp) {
			super(context);
			this.bmp = mybmp;
			paint = new Paint();
			bmprect = new Rect(0, 0, bmp.getHeight(), bmp.getWidth());
		}

		@Override
		protected void onDraw(Canvas canvas) {
			paint.setFilterBitmap(true);
			paint.setAntiAlias(true);
			canvas.drawBitmap(bmp, null, bmprect, paint);
			super.onDraw(canvas);
		}
	}

}
