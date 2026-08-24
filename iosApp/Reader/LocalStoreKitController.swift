import Combine
import Foundation
import ReaderShared
import StoreKit

#if canImport(FirebaseAuth)
import FirebaseAuth
#endif
#if canImport(FirebaseCore)
import FirebaseCore
#endif
#if canImport(FirebaseFirestore)
import FirebaseFirestore
#endif

/// StoreKit proves transactions; the worker owns durable grants. Transactions
/// remain unfinished until the worker acknowledges an idempotent account claim.
@MainActor
final class LocalStoreKitController: ObservableObject {
    private enum ProductID {
        static let pro = "episteme_pro_lifetime"
        static let credits100 = "credits_100"
        static let credits300 = "credits_300"
        static let credits750 = "credits_750"
        static let all = [pro, credits100, credits300, credits750]
    }

    private enum Endpoint {
        static let base = URL(string: "https://episteme-verifier.aryanrajivyms.workers.dev")!
        static let accountToken = base.appending(path: "v2/apple/account-token")
        static let verify = base.appending(path: "v2/apple/verify")
    }

    private struct AccountTokenResponse: Decodable { let appAccountToken: String }
    private struct VerificationResponse: Decodable { let status: String; let message: String }

    private weak var bridge: ReaderIosBridge?
    private var products: [String: Product] = [:]
    private var updatesTask: Task<Void, Never>?
    private var startupTask: Task<Void, Never>?
    private var isProUnlocked = false
    private var serverCredits = 0
    private var localTestingProUnlocked = false
    private var localTestingCredits = 0
    private var localTestingClaimedTransactions = Set<UInt64>()
    private var status: String?
    /// Durable account grants are independent from the StoreKit product catalog.
    /// Keep this separate so a catalog outage cannot clear a valid account state.
    private var serverEntitlementsLoaded = false
#if canImport(FirebaseAuth)
    private var authStateHandle: AuthStateDidChangeListenerHandle?
#endif

    func attach(to bridge: ReaderIosBridge) {
        guard self.bridge !== bridge else { return }
        self.bridge = bridge
        bridge.setLocalStoreKitHandlers(
            purchase: { [weak self] id in Task { @MainActor in await self?.purchase(productID: id) } },
            restore: { [weak self] in Task { @MainActor in await self?.restore() } }
        )
        updatesTask?.cancel()
        startupTask?.cancel()
        updatesTask = observeTransactionUpdates()
        observeAccount()
        startupTask = Task { [weak self] in
            guard let self else { return }
            await loadProducts()
            await reconcileUnfinishedTransactions()
            await refreshServerEntitlements()
        }
    }

    deinit {
        updatesTask?.cancel()
        startupTask?.cancel()
#if canImport(FirebaseAuth)
        if let authStateHandle { Auth.auth().removeStateDidChangeListener(authStateHandle) }
#endif
    }

    private func loadProducts() async {
        do {
            products = Dictionary(uniqueKeysWithValues: try await Product.products(for: ProductID.all).map { ($0.id, $0) })
            status = products.isEmpty ? "App Store products are not available yet." : nil
        } catch {
            status = "Could not load App Store products: \(error.localizedDescription)"
        }
        publish()
    }

    private func purchase(productID: String) async {
        guard let product = products[productID] else {
            status = "This product is not currently available from the App Store."
            publish(); return
        }
        guard currentFirebaseUserExists else {
            status = "Sign in with Apple or Google before purchasing."
            publish(); return
        }
        guard productID != ProductID.pro || !(isProUnlocked || localTestingProUnlocked) else {
            status = "Pro is already active on this Episteme account."
            publish(); return
        }
        do {
            let token = try await fetchAppAccountToken()
            switch try await product.purchase(options: [.appAccountToken(token)]) {
            case .success(let result):
                let transaction = try verified(result)
                if isLocalXcodeTransaction(transaction) {
                    applyLocalTestingTransaction(transaction)
                    status = "Local StoreKit test purchase completed. No server entitlement was granted."
                } else {
                    try await claimWithServer(transaction, expectedAccountToken: token)
                    await refreshServerEntitlements()
                    status = "Purchase completed."
                }
                await transaction.finish()
            case .pending: status = "Purchase is pending approval."
            case .userCancelled: status = "Purchase cancelled."
            @unknown default: status = "The App Store returned an unknown purchase state."
            }
        } catch {
            status = userFacingPurchaseError(error)
        }
        publish()
    }

