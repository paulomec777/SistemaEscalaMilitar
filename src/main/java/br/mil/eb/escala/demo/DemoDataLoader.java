package br.mil.eb.escala.demo;

import br.mil.eb.escala.model.Feriado;
import br.mil.eb.escala.model.Militar;
import br.mil.eb.escala.model.Perfil;
import br.mil.eb.escala.model.Usuario;
import br.mil.eb.escala.repository.FeriadoRepository;
import br.mil.eb.escala.repository.MilitarRepository;
import br.mil.eb.escala.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Dados fictícios para a versão de demonstração (perfil "demo").
 * Na inicialização e a cada hora, o banco é apagado e repovoado,
 * para que nada do que um visitante alterar fique permanente.
 * Login de visitante: demo / demo.
 */
@Component
@Profile("demo")
@Order(100)
public class DemoDataLoader implements CommandLineRunner {

    public static final String LOGIN_DEMO = "demo";
    public static final String SENHA_DEMO = "demo";

    private final MilitarRepository militarRepository;
    private final FeriadoRepository feriadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataLoader(MilitarRepository militarRepository,
                          FeriadoRepository feriadoRepository,
                          UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder) {
        this.militarRepository = militarRepository;
        this.feriadoRepository = feriadoRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        restaurarDadosDemo();
    }

    @Scheduled(cron = "0 0 * * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void restaurarDadosDemo() {
        militarRepository.deleteAll();
        feriadoRepository.deleteAll();
        usuarioRepository.deleteAll();

        criarUsuario();
        criarFeriados();
        criarMilitares();

        System.out.println("--- DEMO: dados fictícios restaurados ---");
    }

    private void criarUsuario() {
        Usuario demo = new Usuario();
        demo.setLogin(LOGIN_DEMO);
        demo.setNomeCompleto("Visitante da Demonstração");
        demo.setSenha(passwordEncoder.encode(SENHA_DEMO));
        demo.setEmail("demo@exemplo.com");
        demo.setPerfil(Perfil.ADM);
        usuarioRepository.save(demo);
    }

    private void criarFeriados() {
        int ano = LocalDate.now().getYear();
        for (int a = ano; a <= ano + 1; a++) {
            feriado(LocalDate.of(a, 1, 1), "CONFRATERNIZAÇÃO UNIVERSAL");
            feriado(LocalDate.of(a, 4, 19), "DIA DO EXÉRCITO");
            feriado(LocalDate.of(a, 4, 21), "TIRADENTES");
            feriado(LocalDate.of(a, 5, 1), "DIA DO TRABALHADOR");
            feriado(LocalDate.of(a, 9, 7), "INDEPENDÊNCIA DO BRASIL");
            feriado(LocalDate.of(a, 10, 12), "NOSSA SENHORA APARECIDA");
            feriado(LocalDate.of(a, 11, 2), "FINADOS");
            feriado(LocalDate.of(a, 11, 15), "PROCLAMAÇÃO DA REPÚBLICA");
            feriado(LocalDate.of(a, 11, 20), "DIA DA CONSCIÊNCIA NEGRA");
            feriado(LocalDate.of(a, 12, 25), "NATAL");
        }
    }

    private void feriado(LocalDate data, String descricao) {
        if (!feriadoRepository.existsByData(data)) {
            feriadoRepository.save(new Feriado(null, data, descricao));
        }
    }

    private void criarMilitares() {
        LocalDate hoje = LocalDate.now();

        militar("Alves", "3º Sgt", 9, hoje.minusDays(12));
        militar("Barros", "3º Sgt", 8, hoje.minusDays(11));
        militar("Cardoso", "Cb", 7, hoje.minusDays(10));
        militar("Duarte", "Cb", 6, hoje.minusDays(9));
        militar("Esteves", "Cb", 5, hoje.minusDays(8));
        militar("Ferraz", "Sd", 4, hoje.minusDays(6));
        militar("Gouveia", "Sd", 3, hoje.minusDays(5));
        militar("Holanda", "Sd", 2, hoje.minusDays(3));
        militar("Ivo", "Sd", 1, hoje.minusDays(2));

        Militar ferias = militar("Jardim", "3º Sgt", 10, hoje.minusDays(15));
        ferias.setAtivoNaEscala(false);
        ferias.setMotivoAfastamento("Férias");
        ferias.setDataInicioAfastamento(hoje.minusDays(5));
        ferias.setDataFimAfastamento(hoje.plusDays(10));
        militarRepository.save(ferias);

        Militar externo = militar("Leite", "Cb", 6, hoje.minusDays(7));
        externo.setDataInicioServicoExterno(hoje);
        externo.setDataFimServicoExterno(hoje.plusDays(1));
        militarRepository.save(externo);
    }

    private Militar militar(String nomeGuerra, String graduacao, int folga, LocalDate ultimoServico) {
        Militar m = new Militar();
        m.setNomeGuerra(nomeGuerra);
        m.setGraduacao(graduacao);
        m.setFolga(folga);
        m.setDataUltimoServico(ultimoServico);
        return militarRepository.save(m);
    }
}
