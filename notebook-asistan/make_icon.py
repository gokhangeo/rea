"""icon.ico üretir (CI'da ve yerelde çalışır)."""
from PIL import Image, ImageDraw, ImageFilter

S = 512
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
grad = Image.new("RGBA", (S, S))
a, b = (124, 92, 255), (34, 211, 238)
px = grad.load()
for y in range(S):
    for x in range(S):
        t = (x + y) / (2 * S)
        px[x, y] = tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)
mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle((16, 16, S - 16, S - 16), radius=120, fill=255)
img.paste(grad, (0, 0), mask)
d = ImageDraw.Draw(img)
w = 30
# kitap
d.rounded_rectangle((130, 110, 382, 360), radius=26, outline="white", width=w)
d.line((130, 360, 130, 410), fill="white", width=w)
d.rounded_rectangle((130, 360, 382, 412), radius=26, outline="white", width=w)
# yıldız
cx, cy, r1, r2 = 256, 232, 70, 22
pts = []
import math
for i in range(8):
    ang = math.pi / 4 * i - math.pi / 2
    r = r1 if i % 2 == 0 else r2
    pts.append((cx + r * math.cos(ang), cy + r * math.sin(ang)))
d.polygon(pts, fill="white")
img.save("icon.png")
img.save("icon.ico", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
print("icon.ico ok")
