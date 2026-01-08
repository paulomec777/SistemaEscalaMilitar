package br.mil.eb.escala.service;

import br.mil.eb.escala.model.Feriado;
import br.mil.eb.escala.model.Militar;
import br.mil.eb.escala.model.MotivoInatividade;
import br.mil.eb.escala.model.Usuario;
import br.mil.eb.escala.repository.FeriadoRepository;
import br.mil.eb.escala.repository.MilitarRepository;
import br.mil.eb.escala.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled; // <--- IMPORT NOVO AQUI
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class EscalaService {

    @Autowired
    private MilitarRepository militarRepository;
    
    @Autowired
    private FeriadoRepository feriadoRepository;
    
    @Autowired
    private UsuarioRepository usuarioRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    // --- LÓGICA DE ESCALA (DASHBOARD) ---
    
    public List<Militar> getMilitaresOrdenadosParaEscala() {
        List<Militar> todos = militarRepository.findByAtivoNaEscalaTrueAndEmServicoExternoFalseOrderByFolgaDesc();
        LocalDate ontem = LocalDate.now().minusDays(1);
        
        return todos.stream()
                .filter(m -> m.getDataUltimoServico() == null || !m.getDataUltimoServico().equals(ontem))
                .toList();
    }

    public Militar getProximoDaEscala() {
        List<Militar> ordenados = getMilitaresOrdenadosParaEscala();
        return ordenados.isEmpty() ? null : ordenados.get(0);
    }
    
    public Militar getProximoSubstituto() {
        List<Militar> ordenados = getMilitaresOrdenadosParaEscala();
        return ordenados.size() < 2 ? null : ordenados.get(1); 
    }

    public List<Militar> getTodosMilitaresParaDashboard() {
        return militarRepository.findAllByOrderByAtivoNaEscalaDescFolgaDesc();
    }

    // --- LÓGICA DE MILITAR (CRUD) ---

    public List<Militar> getMilitaresAtivos() {
        return militarRepository.findByAtivoNaEscalaTrue();
    }
    
    public Militar findMilitarById(Long id) {
        return militarRepository.findById(id).orElse(null); 
    }

    public void salvarNovoMilitar(Militar militar) {
        militar.setAtivoNaEscala(true);
        militar.setEmServicoExterno(false); 
        militar.setDataUltimoServico(LocalDate.now()); 
        militarRepository.save(militar);
    }
    
    public void editarMilitar(Militar dados) {
        Militar original = findMilitarById(dados.getId());
        if (original != null) {
            original.setGraduacao(dados.getGraduacao());
            original.setNomeGuerra(dados.getNomeGuerra());
            militarRepository.save(original);
        }
    }
    
    public void deletarMilitar(Long id) {
        if (militarRepository.existsById(id)) {
            militarRepository.deleteById(id);
        }
    }

    // --- LÓGICA DE AFASTAMENTO ---
    
    public void afastarMilitar(Long id, MotivoInatividade motivo, LocalDate inicio, LocalDate fim) {
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
        return militarRepository.findAll().stream()
                .filter(m -> !m.isAtivoNaEscala()).toList();
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

    // --- MOTOR DA ESCALA (AVANÇAR DIA) ---
    
    // --- AQUI ESTÁ A CORREÇÃO PRINCIPAL ---
    // Roda todo dia à meia-noite (00:00:00) no horário de Brasília
    @Scheduled(cron = "0 0 0 * * *", zone = "America/Sao_Paulo")
    public void avancarDiaDaEscala() {
        System.out.println("--- EXECUTANDO ESCALA AUTOMÁTICA: " + LocalDate.now() + " ---");
        
        LocalDate hoje = LocalDate.now();

        if (!isDiaUtil(hoje)) {
            System.out.println("Escala não rodou pois hoje não é dia útil (Sábado, Domingo ou Feriado).");
            return;
        }

        Militar proximo = getProximoDaEscala();
        List<Militar> todos = militarRepository.findAll();

        for (Militar m : todos) {
            
            // CASO 1: Tirou Serviço Interno (Permanência)
            if (proximo != null && m.getId().equals(proximo.getId())) {
                m.setFolga(0); // Zera a folga
                m.setDataUltimoServico(hoje);
                m.setEmServicoExterno(false);
            
            // CASO 2: Tirou Serviço Externo (Marcado pelo ADM)
            } else if (m.isEmServicoExterno()) {
                m.setFolga(m.getFolga() + 1); 
                m.setDataUltimoServico(hoje);
                m.setEmServicoExterno(false); 

            // CASO 3: Estava de Folga
            } else {
                m.setFolga(m.getFolga() + 1);
                m.setEmServicoExterno(false); 
                
                // Reativação automática
                if (!m.isAtivoNaEscala() && m.getDataFimAfastamento() != null && 
                   (m.getDataFimAfastamento().isEqual(hoje) || m.getDataFimAfastamento().isBefore(hoje))) {
                        reativarMilitar(m.getId());
                }
            }
        }
        militarRepository.saveAll(todos);
    }
    
    private boolean isDiaUtil(LocalDate data) {
        DayOfWeek dia = data.getDayOfWeek();
        if (dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY) return false;
        if (feriadoRepository.existsByData(data)) return false;
        return true;
    }
    
    // --- LÓGICA DE SERVIÇO EXTERNO ---
    
    public void marcarServicoExterno(Long id) {
        Militar m = findMilitarById(id);
        if (m != null) { m.setEmServicoExterno(true); militarRepository.save(m); }
    }

    public void limparStatusExterno(Long id) {
        Militar m = findMilitarById(id);
        if (m != null) { m.setEmServicoExterno(false); militarRepository.save(m); }
    }
    
    // --- LÓGICA DE TROCA ---
    
    public void processarTroca(Long idSai, Long idEntra) {
        Militar sai = findMilitarById(idSai);
        Militar entra = findMilitarById(idEntra);

        if (sai != null && entra != null) {
            // Quem paga (sai) tem a folga zerada
            sai.setFolga(0);
            sai.setDataUltimoServico(LocalDate.now());
            militarRepository.save(sai);
            // Quem recebe (entra) mantém a folga (ou aumenta, dependendo da sua regra de negócio)
        }
    }
    
    // --- LÓGICA DE FERIADOS ---
    
    public List<Feriado> getTodosFeriados() {
        return feriadoRepository.findAllByOrderByDataAsc();
    }
    
    public void salvarFeriado(Feriado f) {
        feriadoRepository.save(f);
    }
    
    public void deletarFeriado(Long id) {
        feriadoRepository.deleteById(id);
    }

    // --- LÓGICA DE USUÁRIOS (LOGIN) ---

    public List<Usuario> getTodosUsuarios() {
        return usuarioRepository.findAll();
    }
    
    public void salvarNovoUsuario(Usuario u) {
        u.setSenha(passwordEncoder.encode(u.getSenha()));
        usuarioRepository.save(u);
    }

    public void deletarUsuario(Long id) {
        if (id != 1L) { 
            usuarioRepository.deleteById(id);
        }
    }
}