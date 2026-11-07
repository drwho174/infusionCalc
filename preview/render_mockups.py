# -*- coding: utf-8 -*-
"""Предварительный визуальный дизайн v1.1:
S-0 дисклеймер, S-W вес при запуске, S-1 список, S-2 калькулятор (свет/тьма/предупреждение),
S-3 карточка (без МНН/Идентификации/Источника), S-4 редактор (пресеты концентраций)."""
import os, math
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.dirname(os.path.abspath(__file__))
W, H = 600, 1320
FONT_DIR = r"C:\Windows\Fonts"
FREG = os.path.join(FONT_DIR, "segoeui.ttf")
FBOLD = os.path.join(FONT_DIR, "segoeuib.ttf")
FSEMI = os.path.join(FONT_DIR, "seguisb.ttf")
if not os.path.exists(FSEMI):
    FSEMI = FBOLD

def f(path, size):
    return ImageFont.truetype(path, size)

LIGHT = dict(
    bg="#FAFDFA", surface="#FAFDFA", onSurface="#191C1B", onSurfaceVar="#3F4946",
    primary="#006B5B", onPrimary="#FFFFFF", primaryContainer="#79F8DC", onPrimaryContainer="#00201A",
    secondary="#006491", secondaryContainer="#C9E6FF", onSecondaryContainer="#001E2F",
    outline="#6F7976", outlineVar="#BFC9C4", surfaceLow="#F2F5F2", surfaceVar="#DBE5E0",
    error="#BA1A1A", errorContainer="#FFDAD6", onErrorContainer="#410002",
    okBg="#E8F5E9", okFg="#1B5E20", warnBg="#FFF8E1", warnFg="#6D4C00",
)
DARK = dict(
    bg="#191C1B", surface="#191C1B", onSurface="#E0E3E1", onSurfaceVar="#BFC9C4",
    primary="#59DBC1", onPrimary="#00382E", primaryContainer="#005144", onPrimaryContainer="#79F8DC",
    secondary="#89CEFF", secondaryContainer="#004C6E", onSecondaryContainer="#C9E6FF",
    outline="#89938F", outlineVar="#3F4946", surfaceLow="#141716", surfaceVar="#3F4946",
    error="#FFB4AB", errorContainer="#93000A", onErrorContainer="#FFDAD6",
    okBg="#13361C", okFg="#A5D6A7", warnBg="#3E3100", warnFg="#FFE082",
)

def rr(d, box, r, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)

def txt(d, xy, s, font, fill, anchor="la"):
    d.text(xy, s, font=font, fill=fill, anchor=anchor)

