package com.bcbsfl.filter.security.key;

import java.security.Key;

import com.bcbsfl.filter.security.config.Configurations;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.SigningKeyResolver;

public class JwtSigningKeyResolver implements SigningKeyResolver {

	@Override
	public Key resolveSigningKey(JwsHeader header, Claims claims) {
		String keyId = header.get("kid").toString();
        try {
			return JwtPulicKey.getPublicKey(Configurations.getFilterConfig(), keyId);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} 
	}

	@Override
	public Key resolveSigningKey(JwsHeader header, String plaintext) {
		String keyId = header.get("kid").toString();
        try {
			return JwtPulicKey.getPublicKey(Configurations.getFilterConfig(), keyId);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} 
	}

}
