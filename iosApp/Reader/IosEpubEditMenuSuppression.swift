import ObjectiveC
import UIKit

/// Android parity (`InteractiveWebView.startActionMode`): the EPUB reader renders its
/// own selection menu inside the page (`#reader-selection-menu`), so the native iOS
/// edit menu (Copy / Look Up / Share...) that WebKit raises on text selection must be
/// suppressed. On modern iOS WebKit presents that menu through
/// `-[UIEditMenuInteraction presentEditMenuWithConfiguration:]`; redirecting that
/// implementation to a gate that refuses for WebKit content views removes the system
/// menu while keeping selection, native selection handles, magnification, and drag
/// behavior intact. (Filtering `canPerformAction:` is not enough on iOS 16+ — it only
/// trims the action list, the menu still presents.)
enum IosEpubEditMenuSuppression {
    private static var installed = false
    private static let lock = NSLock()

    /// Idempotent; safe to call repeatedly. Installs once for the whole process,
    /// mirroring Android where `startActionMode(_:)` is overridden on the shared
    /// WebView class for every reader instance.
    static func install() {
        lock.lock()
        defer { lock.unlock() }
        guard !installed else { return }
        guard
            let interactionClass: AnyClass = UIEditMenuInteraction.self as AnyClass?,
            let method = class_getInstanceMethod(
                interactionClass,
                NSSelectorFromString("presentEditMenuWithConfiguration:")
            )
        else { return }
        let selector = NSSelectorFromString("presentEditMenuWithConfiguration:")
        let originalImplementation = method_getImplementation(method)
        typealias PresentIMP = @convention(c) (AnyObject, Selector, UIEditMenuConfiguration) -> Void
        let block: @convention(block) (AnyObject, UIEditMenuConfiguration) -> Void = { receiver, configuration in
            // Scope: only WebKit's content view (the EPUB reader WebView). Compose text
            // fields and other UIKit text views keep their system menus.
            if let interaction = receiver as? UIEditMenuInteraction,
               let view = interaction.view,
               NSStringFromClass(type(of: view)) == "WKContentView" {
                return
            }
            let original = unsafeBitCast(originalImplementation, to: PresentIMP.self)
            original(receiver, selector, configuration)
        }
        method_setImplementation(method, imp_implementationWithBlock(block))
        installed = true
    }
}
