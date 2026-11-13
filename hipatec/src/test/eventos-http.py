"""HTTP real exclusivamente localhost:18081, contas sintéticas; não imprime credenciais/cookies."""
import json, urllib.request, urllib.error, http.cookiejar, uuid, sys
BASE = "http://127.0.0.1:18081"
class Cliente:
    def __init__(self):
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    def req(self, method, path, body=None, csrf=True):
        headers = {"Content-Type": "application/json"}
        if method != "GET" and csrf:
            _, token = self.req("GET", "/auth/csrf")
            headers[token["headerName"]] = token["token"]
        try:
            with self.http.open(urllib.request.Request(BASE+path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)) as r:
                raw=r.read(); return r.status, json.loads(raw) if raw else None
        except urllib.error.HTTPError as e:
            return e.code, None
    def login(self, nome, role="estudantes"):
        assert self.req("POST", "/auth/login", {"email":nome+"@example.test", "senha":"TesteLocal123!", "role":role})[0] == 200
        return self
aluna=Cliente().login("aluna"); outra=Cliente().login("outra"); mentora=Cliente().login("mentora", "mentoras")
consulta="/eventos?de=2026-12-01&ate=2026-12-31"
if "--verificar" in sys.argv:
    status,eventos=aluna.req("GET",consulta)
    assert status==200 and any(e["titulo"]=="HTTP privado persistente" for e in eventos)
    assert not any(e["titulo"]=="HTTP privado persistente" for e in outra.req("GET",consulta)[1])
    print("PASS HTTP nova sessão após restart: privado permanece e não vaza")
    sys.exit(0)
body={"titulo":"HTTP privado persistente","data":"2026-12-01","horario":None,"local":"Sala sintética","descricao":"Teste local","visibilidade":"PRIVADO","requisicaoId":str(uuid.uuid4()),"proprietarioPerfilId":99999}
status,event=aluna.req("POST","/eventos",body); assert status==201
assert event["data"]==body["data"] and event["horario"] is None and event["fuso"]=="America/Sao_Paulo"
assert aluna.req("POST","/eventos",body)[1]["id"]==event["id"]
assert aluna.req("GET","/eventos/"+str(event["id"]))[0]==200
assert outra.req("GET","/eventos/"+str(event["id"]))[0]==404
assert Cliente().req("GET",consulta)[0]==401
assert aluna.req("POST","/eventos",body,csrf=False)[0]==403
assert aluna.req("POST","/eventos",{**body,"visibilidade":"PUBLICO"})[0]==403
assert aluna.req("POST","/eventos",{**body,"data":"2026-02-30"})[0]==400
assert aluna.req("POST","/eventos",{**body,"titulo":"Conflito"})[0]==409
assert mentora.req("POST","/eventos",{**body,"requisicaoId":str(uuid.uuid4()),"titulo":"HTTP público","horario":"14:30","visibilidade":"PUBLICO"})[0]==201
assert any(e["titulo"]=="HTTP público" and e["horario"]=="14:30" for e in outra.req("GET",consulta)[1])
nova=Cliente().login("aluna"); assert nova.req("GET","/eventos/"+str(event["id"]))[0]==200
assert Cliente().req("POST","/auth/login",{"email":"pendente@example.test","senha":"TesteLocal123!","role":"mentoras"})[0]==403
# Mesmo identificador, duas sessões/processamentos concorrentes: somente uma linha.
from concurrent.futures import ThreadPoolExecutor
clientes=[Cliente().login("aluna"),Cliente().login("aluna")]
concorrente={**body,"titulo":"HTTP concorrência","requisicaoId":str(uuid.uuid4())}
with ThreadPoolExecutor(max_workers=2) as pool:
    resultados=list(pool.map(lambda c:c.req("POST","/eventos",concorrente),clientes))
assert all(status==201 for status,_ in resultados)
assert resultados[0][1]["id"]==resultados[1][1]["id"]
print("PASS HTTP concorrência: duas sessões, uma gravação")
print("PASS HTTP criar/recuperar/listar/nova sessão/dono/outro/visitante/papéis/CSRF/inválido/retry/conflito/fuso")
