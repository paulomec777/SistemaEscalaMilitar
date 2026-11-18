package br.mil.eb.escala.exception;

/** Conferência de textos digitados nas telas (o limite do HTML pode ser burlado). */
public final class Textos {

    private Textos() {
    }

    /** Campo obrigatório: não pode ser vazio nem passar do tamanho. Devolve o texto sem espaços nas pontas. */
    public static String obrigatorio(String valor, String campo, int maximo) {
        if (valor == null || valor.isBlank()) throw new RegraNegocioException("Informe " + campo + ".");
        return limite(valor, campo, maximo);
    }

    /** Campo opcional: vazio vira null; se tiver texto, não pode passar do tamanho. */
    public static String opcional(String valor, String campo, int maximo) {
        if (valor == null || valor.isBlank()) return null;
        return limite(valor, campo, maximo);
    }

    private static String limite(String valor, String campo, int maximo) {
        String limpo = valor.trim();
        if (limpo.length() > maximo) {
            throw new RegraNegocioException("O campo " + campo + " aceita no máximo " + maximo + " caracteres.");
        }
        return limpo;
    }
}
