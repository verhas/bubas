#!/usr/bin/env python3
"""Build a PDF of the BUBAS book from the chapter files.

The order of the chapters, their numbers and the part structure are read from BOOK.md, which is
the only place any of that is written down. Adding a chapter means adding a file and a line there;
this script needs no editing.

The chapter files are mdship sources, so they carry INCLUDE markers around content generated from
the code. The markers are stripped and the generated content kept, which means the PDF shows the
same compiled programs and captured transcripts every other rendering of the book shows.
"""

import os
import re
import sys

from reportlab.lib.colors import HexColor
from reportlab.lib.enums import TA_CENTER, TA_JUSTIFY, TA_LEFT
from reportlab.lib.pagesizes import A5
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import cm
from reportlab.platypus import (
    BaseDocTemplate, Frame, KeepTogether, PageBreak, PageTemplate, Paragraph,
    Preformatted, Spacer, Table, TableStyle,
)

# ─── Page ───
PAGE_W, PAGE_H = A5
MARGIN_TOP = 1.9 * cm
MARGIN_BOTTOM = 2.0 * cm
MARGIN_LEFT = 1.8 * cm
MARGIN_RIGHT = 1.6 * cm
TEXT_W = PAGE_W - MARGIN_LEFT - MARGIN_RIGHT

# ─── Fonts ───
# The base-14 fonts, so that nothing has to be installed and the script runs anywhere reportlab
# does. Courier matters more here than in most books: a third of these pages are code.
BODY = "Times-Roman"
BODY_BOLD = "Times-Bold"
BODY_ITALIC = "Times-Italic"
HEAD = "Helvetica-Bold"
MONO = "Courier"

CODE_SIZE = 7.2
CODE_LEADING = 8.6
# How many monospace characters fit across the text block. Courier's advance is 0.6 em.
CODE_COLS = int((TEXT_W - 0.7 * cm) / (CODE_SIZE * 0.6))

# ─── Colours ───
DARK = HexColor("#1a1a1a")
MED = HexColor("#444444")
LIGHT = HexColor("#8a8a8a")
RULE = HexColor("#cccccc")
CODE_BG = HexColor("#f4f4f2")
CODE_EDGE = HexColor("#e0e0dc")

BOOK_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_PATH = os.path.join(BOOK_DIR, "BUBAS.pdf")

TITLE = "BUBAS"
SUBTITLE = "Orchestration for people who own the rules"


# ─── Reading the book's own table of contents ───

def read_order():
    """Parts and chapters, in order, from BOOK.md.

    Returns a list of ('part', number, title) and ('chapter', number, title, filename).
    """
    source = os.path.join(BOOK_DIR, "BOOK.md")
    with open(source, encoding="utf-8") as handle:
        text = handle.read()

    items = []
    for line in text.split("\n"):
        part = re.match(r"^## Part (\d+)\s*[—-]\s*(.+)$", line)
        if part:
            items.append(("part", int(part.group(1)), part.group(2).strip()))
            continue
        chapter = re.match(r"^### (\d+)\.\s*\[([^\]]+)\]\(([^)]+)\)", line)
        if chapter:
            items.append(("chapter", int(chapter.group(1)),
                          chapter.group(2).strip(), chapter.group(3).strip()))
    return items


# ─── Cleaning an mdship source ───

def strip_markers(text):
    """Remove mdship's own markers, keeping the content they generated."""
    # An INCLUDE's opening marker carries its configuration and a checksum; the content follows it.
    text = re.sub(r"<!--INCLUDE.*?-->\n", "", text, flags=re.S)
    text = text.replace("<!--/INCLUDE-->\n", "")
    text = re.sub(r"<!--\s*/?abstract\s*-->\n", "", text)
    # Anything else in comment form is machinery, not prose.
    text = re.sub(r"<!--.*?-->\n?", "", text, flags=re.S)
    return text


