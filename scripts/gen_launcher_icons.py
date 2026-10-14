"""
Launcher icons for each app style (adaptive icons: background, foreground, monochrome for themed icons).

Run from the repository root:  python3 scripts/gen_launcher_icons.py
Add --preview to also write PNG previews to build/icon-previews (needs: pip install cairosvg).
"""
import math, os, sys
R = "app/src/main/res"

def f(v): return f"{v:.2f}".rstrip('0').rstrip('.')
def circle(cx, cy, r): return f"M{f(cx-r)},{f(cy)} a{f(r)},{f(r)} 0 1,0 {f(2*r)},0 a{f(r)},{f(r)} 0 1,0 {f(-2*r)},0 Z"
def ellipse(cx, cy, rx, ry): return f"M{f(cx-rx)},{f(cy)} a{f(rx)},{f(ry)} 0 1,0 {f(2*rx)},0 a{f(rx)},{f(ry)} 0 1,0 {f(-2*rx)},0 Z"

class Icon:
    def __init__(s): s.items = []
    def fill(s, d, color, alpha=1.0): s.items.append(('fill', d, color, alpha)); return s
    def stroke(s, d, color, w, alpha=1.0): s.items.append(('stroke', d, color, w, alpha)); return s

def palm_leaves(top, length, angles=(-172, -138, -104, -76, -42, -8)):
    out = []
    tx, ty = top
    for i, deg in enumerate(angles):
        a = math.radians(deg)
        tip = (tx + math.cos(a) * length, ty + math.sin(a) * length + length * 0.35)
        dx, dy = tip[0] - tx, tip[1] - ty
        d = max(math.hypot(dx, dy), 1)
        n = (-dy / d, dx / d)
        mid = ((tx + tip[0]) / 2, (ty + tip[1]) / 2 - length * 0.28)
        c1 = (mid[0] + n[0] * length * 0.26, mid[1] + n[1] * length * 0.26)
        c2 = (mid[0] - n[0] * length * 0.06, mid[1] - n[1] * length * 0.06)
        out.append((i, f"M{f(tx)},{f(ty)} Q{f(c1[0])},{f(c1[1])} {f(tip[0])},{f(tip[1])} Q{f(c2[0])},{f(c2[1])} {f(tx)},{f(ty)} Z",
                    f"M{f(tx)},{f(ty)} Q{f(mid[0])},{f(mid[1] - length*0.04)} {f(tip[0])},{f(tip[1])}"))
    return out

TOP = (57, 41)
TRUNK = f"M51,76 Q48,58 {TOP[0]},{TOP[1]}"
ISLAND_SAND = "M22,78 Q38,68 54,68 Q70,68 86,78 Q54,86 22,78 Z"
HILL = "M31,74 Q42,63 55,63 Q68,63 78,74 Q55,77 31,74 Z"
SEA = "M0,72 Q27,69 54,72 Q81,75 108,72 L108,108 L0,108 Z"

def lagoon():
    i = Icon()
    i.fill(circle(80, 28, 9), '#FFC970')
    i.fill(SEA, '#2A9D95')
    i.fill(ellipse(54, 79, 40, 9), '#8FE0D6')
    i.fill(ISLAND_SAND, '#F3D9A4')
    i.fill(HILL, '#4FA36A')
    i.stroke(TRUNK, '#8B6544', 4.2)
    for k, leaf, _ in palm_leaves(TOP, 27):
        i.fill(leaf, '#2F8A5A' if k % 2 == 0 else '#22704A')
    i.fill(circle(TOP[0] - 2, TOP[1] + 2.5, 2.4), '#6B4A2E').fill(circle(TOP[0] + 2.4, TOP[1] + 3, 2.4), '#6B4A2E')
    return i, ('#8ECFE8', '#FCE6CF')

