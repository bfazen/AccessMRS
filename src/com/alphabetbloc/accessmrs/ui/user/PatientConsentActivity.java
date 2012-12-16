package com.alphabetbloc.accessmrs.ui.user;

//import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.alphabetbloc.accessmrs.R;
import com.alphabetbloc.accessmrs.data.Patient;
import com.alphabetbloc.accessmrs.providers.DataModel;
import com.alphabetbloc.accessmrs.providers.Db;
import com.alphabetbloc.accessmrs.utilities.App;
import com.alphabetbloc.accessmrs.utilities.FileUtils;
import com.alphabetbloc.accessmrs.utilities.UiUtils;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * @author BetterThanZero (www.mysamplecode.com) (released under GNU or Apache
 *         or MIT)
 * 
 */
public class PatientConsentActivity extends BaseUserActivity {

	public static final String TAG = PatientConsentActivity.class.getSimpleName();
	public static final String DEFAULT_CONSENT_FORM = "default_consent_form.html";

	private LinearLayout mContent;
	private Signature mSignature;
	private Button mAcceptSignature;
	private View mView;
	private ViewGroup mViewGroup;
	private boolean mConsented = false;
	private Patient mPatient;
	private Context mContext;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.consent_content);
		mViewGroup = (ViewGroup) findViewById(R.id.consent_content);
		mContext = this;

		String patientIdStr = getIntent().getStringExtra(BasePatientListActivity.KEY_PATIENT_ID);
		Integer patientId = Integer.valueOf(patientIdStr);
		mPatient = Db.open().getPatient(patientId);

		Integer consent = mPatient.getConsent();
		Long consentDate = mPatient.getConsentDate();
		if (consent == null && consentDate != null) {
			Date date = new Date();
			date.setTime(consentDate);
			String consentDateString = new SimpleDateFormat("MMM dd, yyyy").format(date) + " ";
			UiUtils.toastAlert(getString(R.string.consent_expired_title), getString(R.string.consent_expired_body, consentDateString));
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		refreshView();
	}

	@Override
	protected void refreshView() {
		mViewGroup.removeAllViews();

		if (!mConsented)
			showConsentForm();
		else
			showSignatureBox();
	}

	private void showConsentForm() {
		Log.e(TAG, "adding consent view");
		mViewGroup.addView(View.inflate(this, R.layout.consent, null));
		try {
			WebView webView = (WebView) findViewById(R.id.webView1);
			webView.setWebViewClient(new WebViewClient());

			File consentForm = new File(FileUtils.getExternalConsentPath(), "default_consent_form.html");
			if (!consentForm.exists())
				consentForm = getDefaultConsentForm();

			// try to load consent form from sd
			Uri url = Uri.fromFile(consentForm);
			webView.loadUrl(url.toString());

		} catch (Exception e) {
			Log.e(TAG, "Consent File is missing! ");
			finish();
		}

		TextView validity = (TextView) findViewById(R.id.validity_period);
		final Date date = new Date();
		date.setTime(System.currentTimeMillis());
		String dateString = new SimpleDateFormat("MMM dd, yyyy").format(date) + " ";
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		String consentSeconds = prefs.getString(getString(R.string.key_max_consent_time), getString(R.string.default_max_consent_time));
		Long expiryPeriod = Integer.valueOf(consentSeconds) * 1000L;
		Long expiryTime = System.currentTimeMillis() + expiryPeriod;
		Date expiryDate = new Date();
		expiryDate.setTime(expiryTime);
		String expiryString = new SimpleDateFormat("MMM dd, yyyy").format(expiryDate) + " ";
		validity.setText(getString(R.string.consent_validity_period, dateString, expiryString));
		
		Button cancel = (Button) findViewById(R.id.consent_cancel);
		cancel.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				setResult(RESULT_CANCELED);
				finish();
			}
		});

		Button accept = (Button) findViewById(R.id.consent_accept);
		accept.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				mConsented = true;
				refreshView();
			}
		});

		Button decline = (Button) findViewById(R.id.consent_decline);
		decline.setOnClickListener(new OnClickListener() {
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

	private void showSignatureBox() {
		Log.e(TAG, "adding signature view");
		mViewGroup.addView(View.inflate(this, R.layout.signature, null));
		mContent = (LinearLayout) findViewById(R.id.signature_box);
		mSignature = new Signature(this, null);
		mSignature.setBackgroundColor(Color.WHITE);
		mContent.addView(mSignature, LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		mView = mContent;

		mAcceptSignature = (Button) findViewById(R.id.signature_accept);
		mAcceptSignature.setEnabled(false);
		mAcceptSignature.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (App.DEBUG)
					Log.v(TAG, "Consent Signature Has Been Saved");
				mView.setDrawingCacheEnabled(true);
				mSignature.save(mView);
				Bundle b = new Bundle();
				b.putInt(DataModel.CONSENT_VALUE, DataModel.CONSENT_OBTAINED);
				Intent i = new Intent();
				i.putExtras(b);
				setResult(RESULT_OK, i);
				finish();
			}
		});

		Button clearButton = (Button) findViewById(R.id.signature_clear);
		clearButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (App.DEBUG)
					Log.v(TAG, "Consent Signature Cleared");
				mSignature.clear();
				mAcceptSignature.setEnabled(false);
			}
		});

		Button cancelButton = (Button) findViewById(R.id.signature_cancel);
		cancelButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				setResult(RESULT_CANCELED);
				finish();
			}
		});
	}

	private static File getDefaultConsentForm() {

		// input path: find default form in assets
		AssetManager assetManager = App.getApp().getAssets();
		String[] allAssetPaths = null;
		String assetPath = null;
		try {
			allAssetPaths = assetManager.list("");

			for (String path : allAssetPaths) {
				if (path.contains(DEFAULT_CONSENT_FORM)) {
					assetPath = path;
				}
			}

		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
		}

		// output path:
		File consentForm = new File(FileUtils.getExternalConsentPath(), DEFAULT_CONSENT_FORM);
		consentForm.getParentFile().mkdirs();
		String sdPath = consentForm.getAbsolutePath();

		// move to working dir on sd
		return FileUtils.copyAssetToSd(assetPath, sdPath);
	}

	public class Signature extends View {
		private static final float STROKE_WIDTH = 5f;
		private static final float HALF_STROKE_WIDTH = STROKE_WIDTH / 2;
		private Paint paint = new Paint();
		private Path path = new Path();

		private float lastTouchX;
		private float lastTouchY;
		private final RectF dirtyRect = new RectF();

		public Signature(Context context, AttributeSet attrs) {
			super(context, attrs);
			paint.setAntiAlias(true);
			paint.setColor(Color.BLACK);
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeJoin(Paint.Join.ROUND);
			paint.setStrokeWidth(STROKE_WIDTH);
		}

		public void save(View v) {
			if (App.DEBUG)
				Log.v(TAG, "\n\t Width: " + v.getWidth() + "\n\t Height: " + v.getHeight());

			Bitmap bitmap = Bitmap.createBitmap(mContent.getWidth(), mContent.getHeight() + 40, Bitmap.Config.RGB_565);
			Canvas canvas = new Canvas(bitmap);

			try {
				canvas.drawColor(Color.WHITE);
				v.draw(canvas);
				Paint stamp = new Paint(Paint.LINEAR_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
				stamp.setAntiAlias(true);
				stamp.setColor(Color.DKGRAY);
				stamp.setTextSize(12);

				// Name and Date
				String clientName = mPatient.getGivenName() + "  " + mPatient.getFamilyName() + " (ID:" + mPatient.getPatientId() + ")";
				canvas.drawText(clientName, 10, mContent.getHeight() + 12, stamp);

				// Provider ID and Period
				final Date date = new Date();
				date.setTime(System.currentTimeMillis());
				String dateString = new SimpleDateFormat("MMM dd, yyyy").format(date) + " ";
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
				// String providerId =
				// prefs.getString(getString(R.string.key_provider),
				// getString(R.string.default_provider));
				String consentSeconds = prefs.getString(getString(R.string.key_max_consent_time), getString(R.string.default_max_consent_time));
				Long expiryPeriod = Integer.valueOf(consentSeconds) * 1000L;
				Long expiryTime = System.currentTimeMillis() + expiryPeriod;
				Date expiryDate = new Date();
				expiryDate.setTime(expiryTime);
				String expiryString = new SimpleDateFormat("MMM dd, yyyy").format(expiryDate) + " ";

				canvas.drawText(mContext.getString(R.string.consent_signature_addendum_line1, dateString), 10, mContent.getHeight() + 24, stamp);
				canvas.drawText(mContext.getString(R.string.consent_signature_addendum_line2, expiryString), 10, mContent.getHeight() + 36, stamp);

				// Save Bitmap To Db
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				bitmap.compress(CompressFormat.PNG, 0, os);
				byte[] bitmapdata = os.toByteArray();

				Db.open().updateConsent(mPatient.getPatientId(), DataModel.CONSENT_OBTAINED, bitmapdata);

				// Sampling the Image (reduces signature size by half... but
				// unreadable)
				// final BitmapFactory.Options options = new
				// BitmapFactory.Options();
				// options.inSampleSize = 2;
				// ByteArrayInputStream bs = new
				// ByteArrayInputStream(bitmapdata);
				// Bitmap sampledBitmap = BitmapFactory.decodeStream(bs, null,
				// options);
				// ByteArrayOutputStream bos = new ByteArrayOutputStream();
				// sampledBitmap.compress(CompressFormat.PNG, 0, bos);
				// byte[] sampledImage = bos.toByteArray();

				// Resizing the bitmap:
				// Bitmap resized = Bitmap.createScaledBitmap(bitmap, (int)
				// (bitmap.getWidth() * 0.8), (int) (bitmap.getHeight() * 0.8),
				// true);

				// Saving to a File on SD For Test Purposes:
				// String tempDir =
				// Environment.getExternalStorageDirectory().getAbsolutePath();
				// File mypath = new File(tempDir, "test.png");
				// FileOutputStream mFileOutStream = new
				// FileOutputStream(mypath);
				// resized.compress(Bitmap.CompressFormat.PNG, 90,
				// mFileOutStream);
				// mFileOutStream.flush();
				// mFileOutStream.close();

			} catch (Exception e) {
				Log.v(TAG, e.toString());
			}
		}

		public void clear() {
			path.reset();
			invalidate();
		}

		@Override
		protected void onDraw(Canvas canvas) {
			canvas.drawPath(path, paint);
		}

		@Override
		public boolean onTouchEvent(MotionEvent event) {
			float eventX = event.getX();
			float eventY = event.getY();
			mAcceptSignature.setEnabled(true);

			switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				path.moveTo(eventX, eventY);
				lastTouchX = eventX;
				lastTouchY = eventY;
				return true;

			case MotionEvent.ACTION_MOVE:

			case MotionEvent.ACTION_UP:

				resetDirtyRect(eventX, eventY);
				int historySize = event.getHistorySize();
				for (int i = 0; i < historySize; i++) {
					float historicalX = event.getHistoricalX(i);
					float historicalY = event.getHistoricalY(i);
					expandDirtyRect(historicalX, historicalY);
					path.lineTo(historicalX, historicalY);
				}
				path.lineTo(eventX, eventY);
				break;

			default:
				debug("Ignored touch event: " + event.toString());
				return false;
			}

			invalidate((int) (dirtyRect.left - HALF_STROKE_WIDTH), (int) (dirtyRect.top - HALF_STROKE_WIDTH), (int) (dirtyRect.right + HALF_STROKE_WIDTH), (int) (dirtyRect.bottom + HALF_STROKE_WIDTH));

			lastTouchX = eventX;
			lastTouchY = eventY;

			return true;
		}

		private void debug(String string) {
		}

		private void expandDirtyRect(float historicalX, float historicalY) {
			if (historicalX < dirtyRect.left) {
				dirtyRect.left = historicalX;
			} else if (historicalX > dirtyRect.right) {
				dirtyRect.right = historicalX;
			}

			if (historicalY < dirtyRect.top) {
				dirtyRect.top = historicalY;
			} else if (historicalY > dirtyRect.bottom) {
				dirtyRect.bottom = historicalY;
			}
		}

		private void resetDirtyRect(float eventX, float eventY) {
			dirtyRect.left = Math.min(lastTouchX, eventX);
			dirtyRect.right = Math.max(lastTouchX, eventX);
			dirtyRect.top = Math.min(lastTouchY, eventY);
			dirtyRect.bottom = Math.max(lastTouchY, eventY);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return false;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return false;
	}

}
