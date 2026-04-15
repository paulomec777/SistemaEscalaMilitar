package br.mil.eb.escala.model;

public enum MotivoInatividade {
    FERIAS("Férias"),
    ATESTADO("Atestado Médico"),
    MISSAO("Missão"),
    DISPENSADO("Dispensado"), 
    OUTRO("Outro");
    
    private String descricao;
    
    MotivoInatividade(String descricao) {
        this.descricao = descricao;
    }
    
    public String getDescricao() {
        return descricao;
    }
}