package com.limio.api.modulos.financeiro.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.financeiro.ConsultaGold;
import com.limio.api.modulos.financeiro.actions.usecase.ConsultarGoldUseCase;
import com.limio.api.modulos.financeiro.excecao.PlanoNaoEncontradoException;
import com.limio.api.modulos.financeiro.excecao.SomentePrestadorAssinaGoldException;
import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamento;
import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamentoService;
import com.limio.api.modulos.financeiro.plano.Plano;
import com.limio.api.modulos.financeiro.plano.PlanoService;
import com.limio.api.modulos.financeiro.plano.enums.CicloPlano;

@ExtendWith(MockitoExtension.class)
class ConsultarGoldUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();

    @Mock
    private PlanoService planoService;

    @Mock
    private MeioPagamentoService meioPagamentoService;

    private ConsultarGoldUseCase useCase;

    private ConsultarGoldUseCase montar() {
        return new ConsultarGoldUseCase(planoService, meioPagamentoService);
    }

    private static Plano planoGold() {
        return Plano.builder()
                .codigo(Plano.CODIGO_GOLD)
                .nome("Plano GOLD")
                .precoCentavos(2990)
                .moeda("BRL")
                .ciclo(CicloPlano.MENSAL)
                .ativo(true)
                .build();
    }

    @Test
    void dadoPrestadorSemCartaoSalvo_quandoConsulta_entaoDevolvePlanoSemCartao() {
        useCase = montar();
        when(planoService.buscarAtivoPorCodigo(Plano.CODIGO_GOLD)).thenReturn(Optional.of(planoGold()));
        when(meioPagamentoService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.empty());

        ConsultaGold resultado = useCase.executar(USUARIO_ID, PapelUsuario.PRESTADOR);

        assertThat(resultado.plano().getCodigo()).isEqualTo(Plano.CODIGO_GOLD);
        assertThat(resultado.cartaoSalvo()).isEmpty();
    }

    @Test
    void dadoPrestadorComCartaoSalvo_quandoConsulta_entaoDevolveCartao() {
        useCase = montar();
        when(planoService.buscarAtivoPorCodigo(Plano.CODIGO_GOLD)).thenReturn(Optional.of(planoGold()));
        MeioPagamento cartao = MeioPagamento.novo(USUARIO_ID, "tok_abc", "VISA", "1234");
        when(meioPagamentoService.buscarPorUsuario(USUARIO_ID)).thenReturn(Optional.of(cartao));

        ConsultaGold resultado = useCase.executar(USUARIO_ID, PapelUsuario.PRESTADOR);

        assertThat(resultado.cartaoSalvo()).contains(cartao);
    }

    @Test
    void dadoPapelAtivoEmpregador_quandoConsulta_entaoRecusaSemConsultarNada() {
        useCase = montar();

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, PapelUsuario.EMPREGADOR))
                .isInstanceOf(SomentePrestadorAssinaGoldException.class);

        verifyNoInteractions(planoService, meioPagamentoService);
    }

    @Test
    void dadoPlanoGoldInexistenteOuInativo_quandoConsulta_entaoPlanoNaoEncontrado() {
        useCase = montar();
        when(planoService.buscarAtivoPorCodigo(Plano.CODIGO_GOLD)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(USUARIO_ID, PapelUsuario.PRESTADOR))
                .isInstanceOf(PlanoNaoEncontradoException.class);
    }
}