# ─── Inline formatting ───

def escape(text):
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def inline(text):
    """Markdown inline formatting to reportlab's mini-markup."""
    # Code spans are protected before anything else touches them.
    spans = []

    def hold(match):
        spans.append(match.group(1))
        return f"\x00{len(spans) - 1}\x00"

    text = re.sub(r"`([^`]+)`", hold, text)
    text = escape(text)
    # A link to another chapter is meaningless on paper; keep the words.
    text = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)
    text = re.sub(r"\*\*([^*]+)\*\*", r"<b>\1</b>", text)
    text = re.sub(r"(?<![*\w])\*([^*]+)\*(?!\*)", r"<i>\1</i>", text)

    def release(match):
        code = escape(spans[int(match.group(1))])
        return f'<font face="{MONO}" size="8.4">{code}</font>'

    return re.sub(r"\x00(\d+)\x00", release, text)


def wrap_code(line):
    """Soft-wrap one code line, marking continuations so a reader can see what happened.

    Every pass must shorten what is left, or a line whose only space sits near its start makes the
    loop oscillate for ever: cut early, re-add the continuation prefix, and end up no shorter than
    before. So a break is only taken well past the prefix, and a line with nowhere good to break is
    broken hard.
    """
    if len(line) <= CODE_COLS:
        return [line]

    indent = min(len(line) - len(line.lstrip()), CODE_COLS // 3)
    prefix = " " * indent + "\u21aa "
    least = len(prefix) + 8

    out, rest = [], line
    while len(rest) > CODE_COLS:
        cut = rest.rfind(" ", least, CODE_COLS)
        if cut < least:
            cut = CODE_COLS
        out.append(rest[:cut].rstrip())
        rest = prefix + rest[cut:].lstrip()
    out.append(rest)
    return out


# ─── Styles ───

def create_styles():
    return {
        "BookTitle": ParagraphStyle("BookTitle", fontName=HEAD, fontSize=30, leading=34,
                                    alignment=TA_CENTER, textColor=DARK, spaceAfter=6),
        "BookSubtitle": ParagraphStyle("BookSubtitle", fontName=BODY_ITALIC, fontSize=12,
                                       leading=16, alignment=TA_CENTER, textColor=MED),
        "HalfTitle": ParagraphStyle("HalfTitle", fontName=HEAD, fontSize=20, leading=24,
                                    alignment=TA_CENTER, textColor=MED),
        "PartTitle": ParagraphStyle("PartTitle", fontName=HEAD, fontSize=22, leading=26,
                                    alignment=TA_CENTER, textColor=DARK, spaceBefore=8),
        "PartLabel": ParagraphStyle("PartLabel", fontName=BODY, fontSize=9, leading=12,
                                    alignment=TA_CENTER, textColor=LIGHT, spaceAfter=10),
        "ChapterNumber": ParagraphStyle("ChapterNumber", fontName=BODY, fontSize=8.5, leading=11,
                                        alignment=TA_LEFT, textColor=LIGHT, spaceAfter=4),
        "ChapterTitle": ParagraphStyle("ChapterTitle", fontName=HEAD, fontSize=17, leading=21,
                                       alignment=TA_LEFT, textColor=DARK, spaceAfter=14),
        "Abstract": ParagraphStyle("Abstract", fontName=BODY_ITALIC, fontSize=9.4, leading=13,
                                   alignment=TA_LEFT, textColor=MED, spaceAfter=12,
                                   leftIndent=0.4 * cm, rightIndent=0.4 * cm),
        "Section": ParagraphStyle("Section", fontName=HEAD, fontSize=10.5, leading=14,
                                  textColor=DARK, spaceBefore=13, spaceAfter=5),
        # The vocabulary documents the chapters include carry their own ### entries.
        "Subsection": ParagraphStyle("Subsection", fontName=BODY_BOLD, fontSize=9.6, leading=13,
                                     textColor=DARK, spaceBefore=8, spaceAfter=3),
        "Body": ParagraphStyle("Body", fontName=BODY, fontSize=9.6, leading=13.4,
                               alignment=TA_JUSTIFY, textColor=DARK, spaceAfter=6,
                               firstLineIndent=0.45 * cm),
        "BodyFirst": ParagraphStyle("BodyFirst", fontName=BODY, fontSize=9.6, leading=13.4,
                                    alignment=TA_JUSTIFY, textColor=DARK, spaceAfter=6),
        "Bullet": ParagraphStyle("Bullet", fontName=BODY, fontSize=9.6, leading=13.4,
                                 alignment=TA_LEFT, textColor=DARK, spaceAfter=3,
                                 leftIndent=0.55 * cm, bulletIndent=0.2 * cm),
        "Quote": ParagraphStyle("Quote", fontName=BODY_ITALIC, fontSize=9.6, leading=13.4,
                                alignment=TA_LEFT, textColor=MED, spaceBefore=5, spaceAfter=7,
                                leftIndent=0.7 * cm, rightIndent=0.4 * cm),
        "Code": ParagraphStyle("Code", fontName=MONO, fontSize=CODE_SIZE, leading=CODE_LEADING,
                               textColor=DARK),
        "TableCell": ParagraphStyle("TableCell", fontName=BODY, fontSize=8.2, leading=10.6,
                                    textColor=DARK),
        "TableHead": ParagraphStyle("TableHead", fontName=BODY_BOLD, fontSize=8.2, leading=10.6,
                                    textColor=DARK),
        "TocPart": ParagraphStyle("TocPart", fontName=HEAD, fontSize=10, leading=15,
                                  textColor=DARK, spaceBefore=9, spaceAfter=2),
        "TocChapter": ParagraphStyle("TocChapter", fontName=BODY, fontSize=9, leading=12.4,
                                     textColor=MED, leftIndent=0.5 * cm),
        "Front": ParagraphStyle("Front", fontName=BODY, fontSize=8.6, leading=12,
                                alignment=TA_CENTER, textColor=MED),
    }


# ─── Blocks ───

def code_block(lines, styles):
    """A fenced block, boxed and tinted so it reads as a specimen rather than as prose."""
    wrapped = []
    for line in lines:
        wrapped.extend(wrap_code(line.rstrip()))
    body = Preformatted("\n".join(wrapped) or " ", styles["Code"])
    table = Table([[body]], colWidths=[TEXT_W])
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), CODE_BG),
        ("BOX", (0, 0), (-1, -1), 0.4, CODE_EDGE),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ]))
    return table


