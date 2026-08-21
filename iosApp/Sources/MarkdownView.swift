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

    @ViewBuilder
    private func blockView(_ block: MdBlock) -> some View {
        switch block {
        case let h as MdBlockHeading:
            inlineText(h.spans)
                .font(h.level == 1 ? .title : (h.level == 2 ? .title2 : .title3))
                .bold()
        case let p as MdBlockParagraph:
            inlineText(p.spans)
        case let b as MdBlockBulletItem:
            HStack(alignment: .top, spacing: 6) {
                Text("•")
                inlineText(b.spans)
            }
        case let q as MdBlockQuote:
            inlineText(q.spans)
                .italic()
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.gray.opacity(0.15))
                .cornerRadius(6)
        case let c as MdBlockCodeBlock:
            Text(c.code)
                .font(.system(.body, design: .monospaced))
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.gray.opacity(0.15))
                .cornerRadius(6)
        case is MdBlockDivider:
            Divider()
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
            default: return acc
            }
        }
    }
}
