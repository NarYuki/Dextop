"""Move a group of MirrorService members into their own class behind a host interface.

usage: extract.py iface  <Interface> <field> name...
       extract.py move   <Class> <field> <regex> [--not <regex>] [--base I:field ...] [--type name=Type ...]
       extract.py fix    <Class> <field>          (compile, apply error-driven fixes, repeat)
"""
import re,sys,subprocess,os,json
import members
ROOT=os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
KDIR=ROOT+'/android/app/src/main/kotlin/moe/n4tsu/dextop'
SRC=KDIR+'/MirrorService.kt'
STATE=os.path.dirname(os.path.abspath(__file__))+'/state.json'
MODS=r'(?:(?:private|internal|public|protected|inline|suspend|lateinit|const|override|open|final) )*'

def split_top(s,sep=','):
    out=[];d=0;cur=''
    for k,ch in enumerate(s):
        if ch in '(<[{': d+=1
        elif ch in ')]}' or (ch=='>' and s[k-1:k]!='-'): d-=1
        if ch==sep and d==0: out.append(cur);cur=''
        else: cur+=ch
    if cur.strip(): out.append(cur)
    return out

def signature(L,m,assigned,types):
    """-> (interface decl, override impl) for member m."""
    name,kind=m[0],m[1]
    text=' '.join(l.strip() for l in L[m[4]:m[3]+1])
    text=re.sub(r'^(@[\w.]+(\([^)]*\))? *)*','',text)
    if kind=='fun':
        mm=re.match(MODS+r'fun (<[^>]+> )?(\w+)\(',text)
        gen=mm.group(1) or ''
        i=mm.end();d=1;j=i
        while d:
            if text[j]=='(':d+=1
            elif text[j]==')':d-=1
            j+=1
        params=text[i:j-1];rest=text[j:].lstrip()
        if name in types: ret=types[name]
        elif rest.startswith(':'):
            ret=re.match(r':\s*([^={]+?)\s*(=|\{|$)',rest).group(1)
        elif rest.startswith('{'): ret='Unit'
        else: ret=None
        ps=[p.strip() for p in split_top(params)]
        if any(not re.match(r'(?:vararg )?(\w+)\s*:',p) for p in ps): return None,None
        names=[re.match(r'(?:vararg )?(\w+)\s*:',p).group(1) for p in ps]
        plain=[re.sub(r'\s*=\s*[^=].*$','',p) if '=' in split_eq(p) else p for p in ps]
        plain=[strip_default(p) for p in ps]
        call=', '.join(('*'+n if p.startswith('vararg') else n) for n,p in zip(names,ps))
        if ret is None: return None,None
        r='' if ret=='Unit' else f': {ret}'
        decl=f'    fun {gen}{name}({", ".join(ps)}){r}'
        impl=f'        override fun {gen}{name}({", ".join(plain)}): {ret} = this@MirrorService.{name}({call})'
        return decl,impl
    mm=re.match(MODS+r'(val|var) (\w+)\s*(?::\s*([^=]+?))?\s*(=|by |$)(.*)',text)
    typ=types.get(name) or (mm.group(3).strip() if mm.group(3) else None)
    init=mm.group(5).strip()
    if typ is None:
        if re.match(r'(true|false)\b',init): typ='Boolean'
        elif re.match(r'-?\d+L\b',init): typ='Long'
        elif re.match(r'-?\d*\.?\d+f\b',init): typ='Float'
        elif re.match(r'-?\d+\b(?!\.)',init): typ='Int'
        elif init.startswith('"'): typ='String'
        else:
            c=re.match(r'(?:lazy(?:\([^)]*\))? *\{ *)?([A-Z][\w.]*(?:<[^>]+>)?)\(',init)
            if c: typ=c.group(1)
    if typ is None: return None,None
    if name in assigned and mm.group(1)=='var':
        return (f'    var {name}: {typ}',
                f'        override var {name}: {typ}\n            get() = this@MirrorService.{name}\n            set(value) {{ this@MirrorService.{name} = value }}')
    return f'    val {name}: {typ}', f'        override val {name}: {typ} get() = this@MirrorService.{name}'
def split_eq(p): return p
def strip_default(p):
    d=0
    for i,ch in enumerate(p):
        if ch in '(<[{': d+=1
        elif ch in ')]}' or (ch=='>' and p[i-1:i]!='-'): d-=1
        elif ch=='=' and d==0 and p[i+1:i+2]!='=' and p[i-1]!='=' : return p[:i].rstrip()
    return p