def md_table(rows, styles):
    cells = []
    for index, row in enumerate(rows):
        style = styles["TableHead"] if index == 0 else styles["TableCell"]
        cells.append([Paragraph(inline(c), style) for c in row])
    widths = [TEXT_W / len(cells[0])] * len(cells[0])
    table = Table(cells, colWidths=widths, repeatRows=1)
    table.setStyle(TableStyle([
        ("LINEBELOW", (0, 0), (-1, 0), 0.6, MED),
        ("LINEBELOW", (0, 1), (-1, -2), 0.25, RULE),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 3),
        ("RIGHTPADDING", (0, 0), (-1, -1), 3),
        ("TOPPADDING", (0, 0), (-1, -1), 3),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
    ]))
    return table


def split_row(line):
    return [c.strip() for c in line.strip().strip("|").split("|")]


def chapter_flowables(number, title, text, styles):
    """One chapter, from its cleaned markdown."""
    flow = [PageBreak(),
            Paragraph(f"CHAPTER {number}", styles["ChapterNumber"])]
    lines = strip_markers(text).split("\n")
    i = 0
    seen_title = False
    after_heading = True
    in_abstract = False

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        if not stripped:
            i += 1
            continue

        if stripped.startswith("# ") and not seen_title:
            flow.append(Paragraph(inline(stripped[2:]), styles["ChapterTitle"]))
            seen_title = True
            in_abstract = True
            i += 1
            continue

        if stripped == "---":
            in_abstract = False
            after_heading = True
            i += 1
            continue

        # Any heading, at any level. The catch-all matters: a chapter that includes a generated
        # vocabulary document inherits its ### entries, and a parser with no branch for them stops
        # advancing rather than failing, which is the worst way to be wrong.
        heading = re.match(r"^(#{2,6})\s+(.*)$", stripped)
        if heading:
            depth = len(heading.group(1))
            style = "Section" if depth == 2 else "Subsection"
            flow.append(Paragraph(inline(heading.group(2)), styles[style]))
            after_heading = True
            i += 1
            continue

        if stripped.startswith("#"):
            # A lone # or a level we do not style: keep the words, drop the marker.
            flow.append(Paragraph(inline(stripped.lstrip("#").strip()), styles["Subsection"]))
            i += 1
            continue

        if stripped.startswith("```"):
            i += 1
            block = []
            while i < len(lines) and not lines[i].strip().startswith("```"):
                block.append(lines[i])
                i += 1
            i += 1
            flow.append(Spacer(1, 3))
            flow.append(code_block(block, styles))
            flow.append(Spacer(1, 6))
            after_heading = False
            continue

        if stripped.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                row = split_row(lines[i])
                if not all(re.fullmatch(r":?-{2,}:?", c) for c in row):
                    rows.append(row)
                i += 1
            flow.append(Spacer(1, 3))
            flow.append(md_table(rows, styles))
            flow.append(Spacer(1, 7))
            after_heading = False
            continue

        if stripped.startswith("> "):
            quoted = []
            while i < len(lines) and lines[i].strip().startswith(">"):
                quoted.append(lines[i].strip().lstrip(">").strip())
                i += 1
            flow.append(Paragraph(inline(" ".join(quoted)), styles["Quote"]))
            after_heading = False
            continue

        if stripped.startswith("- "):
            while i < len(lines) and lines[i].strip().startswith("- "):
                item = [lines[i].strip()[2:]]
                i += 1
                while (i < len(lines) and lines[i].strip()
                       and not lines[i].strip().startswith(("- ", "#", "|", "```", ">"))
                       and lines[i].startswith("  ")):
                    item.append(lines[i].strip())
                    i += 1
                flow.append(Paragraph(inline(" ".join(item)), styles["Bullet"],
                                      bulletText="•"))
            flow.append(Spacer(1, 4))
            after_heading = False
            continue

        para = []
        while (i < len(lines) and lines[i].strip()
               and not lines[i].strip().startswith(("#", "```", "|", "> ", "- "))
               and lines[i].strip() != "---"):
            para.append(lines[i].strip())
            i += 1
        if para:
            joined = " ".join(para)
            if in_abstract:
                flow.append(Paragraph(inline(joined), styles["Abstract"]))
            else:
                flow.append(Paragraph(inline(joined),
                                      styles["BodyFirst" if after_heading else "Body"]))
                after_heading = False
    return flow


