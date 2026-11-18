package br.mil.eb.escala.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Aviso publicado pelo ADM (ex.: "Formatura sexta às 7h").
 * Fica visível no painel e no mapa entre dataInicio e dataFim.
 */
@Entity
@Table(name = "avisos")
@Getter
@Setter
@NoArgsConstructor
public class Aviso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(length = 1000)
    private String mensagem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrioridadeAviso prioridade = PrioridadeAviso.NORMAL;

    @Column(nullable = false)
    private LocalDate dataInicio;

    @Column(nullable = false)
    private LocalDate dataFim;

    private String autor;

    private LocalDateTime criadoEm;

    /** O aviso vale para essa data? */
    public boolean isVigenteEm(LocalDate data) {
        return !data.isBefore(dataInicio) && !data.isAfter(dataFim);
    }

    /** O aviso aparece em algum dia do período [inicio, fim]? */
    public boolean cruzaPeriodo(LocalDate inicio, LocalDate fim) {
        return !dataFim.isBefore(inicio) && !dataInicio.isAfter(fim);
    }
}
