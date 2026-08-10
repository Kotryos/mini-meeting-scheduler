package dev.kotryos.minischeduler.identity.internal;

import dev.kotryos.minischeduler.identity.CurrentUser;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
class ApiKeyAuthenticationProvider implements AuthenticationProvider {

    private final ApiKeyRepository apiKeys;

    ApiKeyAuthenticationProvider(ApiKeyRepository apiKeys) {
        this.apiKeys = apiKeys;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        var presentedKey = String.valueOf(authentication.getPrincipal());
        return apiKeys.findByKeyHashAndRevokedAtIsNull(sha256Hex(presentedKey))
                .map(ApiKeyAuthenticationProvider::authenticated)
                .orElseThrow(() -> new BadCredentialsException("Unknown API key"));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return PreAuthenticatedAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static Authentication authenticated(ApiKey apiKey) {
        return new PreAuthenticatedAuthenticationToken(
                new CurrentUser(apiKey.userId()),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + apiKey.role())));
    }

    private static String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
