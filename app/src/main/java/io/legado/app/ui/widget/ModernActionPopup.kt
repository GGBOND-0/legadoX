package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.MenuRes
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.lib.theme.UiCorner
import io.legado.app.lib.theme.applyUiMenuItemTypeface
import io.legado.app.utils.dpToPx

object ModernActionPopup {

    data class Action(
        val title: String,
        val invoke: () -> Unit
    )

    fun show(
        anchor: View,
        actions: List<Action>,
        previousPopup: PopupWindow? = null
    ): PopupWindow? {
        if (actions.isEmpty()) return previousPopup
        val context = anchor.context
        var popup: PopupWindow? = null
        val content = createContent(context, actions) {
            popup?.dismiss()
        }
        previousPopup?.dismiss()
        val popupSize = measurePopupSize(anchor, content)
        popup = PopupWindow(
            content.root,
            popupSize.first,
            popupSize.second,
            true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = 0f
            }
            content.updateScrollIndicators()
            showAnchored(anchor)
        }
        return popup
    }

    fun showFromMenu(
        anchor: View,
        @MenuRes menuRes: Int,
        previousPopup: PopupWindow? = null,
        prepare: (Menu.() -> Unit)? = null,
        onClick: (MenuItem) -> Boolean
    ): PopupWindow? {
        val popupMenu = PopupMenu(anchor.context, anchor)
        popupMenu.inflate(menuRes)
        prepare?.invoke(popupMenu.menu)
        val actions = mutableListOf<Action>()
        for (index in 0 until popupMenu.menu.size()) {
            val item = popupMenu.menu.getItem(index)
            if (item.isVisible) {
                actions.add(Action(item.title.toString()) { onClick(item) })
            }
        }
        return show(anchor, actions, previousPopup)
    }

    private fun createContent(
        context: Context,
        actions: List<Action>,
        dismiss: () -> Unit
    ): PopupContent {
        val textColor = ContextCompat.getColor(context, R.color.primaryText)
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(Color.TRANSPARENT)
            setPadding(6.dpToPx(), 6.dpToPx(), 6.dpToPx(), 6.dpToPx())
            actions.forEach { action ->
                addView(createItem(context, action, textColor, dismiss))
            }
        }
        val scrollView = ScrollView(context).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(
                list,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val root = FrameLayout(context).apply {
            background = UiCorner.opaqueRounded(
                ContextCompat.getColor(context, R.color.background_card),
                UiCorner.panelRadius(context)
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                clipToOutline = true
            }
            addView(
                scrollView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        val topIndicator = createScrollIndicator(context, "^", Gravity.TOP)
        val bottomIndicator = createScrollIndicator(context, "v", Gravity.BOTTOM)
        root.addView(topIndicator)
        root.addView(bottomIndicator)
        return PopupContent(root, scrollView, topIndicator, bottomIndicator)
    }

    private fun LinearLayout.createItem(
        context: Context,
        action: Action,
        textColor: Int,
        dismiss: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = action.title
            gravity = Gravity.CENTER_VERTICAL
            minWidth = 132.dpToPx()
            minHeight = 42.dpToPx()
            setTextColor(textColor)
            textSize = 14f
            applyUiMenuItemTypeface(context)
            includeFontPadding = false
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
            background = UiCorner.actionSelector(
                Color.TRANSPARENT,
                ContextCompat.getColor(context, R.color.background_menu),
                UiCorner.actionRadius(context)
            )
            setOnClickListener {
                dismiss()
                action.invoke()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                42.dpToPx()
            ).apply {
                setMargins(0, 1.dpToPx(), 0, 1.dpToPx())
            }
        }
    }

    private fun createScrollIndicator(
        context: Context,
        text: String,
        gravity: Int
    ): TextView {
        return TextView(context).apply {
            this.text = text
            this.gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.primaryText))
            textSize = 13f
            includeFontPadding = false
            alpha = 0.72f
            background = ColorDrawable(ContextCompat.getColor(context, R.color.background_card))
            isClickable = false
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                18.dpToPx(),
                gravity
            )
        }
    }

    private fun measurePopupSize(anchor: View, content: PopupContent): Pair<Int, Int> {
        val gap = 8.dpToPx()
        val location = IntArray(2)
        val visibleFrame = Rect()
        anchor.getLocationOnScreen(location)
        anchor.rootView.getWindowVisibleDisplayFrame(visibleFrame)
        content.scrollView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val spaceAbove = location[1] - visibleFrame.top - gap * 2
        val spaceBelow = visibleFrame.bottom - (location[1] + anchor.height) - gap * 2
        val maxHeight = maxOf(spaceAbove, spaceBelow)
            .coerceAtLeast(160.dpToPx())
            .coerceAtMost(visibleFrame.height() - gap * 2)
        return content.scrollView.measuredWidth to content.scrollView.measuredHeight.coerceAtMost(maxHeight)
    }

    private fun PopupWindow.showAnchored(anchor: View) {
        val gap = 4.dpToPx()
        val location = IntArray(2)
        val visibleFrame = Rect()
        anchor.getLocationOnScreen(location)
        anchor.rootView.getWindowVisibleDisplayFrame(visibleFrame)

        val popupWidth = width
        val popupHeight = height
        val desiredX = location[0] + anchor.width - popupWidth
        val x = desiredX.coerceIn(
            visibleFrame.left + gap,
            (visibleFrame.right - popupWidth - gap).coerceAtLeast(visibleFrame.left + gap)
        )
        val belowY = location[1] + anchor.height + gap
        val aboveY = location[1] - popupHeight - gap
        val hasRoomBelow = belowY + popupHeight <= visibleFrame.bottom - gap
        val y = if (hasRoomBelow || aboveY < visibleFrame.top + gap) {
            belowY
        } else {
            aboveY
        }.coerceIn(
            visibleFrame.top + gap,
            (visibleFrame.bottom - popupHeight - gap).coerceAtLeast(visibleFrame.top + gap)
        )
        showAtLocation(anchor.rootView, Gravity.NO_GRAVITY, x, y)
    }

    private class PopupContent(
        val root: FrameLayout,
        val scrollView: ScrollView,
        private val topIndicator: View,
        private val bottomIndicator: View
    ) {

        fun updateScrollIndicators() {
            fun update() {
                topIndicator.visibility = if (scrollView.canScrollVertically(-1)) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                bottomIndicator.visibility = if (scrollView.canScrollVertically(1)) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
            scrollView.viewTreeObserver.addOnScrollChangedListener { update() }
            scrollView.post { update() }
        }
    }
}
