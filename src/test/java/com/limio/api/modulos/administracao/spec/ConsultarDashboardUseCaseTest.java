package com.limio.api.modulos.administracao.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.limio.api.comum.portas.ConsultaTotaisDeAnuncios;
import com.limio.api.comum.portas.ConsultaTotaisDeAssinaturas;
import com.limio.api.comum.portas.ConsultaTotaisDeDenuncias;
import com.limio.api.comum.portas.ConsultaTotaisDeUsuarios;
import com.limio.api.comum.portas.TotaisDeAnuncios;
import com.limio.api.modulos.administracao.NumerosDaOperacao;
import com.limio.api.modulos.administracao.actions.mapper.DashboardMapper;
import com.limio.api.modulos.administracao.actions.usecase.ConsultarDashboardUseCase;
import com.limio.api.modulos.administracao.enums.StatusOperacional;
import com.limio.api.modulos.administracao.records.DashboardResponse;

class ConsultarDashboardUseCaseTest {

    private final ConsultaTotaisDeUsuarios usuarios = mock(ConsultaTotaisDeUsuarios.class);
    private final ConsultaTotaisDeAnuncios anuncios = mock(ConsultaTotaisDeAnuncios.class);
    private final ConsultaTotaisDeAssinaturas assinaturas = mock(ConsultaTotaisDeAssinaturas.class);
    private final ConsultaTotaisDeDenuncias denuncias = mock(ConsultaTotaisDeDenuncias.class);

    private final ConsultarDashboardUseCase useCase = new ConsultarDashboardUseCase(usuarios, anuncios, assinaturas,
            denuncias);

    @Test
    void juntaOsTotaisDeCadaModulo() {
        when(usuarios.contarContasNaoEncerradas()).thenReturn(120L);
        when(anuncios.contar()).thenReturn(new TotaisDeAnuncios(40, 95, 30));
        when(assinaturas.contarGoldAtivas()).thenReturn(12L);
        when(denuncias.contarAbertas()).thenReturn(3L);

        NumerosDaOperacao numeros = useCase.executar();

        assertThat(numeros).isEqualTo(
                new NumerosDaOperacao(120, 40, 95, 30, 12, 3, StatusOperacional.OPERANDO_NORMALMENTE));
    }

    @Test
    void baseVaziaDaZeroEmTudo() {
        when(anuncios.contar()).thenReturn(new TotaisDeAnuncios(0, 0, 0));

        NumerosDaOperacao numeros = useCase.executar();

        assertThat(numeros).isEqualTo(new NumerosDaOperacao(0, 0, 0, 0, 0, 0, StatusOperacional.OPERANDO_NORMALMENTE));
    }

    @Test
    void mapperLevaOTextoDoStatus() {
        DashboardResponse resposta = new DashboardMapper().toResponse(
                new NumerosDaOperacao(1, 2, 3, 4, 5, 6, StatusOperacional.OPERANDO_NORMALMENTE));

        assertThat(resposta.statusOperacional()).isEqualTo(StatusOperacional.OPERANDO_NORMALMENTE);
        assertThat(resposta.statusOperacionalDescricao()).isEqualTo("Operando normalmente");
        assertThat(resposta.denunciasAbertas()).isEqualTo(6);
    }

    @Test
    void respostaNaoAceitaContadorNegativo() {
        assertThatThrownBy(() -> new DashboardResponse(1, -1, 0, 0, 0, 0, StatusOperacional.OPERANDO_NORMALMENTE,
                "Operando normalmente"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anuncios");
    }
}
