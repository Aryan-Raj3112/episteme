//
//  ContentView.swift
//  Reader
//
//  Created by Aryan Raj on 08/07/26.
//

import SwiftUI
import ReaderShared

struct ContentView: View {
    private let supportedFormats = SharedFileCapabilities.shared.supportedFormatsLabel(platform: ReaderPlatform.ios)
    private let sharedTimestamp = Platform_iosKt.currentTimestamp()

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Image(systemName: "book.pages")
                .imageScale(.large)
                .foregroundStyle(.tint)

            Text("Reader")
                .font(.largeTitle.bold())

            Text("iOS shell is connected to shared Kotlin.")
                .font(.headline)

            Text("Supported formats: \(supportedFormats)")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            Text("Shared clock: \(sharedTimestamp)")
                .font(.caption)
                .foregroundStyle(.tertiary)
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
