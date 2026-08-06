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
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EscalaService {

    @Autowired private MilitarRepository militarRepository;
    @Autowired private FeriadoRepository feriadoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    public List<Militar> getMilitaresOrdenadosParaEscala() {
        LocalDate hoje = LocalDate.now();
        LocalDate ontem = hoje.minusDays(1);
        List<Militar> todos = militarRepository.findAll();
        
        return todos.stream()
                .filter(m -> m.estaAptoParaServico(hoje))
                .filter(m -> m.getDataUltimoServico() == null || !m.getDataUltimoServico().equals(ontem)) 
                .sorted(Comparator.comparing(Militar::getFolga).reversed()
                        .thenComparing(Militar::getDataUltimoServico, Comparator.nullsFirst(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    // MÉTODO CORRIGIDO: Prioriza quem foi escalado manualmente para hoje
    public Militar getProximoPermanencia() {
        LocalDate hoje = LocalDate.now();
        List<Militar> todos = militarRepository.findAll();
        
        Militar trocadoParaHoje = todos.stream()
                .filter(m -> m.getDataUltimoServico() != null && m.getDataUltimoServico().isEqual(hoje))
                .findFirst()
                .orElse(null);
            
        if (trocadoParaHoje != null) return trocadoParaHoje;

        List<Militar> ordenados = getMilitaresOrdenadosParaEscala(); 
        return ordenados.isEmpty() ? null : ordenados.get(0);
    }
    
    public Militar getProximoSubstituto() {
        List<Militar> ordenados = getMilitaresOrdenadosParaEscala();
        return ordenados.size() < 2 ? null : ordenados.get(1); 
    }

    public List<Militar> getTodosMilitaresParaDashboard() {
        return militarRepository.findAll().stream()
                .sorted(Comparator.comparing(Militar::isAtivoNaEscala).reversed()
                        .thenComparing(Militar::getFolga).reversed())
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
            
            LocalDate hoje = LocalDate.now();
            int folgaCalculada = 0;
            
            if (novaData.isBefore(hoje)) {
                folgaCalculada = (int) ChronoUnit.DAYS.between(novaData, hoje);
            }
            
            militar.setFolga(folgaCalculada);
            militarRepository.save(militar);
        }
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "America/Sao_Paulo")
    @Transactional 
    public void avancarDiaDaEscala() {
        LocalDate hoje = LocalDate.now();
        boolean ehDiaDeServico = isDiaUtil(hoje); 
        
        Militar permanencia = ehDiaDeServico ? getProximoPermanencia() : null;
        List<Militar> todos = militarRepository.findAll();

        for (Militar m : todos) {
            if (!m.isAtivoNaEscala() && m.getDataFimAfastamento() != null && 
               (m.getDataFimAfastamento().isEqual(hoje) || m.getDataFimAfastamento().isBefore(hoje))) {
                    m.setAtivoNaEscala(true);
                    m.setMotivoAfastamento(null);
                    m.setDataInicioAfastamento(null);
                    m.setDataFimAfastamento(null);
            }

            if (ehDiaDeServico) {
                if (permanencia != null && m.getId().equals(permanencia.getId())) {
                    m.setFolga(0); 
                    m.setDataUltimoServico(hoje); 
                } else {
                    m.setFolga(m.getFolga() + 1); 
                }
            } else {
                m.setFolga(m.getFolga() + 1);
            }
        }
        militarRepository.saveAll(todos); 
    }

    public List<Militar> getMilitaresAtivos() {
        return militarRepository.findAll().stream().filter(Militar::isAtivoNaEscala).collect(Collectors.toList());
    }
    
    public Militar findMilitarById(Long id) {
        return militarRepository.findById(id).orElse(null); 
    }

    public void salvarNovoMilitar(Militar militar) {
        militar.setAtivoNaEscala(true);
        if (militar.getDataUltimoServico() == null) militar.setDataUltimoServico(LocalDate.now());

        LocalDate hoje = LocalDate.now();
        LocalDate dataUltimoSv = militar.getDataUltimoServico();
        int folgaCalculada = 0;

        if (dataUltimoSv.isBefore(hoje)) {
            folgaCalculada = (int) ChronoUnit.DAYS.between(dataUltimoSv, hoje);
        }

        militar.setFolga(folgaCalculada); 
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
    
    @Transactional
    public void processarTroca(Long idSai, Long idEntra, String tipoTroca, String funcaoAlvo) {
        Militar sai = findMilitarById(idSai);
        Militar entra = findMilitarById(idEntra);
        
        if (sai == null || entra == null) return;
        
        LocalDate hoje = LocalDate.now();
        if (tipoTroca == null) tipoTroca = "MISSAO";

        switch (tipoTroca) {
            case "PAGO":
                sai.setFolga(0);
                sai.setDataUltimoServico(hoje);
                break;
                
            case "MISSAO":
                // Força o Cb Tomaz a ser exibido no Dashboard hoje sem zerar a folga dele ainda
                entra.setDataUltimoServico(hoje);
                break;
                
            case "PERMUTA":
                int folgaTemp = sai.getFolga();
                LocalDate dataTemp = sai.getDataUltimoServico();
                sai.setFolga(entra.getFolga());
                sai.setDataUltimoServico(entra.getDataUltimoServico());
                entra.setFolga(folgaTemp);
                entra.setDataUltimoServico(dataTemp);
                break;
        }
        
        militarRepository.save(sai);
        militarRepository.save(entra);
    }
    
    public List<Feriado> getTodosFeriados() { return feriadoRepository.findAll(); }

    public List<Usuario> getTodosUsuarios() { return usuarioRepository.findAll(); }

    @Transactional
    public void salvarNovoUsuario(Usuario usuario) {
        if (usuario.getSenha() != null && !usuario.getSenha().isEmpty()) {
            usuario.setSenha(passwordEncoder.encode(usuario.getSenha()));
        }
        usuarioRepository.save(usuario);
    }

    @Transactional
    public void deletarUsuario(Long id) {
        if (usuarioRepository.existsById(id)) usuarioRepository.deleteById(id);
    }
}
