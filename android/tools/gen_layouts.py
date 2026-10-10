#!/usr/bin/env python3
"""Writes the widget layouts in app/src/main/res/layout from shared pieces.

Run from android/: python3 tools/gen_layouts.py
App widgets only accept FrameLayout, LinearLayout, TextView and ImageView here,
so every row is spelled out and shown or hidden from code.
"""
import os
import re

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "layout")
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'


def tv(id_, text, size, color, family="sans-serif-condensed", bold=False, caps=False, spacing=None, lines=1,
       w="wrap_content", h="wrap_content", extra=""):
    a = {}
    if id_:
        a["android:id"] = f"@+id/{id_}"
    a.update({"android:layout_width": w, "android:layout_height": h, "android:fontFamily": family, "android:text": text,
              "android:textSize": size, "android:textColor": f"@color/{color}"})
    if bold:
        a["android:textStyle"] = "bold"
    if caps:
        a["android:textAllCaps"] = "true"
    if spacing:
        a["android:letterSpacing"] = spacing
    if lines:
        a["android:maxLines"] = str(lines)
        a["android:ellipsize"] = "end"
    for k, v in re.findall(r'(\S+?)="([^"]*)"', extra):
        a[k] = v  # extra settings win over the defaults above
    return "<TextView " + " ".join(f'{k}="{v}"' for k, v in a.items()) + " />"


def eyebrow(id_, text, color, size="10.5sp", w="wrap_content", extra=""):
    return tv(id_, text, size, color, bold=True, caps=True, spacing="0.12", w=w, extra=extra)


def head(title, eyebrow_text, stat, dark=False):
    eb = "sky" if dark else "blue"
    st = "on_navy_2" if dark else "ink_3"
    tc = "white" if dark else "ink"
    rule = "navy_line" if dark else "ink"
    return f'''
    <LinearLayout android:id="@+id/head" android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="vertical" android:paddingStart="16dp" android:paddingTop="14dp" android:paddingEnd="16dp">
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center_vertical" android:orientation="horizontal">
            <ImageView android:layout_width="14dp" android:layout_height="14dp" android:importantForAccessibility="no" android:src="@drawable/{"mark" if dark else "mark_solid"}" />
            {eyebrow("eyebrow", eyebrow_text, eb, w="0dp", extra='android:layout_weight="1" android:layout_marginStart="7dp"')}
            {eyebrow("stat", stat, st, extra='android:layout_marginStart="8dp"')}
        </LinearLayout>
        {tv("title", title, "22sp", tc, family="sans-serif-condensed-medium", caps=True, extra='android:layout_marginTop="6dp"')}
        <FrameLayout android:layout_width="match_parent" android:layout_height="1.5dp" android:layout_marginTop="8dp" android:background="@color/{rule}" />
    </LinearLayout>'''


def footer(more, dark=False):
    up = "on_navy_2" if dark else "ink_3"
    mo = "sky" if dark else "blue"
    return f'''
    <LinearLayout android:id="@+id/footer" android:layout_width="match_parent" android:layout_height="wrap_content"
        android:gravity="center_vertical" android:orientation="horizontal"
        android:paddingStart="16dp" android:paddingTop="6dp" android:paddingEnd="16dp" android:paddingBottom="13dp">
        {eyebrow("updated", "↻  Updated Fri 9 Oct", up, size="9.5sp", w="0dp", extra='android:layout_weight="1" android:paddingTop="6dp" android:paddingBottom="6dp"')}
        {eyebrow("more", more, mo, size="9.5sp", extra='android:paddingTop="6dp" android:paddingBottom="6dp" android:layout_marginStart="8dp"')}
    </LinearLayout>'''


SAMPLE_MOVES = [
    ("g_dot", "Partnership · Visa · 8 Oct", "Visa and Abu Dhabi's ADI Foundation agree to explore on-chain payments"),
    ("g_dia", "Regulation · ESMA · 8 Oct", "ESMA sets three-month wind-down for unauthorised stablecoin services"),
    ("g_dia", "Regulation · ADGM FSRA · 6 Oct", "ADGM FSRA consults on DeFi risk management guidance"),
    ("g_dia", "Regulation · VARA · 6 Oct", "VARA sets tougher standards for audits of VASP reserves"),
    ("g_dia", "Licence · VARA · 5 Oct", "Rain MENA FZE receives full VARA licence for exchange and brokerage"),
]