# ─── Document ───

class BookTemplate(BaseDocTemplate):
    """Running heads: the book on the left page, the current chapter on the right."""

    def __init__(self, filename, **kwargs):
        self._chapter = ""
        self._front = True
        super().__init__(filename, **kwargs)

    def afterFlowable(self, flowable):
        if isinstance(flowable, Paragraph):
            if flowable.style.name == "ChapterTitle":
                self._chapter = flowable.getPlainText()
                self._front = False
            elif flowable.style.name == "PartTitle":
                self._chapter = flowable.getPlainText()

    def afterPage(self):
        if self._front or self.page <= 4:
            return
        self.canv.saveState()
        self.canv.setFont(BODY, 8)
        self.canv.setFillColor(LIGHT)
        self.canv.drawCentredString(PAGE_W / 2, MARGIN_BOTTOM - 0.95 * cm, str(self.page))
        y = PAGE_H - MARGIN_TOP + 0.55 * cm
        self.canv.setFont(BODY_ITALIC, 7)
        if self.page % 2 == 0:
            self.canv.drawString(MARGIN_LEFT, y, TITLE)
        elif self._chapter:
            self.canv.drawRightString(PAGE_W - MARGIN_RIGHT, y, self._chapter)
        self.canv.setStrokeColor(RULE)
        self.canv.setLineWidth(0.3)
        self.canv.line(MARGIN_LEFT, y - 4, PAGE_W - MARGIN_RIGHT, y - 4)
        self.canv.restoreState()


