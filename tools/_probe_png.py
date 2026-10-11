import struct, zlib, sys

def read_png(path):
    with open(path, 'rb') as f:
        data = f.read()
    pos = 8
    w = h = bd = ct = None
    idat = b''
    plte = None
    trns = None
    while pos < len(data):
        ln = struct.unpack('>I', data[pos:pos+4])[0]
        typ = data[pos+4:pos+8]
        chunk = data[pos+8:pos+8+ln]
        if typ == b'IHDR':
            w, h, bd, ct, comp, filt, inter = struct.unpack('>IIBBBBB', chunk[:13])
            assert bd == 8 and ct == 6, (bd, ct)
            assert inter == 0
        elif typ == b'IDAT':
            idat += chunk
        pos += 12 + ln
    raw = zlib.decompress(idat)
    ch = 4
    stride = w * ch
    out = bytearray(h * stride)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        ft = raw[p]; p += 1
        line = bytearray(raw[p:p+stride]); p += stride
        if ft == 1:
            for i in range(ch, stride):
                line[i] = (line[i] + line[i-ch]) & 255
        elif ft == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 255
        elif ft == 3:
            for i in range(stride):
                a = line[i-ch] if i >= ch else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif ft == 4:
            for i in range(stride):
                a = line[i-ch] if i >= ch else 0
                b = prev[i]
                c = prev[i-ch] if i >= ch else 0
                pa = abs(b - c); pb = abs(a - c); pc = abs(a + b - 2*c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out[y*stride:(y+1)*stride] = line
        prev = line
    return w, h, out

def px(img, w, x, y):
    stride = w*4
    o = y*stride + x*4
    return tuple(img[o:o+4])

if __name__ == '__main__':
    path = sys.argv[1]
    w, h, img = read_png(path)
    print('size', w, h)
    # print a small grid of sample points from the requested coords
    pts = [tuple(int(v) for v in a.split(',')) for a in sys.argv[2:]]
    for (x, y) in pts:
        print((x, y), px(img, w, x, y))
