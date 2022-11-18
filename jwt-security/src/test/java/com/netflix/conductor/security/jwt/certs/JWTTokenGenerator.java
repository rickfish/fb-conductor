package com.netflix.conductor.security.jwt.certs;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.DefaultClaims;

public class JWTTokenGenerator {

	static Map<String, Object> headers = null;
	static PrivateKey key = null;

	static {

		try {
			key = KeyGenerator.getPrivateKey();
			String thumbPrint = KeyGenerator.getThumbPrint(KeyGenerator.getPublicKey());
			headers = new HashMap<String, Object>();
			headers.put("alg", "RS256");
			headers.put("typ", "JWT");
			headers.put("kid", thumbPrint);
		} catch (Exception e) {
		}

	}

	public static String generateJWTTokenForAdminURLs() {

		Date currentTime = new Date();
		long t = currentTime.getTime();
		Date exipryTime = new Date(t + (300 * 60 * 1000));
		Claims claims = new DefaultClaims();
		claims.setSubject("admin");
		claims.setIssuedAt(currentTime);
		claims.setId(UUID.randomUUID().toString());
		claims.setExpiration(exipryTime);
		List<String> roles = new ArrayList<String>();
		roles.add("conductor-admin");
		claims.put("roles", roles);
		claims.setIssuer("dev");

		final String compactJws = Jwts.builder().setHeader(headers).setClaims(claims)
				.signWith(SignatureAlgorithm.RS256, key).compact();

		return compactJws;

	}
	
	public static String generateJWTTokenForDomainURLs() {

		Date currentTime = new Date();
		long t = currentTime.getTime();
		Date exipryTime = new Date(t + (300 * 60 * 1000));
		Claims claims = new DefaultClaims();
		claims.setSubject("admin");
		claims.setIssuedAt(currentTime);
		claims.setId(UUID.randomUUID().toString());
		claims.setExpiration(exipryTime);
		List<String> roles = new ArrayList<String>();
		roles.add("conductor-user");
		claims.put("roles", roles);
		claims.setIssuer("dev");
		claims.put("domain","mydomain");

		final String compactJws = Jwts.builder().setHeader(headers).setClaims(claims)
				.signWith(SignatureAlgorithm.RS256, key).compact();

		return compactJws;

	}
	

	public static String generateJWTTokenForUserURLs() {

		Date currentTime = new Date();
		long t = currentTime.getTime();
		Date exipryTime = new Date(t + (300 * 60 * 1000));
		Claims claims = new DefaultClaims();
		claims.setSubject("admin");
		claims.setIssuedAt(currentTime);
		claims.setId(UUID.randomUUID().toString());
		claims.setExpiration(exipryTime);
		claims.put("roles", "conductor-user");

		final String compactJws = Jwts.builder().setHeader(headers).setClaims(claims)
				.signWith(SignatureAlgorithm.RS256, key).compact();

		return compactJws;

	}

	public static void main(String[] args) {

		System.out.println(generateJWTTokenForAdminURLs());

	}

}
