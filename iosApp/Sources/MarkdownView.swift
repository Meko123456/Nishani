import SwiftUI
import Shared

/// Renders the shared `MarkdownParser`'s output natively with SwiftUI — mirroring the
/// Android `MarkdownText` composable. Parsing is shared; only this rendering is per-platform.
struct MarkdownView: View {
    let blocks: [MdBlock]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { _, block in
                blockView(block)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // Each rendered block is tagged with the kind of block the parser said it was. The tags cost
    // nothing at runtime and they are what lets a UI test assert the *shape* of the parse — one
    // heading and two bullets, rather than a wall of text that happens to contain the right words.
    // Without them "# Shopping" rendered verbatim as a paragraph looks exactly like a heading.
    @ViewBuilder
    private func blockView(_ block: MdBlock) -> some View {
        switch block {
        case let h as MdBlockHeading:
            inlineText(h.spans)
                .font(h.level == 1 ? .title : (h.level == 2 ? .title2 : .title3))
                .bold()
                .accessibilityIdentifier("md-heading-\(h.level)")
        case let p as MdBlockParagraph:
            inlineText(p.spans)
                .accessibilityIdentifier("md-paragraph")
        case let b as MdBlockBulletItem:
            HStack(alignment: .top, spacing: 6) {
                Text("•")
                inlineText(b.spans)
                    .accessibilityIdentifier("md-bullet")
            }
        case let o as MdBlockOrderedItem:
            HStack(alignment: .top, spacing: 6) {
                Text("\(o.number).")
                inlineText(o.spans)
                    .accessibilityIdentifier("md-ordered")
            }
        case let q as MdBlockQuote:
            inlineText(q.spans)
                .italic()
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.gray.opacity(0.15))
                .cornerRadius(6)
                .accessibilityIdentifier("md-quote")
        case let c as MdBlockCodeBlock:
            Text(c.code)
                .font(.system(.body, design: .monospaced))
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.gray.opacity(0.15))
                .cornerRadius(6)
                .accessibilityIdentifier("md-code")
        case is MdBlockDivider:
            Divider()
                .accessibilityIdentifier("md-divider")
        default:
            EmptyView()
        }
    }

    private func inlineText(_ spans: [MdSpan]) -> Text {
        spans.reduce(Text("")) { acc, span in
            switch span {
            case let t as MdSpanText: return acc + Text(t.text)
            case let b as MdSpanBold: return acc + Text(b.text).bold()
            case let i as MdSpanItalic: return acc + Text(i.text).italic()
            case let c as MdSpanCode: return acc + Text(c.text).font(.system(.body, design: .monospaced))
            case let s as MdSpanStrikethrough: return acc + Text(s.text).strikethrough()
            case let l as MdSpanLink: return acc + Text(l.text).foregroundColor(.blue).underline()
            default: return acc
            }
        }
    }
}
