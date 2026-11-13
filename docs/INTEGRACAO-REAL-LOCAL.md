# Validação local de funcionalidades — 13/11/2026

Escopo implementado: feed e interações persistidos no Firebase RTDB SDK; denúncias em fila JPA; criação/listagem de cursos e mentorias; perfil/configurações. Revisão e Git ficam com Hermes. Não executar estes comandos contra produção.

## Ambiente e repetição

Use três terminais, todos no repositório backend. Não há sudo, Docker privilegiado nem leitura de `.env`. Pré-requisitos disponíveis nesta máquina: Docker, JDK17, JDK21 (emulador), Python3 e Node22. Dependências/SDK existentes e lockfiles preservados. O jar oficial RTDB4.11.2 foi obtido por firebase-tools instalado apenas no scratch; não é substituído por mock.

```sh
export HIPATEC_TEST_SCRATCH=/home/rosa/.hermes/cache/scratch/hipatec-real-20261113-161513
bash scripts/integracao-real-local.sh init
# Só iniciar emulator/api se suas portas ainda não estiverem ocupadas pela execução entregue.
bash scripts/integracao-real-local.sh emulator
bash scripts/integracao-real-local.sh api
# Em outro terminal, após API pronta:
bash scripts/integracao-real-local.sh test
bash scripts/integracao-real-local.sh http
```

Portas exclusivamente loopback: PostgreSQL18432 (`hipatec-real-pg-161513`, postgres18.3), RTDB18900 (namespace `demo-hipatec-real`), API18086, SMTP18125/Mailpit18126. Banco de testes usa contas sintéticas geradas em runtime e trust apenas no container local, sem senha registrada. Não serve de configuração de produção. Limite vigente de login:20 tentativas/IP por15min; uma rodada HTTP usa menos que isso. Repetições de UI+HTTP podem receber429: aguardar a janela ou reiniciar **somente a API isolada identificada no manifesto**, sem remover o limitador.

`init` aplica SQL apenas se não houver tabelas. A fixture `integracao-real-legado.sql` é um schema anterior **sintético**, não baseline inferido de Neon. Migrações SQL manuais:20261110, evento20261113, curso/mentoria, denúncia e evidência. São aditivas; conferir schema e aplicar uma única vez, em transação, no mecanismo manual vigente. Reversão da aplicação preserva tabelas/colunas/dados. Nenhuma migração foi aplicada remotamente.

## Frontend e navegador

No frontend, com a mesma variável de scratch:

```sh
npm run build
npm run lint
npx ng build --configuration development --output-path="$HIPATEC_TEST_SCRATCH/ui"
# O bundle de teste deve apontar para18086, sem alterar environments/fontes:
node scripts/prepare-real-local.cjs
CHROME_BIN=/usr/bin/google-chrome npm test -- --watch=false --browsers=ChromeHeadless --karma-config="$HIPATEC_TEST_SCRATCH/karma.cjs"
node scripts/serve-real-local.cjs
# Em outro terminal, apenas se não houver Chrome isolado já iniciado:
google-chrome --headless=new --no-sandbox --disable-gpu --remote-debugging-port=0 --user-data-dir="$HIPATEC_TEST_SCRATCH/chrome" about:blank
node scripts/test-real-social.cjs
node scripts/test-real-profile.cjs
```

Prévia: http://localhost:14286. Os scripts usam CDP no perfil **próprio** do scratch, jamais9222, e bloqueiam hosts externos. `test.png` é uma imagem sintética1px. Fixtures criadas via API real; somente aprovação de mentora e foto propositalmente quebrada usam SQL local identificado. Falha503 é injetada explicitamente. Matriz de ações, respostas e capturas: `browser-results.json` e `browser-extra-results.json` no scratch. Teclado é CDP desktop; não equivale a dispositivo físico/teclado virtual/leitor de tela.

## Contratos e limites

