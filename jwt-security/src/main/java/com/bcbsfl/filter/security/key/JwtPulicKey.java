/**
 * 
 */
package com.bcbsfl.filter.security.key;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.text.ParseException;
import java.util.concurrent.ConcurrentHashMap;

import com.bcbsfl.filter.security.config.FilterConfigs;
import com.bcbsfl.filter.security.helpers.JWTLogger;
import com.bcbsfl.filter.security.jwt.error.JwtCustomError;

/**
 * @author d6cu
 *
 */
public class JwtPulicKey {

	private static ConcurrentHashMap<String, PublicKey> keyMap = new ConcurrentHashMap<String, PublicKey>();

	// local PAAS path for cert PV to should be mounted
	// This is currently only supporting the defined Path.
	// This could be a configurable endpoint, however Jagadish desired this to
	// be hardcoded
	private static final String PAAS_CERT_PATH = "/etc/jwt-secrets/";

	private static final JWTLogger LOGGER = JWTLogger.getInstance();

	public static PublicKey getPublicKey(FilterConfigs config, String keyId)
			throws ParseException, CertificateException, FileNotFoundException, JwtCustomError {
		String pemCert;
		if (keyId == null) {
			throw new JwtCustomError("Header KID can not be Null");
		}

		// *********************************************************************************
		// Adding Extra If around funtional code to handle testing case.
		// Team would like to test thumbnail ability with existing key as the
		// thumbnail value.
		// After testing the surounding if is excessive code and should be
		// removed for performance reasons
		// *********************************************************************************
		if (fileExists(config, keyId + ".pem")) {
			return getPublicKeyFromFile(config, keyId + "thumbprint", keyId + ".pem");
		} else {
			if (keyId.equalsIgnoreCase("sm")) {
				pemCert = "siteminder.pem";
			} else if (keyId.equalsIgnoreCase("dp")) {
				pemCert = "datapower.pem";
			} else {
				if (keyId.endsWith(".cer") || keyId.endsWith(".pem")) {
					pemCert = keyId;
				} else {
					pemCert = keyId + ".pem";
				}
			}
		}

		if (keyMap.containsKey(keyId)) {
			return keyMap.get(keyId);
		}

		return getPublicKeyFromFile(config, keyId, pemCert);
	}

	@SuppressWarnings({ "unused", "resource" })
	private static boolean fileExists(FilterConfigs config, String filePath) {
		// TODO Auto-generated method stub
		// filePath = PAAS_CERT_PATH + filePath;

		filePath = PAAS_CERT_PATH + filePath;

		InputStream is;
		try {
			is = new FileInputStream(filePath);
		} catch (FileNotFoundException e) {
			return false;
		}

		if (is == null) {
			return false;
		}
		return true;
	}

	public static PublicKey getPublicKeyFromFile(FilterConfigs config, String keyId, String pemCert)
			throws FileNotFoundException, ParseException, CertificateException {

		InputStream is = null;

		if (!pemCert.contains("/")) {
			// pemCert = PAAS_CERT_PATH + pemCert;
			pemCert = PAAS_CERT_PATH + pemCert;
			LOGGER.debug("PEM location was not a path.  appending PaaS cert path to PEM filename of " + pemCert);
		}

		is = new FileInputStream(pemCert);

		// if (is == null)
		// throw new ParseException("Unable to load the cert file: " + config,
		// 0);

		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		Certificate cert = cf.generateCertificate(is);
		PublicKey publicKey = cert.getPublicKey();

		// store the public key in local static HashMap (VERY basic Cache type
		// implementation)
		// There was no reason to implement a full caching solution for simple
		// Key storage
		LOGGER.debug("Storing the key for future use: " + publicKey);
		if (keyMap.containsKey(keyId)) {
			keyMap.replace(keyId, publicKey);
		} else {
			keyMap.put(keyId, publicKey);
		}

		return publicKey;
	}

}
