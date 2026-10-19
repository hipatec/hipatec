# Sprint 2: autenticação e perfis

Estudantes entram após o cadastro. Mentoras precisam de aprovação manual da equipe (decisão de 19/10/2026). Não há nova tela administrativa nem aprovação disponível pela API pública.

## Preparação do banco e aprovação

Antes de atualizar a API, revisar e executar `hipatec/sql/sprint2-autenticacao.sql` no SQL Server de desenvolvimento com backup. O script interrompe se encontrar e-mails duplicados; não remove nem escolhe contas automaticamente. Contas de mentoras existentes também começam pendentes, até a equipe confirmar cada aprovação.

A pessoa responsável pelo banco confere a identidade e o ID da mentora. Em uma conexão administrativa restrita, usa uma transação para atualizar **somente o ID confirmado**:

```sql
BEGIN TRANSACTION;
DECLARE @id int = NULL; -- substituir pelo ID conferido; NULL não aprova ninguém
UPDATE mentora SET aprovada = 1 WHERE id = @id;
SELECT id, nome, email, aprovada FROM mentora WHERE id = @id;
-- Após conferir exatamente uma conta, executar COMMIT; caso contrário, ROLLBACK.
ROLLBACK;
```

Para revogar, alterar `aprovada` para 0. A próxima requisição encerra sessões da mentora. Não distribuir acesso ao banco às usuárias.

Cadastros novos usam BCrypt (12). Senhas antigas são migradas para hash somente depois que o procedimento SQL legado confirma as credenciais e o ID da mesma conta. Os procedimentos `login_estudante`/`login_mentora` continuam necessários para contas legadas; sua definição ainda não está versionada. Recuperar a senha também produz hash. Não aplicar migração de senhas em massa sem conhecer o esquema real.

## Contrato de sessão

- `GET /auth/csrf`: obter `token` e `headerName` antes de cada operação de escrita.
- `POST /auth/login`: JSON `{ "role": "estudantes", "email": "...", "senha": "..." }`; resposta somente `{id, role}`. Credenciais não vão na URL.
- `GET /auth/session`: identidade autenticada ou 401.
- `POST /auth/logout`: invalida sessão e cookie; exige CSRF.
- Login antigo em `/estudantes/login` e `/mentoras/login` foi removido. Atualizar o frontend junto com o backend.

Cookie HttpOnly, SameSite=Lax, expiração por 30 minutos de inatividade. Secure é padrão; no desenvolvimento HTTP, definir explicitamente `HIPATEC_SESSION_SECURE=false`. O ambiente descartável de testes já usa HTTP local. Produção exige HTTPS e frontend/API no mesmo site; não liberar CORS com `*`. `HIPATEC_FRONTEND_ORIGIN` configura a origem permitida. Alteração de senha invalida sessões anteriores na próxima requisição.

O navegador conserva apenas cache de apresentação; alterar `userId`, `userRole` ou `perfil` não concede acesso. Rotas de perfil/mentorias consultam a sessão. API restringe perfil à titular e criação de mentoria a mentoras aprovadas; autoria de posts e interações vem do servidor. Feed básico e perfis públicos usados para nomes de autoras continuam legíveis sem login.

## Perfis

A consulta individual e a edição exigem a própria conta autenticada. Nome e usuário têm limite de 100 caracteres; biografia, 255. Imagens aceitas: PNG/JPEG, até 5 MB e 4096 pixels por dimensão. O frontend usa o ID do perfil devolvido pela API mesmo sem foto, sem confundir ID da conta com ID do perfil.

## Verificação e limites

`bash gradlew test` dentro de `hipatec/` usa H2 nos testes de sessão/cadastro/perfis e isola Firebase/Cloudinary no teste de contexto. `SessaoTests` cobre acesso anônimo, CSRF, logout, isolamento de perfis, autoria, aprovação/revogação e troca de senha. O teste opcional SMTP continua dependendo de Mailpit e `HIPATEC_TEST_SMTP_PORT`.

As verificações locais não equivalem a validar o SQL Server da equipe, entrega real de e-mail ou upload na conta Cloudinary. O limitador de login usa memória de uma instância (20 tentativas por IP/15 min); ao escalar, mover o limite para armazenamento compartilhado. Sessões também ficam na instância e se encerram ao reiniciar a API. Não ativar balanceamento sem armazenamento de sessão compartilhado.

Referências: [persistência de sessão no Spring Security](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/session-management.html) e [CSRF](https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/csrf.html).

## Conferência no navegador sem serviços externos

Dentro de `hipatec/`, executar `bash gradlew sprint2Local`; no frontend, `npm start -- --port 8100`. Usar contas fictícias `aluna@example.test`, `outra@example.test`, `mentora@example.test` (aprovada) e `pendente@example.test` (mentora pendente), senha `TesteLocal123!`.

O ambiente executa sessão, cadastro e edição textual reais no H2 descartável. Feed e Cloudinary são substituídos; upload retorna indisponível. Não usar para demonstrar integração externa ou recuperação de senha (para isso, usar `recuperacaoLocal` com Mailpit). As classes ficam em `src/test` e não entram no JAR.

Roteiro: tentar perfil sem sessão; entrar como aluna; editar nome/biografia; recarregar; sair; tentar perfil novamente; entrar como outra e conferir isolamento; tentar criar mentoria como estudante; conferir recusa de mentora pendente e acesso da mentora aprovada.
