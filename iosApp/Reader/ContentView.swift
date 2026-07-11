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
    @State private var isReaderSystemUiHidden = false

    var body: some View {
        ReaderComposeHost(
            bridge: bridge,
            isSystemUiHidden: $isReaderSystemUiHidden,
            onImportBooks: {
                isImportPickerPresented = true
            }
        )
        .ignoresSafeArea()
        .statusBarHidden(isReaderSystemUiHidden)
        .persistentSystemOverlays(isReaderSystemUiHidden ? .hidden : .visible)
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
    @Binding var isSystemUiHidden: Bool
    let onImportBooks: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        let composeController = ReaderIosAppKt.readerComposeViewController(
            bridge: bridge,
            onImportBooks: onImportBooks
        )
        let hostController = ReaderStatusBarHostController(content: composeController)
        bridge.setSystemUiHandler { hidden, lightContent, backgroundArgb in
            DispatchQueue.main.async {
                isSystemUiHidden = hidden.boolValue
            }
            hostController.updateSystemUi(
                hidden: hidden.boolValue,
                lightContent: lightContent.boolValue,
                backgroundArgb: backgroundArgb.int64Value
            )
        }
        return hostController
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

private final class ReaderStatusBarHostController: UIViewController {
    private let contentController: UIViewController
    private var hidesSystemUi = false
    private var usesLightStatusBarContent = false
    private var contentInterfaceStyle: UIUserInterfaceStyle = .unspecified
    private let statusBarBackdrop = UIView()
    private let navigationBarBackdrop = UIView()
    private var contentTopToSafeAreaConstraint: NSLayoutConstraint?
    private var contentBottomToSafeAreaConstraint: NSLayoutConstraint?
    private var contentTopToEdgeConstraint: NSLayoutConstraint?
    private var contentBottomToEdgeConstraint: NSLayoutConstraint?

    init(content: UIViewController) {
        self.contentController = content
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        contentInterfaceStyle = traitCollection.userInterfaceStyle
        contentController.overrideUserInterfaceStyle = contentInterfaceStyle
        addChild(contentController)
        contentController.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(contentController.view)
        let topToSafeArea = contentController.view.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor)
        let bottomToSafeArea = contentController.view.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor)
        let topToEdge = contentController.view.topAnchor.constraint(equalTo: view.topAnchor)
        let bottomToEdge = contentController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        contentTopToSafeAreaConstraint = topToSafeArea
        contentBottomToSafeAreaConstraint = bottomToSafeArea
        contentTopToEdgeConstraint = topToEdge
        contentBottomToEdgeConstraint = bottomToEdge
        NSLayoutConstraint.activate([
            contentController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            contentController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            topToSafeArea,
            bottomToSafeArea
        ])
        contentController.didMove(toParent: self)

        statusBarBackdrop.isUserInteractionEnabled = false
        navigationBarBackdrop.isUserInteractionEnabled = false
        statusBarBackdrop.translatesAutoresizingMaskIntoConstraints = false
        navigationBarBackdrop.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(statusBarBackdrop)
        view.addSubview(navigationBarBackdrop)
        NSLayoutConstraint.activate([
            statusBarBackdrop.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            statusBarBackdrop.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            statusBarBackdrop.topAnchor.constraint(equalTo: view.topAnchor),
            statusBarBackdrop.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            navigationBarBackdrop.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            navigationBarBackdrop.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            navigationBarBackdrop.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            navigationBarBackdrop.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor)
        ])
    }

    override var prefersStatusBarHidden: Bool { hidesSystemUi }
    override var prefersHomeIndicatorAutoHidden: Bool { hidesSystemUi }
    override var preferredStatusBarUpdateAnimation: UIStatusBarAnimation { .fade }
    override var preferredStatusBarStyle: UIStatusBarStyle {
        usesLightStatusBarContent ? .lightContent : .darkContent
    }

    private func updateSystemBarLayout() {
        contentTopToSafeAreaConstraint?.isActive = !hidesSystemUi
        contentBottomToSafeAreaConstraint?.isActive = !hidesSystemUi
        contentTopToEdgeConstraint?.isActive = hidesSystemUi
        contentBottomToEdgeConstraint?.isActive = hidesSystemUi
        view.setNeedsLayout()
    }

    func updateSystemUi(hidden: Bool, lightContent: Bool, backgroundArgb: Int64) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            hidesSystemUi = hidden
            usesLightStatusBarContent = lightContent
            overrideUserInterfaceStyle = lightContent ? .dark : .light
            contentController.overrideUserInterfaceStyle = contentInterfaceStyle
            let bits = UInt64(bitPattern: backgroundArgb)
            let red = CGFloat((bits >> 16) & 0xFF) / 255
            let green = CGFloat((bits >> 8) & 0xFF) / 255
            let blue = CGFloat(bits & 0xFF) / 255
            let alpha = CGFloat((bits >> 24) & 0xFF) / 255
            let themeColor = UIColor(red: red, green: green, blue: blue, alpha: alpha)
            view.backgroundColor = themeColor
            view.window?.backgroundColor = view.backgroundColor
            statusBarBackdrop.backgroundColor = themeColor
            navigationBarBackdrop.backgroundColor = themeColor
            updateSystemBarLayout()
            setNeedsStatusBarAppearanceUpdate()
            setNeedsUpdateOfHomeIndicatorAutoHidden()
        }
    }
}

#Preview {
    ContentView()
}
