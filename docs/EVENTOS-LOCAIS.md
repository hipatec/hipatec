# Eventos pessoais — contrato e verificação local

Controle canônico: https://github.com/hipatec/hipatec/issues/17 . Revisão e homologação: Hermes. Não fechar a issue com base apenas nestes comandos.

## Contrato

- POST `/eventos`: `titulo` (1–160 caracteres após trim), `data` (YYYY-MM-DD, 2000-01-01 a 2100-12-31), `horario` (null/vazio ou HH:mm), `local` (até 1000), `descricao` (até 4000), `visibilidade` (PRIVADO/PUBLICO), `requisicaoId` (UUID canônico). Retorna 201 somente após transação confirmada. Strings opcionais ausentes viram vazias.
- GET `/eventos?de=YYYY-MM-DD&ate=YYYY-MM-DD`: período inclusivo, fim não anterior ao início, diferença máxima 366 dias; ordenação data/horário/ID. Sem truncamento silencioso da lista.
- GET `/eventos/{id}`: item visível ou 404, inclusive se existir mas for privado de outra pessoa.
- Proprietário: ID do perfil resolvido por `Acesso.perfil()`, nunca por payload. Conta e perfil não são intercambiáveis. Sessões operacionais: estudante e mentora aprovada; aluno só PRIVADO. Não há novo papel ou privilégio admin.
- PUBLICO: visível a sessões válidas de acesso permitido ao Hipatec. PRIVADO: apenas dono. Visitantes recebem 401; CSRF é obrigatório no POST. Aprovação revogada invalida a sessão pelo filtro existente.
- Resposta omite proprietário e chave de envio; inclui `id,titulo,data,horario,local,descricao,visibilidade,fuso`.
- 400 de validação de domínio contém `campos` para erros inline; 403 para publicação não autorizada; 409 quando chave já gravada é reenviada com conteúdo diferente. Payload extra de dono não é utilizado.

## Data, hora e limites

Data civil armazenada em DATE, horário local opcional em TIME, fuso explícito constante `America/Sao_Paulo`. Sem horário representa dia inteiro: não há instante UTC fictício. Com horário, a API verifica que existe exatamente um offset válido na regra IANA para a data/hora; rejeita horário inexistente/ambíguo em transições históricas. Nada depende do fuso padrão do navegador, JVM ou conexão JDBC. A UI exibe a data civil com formatação UTC ancorada e mostra o horário literal e o nome do fuso. Este fluxo registra um horário pontual, sem fim/duração/recorrência; EventoMentoria e sua regra de intervalo permanecem separados.

Os limites de tamanho seguem unidades UTF-16 do Java/maxlength do formulário. Local/link permanece texto escapado, sem abrir URL arbitrária. O intervalo de consulta é limitado, mas o número de eventos por mês não foi limitado por regra de produto; paginação/cotas exigem incremento caso volume real justifique.

Um UUID é criado por abertura do modal. Botões e fechamento bloqueados durante POST. Lock de perfil na transação e UNIQUE(dono,chave) serializam retries, incluindo sessões concorrentes. Retry com dados iguais devolve a mesma gravação; alteração após resposta incerta pode retornar 409 e exige conferir agenda. Fechar e abrir outro formulário inicia nova intenção, não deduplicação por título/data.

## Migração manual

Arquivo novo: `database/migrations/20261113_create_evento.sql`. Não muda `20261110_add_missing_product_entities.sql`, não modifica EventoMentoria, não fabrica mentoria/proprietário e não executa backfill. Exige `perfil(id)` existente. A migração falha se tabela já existir, para não mascarar drift. Rever schema e aplicar uma única vez sob controle humano no banco de destino autorizado; **nenhuma aplicação em produção/Neon foi executada**.

O mecanismo vigente é SQL manual versionado, sem Flyway/Liquibase. `ddl-auto=none` permanece no principal. Não seguir instrução antiga do README sobre update. Rollback seguro: voltar a aplicação anterior e conservar tabela/dados; não executar DROP, recriação de schema ou limpeza de registros.

## Ambiente persistente de teste

Somente H2 arquivo em scratch, dependência já existente. PostgreSQL local não foi encontrado; isso não homologa o dialeto/ambiente PostgreSQL de produção. O preparador cria um banco **novo**, com schema legado sintético mínimo e contas fictícias, insere evento de mentoria anterior e aplica os bytes do SQL versionado. Recusa banco existente. Não usa ddl-auto na preparação nem na execução. Testes unitários continuam usando bancos descartáveis próprios, distintos desta evidência de durabilidade.

Da pasta `hipatec` que contém Gradle Wrapper, em cópia isolada para não competir com servidores Hermes:

```bash
rtk proxy bash gradlew eventosLocal --offline --no-daemon --args="preparar /home/rosa/.hermes/cache/scratch/SEU-DIRETORIO/db/eventos /CAMINHO/REPO/database/migrations/20261113_create_evento.sql"
rtk proxy bash gradlew eventosLocal --offline --no-daemon --args="servir /home/rosa/.hermes/cache/scratch/SEU-DIRETORIO/db/eventos 18081"
rtk proxy python3 src/test/eventos-http.py
```

