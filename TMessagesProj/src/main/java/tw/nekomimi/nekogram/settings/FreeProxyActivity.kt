package tw.nekomimi.nekogram.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.*
import org.telegram.ui.ActionBar.*
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.ShadowSectionCell
import org.telegram.ui.Cells.TextSettingsCell
import org.telegram.ui.Components.*
import androidx.recyclerview.widget.LinearLayoutManager
import org.telegram.messenger.AndroidUtilities.dp
import tw.nekomimi.nekogram.helpers.FreeProxyManager
import tw.nekomimi.nekogram.helpers.FreeProxyManager.FreeProxy
import java.util.*
import kotlinx.coroutines.*
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.tgnet.ConnectionsManager
import tw.nekomimi.nekogram.utils.AlertUtil.showToast

class FreeProxyActivity : BaseNekoSettingsActivity(), NotificationCenterDelegate {

    private var proxies: List<FreeProxy> = emptyList()
    private var filteredProxies: List<FreeProxy> = emptyList()
    private var meta: FreeProxyManager.FreeProxyMeta? = null
    private var isLoading = false
    
    private var fetchJob: Job? = null
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var currentSearchQuery: String = ""
    private var isSearch = false
    private var selectedCountry: String? = null
    
    private var countryChips: HorizontalCountryChips? = null
    private var countries: List<String> = emptyList()

    private val pingMap = mutableMapOf<String, Long>()
    private val pendingPings = mutableSetOf<String>()
    private var proxyStartRow = 0
    private var proxyEndRow = 0

