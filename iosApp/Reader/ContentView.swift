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
    private enum ImportKind { case books, folder, fonts, cover }

    private let bridge = ReaderIosBridge()
    @StateObject private var localStoreKit = LocalStoreKitController()
    @StateObject private var localAccount = LocalAccountController()
    @Environment(\.scenePhase) private var scenePhase
    @State private var isImportPickerPresented = false
    @State private var importKind: ImportKind = .books
    @State private var isReaderSystemUiHidden = false
    @State private var pendingExternalURL: URL?

    var body: some View {
        ReaderComposeHost(
            bridge: bridge,
            isSystemUiHidden: $isReaderSystemUiHidden,
            onImportBooks: {
                importKind = .books
                isImportPickerPresented = true
            },
            onImportFolder: {
                importKind = .folder
                isImportPickerPresented = true
            },
            onRefreshFolders: {
                refreshImportedFolders(bridge: bridge)
            },
            onImportFonts: {
                importKind = .fonts
                isImportPickerPresented = true
            },
            onImportCover: {
                importKind = .cover
                isImportPickerPresented = true
            },
            onRemoveFolder: { folderName in
                removeImportedFolder(named: folderName)
            }
        )
        .ignoresSafeArea()
        .statusBarHidden(isReaderSystemUiHidden)
        .persistentSystemOverlays(isReaderSystemUiHidden ? .hidden : .visible)
        .fileImporter(
            isPresented: $isImportPickerPresented,
            allowedContentTypes: importKind == .fonts
                ? [.font]
                : (importKind == .folder ? [.folder] : (importKind == .cover ? [.image] : [.item])),
            allowsMultipleSelection: importKind != .folder && importKind != .cover
        ) { result in
            switch result {
            case .success(let urls):
                if importKind == .folder, let folderURL = urls.first {
                    rememberImportedFolder(folderURL)
                    let importedFiles = copyImportedFolderToAppSupport(folderURL)
                    bridge.recordImportedFolder(
                        folderName: folderURL.lastPathComponent,
                        fileNames: importedFiles.map(\.name),
                        filePaths: importedFiles.map(\.path)
                    )
                    return
                }
                let importedFiles = urls.compactMap { url in
                    copyImportedFileToAppSupport(
                        url,
                        directoryName: importKind == .fonts ? "Fonts" : (importKind == .cover ? "Covers" : "Imports")
                    )
                }
                if importKind == .fonts {
                    bridge.recordImportedFonts(fileNames: importedFiles.map(\.name), filePaths: importedFiles.map(\.path))
                } else if importKind == .cover {
                    bridge.recordImportedCover(filePath: importedFiles.first?.path)
                } else {
                    bridge.recordImportedFiles(fileNames: importedFiles.map(\.name), filePaths: importedFiles.map(\.path))
                }
            case .failure:
                if importKind == .fonts {
                    bridge.recordImportedFonts(fileNames: [], filePaths: [])
                } else if importKind == .cover {
                    bridge.recordImportedCover(filePath: nil)
                } else if importKind == .folder {
                    bridge.recordImportedFolder(folderName: "folder", fileNames: [], filePaths: [])
                } else {
                    bridge.recordImportedFiles(fileNames: [], filePaths: [])
                }
            }
        }
        .onOpenURL { url in
            if !localAccount.handleOpenURL(url) {
                handleExternalURL(url)
            }
        }
        .task {
            localStoreKit.attach(to: bridge)
            localAccount.attach(to: bridge)
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                refreshImportedFolders(bridge: bridge)
            }
        }
        .confirmationDialog(
            "Open external file",
            isPresented: Binding(
                get: { pendingExternalURL != nil },
                set: { if !$0 { pendingExternalURL = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Copy to Library") {
                if let url = pendingExternalURL {
                    openExternalURL(url, addToLibrary: true)
                }
                pendingExternalURL = nil
            }
            Button("Open Temporarily") {
                if let url = pendingExternalURL {
                    openExternalURL(url, addToLibrary: false)
                }
                pendingExternalURL = nil
            }
            Button("Cancel", role: .cancel) {
                pendingExternalURL = nil
            }
        } message: {
            Text("Choose whether to keep a managed copy in your library.")
        }
    }

    private func handleExternalURL(_ url: URL) {
        switch bridge.externalFileBehavior().uppercased() {
        case "COPY":
            openExternalURL(url, addToLibrary: true)
        case "TEMPORARY":
            openExternalURL(url, addToLibrary: false)
        default:
            pendingExternalURL = url
        }
    }

    private func openExternalURL(_ url: URL, addToLibrary: Bool) {
        let imported = addToLibrary
            ? copyImportedFileToAppSupport(url, directoryName: "Imports")
            : copyExternalFileToTemporaryStorage(url)
        guard let imported else {
            bridge.recordNativeEvent(message: "Could not open the external file")
            return
        }
        bridge.openExternalFile(
            fileName: imported.name,
            filePath: imported.path,
            addToLibrary: addToLibrary
        )
    }
}

