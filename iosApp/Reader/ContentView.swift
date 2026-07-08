//
//  ContentView.swift
//  Reader
//
//  Created by Aryan Raj on 08/07/26.
//

import SwiftUI
import ReaderShared
import UniformTypeIdentifiers

struct ContentView: View {
    private let bridge = ReaderIosBridge()
    @State private var isImportPickerPresented = false

    var body: some View {
        ReaderComposeHost(
            bridge: bridge,
            onImportBooks: {
                isImportPickerPresented = true
            }
        )
        .ignoresSafeArea(.keyboard)
        .fileImporter(
            isPresented: $isImportPickerPresented,
            allowedContentTypes: [.item],
            allowsMultipleSelection: true
        ) { result in
            switch result {
            case .success(let urls):
                let importedFiles = urls.compactMap { url in
                    copyImportedFileToAppSupport(url)
                }
                bridge.recordImportedFiles(
                    fileNames: importedFiles.map(\.name),
                    filePaths: importedFiles.map(\.path)
                )
            case .failure:
                bridge.recordImportedFiles(fileNames: [], filePaths: [])
            }
        }
    }
}

private struct ImportedReaderFile {
    let name: String
    let path: String
}

private func copyImportedFileToAppSupport(_ sourceURL: URL) -> ImportedReaderFile? {
    let didStartAccessing = sourceURL.startAccessingSecurityScopedResource()
    defer {
        if didStartAccessing {
            sourceURL.stopAccessingSecurityScopedResource()
        }
    }

    do {
        let fileManager = FileManager.default
        let appSupport = try fileManager.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let importsDirectory = appSupport.appendingPathComponent("Imports", isDirectory: true)
        try fileManager.createDirectory(at: importsDirectory, withIntermediateDirectories: true)

        let fileName = sourceURL.lastPathComponent
        let destinationURL = importsDirectory.appendingPathComponent(uniqueImportedFileName(fileName))
        if fileManager.fileExists(atPath: destinationURL.path) {
            try fileManager.removeItem(at: destinationURL)
        }
        try fileManager.copyItem(at: sourceURL, to: destinationURL)
        return ImportedReaderFile(name: fileName, path: destinationURL.path)
    } catch {
        return nil
    }
}

private func uniqueImportedFileName(_ fileName: String) -> String {
    let source = URL(fileURLWithPath: fileName)
    let baseName = source.deletingPathExtension().lastPathComponent
    let fileExtension = source.pathExtension
    let suffix = UUID().uuidString.prefix(8)
    if fileExtension.isEmpty {
        return "\(baseName)-\(suffix)"
    }
    return "\(baseName)-\(suffix).\(fileExtension)"
}

private struct ReaderComposeHost: UIViewControllerRepresentable {
    let bridge: ReaderIosBridge
    let onImportBooks: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        ReaderIosAppKt.readerComposeViewController(
            bridge: bridge,
            onImportBooks: onImportBooks
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

#Preview {
    ContentView()
}
