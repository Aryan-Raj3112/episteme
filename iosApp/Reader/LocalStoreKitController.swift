import Foundation
import Combine
import StoreKit
import ReaderShared

@MainActor
final class LocalStoreKitController: ObservableObject {
    private enum ProductID {
        static let pro = "episteme_pro_lifetime"
        static let credits100 = "credits_100"
        static let credits300 = "credits_300"
        static let credits750 = "credits_750"
        static let all = [pro, credits100, credits300, credits750]

        static func creditAmount(for productID: String) -> Int {
            switch productID {
            case credits100: return 100
            case credits300: return 300
            case credits750: return 750
            default: return 0
            }
        }
    }

    private enum DefaultsKey {
        static let claimedTransactions = "reader.ios.localStoreKit.claimedTransactions.v1"
        static let credits = "reader.ios.localStoreKit.credits.v1"
    }

    private weak var bridge: ReaderIosBridge?
    private var products: [String: Product] = [:]
    private var updatesTask: Task<Void, Never>?
    private var isProUnlocked = false
    private var status: String?

    func attach(to bridge: ReaderIosBridge) {
        guard self.bridge !== bridge else { return }
        self.bridge = bridge
        bridge.setLocalStoreKitHandlers(
            purchase: { [weak self] productID in
                Task { @MainActor in await self?.purchase(productID: productID) }
            },
            restore: { [weak self] in
                Task { @MainActor in await self?.restore() }
            }
        )
#if DEBUG
        updatesTask?.cancel()
        updatesTask = observeTransactionUpdates()
        Task {
            await loadProducts()
            await refreshEntitlements()
        }
#else
        publish(available: false)
#endif
    }

    deinit {
        updatesTask?.cancel()
    }

    private func loadProducts() async {
        do {
            products = Dictionary(
                uniqueKeysWithValues: try await Product.products(for: ProductID.all).map { ($0.id, $0) }
            )
            status = products.isEmpty ? "No local StoreKit products found. Select Episteme.storekit in the Run scheme." : nil
        } catch {
            status = "Could not load local StoreKit products: \(error.localizedDescription)"
        }
        publish(available: true)
    }

    private func purchase(productID: String) async {
        guard let product = products[productID] else {
            status = "Product \(productID) is unavailable in the active StoreKit configuration."
            publish(available: true)
            return
        }
        do {
            let result = try await product.purchase()
            switch result {
            case .success(let verification):
                let transaction = try verified(verification)
                apply(transaction)
                await transaction.finish()
                status = "Purchase completed."
            case .pending:
                status = "Purchase is pending approval."
            case .userCancelled:
                status = "Purchase cancelled."
            @unknown default:
                status = "StoreKit returned an unknown purchase state."
            }
        } catch {
            status = "Purchase failed: \(error.localizedDescription)"
        }
        await refreshEntitlements()
    }

    private func restore() async {
        do {
            try await AppStore.sync()
            status = "Purchases restored."
        } catch {
            status = "Restore failed: \(error.localizedDescription)"
        }
        await refreshEntitlements()
    }

    private func refreshEntitlements() async {
        var hasPro = false
        for await result in Transaction.currentEntitlements {
            guard let transaction = try? verified(result) else { continue }
            if transaction.productID == ProductID.pro,
               transaction.revocationDate == nil {
                hasPro = true
            }
        }
        isProUnlocked = hasPro
        publish(available: true)
    }

    private func observeTransactionUpdates() -> Task<Void, Never> {
        Task { [weak self] in
            for await result in Transaction.updates {
                guard !Task.isCancelled, let self else { return }
                do {
                    let transaction = try self.verified(result)
                    await MainActor.run {
                        self.apply(transaction)
                        self.status = "StoreKit transaction updated."
                        self.publish(available: true)
                    }
                    await transaction.finish()
                } catch {
                    await MainActor.run {
                        self.status = "An unverified StoreKit transaction was ignored."
                        self.publish(available: true)
                    }
                }
            }
        }
    }

    private func apply(_ transaction: Transaction) {
        if transaction.productID == ProductID.pro {
            isProUnlocked = transaction.revocationDate == nil
            return
        }
        let amount = ProductID.creditAmount(for: transaction.productID)
        guard amount > 0 else { return }
        var claimed = Set(UserDefaults.standard.stringArray(forKey: DefaultsKey.claimedTransactions) ?? [])
        let claimID = String(transaction.id)
        guard claimed.insert(claimID).inserted else { return }
        UserDefaults.standard.set(Array(claimed).sorted(), forKey: DefaultsKey.claimedTransactions)
        UserDefaults.standard.set(localCredits + amount, forKey: DefaultsKey.credits)
    }

    private var localCredits: Int {
        UserDefaults.standard.integer(forKey: DefaultsKey.credits)
    }

    private func verified<T>(_ result: VerificationResult<T>) throws -> T {
        switch result {
        case .verified(let value): return value
        case .unverified: throw StoreKitError.notEntitled
        }
    }

    private func publish(available: Bool) {
        bridge?.updateLocalStoreKitState(
            available: available,
            proUnlocked: isProUnlocked,
            credits: Int32(localCredits),
            proPrice: products[ProductID.pro]?.displayPrice,
            credits100Price: products[ProductID.credits100]?.displayPrice,
            credits300Price: products[ProductID.credits300]?.displayPrice,
            credits750Price: products[ProductID.credits750]?.displayPrice,
            status: status
        )
    }
}
