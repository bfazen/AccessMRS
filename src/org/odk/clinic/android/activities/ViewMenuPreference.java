package org.odk.clinic.android.activities;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

/**
 * @author Louis Fazen (louis.fazen@gmail.com)
 *
 */

public class ViewMenuPreference extends Activity {

	
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
    SharedPreferences settings = getSharedPreferences("ChwSettings", MODE_PRIVATE);
    Bundle extra = getIntent().getExtras();
    
    Boolean toggle = extra.getBoolean("ShowMenu");
    Log.e("ChvSetMenuPreference", "OnCreate has been called with toggle extra= " + toggle);
    SharedPreferences.Editor editor = settings.edit();
    editor.putBoolean("IsMenuEnabled", toggle);
    editor.commit();
    finish();
    }
    
}