def imports(L):
    return [l for l in L if l.startswith('import ')]

def insert_top(L,start,block):
    L[start+1:start+1]=block

def iface(name,field,names,types):
    L,M,start,end=members.load()
    by={m[0]:m for m in M}
    decls=[];impls=[]
    for n in names:
        d,i=signature(L,by[n],set(),types)
        assert d,f'need --type for {n}: {L[by[n][4]].strip()}'
        decls.append(d);impls.append(i)
    open(f'{KDIR}/{name}.kt','w',encoding='utf-8').write('package moe.n4tsu.dextop\n\n'+'\n'.join(imports(L))+f'\n\ninternal interface {name} {{\n'+'\n'.join(decls)+'\n}\n')
    block=[f'    private val {field} = object : {name} {{']+impls+['    }','']
    insert_top(L,start,block)
    open(SRC,'w',encoding='utf-8').write('\n'.join(L))
    st=json.load(open(STATE)) if os.path.exists(STATE) else {}
    st.setdefault('bases',{})[name]={'field':field,'names':names}
    json.dump(st,open(STATE,'w'),indent=1)
    prune(name)
    print('interface',name,len(names),'members')

def move(cls,field,pat,excl,bases,types,extra_names,only):
    L,M,start,end=members.load()
    st=json.load(open(STATE)) if os.path.exists(STATE) else {}
    rp=re.compile(pat) if pat else None; rx=re.compile(excl) if excl else None
    grp=[m for m in M if m[0]!='companion' and ((rp and rp.search(m[0]) and not (rx and rx.search(m[0]))) or m[0] in extra_names)]
    gn={m[0] for m in grp}
    allnames={m[0] for m in M}
    by={m[0]:m for m in M}
    gcode='\n'.join('\n'.join(L[m[2]:m[3]+1]) for m in grp)
    need=members.refs(gcode,allnames-gn)
    wiring={b['field'] for b in st.get('bases',{}).values()}
    need={n for n in need if by[n][1] in('fun','val','var') and n not in wiring}
    types_needed={n for n in members.refs(gcode,allnames-gn) if by[n][1] not in('fun','val','var')}
    basenames=set()
    for b in bases: basenames|=set(st['bases'][b]['names'])
    assigned={n for n in need if re.search(r'(?<![\w.])'+n+r'\s*(=(?!=)|\+=|-=|\*=|\+\+|--)',members.strip(gcode)) or re.search(r'(\+\+|--)'+n+r'\b',gcode)}
    bad=assigned&basenames
    extras=sorted(need-basenames)
    decls=[];impls=[];missing=[]
    # defaults in a host function's signature read host state: that state has to be on the host too
    grew=True
    while grew:
        grew=False
        for n in list(extras):
            d,_=signature(L,by[n],assigned,types)
            for x in members.refs(d or '',allnames-gn):
                if x!=n and x not in need and x not in basenames and by[x][1] in('fun','val','var'): need.add(x); extras.append(x); grew=True
    extras=sorted(set(extras))
    for n in extras:
        d,i=signature(L,by[n],assigned,types)
        if not d: missing.append(f'{n}: {L[by[n][4]].strip()[:110]}')
        else: decls.append(d);impls.append(i)
    for n in sorted(bad):
        d,i=signature(L,by[n],assigned,types)
        if not d: missing.append(f'{n}: {L[by[n][4]].strip()[:110]}')
        else: decls.append('    /** Written here, not only read. */\n    override'+d[3:]); impls.append(i)
    print(f'{cls}: {len(grp)} members, {sum(m[3]-m[2]+1 for m in grp)} lines; host extras {len(extras)}; nested types referenced: {sorted(types_needed)}')
    if bad: print('writes base members:',bad)
    if missing:
        print('NEED --type:'); [print('  ',x) for x in missing]
    if only=='dry': return
    host=cls+'Host'
    body=[]; top=[]
    for m in grp:
        code=L[m[2]:m[3]+1]
        if m[1] in('fun','val','var') or 'inner ' in L[m[4]]: body+=code+['']
        else:
            code=[l[4:] if l.startswith('    ') else l for l in code]
            k=m[4]-m[2]; code[k]=re.sub(r'^((?:@[\w.]+(?:\([^)]*\))? *)*)private ',r'\1internal ',code[k])
            top+=code+['']
    text='\n'.join(body).rstrip()+'\n'
    text=text.replace('this@MirrorService','context')
    toptext=('\n'.join(top).rstrip()+'\n\n') if top else ''
    ext=(' : '+', '.join(bases)) if bases else ''
    new='package moe.n4tsu.dextop\n\n'+'\n'.join(imports(L))+f'\n\ninternal interface {host}{ext} {{\n'+'\n'.join(decls)+f'\n}}\n\n{toptext}internal class {cls}(\n    private val context: Context,\n    private val desk: {host},\n) {{\n'+text+'}\n'
    open(f'{KDIR}/{cls}.kt','w',encoding='utf-8').write(new)
    # remove from service (bottom-up), put the component where the first member was
    first=min(m[2] for m in grp)
    for m in sorted(grp,key=lambda m:-m[2]):
        hi=m[3]+1
        while hi<len(L) and L[hi].strip()=='' and (m[2]==0 or L[m[2]-1].strip()==''): hi+=1
        del L[m[2]:hi]
    deleg=''.join(f', {b} by {st["bases"][b]["field"]}' for b in bases)
    block=[f'    private val {field}: {cls} = {cls}(this, object : {host}{deleg} {{']+impls+['    })','']
    L[first:first]=block
    open(SRC,'w',encoding='utf-8').write('\n'.join(L))
    st['nested']=sorted(set(st.get('nested',[]))|{m[0] for m in M if m[1] not in('fun','val','var')})
    st.setdefault('components',{})[cls]={'field':field,'names':sorted(gn),'host':sorted(need)}
    json.dump(st,open(STATE,'w'),indent=1)

