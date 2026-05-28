package com.example.athletehub.service;

import com.example.athletehub.enums.OAuthProvider;

/**
 * Verifies an OAuth ID token presented by the mobile app (the app did the
 * native OAuth dance with the provider and obtained this token). The
 * production implementation talks to the provider's JWKS endpoint to verify
 * the signature and validates issuer + audience claims; integration tests
 * replace this whole bean with a Mockito stub so they don't depend on
 * Google / Apple network calls.
 */
public interface OAuthTokenVerifier {

    /**
     * Verify the token and return the canonical identity it asserts.
     *
     * @throws com.example.athletehub.exception.InvalidOAuthTokenException
     *         when the signature, issuer, audience, expiry, or required
     *         claims fail validation.
     */
    OAuthIdentity verify(OAuthProvider provider, String idToken);

    /** What a successfully-verified ID token tells us about the user. */
    record OAuthIdentity(String providerUid, String email, String displayName) {}
}