def move_row(i, dark=False, title_size="14.5sp"):
    g, meta, title = SAMPLE_MOVES[(i - 1) % len(SAMPLE_MOVES)]
    tint = 'android:tint="@color/sky"' if dark else ""
    div = "" if i == 1 else f'<FrameLayout android:id="@+id/div{i}" android:layout_width="match_parent" android:layout_height="1dp" android:background="@color/{"navy_line" if dark else "line"}" />'
    return f'''
        {div}
        <LinearLayout android:id="@+id/row{i}" android:layout_width="match_parent" android:layout_height="wrap_content"
            android:orientation="vertical" android:paddingTop="9dp" android:paddingBottom="9dp">
            <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center_vertical" android:orientation="horizontal">
                <ImageView android:id="@+id/glyph{i}" android:layout_width="8dp" android:layout_height="8dp" android:layout_marginEnd="7dp"
                    android:importantForAccessibility="no" android:src="@drawable/{g}" {tint} />
                {eyebrow(f"meta{i}", meta, "on_navy_2" if dark else "ink_3", size="9.5sp", w="0dp", extra='android:layout_weight="1" android:letterSpacing="0.1"')}
            </LinearLayout>
            {tv(f"title{i}", title.replace("'", "\\'"), title_size, "white" if dark else "ink", lines=2, w="match_parent", extra='android:layout_marginTop="3dp" android:lineSpacingMultiplier="1.02"')}
        </LinearLayout>'''


SAMPLE_COS = [("TE", "Tether", "Issuer · El Salvador", "8 Oct", "2 moves"), ("RA", "Rain", "On/off-ramp · Bahrain", "5 Oct", "1 move"),
              ("FA", "Fasset", "Settlement rails · United States", "16 Sep", "1 move"), ("DD", "DDSC", "Issuer · United Arab Emirates", "16 Sep", "1 move"),
              ("NI", "Network International", "Merchant gateway · UAE", "9 Sep", "Last move"), ("FU", "Fuze", "On/off-ramp · UAE", "8 Sep", "Last move")]


def co_row(i):
    ini, name, sub, last, n = SAMPLE_COS[(i - 1) % len(SAMPLE_COS)]
    div = "" if i == 1 else f'<FrameLayout android:id="@+id/div{i}" android:layout_width="match_parent" android:layout_height="1dp" android:background="@color/line" />'
    return f'''
        {div}
        <LinearLayout android:id="@+id/row{i}" android:layout_width="match_parent" android:layout_height="wrap_content"
            android:gravity="center_vertical" android:orientation="horizontal" android:paddingTop="8dp" android:paddingBottom="8dp">
            {tv(f"mono{i}", ini, "13sp", "blue_deep", bold=True, spacing="0.05", w="38dp", h="38dp", extra='android:gravity="center" android:background="@drawable/bg_mono"')}
            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:layout_marginStart="12dp" android:orientation="vertical">
                {tv(f"name{i}", name, "16sp", "ink", family="sans-serif-condensed-medium", w="match_parent")}
                {tv(f"sub{i}", sub, "12sp", "ink_3", family="sans-serif", w="match_parent", extra='android:layout_marginTop="1dp"')}
            </LinearLayout>
            <LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginStart="10dp" android:gravity="end" android:orientation="vertical">
                {eyebrow(f"last{i}", last, "ink", size="12sp", extra='android:letterSpacing="0.08"')}
                {eyebrow(f"n{i}", n, "blue", size="9sp", extra='android:layout_marginTop="2dp" android:letterSpacing="0.1"')}
            </LinearLayout>
        </LinearLayout>'''


# The black-and-white look: every widget colour mapped by its role. Surfaces that are blue or navy
# in colour turn black, accents on white turn black, accents on black turn light grey.
MONO = {
    "blue": ("#000000", "brand surfaces, and accents on white"),
    "blue_deep": ("#111111", "initials on the light squares"),
    "sky": ("#C9C9C9", "accents on black"),
    "pale": ("#E8E8E8", "initials squares, ring in the mark"),
    "mist": ("#F0F0F0", "band behind the regions"),
    "on_blue_2": ("#B5B5B5", "second text on black"),
    "ink": ("#000000", "text on white"),
    "ink_2": ("#474747", "quieter text on white"),
    "ink_3": ("#6E6E6E", "labels and dates on white"),
    "line": ("#E4E4E4", "dividers on white"),
    "navy": ("#000000", "the licences widget"),
    "navy_line": ("#2C2C2C", "dividers on black"),
    "on_navy_2": ("#A8A8A8", "second text on black"),
    "white": ("#FFFFFF", "white"),
}
# Drawables that carry colour get a black-and-white twin named mono_<name>.
MONO_DRAWABLES = ["bg_blue", "bg_blue_top", "bg_chip", "bg_mono", "bg_navy", "bg_white", "chart_preview",
                  "g_dia", "g_dot", "g_sq", "mark", "mark_solid"]
