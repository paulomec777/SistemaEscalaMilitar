package br.mil.eb.escala.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "feriados", indexes = {
    @Index(name = "idx_feriado_data", columnList = "data") 
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Feriado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
     //Ex: 2026-04-16
    private LocalDate data;

    @Column(nullable = false, length = 100)
    //Ex: DIA DA BANDEIRA
    private String descricao; 

    //Método utilitário para formatar a exibição no sistema/HTML
    public String getInformativo() {
        return String.format("%td/%tm - %s", data, data, descricao);
    }
}