def tri_down(d, cx, cy, color, w=12, h=9):
    d.polygon([(cx-w//2, cy-h//2), (cx+w//2, cy-h//2), (cx, cy+h//2)], fill=color)

def tri_right(d, cx, cy, color, w=9, h=12):
    d.polygon([(cx-w//2, cy-h//2), (cx-w//2, cy+h//2), (cx+w//2, cy)], fill=color)

def star(d, cx, cy, r, fill=None, outline=None, width=2):
    pts = []
    for k in range(10):
        ang = -math.pi/2 + k*math.pi/5
        rad = r if k % 2 == 0 else r*0.45
        pts.append((cx+rad*math.cos(ang), cy+rad*math.sin(ang)))
    d.polygon(pts, fill=fill, outline=outline, width=width)

def status_bar(d, p):
    d.rectangle([0, 0, W, 40], fill=p["bg"])
    txt(d, (24, 20), "9:41", f(FBOLD, 19), p["onSurface"], "lm")
    rr(d, [W-76, 14, W-30, 26], 4, outline=p["onSurface"], width=2)
    d.rectangle([W-74, 16, W-44, 24], fill=p["onSurface"])
    d.rectangle([W-28, 18, W-24, 22], fill=p["onSurface"])

def weight_chip(d, p, x0, x1, y0=64, y1=116, label="70 кг"):
    rr(d, [x0, y0, x1, y1], 26, fill=p["secondaryContainer"])
    txt(d, ((x0+x1)//2, (y0+y1)//2), label, f(FSEMI, 18), p["onSecondaryContainer"], "mm")

def top_bar(d, p, title, back=False, settings=False, overflow=False, chip=False):
    y0, y1 = 40, 144
    d.rectangle([0, y0, W, y1], fill=p["bg"])
    if back:
        cx, cy = 46, (y0+y1)//2
        d.line([(cx+10, cy-14), (cx-8, cy), (cx+10, cy+14)], fill=p["onSurface"], width=4, joint="curve")
        tx = 84
    else:
        tx = 24
    txt(d, (tx, (y0+y1)//2), title, f(FSEMI, 26), p["onSurface"], "lm")
    right = W - 46
    if settings:
        for i in (-10, 0, 10):
            d.ellipse([right-3, (y0+y1)//2+i-3, right+3, (y0+y1)//2+i+3], fill=p["onSurface"])
    if overflow:
        for i in (-12, 0, 12):
            d.ellipse([right-3, (y0+y1)//2+i-3, right+3, (y0+y1)//2+i+3], fill=p["onSurface"])
    if chip:
        weight_chip(d, p, right-136, right-14)

def field(d, p, x0, y0, x1, label, value, suffix, hint=None, hint_color=None, focused=False):
    txt(d, (x0, y0), label, f(FREG, 19), p["onSurfaceVar"], "la")
    box = [x0, y0+30, x1, y0+106]
    border = p["primary"] if focused else p["outline"]
    rr(d, box, 14, fill=p["surface"], outline=border, width=3 if focused else 2)
    txt(d, (x0+18, y0+68), value, f(FBOLD, 30), p["onSurface"], "lm")
    txt(d, (x1-18, y0+68), suffix, f(FREG, 21), p["onSurfaceVar"], "rm")
    if hint:
        txt(d, (x0, y0+120), hint, f(FREG, 17), hint_color or p["onSurfaceVar"], "la")

def result_card(d, p, value, unit, status_text, status_bg, status_fg, subst):
    box = [20, 160, W-20, 360]
    rr(d, box, 20, fill=p["primaryContainer"])
    txt(d, (44, 190), "РЕЗУЛЬТАТ", f(FBOLD, 17), p["onPrimaryContainer"])
    txt(d, (44, 258), value, f(FBOLD, 56), p["onPrimaryContainer"], "lm")
    vw = d.textlength(value, font=f(FBOLD, 56))
    txt(d, (44+vw+12, 270), unit, f(FSEMI, 24), p["onPrimaryContainer"], "lm")
    cw = d.textlength(status_text, font=f(FSEMI, 17)) + 44
    rr(d, [44, 294, 44+cw, 328], 17, fill=status_bg)
    d.ellipse([58, 304, 70, 316], fill=status_fg)
    txt(d, (80, 311), status_text, f(FSEMI, 17), status_fg, "lm")
    txt(d, (44, 340), subst, f(FREG, 17), p["onPrimaryContainer"], "la")

def banner(d, p, y, title, body, bg, fg):
    bf = f(FREG, 16)
    maxw = W - 20 - 78 - 20
    words, lines, cur = body.split(), [], ""
    for w_ in words:
        t = (cur + " " + w_).strip()
        if d.textlength(t, font=bf) <= maxw:
            cur = t
        else:
            lines.append(cur); cur = w_
    if cur:
        lines.append(cur)
    h = 60 + len(lines)*22 + 12
    rr(d, [20, y, W-20, y+h], 16, fill=bg)
    cx, cy = 52, y+34
    d.ellipse([cx-13, cy-13, cx+13, cy+13], outline=fg, width=3)
    txt(d, (cx, cy), "!", f(FBOLD, 20), fg, "mm")
    txt(d, (78, y+26), title, f(FSEMI, 20), fg)
    yy = y+58
    for ln in lines:
        txt(d, (78, yy), ln, bf, fg); yy += 22
    return y + h + 16

def s0():
    p = LIGHT
    img = Image.new("RGB", (W, H), p["bg"]); d = ImageDraw.Draw(img)
    status_bar(d, p)
    cy = 300
    d.ellipse([W//2-52, cy-52, W//2+52, cy+52], fill=p["primaryContainer"])
    txt(d, (W//2, cy), "мл/ч", f(FBOLD, 26), p["onPrimaryContainer"], "mm")
    txt(d, (W//2, 420), "Калькулятор", f(FBOLD, 34), p["onSurface"], "mm")
    txt(d, (W//2, 462), "скорости инфузии", f(FBOLD, 34), p["onSurface"], "mm")
    lines = ["Считает скорость инфузии по дозе", "и дозу по скорости",
             "Концентрация — из предустановок препарата",
             "Справочный инструмент. Не заменяет", "назначение и проверку врачом"]
    y = 540
    for ln in lines:
        txt(d, (W//2, y), ln, f(FREG, 22), p["onSurfaceVar"], "mm"); y += 36
    rr(d, [20, 840, W-20, 940], 28, fill=p["primary"])
    txt(d, (W//2, 890), "Принять и продолжить", f(FSEMI, 23), p["onPrimary"], "mm")
    txt(d, (W//2, 990), "Полный текст дисклеймера", f(FSEMI, 20), p["primary"], "mm")
    txt(d, (W//2, 1230), "Работает офлайн. Данные остаются на устройстве.", f(FREG, 16), p["onSurfaceVar"], "mm")
    return img

def s_weight():
    p = LIGHT
    img = Image.new("RGB", (W, H), p["bg"]); d = ImageDraw.Draw(img)
    status_bar(d, p)
    txt(d, (24, 200), "Вес пациента", f(FBOLD, 34), p["onSurface"])
    txt(d, (24, 248), "Укажите вес один раз — он действует для всех препаратов", f(FREG, 18), p["onSurfaceVar"])
    txt(d, (20, 330), "Вес пациента, кг", f(FREG, 19), p["onSurfaceVar"])
    rr(d, [20, 360, W-20, 470], 14, fill=p["surface"], outline=p["primary"], width=3)
    txt(d, (38, 415), "70", f(FBOLD, 40), p["onSurface"], "lm")
    txt(d, (W-38, 415), "кг", f(FREG, 22), p["onSurfaceVar"], "rm")
    txt(d, (20, 486), "например, 70", f(FREG, 17), p["onSurfaceVar"])
    rr(d, [20, 1080, W-20, 1180], 28, fill=p["primary"])
    txt(d, (W//2, 1130), "Продолжить", f(FSEMI, 23), p["onPrimary"], "mm")
    txt(d, (W//2, 1220), "Вес действует для всех препаратов в этом сеансе", f(FREG, 16), p["onSurfaceVar"], "mm")
    txt(d, (W//2, 1246), "и не сохраняется на устройстве.", f(FREG, 16), p["onSurfaceVar"], "mm")
    return img

def s1():
    p = LIGHT
    img = Image.new("RGB", (W, H), p["bg"]); d = ImageDraw.Draw(img)
    status_bar(d, p)
    top_bar(d, p, "Препараты", settings=True, chip=True)
    rr(d, [20, 170, W-20, 240], 14, outline=p["outline"], width=2)
    d.ellipse([40, 194, 54, 208], outline=p["outline"], width=2); d.line([52, 206, 60, 214], fill=p["outline"], width=2)
    txt(d, (74, 205), "Поиск по названию", f(FREG, 20), p["onSurfaceVar"], "lm")
    chips = [("Все", True), ("Избранное", False), ("Мои", False)]
    x = 20
    for name, sel in chips:
        cw = d.textlength(name, font=f(FSEMI, 18)) + 50
        rr(d, [x, 262, x+cw, 308], 23, fill=p["secondaryContainer"] if sel else None,
           outline=None if sel else p["outline"], width=2)
        txt(d, (x+cw/2, 285), name, f(FSEMI, 18), p["onSecondaryContainer"] if sel else p["onSurface"], "mm")
        x += cw + 10
    rows = [("Норадреналин", "мкг/кг/мин · 40 мг / 250 мл", True, False),
            ("Адреналин", "мкг/кг/мин · 1 мг / 1 мл", False, False),
            ("Гепарин", "ЕД/ч · 25 000 ЕД / 5 мл", False, True),
            ("Вазопрессин", "ЕД/мин · 20 ЕД / мл", False, False),
            ("Амиодарон", "мг/мин · 50 мг / мл", False, False),
            ("Никардипин", "мг/ч · 0,1 мг / мл", False, False)]
    y = 330
    for name, sub, fav, custom in rows:
        txt(d, (24, y), name, f(FSEMI, 23), p["onSurface"])
        txt(d, (24, y+36), sub, f(FREG, 17), p["onSurfaceVar"])
        if custom:
            cw = d.textlength("Мой", font=f(FSEMI, 14)) + 24
            rx = W - 60 - cw
            rr(d, [rx, y+8, rx+cw, y+30], 11, fill=p["secondaryContainer"])
            txt(d, (rx+cw/2, y+19), "Мой", f(FSEMI, 14), p["onSecondaryContainer"], "mm")
        if fav:
            star(d, W-46, y+16, 13, fill=p["primary"])
        else:
            star(d, W-46, y+16, 13, outline=p["outline"], width=2)
        d.line([(20, y+82), (W-20, y+82)], fill=p["outlineVar"], width=1)
        y += 92
    d.ellipse([W-124, H-140, W-28, H-44], fill=p["primary"])
    txt(d, (W-76, H-92), "+", f(FBOLD, 46), p["onPrimary"], "mm")
    return img

def s2(dark=False, warn=False):
    p = DARK if dark else LIGHT
    img = Image.new("RGB", (W, H), p["bg"]); d = ImageDraw.Draw(img)
    status_bar(d, p)
    top_bar(d, p, "Норадреналин", back=True, overflow=True, chip=True)
    if warn:
        result_card(d, p, "≈ 30,0", "мл/ч", "выше диапазона", p["warnBg"], p["warnFg"],
                    "2,0 мкг/кг/мин · 70 кг · 4 мкг/мл")
        y = banner(d, p, 372, "Доза выше указанного диапазона",
                   "Диапазон: 0,05–0,2 мкг/кг/мин. Проверьте единицы, вес и концентрацию.",
                   p["warnBg"], p["warnFg"])
    else:
        result_card(d, p, "≈ 2,6", "мл/ч", "в диапазоне", p["okBg"], p["okFg"],
                    "0,1 мкг/кг/мин · 70 кг · 40 мг/250 мл")
        y = 380
    # concentration selector
    txt(d, (20, y), "Концентрация", f(FREG, 19), p["onSurfaceVar"])
    rr(d, [20, y+30, W-20, y+104], 14, fill=p["surface"], outline=p["primary"], width=3)
    txt(d, (38, y+67), "40 мг / 250 мл = 0,16 мг/мл", f(FSEMI, 20), p["onSurface"], "lm")
    tri_down(d, W-44, y+67, p["onSurfaceVar"])
    dy = y + 150
    field(d, p, 20, dy, W-20, "Доза", "2,0" if warn else "0,1", "мкг/кг/мин",
          hint="Диапазон: 0,05–0,2 мкг/кг/мин", hint_color=p["warnFg"] if warn else None, focused=True)
    ry = dy + 160
    field(d, p, 20, ry, W-20, "Скорость инфузии", "≈ 30,0" if warn else "≈ 2,6", "мл/ч",
          hint="пересчитано из дозы")
    fy = ry + 150
    tri_right(d, 27, fy+11, p["primary"])
    txt(d, (42, fy), "Показать расчёт", f(FSEMI, 19), p["primary"])
    txt(d, (20, fy+50), "Калькулятор не заменяет назначение врача", f(FREG, 16), p["primary"])
    return img

def s3():
    p = LIGHT
    img = Image.new("RGB", (W, H), p["bg"]); d = ImageDraw.Draw(img)
    status_bar(d, p)
    top_bar(d, p, "Норадреналин", back=True, overflow=True, chip=True)
    txt(d, (20, 170), "Норадреналин", f(FBOLD, 32), p["onSurface"])
    cw = d.textlength("Каталог", font=f(FSEMI, 15)) + 28
    rr(d, [20, 214, 20+cw, 242], 14, fill=p["surfaceVar"])
    txt(d, (20+cw/2, 228), "Каталог", f(FSEMI, 15), p["onSurface"], "mm")

    def section(y, title, rows):
        box = [20, y, W-20, y+52+len(rows)*40]
        rr(d, box, 18, fill=p["surfaceLow"])
        txt(d, (40, y+24), title, f(FSEMI, 21), p["onSurface"])
        yy = y + 66
        for k, v in rows:
            txt(d, (40, yy), k, f(FREG, 17), p["onSurfaceVar"])
            txt(d, (W-40, yy), v, f(FSEMI, 17), p["onSurface"], "ra")
            yy += 40
        return y + 52 + len(rows)*40 + 16

    y = section(266, "Дозирование", [("Схема", "мкг/мин"), ("Диапазон", "2–12 мкг/мин"),
                                     ("Округление", "0,1 мл/ч")])
    y = section(y, "Концентрации", [("4 мг / 4 мл", "1 мг/мл"),
                                    ("4 мг / 1000 мл", "4 мкг/мл")])
    y = section(y, "Заметки", [("Экстравазация", "некроз, антидот фентоламин"),
                               ("Отмена", "снижать постепенно")])
    rr(d, [20, H-120, W-20, H-40], 28, fill=p["primary"])
    txt(d, (W//2, H-80), "К расчёту", f(FSEMI, 23), p["onPrimary"], "mm")
    return img

def s4():
    p = LIGHT
    img = Image.new("RGB", (W, H), p["bg"]); d = ImageDraw.Draw(img)
    status_bar(d, p)
    top_bar(d, p, "Новый препарат", back=True)
    txt(d, (24, 158), "Отмена", f(FSEMI, 19), p["primary"])

    def row(y, label, value, suffix=None):
        txt(d, (20, y), label, f(FREG, 18), p["onSurfaceVar"])
        rr(d, [20, y+26, W-20, y+90], 14, fill=p["surface"], outline=p["outline"], width=2)
        txt(d, (36, y+58), value, f(FREG, 22), p["onSurface"], "lm")
        if suffix:
            txt(d, (W-36, y+58), suffix, f(FREG, 18), p["onSurfaceVar"], "rm")
        return y + 112

    y = 196
    y = row(y, "Название", "Норадреналин")
    # units dropdown
    txt(d, (20, y), "Единицы дозы", f(FREG, 18), p["onSurfaceVar"])
    rr(d, [20, y+26, W-20, y+90], 14, fill=p["surface"], outline=p["primary"], width=3)
    txt(d, (36, y+58), "мкг/кг/мин", f(FSEMI, 22), p["onSurface"], "lm")
    tri_down(d, W-44, y+58, p["onSurfaceVar"])
    y += 112
    # dose range from/to
    txt(d, (20, y), "Диапазон доз, от", f(FREG, 18), p["onSurfaceVar"])
    rr(d, [20, y+26, 290, y+90], 14, fill=p["surface"], outline=p["outline"], width=2)
    txt(d, (36, y+58), "0,05", f(FREG, 22), p["onSurface"], "lm")
    txt(d, (24, y+104), "до", f(FREG, 18), p["onSurfaceVar"])
    rr(d, [20, y+130, 290, y+194], 14, fill=p["surface"], outline=p["outline"], width=2)
    txt(d, (36, y+162), "0,2", f(FREG, 22), p["onSurface"], "lm")
    txt(d, (300, y+58), "мкг/кг/мин", f(FREG, 17), p["onSurfaceVar"], "lm")
    y += 214
    y = row(y, "Путь введения", "в/в")
    y = row(y, "Заметки", "Экстравазация → некроз")
    # presets
    txt(d, (20, y), "Концентрации", f(FSEMI, 20), p["onSurface"])
    yy = y + 34
    for label, val in [("4 мг / 4 мл", "1 мг/мл"), ("4 мг / 1000 мл", "4 мкг/мл")]:
        rr(d, [20, yy, W-20, yy+64], 14, fill=p["surfaceLow"])
        txt(d, (36, yy+18), label, f(FSEMI, 18), p["onSurface"])
        txt(d, (36, yy+42), val, f(FREG, 15), p["onSurfaceVar"])
        txt(d, (W-206, yy+32), "Изменить", f(FSEMI, 15), p["primary"], "lm")
        txt(d, (W-36, yy+32), "Удалить", f(FSEMI, 15), p["error"], "rm")
        yy += 74
    txt(d, (36, yy+8), "+ Добавить пресет", f(FSEMI, 18), p["primary"])
    rr(d, [20, H-110, W-20, H-30], 28, fill=p["primary"])
    txt(d, (W//2, H-70), "Сохранить", f(FSEMI, 23), p["onPrimary"], "mm")
    return img

if __name__ == "__main__":
    for name in os.listdir(OUT):
        if name.lower().endswith(".png"):
            os.remove(os.path.join(OUT, name))
    items = [
        ("01_s0_disclaimer", s0()),
        ("02_s_weight", s_weight()),
        ("03_s1_drugs", s1()),
        ("04_s2_calculator_light", s2()),
        ("05_s2_calculator_dark", s2(dark=True)),
        ("06_s2_warning", s2(warn=True)),
        ("07_s3_drug_card", s3()),
        ("08_s4_editor", s4()),
    ]
    for name, im in items:
        path = os.path.join(OUT, name + ".png")
        im.save(path)
        print("saved", path)
