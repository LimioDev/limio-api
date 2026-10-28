package com.limio.api.comum.midia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.comum.midia.enums.TipoMidia;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;

@Component
public class MidiaService extends BaseService<Midia, UUID, MidiaRepository> {

    private final TravaTransacional travaTransacional;

    public MidiaService(MidiaRepository repository, TravaTransacional travaTransacional) {
        super(repository);
        this.travaTransacional = travaTransacional;
    }

    public Optional<Midia> buscarFotoPerfilAtiva(UUID usuarioId) {
        return repository.findByUsuarioIdAndTipoAndExpurgoEmIsNull(usuarioId, TipoMidia.FOTO_PERFIL);
    }

    /** Serializa trocas simultâneas da foto do mesmo usuário até o fim da transação. */
    public void travarFotoPerfil(UUID usuarioId) {
        travaTransacional.travar(EscopoTrava.FOTO_PERFIL, usuarioId.toString());
    }

    /** Fotos ativas do anúncio, na ordem em que foram enviadas. */
    public List<Midia> buscarFotosAtivasDoAnuncio(UUID anuncioId) {
        return repository.findByAnuncioIdAndTipoAndExpurgoEmIsNullOrderByCriadoEmAscIdAsc(anuncioId,
                TipoMidia.FOTO_ANUNCIO);
    }

    /** Serializa envios simultâneos de fotos do mesmo anúncio até o fim da transação (limite de quantidade). */
    public void travarFotosAnuncio(UUID anuncioId) {
        travaTransacional.travar(EscopoTrava.FOTOS_ANUNCIO, anuncioId.toString());
    }

    /** Grava já, pra o índice de "uma foto ativa" ver o expurgo da anterior antes do insert da nova. */
    public Midia salvarAgora(Midia midia) {
        return repository.saveAndFlush(midia);
    }
}
