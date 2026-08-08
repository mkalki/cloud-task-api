package com.mkalki.cloudtaskapi.security;

import com.mkalki.cloudtaskapi.entity.RefreshToken;
import com.mkalki.cloudtaskapi.entity.User;
import com.mkalki.cloudtaskapi.exception.InvalidRefreshTokenException;
import com.mkalki.cloudtaskapi.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class RefreshTokenService {
    private final SecureRandom secureRandom = new SecureRandom();
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration}")
    private long refreshTokenExpiration;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public String generateToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(hash);
        }  catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available",e);
        }
    }

    public String createRefreshToken(User user) {
        String rawToken = generateToken();
        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(
                LocalDateTime.now()
                        .plusSeconds(refreshTokenExpiration/1000)
        );
        refreshToken.setUser(user);
        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    public RefreshToken getRefreshToken(String rawToken) {
        String tokenHash = hashToken(rawToken);

        return refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() ->
                        new InvalidRefreshTokenException("Invalid refresh token"));
    }

    public void validateRefreshToken(RefreshToken refreshToken) {
        if(refreshToken.isRevoked()){
            throw new InvalidRefreshTokenException("Refresh token has been revoked");
        }

        if(refreshToken.getExpiresAt().isBefore(LocalDateTime.now())){
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }
    }

    public void revokeRefreshToken(RefreshToken refreshToken) {
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }
}
