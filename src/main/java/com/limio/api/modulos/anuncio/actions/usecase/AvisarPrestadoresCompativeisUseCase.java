package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.limio.api.comum.eventos.NovoServicoNaAreaEvent;
import com.limio.api.comum.portas.ConsultaCategoria;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.ConsultaPrestadoresCompativeis;
import com.limio.api.comum.portas.PrestadorCompativel;
import com.limio.api.comum.portas.ResumoCategoria;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.eventos.AnuncioPublicadoEvent;
import com.limio.api.modulos.anuncio.excecao.CategoriaDoAnuncioNaoEncontradaException;

/**
 * UC "Publicar no mural geográfico" — aviso aos Prestadores compatíveis (ETI-65). Ao publicar um anúncio, pede um
 * aviso "Novo serviço na sua área" ({@link NovoServicoNaAreaEvent}) para cada Prestador que o veria no mural. Quem
 * envia é o {@code notificacao} (ETI-112), respeitando os canais ligados.
 *
 * <p>Quem recebe:
 * <ol>
 *   <li>compatível pela regra do mural ({@link ConsultaPrestadoresCompativeis}, do {@code perfil}): perfil completo,
 *       categoria/"Outros" e raio;</li>
 *   <li>não é o dono do anúncio;</li>
 *   <li>conta apta ({@link ConsultaDadosUsuario#filtrarAptosAReceberAviso}): não encerrada, não suspensa, e-mail
 *       confirmado. O papel ativo não conta: quem está navegando como Empregador continua Prestador.</li>
 * </ol>
 * "Pausado" não existe ainda no perfil; entra na consulta do {@code perfil} quando o card de pausar chegar.
 *
 * <p>Roda depois do commit da publicação, em transação própria (ADR-0003): falha aqui não desfaz o anúncio. Anúncio
 * que sumiu ou saiu de {@code PUBLICADO} entre o commit e agora não avisa ninguém; sem coordenadas no retrato (só
 * em dado anterior à ETI-59) também não, porque não há distância. Sem Prestador no raio, o anúncio
 * segue publicado e ninguém é avisado (UC).
 */
@Service
public class AvisarPrestadoresCompativeisUseCase {

    private final AnuncioService anuncioService;
    private final ConsultaPrestadoresCompativeis consultaPrestadoresCompativeis;
    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final ConsultaCategoria consultaCategoria;
    private final ApplicationEventPublisher eventos;

    public AvisarPrestadoresCompativeisUseCase(AnuncioService anuncioService,
            ConsultaPrestadoresCompativeis consultaPrestadoresCompativeis,
            ConsultaDadosUsuario consultaDadosUsuario,
            ConsultaCategoria consultaCategoria,
            ApplicationEventPublisher eventos) {
        this.anuncioService = anuncioService;
        this.consultaPrestadoresCompativeis = consultaPrestadoresCompativeis;
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.consultaCategoria = consultaCategoria;
        this.eventos = eventos;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void aoPublicarAnuncio(AnuncioPublicadoEvent evento) {
        anuncioService.buscarPorId(evento.anuncioId())
                .filter(anuncio -> anuncio.getStatus() == StatusAnuncio.PUBLICADO)
                .filter(anuncio -> anuncio.getLatitudeSnapshot() != null && anuncio.getLongitudeSnapshot() != null)
                .ifPresent(this::avisar);
    }

    private void avisar(Anuncio anuncio) {
        List<PrestadorCompativel> compativeis = consultaPrestadoresCompativeis.buscarCompativeis(
                anuncio.getCategoriaId(), anuncio.getLatitudeSnapshot(), anuncio.getLongitudeSnapshot()).stream()
                .filter(prestador -> !anuncio.pertenceA(prestador.usuarioId()))
                .toList();
        if (compativeis.isEmpty()) {
            return;
        }

        Set<UUID> aptos = consultaDadosUsuario.filtrarAptosAReceberAviso(
                compativeis.stream().map(PrestadorCompativel::usuarioId).toList());
        String categoriaNome = consultaCategoria.buscarPorId(anuncio.getCategoriaId())
                .map(ResumoCategoria::nome)
                .orElseThrow(() -> new CategoriaDoAnuncioNaoEncontradaException(anuncio.getId()));
        Instant agora = Instant.now();

        compativeis.stream()
                .filter(prestador -> aptos.contains(prestador.usuarioId()))
                .forEach(prestador -> eventos.publishEvent(new NovoServicoNaAreaEvent(prestador.usuarioId(),
                        anuncio.getId(), anuncio.getTitulo(), categoriaNome, anuncio.getBairroRegiaoSnapshot(),
                        prestador.distanciaKm(), agora)));
    }
}