def adopt(cls,names):
    """Move state the host kept for a component into the component that owns it."""
    L,M,start,end=members.load()
    st=json.load(open(STATE)); by={m[0]:m for m in M}
    f=f'{KDIR}/{cls}.kt'; t=open(f,encoding='utf-8').read()
    code=[]
    for n in names:
        m=by[n]; c=L[m[2]:m[3]+1]; k=m[4]-m[2]
        c[k]=re.sub(r'^(    (?:@[\w.]+(?:\([^)]*\))? *)*)private ',r'\1internal ',c[k]); code+=c
    for m in sorted((by[n] for n in names),key=lambda m:-m[2]): del L[m[2]:m[3]+1]
    open(SRC,'w',encoding='utf-8').write('\n'.join(L))
    for n in names: t=re.sub(r'\bdesk\.'+n+r'\b',n,t)
    a=t.index(f'internal class {cls}('); b=t.index(') {\n',a)+4
    t=t[:b]+'\n'.join(code).replace('this@MirrorService','context')+'\n\n'+t[b:]
    open(f,'w',encoding='utf-8').write(t)
    c=st['components'][cls]; c['names']=sorted(set(c['names'])|set(names)); c['host']=sorted(set(c['host'])-set(names))
    json.dump(st,open(STATE,'w'),indent=1)
    prune_host(cls)
    print(cls,'adopted',len(names))

def dedupe():
    """Drop host members that a base interface now provides."""
    st=json.load(open(STATE)); base=set()
    for b in st['bases'].values(): base|=set(b['names'])
    for cls,c in st['components'].items():
        f=f'{KDIR}/{cls}.kt'; t=open(f,encoding='utf-8').read()
        a=t.index(f'internal interface {cls}Host'); b=t.index('\n}\n',a)
        head=t[a:b].split('\n'); dead=[]; keep=[head[0]]
        for l in head[1:]:
            m=re.match(r'    (?:val|var|fun(?: <[^>]+>)?) (\w+)',l)
            if m and m.group(1) in base: dead.append(m.group(1))
            else: keep.append(l)
        if not dead: continue
        open(f,'w',encoding='utf-8').write(t[:a]+'\n'.join(keep)+t[b:])
        L=open(SRC,encoding='utf-8').read().split('\n')
        i=next(k for k,l in enumerate(L) if re.match(r'    private val \w+(?:: \w+)? = '+cls+r'\(this, object : ',l))
        j=next(k for k in range(i,len(L)) if L[k]=='    })')
        blk=L[i+1:j]; res=[]; k=0
        while k<len(blk):
            m=re.match(r'        override (?:val|var|fun(?: <[^>]+>)?) (\w+)',blk[k]); n=1
            while k+n<len(blk) and not blk[k+n].startswith('        override'): n+=1
            if not (m and m.group(1) in dead): res+=blk[k:k+n]
            k+=n
        L[i+1:j]=res
        open(SRC,'w',encoding='utf-8').write('\n'.join(L))
        c['host']=sorted(set(c['host'])-set(dead)); print(cls,'now from base:',dead)
    json.dump(st,open(STATE,'w'),indent=1)

