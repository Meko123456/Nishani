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

    func testAQuoteWrittenOverLinesIsOneQuote() {
        launchFresh()

        // A bare ">" between two quotes, and a last line without its ">".
        writeNote("""
        # Proverb
        > The best time to plant a tree
        > was twenty years ago.
        >
        > The second best time
        is now.
        """)

        openNote(titled: "Proverb")
        tap(app.buttons["togglePreview"], "the preview toggle")

        // Each "> " line was a quote of its own, the bare ">" an empty one, and the last line a
        // paragraph under them: five blocks where there are two quotes.
        let quotes = app.staticTexts.matching(identifier: "md-quote")
        XCTAssertTrue(quotes.firstMatch.waitForExistence(timeout: 20), "the preview produced no quote")
        XCTAssertEqual(quotes.count, 2)
        XCTAssertEqual(quotes.element(boundBy: 0).label, "The best time to plant a tree was twenty years ago.")
        XCTAssertEqual(quotes.element(boundBy: 1).label, "The second best time is now.")
        XCTAssertEqual(app.staticTexts.matching(identifier: "md-paragraph").count, 0, "the line without a \">\" left the quote")
    }

    func testABulletWrappedByHandIsOneBullet() {
        launchFresh()

        writeNote("""
        # Packing
        - a warm jacket, because the pass
          is cold even in June
        - water
        """)

        openNote(titled: "Packing")
        tap(app.buttons["togglePreview"], "the preview toggle")

        // The wrapped line was a paragraph of its own, between the two bullets.
        let bullets = app.staticTexts.matching(identifier: "md-bullet")
        XCTAssertTrue(bullets.firstMatch.waitForExistence(timeout: 20), "the preview produced no bullet")
        XCTAssertEqual(bullets.count, 2)
        XCTAssertEqual(bullets.element(boundBy: 0).label, "a warm jacket, because the pass is cold even in June")
        XCTAssertEqual(app.staticTexts.matching(identifier: "md-paragraph").count, 0, "the wrapped line left the bullet")
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

        // The regression that already happened on the other platform. Saving an edit rebuilds the
        // stored row, and an editor that rebuilds it from only the fields it is editing drops the
        // pinned flag. The marker is what is asserted, not the position: an unpinned note would
        // also sort first here, because the edit makes it the most recently updated. It has to be
        // a real edit: text that has not changed is not written at all.
        tap(app.buttons["note-Older note"], "the pinned row")
        let body = app.textViews["noteBody"]
        XCTAssertTrue(body.waitForExistence(timeout: 20), "the editor never opened")
        // Below the last line, so the cursor lands at the end and the title line stays as it is.
        let end = body.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.9))
        end.tap()
        typeOnceFocused(body, "\nEdited.") { end.tap() }
        tap(app.buttons["doneEditing"], "Done")

        expectTitles(["📌 Older note", "Newer note"])
    }

    func testSwipingToDeleteANoteAsksFirst() {
        launchFresh()
        writeNote("# Keep me\nThe only copy.")
        expectTitles(["Keep me"])

        // The swipe deleted the note outright, and nothing could bring it back.
        app.buttons["note-Keep me"].swipeLeft()
        tap(app.buttons["delete-Keep me"], "the swipe's Delete")
        let alert = app.alerts["Delete “Keep me”?"]
        XCTAssertTrue(alert.waitForExistence(timeout: 30), "the swipe deleted the note without asking")
        XCTAssertTrue(alert.staticTexts["It can't be brought back."].exists, "the question did not say it is for good")
        tap(alert.buttons["Cancel"], "Cancel")
        expectTitles(["Keep me"])

        app.buttons["note-Keep me"].swipeLeft()
        tap(app.buttons["delete-Keep me"], "the swipe's Delete")
        XCTAssertTrue(alert.waitForExistence(timeout: 30), "the second swipe did not ask either")
        tap(alert.buttons["Delete"], "Delete")
        expectNoteCount(0, "the confirmed delete left the note in the list")
    }

    func testOpeningANoteWithoutEditingLeavesItWhereItWas() {
        launchFresh()

        writeNote("# Older note\nWritten first.")
        writeNote("# Newer note\nWritten second.")
        expectTitles(["Newer note", "Older note"])

        // Opened, read for longer than the 600 ms autosave, and closed without a keystroke. The
        // editor saves what it shows either way, and that save stamped the note as just edited,
        // so reading the older note brought it back to the top of the list.
        openNote(titled: "Older note")
        XCTAssertTrue(app.buttons["doneEditing"].waitForExistence(timeout: 20), "the editor never opened")
        Thread.sleep(forTimeInterval: 1.5)
        tap(app.buttons["doneEditing"], "Done")

        expectTitles(["Newer note", "Older note"])
    }

    func testToolbarButtonsSayWhatTheyDo() {
        launchFresh()

        // Glyph-only buttons: VoiceOver reads the label, so it has to say what the button does.
        let newNote = app.buttons["newNote"]
        XCTAssertTrue(newNote.waitForExistence(timeout: 30))
        XCTAssertEqual(newNote.label, "New note")

        writeNote("# Labels\nChecked by VoiceOver's names.")
        openNote(titled: "Labels")
        let toggle = app.buttons["togglePreview"]
        XCTAssertTrue(toggle.waitForExistence(timeout: 20), "the editor never opened")
        XCTAssertEqual(toggle.label, "Preview")
        tap(toggle, "the preview toggle")
        let switched = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label == 'Edit'"), object: toggle)
        XCTAssertEqual(XCTWaiter().wait(for: [switched], timeout: 10), .completed, "in preview the toggle should offer Edit")
    }

    func testANewNoteOpensReadyToType() {
        launchFresh()

        // No tap on the page first: the keyboard is already up for it, as on Android.
        tap(app.buttons["newNote"], "the new-note button")
        let body = app.textViews["noteBody"]
        XCTAssertTrue(body.waitForExistence(timeout: 20), "the editor never opened")
        expectKeyboard(on: body, "a new note opened without the keyboard")
        body.typeText("# Typed at once")
        tap(app.buttons["doneEditing"], "Done")
        expectTitles(["Typed at once"])

        // An existing note opens to be read, not with the keyboard over it.
        openNote(titled: "Typed at once")
        XCTAssertTrue(body.waitForExistence(timeout: 20), "the editor never opened")
        Thread.sleep(forTimeInterval: 1)
        XCTAssertFalse(app.keyboards.firstMatch.exists, "an existing note opened with the keyboard up")
    }

    func testTheHomeScreenQuickActionStartsABlankNote() {
        launchFresh()

        // Half way through a new note when the app is left for the Home Screen.
        tap(app.buttons["newNote"], "the new-note button")
        let body = app.textViews["noteBody"]
        XCTAssertTrue(body.waitForExistence(timeout: 20), "the editor never opened")
        expectKeyboard(on: body, "a new note opened without the keyboard")
        body.typeText("# Half a thought")

        useQuickAction("io.github.meko123456.nishani.new-note")

        // A blank page with the keyboard up, not the note that was open.
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 20), "the quick action did not bring Nishani back")
        expectKeyboard(on: body, "the quick action's note opened without the keyboard")
        XCTAssertEqual(body.value as? String ?? "", "", "the quick action reopened the note that was open")
        body.typeText("# From the Home Screen")
        tap(app.buttons["doneEditing"], "Done")

        // The note that was open was saved as its editor went.
        expectTitles(["From the Home Screen", "Half a thought"])
    }

    func testTheQuickActionOpensANoteWhenItStartsTheApp() {
        launchFresh()
        app.terminate()

        // Started by the quick action, so without this test's settings: only the editor is
        // checked, and nothing is typed or saved into the simulator's own notes.
        useQuickAction("io.github.meko123456.nishani.new-note")
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 30), "the quick action did not start Nishani")
        let body = app.textViews["noteBody"]
        XCTAssertTrue(body.waitForExistence(timeout: 30), "Nishani started on the list, not a new note")
        expectKeyboard(on: body, "the new note opened without the keyboard")
        XCTAssertEqual(body.value as? String ?? "", "")
        tap(app.buttons["doneEditing"], "Done")
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
        typeOnceFocused(body, markdown) { body.tap() }
        tap(app.buttons["doneEditing"], "Done")
    }

    /// Picks a Home Screen quick action the way a person does: a long press on Nishani's icon,
    /// then the action in the menu that opens. [type] is the action's UIApplicationShortcutItemType,
    /// which SpringBoard uses as the menu row's identifier.
    ///
    /// SpringBoard never reports itself idle while that menu is open, so every event sent while it
    /// is up first waits out XCUITest's 60-second idle timeout. When the menu opens below the icon,
    /// its first row is the app's own action, and one press that slides onto that row and lets go
    /// is a single event, sent before the menu exists. Otherwise it is a press, then a tap, and two
    /// of those waits.
    private func useQuickAction(_ type: String) {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        XCUIDevice.shared.press(.home)
        // Again, which takes the Home Screen back to its first page. An icon on another page has
        // an empty frame, which says nothing about which way it is, so the search starts there.
        XCUIDevice.shared.press(.home)
        let icon = springboard.icons["Nishani"]
        XCTAssertTrue(icon.waitForExistence(timeout: 30), "Nishani is not on the Home Screen")
        // A newly installed app goes on the last page.
        var pages = 0
        while !icon.isHittable && pages < 5 {
            springboard.swipeLeft()
            pages += 1
        }

        let frame = icon.frame
        let origin = springboard.coordinate(withNormalizedOffset: .zero)
        // The frame takes in the name under the picture; the press goes on the picture.
        let picture = origin.withOffset(CGVector(dx: frame.midX, dy: frame.minY + frame.width / 2))
        if frame.maxY + 320 < springboard.frame.maxY {
            let firstRow = origin.withOffset(CGVector(dx: frame.midX, dy: frame.maxY + 21))
            picture.press(forDuration: 1.0, thenDragTo: firstRow, withVelocity: .slow, thenHoldForDuration: 0.3)
        } else {
            picture.press(forDuration: 1.0)
            let action = springboard.buttons[type]
            XCTAssertTrue(action.waitForExistence(timeout: 15), "the icon's menu has no \(type)")
            action.tap()
        }
    }

    private func openNote(titled title: String) {
        tap(app.buttons["note-\(title)"], "the row for \"\(title)\"")
    }

    private func search(for text: String) {
        let field = app.searchFields.firstMatch
        tap(field, "the search field")
        let clear = field.buttons.firstMatch
        if clear.exists { clear.tap() }
        typeOnceFocused(field, text) { field.tap() }
    }

    private func expectKeyboard(
        on element: XCUIElement,
        _ message: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let focused = XCTNSPredicateExpectation(predicate: NSPredicate(format: "hasKeyboardFocus == true"), object: element)
        XCTAssertEqual(XCTWaiter().wait(for: [focused], timeout: 10), .completed, message, file: file, line: line)
    }

    /// Types only once [element] has the keyboard, and taps it again with [refocus] if it never
    /// gets it. On a busy CI runner (14 October 2026) the search field was still moving into place
    /// after the tap and typeText found nothing focused; DayBlocks' block editor failed on CI the
    /// same way on 17 October. The note body is typed into the same way, so it waits as well.
    private func typeOnceFocused(_ element: XCUIElement, _ text: String, refocus: () -> Void) {
        let focused = XCTNSPredicateExpectation(predicate: NSPredicate(format: "hasKeyboardFocus == true"), object: element)
        if XCTWaiter().wait(for: [focused], timeout: 10) != .completed { refocus() }
        element.typeText(text)
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
