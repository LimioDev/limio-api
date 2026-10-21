package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import com.limio.api.comum.config.StorageConfig;
import com.limio.api.comum.config.StorageProperties;
import com.limio.api.modulos.perfil.actions.service.ArmazenamentoService;
import com.limio.api.modulos.perfil.excecao.ArmazenamentoIndisponivelException;
import com.limio.api.modulos.perfil.midia.LinkTemporario;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class ArmazenamentoServiceTest {

    private static final StorageProperties PROPS = new StorageProperties("limio-teste",
            "https://storage.exemplo.test", "auto", "chave", "segredo");

    @Test
    void dadoStorageConfigurado_quandoGeraLink_entaoAssinaAChaveComAValidadeConfigurada() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("s3Presigner", new StorageConfig().s3Presigner(PROPS));
        beans.addBean("storageProperties", PROPS);
        ArmazenamentoService service = new ArmazenamentoService(beans.getBeanProvider(S3Client.class),
                beans.getBeanProvider(S3Presigner.class), beans.getBeanProvider(StorageProperties.class),
                Duration.ofHours(1));

        Instant antes = Instant.now();
        LinkTemporario link = service.gerarLink("fotos-perfil/u1/foto.jpg");

        assertThat(link.url()).contains("limio-teste").contains("fotos-perfil/u1/foto.jpg")
                .contains("X-Amz-Signature=");
        assertThat(link.expiraEm()).isBetween(antes.plus(Duration.ofMinutes(59)), antes.plus(Duration.ofMinutes(61)));
    }

    @Test
    void dadoStorageNaoConfigurado_quandoEnviaOuGeraLink_entao503ESemQuebrarAoRemover() {
        StaticListableBeanFactory vazio = new StaticListableBeanFactory();
        ArmazenamentoService service = new ArmazenamentoService(vazio.getBeanProvider(S3Client.class),
                vazio.getBeanProvider(S3Presigner.class), vazio.getBeanProvider(StorageProperties.class),
                Duration.ofHours(1));

        assertThatThrownBy(() -> service.enviar("x.jpg", new byte[] { 1 }, "image/jpeg"))
                .isInstanceOf(ArmazenamentoIndisponivelException.class);
        assertThatThrownBy(() -> service.gerarLink("x.jpg"))
                .isInstanceOf(ArmazenamentoIndisponivelException.class);
        assertThatCode(() -> service.remover("x.jpg")).doesNotThrowAnyException();
    }
}
