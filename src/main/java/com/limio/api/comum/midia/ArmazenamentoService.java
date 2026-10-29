package com.limio.api.comum.midia;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.limio.api.comum.config.StorageProperties;
import com.limio.api.comum.midia.excecao.ArmazenamentoIndisponivelException;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Integração com o bucket S3-compatível ({@code comum/config/StorageConfig}, Railway hoje). O bucket
 * é privado: leitura só por link temporário assinado.
 *
 * Sem {@code STORAGE_BUCKET} (dev local, testes) os beans de S3 não existem: enviar e gerar link
 * respondem 503 {@code ARMAZENAMENTO_INDISPONIVEL}, e o resto da API sobe normalmente.
 */
@Component
public class ArmazenamentoService {

    private static final Logger log = LoggerFactory.getLogger(ArmazenamentoService.class);

    private final ObjectProvider<S3Client> s3Client;
    private final ObjectProvider<S3Presigner> s3Presigner;
    private final ObjectProvider<StorageProperties> storageProperties;
    private final Duration validadeLink;

    public ArmazenamentoService(ObjectProvider<S3Client> s3Client, ObjectProvider<S3Presigner> s3Presigner,
            ObjectProvider<StorageProperties> storageProperties,
            @Value("${midia.validade-link}") Duration validadeLink) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.storageProperties = storageProperties;
        this.validadeLink = validadeLink;
    }

    public void enviar(String chave, byte[] conteudo, String mime) {
        S3Client cliente = s3Client.getIfAvailable();
        StorageProperties props = storageProperties.getIfAvailable();
        if (cliente == null || props == null)
            throw new ArmazenamentoIndisponivelException();
        try {
            // Content-Length sai do próprio RequestBody.fromBytes.
            cliente.putObject(r -> r.bucket(props.bucket()).key(chave).contentType(mime),
                    RequestBody.fromBytes(conteudo));
        } catch (SdkException e) {
            log.warn("falha ao enviar objeto ao storage ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            throw new ArmazenamentoIndisponivelException();
        }
    }

    public LinkTemporario gerarLink(String chave) {
        S3Presigner presigner = s3Presigner.getIfAvailable();
        StorageProperties props = storageProperties.getIfAvailable();
        if (presigner == null || props == null)
            throw new ArmazenamentoIndisponivelException();
        try {
            var assinada = presigner.presignGetObject(r -> r.signatureDuration(validadeLink)
                    .getObjectRequest(g -> g.bucket(props.bucket()).key(chave)));
            return new LinkTemporario(assinada.url().toString(), assinada.expiration());
        } catch (SdkException e) {
            log.warn("falha ao assinar link do storage ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            throw new ArmazenamentoIndisponivelException();
        }
    }

    /**
     * Apaga o objeto. Falha só é logada: quem chama é limpeza pós-commit, e um objeto órfão no bucket
     * não afeta o usuário (a mídia já está expurgada no banco).
     */
    public void remover(String chave) {
        S3Client cliente = s3Client.getIfAvailable();
        StorageProperties props = storageProperties.getIfAvailable();
        if (cliente == null || props == null) {
            log.warn("storage não configurado; objeto {} não foi apagado", chave);
            return;
        }
        try {
            cliente.deleteObject(r -> r.bucket(props.bucket()).key(chave));
        } catch (SdkException e) {
            log.warn("falha ao apagar objeto {} do storage ({}): {}", chave, e.getClass().getSimpleName(),
                    e.getMessage());
        }
    }
}