private let importedFolderBookmarksKey = "reader.ios.importedFolderBookmarks.v1"

private func rememberImportedFolder(_ url: URL) {
    do {
        let bookmark = try url.bookmarkData(
            options: [.minimalBookmark],
            includingResourceValuesForKeys: nil,
            relativeTo: nil
        )
        var bookmarks = UserDefaults.standard.dictionary(forKey: importedFolderBookmarksKey) as? [String: Data] ?? [:]
        bookmarks[url.lastPathComponent] = bookmark
        UserDefaults.standard.set(bookmarks, forKey: importedFolderBookmarksKey)
    } catch {
        // The managed copy remains usable if a document provider cannot issue a bookmark.
    }
}

private func refreshImportedFolders(bridge: ReaderIosBridge) {
    let bookmarks = UserDefaults.standard.dictionary(forKey: importedFolderBookmarksKey) as? [String: Data] ?? [:]
    for (folderName, bookmark) in bookmarks {
        var isStale = false
        guard let folderURL = try? URL(
            resolvingBookmarkData: bookmark,
            options: [.withoutUI],
            relativeTo: nil,
            bookmarkDataIsStale: &isStale
        ) else {
            continue
        }
        if isStale {
            rememberImportedFolder(folderURL)
        }
        let importedFiles = copyImportedFolderToAppSupport(folderURL)
        bridge.recordImportedFolder(
            folderName: folderName,
            fileNames: importedFiles.map(\.name),
            filePaths: importedFiles.map(\.path)
        )
    }
}

private func removeImportedFolder(named folderName: String) {
    var bookmarks = UserDefaults.standard.dictionary(forKey: importedFolderBookmarksKey) as? [String: Data] ?? [:]
    bookmarks.removeValue(forKey: folderName)
    UserDefaults.standard.set(bookmarks, forKey: importedFolderBookmarksKey)
    guard let appSupport = try? FileManager.default.url(
        for: .applicationSupportDirectory,
        in: .userDomainMask,
        appropriateFor: nil,
        create: true
    ) else {
        return
    }
    let managedFolder = appSupport
        .appendingPathComponent("LocalFolders", isDirectory: true)
        .appendingPathComponent(safeLocalFolderName(folderName), isDirectory: true)
    if FileManager.default.fileExists(atPath: managedFolder.path) {
        try? FileManager.default.removeItem(at: managedFolder)
    }
}

private func copyImportedFolderToAppSupport(_ sourceURL: URL) -> [ImportedReaderFile] {
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
        let folderRoot = appSupport
            .appendingPathComponent("LocalFolders", isDirectory: true)
            .appendingPathComponent(safeLocalFolderName(sourceURL.lastPathComponent), isDirectory: true)
        if fileManager.fileExists(atPath: folderRoot.path) {
            try fileManager.removeItem(at: folderRoot)
        }
        try fileManager.createDirectory(at: folderRoot, withIntermediateDirectories: true)

        let resourceKeys: [URLResourceKey] = [.isRegularFileKey, .isDirectoryKey]
        guard let enumerator = fileManager.enumerator(
            at: sourceURL,
            includingPropertiesForKeys: resourceKeys,
            options: [.skipsHiddenFiles, .skipsPackageDescendants]
        ) else {
            return []
        }

        var imported: [ImportedReaderFile] = []
        for case let itemURL as URL in enumerator {
            let values = try itemURL.resourceValues(forKeys: Set(resourceKeys))
            let relativePath = itemURL.path.replacingOccurrences(
                of: sourceURL.path + "/",
                with: "",
                options: [.anchored]
            )
            let destinationURL = folderRoot.appendingPathComponent(relativePath)
            if values.isDirectory == true {
                try fileManager.createDirectory(at: destinationURL, withIntermediateDirectories: true)
            } else if values.isRegularFile == true {
                try fileManager.createDirectory(
                    at: destinationURL.deletingLastPathComponent(),
                    withIntermediateDirectories: true
                )
                if fileManager.fileExists(atPath: destinationURL.path) {
                    try fileManager.removeItem(at: destinationURL)
                }
                try fileManager.copyItem(at: itemURL, to: destinationURL)
                imported.append(ImportedReaderFile(name: itemURL.lastPathComponent, path: destinationURL.path))
            }
        }
        return imported
    } catch {
        return []
    }
}

private func safeLocalFolderName(_ name: String) -> String {
    let cleaned = name.replacingOccurrences(of: "/", with: "_").trimmingCharacters(in: .whitespacesAndNewlines)
    return cleaned.isEmpty ? "Imported Folder" : cleaned
}

private struct ImportedReaderFile {
    let name: String
    let path: String
}

