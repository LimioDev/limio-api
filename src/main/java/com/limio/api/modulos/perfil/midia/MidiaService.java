package com.limio.api.modulos.perfil.midia;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.perfil.midia.enums.TipoMidia;

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

    /** Grava já, pra o índice de "uma foto ativa" ver o expurgo da anterior antes do insert da nova. */
    public Midia salvarAgora(Midia midia) {
        return repository.saveAndFlush(midia);
    }
}
