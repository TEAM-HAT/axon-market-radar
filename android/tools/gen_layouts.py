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


def write(name, body):
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by tools/gen_layouts.py -->\n' + body.strip() + "\n")


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

print("layouts written to", os.path.normpath(OUT))
