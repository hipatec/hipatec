"""API real exclusivamente em localhost18086; PostgreSQL descartável. Não imprime credenciais."""
import sys; sys.dont_write_bytecode = True
import urllib.request, urllib.error, http.cookiejar, json, uuid, secrets, subprocess
from concurrent.futures import ThreadPoolExecutor
BASE = 'http://127.0.0.1:18086'
class Client:
    def __init__(self):
        self.jar=http.cookiejar.CookieJar()
        self.http=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
    def call(self, method, path, data=None, csrf=True, expected=200):
        if method != 'GET' and csrf: self.call('GET','/auth/csrf')
        headers={'Content-Type':'application/json'}
        if csrf:
            for cookie in self.jar:
                if cookie.name=='XSRF-TOKEN': headers['X-XSRF-TOKEN']=cookie.value
        req=urllib.request.Request(BASE+path,data=json.dumps(data).encode() if data is not None else None,headers=headers,method=method)
        try:
            with self.http.open(req,timeout=20) as response: status=response.status; raw=response.read()
        except urllib.error.HTTPError as error: status=error.code; raw=error.read()
        assert status==expected, (method,path,status,expected)
        return json.loads(raw) if raw else None

def run():
    suffix=uuid.uuid4().hex[:8]; password=secrets.token_urlsafe(18)+'aA1!'
    email='mentora-'+suffix+'@example.test'; student='aluna-'+suffix+'@example.test'
    anon=Client()
    for role,address in [('mentoras',email),('estudantes',student)]:
        anon.call('POST','/'+role,{'nome':'Conta sintética','usuario':role+suffix,'email':address,'senha':password,'dataNascimento':'1995-01-01'})
    # Aprovação vigente manual; somente container explicitamente descartável desta execução.
    subprocess.run(['docker','exec','hipatec-real-pg-161513','psql','-U','postgres','-d','hipatec_test','-v','ON_ERROR_STOP=1','-c',"UPDATE mentora SET aprovada=true WHERE email='"+email+"'"],check=True,stdout=subprocess.DEVNULL)
    def login(role,address):
        c=Client(); session=c.call('POST','/auth/login',{'role':role,'email':address,'senha':password}); return c,session
    mentor,session=login('mentoras',email); pupil,_=login('estudantes',student)
    for route,specific in [('/cursos','instituicao'),('/mentorias','area')]:
        payload={'titulo':'Conteúdo sintético '+suffix,'descricao':'Descrição preservada','formato':'Online',specific:'Hipatec','idMentora':99999,'requisicaoId':str(uuid.uuid4())}
        anon.call('GET',route,expected=401)
        pupil.call('POST',route,payload,expected=403)
        mentor.call('POST',route,payload,csrf=False,expected=403)
        mentor.call('POST',route,{**payload,'titulo':'   '},expected=400)
        sessions=[login('mentoras',email)[0] for _ in range(2)]
        with ThreadPoolExecutor(2) as executor: results=list(executor.map(lambda c:c.call('POST',route,payload,expected=201),sessions))
        assert results[0]['id']==results[1]['id']
        assert results[0]['idMentora']==session['id'] and results[0][specific]=='Hipatec'
        fresh,_=login('mentoras',email)
        rows=fresh.call('GET',route)
        assert len([r for r in rows if r['titulo']==payload['titulo']])==1
        mentor.call('POST',route,{**payload,'descricao':'Conflito'},expected=409)
        print('PASS',route,'papel/dono/CSRF/validação/retry concorrente/nova sessão')
    rows=mentor.call('GET','/mentorias')
    assert any(r['titulo']=='Mentoria anterior sintética' and r['descricao']=='Preservar este registro' for r in rows)
    print('PASS registro anterior preservado após migração PostgreSQL')
if __name__=='__main__': run()
