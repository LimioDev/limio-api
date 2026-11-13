package com.limio.api.comum.midia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.limio.api.TestcontainersConfiguration;
import com.limio.api.comum.midia.enums.TipoMidia;

/**
 * Persistência da evidência de check-out (ETI-90): o tipo novo grava com {@code anuncio_id} e a constraint
 * da V30 continua barrando {@code anuncio_id} onde não faz sentido.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MidiaEvidenciaCheckoutTest {

    @Autowired
    MidiaRepository repository;

    @AfterEach
    void limpar() {
        repository.deleteAll();
    }

    @Test
    void dadaEvidenciaDeCheckout_quandoGrava_entaoPersisteComTipoEAnuncio() {
        UUID prestador = UUID.randomUUID();
        UUID anuncio = UUID.randomUUID();

        Midia salva = repository.saveAndFlush(
                Midia.evidenciaCheckout(prestador, anuncio, "fotos-checkout/" + anuncio + "/a.jpg", "image/jpeg", 800, 600, 1234));

        Midia lida = repository.findById(salva.getId()).orElseThrow();
        assertThat(lida.getTipo()).isEqualTo(TipoMidia.EVIDENCIA_CHECKOUT);
        assertThat(lida.getAnuncioId()).isEqualTo(anuncio);
        assertThat(lida.getUsuarioId()).isEqualTo(prestador);
        assertThat(lida.isExifRemovido()).isTrue();
    }

    @Test
    void dadasEvidenciasEFotosDoAnuncio_quandoConsultaPorTipo_entaoNaoSeMisturam() {
        UUID dono = UUID.randomUUID();
        UUID prestador = UUID.randomUUID();
        UUID anuncio = UUID.randomUUID();
        repository.saveAndFlush(Midia.fotoAnuncio(dono, anuncio, "fotos-anuncio/" + anuncio + "/a.jpg", "image/jpeg", 10, 10, 10));
        repository.saveAndFlush(
                Midia.evidenciaCheckout(prestador, anuncio, "fotos-checkout/" + anuncio + "/b.jpg", "image/jpeg", 10, 10, 10));

        assertThat(repository.findByAnuncioIdAndTipoAndExpurgoEmIsNullOrderByCriadoEmAscIdAsc(anuncio, TipoMidia.FOTO_ANUNCIO))
                .hasSize(1);
        assertThat(repository.findByAnuncioIdAndTipoAndExpurgoEmIsNullOrderByCriadoEmAscIdAsc(anuncio, TipoMidia.EVIDENCIA_CHECKOUT))
                .hasSize(1);
    }

    @Test
    void dadaEvidenciaSemAnuncio_quandoGrava_entaoBancoRecusa() {
        Midia semAnuncio = Midia.evidenciaCheckout(UUID.randomUUID(), null, "fotos-checkout/x.jpg", "image/jpeg", 10, 10, 10);

        assertThatThrownBy(() -> repository.saveAndFlush(semAnuncio)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dadaFotoDePerfilComAnuncio_quandoGrava_entaoBancoContinuaRecusando() {
        Midia perfilComAnuncio = Midia.fotoPerfil(UUID.randomUUID(), "fotos-perfil/x.jpg", "image/jpeg", 10, 10, 10);
        org.springframework.test.util.ReflectionTestUtils.setField(perfilComAnuncio, "anuncioId", UUID.randomUUID());

        assertThatThrownBy(() -> repository.saveAndFlush(perfilComAnuncio)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
