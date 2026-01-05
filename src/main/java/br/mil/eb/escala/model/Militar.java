package br.mil.eb.escala.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "militares")
@Data
@NoArgsConstructor
public class Militar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nomeGuerra;
    private String graduacao;

    private int folga;
    private LocalDate dataUltimoServico;
    private boolean emServicoExterno;
    private boolean ativoNaEscala;

    private LocalDate dataInicioAfastamento;
    private LocalDate dataFimAfastamento;
    
    @Enumerated(EnumType.STRING)
    private MotivoInatividade motivoAfastamento;
}