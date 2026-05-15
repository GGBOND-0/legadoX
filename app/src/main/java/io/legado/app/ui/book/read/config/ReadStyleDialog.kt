package io.legado.app.ui.book.read.config

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CompoundButton
import androidx.core.graphics.toColorInt
import androidx.core.view.get
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.liuyueyi.quick.transfer.constants.TransType
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.EventBus
import io.legado.app.constant.PageAnim
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.DialogReadBookStyleBinding
import io.legado.app.databinding.ItemBgImageBinding
import io.legado.app.databinding.ItemReadStyleBinding
import io.legado.app.databinding.ItemRestoreReadStyleBinding
import io.legado.app.help.DefaultData
import io.legado.app.help.book.isImage
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.prefs.ColorPreference.ColorPickerDialogCompat
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.applyUiBodyTypefaceDeep
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.UiCorner
import io.legado.app.lib.theme.uiTypeface
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.config.BgTextConfigDialog.Companion.BG_COLOR
import io.legado.app.ui.book.read.config.BgTextConfigDialog.Companion.READ_MENU_BG_COLOR
import io.legado.app.ui.book.read.config.BgTextConfigDialog.Companion.TEXT_ACCENT_COLOR
import io.legado.app.ui.book.read.config.BgTextConfigDialog.Companion.TEXT_COLOR
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.font.FontSelectDialog
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.ChineseUtils
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.createFileReplace
import io.legado.app.utils.createFolderReplace
import io.legado.app.utils.delete
import io.legado.app.utils.dpToPx
import io.legado.app.utils.externalCache
import io.legado.app.utils.externalFiles
import io.legado.app.utils.find
import io.legado.app.utils.getFile
import io.legado.app.utils.hexString
import io.legado.app.utils.inputStream
import io.legado.app.utils.longToast
import io.legado.app.utils.openInputStream
import io.legado.app.utils.openOutputStream
import io.legado.app.utils.outputStream
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.readBytes
import io.legado.app.utils.readUri
import io.legado.app.utils.setSelectionSafely
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class ReadStyleDialog : BaseDialogFragment(R.layout.dialog_read_book_style),
    FontSelectDialog.CallBack {

    private val binding by viewBinding(DialogReadBookStyleBinding::bind)
    private val callBack get() = activity as? ReadBookActivity
    private val configFileName = "readConfig.zip"
    private var bgSelectDialog: androidx.appcompat.app.AlertDialog? = null
    private var primaryTextColor = 0
    private var secondaryTextColor = 0
    private var currentStyleTab = StyleTab.TEXT
    private var firstStyleTabHeight = 0
    private val importFormNet = "网络导入"
    private var savedConfigSnapshot = ""
    private val selectBgImage = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri -> setBgFromUri(uri) }
    }
    private val selectExportDir = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri -> exportConfig(uri) }
    }
    private val selectImportDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            if (uri.path == "/$importFormNet") {
                importNetConfigAlert()
            } else {
                importConfig(uri)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(android.R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            val attr = attributes
            attr.dimAmount = 0.0f
            attr.gravity = Gravity.BOTTOM
            attributes = attr
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (activity as ReadBookActivity).bottomDialog++
        initView()
        initData()
        initViewEvent()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        ReadBookConfig.save()
        (activity as ReadBookActivity).bottomDialog--
    }

    override fun onResume() {
        super.onResume()
        upView()
    }

    private fun initView() = binding.run {
        rootView.applyUiBodyTypefaceDeep(requireContext().uiTypeface())
        llTextGroup.background = null
        panelPageAnim.background = null
        panelColorBackground.background = null
        updateDialogStyle()
        dsbTextSize.valueFormat = {
            (it + 5).toString()
        }
        dsbTextLetterSpacing.valueFormat = {
            ((it - 50) / 100f).toString()
        }
        dsbLineSize.valueFormat = { ((it - 10) / 10f).toString() }
        dsbParagraphSpacing.valueFormat = { (it / 10f).toString() }
        rowBgImage.setOnClickListener { showBgImageSelector() }
        lockHeightToFirstStyleTab()
    }

    private fun initData() {
        binding.cbShareLayout.isChecked = ReadBookConfig.shareLayout
        upView()
        savedConfigSnapshot = GSON.toJson(ReadBookConfig.durConfig)
    }

    private fun initViewEvent() = binding.run {
        observeEvent<Boolean>(EventBus.UPDATE_READ_ACTION_BAR) {
            upView()
        }
        observeEvent<ArrayList<Int>>(EventBus.UP_CONFIG) {
            if (it.any { value -> value == 1 || value == 2 }) {
                upView()
            }
        }
        btnTabText.setOnClickListener { showStyleTab(StyleTab.TEXT) }
        btnTabPage.setOnClickListener { showStyleTab(StyleTab.PAGE) }
        btnTabStyle.setOnClickListener { showStyleTab(StyleTab.STYLE) }

        ivEdit.setOnClickListener {
            alert(R.string.style_name) {
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.hint = "name"
                    editView.setText(ReadBookConfig.durConfig.name)
                    root.applyUiBodyTypefaceDeep(requireContext().uiTypeface())
                }
                customView { alertBinding.root }
                okButton {
                    alertBinding.editView.text?.toString()?.let {
                        tvName.text = it
                        ReadBookConfig.durConfig.name = it
                    }
                }
                cancelButton()
            }
        }
        tvRestore.setOnClickListener { restoreStyleWithUnsavedCheck() }
        ivImport.setOnClickListener {
            selectImportDoc.launch {
                mode = HandleFileContract.FILE
                title = getString(R.string.import_str)
                allowExtensions = arrayOf("zip")
                otherActions = arrayListOf(SelectItem(importFormNet, -1))
            }
        }
        ivExport.setOnClickListener {
            selectExportDir.launch {
                title = getString(R.string.export_str)
            }
        }

        rowTextColor.setOnClickListener {
            ColorPickerDialog.newBuilder()
                .setColor(ReadBookConfig.durConfig.curTextColor())
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(TEXT_COLOR)
                .show(requireActivity())
        }
        rowTextAccentColor.setOnClickListener {
            ColorPickerDialog.newBuilder()
                .setColor(ReadBookConfig.durConfig.curTextAccentColor())
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(TEXT_ACCENT_COLOR)
                .show(requireActivity())
        }
        rowBgColor.setOnClickListener {
            val bgColor = if (ReadBookConfig.durConfig.curBgType() == 0) {
                ReadBookConfig.durConfig.curBgStr().toColorInt()
            } else {
                "#015A86".toColorInt()
            }
            ColorPickerDialog.newBuilder()
                .setColor(bgColor)
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(BG_COLOR)
                .show(requireActivity())
        }
        rowMenuBgColor.setOnClickListener {
            ColorPickerDialogCompat.newBuilder()
                .setColor(ReadBookConfig.durConfig.curReadMenuBgColor() ?: defaultReadMenuBgColor())
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(READ_MENU_BG_COLOR)
                .setShowDefaultColorButton(true)
                .show(requireActivity())
        }
        rowReadMenuAlpha.setOnClickListener {
            NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.read_menu_alpha))
                .setMaxValue(100)
                .setMinValue(35)
                .setValue(ReadBookConfig.durConfig.readMenuAlpha.coerceIn(35, 100))
                .setCustomButton(R.string.btn_default_s) {
                    ReadBookConfig.durConfig.readMenuAlpha = 100
                    upView()
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }
                .show {
                    ReadBookConfig.durConfig.readMenuAlpha = it.coerceIn(35, 100)
                    upView()
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }
        }
        chineseConverter.onChanged {
            ChineseUtils.unLoad(*TransType.entries.toTypedArray())
            updateTextRows()
            postEvent(EventBus.UP_CONFIG, arrayListOf(5))
        }
        textFontWeightConverter.onChanged {
            updateTextRows()
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 9, 6))
        }
        rowTextFontWeight.setOnClickListener {
            textFontWeightConverter.performClick()
        }
        rowChineseConverter.setOnClickListener {
            chineseConverter.performClick()
        }
        rowTextFont.setOnClickListener {
            showDialogFragment<FontSelectDialog>()
        }
        tvTextFont.setOnClickListener {
            rowTextFont.performClick()
        }
        rowTextIndent.setOnClickListener {
            context?.selector(
                title = getString(R.string.text_indent),
                items = resources.getStringArray(R.array.indent).toList()
            ) { _, index ->
                ReadBookConfig.paragraphIndent = "　".repeat(index)
                updateTextRows()
                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
            }
        }
        tvTextIndent.setOnClickListener {
            rowTextIndent.performClick()
        }
        rowPadding.setOnClickListener {
            dismissAllowingStateLoss()
            callBack?.showPaddingConfig()
        }
        tvPadding.setOnClickListener {
            rowPadding.performClick()
        }
        rowTip.setOnClickListener {
            TipConfigDialog().show(childFragmentManager, "tipConfigDialog")
        }
        tvTip.setOnClickListener {
            rowTip.performClick()
        }
        pageAnimButtons().forEach { button ->
            button.setOnClickListener {
                val checkedId = button.id
                checkPageAnim(checkedId)
                ReadBook.book?.setPageAnim(-1)
                ReadBookConfig.pageAnim = pageAnimById(checkedId)
                callBack?.upPageAnim()
                ReadBook.loadContent(false)
            }
        }
        cbShareLayout.onCheckedChangeListener = { _, isChecked ->
            ReadBookConfig.shareLayout = isChecked
            upView()
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
        }
        dsbTextSize.onChanged = {
            ReadBookConfig.textSize = it + 5
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        dsbTextLetterSpacing.onChanged = {
            ReadBookConfig.letterSpacing = (it - 50) / 100f
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        dsbLineSize.onChanged = {
            ReadBookConfig.lineSpacingExtra = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        dsbParagraphSpacing.onChanged = {
            ReadBookConfig.paragraphSpacing = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
    }

    private fun showStyleTab(tab: StyleTab, requestLayout: Boolean = true) = binding.run {
        currentStyleTab = tab
        llTextGroup.visibility = if (tab == StyleTab.TEXT) View.VISIBLE else View.GONE
        panelPageAnim.visibility = if (tab == StyleTab.PAGE) View.VISIBLE else View.GONE
        panelColorBackground.visibility = if (tab == StyleTab.STYLE) View.VISIBLE else View.GONE
        val bg = ReadBookConfig.durConfig.curReadMenuBgColor() ?: defaultReadMenuBgColor()
        val palette = ReaderSheetStyle.resolve(requireContext(), bg)
        val isLight = ColorUtils.isColorLight(bg)
        val menuOpacity = (ReadBookConfig.durConfig.readMenuAlpha / 100f).coerceIn(0.35f, 1f)
        val selectedBackground = ColorUtils.blendColors(
            palette.surface,
            palette.primaryColor,
            if (isLight) 0.12f else 0.2f
        )
        listOf(
            btnTabText to StyleTab.TEXT,
            btnTabPage to StyleTab.PAGE,
            btnTabStyle to StyleTab.STYLE
        ).forEach { (tabView, itemTab) ->
            tabView.isSelected = itemTab == tab
            tabView.background = if (itemTab == tab) {
                UiCorner.opaqueRounded(
                    ColorUtils.withAlpha(selectedBackground, menuOpacity),
                    UiCorner.actionRadius(requireContext())
                )
            } else {
                ColorDrawable(Color.TRANSPARENT)
            }
        }
        if (requestLayout) {
            rootView.requestLayout()
        }
    }

    private fun lockHeightToFirstStyleTab() = binding.run {
        rootView.post {
            if (firstStyleTabHeight > 0 || currentStyleTab != StyleTab.TEXT || rootView.height <= 0) {
                return@post
            }
            firstStyleTabHeight = rootView.height
            rootView.layoutParams = rootView.layoutParams.apply {
                height = firstStyleTabHeight
            }
        }
    }

    private fun changeBgTextConfig(index: Int) {
        if (index !in ReadBookConfig.configList.indices || index == ReadBookConfig.styleSelect) {
            return
        }
        ReadBookConfig.styleSelect = index
        savedConfigSnapshot = GSON.toJson(ReadBookConfig.durConfig)
        upView()
        postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
        if (AppConfig.readBarStyleFollowPage) {
            postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
        }
    }

    private fun restoreStyleToCurrent(index: Int) {
        val restoredConfig = ReadBookConfig.configList.getOrNull(index)?.copy() ?: return
        ReadBookConfig.durConfig = restoredConfig
        savedConfigSnapshot = GSON.toJson(restoredConfig)
        upView()
        postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
        postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
    }

    private fun upView() = binding.run {
        updateDialogStyle()
        textFontWeightConverter.upUi(ReadBookConfig.textBold)
        ReadBook.pageAnim().let {
            checkPageAnim(pageAnimIdByValue(it))
        }
        ReadBookConfig.let {
            binding.tvName.text = ReadBookConfig.durConfig.name.ifBlank { "自定义" }
            dsbTextSize.progress = it.textSize - 5
            dsbTextLetterSpacing.progress = (it.letterSpacing * 100).toInt() + 50
            dsbLineSize.progress = it.lineSpacingExtra
            dsbParagraphSpacing.progress = it.paragraphSpacing
        }
        updateTextRows()
        updateColorRows()
    }

    private fun updateDialogStyle() = binding.run {
        val bg = ReadBookConfig.durConfig.curReadMenuBgColor() ?: defaultReadMenuBgColor()
        val palette = ReaderSheetStyle.resolve(requireContext(), bg)
        val menuOpacity = (ReadBookConfig.durConfig.readMenuAlpha / 100f).coerceIn(0.35f, 1f)
        primaryTextColor = palette.textColor
        secondaryTextColor = palette.secondaryTextColor
        rootView.background = ReaderSheetStyle.topSheetDrawable(
            palette.copy(surface = ColorUtils.withAlpha(bg, menuOpacity))
        )

        val isLight = ColorUtils.isColorLight(bg)
        val tabBg = ColorUtils.blendColors(
            bg,
            palette.primaryColor,
            if (isLight) 0.08f else 0.16f
        )
        tabEditBar.background = UiCorner.opaqueRounded(
            ColorUtils.withAlpha(tabBg, menuOpacity),
            UiCorner.panelRadius(requireContext())
        )
        showStyleTab(currentStyleTab, requestLayout = false)

        // tvPageAnim.setTextColor(secondaryTextColor)
        // tvName.setTextColor(secondaryTextColor)
        // tvNameTitle.setTextColor(primaryTextColor)

        ivEdit.setColorFilter(secondaryTextColor, PorterDuff.Mode.SRC_IN)
        ivImport.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
        ivExport.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)

        // tvbgimage.setTextColor(primaryTextColor)
        // tvShareLayout.setTextColor(primaryTextColor)
        // tvTextColor.setTextColor(primaryTextColor)
        // tvBgColor.setTextColor(primaryTextColor)
        // tvTextAccentColor.setTextColor(primaryTextColor)
        // tvMenuBgColor.setTextColor(primaryTextColor)
        // tvReadMenuAlpha.setTextColor(primaryTextColor)
        // listOf(
        //     textFontWeightConverter,
        //     tvTextFont,
        //     tvTextIndent,
        //     chineseConverter,
        //     tvPadding,
        //     tvTip,
        //     tvTextColorValue,
        //     tvBgColorValue,
        //     tvTextAccentColorValue,
        //     tvMenuBgColorValue,
        //     tvReadMenuAlphaValue
        // ).forEach {
        //     it.setTextColor(secondaryTextColor)
        // }
    }

    private fun updateTextRows() = binding.run {
        textFontWeightConverter.upUi(ReadBookConfig.textBold)
        tvTextFont.text = ReadBookConfig.textFont
            .takeIf { it.isNotBlank() }
            ?.let { FileDoc.fromFile(it).name }
            ?: getString(R.string.btn_default_s)
        val indentItems = resources.getStringArray(R.array.indent)
        tvTextIndent.text = indentItems.getOrNull(ReadBookConfig.paragraphIndent.length)
            ?: ReadBookConfig.paragraphIndent.length.toString()
        val config = ReadBookConfig.config
        tvPadding.text = getString(R.string.setting)
        tvTip.text = getString(R.string.setting)
    }

    override val curFontPath: String
        get() = ReadBookConfig.textFont

    override fun selectFont(path: String) {
        if (path != ReadBookConfig.textFont || path.isEmpty()) {
            ReadBookConfig.textFont = path
            updateTextRows()
            postEvent(EventBus.UP_CONFIG, arrayListOf(2, 5))
        }
    }

    private fun pageAnimById(checkedId: Int): Int {
        return when (checkedId) {
            R.id.rb_anim0 -> PageAnim.coverPageAnim
            R.id.rb_anim_linked_cover -> PageAnim.linkedCoverPageAnim
            R.id.rb_anim1 -> PageAnim.slidePageAnim
            R.id.rb_simulation_anim -> PageAnim.simulationPageAnim
            R.id.rb_scroll_anim -> PageAnim.scrollPageAnim
            R.id.rb_no_anim -> PageAnim.noAnim
            else -> PageAnim.coverPageAnim
        }
    }

    private fun pageAnimIdByValue(pageAnim: Int): Int {
        return when (pageAnim) {
            PageAnim.coverPageAnim -> R.id.rb_anim0
            PageAnim.linkedCoverPageAnim -> R.id.rb_anim_linked_cover
            PageAnim.slidePageAnim -> R.id.rb_anim1
            PageAnim.simulationPageAnim -> R.id.rb_simulation_anim
            PageAnim.scrollPageAnim -> R.id.rb_scroll_anim
            PageAnim.noAnim -> R.id.rb_no_anim
            else -> R.id.rb_anim0
        }
    }

    private fun pageAnimButtons(): List<CompoundButton> = binding.run {
        listOf(rbAnim0, rbAnimLinkedCover, rbAnim1, rbSimulationAnim, rbScrollAnim, rbNoAnim)
    }

    private fun checkPageAnim(checkedId: Int) {
        pageAnimButtons().forEach {
            it.isChecked = it.id == checkedId
        }
    }

    private fun updateColorRows() = binding.run {
        val config = ReadBookConfig.durConfig
        val textColor = config.curTextColor()
        val bgIsImage = config.curBgType() != 0
        val bgColor = if (bgIsImage) "#015A86".toColorInt() else config.curBgStr().toColorInt()
        val textAccentColor = config.curTextAccentColor()
        val menuColor = config.curReadMenuBgColor() ?: defaultReadMenuBgColor()
        tvTextColorValue.text = textColor.toHexText()
        tvBgColorValue.text = if (bgIsImage) "图片" else bgColor.toHexText()
        tvTextAccentColorValue.text = textAccentColor.toHexText()
        tvMenuBgColorValue.text = config.curReadMenuBgColor()?.toHexText() ?: getString(R.string.btn_default_s)
        updateReadMenuAlphaRow()
        vwTextColorSwatch.background = colorSwatch(textColor)
        vwBgColorSwatch.background = if (bgIsImage) {
            config.curBgDrawable(22.dpToPx(), 22.dpToPx())
        } else {
            colorSwatch(bgColor)
        }
        vwTextAccentColorSwatch.background = colorSwatch(textAccentColor)
        vwMenuBgColorSwatch.background = colorSwatch(menuColor)
        ivBgPreview.setImageDrawable(config.curBgDrawable(88.dpToPx(), 88.dpToPx()))
    }

    private fun updateReadMenuAlphaRow() = binding.run {
        tvReadMenuAlphaValue.text = getString(
            R.string.ui_layout_alpha_value,
            ReadBookConfig.durConfig.readMenuAlpha.coerceIn(35, 100)
        )
    }

    private fun defaultReadMenuBgColor(): Int {
        val baseColor = if (
            AppConfig.readBarStyleFollowPage
            && ReadBookConfig.durConfig.curBgType() == 0
        ) {
            runCatching {
                ReadBookConfig.durConfig.curBgStr().toColorInt()
            }.getOrDefault(requireContext().bottomBackground)
        } else {
            requireContext().bottomBackground
        }
        val palette = ReaderSheetStyle.resolve(requireContext(), baseColor)
        val isBgLight = ColorUtils.isColorLight(baseColor)
        return ColorUtils.blendColors(
            palette.surface,
            palette.primaryColor,
            if (isBgLight) 0.18f else 0.28f
        )
    }

    private fun colorSwatch(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = UiCorner.scaledDp(11f)
            setColor(color)
            setStroke(1.dpToPx(), secondaryTextColor)
        }
    }

    private fun showBgImageSelector() {
        val recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = GridLayoutManager(requireContext(), 5)
            clipToPadding = false
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
        }
        val adapter = BgAdapter(requireContext(), secondaryTextColor) {
            bgSelectDialog?.dismiss()
            upView()
        }
        recyclerView.adapter = adapter
        adapter.addHeaderView {
            ItemBgImageBinding.inflate(layoutInflater, it, false).apply {
                root.applyUiBodyTypefaceDeep(requireContext().uiTypeface())
                tvName.setTextColor(secondaryTextColor)
                tvName.text = getString(R.string.select_image)
                ivBg.setImageResource(R.drawable.ic_image)
                ivBg.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
                root.setOnClickListener {
                    bgSelectDialog?.dismiss()
                    selectBgImage.launch {
                        mode = HandleFileContract.IMAGE
                    }
                }
            }
        }
        requireContext().assets.list("bg")?.let {
            adapter.setItems(it.toList())
        }
        bgSelectDialog = alert(getString(R.string.bg_image)) {
            customView { recyclerView }
            onDismiss {
                bgSelectDialog = null
            }
        }
        bgSelectDialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun Int.toHexText(): String {
        return "#${hexString}".uppercase(Locale.ROOT)
    }

    private fun saveCurrentAsCustomStyle(onSaved: (() -> Unit)? = null) {
        alert(R.string.style_name) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "name"
                editView.setText(ReadBookConfig.durConfig.name.ifBlank { "自定义" })
                root.applyUiBodyTypefaceDeep(requireContext().uiTypeface())
            }
            customView { alertBinding.root }
            okButton {
                val styleName = alertBinding.editView.text?.toString()?.trim().orEmpty()
                    .ifBlank { "自定义" }
                val newConfig = ReadBookConfig.durConfig.copy(name = styleName)
                val existingIndex = ReadBookConfig.configList.withIndex()
                    .firstOrNull {
                        it.index != ReadBookConfig.styleSelect && it.value.name == styleName
                    }?.index ?: -1
                if (existingIndex >= 0) {
                    alert("覆盖样式") {
                        setMessage("已存在同名样式，是否覆盖？")
                        positiveButton("覆盖") {
                            saveStyleAt(existingIndex, newConfig, onSaved)
                        }
                        cancelButton()
                    }
                } else {
                    ReadBookConfig.configList.add(newConfig)
                    saveStyleAt(ReadBookConfig.configList.lastIndex, newConfig, onSaved)
                }
            }
            cancelButton()
        }
    }

    private fun saveStyleAt(
        index: Int,
        config: ReadBookConfig.Config,
        onSaved: (() -> Unit)? = null
    ) {
        ReadBookConfig.configList[index] = config
        ReadBookConfig.styleSelect = index
        savedConfigSnapshot = GSON.toJson(config)
        ReadBookConfig.save()
        upView()
        postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
        if (AppConfig.readBarStyleFollowPage) {
            postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
        }
        onSaved?.invoke()
    }

    private fun restoreStyleWithUnsavedCheck() {
        if (GSON.toJson(ReadBookConfig.durConfig) == savedConfigSnapshot) {
            showRestoreStyleSelector()
            return
        }
        alert("保存当前样式?") {
            setMessage("当前颜色和背景有变动, 是否先保存为自定义样式?")
            yesButton {
                saveCurrentAsCustomStyle {
                    showRestoreStyleSelector()
                }
            }
            noButton {
                showRestoreStyleSelector()
            }
            cancelButton()
        }
    }

    private fun showRestoreStyleSelector() {
        val recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
            clipToPadding = false
            setPadding(8.dpToPx(), 4.dpToPx(), 8.dpToPx(), 4.dpToPx())
        }
        val adapter = RestoreStyleAdapter()
        fun reloadItems() {
            adapter.setItems(
                ReadBookConfig.configList.withIndex()
                    .filter { it.index != ReadBookConfig.styleSelect }
                    .sortedBy { if (it.index >= DefaultData.readConfigs.size) 0 else 1 }
            )
        }
        recyclerView.adapter = adapter
        reloadItems()
        val restoreDialog = alert(getString(R.string.restore)) {
            customView { recyclerView }
        }
        adapter.onRestore = { index ->
            restoreDialog.dismiss()
            restoreStyleToCurrent(index)
        }
        adapter.onDelete = { index ->
            if (ReadBookConfig.deleteAt(index)) {
                ReadBookConfig.clearBgAndCache()
                ReadBookConfig.save()
                reloadItems()
                upView()
                postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
                toastOnUi(getString(R.string.delete_success))
            } else {
                toastOnUi("至少保留一个样式")
            }
        }
    }
    private fun exportConfig(uri: Uri) {
        val exportFileName = if (ReadBookConfig.config.name.isBlank()) {
            configFileName
        } else {
            "${ReadBookConfig.config.name}.zip"
        }
        execute {
            val exportFiles = arrayListOf<File>()
            val configDir = requireContext().externalCache.getFile("readConfig")
            configDir.createFolderReplace()
            val configFile = configDir.getFile("readConfig.json")
            configFile.createFileReplace()
            val config = ReadBookConfig.getExportConfig()
            val fontPath = ReadBookConfig.textFont
            if (fontPath.isNotEmpty()) {
                val fontDoc = FileDoc.fromFile(fontPath)
                val fontName = fontDoc.name
                fontDoc.openInputStream().getOrNull()?.use {
                    val fontExportFile = FileUtils.createFileIfNotExist(configDir, fontName)
                    fontExportFile.outputStream().use { out -> it.copyTo(out) }
                    config.textFont = fontName
                    exportFiles.add(fontExportFile)
                }
            }
            configFile.writeText(GSON.toJson(config))
            exportFiles.add(configFile)
            repeat(3) {
                val path = ReadBookConfig.durConfig.getBgPath(it) ?: return@repeat
                val bgExportFile = copyBgImage(path, configDir) ?: return@repeat
                exportFiles.add(bgExportFile)
            }
            val configZipPath = FileUtils.getPath(requireContext().externalCache, configFileName)
            if (ZipUtils.zipFiles(exportFiles, File(configZipPath))) {
                val exportDir = FileDoc.fromDir(uri)
                exportDir.find(exportFileName)?.delete()
                val exportFileDoc = exportDir.createFileIfNotExist(exportFileName)
                exportFileDoc.openOutputStream().getOrThrow().use { out ->
                    File(configZipPath).inputStream().use { it.copyTo(out) }
                }
            }
        }.onSuccess {
            toastOnUi("导出成功, 文件名为 $exportFileName")
        }.onError {
            it.printOnDebug()
            longToast("导出失败:${it.localizedMessage}")
        }
    }

    private fun copyBgImage(path: String, configDir: File): File? {
        val bgName = FileUtils.getName(path)
        val bgFile = File(path)
        if (bgFile.exists()) {
            val bgExportFile = File(FileUtils.getPath(configDir, bgName))
            if (!bgExportFile.exists()) {
                bgFile.copyTo(bgExportFile)
            }
            return bgExportFile
        }
        return null
    }

    @SuppressLint("InflateParams")
    private fun importNetConfigAlert() {
        alert("请输入地址") {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater)
            alertBinding.root.applyUiBodyTypefaceDeep(requireContext().uiTypeface())
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let { url ->
                    importNetConfig(url)
                }
            }
            cancelButton()
        }
    }

    private fun importNetConfig(url: String) {
        execute {
            okHttpClient.newCallResponseBody {
                url(url)
            }.bytes().let {
                importConfig(it)
            }
        }.onError {
            longToast(it.stackTraceStr)
        }
    }

    private fun importConfig(uri: Uri) {
        execute {
            ReadBookConfig.import(uri.readBytes(requireContext()))
        }.onSuccess {
            ReadBookConfig.durConfig = it
            savedConfigSnapshot = GSON.toJson(it)
            upView()
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
            postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            toastOnUi("导入成功")
        }.onError {
            it.printOnDebug()
            longToast("导入失败:${it.localizedMessage}")
        }
    }

    private fun importConfig(byteArray: ByteArray) {
        execute {
            ReadBookConfig.import(byteArray)
        }.onSuccess {
            ReadBookConfig.durConfig = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
            toastOnUi("导入成功")
        }.onError {
            it.printOnDebug()
            longToast("导入失败:${it.localizedMessage}")
        }
    }

    private fun setBgFromUri(uri: Uri) {
        readUri(uri) { fileDoc, inputStream ->
            kotlin.runCatching {
                var file = requireContext().externalFiles
                val suffix = if (fileDoc.name.contains(".9.png", true)) {
                    ".9.png"
                } else {
                    "." + fileDoc.name.substringAfterLast(".")
                }
                val fileName = uri.inputStream(requireContext()).getOrThrow().use {
                    MD5Utils.md5Encode(it) + suffix
                }
                file = FileUtils.createFileIfNotExist(file, "bg", fileName)
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                ReadBookConfig.durConfig.setCurBg(2, fileName)
                upView()
                postEvent(EventBus.UP_CONFIG, arrayListOf(1))
            }.onFailure {
                toastOnUi(it.localizedMessage.orEmpty())
            }
        }
    }

    inner class RestoreStyleAdapter :
        RecyclerAdapter<IndexedValue<ReadBookConfig.Config>, ItemRestoreReadStyleBinding>(requireContext()) {

        var onRestore: ((index: Int) -> Unit)? = null
        var onDelete: ((index: Int) -> Unit)? = null

        override fun getViewBinding(parent: ViewGroup): ItemRestoreReadStyleBinding {
            return ItemRestoreReadStyleBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemRestoreReadStyleBinding,
            item: IndexedValue<ReadBookConfig.Config>,
            payloads: MutableList<Any>
        ) {
            val config = item.value
            binding.apply {
                val isBuiltIn = item.index < DefaultData.readConfigs.size
                val styleName = config.name.ifBlank {
                    if (isBuiltIn) "文字" else "自定义"
                }
                tvStyleName.text = styleName
                tvStyleName.setTextColor(primaryTextColor)
                tvStyleName.typeface = requireContext().uiTypeface()
                ivStylePreview.setText("")
                ivStylePreview.setImageDrawable(config.curBgDrawable(72, 72))
                ivStylePreview.borderColor = config.curTextColor()
                ivDelete.visibility = if (isBuiltIn) View.INVISIBLE else View.VISIBLE
                ivDelete.isEnabled = !isBuiltIn
                ivDelete.setColorFilter(secondaryTextColor, PorterDuff.Mode.SRC_IN)
            }
        }

        override fun registerListener(
            holder: ItemViewHolder,
            binding: ItemRestoreReadStyleBinding
        ) {
            binding.root.setOnClickListener {
                getItem(holder.layoutPosition - getHeaderCount())?.let {
                    onRestore?.invoke(it.index)
                }
            }
            binding.ivDelete.setOnClickListener {
                getItem(holder.layoutPosition - getHeaderCount())?.let { stl ->
                    if (stl.index < DefaultData.readConfigs.size) {
                        return@let
                    }
                    alert(getString(R.string.delete)) {
                        setMessage(getString(R.string.sure_del_any, stl.value.name.ifBlank { "自定义" }))
                        positiveButton(getString(R.string.sure)) {
                            onDelete?.invoke(stl.index)
                        }
                        cancelButton()
                    }
                }
            }
        }
    }

    inner class StyleAdapter :
        RecyclerAdapter<ReadBookConfig.Config, ItemReadStyleBinding>(requireContext()) {

        override fun getViewBinding(parent: ViewGroup): ItemReadStyleBinding {
            return ItemReadStyleBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemReadStyleBinding,
            item: ReadBookConfig.Config,
            payloads: MutableList<Any>
        ) {
            binding.apply {
                ivStyle.setText(item.name.ifBlank { "文字" })
                tvStyleName.text = item.name.ifBlank { "文字" }
                tvStyleName.setTextColor(item.curTextColor())
                tvStyleName.typeface = requireContext().uiTypeface()
                ivStyle.setTypeface(requireContext().uiTypeface())
                ivStyle.setTextColor(item.curTextColor())
                ivStyle.setImageDrawable(item.curBgDrawable(100, 150))
                val itemPosition = holder.layoutPosition - getHeaderCount()
                if (ReadBookConfig.styleSelect == itemPosition) {
                    ivStyle.borderColor = accentColor
                    ivStyle.setTextBold(true)
                } else {
                    ivStyle.borderColor = item.curTextColor()
                    ivStyle.setTextBold(false)
                }
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemReadStyleBinding) {
            binding.apply {
                root.setOnClickListener {
                    if (ivStyle.isInView) {
                        changeBgTextConfig(holder.layoutPosition - getHeaderCount())
                    }
                }
            }
        }

    }

    private enum class StyleTab {
        TEXT, PAGE, STYLE
    }
}