def compile_errors():
    env=dict(os.environ)  # needs a JDK Gradle supports (17-21) as JAVA_HOME
    p=subprocess.run(['./gradlew','--offline',':app:compileGithubReleaseKotlin','-q'],cwd=ROOT+'/android',env=env,capture_output=True,text=True)
    errs=[]
    for l in (p.stdout+p.stderr).split('\n'):
        m=re.match(r'e: file://(\S+?):(\d+):(\d+) (.*)',l)
        if m: errs.append((m.group(1),int(m.group(2)),int(m.group(3)),m.group(4)))
    return p.returncode,errs,(p.stdout+p.stderr)

def fix(cls,field,rounds=6):
    st=json.load(open(STATE))
    comp=st['components'][cls]; gn=set(comp['names']); hostn=set(comp['host'])
    newf=f'{KDIR}/{cls}.kt'
    api=json.load(open(os.path.dirname(os.path.abspath(__file__))+'/context_api.json'))
    L0,M0,_,_=members.load()
    nested={m[0] for m in M0 if m[1] not in('fun','val','var')}|set(st.get('nested',[]))
    def companion_names():
        L,M,_,_=members.load()
        c=next(m for m in M if m[0]=='companion')
        out={}
        for k in range(c[4],c[3]+1):
            mm=re.match(r'^ {8}(?:@[\w.]+(?:\([^)]*\))? *)*((?:(?:private|internal|const|lateinit|inline) )*)(?:fun (?:<[^>]+> )?|val |var )(\w+)',L[k])
            if mm: out[mm.group(2)]=k
        return out
    def unprivate_companion(names):
        L=open(SRC,encoding='utf-8').read().split('\n'); cn=companion_names(); c=0
        for nm in names:
            k=cn.get(nm)
            if k is not None and re.match(r'^ {8}(@[\w.]+(\([^)]*\))? *)*private ',L[k]):
                L[k]=L[k].replace('private ','internal ',1); c+=1
                if re.search(r'\bvar '+nm+r'\b',L[k]) and any(('fun set'+nm[0].upper()+nm[1:]+'(') in x for x in L):
                    L[k]=L[k].replace('internal var','@JvmField internal var',1)
        open(SRC,'w',encoding='utf-8').write('\n'.join(L)); return c
    for r in range(rounds):
        rc,errs,raw=compile_errors()
        if rc==0: print('COMPILES after',r,'rounds'); return True
        if not errs: print(raw[-3000:]); return False
        edits={}  # file -> list of (line,col,kind,arg)
        unpriv=set(); left=[]; comp_un=set(); cnames=companion_names(); extra_fixes=0
        for f,ln,col,msg in errs:
            u=re.match(r"Unresolved reference '(\w+)'",msg)
            if u and f==newf:
                nm=u.group(1)
                pre='desk.' if nm in hostn else 'MirrorService.' if nm in cnames else 'context.' if nm in api['members'] else 'desk.accessibilityService.' if nm in api['service'] else 'Context.' if nm in api['consts'] else 'MirrorService.' if nm in nested else None
                if nm in cnames and nm not in hostn: comp_un.add(nm)
                if pre: edits.setdefault(f,[]).append((ln,col,'ins',pre))
                else: left.append((f,ln,col,msg))
            elif u and f==SRC and u.group(1) in gn:
                edits.setdefault(f,[]).append((ln,col,'ins',field+'.'))
            elif re.search(r"Cannot access '.*': it is (private|invisible) in '[\w./]*(?<!MirrorService)(?<!Companion)'",msg):
                nm=re.search(r"Cannot access '(?:(?:val|var|fun|class|object|enum class|data class|constructor|typealias|interface|lateinit|const) )*(?:<[^>]+> )?(\w+)",msg).group(1); unpriv.add(nm)
            elif f==newf and re.search(r"Cannot access '(?:(?:inner|data|enum|sealed) )*(?:class|object|interface) (\w+).*': it is private in '[\w./]*MirrorService'",msg):
                nm=re.search(r"(?:class|object|interface) (\w+)",msg).group(1)
                t=open(SRC,encoding='utf-8').read()
                t2=re.sub(r'(?m)^    private ((?:(?:inner|data|enum|sealed) )*(?:class|object|interface) '+nm+r'\b)',r'    internal \1',t)
                if t2!=t: open(SRC,'w',encoding='utf-8').write(t2); extra_fixes=extra_fixes+1
            elif f==newf and re.match(r"Operator '[!=]==' cannot be applied to 'MirrorService\??' and '"+cls+"'",msg):
                ls=open(f,encoding='utf-8').read().split('\n'); s2=re.sub(r'([!=]== )this\b',r'\1desk.accessibilityService',ls[ln-1])
                if s2!=ls[ln-1]: ls[ln-1]=s2; open(f,'w',encoding='utf-8').write('\n'.join(ls)); extra_fixes+=1
            elif f==newf and re.search(r"actual type is '(android\.content\.)?Context\?', but '(android\.content\.)?Context' was expected",msg):
                edits.setdefault(f,[]).append((ln,col,'outer',None))
            elif f==newf and msg.startswith('Expression expected, but package name found'):
                edits.setdefault(f,[]).append((ln,col,'ins','context.'))
            elif f==newf and msg.startswith('None of the following candidates is applicable'):
                ls=open(f,encoding='utf-8').read().split('\n'); s2=re.sub(r'\(this([,)])',r'(context\1',ls[ln-1],count=1)
                if s2!=ls[ln-1]: ls[ln-1]=s2; open(f,'w',encoding='utf-8').write('\n'.join(ls)); extra_fixes+=1
                else:
                    for k in range(ln,min(ln+3,len(ls))):
                        if ls[k].strip()=='this,': ls[k]=ls[k].replace('this,','context,'); open(f,'w',encoding='utf-8').write('\n'.join(ls)); extra_fixes+=1; break
            elif f==newf and re.match(r"Function invocation '\w+\(\)' expected",msg):
                edits.setdefault(f,[]).append((ln,col,'ctx',None))
            elif f==newf and re.search(r"actual type is '[\w.]*"+cls+r"', but '(Context|android\.content\.Context)!?\??' was expected",msg):
                edits.setdefault(f,[]).append((ln,col,'this',None))
            else: left.append((f,ln,col,msg))
        n=extra_fixes
        for f,es in edits.items():
            lines=open(f,encoding='utf-8').read().split('\n')
            for ln,col,kind,arg in sorted(set(es),key=lambda e:(-e[0],-e[1])):
                s=lines[ln-1]
                if kind=='ins' and s[:col-1].endswith('::') and not s[:col-3].endswith(arg[:-1]):
                    dot='.' if re.search(r'[\w)\]]$',s[:col-3]) else ''
                    lines[ln-1]=s[:col-3]+dot+arg[:-1]+s[col-3:]; n+=1; continue
                if kind=='ins' and s[col-1:col+1] in('++','--'): continue
                if kind=='ins' and s[:col-1].endswith('$') and not s[:col-1].endswith('\\$'):
                    e=re.match(r'\w+',s[col-1:]).end(); lines[ln-1]=s[:col-1]+'{'+arg+s[col-1:col-1+e]+'}'+s[col-1+e:]; n+=1; continue
                if kind=='ins' and f==SRC and s[:col-1].endswith('?.'): arg=arg[:-1]+'?.'
                if kind=='ins' and not s[:col-1].endswith(arg) and (f==SRC or not s[:col-1].endswith('.')): lines[ln-1]=s[:col-1]+arg+s[col-1:]; n+=1
                elif kind=='ctx' and s[:col-1].endswith('desk.'): lines[ln-1]=s[:col-6]+'context.'+s[col-1:]; n+=1
                elif kind=='outer' and s[col-1:col+6]=='context': lines[ln-1]=s[:col-1]+'this@'+cls+'.context'+s[col+6:]; n+=1
                elif kind=='this' and s[col-1:col+3]=='this': lines[ln-1]=s[:col-1]+'context'+s[col+3:]; n+=1
            open(f,'w',encoding='utf-8').write('\n'.join(lines))
        if comp_un: n+=unprivate_companion(comp_un)
        for cf in ([f'{KDIR}/{c}.kt' for c in st['components']] if unpriv else []):
            t=open(cf,encoding='utf-8').read(); t0=t
            for nm in unpriv:
                t2=re.sub(r'(?m)^(    (?:@[\w.]+(?:\([^)]*\))? *)*)private ((?:(?:lateinit|inline|inner|data|enum) )*(?:fun (?:<[^>]+> )?|val |var |class )'+nm+r'\b)',r'\1internal \2',t)
                if t2!=t:
                    n+=1
                    setter='fun set'+nm[0].upper()+nm[1:]+'('
                    if setter in t2:
                        t2=re.sub(r'(?m)^(    internal var '+nm+r'\b[^\n]*[^{(\n])$',r'\1\n        private set',t2,count=1)
                t=t2
            if t!=t0: open(cf,'w',encoding='utf-8').write(t)
        print(f'round {r}: {len(errs)} errors, {n} fixes, {len(left)} unhandled')
        if n==0:
            for e in (left or errs)[:40]: print('  ',e[0].split('/')[-1],e[1],e[2],e[3][:200])
            return False
    return False

