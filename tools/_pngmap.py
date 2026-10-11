import zlib, struct, sys, os
sys.stdout.reconfigure(encoding='utf-8')

def load_png(path):
    d = open(path, 'rb').read()
    assert d[:8] == b'\x89PNG\r\n\x1a\n'
    pos = 8
    idat = b''
    w = h = bitd = ct = None
    while pos < len(d):
        ln = struct.unpack('>I', d[pos:pos+4])[0]
        typ = d[pos+4:pos+8]
        data = d[pos+8:pos+8+ln]
        if typ == b'IHDR':
            w, h, bitd, ct, comp, filt, interlace = struct.unpack('>IIBBBBB', data)
        elif typ == b'IDAT':
            idat += data
        pos += 12 + ln
    raw = zlib.decompress(idat)
    ch = {0:1, 2:3, 3:1, 4:2, 6:4}[ct]
    stride = w * ch
    out = bytearray()
    prev = bytearray(stride)
    i = 0
    for y in range(h):
        f = raw[i]; i += 1
        line = bytearray(raw[i:i+stride]); i += stride
        if f == 1:
            for x in range(ch, stride): line[x] = (line[x] + line[x-ch]) & 255
        elif f == 2:
            for x in range(stride): line[x] = (line[x] + prev[x]) & 255
        elif f == 3:
            for x in range(stride):
                a = line[x-ch] if x >= ch else 0
                line[x] = (line[x] + ((a + prev[x]) >> 1)) & 255
        elif f == 4:
            for x in range(stride):
                a = line[x-ch] if x >= ch else 0
                c = prev[x-ch] if x >= ch else 0
                b = prev[x]
                p = a + b - c
                pa, pb, pc = abs(p-a), abs(p-b), abs(p-c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[x] = (line[x] + pr) & 255
        out += line
        prev = line
    return w, h, ch, bytes(out)

def ascii_map(path, x0, y0, x1, y1, step=2):
    w, h, ch, px = load_png(path)
    print('== %s  %dx%d ch=%d  region x%d..%d y%d..%d step=%d' % (os.path.basename(path), w, h, ch, x0, x1, y0, y1, step))
    for y in range(y0, min(y1, h), step):
        row = ''
        for x in range(x0, min(x1, w), step):
            o = (y * w + x) * ch
            r, g, b = px[o], px[o+1], px[o+2]
            a = px[o+3] if ch == 4 else 255
            if a < 16:
                row += ' '
            else:
                lum = (r*30+g*59+b*11)//100
                row += '.' if lum > 200 else ('+' if lum > 150 else ('o' if lum > 90 else '#'))
        print('%3d %s' % (y, row))

for p, reg in sys.argv[1:]:
    pass
