package com.limio.api.modulos.perfil.midia;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.perfil.midia.enums.TipoMidia;

@Component
public class MidiaService extends BaseService<Midia, UUID, MidiaRepository> {

    public MidiaService(MidiaRepository repository) {
        super(repository);
    }

    public Optional<Midia> buscarFotoPerfilAtiva(UUID usuarioId) {
        return repository.findByUsuarioIdAndTipoAndExpurgoEmIsNull(usuarioId, TipoMidia.FOTO_PERFIL);
    }

    /** Serializa trocas simultâneas da foto do mesmo usuário até o fim da transação. */
    public void travarFotoPerfil(UUID usuarioId) {
        repository.travarFotoPerfilPorUsuario(usuarioId.toString());
    }

    /** Grava já, pra o índice de "uma foto ativa" ver o expurgo da anterior antes do insert da nova. */
    public Midia salvarAgora(Midia midia) {
        return repository.saveAndFlush(midia);
    }
}
