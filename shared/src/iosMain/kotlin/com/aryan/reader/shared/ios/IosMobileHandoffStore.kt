package com.aryan.reader.shared.ios

import com.aryan.reader.shared.MobileHandoffCodec
import com.aryan.reader.shared.MobileHandoffEnvelope
import platform.Foundation.NSUserDefaults

private const val IOS_MOBILE_HANDOFF_DEFAULTS_KEY = "reader.ios.mobile_handoff.v1"

/** UserDefaults-backed queue; writes are synchronous before native callbacks return. */
internal object IosMobileHandoffStore {
    fun load(): MobileHandoffEnvelope = MobileHandoffCodec.decodeOrEmpty(
        NSUserDefaults.standardUserDefaults.stringForKey(IOS_MOBILE_HANDOFF_DEFAULTS_KEY)
    )

    fun save(envelope: MobileHandoffEnvelope) {
        NSUserDefaults.standardUserDefaults.setObject(
            MobileHandoffCodec.encode(envelope),
            forKey = IOS_MOBILE_HANDOFF_DEFAULTS_KEY,
        )
    }

    fun clear() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(IOS_MOBILE_HANDOFF_DEFAULTS_KEY)
    }
}
