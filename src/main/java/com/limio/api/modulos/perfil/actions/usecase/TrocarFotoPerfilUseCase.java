package com.limio.api.modulos.perfil.actions.usecase;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

import com.limio.api.modulos.perfil.actions.helper.ImagemHelper;
import com.limio.api.modulos.perfil.actions.helper.ImagemHelper.ImagemProcessada;
import com.limio.api.modulos.perfil.actions.service.ArmazenamentoService;
import com.limio.api.modulos.perfil.eventos.FotoPerfilTrocadaEvent;
import com.limio.api.modulos.perfil.excecao.FotoInvalidaException;
import com.limio.api.modulos.perfil.midia.LinkTemporario;
import com.limio.api.modulos.perfil.midia.Midia;
import com.limio.api.modulos.perfil.midia.MidiaService;

/**
 * Envia a primeira foto de perfil ou troca a atual. A foto é do usuário (vale para os dois papéis),
 * nunca de quem vier no corpo: o id é sempre o do token.
 */
@Service
public class TrocarFotoPerfilUseCase {

    static final String PREFIXO_CHAVE = "fotos-perfil/";

    private final MidiaService midiaService;
    private final ArmazenamentoService armazenamentoService;
    private final ApplicationEventPublisher eventos;
    private final long tamanhoMaximoBytes;

    public TrocarFotoPerfilUseCase(MidiaService midiaService, ArmazenamentoService armazenamentoService,
            ApplicationEventPublisher eventos, @Value("${perfil.foto.tamanho-maximo}") DataSize tamanhoMaximo) {
        this.midiaService = midiaService;
        this.armazenamentoService = armazenamentoService;
        this.eventos = eventos;
        this.tamanhoMaximoBytes = tamanhoMaximo.toBytes();
    }

    @Transactional
    public LinkTemporario executar(UUID usuarioId, byte[] arquivo) {
        if (arquivo == null || arquivo.length == 0)
            throw FotoInvalidaException.vazia();
        if (arquivo.length > tamanhoMaximoBytes)
            throw FotoInvalidaException.grandeDemais(tamanhoMaximoBytes);
        ImagemProcessada imagem = ImagemHelper.processar(arquivo)
                .orElseThrow(FotoInvalidaException::formatoNaoAceito);

        midiaService.travarFotoPerfil(usuarioId);
        Optional<Midia> anterior = midiaService.buscarFotoPerfilAtiva(usuarioId);

        String chave = PREFIXO_CHAVE + usuarioId + "/" + UUID.randomUUID() + "." + imagem.extensao();
        armazenamentoService.enviar(chave, imagem.conteudo(), imagem.mime());
        // Publicado logo após o upload: se algo abaixo falhar, o rollback apaga o objeto novo.
        eventos.publishEvent(new FotoPerfilTrocadaEvent(chave, anterior.map(Midia::getUrlS3).orElse(null)));

        anterior.ifPresent(foto -> {
            foto.expurgar(Instant.now());
            midiaService.salvarAgora(foto);
        });
        midiaService.salvar(Midia.fotoPerfil(usuarioId, chave, imagem.mime(), imagem.largura(), imagem.altura(),
                imagem.conteudo().length));
        return armazenamentoService.gerarLink(chave);
    }
}
