# Draws the numbered callouts (positions from Capture.java) over each raw snapshot
# and renders the result to <images>/<name>.png with headless Chrome.
import json, os, subprocess, sys

work, images = sys.argv[1], sys.argv[2]
chrome = os.environ.get("CHROME", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
boxes = json.load(open(os.path.join(work, "callouts.json")))
os.makedirs(images, exist_ok=True)

CSS = """
html,body{margin:0;width:1100px;height:620px;overflow:hidden}
.n{position:absolute;width:22px;height:22px;border-radius:50%;background:#d6336c;color:#fff;
   font:700 12px -apple-system,Helvetica,sans-serif;display:flex;align-items:center;justify-content:center;
   box-shadow:0 0 0 2px #fff,0 1px 4px rgba(0,0,0,.35)}
"""

# Where a number goes when the default (left of the part) would cover something.
# l = left, r = right, t = above, b = below, c = on the top-left corner.
SIDE = {
    "01-login": {"3": "r", "7": "r", "9": "r"},
    "02-register-code": {"2": "t", "3": "t"},
    "03-forgot-password": {"6": "r"},
    "05-lobby": {"4": "l", "5": "t", "8": "b", "9": "b", "10": "r"},
    "06-lobby-matching": {"1": "l"},
    "07-lobby-room": {"1": "l", "3": "t", "4": "t", "5": "t"},
    "08-game-caro": {"6": "r"},
    "10-resign-confirm": {"1": "b", "2": "b"},
    "11-game-over": {"2": "r"},
    "12-disconnected": {"1": "r", "2": "l", "3": "l"},
}

def place(name, no, x, y, w, h):
    side = SIDE.get(name, {}).get(no)
    if side is None:
        side = "c" if (w > 260 or h > 110) else ("l" if x > 34 else "r")
    return {
        "l": (x - 30, y + h / 2 - 11),
        "r": (x + w + 8, y + h / 2 - 11),
        "t": (x + w / 2 - 11, y - 28),
        "b": (x + w / 2 - 11, y + h + 6),
        "c": (x - 9, y - 9),
    }[side]

for name, parts in boxes.items():
    dots = []
    for no, (x, y, w, h) in parts.items():
        px, py = place(name, no, x, y, w, h)
        px, py = max(2, min(px, 1100 - 24)), max(2, min(py, 620 - 24))
        dots.append(f'<div class="n" style="left:{px:.0f}px;top:{py:.0f}px">{no}</div>')
    html = os.path.join(work, name + ".html")
    with open(html, "w") as f:
        f.write(f'<!doctype html><html><head><style>{CSS}</style></head><body>'
                f'<img src="{name}.png" style="position:absolute;left:0;top:0;width:1100px;height:620px">'
                + "".join(dots) + '</body></html>')
    subprocess.run([chrome, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--allow-file-access-from-files",
                    "--force-device-scale-factor=1", "--window-size=1100,620",
                    "--screenshot=" + os.path.join(images, name + ".png"), "file://" + html],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
    print("images/" + name + ".png")
