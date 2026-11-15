"""Class-level member map of MirrorService.kt and cross-reference analysis."""
import re,sys,os
SRC=os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))+'/android/app/src/main/kotlin/moe/n4tsu/dextop/MirrorService.kt'
DECL=re.compile(r'^ {4}(?:@[\w.]+(?:\([^)]*\))? *)*(?:(?:private|internal|override|public|protected|inline|suspend|lateinit|const|inner|data|enum|sealed|abstract|open|final) )*(fun|val|var|class|object|interface|companion object)\b ?(?:<[^>]+> )?(?:[\w.<>?, ]+\.)?(\w+)?')
def load():
    L=open(SRC,encoding='utf-8').read().split('\n')
    # class body starts after 'class MirrorService'
    start=next(i for i,l in enumerate(L) if l.startswith('class MirrorService'))
    end=max(i for i,l in enumerate(L) if l=='}')
    members=[]  # (name,kind,first,last) 0-based inclusive
    i=start+1
    cur=None
    def lead(j):
        # extend upward over annotations/comments directly above
        k=j
        while k-1>start and re.match(r'^ {4}(@|//|/\*|\*)',L[k-1]) : k-=1
        return k
    for j in range(start+1,end):
        m=DECL.match(L[j])
        if m and not L[j].startswith('     '):
            kind=m.group(1); name=m.group(2) or 'companion'
            members.append([name,kind,lead(j),None,j])
    for a,b in zip(members,members[1:]): a[3]=b[2]-1
    members[-1][3]=end-1
    # trim trailing blank lines
    for m in members:
        while m[3]>m[4] and L[m[3]].strip()=='' : m[3]-=1
    return L,members,start,end
def strip(code):
    code=re.sub(r'/\*.*?\*/','',code,flags=re.S)
    out=[]
    for l in code.split('\n'):
        # drop // comments not inside strings (rough)
        k=l.find('//')
        while k>=0 and l[:k].count('"')%2==1: k=l.find('//',k+2)
        out.append(l if k<0 else l[:k])
    return '\n'.join(out)
def refs(code,names):
    found={}
    for t in re.finditer(r'(?<![\w.])([A-Za-z_]\w*)\b',strip(code)):
        n=t.group(1)
        if n in names: found[n]=found.get(n,0)+1
    return found
if __name__=='__main__':
    L,M,s,e=load()
    names={}
    for m in M: names.setdefault(m[0],[]).append(m)
    pat=re.compile(sys.argv[1]); excl=re.compile(sys.argv[2]) if len(sys.argv)>2 and sys.argv[2] else None
    grp=[m for m in M if m[0]!='companion' and pat.search(m[0]) and not (excl and excl.search(m[0]))]
    gn={m[0] for m in grp}
    rest=[m for m in M if m[0] not in gn]
    gcode='\n'.join('\n'.join(L[m[2]:m[3]+1]) for m in grp)
    rcode='\n'.join('\n'.join(L[m[2]:m[3]+1]) for m in rest)
    allnames=set(names)
    out=refs(gcode,allnames-gn)   # group -> host
    inn=refs(rcode,gn)            # rest -> group
    kinds={m[0]:m[1] for m in M}
    print(f'group: {len(grp)} members, {sum(m[3]-m[2]+1 for m in grp)} lines')
    print('  funs:',' '.join(sorted(m[0] for m in grp if m[1]=='fun')))
    print('  state:',' '.join(sorted(m[0] for m in grp if m[1]!='fun')))
    print(f'NEEDS from host ({len(out)}):')
    print('  fun:',' '.join(f'{k}({v})' for k,v in sorted(out.items()) if kinds[k]=='fun'))
    print('  state:',' '.join(f'{k}({v})' for k,v in sorted(out.items()) if kinds[k]!='fun'))
    print(f'USED BY host ({len(inn)}):')
    print('  ',' '.join(f'{k}({v})' for k,v in sorted(inn.items())))
