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



	

	public ODKSSLSocketFactory(KeyStore keyStore) throws NoSuchAlgorithmException {
		// try {
		// keyStore = KeyStore.getInstance("BKS");;
		//
		// keyStore.load(cert, "openmrs".toCharArray());

		try {
			sslContext.init(null, new TrustManager[] { new ODKTrustManager(keyStore) }, null); 
			HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory()); //NEW?
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
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public String[] getSupportedCipherSuites() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public Socket createSocket(InetAddress host, int port) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

}