def arcade():
    i = Icon()
    i.fill(circle(78, 28, 10), '#FF5CC8')
    for k in range(4):  # synthwave sun stripes
        y = 30 + k * 3.2
        i.fill(f"M66,{f(y)} h24 v{f(0.8 + 0.45*k)} h-24 Z", '#22104A')
    i.fill(SEA, '#0E1A3A')
    for k in range(1, 6):
        t = (k / 6) ** 2; y = 72 + 36 * t
        i.stroke(f"M0,{f(y)} L108,{f(y)}", '#4DF0FF', 0.9, 0.35 + 0.4 * t)
    for k in range(-5, 6):
        i.stroke(f"M{f(54 + k*6)},72 L{f(54 + k*26)},108", '#4DF0FF', 0.8, 0.35)
    i.fill(ISLAND_SAND, '#1B2F5E')
    i.stroke(ISLAND_SAND, '#4DF0FF', 1.4)
    i.stroke(TRUNK, '#4DF0FF', 6, 0.25)
    i.stroke(TRUNK, '#4DF0FF', 2.2)
    for k, _, rib in palm_leaves(TOP, 27):
        c = '#B8FF3C' if k % 2 == 0 else '#7CDB2A'
        i.stroke(rib, c, 5.5, 0.25)
        i.stroke(rib, c, 2.2)
    i.fill(circle(TOP[0], TOP[1] + 2, 2.2), '#FF5CC8')
    return i, ('#05081A', '#2A1450')

def cherry_crown(i, top, h, c1, c2, dot):
    cl = [(0, 0, .30), (-.30, .10, .24), (.30, .08, .25), (-.12, -.20, .24), (.16, -.18, .23), (0, .14, .22)]
    for k, (dx, dy, r) in enumerate(cl):
        i.fill(circle(top[0] + dx * h, top[1] + dy * h, r * h), c1 if k % 2 == 0 else c2)
    for k in range(6):
        a = k * 1.05
        i.fill(circle(top[0] + math.cos(a) * h * 0.2, top[1] + math.sin(a) * h * 0.14, h * 0.028), dot)

def blossom():
    i = Icon()
    i.fill(circle(80, 26, 8), '#FFF0A8')
    i.fill(SEA, '#B3A2EA')
    i.fill(ellipse(54, 79, 40, 9), '#DCCFFF')
    i.fill(ISLAND_SAND, '#F7DCC8')
    i.fill(HILL, '#A9D8A0')
    top = (55, 44)
    i.stroke(f"M53,74 Q51,58 {top[0]},{top[1]}", '#7A4E3A', 4.4)
    i.stroke(f"M52,60 L64,48", '#7A4E3A', 2.4)
    cherry_crown(i, top, 40, '#F7A8C4', '#F28DB2', '#FFFFFF')
    for (x, y) in [(20, 86), (34, 92), (78, 90), (90, 84), (64, 96)]:
        i.fill(ellipse(x, y, 2.6, 1.3), '#FFC4D8')
    return i, ('#F9CFE2', '#FFE9DC')

def explorer():
    i = Icon()
    ink = '#3A2A18'
    i.fill(circle(80, 26, 9), '#FFD23F').stroke(circle(80, 26, 9), ink, 1.6)
    i.fill(SEA, '#2FA8E0')
    i.fill(ellipse(54, 79, 40, 9), '#7FE0F5')
    i.fill(ISLAND_SAND, '#FFD978').stroke(ISLAND_SAND, ink, 1.8)
    i.fill(HILL, '#5CC94A').stroke(HILL, ink, 1.8)
    i.stroke(TRUNK, ink, 6.2).stroke(TRUNK, '#9C6B3F', 3.6)
    for k, leaf, _ in palm_leaves(TOP, 26):
        i.stroke(leaf, ink, 1.6)
        i.fill(leaf, '#2FA84F' if k % 2 == 0 else '#1E8A3C')
    # Turtle mascot in front of the island
    tx, ty = 72, 86
    i.fill(ellipse(tx - 7, ty + 3, 3, 1.8), '#8EE07A').fill(ellipse(tx + 6, ty + 3.4, 3, 1.8), '#8EE07A')
    i.fill(circle(tx + 11, ty - 1, 3.6), '#8EE07A').stroke(circle(tx + 11, ty - 1, 3.6), ink, 1.3)
    i.fill(circle(tx + 12.2, ty - 2, 0.9), ink)
    shell = f"M{tx-10},{ty+1} A10,8 0 0,1 {tx+10},{ty+1} Z"
    i.fill(shell, '#3FA34D').stroke(shell, ink, 1.4)
    i.fill(circle(tx, ty - 2, 2), '#8EE07A').fill(circle(tx - 5, ty - 0.5, 1.4), '#8EE07A').fill(circle(tx + 5, ty - 0.5, 1.4), '#8EE07A')
    return i, ('#6EC8FF', '#C9EEFF')

