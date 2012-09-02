package com.alphabetbloc.clinic.services;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Base64;
import android.util.Log;

public class EncryptionUtil {

	private static final String TAG = "EncryptionService";
	
	public static final String SYMMETRIC_ALGORITHM = "AES/CFB/PKCS5Padding"; // "AES/CFB8/NoPadding"
	public static final String DECRYPTED_HIDDEN_DIR = ".dec"; // "DES/ECB/PKCS5Padding"
	// "AES/CBC/PKCS5Padding"
	public static final int SYMMETRIC_KEY_LENGTH = 256;
	public static final int AES_BLOCK_SIZE = 128; // me
	public static final int IV_BYTE_LENGTH = 16;

	private static final int IV_LENGTH = 16; // should == blocksize
	private static final Long MAX_DECRYPT_TIME = 1000L;
	

	



}