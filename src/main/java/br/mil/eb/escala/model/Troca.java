package br.mil.eb.escala.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Registro de uma troca de serviço feita pelo ADM (só o ADM vê).
 * Guarda como estavam os dois militares ANTES da troca, para poder desfazer
 * com exatidão (o ADM pode trocar quantas vezes precisar no mesmo dia).
 */
@Entity
@Table(name = "trocas")
@Getter
@Setter
@NoArgsConstructor
public class Troca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoTroca tipo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sai_id")
    private Militar sai;

    @ManyToOne(optional = false)
    @JoinColumn(name = "entra_id")
    private Militar entra;

    // Como estava quem SAIU antes da troca
    private int saiFolgaAntes;
    private LocalDate saiDataUltimoServicoAntes;
    private boolean saiPagouServicoAntes;

    // Como estava quem ENTROU antes da troca
    private int entraFolgaAntes;
    private LocalDate entraDataUltimoServicoAntes;
    private boolean entraServicoPagoAntes;

    @Column(columnDefinition = "boolean default false")
    private boolean desfeita = false;

    private String autor;

    private LocalDateTime criadoEm;

    /** Só dá para desfazer no próprio dia (depois da meia-noite as folgas já viraram). */
    public boolean isPodeDesfazer() {
        return !desfeita && data.isEqual(LocalDate.now());
    }
}