def front_matter(styles):
    story = [Spacer(1, PAGE_H * 0.34), Paragraph(TITLE, styles["HalfTitle"]), PageBreak(),
             Spacer(1, 6), PageBreak(),
             Spacer(1, PAGE_H * 0.20), Paragraph(TITLE, styles["BookTitle"]),
             Spacer(1, 8), Paragraph(SUBTITLE, styles["BookSubtitle"]),
             Spacer(1, PAGE_H * 0.16), Paragraph("Peter Verhas", styles["BookSubtitle"]),
             PageBreak(), Spacer(1, PAGE_H * 0.55),
             Paragraph("Every program, transcript and compiler message in this book is produced "
                       "by the build that produced the book. Nothing here was typed from memory.",
                       styles["Front"]),
             PageBreak()]
    return story


def toc(items, styles):
    story = [Paragraph("Contents", styles["ChapterTitle"])]
    for item in items:
        if item[0] == "part":
            story.append(Paragraph(f"Part {item[1]} &nbsp;&nbsp;{escape(item[2])}",
                                   styles["TocPart"]))
        else:
            story.append(Paragraph(f"{item[1]}.&nbsp;&nbsp;{escape(item[2])}",
                                   styles["TocChapter"]))
    story.append(PageBreak())
    return story


def part_divider(number, title, styles):
    return [PageBreak(), Spacer(1, PAGE_H * 0.30),
            Paragraph(f"PART {number}", styles["PartLabel"]),
            Paragraph(escape(title), styles["PartTitle"])]


def build():
    styles = create_styles()
    items = read_order()
    chapters = [i for i in items if i[0] == "chapter"]
    print(f"  {len(chapters)} chapters, {len([i for i in items if i[0] == 'part'])} parts")

    story = front_matter(styles) + toc(items, styles)

    for item in items:
        if item[0] == "part":
            story.extend(part_divider(item[1], item[2], styles))
            continue
        _, number, title, filename = item
        path = os.path.join(BOOK_DIR, filename)
        if not os.path.exists(path):
            print(f"  MISSING: {filename}", file=sys.stderr)
            continue
        with open(path, encoding="utf-8") as handle:
            story.extend(chapter_flowables(number, title, handle.read(), styles))

    doc = BookTemplate(OUTPUT_PATH, pagesize=A5,
                       topMargin=MARGIN_TOP, bottomMargin=MARGIN_BOTTOM,
                       leftMargin=MARGIN_LEFT, rightMargin=MARGIN_RIGHT,
                       title=f"{TITLE}: {SUBTITLE}", author="Peter Verhas")
    frame = Frame(MARGIN_LEFT, MARGIN_BOTTOM, TEXT_W,
                  PAGE_H - MARGIN_TOP - MARGIN_BOTTOM, id="normal")
    doc.addPageTemplates([PageTemplate(id="normal", frames=frame)])
    doc.build(story)

    size = os.path.getsize(OUTPUT_PATH) / 1024
    print(f"  {OUTPUT_PATH}")
    print(f"  {doc.page} pages, {size:.0f} kB, {CODE_COLS} columns of code")


if __name__ == "__main__":
    build()
