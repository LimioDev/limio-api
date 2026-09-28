package com.limio.api.comum.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class StorageConfigTest {

    private final StorageConfig config = new StorageConfig();

    @Test
    void deveGerarUrlAssinadaApontandoProEndpointConfigurado() {
        var props = new StorageProperties("limio-teste", "https://t3.storageapi.dev", "auto", "chave", "segredo");

        try (var presigner = config.s3Presigner(props)) {
            var url = presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(5))
                    .putObjectRequest(PutObjectRequest.builder().bucket(props.bucket()).key("anuncios/foto.jpg").build())
                    .build()).url();

            assertThat(url.getHost()).isEqualTo("limio-teste.t3.storageapi.dev");
            assertThat(url.getPath()).isEqualTo("/anuncios/foto.jpg");
            assertThat(url.getQuery()).contains("X-Amz-Signature=");
        }
    }

    @Test
    void deveUsarRegiaoAutoQuandoNaoInformada() {
        var props = new StorageProperties("limio-teste", null, null, "chave", "segredo");

        assertThat(props.region()).isEqualTo("auto");
    }

    @Test
    void deveRecusarSemCredenciais() {
        assertThatThrownBy(() -> new StorageProperties("limio-teste", null, "auto", "", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