    private func restore() async {
        guard currentFirebaseUserExists else {
            status = "Sign in to the Episteme account that owns the purchase before restoring."
            publish(); return
        }
        do {
            try await AppStore.sync()
            let token = try await fetchAppAccountToken()
            for await result in Transaction.currentEntitlements {
                let transaction = try verified(result)
                guard ProductID.all.contains(transaction.productID) else { continue }
                if isLocalXcodeTransaction(transaction) {
                    applyLocalTestingTransaction(transaction)
                } else {
                    try await claimWithServer(transaction, expectedAccountToken: token)
                }
                await transaction.finish()
            }
            await reconcileUnfinishedTransactions(expectedAccountToken: token)
            await refreshServerEntitlements()
            status = "Purchases restored for this Episteme account."
        } catch {
            status = "Restore failed: \(error.localizedDescription)"
        }
        publish()
    }

    private func reconcileUnfinishedTransactions(expectedAccountToken: UUID? = nil) async {
        guard currentFirebaseUserExists else { return }
        do {
            let token: UUID
            if let expectedAccountToken {
                token = expectedAccountToken
            } else {
                token = try await fetchAppAccountToken()
            }
            for await result in Transaction.unfinished {
                let transaction = try verified(result)
                guard ProductID.all.contains(transaction.productID) else { continue }
                if isLocalXcodeTransaction(transaction) {
                    applyLocalTestingTransaction(transaction)
                } else {
                    try await claimWithServer(transaction, expectedAccountToken: token)
                }
                await transaction.finish()
            }
        } catch {
            status = "A purchase is waiting for secure verification and will retry automatically."
        }
    }

    private func observeTransactionUpdates() -> Task<Void, Never> {
        Task { [weak self] in
            for await result in Transaction.updates {
                guard !Task.isCancelled, let self else { return }
                do {
                    let transaction = try self.verified(result)
                    guard ProductID.all.contains(transaction.productID) else { continue }
                    if self.isLocalXcodeTransaction(transaction) {
                        self.applyLocalTestingTransaction(transaction)
                        self.status = "Local StoreKit test transaction updated."
                    } else {
                        let token = try await self.fetchAppAccountToken()
                        try await self.claimWithServer(transaction, expectedAccountToken: token)
                        await self.refreshServerEntitlements()
                        self.status = "App Store purchase updated."
                    }
                    await transaction.finish()
                } catch {
                    self.status = "A purchase update is waiting for secure verification."
                }
                self.publish()
            }
        }
    }

    private func fetchAppAccountToken() async throws -> UUID {
        let response: AccountTokenResponse = try await postJSON(
            Endpoint.accountToken,
            body: ["idToken": try await freshFirebaseIDToken()]
        )
        guard let token = UUID(uuidString: response.appAccountToken) else { throw BillingError.invalidServerResponse }
        return token
    }

    private func claimWithServer(_ transaction: StoreKit.Transaction, expectedAccountToken: UUID) async throws {
        guard transaction.productID != ProductID.pro || transaction.revocationDate == nil else {
            throw BillingError.revokedTransaction
        }
        guard transaction.appAccountToken == expectedAccountToken else { throw BillingError.accountMismatch }
        let response: VerificationResponse = try await postJSON(
            Endpoint.verify,
            body: [
                "idToken": try await freshFirebaseIDToken(),
                "transactionId": String(transaction.id),
                "productId": transaction.productID,
                "appAccountToken": expectedAccountToken.uuidString.lowercased(),
            ]
        )
        guard response.status == "success" else { throw BillingError.serverRejected(response.message) }
    }

