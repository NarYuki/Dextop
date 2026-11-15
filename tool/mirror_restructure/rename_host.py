import re,json,extract
K=extract.KDIR
st=json.load(open(extract.STATE))
files=[f'{K}/{c}.kt' for c in st['components']]
for f in files:
    t=open(f,encoding='utf-8').read()
    t2=t.replace('    private val host: ','    private val desk: ',1)
    if t2!=t: open(f,'w',encoding='utf-8').write(t2)
for r in range(4):
    rc,errs,raw=extract.compile_errors()
    if rc==0: print('ok after',r); break
    ed={}
    for f,ln,col,msg in errs:
        if re.match(r"Unresolved reference 'host'",msg): ed.setdefault(f,set()).add((ln,col))
    n=0
    for f,es in ed.items():
        L=open(f,encoding='utf-8').read().split('\n')
        for ln,col in sorted(es,key=lambda e:(-e[0],-e[1])):
            s=L[ln-1]
            if s[col-1:col+3]=='host': L[ln-1]=s[:col-1]+'desk'+s[col+3:]; n+=1
        open(f,'w',encoding='utf-8').write('\n'.join(L))
    print('round',r,len(errs),'errors',n,'renamed')
    if n==0:
        for e in errs[:10]: print(e)
        break
