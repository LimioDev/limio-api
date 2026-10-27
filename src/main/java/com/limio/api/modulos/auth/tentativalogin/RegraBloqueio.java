package com.limio.api.modulos.auth.tentativalogin;

import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

/**
 * Quantas falhas recentes buscar e como decidir, com elas, se o login está bloqueado. Quem monta é o
 * usecase, com a regra do helper; o {@link TentativaLoginService} só busca e aplica, sob lock.
 */
public record RegraBloqueio(int quantidade, Predicate<List<Instant>> bloqueado) {
}