    private func postJSON<T: Decodable>(_ url: URL, body: [String: String]) async throws -> T {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw BillingError.invalidServerResponse }
        guard (200..<300).contains(http.statusCode) else {
            if http.statusCode == 409 { throw BillingError.accountMismatch }
            let message = (try? JSONDecoder().decode(VerificationResponse.self, from: data).message)
                ?? "Secure verification failed (HTTP \(http.statusCode))."
            throw BillingError.serverRejected(message)
        }
        return try JSONDecoder().decode(T.self, from: data)
    }

    private func refreshServerEntitlements() async {
#if canImport(FirebaseFirestore) && canImport(FirebaseAuth)
        guard let uid = Auth.auth().currentUser?.uid else {
            isProUnlocked = false
            serverCredits = 0
            serverEntitlementsLoaded = true
            publish()
            return
        }
        do {
            let snapshot = try await Firestore.firestore().collection("users").document(uid).getDocument()
            let data = snapshot.data() ?? [:]
            let sources = data["proEntitlements"] as? [String: Any] ?? [:]
            isProUnlocked = (data["isPro"] as? Bool ?? false)
                || (sources["appStoreLifetime"] as? String) == "active"
                || (sources["googlePlayLifetime"] as? String) == "active"
            serverCredits = max((data["credits"] as? NSNumber)?.intValue ?? 0, 0)
            serverEntitlementsLoaded = true
        } catch {
            status = "Could not refresh account entitlements."
        }
#else
        isProUnlocked = false
        serverCredits = 0
        serverEntitlementsLoaded = true
#endif
        publish()
    }

    private func observeAccount() {
#if canImport(FirebaseAuth) && canImport(FirebaseCore)
        guard FirebaseApp.app() != nil else {
            status = "Firebase must be configured before purchases can be restored."
            publish(); return
        }
        if let authStateHandle { Auth.auth().removeStateDidChangeListener(authStateHandle) }
        authStateHandle = Auth.auth().addStateDidChangeListener { [weak self] _, _ in
            Task { @MainActor in
                guard let self else { return }
                // Do not project the previous Firebase account while the new
                // account's durable grants are being fetched.
                self.serverEntitlementsLoaded = false
                self.isProUnlocked = false
                self.serverCredits = 0
                self.publish()
                await self.reconcileUnfinishedTransactions()
                await self.refreshServerEntitlements()
            }
        }
#endif
    }

    private var currentFirebaseUserExists: Bool {
#if canImport(FirebaseAuth)
        Auth.auth().currentUser != nil
#else
        false
#endif
    }

    private func freshFirebaseIDToken() async throws -> String {
#if canImport(FirebaseAuth)
        guard let user = Auth.auth().currentUser else { throw BillingError.signInRequired }
        return try await withCheckedThrowingContinuation { continuation in
            user.getIDTokenForcingRefresh(true) { token, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let token {
                    continuation.resume(returning: token)
                } else {
                    continuation.resume(throwing: BillingError.invalidServerResponse)
                }
            }
        }
#else
        throw BillingError.signInRequired
#endif
    }

    private func verified<T>(_ result: VerificationResult<T>) throws -> T {
        switch result {
        case .verified(let value): return value
        case .unverified: throw BillingError.unverifiedTransaction
        }
    }

    private func isLocalXcodeTransaction(_ transaction: StoreKit.Transaction) -> Bool {
#if DEBUG
        return transaction.environment == .xcode
#else
        return false
#endif
    }

    private func applyLocalTestingTransaction(_ transaction: StoreKit.Transaction) {
#if DEBUG
        guard localTestingClaimedTransactions.insert(transaction.id).inserted else { return }
        if transaction.productID == ProductID.pro {
            localTestingProUnlocked = transaction.revocationDate == nil
        } else if transaction.productID == ProductID.credits100 {
            localTestingCredits += 100
        } else if transaction.productID == ProductID.credits300 {
            localTestingCredits += 300
        } else if transaction.productID == ProductID.credits750 {
            localTestingCredits += 750
        }
#endif
    }

    private func publish() {
        bridge?.updateLocalStoreKitState(
            available: !products.isEmpty,
            entitlementsLoaded: serverEntitlementsLoaded,
            proUnlocked: isProUnlocked || localTestingProUnlocked,
            credits: Int32(clamping: serverCredits + localTestingCredits),
            proPrice: products[ProductID.pro]?.displayPrice,
            credits100Price: products[ProductID.credits100]?.displayPrice,
            credits300Price: products[ProductID.credits300]?.displayPrice,
            credits750Price: products[ProductID.credits750]?.displayPrice,
            status: status
        )
    }

    private func userFacingPurchaseError(_ error: Error) -> String {
        if case BillingError.accountMismatch = error {
            return "This purchase belongs to another Episteme account. Sign in to that account or use account recovery; nothing was transferred."
        }
        return "Purchase failed: \(error.localizedDescription)"
    }

    private enum BillingError: LocalizedError {
        case signInRequired, unverifiedTransaction, revokedTransaction, accountMismatch, invalidServerResponse
        case serverRejected(String)

        var errorDescription: String? {
            switch self {
            case .signInRequired: return "Sign in with Apple or Google first."
            case .unverifiedTransaction: return "The App Store transaction could not be verified."
            case .revokedTransaction: return "This purchase has been refunded or revoked."
            case .accountMismatch: return "The purchase is linked to another Episteme account."
            case .invalidServerResponse: return "The purchase server returned an invalid response."
            case .serverRejected(let message): return message
            }
        }
    }
}
