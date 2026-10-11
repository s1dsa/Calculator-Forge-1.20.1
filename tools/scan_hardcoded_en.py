import io, os, re, sys
sys.stdout.reconfigure(encoding='utf-8')
SRC = r'F:\ai\calculator-port\calculator\src\main\java'
# component literals and drawString literals that look like human English words
pat = re.compile(r'(Component\.literal\(\s*"([^"]*)"|drawString\(\s*font\s*,\s*"([^"]*)"|drawCenteredString\(\s*font\s*,\s*"([^"]*)"|renderTooltip\([^,]+,\s*Component\.literal\(\s*"([^"]*)")')
word = re.compile(r'[A-Za-z]{3,}')
skip = re.compile(r'^[A-Za-z0-9_.:/#%\s+\-]+$')
for d, _, fs in os.walk(SRC):
    for f in sorted(fs):
        if not f.endswith('.java'):
            continue
        p = os.path.join(d, f)
        for i, line in enumerate(io.open(p, encoding='utf-8', errors='ignore').read().splitlines(), 1):
            for m in pat.finditer(line):
                text = m.group(2) or m.group(3) or m.group(4) or m.group(5) or ''
                if not text:
                    continue
                if word.search(text):
                    rel = os.path.relpath(p, SRC)
                    if 'gametest' in rel:
                        continue
                    print('%s:%d  %s' % (rel, i, text))
