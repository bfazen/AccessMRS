//package org.odk.clinic.android.utilities;
//
//import java.io.InputStream;
//import java.security.KeyStore;
//
//import org.apache.http.conn.ClientConnectionManager;
//import org.apache.http.conn.scheme.PlainSocketFactory;
//import org.apache.http.conn.scheme.Scheme;
//import org.apache.http.conn.scheme.SchemeRegistry;
//import org.apache.http.conn.ssl.SSLSocketFactory;
//import org.apache.http.impl.client.DefaultHttpClient;
//import org.apache.http.impl.conn.SingleClientConnManager;
//
//import android.content.Context;
//
////TAKEN FROM BLOG OF ANTOINE HAUCK (CODE IS SAME AS CODE PROJECT, CRAZY
//// BOB, (AND VIPUL): http://stackoverflow.com/a/10026598/1439402  Call in Code as: 
////Instantiate the custom HttpClient
////DefaultHttpClient client = new MyHttpClient(getApplicationContext());
////HttpGet get = new HttpGet("https://www.mydomain.ch/rest/contacts/23");
//////Execute the GET call and obtain the response
////HttpResponse getResponse = client.execute(get);
////HttpEntity responseEntity = getResponse.getEntity();  //NOT SURE, ONLY ANTOINE
//
//public class MyHttpClient extends DefaultHttpClient {
//
//	final Context context;
//
//	public MyHttpClient(Context context) {
//		this.context = context;
//	}
//
//	// Register SSLSF for port 443 with keystore to ConnectionManager, same as emmby as well
//	@Override
//	protected ClientConnectionManager createClientConnectionManager() {
//		SchemeRegistry registry = new SchemeRegistry();
//		registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
//		registry.register(new Scheme("https", newSslSocketFactory(), 443));
//		
////		final HttpParams params = new BasicHttpParams();
////		final ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(params,schemeRegistry); // emmby
//		
//		return new SingleClientConnManager(getParams(), registry);
//	}
//	
//
//	
//	private SSLSocketFactory newSslSocketFactory() {
//		try {
//			// Get bouncy castle instance and raw keystore certs
//			KeyStore trusted = KeyStore.getInstance("BKS");
//		
//
////			InputStream in = context.getResources().openRawResource(R.raw.mykeystore);
//
//			// Initialize keystore with certs, provide pwd
//			try {
//				trusted.load(in, "mysecret".toCharArray());
//			} finally {
//				in.close();
//			}
//			// Pass keystore to SSLSF, who verifies server cert
//			SSLSocketFactory sf = new SSLSocketFactory(trusted);
//			// Hostname verification from certificate
//			// http://hc.apache.org/httpcomponents-client-ga/tutorial/html/connmgmt.html#d4e506
//			sf.setHostnameVerifier(SSLSocketFactory.STRICT_HOSTNAME_VERIFIER); // someblogs
//
//			return sf;
//
//		} catch (Exception e) {
//			throw new AssertionError(e);
//		}
//	}
//
//	// ALTS per THOUGHTCRIME:
//	// TAKE FROM ASSETS:
//	// AssetManager assetManager = context.getAssets();
//	// InputStream in = assetManager.open("yourapp.store");
//	// return sslSocketFactory.connectSocket(sslSocketFactory.createSocket(),
//	// host, port, null, 0, new BasicHttpParams());
//
//}