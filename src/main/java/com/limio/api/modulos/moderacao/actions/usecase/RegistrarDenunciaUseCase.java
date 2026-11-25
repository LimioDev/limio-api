package com.limio.api.modulos.moderacao.actions.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.ImagemHelper;
import com.limio.api.comum.midia.ImagemHelper.ImagemProcessada;
import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.comum.midia.excecao.FotoInvalidaException;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.comum.portas.ConsultaCandidatura;
import com.limio.api.comum.portas.ConsultaChat;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaServico;
import com.limio.api.comum.portas.PartesAnuncio;
import com.limio.api.comum.portas.ResumoCandidatura;
import com.limio.api.comum.portas.ResumoMensagem;
import com.limio.api.modulos.moderacao.denuncia.AlvoDenuncia;
import com.limio.api.modulos.moderacao.denuncia.Denuncia;
import com.limio.api.modulos.moderacao.denuncia.DenunciaService;
import com.limio.api.modulos.moderacao.enums.MotivoDenuncia;
import com.limio.api.modulos.moderacao.eventos.EvidenciaDenunciaEnviadaEvent;
import com.limio.api.modulos.moderacao.excecao.AlvoNaoAcessivelException;

/**
 * UC "Registrar denúncia" (ETI-114). Resolve o alvo, confere que o denunciante tem acesso a ele, guarda a foto opcional
 * como {@code Midia} {@code EVIDENCIA_DENUNCIA} e grava a denúncia {@code ABERTA}, com prazo de análise. A denúncia
 * entra na fila do admin (US58). Não muda nada no serviço: não reabre, não mexe no status do anúncio e não move
 * dinheiro. Por isso a denúncia de não pagamento depois de {@code CONCLUIDO} ou em {@code AGUARDANDO_VALIDACAO} é
 * registrada igual às outras.
 *
 * <p>Quem pode denunciar o quê (o contexto mais específico informado decide):
 * <ul>
 *   <li><b>mensagem:</b> quem participa do chat dela. O denunciado é o remetente.</li>
 *   <li><b>candidatura (chat):</b> uma das partes. O denunciado é a outra.</li>
 *   <li><b>anúncio que já teve Prestador escolhido (serviço):</b> uma das partes, mesmo com o serviço cancelado ou o
 *       anúncio encerrado depois da escolha. O denunciado é a outra.</li>
 *   <li><b>anúncio no mural ({@code PUBLICADO}) que nunca teve escolhido:</b> qualquer usuário, menos o dono. O
 *       denunciado é o Empregador dono. Encerrado sem nunca ter tido escolhido já saiu do mural e não é acessível
 *       por aqui (quem se candidatou denuncia pela candidatura).</li>
 *   <li><b>perfil (só o denunciado):</b> qualquer usuário, contra uma conta ativa. Ter visto o perfil não fica
 *       registrado em lugar nenhum.</li>
 * </ul>
 * Ninguém denuncia a si mesmo. Se o cliente mandar {@code denunciadoId} junto com um contexto, ele precisa bater com o
 * denunciado resolvido. Todas as recusas respondem {@code ALVO_NAO_ACESSIVEL} (404), igual a um id inexistente, e vêm
 * antes da conferência da foto, pra não revelar nada a terceiros.
 *
 * <p>A mesma denúncia mandada de novo (toque duplo, retry do app) não vira outra: se o denunciante já tem uma em aberto
 * com o mesmo alvo e o mesmo motivo, ela é devolvida como está, sem gravar nem enviar foto. A trava
 * {@link EscopoTrava#DENUNCIA} por denunciante serializa dois envios simultâneos.
 */
@Service
public class RegistrarDenunciaUseCase {

    static final String PREFIXO_CHAVE = "evidencias-denuncia/";

    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final ConsultaServico consultaServico;
    private final ConsultaCandidatura consultaCandidatura;
    private final ConsultaChat consultaChat;
    private final DenunciaService denunciaService;
    private final MidiaService midiaService;
    private final ArmazenamentoService armazenamentoService;
    private final TravaTransacional travaTransacional;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;
    private final Duration prazoAnalise;
    private final long tamanhoMaximoFotoBytes;

    public RegistrarDenunciaUseCase(ConsultaDadosUsuario consultaDadosUsuario,
            ConsultaServico consultaServico,
            ConsultaCandidatura consultaCandidatura,
            ConsultaChat consultaChat,
            DenunciaService denunciaService,
            MidiaService midiaService,
            ArmazenamentoService armazenamentoService,
            TravaTransacional travaTransacional,
            ApplicationEventPublisher eventos,
            Clock relogio,
            @Value("${moderacao.denuncia.prazo-analise-horas}") long prazoAnaliseHoras,
            @Value("${moderacao.denuncia.foto.tamanho-maximo}") DataSize tamanhoMaximoFoto) {
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.consultaServico = consultaServico;
        this.consultaCandidatura = consultaCandidatura;
        this.consultaChat = consultaChat;
        this.denunciaService = denunciaService;
        this.midiaService = midiaService;
        this.armazenamentoService = armazenamentoService;
        this.travaTransacional = travaTransacional;
        this.eventos = eventos;
        this.relogio = relogio;
        this.prazoAnalise = Duration.ofHours(prazoAnaliseHoras);
        this.tamanhoMaximoFotoBytes = tamanhoMaximoFoto.toBytes();
    }

