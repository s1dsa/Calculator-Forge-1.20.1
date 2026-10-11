import sys, re
for p in sys.argv[1:]:
    print('=====', p)
    t = open(p, 'rb').read().decode('utf-8', errors='ignore')
    for i, l in enumerate(t.splitlines(), 1):
        if 'addSlotToContainer' in l or 'class ' in l or 'super(' in l or 'INV_START' in l or 'getInputStackSize' in l or 'setBlockBounds' in l or 'addInventory' in l:
            print(i, l.strip())
