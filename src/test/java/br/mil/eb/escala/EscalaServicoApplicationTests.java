package br.mil.eb.escala;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Sobe o sistema inteiro com o banco H2 da demo (não precisa de PostgreSQL)
@SpringBootTest
@ActiveProfiles("demo")
class EscalaServicoApplicationTests {

	@Test
	void contextLoads() {
	}

}
