"""Generates the PDF fixture used by the vertical-scroll instrumentation tests.

The fixture deliberately mixes two leading regimes on the same page:

* a **tight-leading** block (10pt step on a 12pt font box, so consecutive lines share roughly 3.8pt of
  vertical band), and
* a **normal-leading** block (24pt step, essentially no shared band).

The tight block is the regression case for the highlight stroke: the old line merge accepted any
positive vertical overlap above 10% of the shorter box, which merged adjacent tight lines into a
single rect and left the first line with no underline at all. See
`docs/pdf-vertical-scroll-and-highlight-audit.md` §3 H3.

Run from the repo root:  python3 scripts/make_scroll_fixture.py
"""

import os

PAGES = 8
PAGE_WIDTH = 612
PAGE_HEIGHT = 792


def escape(text):
    return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")


def text_ops(lines):
    ops = []
    for y, text in lines:
        ops.append(
            "BT /F1 12 Tf 45 %d Td (%s) Tj ET" % (y, escape(text))
        )
    return "\n".join(ops)


def page_lines(page_index):
    lines = []
    # Tight leading: many lines packed together.
    y = 770
    line_number = 0
    while y > 430:
        lines.append(
            (
                y,
                "Tight line %d page %d alpha beta gamma delta epsilon zeta eta theta."
                % (line_number, page_index + 1),
            )
        )
        line_number += 1
        y -= 10
    # Normal leading.
    y = 395
    while y > 95:
        lines.append(
            (y, "Normal line page %d with plenty of readable text here." % (page_index + 1))
        )
        y -= 24
    return lines


def build():
    streams = [text_ops(page_lines(i)) for i in range(PAGES)]

    objects = [
        "<< /Type /Catalog /Pages 2 0 R >>",
        None,  # pages dict, filled once the kid ids are known
    ]
    font_id = 3 + 2 * PAGES
    kids = " ".join("%d 0 R" % (3 + 2 * i) for i in range(PAGES))
    objects[1] = "<< /Type /Pages /Kids [%s] /Count %d >>" % (kids, PAGES)

    for i, stream in enumerate(streams):
        page_id = 3 + 2 * i
        content_id = page_id + 1
        objects.append(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %d %d] "
            "/Resources << /Font << /F1 %d 0 R >> >> /Contents %d 0 R >>"
            % (PAGE_WIDTH, PAGE_HEIGHT, font_id, content_id)
        )
        objects.append(
            "<< /Length %d >>\nstream\n%s\nendstream" % (len(stream), stream)
        )

    objects.append("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")

    out = bytearray(b"%PDF-1.4\n")
    offsets = []
    for index, obj in enumerate(objects, start=1):
        offsets.append(len(out))
        out += ("%d 0 obj\n%s\nendobj\n" % (index, obj)).encode("latin-1")

    xref_offset = len(out)
    out += ("xref\n0 %d\n" % (len(objects) + 1)).encode("latin-1")
    out += b"0000000000 65535 f \n"
    for offset in offsets:
        out += ("%010d 00000 n \n" % offset).encode("latin-1")
    out += (
        "trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n"
        % (len(objects) + 1, xref_offset)
    ).encode("latin-1")
    return bytes(out)


def main():
    data = build()
    target = os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "app",
        "src",
        "androidTest",
        "assets",
        "scroll_fixture.pdf",
    )
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with open(target, "wb") as handle:
        handle.write(data)
    print("wrote %s (%d bytes, %d pages)" % (target, len(data), PAGES))


if __name__ == "__main__":
    main()