private func copyImportedFileToAppSupport(_ sourceURL: URL, directoryName: String) -> ImportedReaderFile? {
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
        let importsDirectory = appSupport.appendingPathComponent(directoryName, isDirectory: true)
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

private func copyExternalFileToTemporaryStorage(_ sourceURL: URL) -> ImportedReaderFile? {
    let didStartAccessing = sourceURL.startAccessingSecurityScopedResource()
    defer {
        if didStartAccessing {
            sourceURL.stopAccessingSecurityScopedResource()
        }
    }
    do {
        let fileManager = FileManager.default
        let directory = fileManager.temporaryDirectory.appendingPathComponent("ExternalOpen", isDirectory: true)
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        let destination = directory.appendingPathComponent(uniqueImportedFileName(sourceURL.lastPathComponent))
        try fileManager.copyItem(at: sourceURL, to: destination)
        return ImportedReaderFile(name: sourceURL.lastPathComponent, path: destination.path)
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
    let onImportFolder: () -> Void
    let onRefreshFolders: () -> Void
    let onImportFonts: () -> Void
    let onImportCover: () -> Void
    let onRemoveFolder: (String) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        let composeController = ReaderIosAppKt.readerComposeViewController(
            bridge: bridge,
            onImportBooks: onImportBooks,
            onImportFolder: onImportFolder,
            onRefreshFolders: onRefreshFolders,
            onImportFonts: onImportFonts,
            onImportCover: onImportCover,
            onRemoveFolder: onRemoveFolder
        )
        let hostController = ReaderStatusBarHostController(content: composeController)
        bridge.setSystemUiHandler { statusHidden, navigationHidden, lightContent, backgroundArgb, edgeToEdge in
            DispatchQueue.main.async {
                isSystemUiHidden = statusHidden.boolValue
            }
            hostController.updateSystemUi(
                statusHidden: statusHidden.boolValue,
                navigationHidden: navigationHidden.boolValue,
                lightContent: lightContent.boolValue,
                backgroundArgb: backgroundArgb.int64Value,
                edgeToEdge: edgeToEdge.boolValue
            )
        }
        bridge.setOrientationHandler { mode in
            hostController.updateReaderOrientation(mode: mode.int32Value)
        }
        return hostController
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

private final class ReaderStatusBarHostController: UIViewController {
    private let contentController: UIViewController
    private var hidesStatusBar = false
    private var hidesHomeIndicator = false
    private var usesLightStatusBarContent = false
    private var contentInterfaceStyle: UIUserInterfaceStyle = .unspecified
    private var readerOrientationMode: Int32 = 0
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

    override var prefersStatusBarHidden: Bool { hidesStatusBar }
    override var prefersHomeIndicatorAutoHidden: Bool { hidesHomeIndicator }
    override var preferredStatusBarUpdateAnimation: UIStatusBarAnimation { .fade }
    override var preferredStatusBarStyle: UIStatusBarStyle {
        usesLightStatusBarContent ? .lightContent : .darkContent
    }

    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        switch readerOrientationMode {
        case 1: return .portrait
        case 2: return .landscape
        default: return .all
        }
    }

    func updateReaderOrientation(mode: Int32) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            readerOrientationMode = mode
            setNeedsUpdateOfSupportedInterfaceOrientations()
            guard let windowScene = view.window?.windowScene else { return }
            let preferences = UIWindowScene.GeometryPreferences.iOS(interfaceOrientations: supportedInterfaceOrientations)
            windowScene.requestGeometryUpdate(preferences)
        }
    }

    private func updateSystemBarLayout(edgeToEdge: Bool) {
        let safeAreaConstraints = [contentTopToSafeAreaConstraint, contentBottomToSafeAreaConstraint].compactMap { $0 }
        let edgeConstraints = [contentTopToEdgeConstraint, contentBottomToEdgeConstraint].compactMap { $0 }
        NSLayoutConstraint.deactivate(safeAreaConstraints + edgeConstraints)
        NSLayoutConstraint.activate(edgeToEdge ? edgeConstraints : safeAreaConstraints)
        view.setNeedsLayout()
    }

    func updateSystemUi(statusHidden: Bool, navigationHidden: Bool, lightContent: Bool, backgroundArgb: Int64, edgeToEdge: Bool) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            hidesStatusBar = statusHidden
            hidesHomeIndicator = navigationHidden
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
            // In edge-to-edge modes these views would sit above Compose and leave a permanent
            // surface-colored strip after Sync hides the reader chrome. The Compose toolbar
            // itself paints beneath the system bars while visible; when it is hidden the PDF
            // must be allowed to draw all the way to the screen edges.
            statusBarBackdrop.isHidden = edgeToEdge
            navigationBarBackdrop.isHidden = edgeToEdge
            updateSystemBarLayout(edgeToEdge: edgeToEdge)
            setNeedsStatusBarAppearanceUpdate()
            setNeedsUpdateOfHomeIndicatorAutoHidden()
        }
    }
}

#Preview {
    ContentView()
}