RES = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")


def to_mono(xml):
    xml = re.sub(r"@color/([a-z_0-9]+)", lambda m: "@color/mono_" + m.group(1) if m.group(1) in MONO else m.group(0), xml)
    return re.sub(r"@drawable/([a-z_0-9]+)", lambda m: "@drawable/mono_" + m.group(1) if m.group(1) in MONO_DRAWABLES else m.group(0), xml)


def write(name, body, twin=True):
    """Writes a layout and its black-and-white twin (widget_x.xml and widget_x_mono.xml), with the same ids.
    A layout whose colours are all set from code (the carousel) needs no twin."""
    head_ = '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by tools/gen_layouts.py -->\n'
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
        f.write(head_ + body.strip() + "\n")
    if twin:
        with open(os.path.join(OUT, name.replace(".xml", "_mono.xml")), "w", encoding="utf-8") as f:
            f.write(head_ + to_mono(body.strip()) + "\n")


def write_res(folder, name, body):
    with open(os.path.join(RES, folder, name), "w", encoding="utf-8") as f:
        f.write(body.strip() + "\n")


def write_mono_resources():
    with open(os.path.join(RES, "values", "colors_mono.xml"), "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by tools/gen_layouts.py: the widgets in black and white -->\n<resources>\n')
        for k, (v, why) in MONO.items():
            f.write(f'    <color name="mono_{k}">{v}</color> <!-- {why} -->\n')
        f.write("</resources>\n")
    for d in MONO_DRAWABLES:
        src = open(os.path.join(RES, "drawable", d + ".xml"), encoding="utf-8").read()
        with open(os.path.join(RES, "drawable", "mono_" + d + ".xml"), "w", encoding="utf-8") as f:
            f.write("<!-- Generated by tools/gen_layouts.py from " + d + ".xml -->\n" + to_mono(src))


# 1. Daily briefing, 4 x 2 -------------------------------------------------------------
write("widget_brief.xml", f'''
<FrameLayout {NS} android:id="@android:id/background" android:layout_width="match_parent" android:layout_height="match_parent"
    android:background="@drawable/bg_blue" android:clipToOutline="true">
    <LinearLayout android:id="@+id/tap" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical"
        android:paddingStart="16dp" android:paddingTop="14dp" android:paddingEnd="16dp" android:paddingBottom="8dp">
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center_vertical" android:orientation="horizontal">
            <ImageView android:layout_width="16dp" android:layout_height="16dp" android:importantForAccessibility="no" android:src="@drawable/mark" />
            {eyebrow("eyebrow", "Daily briefing · Fri 9 Oct", "on_blue_2", w="0dp", extra='android:layout_weight="1" android:layout_marginStart="7dp"')}
        </LinearLayout>
        <LinearLayout android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" android:layout_marginTop="8dp" android:orientation="horizontal">
            <LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content" android:orientation="vertical">
                <LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content" android:gravity="bottom" android:orientation="horizontal">
                    {tv("count", "6", "60sp", "white", family="sans-serif-condensed-light", lines=0, extra='android:includeFontPadding="false"')}
                    {tv("count_label", "Moves\\nin 7 days", "16sp", "white", caps=True, lines=2, extra='android:layout_marginStart="9dp" android:layout_marginBottom="5dp" android:lineSpacingMultiplier="0.88"')}
                </LinearLayout>
                <LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginTop="6dp" android:gravity="center_vertical" android:orientation="horizontal">
                    {eyebrow("dates", "3–9 Oct", "on_blue_2", size="9.5sp", extra='android:letterSpacing="0.1"')}
                    {eyebrow("chip", "+2 new", "blue", size="9sp", extra='android:layout_marginStart="7dp" android:background="@drawable/bg_chip" android:paddingStart="5dp" android:paddingEnd="5dp" android:paddingTop="1dp" android:paddingBottom="1dp" android:letterSpacing="0.1"')}
                </LinearLayout>
            </LinearLayout>
            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:layout_marginStart="14dp" android:orientation="vertical">
                {tv("headline", "Dubai gave Rain a full exchange licence and tightened reserve audits. Europe set a three-month deadline for licensed firms to unwind unauthorised stablecoins.", "13sp", "on_blue_2", family="sans-serif", lines=6, w="match_parent", extra='android:lineSpacingMultiplier="1.08"')}
                {eyebrow("mix", "4 regulatory actions · 1 licence · 1 partnership", "on_blue_2", size="9sp", w="match_parent", extra='android:layout_marginTop="9dp" android:maxLines="2" android:letterSpacing="0.1"')}
            </LinearLayout>
        </LinearLayout>
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center_vertical" android:orientation="horizontal">
            {eyebrow("updated", "↻  Updated Fri 9 Oct", "on_blue_2", size="9.5sp", w="0dp", extra='android:layout_weight="1" android:paddingTop="6dp" android:paddingBottom="6dp" android:letterSpacing="0.1"')}
            {eyebrow("more", "Open →", "white", size="9.5sp", extra='android:paddingTop="6dp" android:paddingBottom="6dp" android:letterSpacing="0.1"')}
        </LinearLayout>
    </LinearLayout>
</FrameLayout>''')

# 2. Moves, 4 x 4 ------------------------------------------------------------------------
write("widget_moves.xml", f'''
<LinearLayout {NS} android:id="@android:id/background" android:layout_width="match_parent" android:layout_height="match_parent"
    android:background="@drawable/bg_white" android:clipToOutline="true" android:orientation="vertical">
    {head("Latest moves", "Market Radar · Moves", "6 in 7 days")}
    <LinearLayout android:id="@+id/list" android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1"
        android:orientation="vertical" android:paddingStart="16dp" android:paddingEnd="16dp">
        {"".join(move_row(i) for i in range(1, 6))}
    </LinearLayout>
    {footer("All moves →")}
</LinearLayout>''')

# 3. Companies, 4 x 4 --------------------------------------------------------------------
write("widget_companies.xml", f'''
<LinearLayout {NS} android:id="@android:id/background" android:layout_width="match_parent" android:layout_height="match_parent"
    android:background="@drawable/bg_white" android:clipToOutline="true" android:orientation="vertical">
    {head("Most active", "Market Radar · Companies", "Last 30 days")}
    <LinearLayout android:id="@+id/list" android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1"
        android:orientation="vertical" android:paddingStart="16dp" android:paddingEnd="16dp" android:paddingTop="2dp">
        {"".join(co_row(i) for i in range(1, 7))}
    </LinearLayout>
    {footer("All companies →")}
</LinearLayout>''')


# 4. Licences, 4 x 4, navy ---------------------------------------------------------------
def tile(i, n, name, where):
    sep = "" if i == 1 else f'<FrameLayout android:id="@+id/tsep{i}" android:layout_width="1dp" android:layout_height="match_parent" android:background="@color/navy_line" />'
    return f'''
            {sep}
            <LinearLayout android:id="@+id/tile{i}" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1"
                android:orientation="vertical" android:paddingStart="{0 if i == 1 else 10}dp" android:paddingEnd="6dp">
                {tv(f"tile_n{i}", n, "34sp", "white", family="sans-serif-condensed-light", lines=0, extra='android:includeFontPadding="false"')}
                {eyebrow(f"tile_name{i}", name, "white", size="12.5sp", w="match_parent", extra='android:layout_marginTop="6dp" android:letterSpacing="0.06"')}
                {tv(f"tile_sub{i}", where, "11.5sp", "on_navy_2", family="sans-serif", w="match_parent")}
            </LinearLayout>'''


write("widget_licences.xml", f'''
<LinearLayout {NS} android:id="@android:id/background" android:layout_width="match_parent" android:layout_height="match_parent"
    android:background="@drawable/bg_navy" android:clipToOutline="true" android:orientation="vertical">
    {head("Regulators", "Market Radar · Licences", "12 months", dark=True)}
    <LinearLayout android:id="@+id/tiles" android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal"
        android:paddingStart="16dp" android:paddingTop="12dp" android:paddingEnd="16dp" android:paddingBottom="12dp">
        {tile(1, "07", "CBUAE", "UAE")}{tile(2, "04", "CSSF", "Luxembourg")}{tile(3, "04", "VARA", "Dubai")}
    </LinearLayout>
    <FrameLayout android:layout_width="match_parent" android:layout_height="1dp" android:layout_marginStart="16dp" android:layout_marginEnd="16dp" android:background="@color/navy_line" />
    <LinearLayout android:id="@+id/list" android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1"
        android:orientation="vertical" android:paddingStart="16dp" android:paddingEnd="16dp">
        {"".join(move_row(i, dark=True) for i in range(1, 5))}
    </LinearLayout>
    {footer("All licences →", dark=True)}
</LinearLayout>''')


# 5. Trends, 4 x 3 ------------------------------------------------------------------------
def stat(i, n, label, sep_color, n_color, l_color, size="30sp"):
    sep = "" if i == 1 else f'<FrameLayout android:layout_width="1dp" android:layout_height="match_parent" android:background="@color/{sep_color}" />'
    return f'''
            {sep}
            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical"
                android:paddingStart="{0 if i == 1 else 8}dp" android:paddingEnd="4dp">
                {tv(f"s_n{i}", n, size, n_color, family="sans-serif-condensed-light", lines=0, extra='android:includeFontPadding="false"')}
                {eyebrow(f"s_l{i}", label, l_color, size="9sp", w="match_parent", extra='android:maxLines="2" android:layout_marginTop="5dp" android:letterSpacing="0.08"')}
            </LinearLayout>'''


write("widget_trends.xml", f'''
<LinearLayout {NS} android:id="@android:id/background" android:layout_width="match_parent" android:layout_height="match_parent"
    android:background="@drawable/bg_white" android:clipToOutline="true" android:orientation="vertical">
    <LinearLayout android:id="@+id/top" android:layout_width="match_parent" android:layout_height="wrap_content" android:background="@drawable/bg_blue_top"
        android:orientation="vertical" android:paddingStart="16dp" android:paddingTop="14dp" android:paddingEnd="16dp" android:paddingBottom="14dp">
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center_vertical" android:orientation="horizontal">
            <ImageView android:layout_width="14dp" android:layout_height="14dp" android:importantForAccessibility="no" android:src="@drawable/mark" />
            {eyebrow("eyebrow", "Market Radar · Trends", "on_blue_2", w="0dp", extra='android:layout_weight="1" android:layout_marginStart="7dp"')}
        </LinearLayout>
        {tv("title", "60 moves since Oct 2025", "21sp", "white", caps=True, extra='android:layout_marginTop="6dp"')}
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="10dp" android:orientation="horizontal">
            {stat(1, "19", "Licences", "sky", "white", "on_blue_2")}{stat(2, "11", "Regulatory actions", "sky", "white", "on_blue_2")}{stat(3, "23", "Commercial moves", "sky", "white", "on_blue_2")}{stat(4, "7", "Capital moves", "sky", "white", "on_blue_2")}
        </LinearLayout>
    </LinearLayout>
    <LinearLayout android:id="@+id/bottom" android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" android:orientation="vertical"
        android:paddingStart="16dp" android:paddingTop="10dp" android:paddingEnd="16dp" android:paddingBottom="12dp">
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal">
            {eyebrow(None, "Moves per month", "blue", size="9.5sp", w="0dp", extra='android:layout_weight="1"')}
            {eyebrow("cap", "Oct 2025 – Oct 2026", "ink_3", size="9.5sp", extra='android:letterSpacing="0.1"')}
        </LinearLayout>
        <ImageView android:id="@+id/chart" android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1"
            android:layout_marginTop="6dp" android:scaleType="fitXY" android:src="@drawable/chart_preview"
            android:contentDescription="Moves per month over the last thirteen months" />
    </LinearLayout>
</LinearLayout>''')


# 6. Dashboard, 4 x 5 ---------------------------------------------------------------------
def region(i, n, label):
    return f'''
            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical">
                {tv(f"r_n{i}", n, "24sp", "ink", family="sans-serif-condensed-light", lines=0, extra='android:includeFontPadding="false"')}
                {eyebrow(f"r_l{i}", label, "ink_3", size="8.5sp", w="match_parent", extra='android:layout_marginTop="3dp" android:letterSpacing="0.1"')}
            </LinearLayout>'''


write("widget_dashboard.xml", f'''
<LinearLayout {NS} android:id="@android:id/background" android:layout_width="match_parent" android:layout_height="match_parent"
    android:background="@drawable/bg_white" android:clipToOutline="true" android:orientation="vertical">
    <LinearLayout android:id="@+id/top" android:layout_width="match_parent" android:layout_height="wrap_content" android:background="@drawable/bg_blue_top"
        android:orientation="vertical" android:paddingStart="16dp" android:paddingTop="14dp" android:paddingEnd="16dp" android:paddingBottom="13dp">
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center_vertical" android:orientation="horizontal">
            <ImageView android:layout_width="14dp" android:layout_height="14dp" android:importantForAccessibility="no" android:src="@drawable/mark" />
            {eyebrow("eyebrow", "Market Radar · Daily briefing · Fri 9 Oct", "on_blue_2", w="0dp", extra='android:layout_weight="1" android:layout_marginStart="7dp"')}
        </LinearLayout>
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="8dp" android:gravity="bottom" android:orientation="horizontal">
            {tv("count", "6", "56sp", "white", family="sans-serif-condensed-light", lines=0, extra='android:includeFontPadding="false"')}
            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:layout_marginStart="12dp" android:layout_marginBottom="4dp" android:orientation="vertical">
                {tv("count_label", "Moves in 7 days", "17sp", "white", caps=True)}
                <LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginTop="3dp" android:gravity="center_vertical" android:orientation="horizontal">
                    {eyebrow("dates", "3–9 Oct 2026", "on_blue_2", size="9.5sp", extra='android:letterSpacing="0.1"')}
                    {eyebrow("chip", "+2 new", "blue", size="9sp", extra='android:layout_marginStart="8dp" android:background="@drawable/bg_chip" android:paddingStart="5dp" android:paddingEnd="5dp" android:paddingTop="1dp" android:paddingBottom="1dp" android:letterSpacing="0.1"')}
                </LinearLayout>
            </LinearLayout>
        </LinearLayout>
        {tv("headline", "Dubai gave Rain a full exchange licence and tightened reserve audits. Europe set a three-month deadline for licensed firms to unwind unauthorised stablecoins.", "13sp", "on_blue_2", family="sans-serif", lines=3, w="match_parent", extra='android:layout_marginTop="9dp" android:lineSpacingMultiplier="1.06"')}
    </LinearLayout>
    <LinearLayout android:id="@+id/regions" android:layout_width="match_parent" android:layout_height="wrap_content" android:background="@color/mist"
        android:orientation="horizontal" android:paddingStart="16dp" android:paddingTop="9dp" android:paddingEnd="16dp" android:paddingBottom="9dp">
        {region(1, "1", "Europe")}{region(2, "5", "Middle East")}{region(3, "0", "N. America")}{region(4, "0", "Global")}
    </LinearLayout>
    <LinearLayout android:id="@+id/list" android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical"
        android:paddingStart="16dp" android:paddingTop="10dp" android:paddingEnd="16dp">
        {eyebrow(None, "Latest moves", "blue", size="9.5sp")}
        {"".join(move_row(i, title_size="14sp") for i in range(1, 4))}
    </LinearLayout>
    <LinearLayout android:id="@+id/split" android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical"
        android:paddingStart="16dp" android:paddingEnd="16dp">
        <FrameLayout android:layout_width="match_parent" android:layout_height="1dp" android:background="@color/line" />
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:paddingTop="9dp" android:paddingBottom="4dp">
            <LinearLayout android:id="@+id/co_col" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical">
                {eyebrow(None, "Most active · 30 days", "blue", size="9sp")}
                {tv("co_lines", "Tether  2\\nRain  1\\nFasset  1", "13sp", "ink", lines=3, w="match_parent", extra='android:layout_marginTop="3dp" android:lineSpacingMultiplier="1.05"')}
            </LinearLayout>
            <LinearLayout android:id="@+id/reg_col" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:layout_marginStart="12dp" android:orientation="vertical">
                {eyebrow(None, "Regulators · 12 months", "blue", size="9sp")}
                {tv("reg_lines", "CBUAE  7\\nCSSF  4\\nVARA  4", "13sp", "ink", lines=3, w="match_parent", extra='android:layout_marginTop="3dp" android:lineSpacingMultiplier="1.05"')}
            </LinearLayout>
        </LinearLayout>
    </LinearLayout>
    <LinearLayout android:id="@+id/trend" android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" android:orientation="vertical"
        android:paddingStart="16dp" android:paddingTop="6dp" android:paddingEnd="16dp">
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal">
            {eyebrow(None, "Moves per month", "blue", size="9sp", w="0dp", extra='android:layout_weight="1"')}
            {eyebrow("cap", "60 since Oct 2025", "ink_3", size="9sp", extra='android:letterSpacing="0.1"')}
        </LinearLayout>
        <ImageView android:id="@+id/chart" android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1"
            android:layout_marginTop="4dp" android:scaleType="fitXY" android:src="@drawable/chart_preview"
            android:contentDescription="Moves per month over the last thirteen months" />
    </LinearLayout>
    <FrameLayout android:id="@+id/spacer" android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" android:visibility="gone" />
    {footer("Open the radar →")}
</LinearLayout>''')



# 7. Carousel, 4 x 4: the app's deck of cards ------------------------------------------------
# The deck itself is drawn from code with the app's typeface (DeckArt), as pictures; the layouts here
# hold those pictures, the taps over them and the controls round them. The picker shows a picture of
# the widget (drawable-nodpi/widget_carousel_preview.png, written by WidgetRenderTest).


def write_deck_resources():
    write_res("drawable", "bg_deck.xml", """<!-- Generated by tools/gen_layouts.py -->
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#000000" />
    <corners android:radius="26dp" />
</shape>""")
    # The app's deck controls (Icons in ui/Icons.kt): white at 80%, and the left arrow halfway down at 55%.
    for name, path, colour in (("up", "M6,14.5 L12,8.5 L18,14.5", "#CCFFFFFF"), ("down", "M6,9.5 L12,15.5 L18,9.5", "#CCFFFFFF"),
                               ("left", "M14.5,6 L8.5,12 L14.5,18", "#8CFFFFFF")):
        write_res("drawable", f"ic_deck_{name}.xml", f"""<!-- Generated by tools/gen_layouts.py -->
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:strokeColor="{colour}" android:strokeWidth="2" android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000" android:pathData="{path}" />
</vector>""")
    write_res("drawable", "ic_deck_grid.xml", """<!-- Generated by tools/gen_layouts.py -->
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:strokeColor="#CCFFFFFF" android:strokeWidth="1.6" android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000" android:pathData="M5,5h5.5v5.5H5zM13.5,5H19v5.5h-5.5zM5,13.5h5.5V19H5zM13.5,13.5H19V19h-5.5z" />
</vector>""")


# The frame: the top line (a picture of the app's), the controls where the app has them (a left arrow
# halfway down, and up, grid and down at the bottom left), and the deck as an Android card stack
# (StackView), which the launcher swipes one card at a time. Each card of the stack is the whole deck
# drawn at that card (widget_carousel_card), so a swipe up shows the deck one card further on, as the
# app's deck does. The stack is drawn upside down and each card the right way up again: that puts the
# next cards under the open one and makes a swipe up go forward, as in the app. The stack is 1.25 times
# the size of its area (weightSum 0.8), so the cards it keeps waiting behind the front one, offset by a
# tenth of its size, stay hidden behind the front card's black margin. From Android 12 the code scales
# the top line and the controls with the deck.
def control(id_, src, desc, pad):
    return (f'<ImageView android:id="@+id/{id_}" android:layout_width="34dp" android:layout_height="34dp" android:padding="{pad}" '
            f'android:src="@drawable/{src}" android:contentDescription="{desc}" />')


write("widget_carousel.xml", f"""
<FrameLayout {NS} android:id="@android:id/background" android:layout_width="match_parent" android:layout_height="match_parent"
    android:background="@drawable/bg_deck" android:clipToOutline="true">
    <LinearLayout android:id="@+id/deckrow" android:layout_width="match_parent" android:layout_height="match_parent" android:layout_marginTop="42dp"
        android:orientation="horizontal">
        <FrameLayout android:layout_width="40dp" android:layout_height="match_parent" />
        <FrameLayout android:id="@+id/deck_area" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1"
            android:layout_marginStart="-4dp" android:layout_marginTop="-4dp" android:layout_marginEnd="4dp" android:layout_marginBottom="-16dp">
            <LinearLayout android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="horizontal" android:weightSum="0.8">
                <LinearLayout android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1" android:orientation="vertical" android:weightSum="0.8">
                    <StackView android:id="@+id/stack" style="@android:style/Widget.Material.StackView" android:theme="@style/RadarStack"
                        android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1"
                        android:scaleY="-1" android:loopViews="false" android:animateFirstView="false" />
                </LinearLayout>
            </LinearLayout>
            <LinearLayout android:id="@+id/empty" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical"
                android:paddingStart="6dp" android:paddingTop="10dp" android:paddingEnd="16dp">
                {tv("empty_text", "Loading the latest moves…", "15sp", "white", family="sans-serif", extra='android:layout_marginTop="4dp"')}
            </LinearLayout>
        </FrameLayout>
    </LinearLayout>
    <FrameLayout android:id="@+id/controls" android:layout_width="match_parent" android:layout_height="match_parent">
        <ImageView android:id="@+id/back" android:layout_width="34dp" android:layout_height="34dp" android:layout_gravity="center_vertical|start"
            android:layout_marginStart="10dp" android:padding="7dp" android:src="@drawable/ic_deck_left" android:contentDescription="Previous card" />
        <LinearLayout android:id="@+id/cluster" android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_gravity="bottom|start"
            android:layout_marginStart="10dp" android:layout_marginBottom="18dp" android:orientation="vertical">
            {control("up", "ic_deck_up", "Previous card", "8dp")}
            {control("grid", "ic_deck_grid", "Open the lists in the app", "8dp")}
            {control("down", "ic_deck_down", "Next card", "8dp")}
        </LinearLayout>
    </FrameLayout>
    <FrameLayout android:id="@+id/top" android:layout_width="match_parent" android:layout_height="42dp" android:background="#000000">
        <ImageView android:id="@+id/topline" android:layout_width="match_parent" android:layout_height="match_parent" android:scaleType="matrix" />
    </FrameLayout>
</FrameLayout>""", twin=False)


# One card of the stack: a picture of the deck at one card, from the top-left of a black card the size of
# the stack's front card (the picture runs to the stack's right edge, so the dots show), with a tap over
# each card that shows, in the top-left 7/8 (sized from code from Android 12; before that the first tap
# covers the whole card). The card is turned upside down to undo the stack's flip. The
# stack measures its cards "at most" its size, so an empty image with a huge minimum size makes each card
# take all of it, and with two full-size children the frame then lays the deck out at exactly that size.
FILL = '<ImageView android:layout_width="match_parent" android:layout_height="match_parent" android:minWidth="4000dp" android:minHeight="4000dp" android:importantForAccessibility="no" />'
ZONES = "\n".join(
    f'                    <FrameLayout android:id="@+id/z{k}" android:layout_width="match_parent" '
    + ('android:layout_height="match_parent" />' if k == 0 else 'android:layout_height="0dp" android:visibility="gone" />')
    for k in range(10))
write("widget_carousel_card.xml", f"""
<FrameLayout {NS} android:id="@+id/item" android:layout_width="match_parent" android:layout_height="match_parent"
    android:background="#000000" android:scaleY="-1">
    {FILL}
    <ImageView android:id="@+id/art" android:layout_width="match_parent" android:layout_height="match_parent" android:scaleType="matrix" />
    <LinearLayout android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical">
        <LinearLayout android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="875" android:orientation="horizontal">
            <FrameLayout android:id="@+id/cell" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="875">
                <LinearLayout android:id="@+id/zones" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical">
                    <FrameLayout android:id="@+id/zgap" android:layout_width="match_parent" android:layout_height="0dp" />
{ZONES}
                </LinearLayout>
            </FrameLayout>
            <FrameLayout android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="125" />
        </LinearLayout>
        <FrameLayout android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="125" />
    </LinearLayout>
</FrameLayout>""", twin=False)

# Shown for a moment while a card of the stack loads: a black card of full size, like the deck.
write("widget_carousel_loading.xml", f"""
<FrameLayout {NS} android:layout_width="match_parent" android:layout_height="match_parent" android:background="#000000">
    {FILL}
    <FrameLayout android:layout_width="match_parent" android:layout_height="match_parent" />
</FrameLayout>""", twin=False)


write_mono_resources()
write_deck_resources()
print("layouts written to", os.path.normpath(OUT))
