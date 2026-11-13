"""Prova HTTP + SDK RTDB emulator + PostgreSQL local; nunca configura provedores remotos."""
import sys; sys.dont_write_bytecode = True
import importlib.util, pathlib, uuid, secrets
from concurrent.futures import ThreadPoolExecutor
spec=importlib.util.spec_from_file_location('http_local',pathlib.Path(__file__).with_name('conteudos-http.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
Client=m.Client
suffix=uuid.uuid4().hex[:8]; password=secrets.token_urlsafe(18)+'aA1!'
users=[]
for index in range(2):
    client=Client();email=f'social-{suffix}-{index}@example.test'
    client.call('POST','/estudantes',{'nome':'Social sintética','usuario':f'social{suffix}{index}','email':email,'senha':password,'dataNascimento':'1995-01-01'})
    session=client.call('POST','/auth/login',{'role':'estudantes','email':email,'senha':password})
    users.append((client,email,session))
a,email,session=users[0]; b=users[1][0]
payload={'conteudo':'Post sintético '+suffix,'autorId':9999,'requisicaoId':str(uuid.uuid4())}
a.call('POST','/posts',payload);a.call('POST','/posts',payload)
posts=a.call('GET','/posts'); own=[p for p in posts if p['conteudo']==payload['conteudo']]
assert len(own)==1 and own[0]['autorId']!=9999
post=own[0];id=post['id'];base='/posts/'+id
for method,route,body in [('POST',base+'/like',{}),('PUT',base,{'conteudo':'Editado'}),('DELETE',base,None)]:
    Client().call(method,route,body,expected=401)
    a.call(method,route,body,csrf=False,expected=403)
b.call('PUT',base,{'conteudo':'Intruso'},expected=403);b.call('DELETE',base,expected=403)
for _ in range(2): a.call('POST',base+'/like',{})
assert a.call('GET',base+'/interacoes')['likesCount']==1
assert not b.call('GET',base+'/interacoes')['curtidoUsuario']
a.call('DELETE',base+'/like');a.call('DELETE',base+'/like')
assert a.call('GET',base+'/interacoes')['likesCount']==0
comment={'texto':'Comentário real','autorId':9999,'autorNome':'Forjado','requisicaoId':str(uuid.uuid4())}
a.call('POST',base+'/comment',comment);a.call('POST',base+'/comment',comment)
comments=a.call('GET',base+'/comments');assert len(comments)==1 and comments[0]['autorId']==post['autorId'] and comments[0]['autorNome']!='Forjado'
for _ in range(2): a.call('POST',base+'/repost',{})
assert a.call('GET',base+'/interacoes')['repostsCount']==1
assert any(p['id']==id for p in a.call('GET','/posts/republicados/'+str(post['autorId'])))
a.call('DELETE',base+'/repost');assert a.call('GET',base+'/interacoes')['repostsCount']==0
quote={'conteudo':'Republicação '+suffix,'requisicaoId':str(uuid.uuid4()),'autorId':9999}
a.call('POST',base+'/repost-comentario',quote);a.call('POST',base+'/repost-comentario',quote)
quotes=[p for p in a.call('GET','/posts') if p['conteudo']==quote['conteudo']]
a.call('POST','/posts',quote,expected=409) # UUID de republicação não pode virar criação simples.
assert len(quotes)==1 and quotes[0]['repostOriginal']['id']==id and quotes[0]['autorId']==post['autorId']
comment_report=b.call('POST',base+'/denuncias',{'motivo':'assedio','comentarioId':comments[0]['id']},expected=201)
report=a.call('POST',base+'/denuncias',{'motivo':'spam'},expected=201)
assert report==a.call('POST',base+'/denuncias',{'motivo':'spam'},expected=201)
a.call('POST',base+'/denuncias',{'motivo':'spam','comentarioId':'ausente'},expected=404)
a.call('PUT',base,{'conteudo':'Post editado '+suffix,'versao':post.get('versao') or 0})
a.call('PUT',base,{'conteudo':'Conflito de edição','versao':post.get('versao') or 0},expected=409)
fresh=Client();fresh.call('POST','/auth/login',{'role':'estudantes','email':email,'senha':password})
assert next(p for p in fresh.call('GET','/posts') if p['id']==id)['conteudo']=='Post editado '+suffix
assert len(fresh.call('GET',base+'/comments'))==1
# Exclusão concorrente nunca deixa interações órfãs acessíveis.
with ThreadPoolExecutor(2) as pool:
    delete=pool.submit(a.call,'DELETE',base)
    try: fresh.call('POST',base+'/like',{})
    except AssertionError as e: assert e.args[0][2]==404
    delete.result()
a.call('DELETE',base)
a.call('GET',base+'/comments',expected=404)
a.call('POST',base+'/like',{},expected=404)
quote=next(p for p in a.call('GET','/posts') if p['id']==quotes[0]['id']);assert quote['originalIndisponivel']
print('PASS RTDB SDK: criação/retry/autoria/CSRF/donos/likes/comments/reposts/edição/exclusão concorrente/nova sessão')
import subprocess
proof=subprocess.check_output(['docker','exec','hipatec-real-pg-161513','psql','-U','postgres','-d','hipatec_test','-tAc',f"SELECT count(*) FROM denuncia WHERE id IN ({int(report['protocolo'])},{int(comment_report['protocolo'])}) AND evidencia IS NOT NULL AND autor_perfil_id={int(post['autorId'])} AND status='PENDENTE'"]).strip()
assert proof==b'2'
print('PASS PostgreSQL: denúncias de publicação/comentário com evidência e autoria preservadas após exclusão; protocolo/retry único')
