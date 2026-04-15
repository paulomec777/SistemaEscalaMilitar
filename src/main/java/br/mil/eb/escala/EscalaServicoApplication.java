package br.mil.eb.escala;

import br.mil.eb.escala.model.Feriado;
import br.mil.eb.escala.model.Perfil;
import br.mil.eb.escala.model.Usuario;
import br.mil.eb.escala.repository.FeriadoRepository;
import br.mil.eb.escala.repository.UsuarioRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;
import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class EscalaServicoApplication implements CommandLineRunner {

    @Autowired
    private UsuarioRepository usuarioRepository;
    
    @Autowired
    private FeriadoRepository feriadoRepository; // Injetado para gerenciar as datas do EB

    @Autowired
    private PasswordEncoder passwordEncoder;

    public static void main(String[] args) {
        // --- CONFIGURAÇÃO DE EMERGÊNCIA (HARDCODED) ---
        System.setProperty("spring.datasource.url", "jdbc:postgresql://ep-twilight-hill-ahxn609v-pooler.c-3.us-east-1.aws.neon.tech/neondb?user=neondb_owner&password=npg_JjX8OAhMFYV4&sslmode=require&channelBinding=require");
        System.setProperty("spring.datasource.driver-class-name", "org.postgresql.Driver");
        System.setProperty("spring.jpa.hibernate.ddl-auto", "update");
        System.setProperty("spring.jpa.show-sql", "true");
        System.setProperty("spring.jpa.properties.hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        // ----------------------------------------------

        SpringApplication.run(EscalaServicoApplication.class, args);
    }

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        System.out.println("--- Fuso horário definido para: " + TimeZone.getDefault().getID() + " ---");
    }

    @Override
    public void run(String... args) throws Exception {
        // 1. GESTÃO DE USUÁRIO ROOT
        Optional<Usuario> admin = usuarioRepository.findByLogin("root");
        if (admin.isEmpty()) {
            System.out.println("--- CRIANDO USUÁRIO ADM (root) ---");
            Usuario novoAdmin = new Usuario();
            novoAdmin.setLogin("root");
            novoAdmin.setNomeCompleto("Administrador do Sistema");
            novoAdmin.setSenha(passwordEncoder.encode("root")); 
            novoAdmin.setPerfil(Perfil.ADM); 
            novoAdmin.setEmail("admin@escala.eb.mil.br"); 
            usuarioRepository.save(novoAdmin);
        }

        // 2. PRÉ-CADASTRO DE FERIADOS DO EXÉRCITO (Para garantir que o sistema não rode amanhã)
        cadastrarFeriadoSeNaoExistir(LocalDate.of(2026, 4, 16), "DIA DA BANDEIRA (EXPEDIENTE SUSPENSO)");
        cadastrarFeriadoSeNaoExistir(LocalDate.of(2026, 4, 19), "DIA DO EXÉRCITO");
        cadastrarFeriadoSeNaoExistir(LocalDate.of(2026, 5, 1), "DIA DO TRABALHADOR");
        
        System.out.println("--- INICIALIZAÇÃO CONCLUÍDA COM SUCESSO ---");
    }

    /**
     * Método auxiliar para popular os feriados iniciais
     */
    private void cadastrarFeriadoSeNaoExistir(LocalDate data, String descricao) {
        if (!feriadoRepository.existsByData(data)) {
            Feriado f = new Feriado();
            f.setData(data);
            f.setDescricao(descricao);
            feriadoRepository.save(f);
            System.out.println("--- FERIADO CADASTRADO: " + descricao + " [" + data + "] ---");
        }
    }
}