def monochrome(kind):
    """Silhouette for themed icons (Android 13+): island and the style's tree only."""
    i = Icon()
    i.fill(ISLAND_SAND, '#000000')
    if kind == 'blossom':
        top = (55, 44)
        i.stroke(f"M53,74 Q51,58 {top[0]},{top[1]}", '#000000', 4.4)
        cherry_crown(i, top, 40, '#000000', '#000000', '#000000')
    elif kind == 'arcade':
        i.stroke(TRUNK, '#000000', 3.2)
        for k, _, rib in palm_leaves(TOP, 27): i.stroke(rib, '#000000', 3.2)
    else:
        i.stroke(TRUNK, '#000000', 4.2)
        for k, leaf, _ in palm_leaves(TOP, 27): i.fill(leaf, '#000000')
    return i

# Launchers show only the central 72 dp (and mask it): the scene is drawn at this scale around the centre.
SCALE = 0.78

def to_svg(icon, bg):
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="432" height="432">']
    if bg:
        parts.append(f'<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{bg[0]}"/><stop offset="0.7" stop-color="{bg[1]}"/></linearGradient></defs><rect width="108" height="108" fill="url(#g)"/>')
    parts.append(f'<g transform="translate(54 54) scale({SCALE}) translate(-54 -54)">')
    for it in icon.items:
        if it[0] == 'fill': parts.append(f'<path d="{it[1]}" fill="{it[2]}" fill-opacity="{it[3]}"/>')
        else: parts.append(f'<path d="{it[1]}" fill="none" stroke="{it[2]}" stroke-width="{it[3]}" stroke-opacity="{it[4]}" stroke-linecap="round" stroke-linejoin="round"/>')
    parts.append('</g></svg>'); return ''.join(parts)

def to_vector(icon, comment):
    lines = ['<?xml version="1.0" encoding="utf-8"?>', f'<!-- {comment} Generated by scripts/gen_launcher_icons.py. -->',
             '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
             '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">',
             f'    <group android:pivotX="54" android:pivotY="54" android:scaleX="{SCALE}" android:scaleY="{SCALE}">']
    for it in icon.items:
        if it[0] == 'fill':
            a = f' android:fillAlpha="{f(it[3])}"' if it[3] < 1 else ''
            lines.append(f'        <path android:fillColor="{it[2]}"{a} android:pathData="{it[1]}" />')
        else:
            a = f' android:strokeAlpha="{f(it[4])}"' if it[4] < 1 else ''
            lines.append(f'        <path android:strokeColor="{it[2]}" android:strokeWidth="{f(it[3])}"{a} android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{it[1]}" />')
    lines.append('    </group>'); lines.append('</vector>'); return '\n'.join(lines) + '\n'

def bg_vector(bg, comment):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {comment} Generated by scripts/gen_launcher_icons.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="54" android:startY="0" android:endX="54" android:endY="76">
                <item android:offset="0" android:color="{bg[0]}" />
                <item android:offset="1" android:color="{bg[1]}" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
'''

STYLES = {'lagoon': lagoon, 'arcade': arcade, 'blossom': blossom, 'explorer': explorer}
if __name__ == '__main__':
    preview = '--preview' in sys.argv
    if preview:
        import cairosvg
        OUT = 'build/icon-previews'
        os.makedirs(OUT, exist_ok=True)
    for name, fn in STYLES.items():
        icon, bg = fn()
        if preview:
            cairosvg.svg2png(bytestring=to_svg(icon, bg).encode(), write_to=f'{OUT}/{name}.png')
            cairosvg.svg2png(bytestring=to_svg(monochrome(name), None).encode(), write_to=f'{OUT}/{name}_mono.png')
        if True:
            suffix = '' if name == 'lagoon' else f'_{name}'
            open(f'{R}/drawable/ic_launcher_foreground{suffix}.xml', 'w').write(to_vector(icon, f'Launcher icon foreground, {name} style.'))
            open(f'{R}/drawable/ic_launcher_monochrome{suffix}.xml', 'w').write(to_vector(monochrome(name), f'Themed (monochrome) launcher icon, {name} style.'))
            open(f'{R}/drawable/ic_launcher_background{suffix}.xml', 'w').write(bg_vector(bg, f'Launcher icon background, {name} style.'))
            adaptive = f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background{suffix}" />
    <foreground android:drawable="@drawable/ic_launcher_foreground{suffix}" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome{suffix}" />
</adaptive-icon>
'''
            for kind in ('ic_launcher', 'ic_launcher_round'):
                open(f'{R}/mipmap-anydpi-v26/{kind}{suffix}.xml', 'w').write(adaptive)
    print('done')
