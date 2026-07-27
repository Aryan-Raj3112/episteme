import AuthenticationServices
import Combine
import CryptoKit
import Foundation
import ReaderShared
import Security
import UIKit

#if canImport(FirebaseAuth)
import FirebaseAuth
#endif

#if canImport(FirebaseCore)
import FirebaseCore
#endif

#if canImport(GoogleSignIn)
import GoogleSignIn
#endif

/// Native authentication boundary for the shared iOS UI.
///
/// Firebase owns the Episteme account. Google Sign-In separately requests
/// `drive.appdata`, because a Firebase Google credential alone cannot authorize
/// Google Drive sync.
@MainActor
final class LocalAccountController: NSObject, ObservableObject {
    private enum Provider {
        static let apple = "APPLE"
        static let google = "GOOGLE"
    }

    private static let googleDriveScope = "https://www.googleapis.com/auth/drive.appdata"

    private weak var bridge: ReaderIosBridge?
    private var appleNonce: String?
    private var googleDriveAuthorized = false

#if canImport(FirebaseAuth)
    private var authStateHandle: AuthStateDidChangeListenerHandle?
#endif

    func attach(to bridge: ReaderIosBridge) {
        guard self.bridge !== bridge else { return }
        detachAuthObserver()
        self.bridge = bridge
        bridge.setAuthHandlers(
            authenticate: { [weak self] provider in
                Task { @MainActor in await self?.authenticate(provider: provider) }
            },
            signOut: { [weak self] in
                self?.signOut()
            }
        )
        observeAccount()
    }

    func handleOpenURL(_ url: URL) -> Bool {
#if canImport(GoogleSignIn)
        return GIDSignIn.sharedInstance.handle(url)
#else
        return false
#endif
    }

    private func authenticate(provider: String) async {
        switch provider {
        case Provider.apple:
            beginAppleSignIn()
        case Provider.google:
            await beginGoogleSignIn()
        default:
            publish(status: "Unsupported sign-in provider.")
        }
    }

    private func beginAppleSignIn() {
#if canImport(FirebaseAuth) && canImport(FirebaseCore)
        guard FirebaseApp.app() != nil else {
            publish(status: "GoogleService-Info.plist is missing from the iOS target.")
            return
        }
        let nonce = Self.randomNonce()
        appleNonce = nonce
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = Self.sha256(nonce)
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
#else
        publish(status: "Apple login is ready for Firebase, but FirebaseAuth is not added to the iOS target yet.")
#endif
    }

    private func beginGoogleSignIn() async {
#if canImport(FirebaseAuth) && canImport(FirebaseCore) && canImport(GoogleSignIn)
        guard FirebaseApp.app() != nil else {
            publish(status: "GoogleService-Info.plist is missing from the iOS target.")
            return
        }
        guard let presenter = Self.presentingViewController() else {
            publish(status: "Could not present Google sign-in.")
            return
        }
        do {
            let result = try await GIDSignIn.sharedInstance.signIn(
                withPresenting: presenter,
                hint: nil,
                additionalScopes: [Self.googleDriveScope]
            )
            guard let idToken = result.user.idToken?.tokenString else {
                publish(status: "Google did not return an ID token.")
                return
            }
            googleDriveAuthorized = result.user.grantedScopes?.contains(Self.googleDriveScope) == true
            let credential = GoogleAuthProvider.credential(
                withIDToken: idToken,
                accessToken: result.user.accessToken.tokenString
            )
            try await signInOrLink(
                credential: credential,
                providerID: "google.com",
                providerLabel: "Google"
            )
        } catch {
            publish(status: Self.userFacingAuthError(error, provider: "Google"))
        }
#else
        publish(status: "Google login needs FirebaseAuth, GoogleSignIn, and GoogleService-Info.plist in the iOS target.")
#endif
    }

#if canImport(FirebaseAuth)
    private func signInOrLink(
        credential: AuthCredential,
        providerID: String,
        providerLabel: String
    ) async throws {
        let auth = Auth.auth()
        if let user = auth.currentUser {
            if user.providerData.contains(where: { $0.providerID == providerID }) {
                try await user.reauthenticate(with: credential)
                publish(status: "\(providerLabel) authorization refreshed.")
                return
            }
            do {
                try await user.link(with: credential)
                publish(status: "\(providerLabel) linked.")
            } catch {
                let code = AuthErrorCode(_bridgedNSError: error as NSError)
                if code == .credentialAlreadyInUse || code == .accountExistsWithDifferentCredential {
                    publish(
                        status: "\(providerLabel) belongs to another Episteme account. Nothing was changed; secure account merge is required."
                    )
                    return
                }
                throw error
            }
        } else {
            try await auth.signIn(with: credential)
            publish(status: "Signed in with \(providerLabel).")
        }
    }
#endif

    private func observeAccount() {
#if canImport(FirebaseAuth) && canImport(FirebaseCore)
        guard FirebaseApp.app() != nil else {
            publish(status: "Add GoogleService-Info.plist to enable Apple and Google login.")
            return
        }
        authStateHandle = Auth.auth().addStateDidChangeListener { [weak self] _, _ in
            Task { @MainActor in self?.publish(status: nil) }
        }
        restoreGoogleDriveAuthorization()
#else
        publish(status: "Add the iOS Firebase configuration to enable Apple and Google login.")
#endif
    }

