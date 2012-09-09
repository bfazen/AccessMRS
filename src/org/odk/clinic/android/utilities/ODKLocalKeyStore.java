package org.odk.clinic.android.utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import android.os.Environment;
import android.util.Log;

public class ODKLocalKeyStore {
	private static String storePath=  Environment.getExternalStorageDirectory() + "/odk/clinic/" + ".key/";
	private static KeyStore keyStore;
	
	private static void loadLocalStore(){
		
		//there should be only one file in the store as we will only load one file from the store 
		//if you want several certs load them into one BKS KeyStore and load them here

		
		try {
			File keyStoreDir = new File(storePath); 
			if (!keyStoreDir.isDirectory() && !keyStoreDir.exists()) {
				Log.e("loadLocalStore", "Keystore does not exist. Ensure " + storePath + " exists");
				new File(storePath);
				return;
			}
			File[] files = keyStoreDir.listFiles();
	        if (files.length>0) {//read the first file in the stream
	        	InputStream in = new FileInputStream(files[0]);
	        	keyStore = KeyStore.getInstance("BKS");;
	   		 
	    		keyStore.load(in, "openmrs".toCharArray());
	    		in.close();
	        }
        } catch (FileNotFoundException e) {
        	Log.e("loadLocalStore", "Keystore no found error" + e.getLocalizedMessage());
    	}catch (KeyStoreException e) {
        	Log.e("loadLocalStore", "Key store error" + e.getLocalizedMessage());
		} catch (NoSuchAlgorithmException e) {
			Log.e("loadLocalStore", "Algorithm error" + e.getLocalizedMessage());
		} catch (CertificateException e) {
			Log.e("loadLocalStore", "Certificate error" + e.getLocalizedMessage());
		} catch (IOException e) {
			Log.e("loadLocalStore", "IO error" + e.getLocalizedMessage());
		}
	}

	public static KeyStore getKeyStore() {
		if (keyStore==null)
			loadLocalStore();
		
		return keyStore;
	}
	
	
}