    /**
     * @param informado o alvo como o cliente mandou: ao menos um campo preenchido (o record já validou)
     * @param descricao o que aconteceu, já sem espaços nas pontas
     * @param foto evidência opcional, {@code null} quando não veio
     */
    @Transactional
    public Denuncia executar(UUID denuncianteId, AlvoDenuncia informado, MotivoDenuncia motivo, String descricao,
            byte[] foto) {
        travaTransacional.travar(EscopoTrava.DENUNCIA, denuncianteId.toString());
        AlvoDenuncia alvo = resolverAlvo(denuncianteId, informado);
        Optional<Denuncia> repetida = denunciaService.buscarEmAbertoIgual(denuncianteId, alvo, motivo);
        if (repetida.isPresent()) {
            return repetida.get();
        }
        UUID evidenciaId = foto == null ? null : guardarEvidencia(denuncianteId, processar(foto));
        Instant agora = Instant.now(relogio);
        return denunciaService.salvarAgora(
                Denuncia.registrar(denuncianteId, alvo, motivo, descricao, evidenciaId, agora.plus(prazoAnalise)));
    }

    private AlvoDenuncia resolverAlvo(UUID denuncianteId, AlvoDenuncia informado) {
        AlvoDenuncia alvo;
        if (informado.mensagemId() != null) {
            alvo = alvoDaMensagem(denuncianteId, informado.mensagemId());
        } else if (informado.candidaturaId() != null) {
            alvo = alvoDaCandidatura(denuncianteId, informado.candidaturaId());
        } else if (informado.anuncioId() != null) {
            alvo = alvoDoAnuncio(denuncianteId, informado.anuncioId());
        } else {
            alvo = alvoDoPerfil(informado.denunciadoId());
        }
        boolean contraSiMesmo = alvo.denunciadoId().equals(denuncianteId);
        boolean denunciadoDivergente = informado.denunciadoId() != null
                && !informado.denunciadoId().equals(alvo.denunciadoId());
        if (contraSiMesmo || denunciadoDivergente) {
            throw new AlvoNaoAcessivelException();
        }
        return alvo;
    }

    private AlvoDenuncia alvoDaMensagem(UUID denuncianteId, UUID mensagemId) {
        ResumoMensagem mensagem = consultaChat.buscarMensagem(mensagemId)
                .filter(encontrada -> encontrada.participa(denuncianteId))
                .orElseThrow(AlvoNaoAcessivelException::new);
        return new AlvoDenuncia(mensagem.remetenteId(), mensagem.anuncioId(), mensagem.candidaturaId(),
                mensagem.id());
    }

    private AlvoDenuncia alvoDaCandidatura(UUID denuncianteId, UUID candidaturaId) {
        ResumoCandidatura candidatura = consultaCandidatura.buscarPorId(candidaturaId)
                .filter(encontrada -> encontrada.participa(denuncianteId))
                .orElseThrow(AlvoNaoAcessivelException::new);
        UUID outraParte = candidatura.contratanteId().equals(denuncianteId) ? candidatura.prestadorId()
                : candidatura.contratanteId();
        return new AlvoDenuncia(outraParte, candidatura.anuncioId(), candidatura.id(), null);
    }

    private AlvoDenuncia alvoDoAnuncio(UUID denuncianteId, UUID anuncioId) {
        PartesAnuncio partes = consultaServico.buscarPartes(anuncioId).orElseThrow(AlvoNaoAcessivelException::new);
        if (partes.teveEscolhido()) {
            if (!partes.participa(denuncianteId)) {
                throw new AlvoNaoAcessivelException();
            }
            return new AlvoDenuncia(partes.outraParte(denuncianteId), anuncioId, null, null);
        }
        if (!partes.noMural()) {
            throw new AlvoNaoAcessivelException();
        }
        return new AlvoDenuncia(partes.contratanteId(), anuncioId, null, null);
    }

    private AlvoDenuncia alvoDoPerfil(UUID denunciadoId) {
        if (consultaDadosUsuario.buscarPorId(denunciadoId).isEmpty()) {
            throw new AlvoNaoAcessivelException();
        }
        return AlvoDenuncia.perfil(denunciadoId);
    }

    private ImagemProcessada processar(byte[] foto) {
        if (foto.length == 0) {
            throw FotoInvalidaException.vazia();
        }
        if (foto.length > tamanhoMaximoFotoBytes) {
            throw FotoInvalidaException.grandeDemais(tamanhoMaximoFotoBytes);
        }
        return ImagemHelper.processar(foto).orElseThrow(FotoInvalidaException::formatoNaoAceito);
    }

    private UUID guardarEvidencia(UUID denuncianteId, ImagemProcessada imagem) {
        String chave = PREFIXO_CHAVE + denuncianteId + "/" + UUID.randomUUID() + "." + imagem.extensao();
        armazenamentoService.enviar(chave, imagem.conteudo(), imagem.mime());
        // Publicado logo após o upload: se algo abaixo falhar, o rollback apaga o objeto.
        eventos.publishEvent(new EvidenciaDenunciaEnviadaEvent(chave));
        Midia midia = midiaService.salvar(Midia.evidenciaDenuncia(denuncianteId, chave, imagem.mime(),
                imagem.largura(), imagem.altura(), imagem.conteudo().length));
        return midia.getId();
    }
}