Usar JDK17 instalado (`JAVA_HOME=/home/rosa/.sdkman/candidates/java/17.0.20.1-amzn`). A API escuta só 127.0.0.1. O harness HTTP tem destino fixo local 18081, contas sintéticas e não imprime cookies/CSRF/senhas. Não apontar para dados reais. O bootstrap `EventosLocalApplication servir` instala a mesma `SocialLocalFixture` de TEST usada por `Sprint2LocalApplication`: feed sintético com criação, lista por autor, likes/unlikes, comentários e contador de repost por perfil em memória. O post inicial é identificado como fixture no próprio conteúdo, e o bootstrap registra o limite no log. Upload externo retorna indisponibilidade. A prova real de persistência deste ambiente é eventos/sessão/JPA, não Firebase/Cloudinary. Reposts locais não homologam o `user_feed` de produção.

Encerrar **somente o processo isolado iniciado para esta verificação**, então:

```bash
rtk proxy bash gradlew eventosLocal --offline --no-daemon --args="verificar /home/rosa/.hermes/cache/scratch/SEU-DIRETORIO/db/eventos"
rtk proxy bash gradlew eventosLocal --offline --no-daemon --args="servir /home/rosa/.hermes/cache/scratch/SEU-DIRETORIO/db/eventos 18081"
rtk proxy python3 src/test/eventos-http.py --verificar
```

A verificação JDBC em novo processo exige registros anteriores e novos; HTTP exige novo login e isolamento do privado. Sessões antigas não sobrevivem ao restart (mecanismo atual), eventos sobrevivem. Não reiniciar 4200/8080 ou bridge LAN; Hermes coordena esses recursos após revisão.

## UI e revisão Hermes

Frontend desenvolvimento local, origem localhost:8100. O harness CDP no diretório de evidências redireciona transporte 8080 para 18081; não modifica environments/CORS. Não abrir o bundle de produção para testes locais, pois ele aponta ao serviço publicado.

Rotas: `/mentorias`, `/calendario/eventos/novo` (modal sobre painel), `/calendario/eventos/1/editar` (indisponibilidade honesta; não grava localStorage). Testar estudante e mentora, horário vazio/informado, público/privado, falha do POST, falha da releitura, dois cliques, Escape, Cancelar/Descartar e restauração de foco em 390/768/1440. Recarregar e entrar novamente para comprovar consulta real. Não interpretar HTTP200 do fallback SPA como fluxo validado.

Antes do handoff final, Hermes revisa o delta exclusivo e coordena o restart do backend protegido. Esta correção não reiniciou nem alterou o processo protegido. Hermes deve revisar o delta e reiniciar o bootstrap com o mesmo caminho do banco já usado no preview.


## Regressão do feed no bootstrap combinado — 2026-11-13

Causa: a configuração `EventosTests.Config` fornece um `PostService` mock sem handlers; ao trocar o bootstrap, GET `/posts` devolvia `[]`. O helper `src/test/java/com/solucao/hipatec/SocialLocalFixture.java` extrai os handlers existentes de Sprint2 e é instalado pelos dois bootstraps. Não há fixture em `src/main`, mudança de frontend ou criação de contas durante `servir`. O helper usa um perfil existente (prefere mentora); sem perfil, falha explicitamente.

Posts e interações são voláteis. Posts antigos do processo encerrado **não são recuperáveis** por este helper; nenhum restore foi feito ou fabricado. Eventos continuam no H2 arquivo, com `ddl-auto=none`. O contador de publicações do perfil mantém o comportamento anterior de incrementar no create; esse contador persistido não significa durabilidade dos posts. O banco existente do preview não foi aberto nem preparado nesta rodada.

Evidências isoladas: `/home/rosa/.hermes/cache/scratch/hipatec-feed-regressao-20261113/`. `before/` guarda o snapshot anterior; `delta-exclusivo.patch` inclui os arquivos de teste novos e alterados; `red.log` e `red-test-results/` registram a falha esperada (feed com 0 em vez de 1). O teste `EventosLocalTests` inicializa apenas um banco novo exclusivo em scratch e depois executa dois boots de `servir`, sem reset entre eles. Usa controllers/filtros reais via MockMvc, login novo, create/lista por autor, likes idempotentes/unlike, comentários e contador de repost com readback, além de leitura do evento anterior após reabrir o arquivo. A reabertura é de contextos na mesma JVM; não é evidência de Firebase, browser nem recuperação de posts. O schema de teste é criado pelo Hibernate e não homologa a migração SQL.

Para Hermes, após revisar e encerrar por conta própria **somente o backend anterior**, iniciar da cópia isolada testada (ou de sua cópia atualizada com estes quatro arquivos de TEST):

```bash
cd /home/rosa/.hermes/cache/scratch/hipatec-feed-regressao-20261113/work/hipatec
JAVA_HOME=/home/rosa/.sdkman/candidates/java/17.0.20.1-amzn rtk proxy bash gradlew eventosLocal --offline --no-daemon --args="servir CAMINHO_ABSOLUTO_ATUAL_DO_BANCO_SEM_MV_DB 8080"
```

Substituir o marcador pelo **mesmo** caminho scratch já utilizado pelo processo atual, sem extensão `.mv.db`; não usar o banco criado pelo teste. Não executar `preparar`, migração, reset ou exclusão sobre o banco existente. O caminho exato do banco protegido não foi inspecionado nesta rodada. 4200 e bridge LAN permanecem sob controle de Hermes. Após restart, conferir o aviso de fixture e GET `/posts`, e validar os eventos já existentes com nova sessão. Esta rodada não acessou cloud nem autenticou serviços externos.

Validação desta rodada: `gradlew build --offline --no-daemon` com JDK17 concluiu com sucesso; 48 testes, 0 falhas, 0 erros e 1 ignorados. Log: `backend-completo.log`; relatórios: `work/hipatec/build/reports/tests/test/`. Não houve teste de navegador nesta correção.
