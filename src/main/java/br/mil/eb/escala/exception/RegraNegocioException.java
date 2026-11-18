package br.mil.eb.escala.exception;

/**
 * Erro de regra do sistema (ex.: data final antes da inicial).
 * A mensagem é mostrada ao usuário na tela.
 */
public class RegraNegocioException extends RuntimeException {
    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
