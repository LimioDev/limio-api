package com.limio.api.modulos.moderacao.actions.usecase;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaContasParaModeracao;
import com.limio.api.comum.portas.ContaParaModeracao;
import com.limio.api.modulos.moderacao.ItemDaFila;
import com.limio.api.modulos.moderacao.denuncia.Denuncia;
import com.limio.api.modulos.moderacao.denuncia.DenunciaService;
import com.limio.api.modulos.moderacao.enums.MotivoDenuncia;
import com.limio.api.modulos.moderacao.enums.Severidade;

/**
 * Fila de denúncias do administrador (ETI-116, somente leitura): as abertas, do prazo de análise mais curto para o
 * mais longo. Os filtros são opcionais e se somam: motivo e severidade (que sai do motivo).
 */
@Component
public class ListarFilaDeDenunciasUseCase {

    private final DenunciaService denunciaService;
    private final ConsultaContasParaModeracao consultaContas;

    public ListarFilaDeDenunciasUseCase(DenunciaService denunciaService, ConsultaContasParaModeracao consultaContas) {
        this.denunciaService = denunciaService;
        this.consultaContas = consultaContas;
    }

    @Transactional(readOnly = true)
    public Page<ItemDaFila> executar(MotivoDenuncia motivo, Severidade severidade, int pagina, int tamanho) {
        Collection<MotivoDenuncia> motivos = motivos(motivo, severidade);
        if (motivos.isEmpty()) {
            return Page.empty(PageRequest.of(pagina, tamanho));
        }
        Page<Denuncia> denuncias = denunciaService.buscarAbertas(motivos, pagina, tamanho);
        Map<UUID, ContaParaModeracao> denunciados = consultaContas.buscarPorIds(
                denuncias.map(Denuncia::getDenunciadoId).toSet());
        return denuncias.map(denuncia -> new ItemDaFila(denuncia, denunciados.get(denuncia.getDenunciadoId())));
    }

    /** Sem filtro, todos os motivos. Motivo fora da severidade pedida dá uma fila vazia. */
    private static Collection<MotivoDenuncia> motivos(MotivoDenuncia motivo, Severidade severidade) {
        List<MotivoDenuncia> todos = severidade == null ? Arrays.asList(MotivoDenuncia.values()) : severidade.motivos();
        return motivo == null ? todos : todos.stream().filter(motivo::equals).toList();
    }
}
