package br.mil.eb.escala.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Missão de um dia com um ou mais militares escalados.
 * Quem está em missão no dia não entra na permanência desse dia.
 * "Última hora" marca missões urgentes, que aparecem em alerta no painel.
 */
@Entity
@Table(name = "missoes")
@Getter
@Setter
@NoArgsConstructor
public class Missao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(length = 1000)
    private String descricao;

    @Column(name = "local_missao", length = 120)
    private String local;

    @Column(nullable = false)
    private LocalDate data;

    private LocalTime horario;

    @Column(nullable = false)
    private boolean ultimaHora = false;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "missao_militares",
            joinColumns = @JoinColumn(name = "missao_id"),
            inverseJoinColumns = @JoinColumn(name = "militar_id"))
    @OrderBy("nomeGuerra ASC")
    private Set<Militar> militares = new LinkedHashSet<>();

    private String autor;

    private LocalDateTime criadoEm;

    public boolean isConcluida() {
        return data.isBefore(LocalDate.now());
    }

    public boolean isHoje() {
        return data.isEqual(LocalDate.now());
    }

    /** "Sgt Silva, Cb Souza" — para listas e tabelas. */
    public String getNomesMilitares() {
        return militares.stream()
                .map(m -> m.getGraduacao() + " " + m.getNomeGuerra())
                .collect(Collectors.joining(", "));
    }
}
