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

    @Column(nullable = false)
    private String nomeGuerra;
    
    @Column(nullable = false)
    private String graduacao; // Ex: Soldado, Cabo, Sargento

    // DICA: Use este campo para ordenar a fila da escala (quem é mais antigo)
    // Se não usar, a escala vai seguir a ordem de cadastro (ID)
    // private Integer antiguidade; 

    private int folga; // Quantos dias/serviços de folga ele tem acumulado

    private LocalDate dataUltimoServico;

    @Column(columnDefinition = "boolean default false")
    private boolean emServicoExterno; // Missão, etc.

    @Column(columnDefinition = "boolean default true")
    private boolean ativoNaEscala; // Se está pronto para o serviço

    private LocalDate dataInicioAfastamento;
    private LocalDate dataFimAfastamento;
    
    @Enumerated(EnumType.STRING)
    private MotivoInatividade motivoAfastamento;
    
    // Método auxiliar para saber se o militar pode tirar serviço hoje
    public boolean estaAptoParaServico(LocalDate dataServico) {
        if (!ativoNaEscala) return false;
        
        // Verifica se está afastado na data do serviço
        if (dataInicioAfastamento != null && dataFimAfastamento != null) {
            return dataServico.isBefore(dataInicioAfastamento) || dataServico.isAfter(dataFimAfastamento);
        }
        
        return true;
    }
}