    override fun onFragmentCreate(): Boolean {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxyCheckDone)
        loadData()
        return super.onFragmentCreate()
    }

    override fun onFragmentDestroy() {
        super.onFragmentDestroy()
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxyCheckDone)
        fetchJob?.cancel()
        activityScope.cancel()
    }

    private fun loadData(force: Boolean = false) {
        isLoading = true
        listAdapter?.notifyDataSetChanged()
        fetchJob = activityScope.launch {
            try {
                proxies = FreeProxyManager.fetchProxies(force)
                meta = FreeProxyManager.fetchMeta(force)
                FileLog.d("FreeProxyActivity: Loaded ${proxies.size} proxies")
                if (proxies.isEmpty()) {
                    showToast("No proxies found. Trying mirrors...")
                } else {
                    showToast("Loaded ${proxies.size} proxies")
                }
                countries = proxies.map { it.geolocation.country }.distinct().sorted()
                filterProxies()
                isLoading = false
                updateRows()
                listAdapter?.notifyDataSetChanged()
                countryChips?.updateCountries(countries)
            } catch (e: Exception) {
                FileLog.e(e)
                showToast("Connection error. Please check your network.")
                isLoading = false
                updateRows()
                listAdapter?.notifyDataSetChanged()
            }
        }
    }

    private fun filterProxies() {
        filteredProxies = proxies.filter { proxy ->
            val matchesSearch = currentSearchQuery.isEmpty() || 
                proxy.ip.contains(currentSearchQuery, true) || 
                proxy.geolocation.country.contains(currentSearchQuery, true) || 
                proxy.geolocation.city.contains(currentSearchQuery, true)
            
            val matchesCountry = selectedCountry == null || proxy.geolocation.country == selectedCountry
            
            matchesSearch && matchesCountry
        }.sortedWith(Comparator { p1, p2 ->
            val ping1 = pingMap[p1.proxy]
            val ping2 = pingMap[p2.proxy]
            
            // Category 1: Working (ping > 0)
            // Category 2: Untested (ping == null or 0)
            // Category 3: Dead/Error (ping < 0)
            val cat1 = if (ping1 != null && ping1 > 0) 1 else if (ping1 != null && ping1 < 0) 3 else 2
            val cat2 = if (ping2 != null && ping2 > 0) 1 else if (ping2 != null && ping2 < 0) 3 else 2
            
            if (cat1 != cat2) {
                cat1.compareTo(cat2) // 1 before 2 before 3
            } else if (cat1 == 1 && ping1 != null && ping2 != null) {
                ping1.compareTo(ping2) // Lowest ping wins
            } else {
                p2.score.compareTo(p1.score) // Fallback to Proxifly score
            }
        })
    }

    override fun createActionBar(context: Context): ActionBar {
        val actionBar = super.createActionBar(context)
        
        val menu = actionBar.createMenu()
        val searchItem = menu.addItem(1, R.drawable.ic_ab_search).setIsSearchField(true)
        searchItem.getSearchField()?.let { searchField ->
            searchField.hint = LocaleController.getString("FreeProxySearchHint", R.string.FreeProxySearchHint)
            searchItem.setActionBarMenuItemSearchListener(object : ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                override fun onSearchExpand() {
                    isSearch = true
                }
                override fun onSearchCollapse() {
                    isSearch = false
                    currentSearchQuery = ""
                    filterProxies()
                    updateRows()
                    listAdapter?.notifyDataSetChanged()
                }
                override fun onTextChanged(editText: EditText) {
                    currentSearchQuery = editText.text.toString()
                    filterProxies()
                    updateRows()
                    listAdapter?.notifyDataSetChanged()
                }
            })
        }
        
        menu.addItem(2, R.drawable.ic_ab_other)
        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id == -1) {
                    finishFragment()
                } else if (id == 2) {
                    loadData(force = true)
                }
            }
        })
        return actionBar
    }

    override fun createView(context: Context): View {
        val view = super.createView(context)
        // Adjust list view if needed
        return view
    }

    override fun updateRows() {
        super.updateRows()
        addRow("country_chips")
        if (isLoading) {
            addRow("loading")
        } else {
            addRow("auto_connect")
            addRow("header_proxies")
            proxyStartRow = rowCount
            rowCount += filteredProxies.size
            proxyEndRow = rowCount
        }
    }

    override fun createAdapter(context: Context): BaseListAdapter {
        return object : BaseListAdapter(context) {
            override fun getItemCount(): Int = rowCount

            override fun getItemViewType(position: Int): Int {
                return when {
                    position == rowMap["country_chips"] -> 1001
                    position == rowMap["loading"] -> TYPE_FLICKER
                    position == rowMap["auto_connect"] -> TYPE_SETTINGS
                    position == rowMap["header_proxies"] -> TYPE_HEADER
                    position in proxyStartRow until proxyEndRow -> TYPE_DETAIL_SETTINGS + 100 // Custom type
                    else -> TYPE_SETTINGS
                }
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = when (viewType) {
                    1001 -> {
                        countryChips = HorizontalCountryChips(mContext).also {
                            it.layoutParams = RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT)
                        }
                        countryChips!!
                    }
                    TYPE_DETAIL_SETTINGS + 100 -> {
                        FreeProxyCell(mContext).also {
                            it.layoutParams = RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT)
                        }
                    }
                    TYPE_SETTINGS -> TextSettingsCell(mContext, resourcesProvider)
                    TYPE_HEADER -> HeaderCell(mContext, resourcesProvider)
                    TYPE_FLICKER -> FlickerLoadingView(mContext, resourcesProvider)
                    else -> View(mContext)
                }
                return RecyclerListView.Holder(view)
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                // Do NOT call super.onBindViewHolder because it has specific cell modernizer logic 
                // that conflicts with our manual row map in this custom view
                val type = getItemViewType(position)
                
                when (position) {
                    rowMap["auto_connect"] -> {
                        val cell = holder.itemView as TextSettingsCell
                        cell.setTextAndValue(LocaleController.getString("FreeProxyAuto", R.string.FreeProxyAuto), "", true)
                        cell.setTextColor(if (isDark) 0xFF33A1FF.toInt() else 0xFF007AFF.toInt())
                        modernizeCellManual(cell, position)
                    }
                    rowMap["header_proxies"] -> {
                        val cell = holder.itemView as HeaderCell
                        val count = meta?.totals?.all ?: filteredProxies.size
                        cell.setText(LocaleController.formatString("FreeProxyCount", R.string.FreeProxyCount, count))
                        modernizeCellManual(cell, position)
                    }
                    rowMap["country_chips"] -> {
                        modernizeCellManual(holder.itemView, position)
                    }
                }
                
                if (type == TYPE_DETAIL_SETTINGS + 100) {
                    val proxyIndex = position - (rowMap["header_proxies"]?.plus(1) ?: proxyStartRow)
                    if (proxyIndex in filteredProxies.indices) {
                        val proxy = filteredProxies[proxyIndex]
                        val cell = holder.itemView as FreeProxyCell
                        val ping = pingMap[proxy.proxy] ?: 0L
                        cell.setProxy(proxy, ping, proxyIndex != filteredProxies.size - 1)
                        modernizeCellManual(cell, position)
                    }
                } else if (type == TYPE_FLICKER) {
                    modernizeCellManual(holder.itemView, position)
                }
            }
        }
    }

    override fun onItemClick(view: View, position: Int, x: Float, y: Float) {
        when (position) {
            rowMap["auto_connect"] -> {
                val best = FreeProxyManager.getAutoProxy()
                if (best != null) {
                    FreeProxyManager.applyProxy(best)
                    finishFragment()
                    showToast("Connected to best proxy: ${best.geolocation.city}, ${best.geolocation.country}")
                }
            }
            in proxyStartRow until proxyEndRow -> {
                val proxy = filteredProxies[position - proxyStartRow]
                FreeProxyManager.applyProxy(proxy)
                finishFragment()
                showToast("Proxy applied: ${proxy.ip}")
            }
        }
    }

    override fun getActionBarTitle(): String = LocaleController.getString("FreeProxy", R.string.FreeProxy)

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id == NotificationCenter.proxyCheckDone) {
            // Safety: Some parts of the app post proxyCheckDone with fewer arguments.
            if (args.size < 6) return
            
            val address = args[0] as? String ?: return
            val port = (args[1] as? Number)?.toInt() ?: return
            
            // Ping can be Integer or Long depending on where it was posted
            val ping = (args[5] as? Number)?.toLong() ?: 0L
            
            proxies.find { it.ip == address && it.port == port }?.let {
                pingMap[it.proxy] = ping
                pendingPings.remove(it.proxy)
                AndroidUtilities.runOnUIThread {
                    if (isPaused) return@runOnUIThread
                    filterProxies()
                    updateRows()
                    listAdapter?.notifyDataSetChanged()
                }
            }
        }
    }

    private fun modernizeCellManual(view: View, position: Int) {
        val type = listAdapter?.getItemViewType(position) ?: return
        
        val isFirst = position == 0 || isBreakType(position - 1)
        val isLast = position == (listAdapter?.itemCount ?: 0) - 1 || isBreakType(position + 1)

        val gd = android.graphics.drawable.GradientDrawable()
        gd.setColor(cardBg)
        val r = dp(16f).toFloat()
        gd.setCornerRadii(floatArrayOf(
            if (isFirst) r else 0f, if (isFirst) r else 0f,
            if (isFirst) r else 0f, if (isFirst) r else 0f,
            if (isLast) r else 0f, if (isLast) r else 0f,
            if (isLast) r else 0f, if (isLast) r else 0f
        ))
        if (isFirst && isLast) {
            gd.setStroke(dp(1f), cardBorder)
        }
        view.background = gd

        if (view is TextSettingsCell) {
            view.textView.setTextColor(if (isDark) android.graphics.Color.WHITE else 0xFF1A1A2E.toInt())
            view.valueTextView.setTextColor(if (isDark) 0xFF33A1FF.toInt() else 0xFF007AFF.toInt())
        }
    }

    private inner class FreeProxyCell(context: Context) : FrameLayout(context) {
        private val titleView = TextView(context)
        private val subtitleView = TextView(context)
        private val pingView = TextView(context)
        private val scoreView = ScoreView(context)
        private var needDivider = false

        init {
            titleView.textSize = 16f
            titleView.setTextColor(if (isDark) 0xFFFFFFFF.toInt() else 0xFF1A1A2E.toInt())
            titleView.typeface = AndroidUtilities.bold()
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or Gravity.START, 72f, 10f, 80f, 0f))

            subtitleView.textSize = 13f
            subtitleView.setTextColor(if (isDark) 0xAAFFFFFF.toInt() else 0xAA1A1A2E.toInt())
            addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or Gravity.START, 72f, 32f, 80f, 0f))

            pingView.textSize = 12f
            pingView.gravity = Gravity.END
            addView(pingView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or Gravity.END, 0f, 32f, 16f, 0f))

            addView(scoreView, LayoutHelper.createFrame(40, 40f, Gravity.TOP or Gravity.START, 16f, 12f, 0f, 0f))
            
            setPadding(0, 0, 0, dp(8f))
        }

        fun setProxy(proxy: FreeProxy, ping: Long, divider: Boolean) {
            val isGlobal = proxy.geolocation.country == "ZZ" || proxy.geolocation.country.equals("unknown", true)
            val countryCode = if (isGlobal) "Global" else proxy.geolocation.country
            val flag = if (isGlobal) "🌍" else getFlagEmoji(proxy.geolocation.country)
            
            titleView.text = "$flag ${if (isGlobal) "Global" else proxy.geolocation.city}, $countryCode"
            subtitleView.text = "${proxy.protocol.uppercase()} • ${proxy.ip}:${proxy.port} • ${proxy.anonymity}"
            
            val hasPing = pingMap.containsKey(proxy.proxy)
            if (hasPing) {
                if (ping > 0) {
                    pingView.text = "${ping}ms"
                    pingView.setTextColor(if (ping < 300) 0xFF4CAF50.toInt() else if (ping < 600) 0xFFFFC107.toInt() else 0xFFF44336.toInt())
                } else {
                    pingView.text = "Error"
                    pingView.setTextColor(0xFFF44336.toInt())
                }
            } else {
                pingView.text = "---"
                pingView.setTextColor(if (isDark) 0x55FFFFFF.toInt() else 0x55000000.toInt())
                
                if (!pendingPings.contains(proxy.proxy)) {
                    pendingPings.add(proxy.proxy)
                    ConnectionsManager.getInstance(currentAccount).checkProxy(proxy.ip, proxy.port, "", "", "", { time ->
                        AndroidUtilities.runOnUIThread {
                            pingMap[proxy.proxy] = time
                            pendingPings.remove(proxy.proxy)
                            listAdapter?.notifyDataSetChanged()
                        }
                    })
                }
            }
            
            scoreView.setScore(proxy.score)
            needDivider = divider
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val height = maxOf(dp(64f), measuredHeight + dp(8f))
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
        }

        override fun onDraw(canvas: Canvas) {
            if (needDivider) {
                canvas.drawLine(dp(72f).toFloat(), measuredHeight - 1f, measuredWidth.toFloat(), measuredHeight - 1f, Theme.dividerPaint)
            }
        }
    }

    private inner class ScoreView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var score = 0
        private val rect = RectF()

        init {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(3f).toFloat()
            paint.strokeCap = Paint.Cap.ROUND
            
            textPaint.textSize = dp(10f).toFloat()
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = AndroidUtilities.bold()
        }

        fun setScore(s: Int) {
            score = s
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = measuredWidth.toFloat()
            val h = measuredHeight.toFloat()
            val r = Math.min(w, h) / 2f - paint.strokeWidth
            rect.set(w / 2f - r, h / 2f - r, w / 2f + r, h / 2f + r)
            
            // Background circle
            paint.color = if (isDark) 0x22FFFFFF.toInt() else 0x11000000.toInt()
            canvas.drawCircle(w/2f, h/2f, r, paint)
            
            // Score arc
            paint.color = when {
                score > 80 -> 0xFF4CAF50.toInt()
                score > 50 -> 0xFFFFC107.toInt()
                else -> 0xFFF44336.toInt()
            }
            canvas.drawArc(rect, -90f, (score / 100f) * 360f, false, paint)
            
            // Text
            textPaint.color = if (isDark) 0xFFFFFFFF.toInt() else 0xFF1A1A2E.toInt()
            canvas.drawText(score.toString(), w/2f, h/2f + dp(4f), textPaint)
        }
    }

    private inner class HorizontalCountryChips(context: Context) : FrameLayout(context) {
        private val listView = RecyclerListView(context)
        private var countryList: List<String> = emptyList()

        init {
            listView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            listView.adapter = object : RecyclerView.Adapter<RecyclerListView.Holder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerListView.Holder {
                    val textView = TextView(context)
                    textView.textSize = 14f
                    textView.setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
                    textView.gravity = Gravity.CENTER
                    return RecyclerListView.Holder(textView)
                }

                override fun onBindViewHolder(holder: RecyclerListView.Holder, position: Int) {
                    val textView = holder.itemView as TextView
                    val country = if (position == 0) "All" else countryList[position - 1]
                    val isSelected = (position == 0 && selectedCountry == null) || (country == selectedCountry)
                    
                    val flag = if (position == 0) "🌍" else getFlagEmoji(country)
                    textView.text = "$flag $country"
                    
                    val color = if (isSelected) (if (isDark) 0xFF33A1FF.toInt() else 0xFF007AFF.toInt()) else (if (isDark) 0x22FFFFFF.toInt() else 0x11000000.toInt())
                    val bg = android.graphics.drawable.GradientDrawable()
                    bg.setColor(color)
                    bg.cornerRadius = dp(20f).toFloat()
                    textView.background = bg
                    textView.setTextColor(if (isSelected) 0xFFFFFFFF.toInt() else (if (isDark) 0xFFBBBBBB.toInt() else 0xFF333333.toInt()))
                    
                }

                override fun getItemCount(): Int = countryList.size + 1
            }
            listView.setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            listView.setClipToPadding(false)
            
            listView.setOnItemClickListener { _, position ->
                val country = if (position == 0) "All" else countryList[position - 1]
                selectedCountry = if (position == 0) null else country
                filterProxies()
                updateRows()
                this@FreeProxyActivity.listAdapter?.notifyDataSetChanged()
                listView.adapter?.notifyDataSetChanged()
            }

            addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56f))
        }

        fun updateCountries(newCountries: List<String>) {
            countryList = newCountries
            listView.adapter?.notifyDataSetChanged()
        }
    }

    private fun getFlagEmoji(countryCode: String?): String {
        if (countryCode == null || countryCode.length != 2) return "🌐"
        if (countryCode == "ZZ") return "🌍"
        
        return try {
            val firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
            val secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
            String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
        } catch (e: Exception) {
            "🏳️"
        }
    }
}
