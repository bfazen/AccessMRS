package org.odk.clinic.android.activities;

import java.util.ArrayList;

import org.odk.clinic.android.R;
import org.odk.clinic.android.database.ClinicAdapter;

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;


/**
 * @author Samuel Mbugua
 *
 */
public class InstanceUploaderList extends ListActivity {

    private static final String BUNDLE_SELECTED_ITEMS_KEY = "selected_items";
    private static final String BUNDLE_TOGGLED_KEY = "toggled";

    private static final int MENU_PREFERENCES = Menu.FIRST;
    private static final int INSTANCE_UPLOADER = 0;

    private Button mActionButton;
    private Button mToggleButton;

    private ClinicAdapter mCla;
    private SimpleCursorAdapter mInstances;
    private ArrayList<Long> mSelected = new ArrayList<Long>();
    private boolean mRestored = false;
    private boolean mToggled = false;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.patient_upload_list);

        mActionButton = (Button) findViewById(R.id.upload_button);
        mActionButton.setOnClickListener(new OnClickListener() {

            @Override
			public void onClick(View arg0) {
                if (mSelected.size() > 0) {
                    // items selected
                    uploadSelectedFiles();
                    refreshData();
                    mToggled = false;
                } else {
                    // no items selected
                    Toast.makeText(getApplicationContext(), getString(R.string.no_select_error),
                        Toast.LENGTH_SHORT).show();
                }
            }

        });

        mToggleButton = (Button) findViewById(R.id.toggle_button);
        mToggleButton.setOnClickListener(new OnClickListener() {
            @Override
			public void onClick(View v) {
                // toggle selections of items to all or none
                ListView ls = getListView();
                mToggled = !mToggled;
                // remove all items from selected list
                mSelected.clear();
                for (int pos = 0; pos < ls.getCount(); pos++) {
                    ls.setItemChecked(pos, mToggled);
                    // add all items if mToggled sets to select all
                    if (mToggled)
                        mSelected.add(ls.getItemIdAtPosition(pos));
                }
                mActionButton.setEnabled(!(mSelected.size() == 0));

            }
        });

    }


    /**
     * Retrieves instance information from {@link ClinicAdapter}, composes and displays each row.
     */
    private void refreshView() {
    	if ( mCla == null ) {
        	ClinicAdapter t = new ClinicAdapter();
            t.open();
            mCla = t;
    	}
    	
//    	mCla.open();
    	
    	//delete all orphan instances
    	mCla.removeOrphanInstances(getApplicationContext());
        // get all un-submitted instances
        Cursor c = mCla.fetchFormInstancesByStatus(ClinicAdapter.STATUS_UNSUBMITTED);
        startManagingCursor(c);

        String[] data = new String[] {
                ClinicAdapter.KEY_FORMINSTANCE_DISPLAY, ClinicAdapter.KEY_FORMINSTANCE_STATUS
        };
        int[] view = new int[] {
                R.id.text1, R.id.text2
        };

        // render total instance view
        mInstances =
            new SimpleCursorAdapter(this, R.layout.two_item_multiple_choice, c, data, view);
        setListAdapter(mInstances);
        getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        getListView().setItemsCanFocus(false);
        mActionButton.setEnabled(!(mSelected.size() == 0));

        // set title
        setTitle(getString(R.string.app_name) + " > " + getString(R.string.upload_patients));

        // if current activity is being reinitialized due to changing orientation restore all check
        // marks for ones selected
        if (mRestored) {
            ListView ls = getListView();
            for (long id : mSelected) {
                for (int pos = 0; pos < ls.getCount(); pos++) {
                    if (id == ls.getItemIdAtPosition(pos)) {
                        ls.setItemChecked(pos, true);
                        break;
                    }
                }

            }
            mRestored = false;
        }
        
//        if (c!= null) {
//        	c.close();
//        }
//        mCla.close();
    }


    private void uploadSelectedFiles() {
        ArrayList<String> selectedInstances = new ArrayList<String>();

        Cursor c = null;

        for (int i = 0; i < mSelected.size(); i++) {
            c = mCla.fetchFormInstance(mSelected.get(i));
            startManagingCursor(c);
            String s = c.getString(c.getColumnIndex(ClinicAdapter.KEY_PATH));
            selectedInstances.add(s);
        }

        // bundle intent with upload files
        Intent i = new Intent(this, InstanceUploaderActivity.class);
        //i.putExtra(FormEntryActivity.KEY_INSTANCES, selectedInstances);
        i.putExtra(ClinicAdapter.KEY_INSTANCES, selectedInstances);
        startActivityForResult(i, INSTANCE_UPLOADER);
    }


    private void refreshData() {
        if (!mRestored) {
            mSelected.clear();
        }
        refreshView();
    }

/*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_PREFERENCES, 0, getString(R.string.server_preferences)).setIcon(
            android.R.drawable.ic_menu_preferences);
        return true;
    }
*/

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case MENU_PREFERENCES:
                createPreferencesMenu();
                return true;
        }
        return super.onMenuItemSelected(featureId, item);
    }


    private void createPreferencesMenu() {
        Intent i = new Intent(this, PreferencesActivity.class);
        startActivity(i);
    }


    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        // get row id from db
        Cursor c = (Cursor) getListAdapter().getItem(position);
        long k = c.getLong(c.getColumnIndex(ClinicAdapter.KEY_ID));

        // add/remove from selected list
        if (mSelected.contains(k))
            mSelected.remove(k);
        else
            mSelected.add(k);

        mActionButton.setEnabled(!(mSelected.size() == 0));

    }

	@Override
	protected void onDestroy() {
		try {
			if ( mCla != null ) {
				ClinicAdapter t = mCla;
				mCla = null;
				t.close();
			}
		} catch ( Exception e ) {
			e.printStackTrace();
		} finally {
			super.onDestroy();
		}
	}


	@Override
    protected void onResume() {
        refreshData();
        super.onResume();
    }


    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        long[] selectedArray = savedInstanceState.getLongArray(BUNDLE_SELECTED_ITEMS_KEY);
        for (int i = 0; i < selectedArray.length; i++)
            mSelected.add(selectedArray[i]);
        mToggled = savedInstanceState.getBoolean(BUNDLE_TOGGLED_KEY);
        mRestored = true;
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        long[] selectedArray = new long[mSelected.size()];
        for (int i = 0; i < mSelected.size(); i++)
            selectedArray[i] = mSelected.get(i);
        outState.putLongArray(BUNDLE_SELECTED_ITEMS_KEY, selectedArray);
        outState.putBoolean(BUNDLE_TOGGLED_KEY, mToggled);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == RESULT_CANCELED) {
            return;
        }
        switch (requestCode) {
            // returns with a form path, start entry
            case INSTANCE_UPLOADER:
                if (intent.getBooleanExtra(ClinicAdapter.KEY_INSTANCES, false)) {
                    refreshData();
                    if (mInstances.isEmpty()) {
                        finish();
                    }
                }
                break;
            default:
                break;
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

}