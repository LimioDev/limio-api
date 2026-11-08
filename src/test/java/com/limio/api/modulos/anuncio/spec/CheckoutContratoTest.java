package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.enums.CodigoErro;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.actions.mapper.ServicoMapper;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.excecao.FotoObrigatoriaException;

/** Contrato do check-out (ETI-90): resposta e erro de foto obrigatória. */
class CheckoutContratoTest {

    private static final Instant AGORA = Instant.parse("2026-11-07T12:00:00Z");

    private final ServicoMapper mapper = new ServicoMapper();

    @Test
    void dadoCheckoutRegistrado_quandoMapeia_entaoTrazStatusCheckoutEPrazo() {
        Anuncio anuncio = Anuncio.builder().status(StatusAnuncio.EM_ANDAMENTO).prazoValidacaoHoras(24).build();
        anuncio.registrarCheckout(AGORA);

        var response = mapper.toCheckoutResponse(anuncio);

        assertThat(response.status()).isEqualTo(StatusAnuncio.AGUARDANDO_VALIDACAO);
        assertThat(response.checkoutEm()).isEqualTo(AGORA);
        assertThat(response.prazoValidacaoEm()).isEqualTo(Instant.parse("2026-11-08T12:00:00Z"));
    }

    @Test
    void dadoAnuncioSemPrazoGravado_quandoMapeia_entaoPrazoNulo() {
        Anuncio anuncio = Anuncio.builder().status(StatusAnuncio.PUBLICADO).build();
        anuncio.registrarCheckout(AGORA);

        assertThat(mapper.toCheckoutResponse(anuncio).prazoValidacaoEm()).isNull();
    }

    @Test
    void dadoCheckoutSemFoto_quandoLancaFotoObrigatoria_entao422ComCodigoProprio() {
        var excecao = new FotoObrigatoriaException();

        assertThat(excecao.getCodigo()).isEqualTo(CodigoErro.FOTO_OBRIGATORIA);
        assertThat(excecao.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(excecao.getMessage()).isEqualTo("Envie ao menos uma foto do serviço.");
    }
}