def prune_host(cls):
    f=f'{KDIR}/{cls}.kt'
    t=open(f,encoding='utf-8').read()
    a=t.index(f'internal interface {cls}Host'); b=t.index('\n}\n',a)
    head=t[a:b].split('\n'); body=t[b:]
    dead=[]; keep=[head[0]]
    for l in head[1:]:
        m=re.match(r'    (?:val|var|fun(?: <[^>]+>)?) (\w+)',l)
        if m and not re.search(r'\b(?:host|desk)(?:\.|::)'+m.group(1)+r'\b',body) and not any(re.search(r'= [^\n]*\b'+m.group(1)+r'\b',o) for o in head[1:] if o is not l): dead.append(m.group(1))
        else: keep.append(l)
    open(f,'w',encoding='utf-8').write(t[:a]+'\n'.join(keep)+body)
    if dead:
        L=open(SRC,encoding='utf-8').read().split('\n')
        i=next(k for k,l in enumerate(L) if re.match(r'    private val \w+(?:: \w+)? = '+cls+r'\(this, object : ',l))
        j=next(k for k in range(i,len(L)) if L[k]=='    })')
        out=[];k=i+1
        blk=L[i+1:j]; res=[]; k=0
        while k<len(blk):
            m=re.match(r'        override (?:val|var|fun(?: <[^>]+>)?) (\w+)',blk[k])
            n=1
            while k+n<len(blk) and not blk[k+n].startswith('        override'): n+=1
            if not (m and m.group(1) in dead): res+=blk[k:k+n]
            k+=n
        L[i+1:j]=res
        open(SRC,'w',encoding='utf-8').write('\n'.join(L))
    print('host interface: dropped unused',dead)