- Identidade de posts/comments/reposts/curso/mentoria vem da sessão, nunca do browser. CSRF nas mutações; dono para editar/excluir; MENTORA aprovada para criar conteúdos. GET de feed/perfis preserva a visibilidade pública já vigente; não declarar esses endpoints privados. Endpoints novos de conteúdo/denúncia exigem sessão.
- `requisicaoId` UUID estabiliza retries. Cursos/mentorias: lock JPA por perfil+unique dona/UUID; posts/comments: transação SDK. Edição exige `versao`; conflito409 preserva rascunho. Reposts locais legados são histórico identificado, não migração automática.
- RTDB: transações no nó existente `posts` tornam exclusão e interações atômicas; tombstone impede recriação por retry. Primeira mutação incorpora likes/comments/reposts legados e marca `interacoesMigradas`. **Interromper escritores antigos antes do rollout**, sem escrita simultânea no formato anterior. Ramos antigos são preservados, ignorados após migração; limpeza futura não faz parte desta entrega. Transação global é adequada ao volume do piloto, mas serializa escritores e lê o conjunto; sem benchmark de carga/múltiplas instâncias. Leituras do SDK podem refletir brevemente cache antes de propagação de uma escrita externa.
- `database/firebase.rules.json` nega acesso direto de clientes; testado no emulador, AdminSDK mantém acesso. Regras remotas não foram inspecionadas/deployadas: confirmar/aplicar por operação autorizada antes de expor a aplicação. Não confiar em autoria de localStorage.
- Upload nesta integração é **adapter de teste local**, com a validação real de PNG/JPEG/5MB/dimensões. Cloudinary remoto não foi acionado. Não há transação distribuída entre upload e RTDB/JPA: falha após upload pode deixar arquivo sem referência; retry de conteúdo não duplica publicação, mas uploads simultâneos podem deixar imagem extra no provedor. Limitação operacional explícita.
- Denúncias: `denuncia` mantém protocolo, alvo, pessoa denunciante, motivo, statusPENDENTE e evidência/autoria do servidor. Encaminhamento é à fila manual de moderação vigente, sem inventar admin/mentora moderadora. Consulta restrita do operador: `SELECT id,post_id,comentario_id,motivo,evidencia,autor_perfil_id,criado_em FROM denuncia WHERE status='PENDENTE' ORDER BY criado_em,id;`. Não expor essa consulta à UI comum. Painel/admin/remoção moderadora não implementados neste recorte.
- Criação de cursos/mentorias não homologa inscrição, aulas, avaliações, certificados ou progresso individual. Permanecem fora deste recorte, identificados como indisponíveis na UI quando expostos.

## Evidência

Scratch acima contém logs finais, hashes/snapshot inicial, diffs exclusivos e manifesto de processos. Testes HTTP reais: `conteudos-http.py`, `social-http.py`, `perfil-http.py`, `rtdb-migration-http.py` e `rtdb-outage-http.py` em `hipatec/src/test/resources`. Migração foi repetida em `hipatec_migration_check` com registro anterior preservado; PostgreSQL foi reiniciado e dump de dados normalizado teve SHA256 idêntico. Sessões novas e restart da API consultaram dados persistidos. SMTP da suíte usa receptor sintético local, sem envio externo. Não há evidência de Neon/Firebase/Cloudinary/SMTP remoto.

Rastreio: [frontend21](https://github.com/hipatec/hipatec-app/issues/21), [feedback36](https://github.com/hipatec/hipatec-app/issues/36), [settings backend10](https://github.com/hipatec/hipatec/issues/10), [eventos backend17](https://github.com/hipatec/hipatec/issues/17). Hermes fará associação/criação das issues faltantes e revisão; nenhuma issue foi encerrada.

Falha real do provedor também exercitada: `HIPATEC_RTDB_TEST_PID=<PID isolado18900> python3 hipatec/src/test/resources/rtdb-outage-http.py`. O harness valida o comando/porta do PID, pausa o emulador e sempre o retoma em `finally`; HTTP não anuncia sucesso sem ACK, timeout é seguido de retry com mesmo UUID e nova sessão sem duplicação. Não executar simultaneamente com outras suítes. Identificador atual no manifesto scratch. Log: `rtdb-outage.log`.
