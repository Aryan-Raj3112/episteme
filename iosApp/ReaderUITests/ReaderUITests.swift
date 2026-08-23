import XCTest

final class ReaderUITests: XCTestCase {
    private let appBundleIdentifier = "com.aryan.episteme"

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testLaunchStaysVisibleAndMainControlsRespectSafeArea() throws {
        let app = launchReader()
        let window = app.windows.firstMatch
        XCTAssertTrue(window.waitForExistence(timeout: 20))

        let menu = try require("Menu", in: app)
        let settings = try require("Settings", in: app)
        let home = try require("Home", in: app)
        let library = try require("Library", in: app)
        XCTAssertGreaterThanOrEqual(menu.frame.minY, window.frame.minY)
        XCTAssertGreaterThanOrEqual(settings.frame.minY, window.frame.minY)
        XCTAssertGreaterThanOrEqual(home.frame.minY, window.frame.minY)
        XCTAssertGreaterThanOrEqual(library.frame.minY, window.frame.minY)
        capture("launch-main-controls", app: app)
    }

    func testDrawerSettingsAccountProAndAiGates() throws {
        let app = launchReader()
        try require("Menu", in: app).tap()

        let drawerSettings = try requireAny(["MobileDrawerSettings", "Settings"], in: app)
        let drawerPro = try requireAny(["MobileDrawerPro", "Upgrade to Pro", "Standard version"], in: app)
        let drawerAi = try requireAny(["MobileDrawerAiSettings", "AI settings", "AI keys and models"], in: app)
        // The simulator may retain a signed-in test account in Keychain even
        // after the debug UserDefaults reset. Exercise the sign-in/account
        // route when signed out; otherwise verify the visible account state
        // without attempting credentials.
        XCTAssertTrue(drawerSettings.exists)
        XCTAssertTrue(drawerPro.exists)
        XCTAssertTrue(drawerAi.exists)
        if let account = waitForAny(["Sign in with Google", "MobileDrawerSignIn"], in: app, timeout: 5) {
            account.tap()
            XCTAssertTrue(try require("Episteme Account", in: app).exists)
            try require("Back", in: app).tap()
            try require("Menu", in: app).tap()
        } else {
            XCTAssertNotNil(waitForAny(["Profile", "Aryan Raj", "Signed in"], in: app, timeout: 5))
        }

        drawerPro.tap()
        XCTAssertTrue(try require("Pro and Credits", in: app).exists)
        try require("Back", in: app).tap()

        try require("Menu", in: app).tap()
        drawerAi.tap()
        XCTAssertTrue(try requireAny(["AI and cloud TTS", "AI keys and models", "AI settings"], in: app).exists)
        capture("drawer-account-pro-ai", app: app)
    }

