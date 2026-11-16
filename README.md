# Sistema de Escala Militar

Sistema web que substitui as planilhas manuais da escala de serviço de uma
organização militar. Ele calcula automaticamente quem é o próximo a tirar
serviço e o substituto, considerando folgas, afastamentos, feriados e trocas.

Antes, a escala ficava em planilhas espalhadas, difíceis de consultar e
sujeitas a erro humano. Agora a informação fica em um só lugar: qualquer
pessoa consulta o painel, e só o administrador altera a escala.

## Funcionalidades

- **Painel público** com o militar de permanência do dia, o substituto e a
  situação de cada um (apto, afastado ou em serviço externo).
- **Cálculo automático da escala:** o próximo é quem tem mais dias de folga;
  no empate, quem tirou serviço há mais tempo. Quem saiu de serviço ontem
  não entra hoje.
- **Virada do dia à meia-noite** (tarefa agendada): em dia útil, quem tirou
  serviço zera a folga e os demais ganham +1; em fim de semana e feriado,
  todos ganham +1. Afastamentos vencidos são reativados sozinhos.
- **Afastamentos** (férias, dispensa médica etc.) com data de início e fim,
  e impedimento manual com justificativa.
- **Serviço externo** com período agendado e a regra das 48 horas: o militar
  fica fora da escala durante o serviço e no dia seguinte.
- **Trocas de serviço** de três tipos:
  - *Pago:* quem entra assume o dia sem perder a folga acumulada.
  - *Missão:* o substituto assume o dia normalmente.
  - *Permuta:* os dois militares trocam folga e data do último serviço.
- **Cadastro de feriados**, que o cálculo usa para saber o que é dia útil.
- **Login e perfis de acesso** (`ADM` e `MILITAR`) com Spring Security e
  senhas criptografadas com BCrypt. A área `/admin` é restrita ao ADM.

## Tecnologias

- Java 21
- Spring Boot 3.1.5 (Web, Data JPA, Security, Validation)
- Thymeleaf + Thymeleaf Extras Spring Security
- PostgreSQL
- Lombok
- Maven

## Estrutura

```
src/main/java/br/mil/eb/escala
├── config/        # Spring Security (rotas públicas e restritas, login, BCrypt)
├── controller/    # Páginas do painel, área administrativa e feriados
├── model/         # Militar, Usuario, Feriado, Perfil
├── repository/    # Interfaces Spring Data JPA
├── security/      # Carrega o usuário para autenticação
└── service/       # EscalaService: regras da escala, trocas e virada do dia
```

As regras de negócio ficam concentradas em `EscalaService` e no método
`Militar.estaAptoParaServico`, que decide se um militar pode tirar serviço
em uma data.

## Como rodar

Pré-requisitos: Java 21 e um banco PostgreSQL.

1. Clone o repositório:

   ```bash
   git clone https://github.com/paulomec777/SistemaEscalaMilitar.git
   cd SistemaEscalaMilitar
   ```

2. Defina as variáveis de ambiente. Nenhuma senha fica no código.

   | Variável         | Exemplo                                        |
   | ---------------- | ---------------------------------------------- |
   | `DB_URL`         | `jdbc:postgresql://localhost:5432/escala`      |
   | `DB_USERNAME`    | `postgres`                                     |
   | `DB_PASSWORD`    | a senha do seu banco                           |
   | `ADMIN_PASSWORD` | senha inicial do usuário administrador `root`  |

   No Linux ou macOS:

   ```bash
   export DB_URL=jdbc:postgresql://localhost:5432/escala
   export DB_USERNAME=postgres
   export DB_PASSWORD=sua_senha
   export ADMIN_PASSWORD=senha_do_admin
   ```

   No Windows (PowerShell), use `$env:DB_URL="..."` e assim por diante.

3. Suba a aplicação:

   ```bash
   ./mvnw spring-boot:run
   ```

   No Windows: `mvnw.cmd spring-boot:run`.

4. Acesse `http://localhost:8080`. O painel abre sem login; para a área
   administrativa, entre com o usuário `root` e a senha de `ADMIN_PASSWORD`.

As tabelas são criadas automaticamente pelo Hibernate na primeira execução.
O usuário `root` só é criado se `ADMIN_PASSWORD` estiver definida.

## Autor

Desenvolvido por **Paulo Henrique Tomaz Martins**.

- [LinkedIn](https://www.linkedin.com/in/paulo-henrique-517268269)
- Contato: paulo92176914@gmail.com