def prune(cls):
    f=f'{KDIR}/{cls}.kt'
    t=open(f,encoding='utf-8').read().split('\n')
    body='\n'.join(l for l in t if not l.startswith('import '))
    keep=[]
    for l in t:
        if l.startswith('import '):
            n=l.split(' as ')[-1].split('.')[-1].strip()
            if n!='*' and n not in('getValue','setValue') and not re.search(r'\b'+re.escape(n)+r'\b',body): continue
        keep.append(l)
    open(f,'w',encoding='utf-8').write('\n'.join(keep))

if __name__=='__main__':
    a=sys.argv[1:]
    def opt(flag,multi=False):
        vals=[]
        while flag in a:
            i=a.index(flag); vals.append(a[i+1]); del a[i:i+2]
        return vals if multi else (vals[0] if vals else None)
    types=dict(x.split('=',1) for x in opt('--type',True))
    bases=opt('--base',True); excl=opt('--not'); names=opt('--name',True); dry='dry' if '--dry' in a else None
    if dry: a.remove('--dry')
    if a[0]=='iface': iface(a[1],a[2],a[3:],types)
    elif a[0]=='move': move(a[1],a[2],a[3] if len(a)>3 else None,excl,bases,types,set(names),dry)
    elif a[0]=='fix':
        if fix(a[1],a[2]):
            prune_host(a[1]); prune(a[1])
            rc,errs,raw=compile_errors(); print('final compile rc',rc,len(errs)); [print('  ',e[0].split('/')[-1],e[1],e[3][:160]) for e in errs[:20]]
    elif a[0]=='prune': prune(a[1])
    elif a[0]=='adopt': adopt(a[1],a[2:])
    elif a[0]=='dedupe': dedupe()