    func testSettingsDiagnosticExportCanBeCancelled() throws {
        let app = launchReader()
        try require("Settings", in: app).tap()
        let search = try requireAny(["Search settings", "reader.settings.search"], in: app)
        search.tap()
        search.typeText("Export logs")
        try require("Export logs", in: app).tap()

        let cancel = try requireAny(["Cancel", "Done"], in: app, timeout: 12)
        cancel.tap()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 5))
        capture("diagnostics-share-cancelled", app: app)
    }

    func testNativeFileImporterCancelReturnsToReader() throws {
        let app = launchReader()
        try require("Select file", in: app).tap()

        let picker = XCUIApplication(bundleIdentifier: "com.apple.DocumentsApp")
        let cancel = try requireAny(["Cancel"], in: picker, fallback: app, timeout: 12)
        cancel.tap()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 8))
        XCTAssertTrue(try require("Select file", in: app).exists)
        capture("file-import-cancelled", app: app)
    }

    func testPdfReaderToolbarSearchAndScrollWhenFixtureIsAvailable() throws {
        let app = launchReader()
        guard selectFixture(named: "sample.pdf", in: app) else {
            throw XCTSkip("Requires RuntimeFixtures/sample.pdf copied into the simulator Files container")
        }

        let search = try requireAny(["Search", "reader.pdf.search"], in: app, timeout: 25)
        search.tap()
        let query = try requireTextField(in: app, timeout: 5)
        query.typeText("The Egg")
        app.keyboards.buttons["Search"].tapIfPresent()
        app.swipeUp()
        capture("pdf-toolbar-search-scroll", app: app)
    }

    /// Walks the primary surfaces and attaches named screenshots for the
    /// visual-parity matrix (compared against Android captures).
    func testVisualParityWalkthrough() throws {
        let app = launchReader()

        // Home (populated or empty state)
        capture("ios-home", app: app)

        try require("Library Beta", in: app).tap()
        capture("ios-library-beta-grid", app: app)
        if waitForAny(["List view"], in: app, timeout: 4) != nil {
            try require("List view", in: app).tap()
            capture("ios-library-beta-list", app: app)
            try require("Grid view", in: app).tapIfPresent()
        }

        try require("Library", in: app, timeout: 6).tap()
        capture("ios-library-classic", app: app)

        try require("Home", in: app, timeout: 6).tap()

        // Drawer + settings last: returning from settings can leave the drawer open.
        try require("Menu", in: app).tap()
        capture("ios-drawer", app: app)
        try requireAny(["MobileDrawerSettings", "Settings"], in: app).tap()
        capture("ios-settings-root", app: app)
    }

    func testUnifiedLibraryGridListTogglePersistsAcrossLaunches() throws {
        let app = launchReader()
        try require("Library Beta", in: app).tap()
        // Default benchmark state is grid ("List view" action available).
        let toggle = try require("List view", in: app, timeout: 20)
        toggle.tap()
        XCTAssertTrue(try require("Grid view", in: app, timeout: 10).exists)
        capture("unified-library-list-view", app: app)

        // Relaunch WITHOUT the state-reset argument: the choice must persist.
        let persisted = XCUIApplication(bundleIdentifier: appBundleIdentifier)
        persisted.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        persisted.launch()
        XCTAssertTrue(persisted.wait(for: .runningForeground, timeout: 30))
        try require("Library Beta", in: persisted).tap()
        XCTAssertTrue(try require("Grid view", in: persisted, timeout: 20).exists)
        try require("Grid view", in: persisted).tap()
        XCTAssertTrue(try require("List view", in: persisted, timeout: 10).exists)
        capture("unified-library-grid-restore", app: persisted)
    }

    func testGeneratedDocumentParsersRenderFormattedChapters() throws {
        let app = launchReader()

        guard selectFixture(named: "parity_notes.md", in: app) else {
            throw XCTSkip("Requires RuntimeFixtures/parity_notes.md")
        }
        XCTAssertTrue(waitForAny(["Parity Guide"], in: app, timeout: 30) != nil)
        XCTAssertNotNil(waitForAny(["Parser Matrix"], in: app, timeout: 15), "Markdown heading sections should become chapters")
        capture("parser-md-sections", app: app)

        guard selectFixture(named: "fb2_probe.fb2", in: app) else {
            throw XCTSkip("Requires RuntimeFixtures/fb2_probe.fb2")
        }
        XCTAssertTrue(waitForAny(["Aurora Section"], in: app, timeout: 30) != nil)
        XCTAssertNotNil(
            waitForAny(["Hello from bold fb2 and italic fb2."], in: app, timeout: 15),
            "FB2 inline markup should render as formatted text",
        )
        capture("parser-fb2-chapters", app: app)

        guard selectFixture(named: "odt_probe.odt", in: app) else {
            throw XCTSkip("Requires RuntimeFixtures/odt_probe.odt")
        }
        XCTAssertTrue(waitForAny(["ODT Probe Heading"], in: app, timeout: 30) != nil)
        XCTAssertNotNil(waitForAny(["alpha item"], in: app, timeout: 15), "ODT lists should render as list items")
        capture("parser-odt-content", app: app)

        guard selectFixture(named: "docx_probe.docx", in: app) else {
            throw XCTSkip("Requires RuntimeFixtures/docx_probe.docx")
        }
        XCTAssertTrue(waitForAny(["DOCX Probe Book"], in: app, timeout: 30) != nil)
        XCTAssertNotNil(waitForAny(["Zenith Chapter"], in: app, timeout: 15), "DOCX headings should split into chapters")
        capture("parser-docx-headings", app: app)
    }

    func testSeriesEpubBackfillsSeriesMetadata() throws {
        let app = launchReader()
        guard selectFixture(named: "series_probe.epub", in: app) else {
            throw XCTSkip("Requires RuntimeFixtures/series_probe.epub")
        }
        XCTAssertTrue(waitForAny(["Stacked Opening"], in: app, timeout: 25) != nil)
        try require("Back", in: app, timeout: 20).tap()

        // The generated presentation backfills blank series fields at import.
        let details = waitForAny(["Book details"], in: app, timeout: 10)
        if let details {
            details.tap()
            XCTAssertNotNil(waitForAny(["Probe Saga"], in: app, timeout: 10), "Series name should surface in book info")
            capture("series-backfill-info-dialog", app: app)
        } else {
            capture("series-backfill-library", app: app)
        }
    }

    func testPdfPageRendersContentAfterThreadingFix() throws {
        let app = launchReader()
        guard selectFixture(named: "sample.pdf", in: app) else {
            throw XCTSkip("Requires RuntimeFixtures/sample.pdf")
        }
        // The page surface exposes the document name once a bitmap renders;
        // blank-page placeholders never produce this hittable image content.
        let deadline = Date().addingTimeInterval(30)
        var rendered = false
        while Date() < deadline, !rendered {
            rendered = waitForAny(["sample.pdf"], in: app, timeout: 3) != nil
        }
        XCTAssertTrue(rendered, "PDF page bitmap should render without Main-thread stalls")
        app.swipeUp()
        capture("pdf-threading-page-render", app: app)
    }

    func testSplitReaderWithDistinctPdfsWhenFixturesAreAvailable() throws {
        let app = launchReader()
        guard selectFixture(named: "sample.pdf", in: app),
              ProcessInfo.processInfo.environment["READER_UI_SECOND_PDF"] != nil,
              selectFixture(named: "split_secondary.pdf", in: app) else {
            throw XCTSkip("Requires two distinct PDFs copied into RuntimeFixtures")
        }

        guard let openSplit = waitForAny(["Open in split reader"], in: app, timeout: 25) else {
            throw XCTSkip("PDF split affordance was not exposed by this reader state")
        }
        openSplit.tap()
        guard let splitTitle = waitForAny(["Choose a PDF to open beside this document", "Split Reader"], in: app, timeout: 10) else {
            throw XCTSkip("Split reader chooser did not appear")
        }
        if splitTitle.label != "Split Reader" {
            let candidate = try requireAny(["split_secondary.pdf", "sample.pdf"], in: app, timeout: 10)
            candidate.tap()
        }
        XCTAssertTrue(try requireAny(["Split Reader"], in: app, timeout: 20).exists)
        try requireAny(["Side by side", "Stacked"], in: app, timeout: 5).tap()
        try requireAny(["Swap documents"], in: app, timeout: 5).tap()
        capture("pdf-split-two-distinct-documents", app: app)
    }

    func testExternalOpenFallbackWhenFixturePathIsProvided() throws {
        guard let path = ProcessInfo.processInfo.environment["READER_UI_EXTERNAL_FIXTURE"],
              !path.isEmpty else {
            throw XCTSkip("external-open-fallback requires READER_UI_EXTERNAL_FIXTURE")
        }
        let app = launchReader()
        let url = URL(fileURLWithPath: path)
        XCTContext.runActivity(named: "external-open-fallback") { _ in
            app.open(url)
        }
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 20))
        capture("external-open-fallback", app: app)
    }

    func testLaunchPerformance() throws {
        let app = configuredApp()
        measure(metrics: [XCTApplicationLaunchMetric(), XCTMemoryMetric()]) {
            app.launch()
            XCTAssertTrue(app.wait(for: .runningForeground, timeout: 20))
            XCTAssertTrue(app.windows.firstMatch.exists)
            app.terminate()
        }
    }

    // MARK: - Harness helpers

    private func configuredApp() -> XCUIApplication {
        let app = XCUIApplication(bundleIdentifier: appBundleIdentifier)
        app.launchArguments += [
            "-episteme.ui-testing-reset-state",
            "-episteme.desktop.diagnostics", "YES",
            "-AppleLanguages", "(en)",
            "-AppleLocale", "en_US",
        ]
        return app
    }

    @discardableResult
    private func launchReader() -> XCUIApplication {
        let app = configuredApp()
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 30))
        XCTAssertTrue(app.windows.firstMatch.waitForExistence(timeout: 15))
        return app
    }

    private func require(_ label: String, in app: XCUIApplication, timeout: TimeInterval = 15) throws -> XCUIElement {
        try requireAny([label], in: app, timeout: timeout)
    }

    private func requireTextField(in app: XCUIApplication, timeout: TimeInterval) throws -> XCUIElement {
        guard let field = waitForTextField(in: app, timeout: timeout) else {
            XCTFail("Expected an accessible text field")
            throw XCTSkip("Missing accessible text field")
        }
        return field
    }

    private func requireAny(
        _ labels: [String],
        in app: XCUIApplication,
        fallback: XCUIApplication? = nil,
        timeout: TimeInterval = 15
    ) throws -> XCUIElement {
        guard let element = waitForAny(labels, in: app, fallback: fallback, timeout: timeout) else {
            XCTFail("Expected accessibility element: \(labels.joined(separator: ", "))")
            throw XCTSkip("Missing accessibility surface")
        }
        return element
    }

    private func waitForAny(
        _ labels: [String],
        in app: XCUIApplication,
        fallback: XCUIApplication? = nil,
        timeout: TimeInterval
    ) -> XCUIElement? {
        let deadline = Date().addingTimeInterval(timeout)
        repeat {
            for label in labels {
                for element in [
                    app.buttons.matching(NSPredicate(format: "label CONTAINS[c] %@", label)).firstMatch,
                    app.buttons[label].firstMatch,
                    app.textViews[label].firstMatch,
                    app.staticTexts[label].firstMatch,
                    app.textFields[label].firstMatch,
                    app.images[label].firstMatch,
                    app.otherElements[label].firstMatch,
                    app.cells[label].firstMatch,
                ] where element.exists && element.isHittable {
                    return element
                }
                if let fallback {
                    for element in [
                        fallback.buttons.matching(NSPredicate(format: "label CONTAINS[c] %@", label)).firstMatch,
                        fallback.buttons[label].firstMatch,
                        fallback.textViews[label].firstMatch,
                        fallback.staticTexts[label].firstMatch,
                        fallback.textFields[label].firstMatch,
                        fallback.images[label].firstMatch,
                        fallback.otherElements[label].firstMatch,
                        fallback.cells[label].firstMatch,
                    ] where element.exists && element.isHittable {
                        return element
                    }
                }
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        } while Date() < deadline
        return nil
    }

    private func waitForTextField(in app: XCUIApplication, timeout: TimeInterval) -> XCUIElement? {
        let deadline = Date().addingTimeInterval(timeout)
        repeat {
            for field in [app.textFields.firstMatch, app.textViews.firstMatch]
                where field.exists && field.isHittable {
                return field
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        } while Date() < deadline
        return nil
    }

    private func selectFixture(named name: String, in app: XCUIApplication) -> Bool {
        // Importing a fixture opens it immediately. Return to the library before
        // selecting the next fixture so split-reader coverage has two library
        // documents instead of trying to find the home action inside a reader.
        if waitForAny(["Select file"], in: app, timeout: 3) == nil,
           let back = waitForAny(["Back"], in: app, timeout: 5) {
            back.tap()
        }
        guard let select = waitForAny(["Select file"], in: app, timeout: 8) else { return false }
        select.tap()
        let picker = XCUIApplication(bundleIdentifier: "com.apple.DocumentsApp")
        guard picker.wait(for: .runningForeground, timeout: 12) else { return false }
        picker.tabBars.buttons["Browse"].tapIfPresent()
        // Climb out of any open folder (e.g. Downloads) to the Browse root.
        for _ in 0..<3 where waitForAny(["RuntimeFixtures", "On My iPhone"], in: picker, timeout: 1) == nil {
            picker.navigationBars.buttons.firstMatch.tapIfPresent()
        }

        // The fixture copy performed by validate_ios_runtime.sh appears at
        // On My iPhone > Reader > RuntimeFixtures. If Files changes its
        // provider layout, the test remains an explicit, reviewable skip.
        for location in ["On My iPhone", "Episteme", "RuntimeFixtures"] {
            if let item = waitForAny([location], in: picker, timeout: 3) {
                item.tap()
            }
        }
        guard let file = waitForAny([name], in: picker, timeout: 8) else {
            picker.buttons["Cancel"].tapIfPresent()
            return false
        }
        file.tap()
        return app.wait(for: .runningForeground, timeout: 25)
    }

    private func capture(_ name: String, app: XCUIApplication) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}

private extension XCUIElement {
    func tapIfPresent() {
        guard exists && isHittable else { return }
        tap()
    }
}
