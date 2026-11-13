"""Perfis/seguimento/senha reais; somente API local18086, contas sintéticas."""
import sys; sys.dont_write_bytecode = True
import importlib.util, pathlib, secrets, uuid, urllib.request, urllib.error, json
from concurrent.futures import ThreadPoolExecutor
spec=importlib.util.spec_from_file_location('harness',pathlib.Path(__file__).with_name('conteudos-http.py')); h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)
def update(c,path,fields,expected=200,image=None,csrf=True):
    c.call('GET','/auth/csrf'); boundary='hipatec-'+uuid.uuid4().hex; parts=[]
    for key,value in fields.items():
        parts.append(('--'+boundary+'\r\nContent-Disposition: form-data; name="'+key+'"\r\n\r\n'+value+'\r\n').encode())
    if image:
        parts.append(('--'+boundary+'\r\nContent-Disposition: form-data; name="pfp"; filename="fixture.png"\r\nContent-Type: image/png\r\n\r\n').encode()+image+b'\r\n')
    parts.append(('--'+boundary+'--\r\n').encode()); headers={'Content-Type':'multipart/form-data; boundary='+boundary}
    if csrf:
        headers.update({'X-XSRF-TOKEN':next(x.value for x in c.jar if x.name=='XSRF-TOKEN')})
    req=urllib.request.Request(h.BASE+path,data=b''.join(parts),headers=headers,method='PUT')
    try:
        with c.http.open(req) as r: status=r.status;raw=r.read()
    except urllib.error.HTTPError as e: status=e.code;raw=e.read()
    assert status==expected,(path,status,expected)
    return json.loads(raw) if raw else None

def run():
    suffix=uuid.uuid4().hex[:10];pw=secrets.token_urlsafe(18)+'aA1!'; anon=h.Client()
    emails=['perfil-'+suffix+str(i)+'@example.test' for i in range(2)]
    for i,email in enumerate(emails): anon.call('POST','/estudantes',{'nome':'Pessoa sintética','usuario':'perfil'+suffix+str(i),'email':email,'senha':pw,'dataNascimento':'1995-01-01'})
    def login(email,password=pw):
        c=h.Client();s=c.call('POST','/auth/login',{'role':'estudantes','email':email,'senha':password});return c,s
    a,sa=login(emails[0]);b,sb=login(emails[1]);path='/perfil/estudantes/'+str(sa['id']);other='/perfil/estudantes/'+str(sb['id'])
    pa=a.call('GET',path);pb=b.call('GET',other)
    assert 'email' not in b.call('GET',path)
    fields={'nome':'Nome atualizado','usuario':'novo'+suffix,'biografia':'Biografia preservada'}
    update(b,path,fields,403);update(a,path,fields,403,csrf=False);update(a,path,{**fields,'nome':''},400)
    update(a,path,fields);assert login(emails[0])[0].call('GET',path)['biografia']==fields['biografia']
    import base64
    image=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aV5cAAAAASUVORK5CYII=')
    result=update(a,path,fields,image=image); assert result['pfp'].startswith(h.BASE+'/test-media/')
    update(a,path,fields,400,image=b'not-an-image');assert a.call('GET',path)['pfp']==result['pfp']
    dest='/perfil/'+str(pb['id']); own='/perfil/'+str(pa['id']); count=pb['num_seguidores']
    a.call('POST',own+'/seguir',expected=400);a.call('POST','/perfil/2147483647/seguir',expected=404);anon.call('POST',dest+'/seguir',expected=401)
    clients=[login(emails[0])[0] for _ in range(2)]
    with ThreadPoolExecutor(2) as pool: list(pool.map(lambda c:c.call('POST',dest+'/seguir'),clients))
    assert b.call('GET',other)['num_seguidores']==count+1
    assert login(emails[0])[0].call('GET',dest+'/seguindo')['seguindo']
    a.call('DELETE',dest+'/seguir');a.call('DELETE',dest+'/seguir');assert b.call('GET',other)['num_seguidores']==count
    update(a,path,{**fields,'novaSenha':'curta'},400);update(a,path,{**fields,'email':emails[1]},409)
    newpw=secrets.token_urlsafe(18)+'aA1!';update(a,path,{**fields,'novaSenha':newpw})
    a.call('GET','/auth/session',expected=401);clients[0].call('GET','/auth/session',expected=401)
    h.Client().call('POST','/auth/login',{'role':'estudantes','email':emails[0],'senha':pw},expected=401)
    assert login(emails[0],newpw)[0].call('GET',path)['nome']==fields['nome']
    print('PASS perfil: dono/CSRF/opcionais/foto adapter local/validação/nova sessão/senha revoga sessões; follow concorrente/totais/unfollow')
if __name__=='__main__':run()
