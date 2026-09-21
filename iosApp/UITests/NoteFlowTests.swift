import XCTest

/// Proves the app works, not merely that it compiles.
///
/// Everything this app decides is decided in Kotlin: what a note is called, which notes a search
/// matches, what order the list is in, and what markdown means. A build that succeeds says the two
/// languages agree about types and says nothing at all about whether a keystroke reaches
/// `NotesRepository` and the answer comes back. These drive that chain instead.
final class NoteFlowTests: XCTestCase {

    private var app: XCUIApplication!
    private var suite: String!

    override func setUp() {
        continueAfterFailure = false
        // A suite per test. Offline-first means the app remembers everything, so tests sharing a
        // store are not independent: the second run of a test that counts notes would find the
        // first run's notes still sitting there.
        suite = "uitests-\(UUID().uuidString)"
        app = XCUIApplication()
    }

    override func tearDown() {
        app = nil
        suite = nil
    }

    // MARK: - Tests

    func testMarkdownIsParsedInKotlinRatherThanShownAsWritten() {
        launchFresh()

        writeNote("""
        # Shopping
        Buy **bread** and *milk*.
        - oats
        - salt
        """)

        openNote(titled: "Shopping")
        tap(app.buttons["togglePreview"], "the preview toggle")

        // A level-1 heading with its marker consumed. This is the assertion that separates
        // parsing from echoing: a renderer that printed the source would show "# Shopping" as a
        // paragraph, and there would be no heading here at all.
        let headings = app.staticTexts.matching(identifier: "md-heading-1")
        XCTAssertTrue(headings.firstMatch.waitForExistence(timeout: 20), "the preview produced no heading")
        XCTAssertEqual(headings.count, 1)
        XCTAssertEqual(headings.firstMatch.label, "Shopping")

        // Two list items, each its own block. Getting this wrong folds them into the paragraph
        // above, which still shows every word on screen and still looks roughly right.
        XCTAssertEqual(app.staticTexts.matching(identifier: "md-bullet").count, 2, "the bullets were not parsed as list items")

        // Inline spans. The paragraph came back as four runs — text, bold, text, italic — and was
        // rejoined in order with the asterisks consumed. Asserted as the whole line rather than
        // word by word, because the ordering is the part that can go wrong.
        let paragraph = app.staticTexts.matching(identifier: "md-paragraph").firstMatch
        XCTAssertTrue(paragraph.exists, "the preview produced no paragraph")
        XCTAssertEqual(paragraph.label, "Buy bread and milk.")
    }

    func testTheListTitleAndTheSearchBothComeFromTheSharedModule() {
        launchFresh()

        writeNote("""
        # Tbilisi trip
        Book the funicular before Friday.
        Pack the red umbrella.
        """)

        // "Tbilisi trip", not "# Tbilisi trip". The title is derived in Kotlin by stripping the
        // markdown markers off the first non-blank line; nothing in Swift does that.
        let title = app.staticTexts.matching(identifier: "noteTitle").firstMatch
        XCTAssertTrue(title.waitForExistence(timeout: 30), "the saved note never reached the list")
        XCTAssertEqual(title.label, "Tbilisi trip")

        // "umbrella" is on the third line, which the list never renders: the row shows the title
        // and the second line only. A search that filtered what is on screen would find nothing.
        search(for: "umbrella")
        expectNoteCount(1, "search missed a word that is only in the body")

        search(for: "kayak")
        XCTAssertTrue(app.staticTexts["No matches"].waitForExistence(timeout: 15), "a search for nothing still matched something")
    }