    private func restoreGoogleDriveAuthorization() {
#if canImport(GoogleSignIn)
        Task {
            do {
                let user = try await GIDSignIn.sharedInstance.restorePreviousSignIn()
                googleDriveAuthorized = user.grantedScopes?.contains(Self.googleDriveScope) == true
                publish(status: nil)
            } catch {
                googleDriveAuthorized = false
                publish(status: nil)
            }
        }
#endif
    }

    private func signOut() {
#if canImport(FirebaseAuth) && canImport(FirebaseCore)
        guard FirebaseApp.app() != nil else {
            googleDriveAuthorized = false
            publish(status: "Signed out.")
            return
        }
        do {
            try Auth.auth().signOut()
#if canImport(GoogleSignIn)
            GIDSignIn.sharedInstance.signOut()
#endif
            googleDriveAuthorized = false
            publish(status: "Signed out.")
        } catch {
            publish(status: "Sign out failed: \(error.localizedDescription)")
        }
#else
        googleDriveAuthorized = false
        publish(status: "Signed out.")
#endif
    }

    private func publish(status: String?) {
#if canImport(FirebaseAuth) && canImport(FirebaseCore)
        guard FirebaseApp.app() != nil else {
            publishSignedOut(status: status)
            return
        }
        let user = Auth.auth().currentUser
        let providerIDs = Set(user?.providerData.map(\.providerID) ?? [])
        bridge?.updateAccountState(
            uid: user?.uid,
            displayName: user?.displayName,
            email: user?.email,
            appleLinked: providerIDs.contains("apple.com"),
            googleLinked: providerIDs.contains("google.com"),
            googleDriveAuthorized: providerIDs.contains("google.com") && googleDriveAuthorized,
            status: status
        )
#else
        publishSignedOut(status: status)
#endif
    }

    private func publishSignedOut(status: String?) {
        bridge?.updateAccountState(
            uid: nil,
            displayName: nil,
            email: nil,
            appleLinked: false,
            googleLinked: false,
            googleDriveAuthorized: false,
            status: status
        )
    }

    private func detachAuthObserver() {
#if canImport(FirebaseAuth)
        if let authStateHandle {
            Auth.auth().removeStateDidChangeListener(authStateHandle)
            self.authStateHandle = nil
        }
#endif
    }

    deinit {
#if canImport(FirebaseAuth)
        if let authStateHandle {
            Auth.auth().removeStateDidChangeListener(authStateHandle)
        }
#endif
    }

    private static func randomNonce(length: Int = 32) -> String {
        precondition(length > 0)
        let characters = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remaining = length
        while remaining > 0 {
            var bytes = [UInt8](repeating: 0, count: 16)
            guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess else {
                fatalError("Unable to generate a secure Sign in with Apple nonce.")
            }
            for byte in bytes where Int(byte) < characters.count {
                result.append(characters[Int(byte)])
                remaining -= 1
                if remaining == 0 { break }
            }
        }
        return result
    }

    private static func sha256(_ value: String) -> String {
        SHA256.hash(data: Data(value.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    private static func presentingViewController() -> UIViewController? {
        let root = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .rootViewController
        return deepestPresentedViewController(from: root)
    }

    private static func deepestPresentedViewController(from root: UIViewController?) -> UIViewController? {
        if let presented = root?.presentedViewController {
            return deepestPresentedViewController(from: presented)
        }
        if let navigation = root as? UINavigationController {
            return deepestPresentedViewController(from: navigation.visibleViewController)
        }
        if let tabs = root as? UITabBarController {
            return deepestPresentedViewController(from: tabs.selectedViewController)
        }
        return root
    }

    private static func userFacingAuthError(_ error: Error, provider: String) -> String {
        let nsError = error as NSError
        if nsError.code == ASAuthorizationError.canceled.rawValue ||
            nsError.code == NSUserCancelledError {
            return "\(provider) sign-in cancelled."
        }
        return "\(provider) sign-in failed: \(error.localizedDescription)"
    }
}

extension LocalAccountController: ASAuthorizationControllerDelegate {
    nonisolated func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        Task { @MainActor in
#if canImport(FirebaseAuth)
            guard
                let appleCredential = authorization.credential as? ASAuthorizationAppleIDCredential,
                let tokenData = appleCredential.identityToken,
                let idToken = String(data: tokenData, encoding: .utf8),
                let nonce = appleNonce
            else {
                publish(status: "Apple did not return a usable identity token.")
                return
            }
            appleNonce = nil
            let credential = OAuthProvider.appleCredential(
                withIDToken: idToken,
                rawNonce: nonce,
                fullName: appleCredential.fullName
            )
            do {
                try await signInOrLink(
                    credential: credential,
                    providerID: "apple.com",
                    providerLabel: "Apple"
                )
            } catch {
                publish(status: Self.userFacingAuthError(error, provider: "Apple"))
            }
#endif
        }
    }

    nonisolated func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError error: Error
    ) {
        Task { @MainActor in
            appleNonce = nil
            publish(status: Self.userFacingAuthError(error, provider: "Apple"))
        }
    }
}

extension LocalAccountController: ASAuthorizationControllerPresentationContextProviding {
    nonisolated func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        MainActor.assumeIsolated {
            let windows = UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap(\.windows)
            guard let window = windows.first(where: \.isKeyWindow) else {
                fatalError("Sign in with Apple requires an active application window.")
            }
            return window
        }
    }
}
