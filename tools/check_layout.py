"""Simula el plano de la instalación (sin Minecraft) y comprueba que todas las salas y anclas son alcanzables
por un monstruo de 3 bloques de alto, y que el mobiliario no tapa las puertas.
Uso: python tools/check_layout.py"""
import re, itertools, sys
from collections import deque
import os
S=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','src','main','java','com','bearfriender','structure')+os.sep
lay=open(S+'FacilityLayout.java').read()
pl=open(S+'FacilityPlacer.java').read()
# ---- rooms ----
rooms=[]
for m in re.finditer(r'\b(r|base)\("(\w+)",\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),',lay):
    kind,id_,x0,x1,z0,z1=m.groups(); rooms.append((id_,int(x0),int(x1),int(z0),int(z1),kind=='base'))
doors=[(m.group(1)=='true',int(m.group(2)),int(m.group(3)),int(m.group(4))) for m in re.finditer(r'new Door\((true|false),\s*(\d+),\s*(\d+),\s*(-?\d+)\)',lay)]
arches=[(m.group(1)=='true',int(m.group(2)),int(m.group(3)),int(m.group(4))) for m in re.finditer(r'new Arch\((true|false),\s*(\d+),\s*(\d+),\s*(\d+)\)',lay)]
const=lambda n:int(re.search(r'%s\s*=\s*(-?\d+)'%n,lay).group(1))
STX,STEPS,SZ0,SZ1=const('STAIR_TOP_X'),const('STAIR_STEPS'),const('STAIR_Z0'),const('STAIR_Z1')
EXX,EZ0,EZ1,TX1=const('EXIT_WALL_X'),const('EXIT_Z0'),const('EXIT_Z1'),const('TUNNEL_X1')
print(len(rooms),'salas',len(doors),'puertas',len(arches),'arcos')
# ---- volumen: solid por defecto ----
X,Y,Z=48,range(-8,7),41
solid={}
def inb(x,y,z): return 0<=x<48 and 0<=z<41 and -8<=y<=6
def isS(x,y,z): return solid.get((x,y,z),False)
for x in range(48):
    for z in range(41):
        if x<=40 or 26<=z<=29:
            for y in Y: solid[(x,y,z)]=True
def air(x,y,z): solid[(x,y,z)]=False
room_of={}
for (id_,x0,x1,z0,z1,b) in rooms:
    fl=-7 if b else 0; top=-2 if b else 5
    for x in range(x0,x1+1):
        for z in range(z0,z1+1):
            for y in range(fl+1,top+1): air(x,y,z)
            room_of[(x,z,b)]=id_
for (xw,fx,st,yb) in doors:
    for i in range(2):
        x,z=(fx,st+i) if xw else (st+i,fx)
        for y in range(yb,yb+4): air(x,y,z)
for (xw,fx,st,w) in arches:
    for i in range(w):
        x,z=(fx,st+i) if xw else (st+i,fx)
        for y in range(1,5): air(x,y,z)
stairs=set()
for i in range(STEPS):
    x=STX-i; y=-i
    for z in range(SZ0,SZ1+1):
        for yy in range(y+1,6): air(x,yy,z)
        solid[(x,y,z)]=True; stairs.add((x,y,z))
landing=STX-STEPS+1
for z in range(SZ0,SZ1+1):
    for y in range(-6,-2): air(landing-1,y,z)
for x in range(41,TX1+1):
    for z in range(EZ0,EZ1+1):
        for y in range(1,5): air(x,y,z)
def seal(open_):
    for z in range(EZ0,EZ1+1):
        for y in range(1,5): solid[(EXX,y,z)]=not open_
seal(True)
# ---- mobiliario (solo bloques que estorban) ----
furn=set()
body=pl[pl.index('private void furnish()'):pl.index('// ---- iluminación tenue')]
def addbox(x0,y0,z0,x1,y1,z1):
    for x in range(x0,x1+1):
        for y in range(y0,y1+1):
            for z in range(z0,z1+1): furn.add((x,y,z))
for m in re.finditer(r'\bbox\((-?\d+), (-?\d+), (-?\d+), (-?\d+), (-?\d+), (-?\d+), st\((?:Blocks\.)?(\w+)\)\)',body):
    a=list(map(int,m.groups()[:6])); 
    if m.group(7) in ('SPRUCE_SLAB',): continue
    addbox(*a)
