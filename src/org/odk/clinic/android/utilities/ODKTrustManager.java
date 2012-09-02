package org.odk.clinic.android.utilities;

import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import android.util.Log;


public class ODKTrustManager implements X509TrustManager {

    protected ArrayList<X509TrustManager> x509TrustManagers = new ArrayList<X509TrustManager>();

    protected ODKTrustManager(KeyStore... odkKeyStores) {
        final ArrayList<TrustManagerFactory> factories = new ArrayList<TrustManagerFactory>();

        try {
            // The default Trustmanager with default keystore
            final TrustManagerFactory original = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            original.init((KeyStore) null);
            factories.add(original);

            for( KeyStore keyStore : odkKeyStores ) {
                final TrustManagerFactory localKeyStoreCerts = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                localKeyStoreCerts.init(keyStore);
                factories.add(localKeyStoreCerts);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        /*
         * loop over the returned trustmanagers, and keep any that is a X509TrustManager
         */
        for (TrustManagerFactory tmf : factories)
            for( TrustManager tm : tmf.getTrustManagers() )
                if (tm instanceof X509TrustManager)
                    x509TrustManagers.add( (X509TrustManager)tm );

        if( x509TrustManagers.size()==0 )
        	Log.e("ODKTrustManager", "Couldn't find any X509TrustManagers");

    }

    /*
     * Delegate to the default trust manager.
     */
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        final X509TrustManager defaultX509TrustManager = x509TrustManagers.get(0);
        defaultX509TrustManager.checkClientTrusted(chain, authType);
    }

    /*
     * Loop over the trustmanagers until we find one that accepts our server cert
     */
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        for( X509TrustManager tm : x509TrustManagers ) {
            try {
                tm.checkServerTrusted(chain,authType);
                return;
            } catch( CertificateException e ) {
            	Log.e("ODKTrustManager", "This manager has no trust anchor for this certificate " + e.getLocalizedMessage());
            }
        }
        throw new CertificateException();
    }

    public X509Certificate[] getAcceptedIssuers() {
        final ArrayList<X509Certificate> list = new ArrayList<X509Certificate>();
        for( X509TrustManager tm : x509TrustManagers )
            list.addAll(Arrays.asList(tm.getAcceptedIssuers()));
        return list.toArray(new X509Certificate[list.size()]);
    }
}