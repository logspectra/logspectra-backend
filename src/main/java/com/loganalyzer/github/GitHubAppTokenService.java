package com.loganalyzer.github;

import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubAppTokenService {

    private final GitHubAppProperties properties;
    private final AtomicReference<CachedToken> cached = new AtomicReference<>();

    public String getInstallationToken() {
        ensureConfigured();
        CachedToken existing = cached.get();
        Instant now = Instant.now();
        if (existing != null && existing.expiresAt().isAfter(now.plusSeconds(60))) {
            return existing.token();
        }
        synchronized (this) {
            existing = cached.get();
            if (existing != null && existing.expiresAt().isAfter(now.plusSeconds(60))) {
                return existing.token();
            }
            String jwt = createAppJwt();
            String installationId = properties.getApp().getInstallationId().trim();
            RestClient client = RestClient.builder()
                .baseUrl(properties.getApp().getApiBaseUrl())
                .build();
            long started = System.currentTimeMillis();
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = client.post()
                    .uri("/app/installations/{id}/access_tokens", installationId)
                    .header("Authorization", "Bearer " + jwt)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .body(Map.class);
                if (body == null || body.get("token") == null) {
                    throw new IllegalStateException("GitHub installation token response missing token");
                }
                String token = String.valueOf(body.get("token"));
                Instant expiresAt = body.get("expires_at") != null
                    ? Instant.parse(String.valueOf(body.get("expires_at")))
                    : Instant.now().plusSeconds(3500);
                cached.set(new CachedToken(token, expiresAt));
                log.debug("operation=getInstallationToken feature=github-app-pr status=exit durationMs={}",
                    System.currentTimeMillis() - started);
                return token;
            } catch (RuntimeException ex) {
                log.error("operation=getInstallationToken feature=github-app-pr status=error error={} durationMs={}",
                    ex.getMessage(), System.currentTimeMillis() - started, ex);
                throw ex;
            }
        }
    }

    void ensureConfigured() {
        if (!properties.getApp().isEnabled()) {
            throw new IllegalStateException("github.app.enabled is false");
        }
        if (!StringUtils.hasText(properties.getApp().getAppId())) {
            throw new IllegalStateException("github.app.app-id is required");
        }
        if (!StringUtils.hasText(properties.getApp().getInstallationId())) {
            throw new IllegalStateException("github.app.installation-id is required");
        }
    }

    String createAppJwt() {
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer(properties.getApp().getAppId().trim())
            .issuedAt(java.util.Date.from(now.minusSeconds(60)))
            .expiration(java.util.Date.from(now.plusSeconds(9 * 60)))
            .signWith(loadPrivateKey(), Jwts.SIG.RS256)
            .compact();
    }

    private PrivateKey loadPrivateKey() {
        String pem = resolvePem();
        boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        String normalized = pem
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        try {
            byte[] pkcs8 = pkcs1 ? wrapPkcs1ToPkcs8(decoded) : decoded;
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (Exception ex) {
            throw new IllegalStateException(
                "Failed to parse GitHub App private key. Prefer PKCS#8: openssl pkcs8 -topk8 -nocrypt -in app.pem -out app-pkcs8.pem",
                ex);
        }
    }

    private String resolvePem() {
        if (StringUtils.hasText(properties.getApp().getPrivateKey())) {
            return properties.getApp().getPrivateKey().replace("\\n", "\n");
        }
        if (StringUtils.hasText(properties.getApp().getPrivateKeyPath())) {
            try {
                return Files.readString(Path.of(properties.getApp().getPrivateKeyPath()), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                throw new IllegalStateException("Cannot read github.app.private-key-path", ex);
            }
        }
        throw new IllegalStateException("Set github.app.private-key or github.app.private-key-path");
    }

    private static byte[] wrapPkcs1ToPkcs8(byte[] pkcs1) {
        byte[] algorithmIdentifier = {
            0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
            (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
        };
        byte[] lenBytes = encodeLength(pkcs1.length);
        byte[] octet = new byte[1 + lenBytes.length + pkcs1.length];
        octet[0] = 0x04;
        System.arraycopy(lenBytes, 0, octet, 1, lenBytes.length);
        System.arraycopy(pkcs1, 0, octet, 1 + lenBytes.length, pkcs1.length);

        int versionLen = 3;
        int seqLen = versionLen + algorithmIdentifier.length + octet.length;
        byte[] seqLenBytes = encodeLength(seqLen);
        byte[] out = new byte[1 + seqLenBytes.length + seqLen];
        int i = 0;
        out[i++] = 0x30;
        System.arraycopy(seqLenBytes, 0, out, i, seqLenBytes.length);
        i += seqLenBytes.length;
        out[i++] = 0x02;
        out[i++] = 0x01;
        out[i++] = 0x00;
        System.arraycopy(algorithmIdentifier, 0, out, i, algorithmIdentifier.length);
        i += algorithmIdentifier.length;
        System.arraycopy(octet, 0, out, i, octet.length);
        return out;
    }

    private static byte[] encodeLength(int length) {
        if (length < 128) {
            return new byte[]{(byte) length};
        }
        if (length < 256) {
            return new byte[]{(byte) 0x81, (byte) length};
        }
        return new byte[]{(byte) 0x82, (byte) ((length >> 8) & 0xff), (byte) (length & 0xff)};
    }

    private record CachedToken(String token, Instant expiresAt) {}
}
