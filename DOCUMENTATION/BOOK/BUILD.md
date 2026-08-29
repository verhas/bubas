# Building the PDF

## Prerequisites

Python 3 with `reportlab`:

```bash
python3 -m venv .venv
.venv/bin/pip install reportlab
```

No fonts to install. The script uses the PDF base-14 faces — Times for the body, Helvetica for
headings, Courier for code — so it runs anywhere reportlab does. That is a deliberate difference
from a literary book, where the typeface is part of the point: a third of these pages are code, and
a monospace face that is guaranteed present matters more here than a handsome serif.

## Generate

```bash
.venv/bin/python DOCUMENTATION/BOOK/generate_pdf.py
```

It writes `DOCUMENTATION/BOOK/BUBAS.pdf` and reports the page count.

## What the script does

**Reads the order from `BOOK.md`.** Chapter numbers, titles, filenames and the part structure are
parsed out of the table of contents. There is no chapter list in the script, so adding a chapter
means adding a file and a line in `BOOK.md` — nothing here needs editing. That follows
[AUTHORING.md D17](../AUTHORING.md): the chapter list lives in exactly one file.

**Strips mdship's markers and keeps what they generated.** The chapter sources carry `INCLUDE`
blocks whose content is produced by the build — compiled programs, captured transcripts, real
compiler messages. The markers go; the content stays. So the PDF shows the same verified examples
as every other rendering of the book.

**Renders** chapter openings, part dividers, an A5 page with running heads and page numbers, a
generated table of contents, and the markdown the chapters use: headings to four levels, tables,
block quotes, bullet lists, and inline bold, italic and code.

**Boxes code blocks** in a tinted frame at 7.2pt Courier, seventy columns wide. Longer lines are
soft-wrapped with a `↪` continuation marker rather than overflowing the page.

## After editing a chapter

Regenerate the documents first, then the PDF:

```bash
mvn -q verify                                    # the examples the chapters include
mdship update DOCUMENTATION/BOOK/*.md            # fill the INCLUDE blocks
.venv/bin/python DOCUMENTATION/BOOK/generate_pdf.py
```

Running the PDF step alone is fine when only prose changed. When code changed, the first two steps
are what make the change reach the page.

## Adjusting the result

Everything worth changing is a constant near the top of the script.

| Constant | Does |
|---|---|
| `PAGE_W`, `PAGE_H` | page size — `A5` by default, `A4` or `B5` for a wider text block |
| `MARGIN_*` | margins, and so the text width |
| `CODE_SIZE` | code type size; `CODE_COLS` follows from it and the text width |
| `TITLE`, `SUBTITLE` | what the title page and running heads say |
| `CODE_BG`, `CODE_EDGE` | the tint and border of a code block |

`CODE_COLS` is computed rather than set: it is how many Courier characters fit across the text
block. Widening the page or shrinking the type gives more columns and less wrapping.
