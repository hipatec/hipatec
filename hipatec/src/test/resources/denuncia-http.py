"""Regressão de denúncia: API corretiva 18087, RTDB e PostgreSQL locais existentes."""
import sys
sys.dont_write_bytecode = True
import importlib.util, pathlib, uuid, secrets, json, subprocess, urllib.request, hashlib, base64
from concurrent.futures import ThreadPoolExecutor
spec=importlib.util.spec_from_file_location('client',pathlib.Path(__file__).with_name('conteudos-http.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
m.BASE='http://127.0.0.1:18087'
suffix=uuid.uuid4().hex
password=secrets.token_urlsafe(20)+'aA1!'
def login(email):
    c=m.Client();c.call('POST','/auth/login',{'role':'estudantes','email':email,'senha':password});return c
users=[]
for i in range(3):
    email=f'denuncia-{suffix}-{i}@example.test'
    c=m.Client();c.call('POST','/estudantes',{'nome':'Denúncia sintética','usuario':f'd{suffix[:12]}{i}','email':email,'senha':password,'dataNascimento':'1995-01-01'})
    users.append((login(email),email))
a,email=users[0]; b,_=users[1]; other,_=users[2]
# Upload real pelo endpoint multipart; imagem sintética PNG 1x1.
png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aE1cAAAAASUVORK5CYII=')
request_id=str(uuid.uuid4());boundary='boundary'+suffix
body=b''
for name,value in [('conteudo','Imagem denunciada '+suffix),('requisicaoId',request_id)]:
    body+=f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode()
body+=f'--{boundary}\r\nContent-Disposition: form-data; name="imagem"; filename="test.png"\r\nContent-Type: image/png\r\n\r\n'.encode()+png+f'\r\n--{boundary}--\r\n'.encode()
b.call('GET','/auth/csrf')
headers={'Content-Type':'multipart/form-data; boundary='+boundary,'X-XSRF-TOKEN':next(c.value for c in b.jar if c.name=='XSRF-TOKEN')}
with b.http.open(urllib.request.Request(m.BASE+'/posts',data=body,headers=headers),timeout=30) as r: assert r.status==200
original=next(p for p in b.call('GET','/posts') if p['conteudo']=='Imagem denunciada '+suffix)
assert original['imagemHash']==hashlib.sha256(png).hexdigest()
id=original['id'];route='/posts/'+id
b.call('POST',route+'/repost-comentario',{'conteudo':'Contexto '+suffix,'requisicaoId':str(uuid.uuid4()),'repostOriginal':{'id':'forjado','conteudo':'forjado'}})
quote=next(p for p in b.call('GET','/posts') if p['conteudo']=='Contexto '+suffix)
b.call('POST',route+'/comment',{'texto':'Comentário denunciado','requisicaoId':str(uuid.uuid4())})
comment=b.call('GET',route+'/comments')[0]
post_body={'motivo':'spam'};comment_body={'motivo':'assedio','comentarioId':comment['id']}
# Descarta a resposta e reconcilia por retry em outra sessão.
a.call('POST',route+'/denuncias',post_body,expected=201)
report=login(email).call('POST',route+'/denuncias',post_body,expected=201)
comment_report=a.call('POST',route+'/denuncias',comment_body,expected=201)
quote_route='/posts/'+quote['id']
clients=[login(email),login(email)]
with ThreadPoolExecutor(2) as pool:
    reports=list(pool.map(lambda c:c.call('POST',quote_route+'/denuncias',post_body,expected=201),clients))
assert reports[0]==reports[1]
# Outro perfil possui seu próprio protocolo, nunca o da primeira denunciante.
other_report=other.call('POST',quote_route+'/denuncias',post_body,expected=201)
assert other_report!=reports[0]
b.call('DELETE',route);b.call('DELETE',quote_route)
fresh=login(email)
assert fresh.call('POST',route+'/denuncias',post_body,expected=201)==report
assert fresh.call('POST',route+'/denuncias',comment_body,expected=201)==comment_report
assert fresh.call('POST',quote_route+'/denuncias',post_body,expected=201)==reports[0]
assert other.call('POST',quote_route+'/denuncias',post_body,expected=201)==other_report
for payload in [post_body,comment_body]:
    fresh.call('POST',route+'/denuncias',{**payload,'motivo':'conteudo-inadequado'},expected=409)
    fresh.call('POST',route+'/denuncias',{**payload,'motivo':'invalido'},expected=400)
    denied=other.call('POST',route+'/denuncias',payload,expected=404)
    assert 'protocolo' not in (denied or {})
fresh.call('POST',route+'/denuncias',{'motivo':'spam','comentarioId':'outro'},expected=404)
ids=[int(r['protocolo']) for r in [report,comment_report,reports[0],other_report]]
sql="SELECT json_agg(d) FROM (SELECT id,post_id,comentario_id,motivo,evidencia,autor_perfil_id FROM denuncia WHERE id IN ("+','.join(map(str,ids))+")) d"
rows=json.loads(subprocess.check_output(['docker','exec','hipatec-real-pg-161513','psql','-U','postgres','-d','hipatec_test','-tAc',sql]))
assert len(rows)==4
for row in rows:
    evidence=json.loads(row['evidencia'])
    assert row['autor_perfil_id']==original['autorId']
    assert len(row['evidencia'])<=5000
    assert 'senha' not in row['evidencia'] and 'forjado' not in row['evidencia']
    visual=evidence['original'] if row['post_id']==quote['id'] else evidence['publicacao']
    assert visual['id']==id and visual['imagemUrl']==original['imagemUrl'] and visual['imagemHash']==original['imagemHash']
    assert visual['conteudo']==original['conteudo']
    if row['comentario_id']: assert evidence['comentario']['texto']==comment['texto']
print('PASS HTTP + SDK RTDB + PostgreSQL: upload imagem, snapshot original derivado servidor, denúncia de comentário, exclusão e SQL readback integral')
print('PASS retries: resposta descartada, tombstone, novo login, concorrência, outro perfil, motivo inválido/conflitante e alvo diferente')
print(json.dumps({'protocolos':ids,'evidencias':rows},ensure_ascii=False))
