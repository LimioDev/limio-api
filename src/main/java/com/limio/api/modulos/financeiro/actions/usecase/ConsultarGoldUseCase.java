package com.limio.api.modulos.financeiro.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.financeiro.ConsultaGold;
import com.limio.api.modulos.financeiro.excecao.PlanoNaoEncontradoException;
import com.limio.api.modulos.financeiro.excecao.SomentePrestadorAssinaGoldException;
import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamentoService;
import com.limio.api.modulos.financeiro.plano.Plano;
import com.limio.api.modulos.financeiro.plano.PlanoService;

/**
 * UC — consultar o plano GOLD antes de assinar (ETI-81): nome, preço, ciclo e o cartão salvo do
 * Prestador, se houver. Só lê, não conhece HTTP nem record de contrato.
 */
@Service
public class ConsultarGoldUseCase {

    private final PlanoService planoService;
    private final MeioPagamentoService meioPagamentoService;

    public ConsultarGoldUseCase(PlanoService planoService, MeioPagamentoService meioPagamentoService) {
        this.planoService = planoService;
        this.meioPagamentoService = meioPagamentoService;
    }

    public ConsultaGold executar(UUID usuarioId, PapelUsuario papelAtivo) {
        exigirPrestador(papelAtivo);

        Plano plano = planoService.buscarAtivoPorCodigo(Plano.CODIGO_GOLD).orElseThrow(PlanoNaoEncontradoException::new);
        return new ConsultaGold(plano, meioPagamentoService.buscarPorUsuario(usuarioId));
    }

    private static void exigirPrestador(PapelUsuario papelAtivo) {
        switch (papelAtivo) {
            case PRESTADOR -> {
            }
            case EMPREGADOR -> throw new SomentePrestadorAssinaGoldException();
        }
    }
}
