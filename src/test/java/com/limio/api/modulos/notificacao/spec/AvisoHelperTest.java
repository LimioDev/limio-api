package com.limio.api.modulos.notificacao.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.limio.api.modulos.notificacao.Aviso;
import com.limio.api.modulos.notificacao.actions.helper.AvisoHelper;
import com.limio.api.modulos.notificacao.enums.TipoNotificacao;

class AvisoHelperTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID ANUNCIO_ID = UUID.randomUUID();

    @Test
    void novoServicoNaArea_usaVirgulaDecimalEUmaCasa() {
        Aviso aviso = AvisoHelper.novoServicoNaArea(USUARIO_ID, ANUNCIO_ID, "Elétrica", "Perdizes", 3.4321);

        assertThat(aviso.titulo()).isEqualTo("Novo serviço na sua área");
        assertThat(aviso.corpo()).isEqualTo("Elétrica em Perdizes, a 3,4 km de distância.");
        assertThat(aviso.tipo()).isEqualTo(TipoNotificacao.NOVO_SERVICO_NA_AREA);
        assertThat(aviso.anuncioId()).isEqualTo(ANUNCIO_ID);
    }

    @Test
    void checkoutEnviado_segueOTextoDoCard() {
        Aviso aviso = AvisoHelper.checkoutEnviado(USUARIO_ID, ANUNCIO_ID, "Rafael S.", "Troca de chuveiro");

        assertThat(aviso.titulo()).isEqualTo("Check-out enviado");
        assertThat(aviso.corpo())
                .isEqualTo("Rafael S. enviou a foto de 'Troca de chuveiro'. Valide para finalizar o serviço.");
    }

    @Test
    void avisoDeAnuncio_cortaOAssuntoNoTamanhoDoTitulo() {
        Aviso aviso = AvisoHelper.avisoDeAnuncio(USUARIO_ID, ANUNCIO_ID, "x".repeat(200));

        assertThat(aviso.titulo()).hasSize(120);
    }
}
