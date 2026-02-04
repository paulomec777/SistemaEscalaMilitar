package br.mil.eb.escala.service;

import br.mil.eb.escala.model.Feriado;
import br.mil.eb.escala.model.Militar;
import br.mil.eb.escala.model.MotivoInatividade;
import br.mil.eb.escala.model.Usuario;
import br.mil.eb.escala.repository.FeriadoRepository;
import br.mil.eb.escala.repository.MilitarRepository;
import br.mil.eb.escala.repository.UsuarioRepository;
import jakarta.transaction.Transactional; // Importante para segurança no banco
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled; 
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

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
        // Busca quem está apto, ordenando por MAIS FOLGA e DATA MAIS ANTIGA
        // Nota: Certifique-se de ter esse método no Repository ou use o padrão Sort
        List<Militar> todos = militarRepository.findByAtivoNaEscalaTrueAndEmServicoExternoFalseOrderByFolgaDescDataUltimoServicoAsc();
        
        // Regra: Quem tirou serviço ontem não pode tirar hoje (impedimento de dobra)
        LocalDate ontem = LocalDate.now().minusDays(1);
        
        return todos.stream()
                .filter(m -> m.getDataUltimoServico() == null || !m.getDataUltimoServico().equals(ontem))
                .toList();
    }

    // O "01" da fila (Mais folgado)
    public Militar getProximoPermanencia() {
        List<Militar> ordenados = getMilitaresOrdenadosParaEscala();
        return ordenados.isEmpty() ? null : ordenados.get(0);
    }
    
    // O "02" da fila (Imediatamente após o mais folgado)
    public Militar getProximoSubstituto() {
        List<Militar> ordenados = getMilitaresOrdenadosParaEscala();
        return ordenados.size() < 2 ? null : ordenados.get(1); 
    }

    public List<Militar> getTodosMilitaresParaDashboard() {
        return militarRepository.findAllByOrderByAtivoNaEscalaDescFolgaDesc();
    }

    // --- FUNCIONALIDADE NOVA: ALTERAR FOLGA MANUALMENTE ---
    
    @Transactional
    public void atualizarFolgaManual(Long idMilitar, int novaFolga) {
        Militar militar = findMilitarById(idMilitar);
        if (militar != null) {
            militar.setFolga(novaFolga);
            militarRepository.save(militar);
        }
    }

    // --- MOTOR DA ESCALA (Lógica de Fim de Semana Ajustada) ---
    
    // Roda todo dia à meia-noite
    @Scheduled(cron = "0 0 0 * * *", zone = "America/Sao_Paulo")
    @Transactional // Garante que ou salva tudo ou não salva nada
    public void avancarDiaDaEscala() {
        LocalDate hoje = LocalDate.now();
        System.out.println("--- RODANDO MOTOR DA ESCALA: " + hoje + " ---");

        boolean ehDiaDeServico = isDiaUtil(hoje); // Verifica se é Seg-Sex (sem feriado)

        // Se for dia útil, pegamos o próximo para escalar (zerar a folga).
        // Se for fim de semana, 'proximo' fica null, então ninguém zera a folga.
        Militar permanencia = ehDiaDeServico ? getProximoPermanencia() : null;
        
        if (!ehDiaDeServico) {
            System.out.println("Hoje é Fim de Semana ou Feriado. A escala gira (folgas aumentam), mas ninguém é escalado.");
        } else {
            System.out.println("Dia Útil. Escalado para hoje: " + (permanencia != null ? permanencia.getNomeGuerra() : "Ninguém"));
        }

        List<Militar> todos = militarRepository.findAll();

        for (Militar m : todos) {
            
            // CASO 1: É o PERMANÊNCIA DO DIA (Só acontece em dia útil)
            if (permanencia != null && m.getId().equals(permanencia.getId())) {
                m.setFolga(0); // Zera a folga (vai pro final da fila)
                m.setDataUltimoServico(hoje);
                m.setEmServicoExterno(false);
            
            // CASO 2: Qualquer outro caso (Substituto, Fim de Semana, Feriado, Missão)
            // Todo mundo ganha +1 de folga para a fila andar
            } else {
                // Se está em serviço externo ou folga normal, ganha ponto
                m.setFolga(m.getFolga() + 1); 
                
                // Se for missão, atualizamos a data só para controle, mas ele continua ganhando folga
                if (m.isEmServicoExterno()) {
                    m.setDataUltimoServico(hoje); 
                }

                // Verifica se o afastamento acabou hoje e reativa automaticamente
                if (!m.isAtivoNaEscala() && m.getDataFimAfastamento() != null && 
                   (m.getDataFimAfastamento().isEqual(hoje) || m.getDataFimAfastamento().isBefore(hoje))) {
                        reativarMilitar(m.getId());
                }
            }
        }
        militarRepository.saveAll(todos);
    }
    
    // --- LÓGICA DE CRUD E UTILITÁRIOS ---

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
    
    // Método auxiliar para checar se deve ter expediente
    private boolean isDiaUtil(LocalDate data) {
        DayOfWeek dia = data.getDayOfWeek();
        if (dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY) return false;
        if (feriadoRepository.existsByData(data)) return false;
        return true;
    }
    
    public void marcarServicoExterno(Long id) {
        Militar m = findMilitarById(id);
        if (m != null) { m.setEmServicoExterno(true); militarRepository.save(m); }
    }

    public void limparStatusExterno(Long id) {
        Militar m = findMilitarById(id);
        if (m != null) { m.setEmServicoExterno(false); militarRepository.save(m); }
    }
    
    public void processarTroca(Long idSai, Long idEntra) {
        Militar sai = findMilitarById(idSai);
        Militar entra = findMilitarById(idEntra);

        if (sai != null && entra != null) {
            sai.setFolga(0);
            sai.setDataUltimoServico(LocalDate.now());
            militarRepository.save(sai);
        }
    }
    
    // --- LÓGICA DE FERIADOS E USUÁRIOS (MANTIDA IGUAL) ---
    
    public List<Feriado> getTodosFeriados() { return feriadoRepository.findAllByOrderByDataAsc(); }
    public void salvarFeriado(Feriado f) { feriadoRepository.save(f); }
    public void deletarFeriado(Long id) { feriadoRepository.deleteById(id); }
    public List<Usuario> getTodosUsuarios() { return usuarioRepository.findAll(); }
    public void salvarNovoUsuario(Usuario u) {
        u.setSenha(passwordEncoder.encode(u.getSenha()));
        usuarioRepository.save(u);
    }
    public void deletarUsuario(Long id) {
        if (id != 1L) usuarioRepository.deleteById(id);
    }
}