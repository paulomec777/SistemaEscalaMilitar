package br.mil.eb.escala.service;

import br.mil.eb.escala.model.Feriado;
import br.mil.eb.escala.model.Militar;
import br.mil.eb.escala.model.MotivoInatividade;
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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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
        LocalDate hoje = LocalDate.now();
        LocalDate ontem = hoje.minusDays(1);
        
        // Busca todos e aplica a ordenação e os filtros complexos da classe Militar
        List<Militar> todos = militarRepository.findAll();
        
        return todos.stream()
                .filter(m -> m.estaAptoParaServico(hoje)) // Verifica Ativo, Missão, Férias e o Descanso de 48h
                .filter(m -> m.getDataUltimoServico() == null || !m.getDataUltimoServico().equals(ontem)) // Impede dobra de serviço
                // Ordena primeiro por quem tem MAIS folga, e em caso de empate, pela DATA MAIS ANTIGA
                .sorted(Comparator.comparing(Militar::getFolga).reversed()
                        .thenComparing(Militar::getDataUltimoServico, Comparator.nullsFirst(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    // O "01" da fila (Mais folgado = Permanência)
    public Militar getProximoPermanencia() {
        List<Militar> ordenados = getMilitaresOrdenadosParaEscala();
        return ordenados.isEmpty() ? null : ordenados.get(0);
    }
    
    // O "02" da fila (Imediatamente após o mais folgado = Substituto)
    public Militar getProximoSubstituto() {
        List<Militar> ordenados = getMilitaresOrdenadosParaEscala();
        return ordenados.size() < 2 ? null : ordenados.get(1); 
    }

    public List<Militar> getTodosMilitaresParaDashboard() {
        // Mostra quem está ativo primeiro e ordena pelas folgas no painel
        return militarRepository.findAll().stream()
                .sorted(Comparator.comparing(Militar::isAtivoNaEscala).reversed()
                        .thenComparing(Militar::getFolga).reversed())
                .collect(Collectors.toList());
    }

    // --- FUNCIONALIDADE: ALTERAR FOLGA MANUALMENTE ---
    
    @Transactional
    public void atualizarFolgaManual(Long idMilitar, int novaFolga) {
        Militar militar = findMilitarById(idMilitar);
        if (militar != null) {
            militar.setFolga(novaFolga);
            militarRepository.save(militar);
        }
    }

    // --- MOTOR DA ESCALA (Lógica de Dias Úteis e Fim de Semana) ---
    
    // Roda todo dia à meia-noite
    @Scheduled(cron = "0 0 0 * * *", zone = "America/Sao_Paulo")
    @Transactional 
    public void avancarDiaDaEscala() {
        LocalDate hoje = LocalDate.now();
        System.out.println("--- RODANDO MOTOR DA ESCALA: " + hoje + " ---");

        boolean ehDiaDeServico = isDiaUtil(hoje); // Verifica se é Seg-Sex (sem feriado)

        // Se for dia útil, pegamos o Permanência para zerar a folga.
        Militar permanencia = ehDiaDeServico ? getProximoPermanencia() : null;
        
        if (!ehDiaDeServico) {
            System.out.println("Fim de Semana/Feriado: Ninguém é escalado e as folgas NÃO aumentam.");
        } else {
            System.out.println("Dia Útil. Escalado: " + (permanencia != null ? permanencia.getNomeGuerra() : "Ninguém"));
        }

        List<Militar> todos = militarRepository.findAll();

        for (Militar m : todos) {
            
            // Verifica se o afastamento (férias/dispensa) acabou hoje e reativa na escala automaticamente
            if (!m.isAtivoNaEscala() && m.getDataFimAfastamento() != null && 
               (m.getDataFimAfastamento().isEqual(hoje) || m.getDataFimAfastamento().isBefore(hoje))) {
                    m.setAtivoNaEscala(true);
                    m.setMotivoAfastamento(null);
                    m.setDataInicioAfastamento(null);
                    m.setDataFimAfastamento(null);
            }

            // A CONTAGEM DAS FOLGAS SÓ ACONTECE EM DIAS ÚTEIS (Seg a Sex)
            if (ehDiaDeServico) {
                // CASO 1: É o PERMANÊNCIA DO DIA
                if (permanencia != null && m.getId().equals(permanencia.getId())) {
                    m.setFolga(0); // Zera a folga (vai pro final da fila de amanhã)
                    m.setDataUltimoServico(hoje); // Crava a data do último serviço
                    m.setEmServicoExterno(false);
                } 
                // CASO 2: Qualquer outro militar (Substituto ou quem está em casa)
                else {
                    // Ganha +1 de folga porque não tirou serviço hoje
                    m.setFolga(m.getFolga() + 1); 
                    
                    // Se estiver em missão externa, também atualizamos a data do último serviço
                    // para não ficar como se estivesse devendo serviço interno há meses
                    if (m.isEmServicoExterno()) {
                        m.setDataUltimoServico(hoje); 
                    }
                }
            }
        }
        
        militarRepository.saveAll(todos); // Salva todas as alterações no banco de uma vez só
    }
    
    // --- LÓGICA DE CRUD E UTILITÁRIOS ---

    public List<Militar> getMilitaresAtivos() {
        return militarRepository.findAll().stream().filter(Militar::isAtivoNaEscala).collect(Collectors.toList());
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

    // --- LÓGICA DE AFASTAMENTO E SERVIÇO EXTERNO ---
    
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
    
    private boolean isDiaUtil(LocalDate data) {
        DayOfWeek dia = data.getDayOfWeek();
        if (dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY) return false;
        if (feriadoRepository.existsByData(data)) return false;
        return true;
    }
    
    public void marcarServicoExterno(Long id) {
        Militar m = findMilitarById(id);
        if (m != null) { 
            m.setEmServicoExterno(true); 
            m.setDataFimServicoExterno(null); // Limpa caso houvesse data de missões antigas
            militarRepository.save(m); 
        }
    }

    public void limparStatusExterno(Long id) {
        Militar m = findMilitarById(id);
        if (m != null) { 
            m.setEmServicoExterno(false); 
            // INÍCIO DO DESCANSO DE 48 HORAS: Registra a data em que o serviço externo acabou
            m.setDataFimServicoExterno(LocalDate.now()); 
            militarRepository.save(m); 
        }
    }
    
    public void processarTroca(Long idSai, Long idEntra) {
        Militar sai = findMilitarById(idSai);
        Militar entra = findMilitarById(idEntra);

        if (sai != null && entra != null) {
            // CORREÇÃO: Quem ENTRA para assumir o serviço do amigo é quem gasta a folga e zera
            entra.setFolga(0);
            entra.setDataUltimoServico(LocalDate.now());
            militarRepository.save(entra);
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