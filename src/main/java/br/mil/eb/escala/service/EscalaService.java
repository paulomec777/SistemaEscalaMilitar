package br.mil.eb.escala.service;

import br.mil.eb.escala.model.Feriado;
import br.mil.eb.escala.model.Militar;
import br.mil.eb.escala.model.MotivoInatividade;
import br.mil.eb.escala.model.Usuario;
import br.mil.eb.escala.repository.FeriadoRepository;
import br.mil.eb.escala.repository.MilitarRepository;
import br.mil.eb.escala.repository.UsuarioRepository;
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
        // Busca quem está apto
        List<Militar> todos = militarRepository.findByAtivoNaEscalaTrueAndEmServicoExternoFalseOrderByFolgaDesc();
        
        // Regra: Quem tirou serviço ontem não pode tirar hoje (dobra)
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
        militar.setDataUltimoServico(LocalDate.now()); // Começa zerado
        militarRepository.save(militar);
    }
    
    public void editarMilitar(Militar dados) {
        Militar original = findMilitarById(dados.getId());
        if (original != null) {
            original.setGraduacao(dados.getGraduacao());
            original.setNomeGuerra(dados.getNomeGuerra());
            // Se precisar editar antiguidade, adicione aqui
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

    // --- MOTOR DA ESCALA (AQUI ESTÁ A LÓGICA DO FIM DE SEMANA) ---
    
    // Roda todo dia à meia-noite (00:00:00)
    @Scheduled(cron = "0 0 0 * * *", zone = "America/Sao_Paulo")
    public void avancarDiaDaEscala() {
        LocalDate hoje = LocalDate.now();
        System.out.println("--- TENTATIVA DE RODAR ESCALA: " + hoje + " ---");

        // [IMPORTANTE] Se for Sábado, Domingo ou Feriado, o sistema PARA aqui.
        // Ninguém ganha folga, ninguém é escalado. O sistema "congela" até segunda.
        if (!isDiaUtil(hoje)) {
            System.out.println("Hoje é " + hoje.getDayOfWeek() + " ou feriado. Escala pausada.");
            return; 
        }

        // Se passou daqui, é dia útil (Segunda a Sexta sem feriado)
        System.out.println("Dia útil detectado. Rodando escala...");

        Militar proximo = getProximoDaEscala();
        List<Militar> todos = militarRepository.findAll();

        for (Militar m : todos) {
            
            // CASO 1: É o militar escalado para HOJE
            if (proximo != null && m.getId().equals(proximo.getId())) {
                m.setFolga(0); // Zera a folga (vai pro final da fila)
                m.setDataUltimoServico(hoje);
                m.setEmServicoExterno(false);
            
            // CASO 2: Está em missão/serviço externo (não concorre mas ganha folga)
            } else if (m.isEmServicoExterno()) {
                m.setFolga(m.getFolga() + 1); 
                m.setDataUltimoServico(hoje); // Atualiza data para controle

            // CASO 3: Está de folga normal
            } else {
                m.setFolga(m.getFolga() + 1); // Ganha +1 ponto de folga
                m.setEmServicoExterno(false); 
                
                // Verifica se o afastamento acabou hoje e reativa o militar
                if (!m.isAtivoNaEscala() && m.getDataFimAfastamento() != null && 
                   (m.getDataFimAfastamento().isEqual(hoje) || m.getDataFimAfastamento().isBefore(hoje))) {
                        reativarMilitar(m.getId());
                }
            }
        }
        militarRepository.saveAll(todos);
    }
    
    // Método auxiliar para checar se deve ter expediente
    private boolean isDiaUtil(LocalDate data) {
        DayOfWeek dia = data.getDayOfWeek();
        // Se for Sábado ou Domingo, retorna Falso
        if (dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY) return false;
        // Se for feriado cadastrado no banco, retorna Falso
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
            // Quem pediu para SAIR perde a vez (vai pro fim da fila como se tivesse tirado)
            sai.setFolga(0);
            sai.setDataUltimoServico(LocalDate.now());
            militarRepository.save(sai);
            
            // Quem ENTROU mantém sua folga (faz o serviço "na camaradagem" ou troca acordada)
            // Obs: Se quiser que quem entra TAMBÉM perca a folga, adicione:
            // entra.setFolga(0);
            // militarRepository.save(entra);
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
        if (id != 1L) { // Proteção para não deletar o admin principal
            usuarioRepository.deleteById(id);
        }
    }
}