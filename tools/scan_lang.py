import sys, re
p = sys.argv[1]
pats = sys.argv[2:]
raw = open(p, 'rb').read()
t = raw.decode('utf-8', errors='ignore')
ls = t.splitlines()
print('lines', len(ls))
for i, l in enumerate(ls, 1):
    for pat in pats:
        if pat in l:
            print(i, l)
            break
