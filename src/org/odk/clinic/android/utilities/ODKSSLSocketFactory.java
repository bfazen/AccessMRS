/*
 * Adapted from:
 * 
 * Bob Lee http://blog.crazybob.org/2010/02/android-trusting-ssl-certificates.html
 * 
 * and
 * 
 * Ductran http://ductranit.blogspot.com/2012/07/android-trusting-ssl-certificates.html
 * 
 */

package org.odk.clinic.android.utilities;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import android.util.Log;

/**
 * Allows you to trust certificates from additional KeyStores in addition to the
 * default KeyStore
 */
public class ODKSSLSocketFactory extends SSLSocketFactory {
	protected SSLContext sslContext = SSLContext.getInstance("TLS");

	// public ODKSSLSocketFactory(InputStream cert) throws
	// NoSuchAlgorithmException{
	// super();
	// this.cert=cert;
	// try {
	// initializeSSLContext();
	// } catch (IOException e) {
	// e.printStackTrace();
	// }
	// }
	//
	// public SSLSocketFactory getSocketFactory(){
	// return sslContext.getSocketFactory();
	// }
	//
	// public KeyStore getKeyStore(){
	// return this.keyStore;
	// }

	// SO ENTRY FROM JEFF SIX, Author of Application Security for the Android
	// Platform, published by O'Reilly
	// http://stackoverflow.com/a/8803983/1439402
	// Here's the high-level approach. Create a self-signed server SSL
	// certificate and deploy on your web server. If you're using Android, you
	// can use the keytool included with the Android SDK for this purpose; if
	// you're using another app platform like iOS, similar tools exist for them
	// as well. Then create a self-signed client and deploy that within your
	// application in a custom keystore included in your application as a
	// resource (keytool will generate this as well). Configure the server to
	// require client-side SSL authentication and to only accept the client
	// certificate you generated. Configure the client to use that client-side
	// certificate to identify itself and only accept the one server-side
	// certificate you installed on your server for that part of it.
	//
	// If someone/something other than your app attempts to connect to your
	// server, the SSL connection will not be created, as the server will reject
	// incoming SSL connections that do not present the client certificate that
	// you have included in your app.
	//

	// FROM THOUGHTCRIME:

	// private Socket constructSSLSocket(Context context, String host, int port)
	// {
	// AssetManager assetManager = context.getAssets();
	// InputStream keyStoreInputStream = assetManager.open("yourapp.store");
	// KeyStore trustStore = KeyStore.getInstance("BKS");
	//
	// trustStore.load(keyStoreInputStream, "somepass".toCharArray());
	//
	// SSLSocketFactory sslSocketFactory = new SSLSocketFactory(trustStore);
	// sslSocketFactory.setHostnameVerifier(SSLSocketFactory.STRICT_HOSTNAME_VERIFIER);
	//
	// return sslSocketFactory.connectSocket(sslSocketFactory.createSocket(),
	// host, port, null, 0, new BasicHttpParams());
	// }

	// // FROM THOUGHTCRIME

	// /FROM CODEPROJECT 

	// @Override
	// protected ClientConnectionManager createClientConnectionManager {
	// SchemeRegistry registry = new SchemeRegistry();
	// registry.register("http", PlainSocketFactory.getSocketFactory(), 80));
	// registry.register("https", newSslSocketFactory(), 443));
	// return new SingleClientConnManager(getParams(), registry);
	// }
	//
	// private SSLSocketFactory newSslSocketFactory() {
	// try {
	// KeyStore trusted = KeyStore.getInstance("BKS");
	// InputStream in = context.getResources().openRawResource(R.raw.mystore);
	// try {
	// trusted.load(in, "mypassword".toCharArray());
	// }
	// finally {
	// in.close();
	// }
	//
	// SSLSocketFactory mySslFact = new SslFactory(trusted);
	// //mySslFact.setHostNameVerifier(new MyHstNameVerifier());
	// return mySslFact;
	// } catch(Exception e) {
	// throw new AssertionError(e);
	// }
	// }

	// FROM CODEPROJECT
	
	//////// FROM BLOG OF ANTOINE HAUCK (CODE IS SAME AS CODE PROJECT, and SAME AS CRAZY BOB)
	@Override
    protected ClientConnectionManager createClientConnectionManager() {
        SchemeRegistry registry = new SchemeRegistry();
        registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        // Register for port 443 our SSLSocketFactory with our keystore
        // to the ConnectionManager
        registry.register(new Scheme("https", newSslSocketFactory(), 443));
        return new SingleClientConnManager(getParams(), registry);
    }
 
    private SSLSocketFactory newSslSocketFactory() {
        try {
            // Get an instance of the Bouncy Castle KeyStore format
            KeyStore trusted = KeyStore.getInstance("BKS");
            // Get the raw resource, which contains the keystore with
            // your trusted certificates (root and any intermediate certs)
            InputStream in = context.getResources().openRawResource(R.raw.mykeystore);
            try {
                // Initialize the keystore with the provided trusted certificates
                // Also provide the password of the keystore
                trusted.load(in, "mysecret".toCharArray());
            } finally {
                in.close();
            }
            // Pass the keystore to the SSLSocketFactory. The factory is responsible
            // for the verification of the server certificate.
            SSLSocketFactory sf = new SSLSocketFactory(trusted);
            // Hostname verification from certificate
            // http://hc.apache.org/httpcomponents-client-ga/tutorial/html/connmgmt.html#d4e506
            sf.setHostnameVerifier(SSLSocketFactory.STRICT_HOSTNAME_VERIFIER); //THIS LINE IS NOT IN OTHER POSTS...
            return sf;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
	
	
	
	
	
	
	

	public ODKSSLSocketFactory(KeyStore keyStore) throws NoSuchAlgorithmException {
		// try {
		// keyStore = KeyStore.getInstance("BKS");;
		//
		// keyStore.load(cert, "openmrs".toCharArray());

		try {
			sslContext.init(null, new TrustManager[] { new ODKTrustManager(keyStore) }, null);
			HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
		} catch (KeyManagementException e) {
			Log.e("loadLocalStore", "Key management error" + e.getLocalizedMessage());
		}

		// }catch (NoSuchAlgorithmException e) {
		//
		// catch (KeyStoreException e) {
		//
		// } catch (CertificateException e) {
		//
		// }
	}

	@Override
	public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
		return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
	}

	@Override
	public Socket createSocket() throws IOException {
		return sslContext.getSocketFactory().createSocket();
	}

	@Override
	public String[] getDefaultCipherSuites() {
		return null;
	}

	@Override
	public String[] getSupportedCipherSuites() {
		return null;
	}

	@Override
	public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
		return null;
	}

	@Override
	public Socket createSocket(InetAddress host, int port) throws IOException {
		return null;
	}

	@Override
	public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
		return null;
	}

	@Override
	public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
		return null;
	}
}