package com.limio.api.modulos.financeiro.assinaturagold.cobranca.documentofiscal;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;

/** Camada técnica de persistência de {@link DocumentoFiscal} — CRUD genérico (herdado). */
@Component
public class DocumentoFiscalService extends BaseService<DocumentoFiscal, UUID, DocumentoFiscalRepository> {

    public DocumentoFiscalService(DocumentoFiscalRepository repository) {
        super(repository);
    }
}
