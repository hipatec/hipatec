"""Migração incremental de interações legadas em emulador real; namespace demo somente."""
import sys; sys.dont_write_bytecode = True
import importlib.util,pathlib,json,urllib.request,urllib.error,uuid,secrets,time
spec=importlib.util.spec_from_file_location('client',pathlib.Path(__file__).with_name('conteudos-http.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
def rtdb(method,path,data=None):
    req=urllib.request.Request('http://127.0.0.1:18900/'+path+'.json?ns=demo-hipatec-real',method=method,
        headers={'Content-Type':'application/json','Authorization':'Bearer owner'},data=json.dumps(data).encode() if data is not None else None)
    with urllib.request.urlopen(req,timeout=15) as r:return json.load(r)
suffix=uuid.uuid4().hex[:10];client=m.Client();password=secrets.token_urlsafe(18)+'aA1!';email=suffix+'@example.test'
client.call('POST','/estudantes',{'nome':'Migração sintética','usuario':'migracao'+suffix,'email':email,'senha':password,'dataNascimento':'1995-01-01'})
session=client.call('POST','/auth/login',{'role':'estudantes','email':email,'senha':password})
profile=client.call('GET','/perfil/estudantes/'+str(session['id']))['id'];id='legacy-'+suffix
post={'id':id,'autorId':profile,'conteudo':'Anterior à migração','dataCriacao':1}
comment={'id':'comentario-legado','autorId':profile,'autorNome':'Autora sintética','texto':'Comentário anterior','dataCriacao':1}
rtdb('PATCH','',{'posts/'+id:post,'likes/'+id:{str(profile):True},'comments/'+id:{'comentario-legado':comment},'reposts/'+id:{str(profile):{'usuarioId':profile,'dataCriacao':1}}})
# A fixture entra por outro cliente RTDB: aguardar a propagação ao listener SDK antes do cenário.
for tentativa in range(30):
    if any(p['id']==id for p in client.call('GET','/posts')): break
    time.sleep(0.1)
else: raise AssertionError('Fixture legada não chegou ao SDK')
base='/posts/'+id
before=client.call('GET',base+'/interacoes');assert before['likesCount']==1 and before['commentsCount']==1 and before['repostsCount']==1
client.call('PUT',base,{'conteudo':'Após migração','versao':0})
after=client.call('GET',base+'/interacoes');assert before==after
assert client.call('GET',base+'/comments')[0]['texto']=='Comentário anterior'
node=rtdb('GET','posts/'+id);assert node['interacoesMigradas'] and node['likes'][str(profile)] and node['comments']['comentario-legado']['texto']=='Comentário anterior'
client.call('POST',base+'/comment',{'texto':'Novo comentário','requisicaoId':str(uuid.uuid4())})
assert client.call('GET',base+'/interacoes')['commentsCount']==2
client.call('DELETE',base);client.call('GET',base+'/comments',expected=404)
# Regras de cliente: backend Admin mantém acesso; cliente anônimo não lê nem escreve RTDB.
rtdb('PUT','.settings/rules',json.loads(pathlib.Path(__file__).parents[4].joinpath('database/firebase.rules.json').read_text()))
try: urllib.request.urlopen('http://127.0.0.1:18900/posts.json?ns=demo-hipatec-real')
except urllib.error.HTTPError as e: assert e.code in (401,403)
else: raise AssertionError('RTDB aberto ao cliente anônimo')
assert isinstance(client.call('GET','/posts'),list)
print('PASS migração RTDB: likes/comentários/reposts preservados, exclusão não reexpõe legado; regras negam cliente direto')
