package br.mil.eb.escala.demo;

import br.mil.eb.escala.model.Aviso;
import br.mil.eb.escala.model.Feriado;
import br.mil.eb.escala.model.Militar;
import br.mil.eb.escala.model.Missao;
import br.mil.eb.escala.model.Perfil;
import br.mil.eb.escala.model.PrioridadeAviso;
import br.mil.eb.escala.model.Usuario;
import br.mil.eb.escala.repository.AvisoRepository;
import br.mil.eb.escala.repository.EscalaFixadaRepository;
import br.mil.eb.escala.repository.FeriadoRepository;
import br.mil.eb.escala.repository.MilitarRepository;
import br.mil.eb.escala.repository.MissaoRepository;
import br.mil.eb.escala.repository.TrocaRepository;
import br.mil.eb.escala.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashSet;
import java.util.List;

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
    private final AvisoRepository avisoRepository;
    private final MissaoRepository missaoRepository;
    private final TrocaRepository trocaRepository;
    private final EscalaFixadaRepository escalaFixadaRepository;

    public DemoDataLoader(MilitarRepository militarRepository,
                          FeriadoRepository feriadoRepository,
                          UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          AvisoRepository avisoRepository,
                          MissaoRepository missaoRepository,
                          TrocaRepository trocaRepository,
                          EscalaFixadaRepository escalaFixadaRepository) {
        this.militarRepository = militarRepository;
        this.feriadoRepository = feriadoRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.avisoRepository = avisoRepository;
        this.missaoRepository = missaoRepository;
        this.trocaRepository = trocaRepository;
        this.escalaFixadaRepository = escalaFixadaRepository;
    }

    @Override
    public void run(String... args) {
        restaurarDadosDemo();
    }

    @Scheduled(cron = "0 0 * * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void restaurarDadosDemo() {
        // Dentro de uma transação o Hibernate faz os INSERTs antes dos DELETEs pendentes
        // (e com IDENTITY o insert sai na hora). Por isso apagamos com comandos diretos
        // (deleteAllInBatch) ou forçando o flush, senão o usuário "demo" novo bate
        // com o antigo ainda não apagado ("Unique index" no e-mail) na virada de hora.
        missaoRepository.deleteAll();   // apaga também a tabela de ligação com os militares
        missaoRepository.flush();
        trocaRepository.deleteAllInBatch();   // antes dos militares (referência)
        escalaFixadaRepository.deleteAllInBatch();
        avisoRepository.deleteAllInBatch();
        militarRepository.deleteAllInBatch();
        feriadoRepository.deleteAllInBatch();
        usuarioRepository.deleteAllInBatch();

        criarUsuario();
        criarFeriados();
        criarMilitares();
        criarAvisos();
        criarMissoes();

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

    private void criarAvisos() {
        LocalDate hoje = LocalDate.now();
        LocalDate sexta = hoje.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));

        aviso("Formatura geral na sexta", "Pátio principal às 07h00. Uniforme 9º A1. Tolerância zero de atraso.",
                PrioridadeAviso.IMPORTANTE, hoje, sexta);
        aviso("Revista do armamento", "Todos os militares escalados na semana devem apresentar o armamento limpo ao Sgt de dia.",
                PrioridadeAviso.NORMAL, hoje, hoje.plusDays(6));
        aviso("Inspeção do Comandante", "Inspeção nas alas e alojamentos amanhã pela manhã. Camas feitas e armários fechados.",
                PrioridadeAviso.URGENTE, hoje, hoje.plusDays(1));
    }

    private void aviso(String titulo, String mensagem, PrioridadeAviso prioridade, LocalDate inicio, LocalDate fim) {
        Aviso a = new Aviso();
        a.setTitulo(titulo);
        a.setMensagem(mensagem);
        a.setPrioridade(prioridade);
        a.setDataInicio(inicio);
        a.setDataFim(fim);
        a.setAutor(LOGIN_DEMO);
        a.setCriadoEm(LocalDateTime.now());
        avisoRepository.save(a);
    }

    private void criarMissoes() {
        LocalDate hoje = LocalDate.now();
        List<Militar> todos = militarRepository.findAll();

        // titular de hoje (Alves) numa missão: continua na permanência (padrão)
        missao("Escolta de material", "Base de Apoio Logístico", hoje, LocalTime.of(14, 0), true, false,
                "Viatura pronta às 13h30 no portão principal.", todos, "Alves", "Duarte", "Ferraz");
        missao("Guarda do paiol", "Paiol central", hoje.plusDays(1), LocalTime.of(7, 30), false, false,
                null, todos, "Cardoso");
        missao("Apoio à formatura", "Pátio principal", hoje.plusDays(2), LocalTime.of(6, 30), false, false,
                "Montagem do palanque e som.", todos, "Gouveia", "Holanda", "Ivo");
        // exemplo em que o ADM afastou o escalado da permanência
        missao("Transporte de pessoal", "Hospital Militar de Área", hoje.plusDays(3), LocalTime.of(9, 0), false, true,
                "Viagem o dia todo: escalado fora da permanência.", todos, "Esteves");
    }

    private void missao(String titulo, String local, LocalDate data, LocalTime horario, boolean ultimaHora,
                        boolean afastaDaPermanencia, String descricao, List<Militar> todos, String... nomesDeGuerra) {
        Missao mi = new Missao();
        mi.setTitulo(titulo);
        mi.setLocal(local);
        mi.setData(data);
        mi.setHorario(horario);
        mi.setUltimaHora(ultimaHora);
        mi.setAfastaDaPermanencia(afastaDaPermanencia);
        mi.setDescricao(descricao);
        mi.setAutor(LOGIN_DEMO);
        mi.setCriadoEm(LocalDateTime.now());
        LinkedHashSet<Militar> escalados = new LinkedHashSet<>();
        for (String nome : nomesDeGuerra) {
            todos.stream().filter(m -> m.getNomeGuerra().equals(nome)).findFirst().ifPresent(escalados::add);
        }
        mi.setMilitares(escalados);
        missaoRepository.save(mi);
    }
}
