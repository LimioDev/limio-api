package com.limio.api.modulos.moderacao.actions.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaFotoPerfil;
import com.limio.api.comum.portas.ConsultaPerfilEmpregador;
import com.limio.api.comum.portas.ConsultaServico;
import com.limio.api.comum.portas.ResumoServico;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.moderacao.AvaliacaoPendente;
import com.limio.api.modulos.moderacao.OutraParte;
import com.limio.api.modulos.moderacao.actions.helper.JanelaAvaliacaoHelper;
import com.limio.api.modulos.moderacao.avaliacao.AvaliacaoService;
import com.limio.api.modulos.moderacao.excecao.ServicoNaoEncontradoException;

/**
 * Consultar avaliação pendente (ETI-107): a outra parte, o serviço e se ainda dá pra avaliar. Serviço não concluído não
 * é erro aqui, só {@code podeAvaliar = false}: quem recusa é {@link AvaliarUseCase}. "Pular" (UC "Pular avaliação") não
 * tem rota: não grava nada, e esta consulta continua respondendo enquanto a janela estiver aberta.
 */
@Service
public class ConsultarAvaliacaoPendenteUseCase {

    private final ConsultaServico consultaServico;
    private final AvaliacaoService avaliacaoService;
    private final ConsultaPerfilEmpregador consultaPerfilEmpregador;
    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final ConsultaFotoPerfil consultaFotoPerfil;
    private final Clock relogio;

    public ConsultarAvaliacaoPendenteUseCase(ConsultaServico consultaServico, AvaliacaoService avaliacaoService,
            ConsultaPerfilEmpregador consultaPerfilEmpregador, ConsultaDadosUsuario consultaDadosUsuario,
            ConsultaFotoPerfil consultaFotoPerfil, Clock relogio) {
        this.consultaServico = consultaServico;
        this.avaliacaoService = avaliacaoService;
        this.consultaPerfilEmpregador = consultaPerfilEmpregador;
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.consultaFotoPerfil = consultaFotoPerfil;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public AvaliacaoPendente executar(UUID anuncioId, UUID usuarioId) {
        ResumoServico servico = consultaServico.buscarPorId(anuncioId)
                .filter(encontrado -> encontrado.participa(usuarioId))
                .orElseThrow(ServicoNaoEncontradoException::new);
        UUID outraParteId = servico.outraParte(usuarioId);
        OutraParte outraParte = outraParte(outraParteId, servico.papelDe(outraParteId));
        if (!servico.concluido()) {
            return new AvaliacaoPendente(servico, outraParte, null, false);
        }

        Instant agora = Instant.now(relogio);
        boolean podeAvaliar = JanelaAvaliacaoHelper.aberta(servico.concluidoEm(), agora)
                && !avaliacaoService.jaAvaliou(anuncioId, usuarioId);
        return new AvaliacaoPendente(servico, outraParte, JanelaAvaliacaoHelper.avaliarAte(servico.concluidoEm()),
                podeAvaliar);
    }

    /**
     * Mesma regra do cabeçalho do chat: Empregador pelo nome de exibição do perfil, Prestador pelo nome do cadastro.
     * Só leitura: consultar nunca cria o perfil da outra parte.
     */
    private OutraParte outraParte(UUID outraParteId, PapelUsuario papel) {
        Optional<OutraParte> encontrada = switch (papel) {
            case EMPREGADOR -> consultaPerfilEmpregador.buscarResumoSemCriar(outraParteId)
                    .map(perfil -> new OutraParte(perfil.nomeExibicao(), perfil.fotoUrl(), perfil.fotoExpiraEm(),
                            papel));
            case PRESTADOR -> consultaDadosUsuario.buscarPorId(outraParteId).map(dados -> {
                Optional<LinkTemporario> foto = consultaFotoPerfil.buscarFoto(outraParteId);
                return new OutraParte(dados.nomeCompleto(), foto.map(LinkTemporario::url).orElse(null),
                        foto.map(LinkTemporario::expiraEm).orElse(null), papel);
            });
        };
        return encontrada.orElseGet(() -> OutraParte.contaEncerrada(papel));
    }
}
