import struct, zlib, sys, os

def read_png(path):
    data = open(path, 'rb').read()
    pos = 8; idat = b''
    while pos < len(data):
        ln = struct.unpack('>I', data[pos:pos+4])[0]
        typ = data[pos+4:pos+8]; chunk = data[pos+8:pos+8+ln]
        if typ == b'IHDR':
            w, h, bd, ct, comp, filt, inter = struct.unpack('>IIBBBBB', chunk[:13])
        elif typ == b'IDAT':
            idat += chunk
        pos += 12 + ln
    raw = zlib.decompress(idat); ch = 4; stride = w*ch
    out = bytearray(h*stride); prev = bytearray(stride); p = 0
    for y in range(h):
        ft = raw[p]; p += 1
        line = bytearray(raw[p:p+stride]); p += stride
        if ft == 1:
            for i in range(ch, stride): line[i] = (line[i] + line[i-ch]) & 255
        elif ft == 2:
            for i in range(stride): line[i] = (line[i] + prev[i]) & 255
        elif ft == 3:
            for i in range(stride):
                a = line[i-ch] if i >= ch else 0; line[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif ft == 4:
            for i in range(stride):
                a = line[i-ch] if i >= ch else 0; b = prev[i]; c = prev[i-ch] if i >= ch else 0
                pa = abs(b-c); pb = abs(a-c); pc = abs(a+b-2*c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out[y*stride:(y+1)*stride] = line; prev = line
    return w, h, out

def find_slots(path, region=360):
    w, h, img = read_png(path)
    stride = w*4
    # find connected components of the vanilla slot interior colour 0x8B8B8B
    target = (139, 139, 139, 255)
    seen = set(); boxes = []
    for y in range(0, min(region, h)):
        for x in range(0, min(region, w)):
            if (x, y) in seen: continue
            o = y*stride + x*4
            if tuple(img[o:o+4]) != target: continue
            # BFS
            stack = [(x, y)]; seen.add((x, y))
            minx = maxx = x; miny = maxy = y; n = 0
            while stack:
                cx, cy = stack.pop(); n += 1
                minx = min(minx, cx); maxx = max(maxx, cx); miny = min(miny, cy); maxy = max(maxy, cy)
                for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
                    nx, ny = cx+dx, cy+dy
                    if 0 <= nx < w and 0 <= ny < h and (nx, ny) not in seen:
                        oo = ny*stride + nx*4
                        if tuple(img[oo:oo+4]) == target:
                            seen.add((nx, ny)); stack.append((nx, ny))
            if n >= 40:
                boxes.append((minx, miny, maxx, maxy, n))
    return w, h, boxes

if __name__ == '__main__':
    base = sys.argv[1]
    for name in sys.argv[2:]:
        p = os.path.join(base, name)
        w, h, boxes = find_slots(p)
        print('==', name, w, h)
        for (a, b, c, d, n) in sorted(boxes, key=lambda t: (t[1], t[0]))[:40]:
            print('   txt(%d,%d)-(%d,%d)  menu(%g,%g)-(%g,%g) px=%d' % (a, b, c, d, a/2, b/2, c/2, d/2, n))
