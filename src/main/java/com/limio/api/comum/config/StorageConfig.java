package com.limio.api.comum.config;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Clientes S3 para quem precisar de arquivo (fotos de anúncio, check-out etc.).
 * Cada módulo usa isso a partir do próprio actions/service/, não daqui direto.
 *
 * Sem STORAGE_BUCKET definido (dev local, testes) os beans não sobem.
 */
@Configuration
@ConditionalOnProperty(prefix = "storage", name = "bucket")
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    public S3Client s3Client(StorageProperties props) {
        var builder = S3Client.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(credenciais(props))
                // Provedores S3-compatíveis nem sempre aceitam os checksums CRC que o SDK manda por padrão.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        if (props.endpoint() != null && !props.endpoint().isBlank())
            builder.endpointOverride(URI.create(props.endpoint()));
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(StorageProperties props) {
        var builder = S3Presigner.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(credenciais(props));
        if (props.endpoint() != null && !props.endpoint().isBlank())
            builder.endpointOverride(URI.create(props.endpoint()));
        return builder.build();
    }

    private static StaticCredentialsProvider credenciais(StorageProperties props) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.accessKeyId(), props.secretAccessKey()));
    }
}
