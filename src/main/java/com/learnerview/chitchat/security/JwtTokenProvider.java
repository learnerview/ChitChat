package com.learnerview.chitchat.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Component
@SuppressWarnings("unchecked")
public class JwtTokenProvider {

    private static final String TENANT_CLAIM = "tenantId";

    @Value("${app.jwtSecret}")
    private String jwtSecret;

    @Value("${app.jwtExpirationInMs:86400000}")
    private int jwtExpirationInMs;

    @Value("${app.externalAuth.enabled:false}")
    private boolean externalAuthEnabled;

    @Value("${app.externalAuth.verifySignature:false}")
    private boolean externalVerifySignature;

    @Value("${app.externalAuth.jwtSecret:}")
    private String externalJwtSecret;

    @Value("${app.externalAuth.usernameClaim:preferred_username}")
    private String externalUsernameClaim;

    @Value("${app.externalAuth.userIdClaim:sub}")
    private String externalUserIdClaim;

    @Autowired
    private ObjectMapper objectMapper;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(deriveHmacKeyMaterial(jwtSecret));
    }

    private SecretKey getExternalSigningKey() {
        return Keys.hmacShaKeyFor(deriveHmacKeyMaterial(externalJwtSecret));
    }

    private byte[] deriveHmacKeyMaterial(String secret) {
        if (secret == null || secret.isBlank() || secret.equals("change-me-to-a-long-random-secret-key-minimum-32-chars")) {
            throw new IllegalStateException("A secure jwtSecret must be provided in application properties");
        }
        try {
            return MessageDigest.getInstance("SHA-512")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-512 algorithm is not available", ex);
        }
    }

    public String generateToken(Authentication authentication, String tenantId) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
        return generateTokenForUsername(userPrincipal.getUsername(), tenantId);
    }

    public String generateTokenForUsername(String username, String tenantId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        return Jwts.builder()
                .setSubject(username)
                .claim(TENANT_CLAIM, tenantId)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

    public String getTenantIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        Object tenant = claims.get(TENANT_CLAIM);
        return tenant == null ? null : tenant.toString();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean isExternalAuthEnabled() {
        return externalAuthEnabled;
    }

    public boolean validateExternalToken(String token) {
        if (!externalAuthEnabled) {
            return false;
        }

        try {
            if (externalVerifySignature) {
                if (externalJwtSecret == null || externalJwtSecret.isBlank()) {
                    return false;
                }
                Jwts.parserBuilder()
                        .setSigningKey(getExternalSigningKey())
                        .build()
                        .parseClaimsJws(token);
                return true;
            }

            Map<String, Object> claims = parseWithoutVerification(token);
            return claims.containsKey(externalUsernameClaim) || claims.containsKey("sub");
        } catch (Exception ex) {
            return false;
        }
    }

    public String getExternalUsernameFromToken(String token) {
        Map<String, Object> claims = getExternalClaims(token);
        Object username = claims.get(externalUsernameClaim);
        if (username == null) {
            username = claims.get("sub");
        }
        return username == null ? null : username.toString();
    }

    public String getExternalUserIdFromToken(String token) {
        Map<String, Object> claims = getExternalClaims(token);
        Object userId = claims.get(externalUserIdClaim);
        return userId == null ? null : userId.toString();
    }

    private Map<String, Object> getExternalClaims(String token) {
        try {
            if (externalVerifySignature && externalJwtSecret != null && !externalJwtSecret.isBlank()) {
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(getExternalSigningKey())
                        .build()
                        .parseClaimsJws(token)
                        .getBody();
                return claims;
            }

            return parseWithoutVerification(token);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid external JWT", ex);
        }
    }

    private Map<String, Object> parseWithoutVerification(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid JWT format");
        }

        byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
        return objectMapper.readValue(decoded, Map.class);
    }
}
