//
//  ContentView.swift
//  Reader
//
//  Created by Aryan Raj on 08/07/26.
//

import SwiftUI
import ReaderShared

struct ContentView: View {
    private let screenState = SampleLibraryKt.sampleReaderScreenState()

    private var home: NonReaderHomeLayoutModel {
        screenState.toNonReaderHomeLayoutModel()
    }

    private var supportedFormats: String {
        SharedFileCapabilities.shared.supportedFormatsLabel(platform: ReaderPlatform.ios)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    header

                    if let book = home.continueBook {
                        ContinueReadingCard(book: book)
                    }

                    BookSection(
                        title: "Recent",
                        books: home.recentBooks,
                        emptyText: home.isLibraryEmpty ? "Import a book to start reading." : "No recent books yet."
                    )

                    BookSection(
                        title: "Active tabs",
                        books: home.activeTabs,
                        emptyText: "Tabs from shared state will appear here."
                    )
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 24)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Reader")
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Library")
                        .font(.largeTitle.bold())
                    Text("Powered by shared Kotlin models")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                Button {
                    // File import comes next; this placeholder keeps the first screen shaped like the real app.
                } label: {
                    Label("Import", systemImage: "plus")
                        .labelStyle(.iconOnly)
                        .font(.headline)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.borderedProminent)
                .accessibilityLabel("Import books")
            }

            Text(supportedFormats)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(3)
        }
    }
}

private struct ContinueReadingCard: View {
    let book: BookItem

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Continue reading")
                .font(.headline)
                .foregroundStyle(.secondary)

            HStack(spacing: 16) {
                BookCover(type: book.type)

                VStack(alignment: .leading, spacing: 8) {
                    Text(book.displayTitle)
                        .font(.title3.bold())
                        .lineLimit(2)

                    Text(book.displayAuthor)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)

                    ProgressView(value: book.progressValue)
                        .tint(.accentColor)

                    Text(book.progressLabel)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(18)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

private struct BookSection: View {
    let title: String
    let books: [BookItem]
    let emptyText: String

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text(title)
                    .font(.headline)
                Spacer()
                Text("\(books.count)")
                    .font(.caption.bold())
                    .foregroundStyle(.secondary)
            }

            if books.isEmpty {
                Text(emptyText)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16)
                    .background(Color(.secondarySystemGroupedBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            } else {
                VStack(spacing: 10) {
                    ForEach(books, id: \.id) { book in
                        BookRow(book: book)
                    }
                }
            }
        }
    }
}

private struct BookRow: View {
    let book: BookItem

    var body: some View {
        HStack(spacing: 14) {
            BookCover(type: book.type, compact: true)

            VStack(alignment: .leading, spacing: 4) {
                Text(book.displayTitle)
                    .font(.body.weight(.semibold))
                    .lineLimit(1)

                Text("\(book.displayAuthor) • \(book.formatLabel)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }

            Spacer()

            Text(book.progressLabel)
                .font(.caption.weight(.medium))
                .foregroundStyle(.secondary)
        }
        .padding(12)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

private struct BookCover: View {
    let type: FileType
    var compact = false

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: compact ? 8 : 10, style: .continuous)
                .fill(type.coverColor.gradient)

            VStack(spacing: compact ? 3 : 6) {
                Image(systemName: type.coverIconName)
                    .font(compact ? .headline : .title2)
                Text(type.shortLabel)
                    .font(compact ? .caption2.bold() : .caption.bold())
            }
            .foregroundStyle(.white)
        }
        .frame(width: compact ? 48 : 72, height: compact ? 64 : 96)
        .shadow(color: .black.opacity(0.08), radius: 8, y: 4)
    }
}

private extension BookItem {
    var displayTitle: String {
        title ?? displayName
    }

    var displayAuthor: String {
        author ?? sourceFolder ?? "Unknown author"
    }

    var progressValue: Double {
        guard let progressPercentage else { return 0 }
        return min(max(progressPercentage.doubleValue / 100.0, 0), 1)
    }

    var progressLabel: String {
        let percent = Int((progressValue * 100).rounded())
        return percent == 0 ? "Not started" : "\(percent)%"
    }

    var formatLabel: String {
        type.shortLabel
    }
}

private extension FileType {
    var shortLabel: String {
        switch self {
        case .pdf: return "PDF"
        case .epub: return "EPUB"
        case .docx: return "DOCX"
        case .mobi: return "MOBI"
        case .md: return "MD"
        case .txt: return "TXT"
        case .html: return "HTML"
        default: return name.uppercased()
        }
    }

    var coverIconName: String {
        switch self {
        case .pdf: return "doc.richtext"
        case .epub, .mobi: return "book.closed"
        case .docx, .odt, .fodt: return "doc.text"
        default: return "doc"
        }
    }

    var coverColor: Color {
        switch self {
        case .pdf: return .red
        case .epub: return .blue
        case .docx: return .indigo
        default: return .gray
        }
    }
}

#Preview {
    ContentView()
}
