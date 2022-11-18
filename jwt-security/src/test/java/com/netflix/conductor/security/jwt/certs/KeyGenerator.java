package com.netflix.conductor.security.jwt.certs;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import javax.xml.bind.DatatypeConverter;

public class KeyGenerator {

	public static void main(String[] args) throws Exception {

		PrivateKey privKey = getPrivateKey();
		PublicKey pubKey = getPublicKey();

		System.out.println(getThumbPrint(pubKey));

	}

	public static PrivateKey getPrivateKey()
			throws IOException, URISyntaxException, NoSuchAlgorithmException, InvalidKeySpecException {

		String privateKeyContent = new String(
				Files.readAllBytes(Paths.get(ClassLoader.getSystemResource("privateKey.key").toURI())));

		privateKeyContent = privateKeyContent.replaceAll("\\n", "").replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "");

		KeyFactory kf = KeyFactory.getInstance("RSA");

		PKCS8EncodedKeySpec keySpecPKCS8 = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyContent));
		PrivateKey privKey = kf.generatePrivate(keySpecPKCS8);

		return privKey;

	}

	public static PublicKey getPublicKey() throws IOException, URISyntaxException, NoSuchAlgorithmException,
			InvalidKeySpecException, CertificateException {

		InputStream is = ClassLoader.getSystemResourceAsStream("certificate.crt");

		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		Certificate cert = cf.generateCertificate(is);
		PublicKey publicKey = cert.getPublicKey();

		return publicKey;

	}

	public static String getThumbPrint(PublicKey cert) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		byte[] der = cert.getEncoded();
		md.update(der);
		byte[] digest = md.digest();
		String digestHex = DatatypeConverter.printHexBinary(digest);
		return digestHex.toLowerCase();
	}

}
