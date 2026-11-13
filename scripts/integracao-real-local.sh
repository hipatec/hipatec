#!/usr/bin/env bash
# Somente infraestrutura local descartável. Nunca carrega .env/application.properties.
set -euo pipefail
: "${HIPATEC_TEST_SCRATCH:?Defina pasta sob $HOME/.hermes/cache/scratch}"
case "$(realpath -m "$HIPATEC_TEST_SCRATCH")" in "$HOME/.hermes/cache/scratch/"*) ;; *) echo 'Scratch fora da raiz permitida' >&2; exit 2;; esac
repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
export PYTHONDONTWRITEBYTECODE=1
export FIREBASE_DATABASE_EMULATOR_HOST=127.0.0.1:18900 HIPATEC_TEST_SMTP_PORT=18125
export JAVA_HOME="${HIPATEC_JDK17:-$HOME/.sdkman/candidates/java/17.0.20.1-amzn}"
pg=hipatec-real-pg-161513
case "${1:-help}" in
  init)
    mkdir -p "$HIPATEC_TEST_SCRATCH/pgdata"
    if ! docker inspect "$pg" >/dev/null 2>&1; then
      docker run -d --name "$pg" -p 127.0.0.1:18432:5432 -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_DB=hipatec_test -v "$HIPATEC_TEST_SCRATCH/pgdata:/var/lib/postgresql" postgres:18.3
    fi
    until docker exec "$pg" pg_isready -U postgres -d hipatec_test >/dev/null; do sleep 1; done
    tables=$(docker exec "$pg" psql -U postgres -d hipatec_test -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'")
    if [ "$tables" = 0 ]; then
      docker exec -i "$pg" psql -U postgres -d hipatec_test -v ON_ERROR_STOP=1 < "$repo_dir/hipatec/src/test/resources/integracao-real-legado.sql"
      for migration in "$repo_dir"/database/migrations/*.sql; do
        docker exec -i "$pg" psql -U postgres -d hipatec_test -v ON_ERROR_STOP=1 < "$migration"
      done
    else echo 'Banco já contém tabelas: preservado; não reaplicando SQL.'; fi
    if ! docker inspect hipatec-real-mail-161513 >/dev/null 2>&1; then
      docker run -d --name hipatec-real-mail-161513 -p 127.0.0.1:18125:1025 -p 127.0.0.1:18126:8025 axllent/mailpit:v1.31.2
    fi
    ;;
  emulator)
    exec "${HIPATEC_JDK21:-$HOME/.sdkman/candidates/java/21.0.11-amzn}/bin/java" -jar "$HIPATEC_TEST_SCRATCH/emulators/firebase-database-emulator-v4.11.2.jar" --host 127.0.0.1 --port 18900
    ;;
  api) cd "$repo_dir/hipatec"; exec bash gradlew --offline --no-daemon integracaoRealLocal ;;
  test) cd "$repo_dir/hipatec"; exec bash gradlew --offline --no-daemon build ;;
  http)
    for suite in conteudos-http social-http perfil-http rtdb-migration-http; do
      python3 "$repo_dir/hipatec/src/test/resources/$suite.py"
    done
    ;;
  *) echo 'Uso: integracao-real-local.sh init|emulator|api|test|http'; exit 2 ;;
esac