for m in re.finditer(r'\bset\((-?\d+), (-?\d+), (-?\d+), (?:st\(|Blocks\.)',body):
    x,y,z=map(int,m.groups())
    if z==0 and y>=1 and x in (6,7): continue
    furn.add((x,y,z))
for m in re.finditer(r'\b(stand|tv|candle)\((-?\d+), (-?\d+), (-?\d+)(?:, \w+)?\)',body):
    furn.add(tuple(map(int,m.groups()[1:])))
for m in re.finditer(r'\btoyShelf\((\d+), (\d+), (\d+), (\d+)\)',body):
    a=list(map(int,m.groups())); addbox(a[0],1,a[1],a[2],4,a[3])
# bucles escritos a mano (espejo del Java)
for x in (16,19,22,25): furn|={(x,1,1),(x,2,1)}
for x in (17,21,24): furn.add((x,2,38))
for p in ((17,17),(23,17),(17,23),(23,23)): addbox(p[0],1,p[1],p[0],5,p[1])
for p in ((18,17),(22,17),(18,23),(22,23)): furn.add((p[0],3,p[1]))
for p in ((16,20),(24,20),(20,16),(20,24)): addbox(p[0],1,p[1],p[0],2,p[1])
for x in range(33,39): furn|={(x,1,20),(x,1,23)}
for z in (19,22,25): furn.add((10,-5,z))
for x in (12,15,18): furn|={(x,-5,16),(x,-5,28)}
print('bloques de mobiliario estorbo:',len(furn))
def blocked(x,y,z): return isS(x,y,z) or (x,y,z) in furn
# ---- ¿mobiliario dentro de huecos de puertas/arcos? ----
bad=[]
for (xw,fx,st,yb) in doors:
    for i in range(2):
        x,z=(fx,st+i) if xw else (st+i,fx)
        # celdas a ambos lados de la puerta
        for d in (-1,0,1):
            xx,zz=(x+d,z) if xw else (x,z+d)
            for y in range(yb,yb+3):
                if (xx,y,zz) in furn: bad.append(('puerta',(xw,fx,st),(xx,y,zz)))
print('mobiliario que bloquea puertas:',bad if bad else 'ninguno')
# ---- alcanzabilidad (oso: 3 de alto, pasa por puertas abiertas) ----
def standable(x,y,z):
    if not inb(x,y,z): return False
    if any(blocked(x,y+k,z) for k in range(3)): return False
    below=isS(x,y-1,z) or (x,y-1,z) in furn
    return below
start=(6,1,4)
assert standable(*start),'inicio no valido'
seen={start}; q=deque([start])
while q:
    x,y,z=q.popleft()
    for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
        for dy in (0,-1,1):
            n=(x+dx,y+dy,z+dz)
            if n in seen or not standable(*n): continue
            if dy==1 and any(blocked(x,y+3,z) for _ in [0]): continue
            if dy!=0 and not ((n[0],n[1]-1,n[2]) in stairs or (x,y-1,z) in stairs or (n[0],n[1],n[2]) in stairs or (x,y,z) in stairs): continue
            seen.add(n); q.append(n)
print('celdas alcanzables:',len(seen))
unreach=[]
for (id_,x0,x1,z0,z1,b) in rooms:
    if id_=='bear_hold': print('  bear_hold      sellada a proposito'); continue
    y=-6 if b else 1
    cells=[(x,y,z) for x in range(x0,x1+1) for z in range(z0,z1+1) if standable(x,y,z)]
    got=[c for c in cells if c in seen]
    pct=100*len(got)//max(1,len(cells))
    flag='' if pct>=60 else '  <-- REVISAR'
    print(f'  {id_:14s} alcanzable {pct:3d}% ({len(got)}/{len(cells)}){flag}')
for name,c in (('teddy',(3,1,28)),('panel',(33,1,38)),('incinerador',(27,-6,19)),('transmisor',(17,-6,22)),('tunel',(46,1,27))):
    print(f'  ancla {name:12s}', 'OK' if c in seen else 'NO alcanzable')
# puertas: ambos lados alcanzables
for (xw,fx,st,yb) in doors:
    x,z=(fx,st) if xw else (st,fx)
    ok=(x,yb,z) in seen
    if not ok: print('  puerta sin acceso',(xw,fx,st,yb))

