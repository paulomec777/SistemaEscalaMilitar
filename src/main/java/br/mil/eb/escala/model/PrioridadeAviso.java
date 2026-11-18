package br.mil.eb.escala.model;

/** Nível de destaque de um aviso no painel e no mapa. */
public enum PrioridadeAviso {
    NORMAL("Normal"),
    IMPORTANTE("Importante"),
    URGENTE("Urgente");

    private final String rotulo;

    PrioridadeAviso(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
