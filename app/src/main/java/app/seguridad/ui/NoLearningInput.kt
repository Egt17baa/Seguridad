package app.seguridad.ui

import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/**
 * A7: no keyboard may learn from any text field of the app. Every text field in [content] (and in its dialogs, menus
 * and bottom sheets: their windows compose with this composition as parent, so they see the same interceptor) asks the
 * keyboard for IME_FLAG_NO_PERSONALIZED_LEARNING, ORed into the options the field set itself (keyboard type, action,
 * no autocorrect), which stay as they were. It is a request the keyboard should honour (Gboard does); Android cannot
 * force a keyboard to obey it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NoLearningInput(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(NoLearningInterceptor, content)
}

@OptIn(ExperimentalComposeUiApi::class)
private val NoLearningInterceptor = PlatformTextInputInterceptor { request, nextHandler ->
    val noLearning = PlatformTextInputMethodRequest { outAttributes ->
        request.createInputConnection(outAttributes).also {
            // After the field filled outAttributes: added to its options, never in place of them.
            outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
    }
    nextHandler.startInputMethod(noLearning)
}
