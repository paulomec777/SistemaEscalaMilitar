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
    private String graduacao; 

    // Número de folgas acumuladas (contadas de segunda a sexta-feira).
    // O Administrador poderá modificar este valor manualmente no sistema.
    private int folga; 

    // Mantém o registo da data do último serviço tirado.
    // A lógica do sistema apenas atualizará esta data quando o militar for o Permanência.
    private LocalDate dataUltimoServico;

    @Column(columnDefinition = "boolean default false")
    private boolean emServicoExterno; 

    // Necessário para calcular o descanso exato de 48h após um serviço externo.
    private LocalDate dataFimServicoExterno; 

    @Column(columnDefinition = "boolean default true")
    private boolean ativoNaEscala; 

    private LocalDate dataInicioAfastamento;
    private LocalDate dataFimAfastamento;
    
    @Enumerated(EnumType.STRING)
    private MotivoInatividade motivoAfastamento;
    
    // Método que verifica se o militar está apto para ser escalado numa determinada data
    public boolean estaAptoParaServico(LocalDate dataServico) {
        if (!ativoNaEscala) return false;
        if (emServicoExterno) return false; // Bloqueia se estiver atualmente em missão
        
        // 1. Verifica se está com afastamento (férias, dispensa, etc.) na data do serviço
        if (dataInicioAfastamento != null && dataFimAfastamento != null) {
            if (!dataServico.isBefore(dataInicioAfastamento) && !dataServico.isAfter(dataFimAfastamento)) {
                return false;
            }
        }

        // 2. Regra das 48 horas (Serviço Externo)
        // Se concluiu um serviço externo, tem de cumprir 2 dias (48h) de descanso
        if (dataFimServicoExterno != null) {
            LocalDate dataLiberacao = dataFimServicoExterno.plusDays(2);
            if (dataServico.isBefore(dataLiberacao)) {
                return false; // Bloqueado: Ainda se encontra no período de 48h de descanso
            }
        }
        
        return true;
    }
}