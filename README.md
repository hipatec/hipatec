# Hipatec — backend

API em **Java 17 e Spring Boot 4.0.6**, com Gradle Wrapper 9.4.1. Repositório de origem: [hipatec/hipatec](https://github.com/hipatec/hipatec). Base original `27db9e96590c757464e916ebbabc977e9400e4dd`, com recuperação de senha adicionada nesta branch.

O projeto Gradle está na subpasta **`hipatec/`**. O frontend com integração existente é [hipatec-app](https://github.com/hipatec/hipatec-app), em Angular/Ionic. Os dois projetos são executados separadamente: frontend normalmente na porta 8100 e API na porta 8080.

## Arquitetura existente

| Tecnologia | Uso observado |
| --- | --- |
| Spring MVC | Controllers HTTP |
| Spring Data JPA + SQL Server | Estudantes, mentoras, perfis e mentorias |
| Firebase Realtime Database | Posts, curtidas, comentários, reposts e referências de feed |
| Cloudinary | Upload de fotos e capas de perfil |
| Spring Security | Configuração atual permite todas as requisições |
| Springdoc | Dependência de documentação OpenAPI |

H2 e Redis aparecem nas dependências, mas não há perfil H2 pronto para substituir SQL Server nem uso de Redis nos serviços examinados. Não são alternativas configuradas automaticamente.

## Pré-requisitos e pendências para executar

1. Instalar **JDK 17**, selecionado pelo toolchain do build. Não é necessário instalar Gradle globalmente; o wrapper baixa a versão declarada. Precisa haver acesso à rede para obter as dependências.
2. Disponibilizar um SQL Server de desenvolvimento e configurar URL JDBC, usuário e senha.
3. Obter com a equipe os scripts do banco, especialmente os procedimentos **`login_estudante` e `login_mentora`**. Contas com senha legada ainda usam esses procedimentos; contas que redefiniram a senha usam BCrypt no Java. Os scripts dos procedimentos não estão versionados. `ddl-auto=update` não cria os procedimentos de login.
4. Disponibilizar uma conta de serviço Firebase e o Realtime Database de desenvolvimento. **`FirebaseConfig.java` possui um caminho absoluto de Windows para um JSON local da Nathalia**; é necessário ajustar essa configuração para a máquina que executará a API. O JSON não está neste repositório.
5. Configurar as credenciais Cloudinary para a funcionalidade de upload.

Essas dependências impedem tratar um clone novo como ambiente pronto. Combine com Nathalia a preparação do banco e das integrações antes de validar cadastro/login e feed.

## Configuração

Arquivo atual: [hipatec/src/main/resources/application.properties](hipatec/src/main/resources/application.properties).

As propriedades Spring abaixo podem ser sobrescritas por variáveis de ambiente, sem editar os valores versionados:

| Variável | Configuração |
| --- | --- |
| `SPRING_DATASOURCE_URL` | URL JDBC do SQL Server de desenvolvimento |
| `SPRING_DATASOURCE_USERNAME` | Usuário do banco |
| `SPRING_DATASOURCE_PASSWORD` | Senha do banco |
| `CLOUDINARY_CLOUD_NAME` | Nome da conta Cloudinary |
| `CLOUDINARY_API_KEY` | Chave da API Cloudinary |
| `CLOUDINARY_API_SECRET` | Segredo da API Cloudinary |
| `SERVER_PORT` | Porta HTTP, padrão Spring 8080 |

Não há carregamento de arquivo `.env` implementado. Configure as variáveis no ambiente do terminal ou da IDE. A configuração do Firebase está fixa em [FirebaseConfig.java](hipatec/src/main/java/com/solucao/hipatec/config/FirebaseConfig.java); variáveis de ambiente não substituem automaticamente o caminho literal e a URL desse código.

O arquivo de propriedades já contém valores de credenciais versionados. Eles não são reproduzidos neste README. A equipe deve verificar e rotacionar os segredos expostos, retirar credenciais do versionamento e usar configurações locais antes de publicar ou compartilhar um ambiente. Sobrescrever por variáveis não apaga os valores do histórico.

Outros comportamentos atuais: `ddl-auto=update` pode alterar o esquema do banco; uploads têm limites configurados de 50 MB por arquivo/requisição. Use um banco de desenvolvimento apropriado para os testes.

## Comandos

Na raiz do repositório, depois de resolver os pré-requisitos:

```bash
cd hipatec
bash gradlew bootRun
```

No Windows, dentro da mesma subpasta:

```powershell
.\gradlew.bat bootRun
```

A API usa normalmente <http://localhost:8080/>. `GET /` retorna `API funcionando!` quando a aplicação inicia. Esse retorno não verifica os procedimentos de login nem todos os serviços externos.

```bash
bash gradlew test
bash gradlew bootJar
```

O JAR é gerado em `hipatec/build/libs/`, considerando a raiz do repositório. O teste original `HipatecApplicationTests` ainda depende do ambiente completo de banco/Firebase. Os testes novos de recuperação usam H2 isolado e não carregam Firebase, Cloudinary nem as credenciais do arquivo principal. Foram executados com JDK 17; não comprovam a integração com o SQL Server da equipe.

## Endpoints presentes

Caminhos relativos à URL da API. Esta tabela descreve o código, não certifica que as operações passaram em testes integrados.

| Métodos e caminhos | Finalidade |
| --- | --- |
| `GET /` | Mensagem de funcionamento |
| `GET /estudantes`, `GET /estudantes/{id}`, `POST /estudantes` | Consulta e cadastro de estudantes |
| `POST /estudantes/login` | Login por `email` e `senha` em parâmetros; retorna ID inteiro |
| `GET /mentoras`, `GET /mentoras/{id}`, `POST /mentoras` | Consulta e cadastro de mentoras |
| `POST /mentoras/login` | Login por `email` e `senha` em parâmetros; retorna ID inteiro |
| `POST /auth/recuperacao-senha` | Solicita link por e-mail; corpo JSON apenas com `email`; retorna 202 sem informar existência da conta |
| `POST /auth/redefinir-senha` | Troca senha com token de uso único; corpo JSON com `token` e `senha`; retorna 204 |
| `GET /perfil`, `POST /perfil` | Consulta e criação de perfil |
| `GET /perfil/{role}/{id}` | Perfil, com `role` esperado como `estudantes` ou `mentoras` |
| `PUT /perfil/{role}/{id}` | Edição por parâmetros `nome`, `usuario`, `biografia` e arquivos opcionais `pfp`, `background` |
| `GET /mentorias`, `POST /mentorias` | Listagem e criação iniciais |
| `GET /posts`, `POST /posts` | Listagem e criação de posts |
| `GET /posts/autor/{autorId}` | Posts de uma autora |
| `POST /posts/{id}/like`, `DELETE /posts/{id}/like` | Curtir/descurtir, parâmetro `usuarioId` |
| `POST /posts/{id}/comment` | Comentário com corpo `ComentarioDTO` |
| `POST /posts/{id}/repost` | Repost, parâmetro `usuarioId` |

Os controllers de estudantes, mentoras, perfil e mentorias liberam CORS para **`http://localhost:8100`**. Posts usam `*`. Execute o `hipatec-app` com `npm start -- --port 8100` para corresponder à origem configurada. CORS não substitui autorização.

## Recuperação de senha — teste local

O frontend usa a tela da equipe em `/forgot-password` e `/redefinir-senha`. Estudantes e mentoras solicitam recuperação apenas por e-mail; o backend identifica o perfil automaticamente. O token aleatório de 256 bits fica no fragmento do link (`#token=...`); apenas seu SHA-256 é persistido. O link vence em **30 minutos**, só pode ser usado uma vez e é substituído no próximo envio. Reenvios para a mesma conta têm intervalo mínimo de 60 segundos. A nova senha recebe BCrypt e a usuária retorna ao login, sem autenticação automática.

A tabela JPA `recuperacao_senha` é nova. O atual `ddl-auto=update` a cria no banco configurado; revise a alteração com Nathalia e use banco de desenvolvimento. A coluna `senha` de estudante e mentora precisa comportar **pelo menos 68 caracteres** (`{bcrypt}` + hash). O tamanho no SQL Server real e eventuais gatilhos precisam ser conferidos: seus scripts não estão no repositório. E-mails com mais de uma conta correspondente, no mesmo perfil ou entre estudante e mentora, não geram link. A resposta HTTP continua genérica; a unicidade global ainda precisa ser garantida no cadastro/banco.

O processamento do pedido é assíncrono: respostas 202 não comprovam entrega. Falhas de SMTP são registradas sem expor e-mail/token/senha e desfazem a substituição do link. O limite por IP é de 5 solicitações e 10 tentativas de redefinição por 15 minutos, separado por operação. Esse limite é local à instância e reinicia com a API; atrás de proxy, configure o limite compartilhado e a origem confiável antes de escalar. A fila de envio é limitada a 50 pedidos; indisponibilidade retorna 503.

### Caixa de e-mail local

Use [Mailpit](https://mailpit.axllent.org/docs/install/) para capturar as mensagens, sem entregá-las a destinatárias reais. Na raiz deste repositório, com Docker disponível:

```bash
docker compose -f compose.mailpit.yaml up -d
```

As portas ficam restritas ao computador local. Para encerrar: `docker compose -f compose.mailpit.yaml down` (a caixa é descartável). O backend roda no host; se for colocado em outro container, configure `SMTP_HOST` para o endereço acessível do Mailpit.

Como alternativa, com o binário instalado:

```bash
mailpit --listen 127.0.0.1:8025 --smtp 127.0.0.1:1025
```

Abra <http://127.0.0.1:8025/>. O backend já usa SMTP `localhost:1025` por padrão, sem autenticação ou TLS, adequado apenas a essa caixa local. Não configure relay/encaminhamento. O envio suporta SMTP (Mailgun ou Mailpit) e a API do OneSignal; o padrão local continua sendo Mailpit.

| Variável | Padrão local / finalidade |
| --- | --- |
| `HIPATEC_MAIL_PROVIDER` | `smtp` local; `onesignal` para envio real |
| `ONESIGNAL_APP_ID`, `ONESIGNAL_API_KEY` | ID do app e App API Key, exigidos no modo `onesignal` |
| `SMTP_HOST`, `SMTP_PORT` | `localhost`, `1025` |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | Vazios; credenciais somente no ambiente quando houver provedor |
| `SMTP_AUTH` | `false`; ajustar conforme o provedor |
| `SMTP_STARTTLS` | `false` local; `true` exige STARTTLS no SMTP real |
| `HIPATEC_MAIL_FROM` | `nao-responda@hipatec.local`; usar remetente autorizado no envio real |
| `HIPATEC_FRONTEND_ORIGIN` | `http://localhost:8100`; CORS dos endpoints de recuperação |
| `HIPATEC_RESET_URL` | `http://localhost:8100/redefinir-senha`; URL fixa confiável, sem fragmento ou parâmetros; usar HTTPS fora do ambiente local |

Execute os testes a partir de `hipatec/`:

```bash
# H2 isolado e envio simulado: não requer SQL Server, Firebase ou Mailpit.
bash gradlew test --tests '*RecuperacaoSenhaTests'

# Também envia uma mensagem fictícia à caixa SMTP local em execução.
HIPATEC_TEST_SMTP_PORT=1025 bash gradlew test --tests '*RecuperacaoSenhaTests' --rerun-tasks
```

No Windows/PowerShell, defina `$env:HIPATEC_TEST_SMTP_PORT="1025"` antes de executar `./gradlew.bat test --tests '*RecuperacaoSenhaTests' --rerun-tasks`. O teste SMTP é opcional e ignorado quando a variável não está definida. Ele usa apenas `127.0.0.1`, uma conta fictícia `estudante@example.test` e banco H2 descartável; seu link não deve ser usado no banco real da aplicação.

Para testar no navegador, primeiro resolva os pré-requisitos do ambiente completo descritos acima, execute a API e o frontend na porta 8100, use uma conta de teste existente e abra **Esqueceu sua senha?**. Abra a mensagem no Mailpit, redefina a senha e confirme que a senha nova entra, a antiga falha e o mesmo link não funciona novamente. Não conecte os testes automatizados ao banco da equipe.

Validação em 16/09/2026: **14 testes passaram com Java 17**, incluindo HTTP, senhas de ambos os perfis, rejeição de links inválidos/expirados/reutilizados, concorrência, reversão em falha SMTP e captura real de um e-mail fictício no Mailpit local. A chamada ao procedimento legado foi verificada com simulação; o SQL Server e os procedimentos reais não foram executados.

Validação em 23/09/2026: **18 testes passaram com Java 17** (15 de recuperação e 3 de envio), incluindo captura do e-mail fictício no Mailpit 1.31.2. O contrato do OneSignal foi testado com HTTP simulado; nenhum e-mail real foi enviado pelo provedor.

### Ambiente visual isolado de recuperação

Para testar o fluxo no navegador sem SQL Server ou Firebase, inicie o Mailpit com o Compose acima e, na pasta `hipatec/` que contém o Gradle Wrapper, execute:

```bash
bash gradlew recuperacaoLocal
```

Em outro terminal, na raiz do frontend `hipatec-app`:

```bash
npm start -- --host localhost --port 8100
```

Abra <http://localhost:8100/login>. Contas fictícias disponíveis: `estudante@example.test` (Estudantes) e `mentora@example.test` (Mentoras), ambas com senha inicial `TesteLocal123!`. Clique em **Esqueceu sua senha?**, solicite o link e abra a mensagem correspondente em <http://localhost:8025>. Defina uma senha e teste o login com ela. A senha antiga e a reutilização do link devem falhar.

Esse comando executa os controllers e serviços reais de recuperação/login com H2 em memória e SMTP local. Os dados são descartados ao encerrar o backend. Não carrega Firebase, Cloudinary ou o banco da equipe, e não permite validar feed, uploads ou os procedimentos legados de login. O frontend completo fica disponível, mas funções fora desse fluxo não são atendidas por esse backend. As classes e contas de demonstração ficam em `src/test` e não entram no JAR de produção. Encerre cada processo com Ctrl+C.

### Teste real com Mailgun sandbox

Copie `hipatec/application-mailgun-local.properties.example` para `hipatec/application-mailgun-local.properties` e preencha o arquivo privado (ignorado pelo Git) com host, porta, usuário e senha SMTP, remetente do sandbox e `hipatec.local.test-email`. O destinatário precisa estar autorizado e confirmado no painel Mailgun.

Na pasta que contém `gradlew`, selecione explicitamente esse arquivo ao iniciar o ambiente descartável:

```bash
SPRING_CONFIG_IMPORT=file:./application-mailgun-local.properties SPRING_MAIL_TEST_CONNECTION=true bash gradlew recuperacaoLocal
```

Uma conta adicional de estudante usa o endereço informado em `hipatec.local.test-email`; as contas fictícias são preservadas. Solicite a recuperação em `http://localhost:8100/forgot-password` com esse endereço. A mensagem chega à caixa real; abra o link no computador que executa a aplicação. A resposta HTTP 202 indica aceitação do pedido, não entrega do e-mail. Sem a variável `SPRING_CONFIG_IMPORT`, o ambiente volta ao Mailpit e à conta fictícia padrão.

O fluxo real pelo Mailgun sandbox foi validado manualmente: recebimento do e-mail, redefinição e login com a nova senha.

### Envio real com OneSignal

A recuperação e o aviso de senha alterada passam pelo `EmailService`. Em desenvolvimento, `HIPATEC_MAIL_PROVIDER=smtp` usa o `JavaMailSender` existente; com `onesignal`, o backend chama `POST https://api.onesignal.com/notifications?c=email` com o cliente HTTP do Spring, sem SDK adicional. Não há fallback automático para SMTP em caso de erro do OneSignal.

Configure o canal de e-mail no painel do OneSignal, verifique o domínio no DNS e autorize o remetente. Defina estas variáveis **no processo do backend**, substituindo os exemplos:

```dotenv
HIPATEC_MAIL_PROVIDER=onesignal
ONESIGNAL_APP_ID=UUID-do-aplicativo
ONESIGNAL_API_KEY=chave-privada-do-aplicativo
HIPATEC_MAIL_FROM=nao-responda@seu-dominio.com
HIPATEC_FRONTEND_ORIGIN=https://app.seu-dominio.com
HIPATEC_RESET_URL=https://app.seu-dominio.com/redefinir-senha
```

O Spring não carrega um arquivo `.env` automaticamente: configure as variáveis na IDE, terminal ou serviço de hospedagem. Não coloque a chave no frontend nem em arquivos versionados. No modo OneSignal, não são necessárias credenciais SMTP. Valores ausentes de App ID/chave e provedor desconhecido impedem a inicialização.

O destinatário é o e-mail da conta encontrada no banco, enviado por `email_to`. O OneSignal cria uma assinatura de e-mail se ela ainda não existir. Usamos `include_unsubscribed=true` para estas mensagens de conta e `disable_email_click_tracking=true` para preservar o link com token. O link no HTML é clicável; o token continua com validade de 30 minutos e uso único.

Cada recuperação concluída envia **dois e-mails**: link e confirmação da alteração, ambos contam na cota. HTTP 200 sem ID de mensagem é tratado como falha; erros HTTP e de conexão também. A mensagem de erro não expõe o corpo devolvido pelo provedor. Aceitação pela API não garante chegada à caixa de entrada: a validação final precisa ser feita com domínio, chave e uma conta de teste autorizada da equipe.

Documentação: [API de e-mail](https://documentation.onesignal.com/reference/email), [configuração do canal](https://documentation.onesignal.com/docs/en/email-setup).

Para testar o contrato HTTP do OneSignal sem credenciais nem envio externo, além do fluxo de recuperação:

```bash
# Dentro de hipatec/, a pasta que contém gradlew:
HIPATEC_TEST_SMTP_PORT=1025 bash gradlew test --tests '*RecuperacaoSenhaTests' --tests '*EmailServiceTests'
```

O teste com Mailpit usa H2 descartável e destinatário fictício; seu link não corresponde ao banco da API em execução. Para testar pelo navegador, siga o fluxo manual acima com uma conta no banco de desenvolvimento. O SQL Server e o Firebase continuam sendo pré-requisitos da aplicação completa.

## Arquivos alterados nesta funcionalidade

| Arquivos | Mudança |
| --- | --- |
| `build.gradle`, `application.properties` | Spring Mail, SMTP e URL de recuperação por variáveis de ambiente |
| `config/RecuperacaoSenhaConfig.java` | BCrypt e executor limitado para envio assíncrono |
| `controller/RecuperacaoSenhaController.java` | Dois endpoints, validação e limite de tentativas |
| `service/RecuperacaoSenhaService.java` | Geração, envio, expiração e consumo transacional do token |
| `model/RecuperacaoSenha.java`, `repository/RecuperacaoSenhaRepository.java` | Persistência do hash e bloqueio contra uso simultâneo |
| `repository/EstudanteRepository.java`, `repository/MentoraRepository.java` | Consulta de contas por e-mail |
| `service/EstudanteService.java`, `service/MentoraService.java` | Login das senhas redefinidas; caminho legado preservado |
| `model/Estudante.java`, `model/Mentora.java` | Senha omitida nas respostas JSON |
| `config/FirebaseConfig.java` | Usa `FirebaseOptions.builder()` em lugar do construtor descontinuado |
| `controller/PerfilController.java`, `service/PerfilService.java` | Remove injeção duplicada sem uso e import redundante apontados pelo editor |
| `src/test/java/com/solucao/hipatec/RecuperacaoSenhaTests.java` | Casos de segurança, concorrência, login, HTTP e SMTP local opcional |
| `README.md` | Contrato, configuração, testes e limites conhecidos |

Os caminhos de classes acima são relativos a `hipatec/src/main/java/com/solucao/hipatec/`. **A recuperação não cria sessões autenticadas nem implementa revogação das sessões existentes:** o projeto ainda usa IDs no navegador e `permitAll()`, conforme as limitações abaixo.

Referências técnicas: [recuperação de senha — OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html), [envio de e-mail — Spring Boot](https://docs.spring.io/spring-boot/reference/io/email.html) e [armazenamento de senhas — Spring Security](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).

## Limitações que afetam o piloto

- `SecurityConfig` usa `permitAll()` e desativa CSRF. Não há verificação de sessão/token ou aprovação administrativa implementada nessa configuração. O ID retornado pelo login não é um token de autenticação.
- O campo `senha` agora aceita escrita, mas não é serializado nas respostas de estudante/mentora. O cadastro legado ainda salva a senha como recebida; a adoção de hash em todo o cadastro e a migração das contas antigas continuam pendentes.
- Login de contas legadas depende de procedimentos ausentes do repositório. Após a recuperação, o hash `{bcrypt}` é conferido no Java, sem passar a nova senha ao procedimento. O contrato legado de login ainda recebe credenciais em parâmetros da URL e precisa ser migrado para corpo de requisição na revisão da autenticação.
- A mentoria possui título, descrição, progresso e ID de mentora; ainda não modela agenda, vagas, inscrições, histórico e gravação. O campo atual `progresso` pertence à mentoria e não representa conclusão individual de uma estudante.
- Operações de escrita no Firebase usam chamadas assíncronas sem aguardar confirmação. Um HTTP de sucesso atual não é evidência suficiente da persistência; verifique também o armazenamento em testes de integração.
- Não há implementação de denúncias e remoção administrativa nos controllers examinados.

Para **27/11/2026**, a meta é MVP funcional, testes com usuárias e Relatório Final. Foi restaurada a classificação original do MoSCoW de 31/07/2026:

- **Must Have:** autenticação segura; mentorias com criação, visualização e inscrição; cursos básicos; perfis personalizáveis; feed básico; moderação básica.
- **Should Have:** curtidas, comentários e compartilhamento; denúncias; painel básico de vagas afirmativas; exclusão de conta.
- **Could Have:** onboarding, avaliações, habilidades no perfil, notificações e downloads offline.
- **Won't Have:** currículo automático, temas/personalização da experiência, chat individual/em grupo em tempo real e recomendação avançada com ML.

O código ainda não implementa toda essa meta. O planejamento semanal está sendo revisto para a capacidade informada de 6 horas de equipe por semana e depende de validação coletiva de viabilidade.

## Organização e colaboração

Dentro de `hipatec/src/main/java/com/solucao/hipatec/`:

- `controller/`: rotas HTTP e entrada/saída.
- `service/`: lógica de aplicação e integrações.
- `repository/`: acesso JPA ao SQL Server.
- `model/`: entidades e estruturas armazenadas.
- `dto/`: dados de perfil e comentários.
- `config/`: segurança, Firebase e Cloudinary.

Nathalia é a representante do grupo e cuida do backend; Thiago do frontend; Lucca auxilia na organização, documentação, testes e apoio técnico, sem decidir sozinho o escopo ou as prioridades. As decisões finais devem ser validadas pelo grupo. Confirmar com a equipe a responsabilidade pelo banco e disponibilizar seus scripts versionados. Registre alterações pequenas em branches, explicando no PR pré-requisitos e resultados de teste. Não versione JSONs de conta de serviço, senhas, chaves privadas ou dados reais de participantes.
