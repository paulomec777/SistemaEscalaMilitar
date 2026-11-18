package br.mil.eb.escala;

import br.mil.eb.escala.demo.DemoDataLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Sobe o sistema inteiro com o banco H2 da demo (não precisa de PostgreSQL)
@SpringBootTest
@ActiveProfiles("demo")
class EscalaServicoApplicationTests {

	@Autowired
	private DemoDataLoader demoDataLoader;

	@Test
	void contextLoads() {
	}

	// A restauração de hora em hora roda numa transação (via agendador) e já falhou com
	// "Unique index" ao recriar o usuário demo. Chamar pelo bean passa pela transação.
	@Test
	void restauracaoDaDemoPodeRodarVariasVezes() {
		demoDataLoader.restaurarDadosDemo();
		demoDataLoader.restaurarDadosDemo();
	}

}
