package br.mil.eb.escala.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "feriados")
@Data
@NoArgsConstructor
public class Feriado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate data; // Ex: 2025-12-25
    private String descricao; // Ex: Natal
}