    func testPinningReordersTheListAndSavingAgainDoesNotUndoIt() {
        launchFresh()

        writeNote("# Older note\nWritten first.")
        writeNote("# Newer note\nWritten second.")

        // Newest first, which is the second half of the repository's comparator.
        expectTitles(["Newer note", "Older note"])

        // Pinning had no way in on iOS before this change: the repository sorted pinned notes to
        // the top and Store.togglePin was wired up to ask it to, but no view ever called it.
        app.buttons["note-Older note"].swipeRight()
        let pin = app.buttons["pin-Older note"]
        XCTAssertTrue(pin.waitForExistence(timeout: 15), "the swipe did not reveal a pin action")
        tap(pin, "the pin action")

        // Pinned first, then newest first — both halves of one Kotlin comparator, and the older
        // note has now overtaken the newer one on the strength of the first half alone.
        expectTitles(["📌 Older note", "Newer note"])

        // The regression that already happened on the other platform. Saving a note rebuilds the
        // stored row, and an editor that rebuilds it from only the fields it is editing drops the
        // pinned flag. The marker is what is asserted, not the position: an unpinned note would
        // also sort first here, because saving makes it the most recently updated.
        tap(app.buttons["note-Older note"], "the pinned row")
        XCTAssertTrue(app.buttons["doneEditing"].waitForExistence(timeout: 20), "the editor never opened")
        tap(app.buttons["doneEditing"], "Done")

        expectTitles(["📌 Older note", "Newer note"])
    }

    // MARK: - Driving the app

    private func launchFresh() {
        app.launchEnvironment["UITEST_DEFAULTS_SUITE"] = suite
        app.launchEnvironment["UITEST_DEFAULTS_RESET"] = "1"
        app.launch()
    }

    /// Taps once the element is genuinely hittable, not merely present.
    ///
    /// `waitForExistence` proves an element is in the accessibility tree and nothing more. Tapping
    /// one whose frame has not settled — the app still being brought to the front, a sheet still
    /// animating — synthesises the event at hit point {-1, -1}, which silently does nothing and
    /// then surfaces as a timeout somewhere else entirely. That is exactly how this suite failed
    /// once, on a simulator that had another app in the foreground when the run started.
    private func tap(
        _ element: XCUIElement,
        _ what: String,
        timeout: TimeInterval = 30,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let hittable = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "isHittable == true"),
            object: element
        )
        XCTAssertEqual(XCTWaiter().wait(for: [hittable], timeout: timeout), .completed,
                       "\(what) never became tappable", file: file, line: line)
        element.tap()
    }

    private func writeNote(_ markdown: String) {
        tap(app.buttons["newNote"], "the new-note button")

        let body = app.textViews["noteBody"]
        XCTAssertTrue(body.waitForExistence(timeout: 20), "the editor never opened")
        tap(body, "the editor")
        body.typeText(markdown)
        tap(app.buttons["doneEditing"], "Done")
    }

    private func openNote(titled title: String) {
        tap(app.buttons["note-\(title)"], "the row for \"\(title)\"")
    }

    private func search(for text: String) {
        let field = app.searchFields.firstMatch
        tap(field, "the search field")
        let clear = field.buttons.firstMatch
        if clear.exists { clear.tap() }
        field.typeText(text)
    }

    // MARK: - Assertions

    /// The titles currently in the list, top to bottom.
    private func titles() -> [String] {
        let rows = app.staticTexts.matching(identifier: "noteTitle")
        return (0..<rows.count).map { rows.element(boundBy: $0).label }
    }

    /// Waits for the list to settle on an order rather than reading it once: the repository
    /// answers and SwiftUI redraws after the tap returns, so reading immediately races the redraw
    /// and fails for a reason that has nothing to do with the ordering.
    private func expectTitles(
        _ expected: [String],
        timeout: TimeInterval = 20,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let deadline = Date().addingTimeInterval(timeout)
        var seen: [String] = []
        repeat {
            seen = titles()
            if seen == expected { return }
            Thread.sleep(forTimeInterval: 0.3)
        } while Date() < deadline
        XCTAssertEqual(seen, expected, file: file, line: line)
    }

    private func expectNoteCount(
        _ expected: Int,
        _ message: String,
        timeout: TimeInterval = 20,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let deadline = Date().addingTimeInterval(timeout)
        var seen = 0
        repeat {
            seen = app.staticTexts.matching(identifier: "noteTitle").count
            if seen == expected { return }
            Thread.sleep(forTimeInterval: 0.3)
        } while Date() < deadline
        XCTAssertEqual(seen, expected, message, file: file, line: line)
    }
}
