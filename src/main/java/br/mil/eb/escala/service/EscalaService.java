package br.mil.eb.escala.service;

import br.mil.eb.escala.model.Feriado;
import br.mil.eb.escala.model.Militar;
import br.mil.eb.escala.model.Usuario;
import br.mil.eb.escala.repository.FeriadoRepository;
import br.mil.eb.escala.repository.MilitarRepository;
import br.mil.eb.escala.repository.UsuarioRepository;
import jakarta.transaction.Transactional; 
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled; 
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit; // Importação adicionada para calcular as folgas
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EscalaService {

    @Autowired private MilitarRepository militarRepository;
    @Autowired private FeriadoRepository feriadoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    /**
     * Retorna a lista de militares APTOS para o serviço do dia,
     * ordenados do MAIS FOLGADO (índice 0) para o MENOS FOLGADO.
     */
    public List<Militar> getMilitaresOrdenadosParaEscala() {
        LocalDate hoje = LocalDate.now();
        LocalDate ontem = hoje.minusDays(1);
        List<Militar> todos = militarRepository.findAll();
        
        return todos.stream()
                .filter(m -> m.estaAptoParaServico(hoje)) // Filtra tirando os doentes/afastados/missão
                .filter(m -> m.getDataUltimoServico() == null || !m.getDataUltimoServico().equals(ontem)) 
                .sorted(Comparator.comparing(Militar::getFolga).reversed() // Maior folga primeiro, sem checagem de null para int
                        .thenComparing(Militar::getDataUltimoServico, Comparator.nullsFirst(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    // O mais folgado da fila de aptos tira o serviço hoje
    public Militar getProximoPermanencia() {
        List<Militar> ordenados = getMilitaresOrdenadosParaEscala(); 
        return ordenados.isEmpty() ? null : ordenados.get(0);
    }
    
    // O reserva imediato (segundo mais folgado apto) assume se o titular adoecer
    public Militar getProximoSubstituto() {
        List<Militar> ordenados = getMilitaresOrdenadosParaEscala();
        return ordenados.size() < 2 ? null : ordenados.get(1); 
    }

    public List<Militar> getTodosMilitaresParaDashboard() {
        return militarRepository.findAll().stream()
                .sorted(Comparator.comparing(Militar::isAtivoNaEscala).reversed()
                        .thenComparing(Militar::getFolga).reversed()) // Sem checagem de null para int
                .collect(Collectors.toList());
    }

    @Transactional
    public void atualizarFolgaManual(Long idMilitar, int novaFolga) {
        Militar militar = findMilitarById(idMilitar);
        if (militar != null) {
            militar.setFolga(novaFolga);
            militarRepository.save(militar);
        }
    }

    @Transactional
    public void atualizarDataUltimoServicoManual(Long idMilitar, LocalDate novaData) {
        Militar militar = findMilitarById(idMilitar);
        if (militar != null) {
            militar.setDataUltimoServico(novaData);
            militarRepository.save(militar);
        }
    }

    // --- MOTOR DA ESCALA COM CONGELAMENTO ---
    
    @Scheduled(cron = "0 0 0 * * *", zone = "America/Sao_Paulo")
    @Transactional 
    public void avancarDiaDaEscala() {
        LocalDate hoje = LocalDate.now();
        boolean ehDiaDeServico = isDiaUtil(hoje); 
        
        // Elege o militar mais folgado APTOS do dia
        Militar permanencia = ehDiaDeServico ? getProximoPermanencia() : null;
        
        List<Militar> todos = militarRepository.findAll();

        for (Militar m : todos) {
            // Reativar afastamentos/doenças vencidas automaticamente
            if (!m.isAtivoNaEscala() && m.getDataFimAfastamento() != null && 
               (m.getDataFimAfastamento().isEqual(hoje) || m.getDataFimAfastamento().isBefore(hoje))) {
                    m.setAtivoNaEscala(true);
                    m.setMotivoAfastamento(null);
                    m.setDataInicioAfastamento(null);
                    m.setDataFimAfastamento(null);
            }

            if (ehDiaDeServico) {
                // Se ele for o eleito do dia, o serviço dele é computado e a folga zera
                if (permanencia != null && m.getId().equals(permanencia.getId())) {
                    m.setFolga(0); 
                    m.setDataUltimoServico(hoje); 
                } else {
                    // CONGELAMENTO AUTOMÁTICO:
                    // Se ele NÃO for o permanência, ganha +1 de folga normalmente.
                    m.setFolga(m.getFolga() + 1); 
                }
            } else {
                // Finais de semana e feriados: todos ganham folga e ninguém tira serviço
                m.setFolga(m.getFolga() + 1);
            }
        }
        militarRepository.saveAll(todos); 
    }
    
    // --- LÓGICA DE CRUD E UTILITÁRIOS ---

    public List<Militar> getMilitaresAtivos() {
        return militarRepository.findAll().stream().filter(Militar::isAtivoNaEscala).collect(Collectors.toList());
    }
    
    public Militar findMilitarById(Long id) {
        return militarRepository.findById(id).orElse(null); 
    }

    // =========================================================
    // MÉTODO ATUALIZADO: INSERÇÃO MANUAL HÍBRIDA
    // =========================================================
    public void salvarNovoMilitar(Militar militar) {
        militar.setAtivoNaEscala(true);
        
        // Fallback: Se a data não vier do HTML, assume a data de hoje para não quebrar o banco
        if (militar.getDataUltimoServico() == null) {
            militar.setDataUltimoServico(LocalDate.now());
        }

        // Calcula a folga inicial do novato com base na data que a administração informou
        LocalDate hoje = LocalDate.now();
        LocalDate dataUltimoSv = militar.getDataUltimoServico();
        int folgaCalculada = 0;

        // Se a data informada for no passado, calcula a diferença em dias
        if (dataUltimoSv.isBefore(hoje)) {
            folgaCalculada = (int) ChronoUnit.DAYS.between(dataUltimoSv, hoje);
        }

        militar.setFolga(folgaCalculada); 
        militarRepository.save(militar);
    }
    // =========================================================
    
    public void editarMilitar(Militar dados) {
        Militar original = findMilitarById(dados.getId());
        if (original != null) {
            original.setGraduacao(dados.getGraduacao());
            original.setNomeGuerra(dados.getNomeGuerra());
            militarRepository.save(original);
        }
    }
    
    public void deletarMilitar(Long id) {
        if (militarRepository.existsById(id)) militarRepository.deleteById(id);
    }

    public void afastarMilitar(Long id, String motivo, LocalDate inicio, LocalDate fim) {
        Militar militar = findMilitarById(id);
        if (militar != null) {
            militar.setAtivoNaEscala(false); 
            militar.setMotivoAfastamento(motivo);
            militar.setDataInicioAfastamento(inicio);
            militar.setDataFimAfastamento(fim);
            militarRepository.save(militar);
        }
    }

    public List<Militar> getMilitaresInativos() {
        return militarRepository.findAll().stream().filter(m -> !m.isAtivoNaEscala()).toList();
    }
    
    public void reativarMilitar(Long id) {
        Militar militar = findMilitarById(id);
        if (militar != null && !militar.isAtivoNaEscala()) {
            militar.setAtivoNaEscala(true);
            militar.setMotivoAfastamento(null);
            militar.setDataInicioAfastamento(null);
            militar.setDataFimAfastamento(null);
            militarRepository.save(militar);
        }
    }
    
    private boolean isDiaUtil(LocalDate data) {
        DayOfWeek dia = data.getDayOfWeek();
        if (dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY) return false;
        return !feriadoRepository.existsByData(data);
    }
    
    public void agendarServicoExterno(Long id, LocalDate inicio, LocalDate fim) {
        Militar m = findMilitarById(id);
        if (m != null) { 
            m.setDataInicioServicoExterno(inicio);
            m.setDataFimServicoExterno(fim); 
            militarRepository.save(m); 
        }
    }

    public void cancelarServicoExterno(Long id) {
        Militar m = findMilitarById(id);
        if (m != null) { 
            m.setDataInicioServicoExterno(null);
            m.setDataFimServicoExterno(null); 
            militarRepository.save(m); 
        }
    }
    
    public void processarTroca(Long idSai, Long idEntra) {
        Militar sai = findMilitarById(idSai);
        Militar entra = findMilitarById(idEntra);
        if (sai != null && entra != null) {
            entra.setFolga(0);
            entra.setDataUltimoServico(LocalDate.now());
            militarRepository.save(entra);
        }
    }
    
    public List<Feriado> getTodosFeriados() {
        return feriadoRepository.findAll();
    }

    // --- MÉTODOS DE USUÁRIO CENTRALIZADOS ---

    public List<Usuario> getTodosUsuarios() {
        return usuarioRepository.findAll();
    }

    @Transactional
    public void salvarNovoUsuario(Usuario usuario) {
        if (usuario.getSenha() != null && !usuario.getSenha().isEmpty()) {
            usuario.setSenha(passwordEncoder.encode(usuario.getSenha()));
        }
        usuarioRepository.save(usuario);
    }

    @Transactional
    public void deletarUsuario(Long id) {
        if (usuarioRepository.existsById(id)) {
            usuarioRepository.deleteById(id);
        }
    }
}
