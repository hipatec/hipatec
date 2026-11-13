"""Falha real do emulador local: pausa só o PID validado, sempre retoma em finally."""
import sys; sys.dont_write_bytecode = True
import os,signal,time,uuid,secrets,pathlib,importlib.util
spec=importlib.util.spec_from_file_location('h',pathlib.Path(__file__).with_name('conteudos-http.py'));h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)
pid=int(os.environ['HIPATEC_RTDB_TEST_PID'])
args=pathlib.Path(f'/proc/{pid}/cmdline').read_bytes().split(b'\0')
assert any(x.endswith(b'firebase-database-emulator-v4.11.2.jar') for x in args) and b'18900' in args and b'127.0.0.1' in args
c=h.Client();suffix=uuid.uuid4().hex[:8];email='outage-'+suffix+'@example.test';pw=secrets.token_urlsafe(18)+'aA1!'
c.call('POST','/estudantes',{'nome':'Falha sintética','usuario':'outage'+suffix,'email':email,'senha':pw,'dataNascimento':'1995-01-01'})
c.call('POST','/auth/login',{'role':'estudantes','email':email,'senha':pw})
payload={'conteudo':'Falha real '+suffix,'requisicaoId':str(uuid.uuid4())};c.call('GET','/posts')
start=time.monotonic()
try:
    os.kill(pid,signal.SIGSTOP)
    c.call('POST','/posts',payload,expected=500)
    assert time.monotonic()-start>=14
finally: os.kill(pid,signal.SIGCONT)
# O timeout não prova rollback remoto. Mesmo UUID torna segura a reconciliação/repetição.
for _ in range(60):
    if any(p['conteudo']==payload['conteudo'] for p in c.call('GET','/posts')):break
    time.sleep(.1)
c.call('POST','/posts',payload)
fresh=h.Client();fresh.call('POST','/auth/login',{'role':'estudantes','email':email,'senha':pw})
assert len([p for p in fresh.call('GET','/posts') if p['conteudo']==payload['conteudo']])==1
print('PASS falha real RTDB: sem confirmação HTTP antes do ACK; timeout, reconciliação/retry e nova sessão sem duplicação')
