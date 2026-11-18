package br.mil.eb.escala.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Militar colocado pelo ADM (arrastando no mapa) como titular ou reserva de um dia futuro.
 * Nesse dia ele tem prioridade sobre a fila automática; os outros dias se recalculam.
 */
@Entity
@Table(name = "escala_fixada",
       uniqueConstraints = @UniqueConstraint(name = "uk_escala_fixada_dia_papel", columnNames = {"data", "papel"}))
@Getter
@Setter
@NoArgsConstructor
public class EscalaFixada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PapelEscala papel;

    @ManyToOne(optional = false)
    @JoinColumn(name = "militar_id")
    private Militar militar;

    private String autor;

    private LocalDateTime criadoEm;
}
