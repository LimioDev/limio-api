package com.limio.api.modulos.anuncio.actions.usecase;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaEndereco;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.comum.portas.EnderecoLocalizado;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.EnderecoDoAnuncio;
import com.limio.api.modulos.anuncio.actions.service.ValidacaoDoAnuncioService;
import com.limio.api.modulos.anuncio.eventos.AnuncioPublicadoEvent;
import com.limio.api.modulos.anuncio.excecao.ContatosNaoConfirmadosException;
import com.limio.api.modulos.anuncio.excecao.SomenteEmpregadorPublicaException;

/**
 * UC "Criar anúncio de serviço (sem valor)" (ETI-59): o Empregador publica o anúncio, que nasce
 * {@code PUBLICADO} e aguarda candidatos.
 *
 * Só publica quem está com o papel ativo {@code EMPREGADOR} e tem e-mail confirmado (ETI-134, telefone não
 * exige confirmação). O endereço
 * (salvo ou avulso) vira retrato no anúncio. A localização do endereço avulso chama o geocodificador, então
 * tudo é conferido antes de abrir a transação: a chamada externa não segura conexão do pool. Só a gravação e
 * o {@link AnuncioPublicadoEvent} ficam na transação.
 */
@Service
public class PublicarAnuncioUseCase {

    private final AnuncioService anuncioService;
    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final ValidacaoDoAnuncioService validacaoDoAnuncio;
    private final ConsultaEndereco consultaEndereco;
    private final ApplicationEventPublisher eventos;
    private final TransactionOperations transacao;
    private final int prazoValidacaoHoras;

    public PublicarAnuncioUseCase(AnuncioService anuncioService,
            ConsultaDadosUsuario consultaDadosUsuario,
            ValidacaoDoAnuncioService validacaoDoAnuncio,
            ConsultaEndereco consultaEndereco,
            ApplicationEventPublisher eventos,
            TransactionOperations transacao,
            @Value("${anuncio.prazo-validacao-horas}") int prazoValidacaoHoras) {
        this.anuncioService = anuncioService;
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.validacaoDoAnuncio = validacaoDoAnuncio;
        this.consultaEndereco = consultaEndereco;
        this.eventos = eventos;
        this.transacao = transacao;
        this.prazoValidacaoHoras = prazoValidacaoHoras;
    }

    /** @param candidato categoria, descrição, data e opcionais vindos da tela; o resto é preenchido aqui */
    public Anuncio executar(UUID usuarioId, PapelUsuario papelAtivo, Anuncio candidato, EnderecoDoAnuncio endereco) {
        exigirEmpregador(papelAtivo);
        validacaoDoAnuncio.exigirDataFutura(candidato.getDataHoraPretendida());
        exigirEmailConfirmado(usuarioId);
        validacaoDoAnuncio.exigirCategoriaAtiva(candidato.getCategoriaId());
        EnderecoLocalizado localizado = localizar(usuarioId, endereco);

        candidato.publicar(usuarioId, enderecoSalvoId(endereco), localizado, prazoValidacaoHoras);
        return transacao.execute(status -> {
            Anuncio publicado = anuncioService.salvar(candidato);
            eventos.publishEvent(new AnuncioPublicadoEvent(publicado.getId()));
            return publicado;
        });
    }

    private static void exigirEmpregador(PapelUsuario papelAtivo) {
        switch (papelAtivo) {
            case EMPREGADOR -> {
            }
            case PRESTADOR -> throw new SomenteEmpregadorPublicaException();
        }
    }

    private void exigirEmailConfirmado(UUID usuarioId) {
        boolean confirmado = consultaDadosUsuario.buscarPorId(usuarioId)
                .map(DadosUsuario::emailConfirmado)
                .orElse(false);
        if (!confirmado) {
            throw new ContatosNaoConfirmadosException();
        }
    }

    private EnderecoLocalizado localizar(UUID usuarioId, EnderecoDoAnuncio endereco) {
        return switch (endereco) {
            case EnderecoDoAnuncio.Salvo salvo -> consultaEndereco.buscarSalvo(usuarioId, salvo.enderecoSalvoId());
            case EnderecoDoAnuncio.Avulso avulso -> consultaEndereco.localizar(avulso.endereco());
        };
    }

    private static UUID enderecoSalvoId(EnderecoDoAnuncio endereco) {
        return switch (endereco) {
            case EnderecoDoAnuncio.Salvo salvo -> salvo.enderecoSalvoId();
            case EnderecoDoAnuncio.Avulso avulso -> null;
        };
    }
}
