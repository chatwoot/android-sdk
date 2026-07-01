package com.chatwoot.android.sdk

import androidx.compose.ui.window.ComposeUIViewController
import com.chatwoot.android.sdk.style.DefaultStyle
import com.chatwoot.android.sdk.style.StyleConfig
import platform.UIKit.UIViewController

/**
 * UIKit entry point for Swift/SwiftUI hosts: a view controller rendering [ChatPage].
 * Embed it directly or via `UIViewControllerRepresentable`.
 */
public fun ChatPageViewController(
    onFinish: () -> Unit = {},
    styleConfig: StyleConfig = DefaultStyle,
): UIViewController = ComposeUIViewController {
    ChatPage(show = true, onFinish = onFinish, styleConfig = styleConfig)
}
