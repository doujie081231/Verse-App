package com.verse.explorer.app.terracotta

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.app.ui.theme.VerseColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.burningtnt.terracotta.TerracottaAndroidAPI
import java.io.File

/**
 * 游戏内联机菜单
 *
 * 等待 / 扫描 / 启动 / 房间就绪（邀请码或联机地址 + 玩家列表）/ 异常 等视图，
 * 支持邀请码实时校验、邀请码自动复制、玩家列表刷新与联机核心日志导出。
 */
class TerracottaMultiplayerDialog(private val activity: android.app.Activity) :
    Dialog(activity), View.OnClickListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var stateUIContainer: FrameLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var metadataText: TextView
    private lateinit var logBtn: TextView
    private lateinit var cancelBtn: TextView

    private val waitingUI by lazy { WaitingUI() }
    private val hostScanningUI by lazy { ScanningUI() }
    private val hostStartingUI by lazy {
        StartingUI(TerracottaTexts.STATUS_HOST_STARTING, TerracottaTexts.HOST_STARTING_BACK)
    }
    private val hostOkUI by lazy { OkUI(isHost = true) }
    private val guestStartingUI by lazy {
        StartingUI(TerracottaTexts.STATUS_GUEST_STARTING, TerracottaTexts.GUEST_STARTING_BACK)
    }
    private val guestOkUI by lazy { OkUI(isHost = false) }
    private val exceptionUI by lazy { ExceptionUI() }

    private var previousState: TerracottaState? = null

    /** 关闭时的外部回调（内部清理之后调用） */
    var onDismissed: (() -> Unit)? = null

    private val stateListener: (TerracottaState) -> Unit = { newState ->
        val old = previousState
        previousState = newState
        val isHostFork = newState is TerracottaState.HostOK &&
            old is TerracottaState.HostOK && newState.isForkOf(old)
        val isGuestFork = newState is TerracottaState.GuestOK &&
            old is TerracottaState.GuestOK && newState.isForkOf(old)
        if (isHostFork) {
            hostOkUI.refreshProfiles()
        } else if (isGuestFork) {
            guestOkUI.refreshProfiles()
        } else {
            switchUI(newState)
        }
    }

    init {
        setCancelable(false)
        setupWindow()

        TerracottaController.initialize(activity)
        TerracottaController.setWaiting(activity, true)

        val meta = TerracottaController.getMetadata()
        metadataText.text = TerracottaTexts.metadata(meta.terracottaVersion, meta.easyTierVersion)
        logBtn.setOnClickListener(this)
        cancelBtn.setOnClickListener(this)

        TerracottaController.addStateListener(stateListener)
        // 先展示等待视图，避免后端首个状态到来前出现空白
        switchUI(TerracottaController.state)
        setOnDismissListener {
            TerracottaController.removeStateListener(stateListener)
            scope.cancel()
            onDismissed?.invoke()
        }
    }

    private fun setupWindow() {
        val width = (activity.resources.displayMetrics.widthPixels * 0.7).toInt()
        val height = (activity.resources.displayMetrics.heightPixels * 0.9).toInt()
        window?.setLayout(width, height)
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground(VerseColors.PcBgSecondary.toArgb(), 16.dp(context))
            setPadding(20.dp(context), 16.dp(context), 20.dp(context), 16.dp(context))
        }
        setContentView(root)

        val title = TextView(context).apply {
            text = TerracottaTexts.MENU
            setTextColor(VerseColors.PcTextPrimary.toArgb())
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(title)

        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                3.dp(context)
            ).apply { topMargin = 8.dp(context) }
        }
        root.addView(progressBar)

        stateUIContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply { topMargin = 12.dp(context) }
        }
        root.addView(stateUIContainer)

        val bottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12.dp(context) }
        }
        root.addView(bottomBar)

        metadataText = TextView(context).apply {
            setTextColor(VerseColors.PcTextMuted.toArgb())
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        bottomBar.addView(metadataText)

        logBtn = TextView(context).apply {
            text = TerracottaTexts.EXPORT_LOG_BTN
            setTextColor(VerseColors.PcAccent.toArgb())
            textSize = 14f
            setPadding(16.dp(context), 8.dp(context), 16.dp(context), 8.dp(context))
            background = cardBackground(VerseColors.PcBgTertiary.toArgb(), 8.dp(context))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 8.dp(context) }
        }
        bottomBar.addView(logBtn)

        cancelBtn = TextView(context).apply {
            text = TerracottaTexts.BACK
            setTextColor(VerseColors.PcTextPrimary.toArgb())
            textSize = 14f
            setPadding(16.dp(context), 8.dp(context), 16.dp(context), 8.dp(context))
            background = cardBackground(VerseColors.PcBgTertiary.toArgb(), 8.dp(context))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 8.dp(context) }
        }
        bottomBar.addView(cancelBtn)
    }

    override fun onClick(v: View) {
        if (v == logBtn) {
            showLogDialog()
        }
        if (v == cancelBtn) {
            dismiss()
        }
    }

    private fun switchUI(state: TerracottaState) {
        val ui = when (state) {
            is TerracottaState.HostScanning -> hostScanningUI
            is TerracottaState.HostStarting -> hostStartingUI
            is TerracottaState.HostOK -> hostOkUI
            is TerracottaState.GuestConnecting, is TerracottaState.GuestStarting -> guestStartingUI
            is TerracottaState.GuestOK -> guestOkUI
            is TerracottaState.ExceptionState -> exceptionUI
            else -> waitingUI
        }
        stateUIContainer.removeAllViews()
        stateUIContainer.addView(ui.layout, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        ui.show(state)
        progressBar.visibility = View.GONE
    }

    private fun setBusy(busy: Boolean) {
        progressBar.visibility = if (busy) View.VISIBLE else View.GONE
    }

    private fun showLogDialog() {
        val logs = TerracottaController.collectLogs() ?: TerracottaTexts.NOT_INITIALIZED
        android.app.AlertDialog.Builder(context)
            .setTitle(TerracottaTexts.EXPORT_LOG_DIALOG_TITLE)
            .setMessage(logs)
            .setPositiveButton(TerracottaTexts.EXPORT_LOG_DIALOG_EXPORT) { _, _ -> exportLogs(context) }
            .setNegativeButton(TerracottaTexts.CONFIRM_NEGATIVE, null)
            .show()
    }

    private fun copyToClipboard(label: String, text: String, toast: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    }

    private fun exportLogs(context: Context) {
        val logString = TerracottaController.collectLogs()
        try {
            val logDir = File(GameConfig.getGameDir(context), "logs")
            logDir.mkdirs()
            File(logDir, "terracotta.log").writeText(logString ?: "")
            Toast.makeText(context, TerracottaTexts.EXPORT_LOG_DONE, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            VerseLogger.error("导出联机日志失败: ${e.message}", TAG)
            Toast.makeText(context, TerracottaTexts.EXPORT_LOG_FAILED, Toast.LENGTH_SHORT).show()
        }
    }

    private abstract inner class StateBindingUI {
        abstract val layout: LinearLayout
        abstract fun show(state: TerracottaState)

        protected fun card(title: String, sub: String, onClick: (() -> Unit)? = null): LinearLayout {
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14.dp(context), 10.dp(context), 14.dp(context), 10.dp(context))
                background = cardBackground(VerseColors.PcBgTertiary.toArgb(), 10.dp(context))
            }
            box.addView(TextView(context).apply {
                text = title
                setTextColor(VerseColors.PcTextPrimary.toArgb())
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            })
            box.addView(TextView(context).apply {
                text = sub
                setTextColor(VerseColors.PcTextMuted.toArgb())
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 3.dp(context) }
            })
            if (onClick != null) {
                box.setOnClickListener { if (box.isEnabled) onClick() }
                box.isClickable = true
                box.isFocusable = true
            }
            return box
        }
    }

    /** waiting：房主 / 房客入口 + 邀请码输入 */
    private inner class WaitingUI : StateBindingUI() {
        override val layout: LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        private val player: String =
            activity.intent.getStringExtra("TERRACOTTA_PLAYER") ?: TerracottaTexts.PLAYER_ANONYMOUS

        private val hostCard: LinearLayout
        private val guestCard: LinearLayout

        init {
            hostCard = card(TerracottaTexts.WAITING_HOST_TITLE, TerracottaTexts.WAITING_HOST_DESC) {
                setBusy(true)
                hostCard.isEnabled = false
                guestCard.isEnabled = false
                scope.launch {
                    val nodes = withContext(Dispatchers.IO) { nodeUrls() }
                    val ok = TerracottaController.setScanning(null, player, nodes)
                    if (!ok) {
                        setBusy(false)
                        hostCard.isEnabled = true
                        guestCard.isEnabled = true
                    }
                }
            }
            layout.addView(hostCard, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))

            guestCard = card(TerracottaTexts.WAITING_GUEST_TITLE, TerracottaTexts.WAITING_GUEST_DESC) {
                InviteCodeDialog().show()
            }
            layout.addView(guestCard, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12.dp(context) })
        }

        private fun nodeUrls(): List<String> =
            TerracottaNodeList.fetch().map { it.toString() }

        private fun guestAction(code: String) {
            setBusy(true)
            hostCard.isEnabled = false
            guestCard.isEnabled = false
            scope.launch {
                val nodes = withContext(Dispatchers.IO) { nodeUrls() }
                val success = TerracottaController.setGuesting(code, player, nodes)
                if (!success) {
                    setBusy(false)
                    hostCard.isEnabled = true
                    guestCard.isEnabled = true
                    Toast.makeText(
                        context,
                        TerracottaTexts.WAITING_GUEST_PROMPT_INVALID,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        override fun show(state: TerracottaState) {
            hostCard.isEnabled = true
            guestCard.isEnabled = true
        }

        private inner class InviteCodeDialog : Dialog(context) {

            private lateinit var editCode: EditText
            private lateinit var validation: TextView

            init {
                setCancelable(false)
                window?.setBackgroundDrawableResource(android.R.color.transparent)
                val root = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    background = cardBackground(VerseColors.PcBgSecondary.toArgb(), 16.dp(context))
                    setPadding(20.dp(context), 16.dp(context), 20.dp(context), 16.dp(context))
                }
                setContentView(root)

                root.addView(TextView(context).apply {
                    text = TerracottaTexts.WAITING_GUEST_PROMPT_TITLE
                    setTextColor(VerseColors.PcTextPrimary.toArgb())
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                })

                editCode = EditText(context).apply {
                    setSingleLine(true)
                    setTextColor(VerseColors.PcTextPrimary.toArgb())
                    setHintTextColor(VerseColors.PcTextMuted.toArgb())
                    background = cardBackground(VerseColors.PcBgTertiary.toArgb(), 8.dp(context))
                    setPadding(12.dp(context), 10.dp(context), 12.dp(context), 10.dp(context))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 12.dp(context) }
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: Editable?) {
                            refreshValidation()
                        }
                    })
                }
                root.addView(editCode)

                validation = TextView(context).apply {
                    visibility = View.GONE
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 6.dp(context) }
                }
                root.addView(validation)

                val buttons = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 14.dp(context) }
                }
                root.addView(buttons)

                buttons.addView(TextView(context).apply {
                    text = TerracottaTexts.CONFIRM_POSITIVE
                    setTextColor(VerseColors.PcAccent.toArgb())
                    textSize = 14f
                    setPadding(16.dp(context), 8.dp(context), 16.dp(context), 8.dp(context))
                    setOnClickListener {
                        val code = editCode.text.toString()
                        if (TerracottaController.parseRoomCode(code) != null) {
                            dismiss()
                            guestAction(code)
                        } else {
                            Toast.makeText(
                                context,
                                TerracottaTexts.WAITING_GUEST_PROMPT_INVALID,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                })
                buttons.addView(TextView(context).apply {
                    text = TerracottaTexts.CONFIRM_NEGATIVE
                    setTextColor(VerseColors.PcTextSecondary.toArgb())
                    textSize = 14f
                    setPadding(16.dp(context), 8.dp(context), 16.dp(context), 8.dp(context))
                    setOnClickListener { dismiss() }
                })
            }

            override fun show() {
                super.show()
                editCode.setText("")
            }

            private fun refreshValidation() {
                val code = editCode.text.toString()
                if (code.isEmpty()) {
                    validation.visibility = View.GONE
                    return
                }
                when (TerracottaController.parseRoomCode(code)) {
                    TerracottaAndroidAPI.RoomType.TERRACOTTA_LEGACY -> showValidation(
                        TerracottaTexts.WAITING_GUEST_PROMPT_TERRACOTTA_LEGACY, Color.YELLOW
                    )
                    TerracottaAndroidAPI.RoomType.PCL2CE -> showValidation(
                        TerracottaTexts.WAITING_GUEST_PROMPT_PCL2CE, Color.YELLOW
                    )
                    TerracottaAndroidAPI.RoomType.SCAFFOLDING -> showValidation(
                        TerracottaTexts.WAITING_GUEST_PROMPT_SCAFFOLDING, Color.GREEN
                    )
                    else -> showValidation(TerracottaTexts.WAITING_GUEST_PROMPT_INVALID, VerseColors.PcRed.toArgb())
                }
            }

            private fun showValidation(text: String, color: Int) {
                validation.visibility = View.VISIBLE
                validation.text = text
                validation.setTextColor(color)
            }
        }
    }

    /** host-scanning：正在扫描局域网世界 */
    private inner class ScanningUI : StateBindingUI() {
        override val layout: LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        private val backCard: LinearLayout

        init {
            layout.addView(TextView(context).apply {
                text = TerracottaTexts.STATUS_HOST_SCANNING
                setTextColor(VerseColors.PcTextPrimary.toArgb())
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
            })
            layout.addView(ProgressBar(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 16.dp(context); gravity = Gravity.CENTER_HORIZONTAL }
            })
            layout.addView(TextView(context).apply {
                text = TerracottaTexts.HOST_SCANNING_DESC
                setTextColor(VerseColors.PcTextSecondary.toArgb())
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 16.dp(context) }
            })
            backCard = card(TerracottaTexts.BACK, TerracottaTexts.HOST_SCANNING_BACK) {
                setBusy(true)
                backCard.isEnabled = false
                TerracottaController.setWaiting(context, true)
            }
            layout.addView(backCard, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20.dp(context) })
        }

        override fun show(state: TerracottaState) {
            backCard.isEnabled = true
        }
    }

    /** host-starting / guest-connecting / guest-starting（房客附带难度提示） */
    private inner class StartingUI(
        private val title: String,
        private val backSub: String
    ) : StateBindingUI() {
        override val layout: LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        private val backCard: LinearLayout
        private val difficultyLayout: LinearLayout
        private val difficultyTextView: TextView

        init {
            layout.addView(TextView(context).apply {
                text = title
                setTextColor(VerseColors.PcTextPrimary.toArgb())
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
            })
            layout.addView(ProgressBar(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 16.dp(context); gravity = Gravity.CENTER_HORIZONTAL }
            })

            difficultyLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12.dp(context), 10.dp(context), 12.dp(context), 10.dp(context))
                background = cardBackground(VerseColors.PcBgTertiary.toArgb(), 10.dp(context))
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 16.dp(context) }
            }
            difficultyLayout.addView(TextView(context).apply {
                text = "⚠"
                textSize = 18f
                setTextColor(VerseColors.PcOrange.toArgb())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 10.dp(context) }
            })
            difficultyTextView = TextView(context).apply {
                setTextColor(VerseColors.PcTextPrimary.toArgb())
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            difficultyLayout.addView(difficultyTextView)
            layout.addView(difficultyLayout)

            layout.addView(TextView(context).apply {
                text = TerracottaTexts.DIFFICULTY_ESTIMATE_ONLY
                setTextColor(VerseColors.PcTextMuted.toArgb())
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 6.dp(context) }
            })

            backCard = card(TerracottaTexts.BACK, backSub) {
                setBusy(true)
                backCard.isEnabled = false
                TerracottaController.setWaiting(context, true)
            }
            layout.addView(backCard, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20.dp(context) })
        }

        override fun show(state: TerracottaState) {
            backCard.isEnabled = true
            val difficulty = (state as? TerracottaState.GuestStarting)?.difficulty
            val text = difficulty?.let { TerracottaTexts.difficultyText(it) }
            if (text != null) {
                difficultyTextView.text = text
                difficultyLayout.visibility = View.VISIBLE
            } else {
                difficultyLayout.visibility = View.GONE
            }
        }
    }

    /** host-ok / guest-ok：房间信息 + 玩家列表 + 复制/退出 */
    private inner class OkUI(private val isHost: Boolean) : StateBindingUI() {
        override val layout: LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        private val copyCard: LinearLayout
        private val backCard: LinearLayout
        private val infoText: TextView
        private val adapter = ProfileAdapter()

        init {
            layout.addView(TextView(context).apply {
                text = if (isHost) TerracottaTexts.STATUS_HOST_OK else TerracottaTexts.STATUS_GUEST_OK
                setTextColor(VerseColors.PcTextPrimary.toArgb())
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
            })
            layout.addView(TextView(context).apply {
                text = if (isHost) TerracottaTexts.HOST_OK_CODE else TerracottaTexts.GUEST_OK_ADDRESS
                setTextColor(VerseColors.PcTextSecondary.toArgb())
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 10.dp(context) }
            })
            infoText = TextView(context).apply {
                setTextColor(VerseColors.PcAccent.toArgb())
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextIsSelectable(true)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 6.dp(context) }
            }
            layout.addView(infoText)

            layout.addView(TextView(context).apply {
                text = TerracottaTexts.PLAYER_LIST
                setTextColor(VerseColors.PcTextSecondary.toArgb())
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 14.dp(context) }
            })

            val listView = ListView(context).apply {
                setAdapter(adapter)
                divider = null
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
                ).apply { topMargin = 4.dp(context) }
            }
            layout.addView(listView)

            copyCard = card(
                if (isHost) TerracottaTexts.HOST_OK_CODE_COPY else TerracottaTexts.GUEST_OK_ADDRESS_COPY,
                if (isHost) TerracottaTexts.HOST_OK_CODE_DESC else TerracottaTexts.GUEST_OK_ADDRESS_DESC
            ) {
                val state = TerracottaController.state
                if (isHost && state is TerracottaState.HostOK) {
                    copyToClipboard("invite_code", state.code, TerracottaTexts.HOST_OK_CODE_COPY_TOAST)
                }
                if (!isHost && state is TerracottaState.GuestOK) {
                    copyToClipboard(
                        "server_address", state.url ?: "", TerracottaTexts.GUEST_OK_ADDRESS_COPY_TOAST
                    )
                }
            }
            layout.addView(copyCard, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12.dp(context) })

            backCard = card(
                TerracottaTexts.BACK,
                if (isHost) TerracottaTexts.HOST_OK_BACK else TerracottaTexts.GUEST_OK_BACK
            ) {
                setBusy(true)
                copyCard.isEnabled = false
                backCard.isEnabled = false
                TerracottaController.setWaiting(context, true)
            }
            layout.addView(backCard, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.dp(context) })
        }

        override fun show(state: TerracottaState) {
            copyCard.isEnabled = true
            backCard.isEnabled = true
            when (state) {
                is TerracottaState.HostOK -> infoText.text = state.code
                is TerracottaState.GuestOK -> infoText.text = state.url ?: ""
                else -> {}
            }
            refreshProfiles()
            if (isHost && state is TerracottaState.HostOK) {
                copyToClipboard("invite_code", state.code, TerracottaTexts.HOST_OK_CODE_COPY_TOAST)
            }
        }

        fun refreshProfiles() {
            val profiles = when (val state = TerracottaController.state) {
                is TerracottaState.HostOK -> state.profiles
                is TerracottaState.GuestOK -> state.profiles
                else -> emptyList()
            }
            adapter.refresh(profiles)
        }
    }

    /** exception：错误描述 + 退出重试 + 导出日志 */
    private inner class ExceptionUI : StateBindingUI() {
        override val layout: LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        private val errorText: TextView
        private val backCard: LinearLayout
        private val exportCard: LinearLayout

        init {
            layout.addView(TextView(context).apply {
                text = TerracottaTexts.STATUS_EXCEPTION
                setTextColor(VerseColors.PcRed.toArgb())
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
            })
            errorText = TextView(context).apply {
                setTextColor(VerseColors.PcTextSecondary.toArgb())
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 10.dp(context) }
            }
            layout.addView(errorText)

            backCard = card(TerracottaTexts.BACK, TerracottaTexts.EXCEPTION_BACK) {
                setBusy(true)
                backCard.isEnabled = false
                exportCard.isEnabled = false
                TerracottaController.setWaiting(context, true)
            }
            layout.addView(backCard, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20.dp(context) })

            exportCard = card(TerracottaTexts.EXPORT_LOG, TerracottaTexts.EXPORT_LOG_DESC) {
                exportLogs(context)
            }
            layout.addView(exportCard, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.dp(context) })
        }

        override fun show(state: TerracottaState) {
            backCard.isEnabled = true
            exportCard.isEnabled = true
            errorText.text = (state as? TerracottaState.ExceptionState)
                ?.let { TerracottaTexts.exceptionText(it.type) } ?: "Unknown Error"
        }
    }

    private inner class ProfileAdapter : BaseAdapter() {
        private var profiles: List<TerracottaProfile> = emptyList()

        fun refresh(list: List<TerracottaProfile>) {
            profiles = list
            notifyDataSetChanged()
        }

        override fun getCount(): Int = profiles.size
        override fun getItem(position: Int): TerracottaProfile = profiles[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val profile = getItem(position)
            val row = (convertView as? LinearLayout) ?: LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 5.dp(context), 0, 5.dp(context))

                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL

                    addView(TextView(context).apply {
                        setTextColor(VerseColors.PcTextPrimary.toArgb())
                        textSize = 14f
                        setSingleLine(true)
                    })

                    addView(TextView(context).apply {
                        textSize = 11f
                        setSingleLine(true)
                        setPadding(6.dp(context), 2.dp(context), 6.dp(context), 2.dp(context))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { marginStart = 10.dp(context) }
                        background = cardBackground(VerseColors.PcBgActive.toArgb(), 6.dp(context))
                    })
                })

                addView(TextView(context).apply {
                    setTextColor(VerseColors.PcTextMuted.toArgb())
                    textSize = 12f
                    setSingleLine(true)
                })
            }

            val header = row.getChildAt(0) as LinearLayout
            val nameView = header.getChildAt(0) as TextView
            val kindView = header.getChildAt(1) as TextView
            val vendorView = row.getChildAt(1) as TextView

            nameView.text = profile.name
            kindView.text = TerracottaTexts.playerKind(profile.type)
            vendorView.text = profile.vendor

            return row
        }
    }

    companion object {
        private const val TAG = "TerracottaMultiplayerDialog"

        private fun Int.dp(context: Context): Int =
            (this * context.resources.displayMetrics.density).toInt()

        private fun cardBackground(color: Int, radiusPx: Int): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx.toFloat()
                setColor(color)
            }

        /** 供设置页「导出联机核心日志」分享使用 */
        fun shareLogFile(context: Context) {
            val logFile = File(File(GameConfig.getGameDir(context), "logs"), "terracotta.log")
            if (!logFile.exists()) {
                Toast.makeText(context, TerracottaTexts.EXPORT_LOG_SHARE_NULL, Toast.LENGTH_SHORT).show()
                return
            }
            try {
                val shareDir = File(context.cacheDir, "logshare")
                shareDir.mkdirs()
                val target = File(shareDir, "terracotta.log")
                logFile.copyTo(target, overwrite = true)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    android.content.Intent.createChooser(intent, TerracottaTexts.EXPORT_LOG_SHARE)
                )
            } catch (e: Exception) {
                VerseLogger.error("分享联机日志失败: ${e.message}", "TerracottaShare")
                Toast.makeText(context, TerracottaTexts.EXPORT_LOG_FAILED, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
