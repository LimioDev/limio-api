package com.limio.api.comum.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciais do bucket S3-compatível. Vêm das variáveis STORAGE_* (Railway hoje);
 * trocar de provedor (GCS interop, AWS S3) é só trocar os valores.
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        String bucket,
        String endpoint,
        String region,
        String accessKeyId,
        String secretAccessKey) {

    public StorageProperties {
        if (bucket == null || bucket.isBlank())
            throw new IllegalArgumentException("storage.bucket é obrigatório");
        if (accessKeyId == null || accessKeyId.isBlank() || secretAccessKey == null || secretAccessKey.isBlank())
            throw new IllegalArgumentException("storage.access-key-id e storage.secret-access-key são obrigatórios");
        if (region == null || region.isBlank())
            region = "auto";
    }
}
