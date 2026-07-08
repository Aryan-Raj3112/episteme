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
                bridge.recordImportedFiles(fileNames: urls.map(\.lastPathComponent))
            case .failure:
                bridge.recordImportedFiles(fileNames: [])
            }
        }
    }
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
