package com.chuckerteam.chucker.internal.ui.transaction

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.getSpans
import androidx.recyclerview.widget.RecyclerView
import com.chuckerteam.chucker.R
import com.chuckerteam.chucker.databinding.ChuckerListItemSectionBinding
import com.chuckerteam.chucker.databinding.ChuckerTransactionItemHeadersBinding
import com.chuckerteam.chucker.databinding.ChuckerTransactionItemImageBinding
import com.chuckerteam.chucker.internal.support.ChessboardDrawable
import com.chuckerteam.chucker.internal.support.SpanTextUtil
import com.chuckerteam.chucker.internal.support.highlightWithDefinedColors
import com.chuckerteam.chucker.internal.support.highlightWithDefinedColorsSubstring
import com.chuckerteam.chucker.internal.support.indicesOf
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Adapter responsible of showing the content of the Transaction Request/Response body.
 * We're using a [RecyclerView] to show the content of the body line by line to do not affect
 * performances when loading big payloads.
 */
internal class TransactionBodyAdapter2 : RecyclerView.Adapter<TransactionPayloadViewHolder2>() {

    private val items = arrayListOf<PayloadItemSection>()

    fun setItems(bodyItems: List<PayloadItemSection>) {
        val previousItemCount = items.size
        items.clear()
        items.addAll(bodyItems)
        notifyItemRangeRemoved(0, previousItemCount)
        notifyItemRangeInserted(0, items.size)
    }

    override fun onBindViewHolder(holder: TransactionPayloadViewHolder2, position: Int) {
        holder.bind(items[position])
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): TransactionPayloadViewHolder2 {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADERS -> {
                val headersItemBinding =
                    ChuckerTransactionItemHeadersBinding.inflate(inflater, parent, false)
                TransactionPayloadViewHolder2.HeaderViewHolder(headersItemBinding)
            }

            TYPE_IMAGE -> {
                val imageItemBinding =
                    ChuckerTransactionItemImageBinding.inflate(inflater, parent, false)
                TransactionPayloadViewHolder2.ImageViewHolder(imageItemBinding)
            }

            else -> {
                val bodyItemBinding =
                    ChuckerListItemSectionBinding.inflate(inflater, parent, false)
                TransactionPayloadViewHolder2.BodyLineViewHolder(bodyItemBinding)
            }
        }
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int): Int {
        return when {
            items[position].headerItem != null -> TYPE_HEADERS
            items[position].body != null -> TYPE_BODY_LINE
            items[position].imageItem != null -> TYPE_IMAGE
            else -> -1
        }
    }

    internal fun highlightQueryWithColors(
        newText: String,
        backgroundColor: Int,
        foregroundColor: Int
    ): List<SearchItemBodyLine> {
        val listOfSearchItems = arrayListOf<SearchItemBodyLine>()
        items.filterIsInstance<TransactionPayloadItem.BodyLineItem>()
            .withIndex()
            .forEach { (index, item) ->
                val listOfOccurrences = item.line.indicesOf(newText)
                if (listOfOccurrences.isNotEmpty()) {
                    // storing the occurrences and their positions
                    listOfOccurrences.forEach {
                        listOfSearchItems.add(
                            SearchItemBodyLine(
                                indexBodyLine = index + 1,
                                indexStartOfQuerySubString = it
                            )
                        )
                    }

                    // highlighting the occurrences
                    item.line.clearHighlightSpans()
                    item.line = item.line.highlightWithDefinedColors(
                        newText,
                        listOfOccurrences,
                        backgroundColor,
                        foregroundColor
                    )
                    notifyItemChanged(index + 1)
                } else {
                    // Let's clear the spans if we haven't found the query string.
                    val removedSpansCount = item.line.clearHighlightSpans()
                    if (removedSpansCount > 0) {
                        notifyItemChanged(index + 1)
                    }
                }
            }
        return listOfSearchItems
    }

    internal fun highlightItemWithColorOnPosition(
        position: Int,
        queryStartPosition: Int,
        queryText: String,
        backgroundColor: Int,
        foregroundColor: Int
    ) {
        val item = items.getOrNull(position) as? TransactionPayloadItem.BodyLineItem
        if (item != null) {
            item.line = item.line.highlightWithDefinedColorsSubstring(
                queryText,
                queryStartPosition,
                backgroundColor,
                foregroundColor
            )
            notifyItemChanged(position)
        }
    }

    internal fun resetHighlight() {
        items.filterIsInstance<TransactionPayloadItem.BodyLineItem>()
            .withIndex()
            .forEach { (index, item) ->
                val removedSpansCount = item.line.clearHighlightSpans()
                if (removedSpansCount > 0) {
                    notifyItemChanged(index + 1)
                }
            }
    }

    companion object {
        private const val TYPE_HEADERS = 1
        private const val TYPE_BODY_LINE = 2
        private const val TYPE_IMAGE = 3
    }

    /**
     * Clear span that created during search process
     * @return Number of spans that removed.
     */
    private fun SpannableStringBuilder.clearHighlightSpans(): Int {
        var removedSpansCount = 0
        val spanList = getSpans<Any>(0, length)
        for (span in spanList)
            if (span !is SpanTextUtil.ChuckerForegroundColorSpan) {
                removeSpan(span)
                removedSpansCount++
            }
        return removedSpansCount
    }

    internal data class SearchItemBodyLine(
        val indexBodyLine: Int,
        val indexStartOfQuerySubString: Int
    )
}

internal sealed class TransactionPayloadViewHolder2(view: View) : RecyclerView.ViewHolder(view) {
    abstract fun bind(item: PayloadItemSection)

    internal class HeaderViewHolder(
        private val headerBinding: ChuckerTransactionItemHeadersBinding
    ) : TransactionPayloadViewHolder2(headerBinding.root) {
        override fun bind(item: PayloadItemSection) {
            item.headerItem?.let {
                headerBinding.responseHeaders.text = it.headers
            }
        }
    }

    internal class BodyLineViewHolder(
        private val bodyBinding: ChuckerListItemSectionBinding
    ) : TransactionPayloadViewHolder2(bodyBinding.root) {

        override fun bind(item: PayloadItemSection) {
            if (item.body == null) {
                bodyBinding.clRoot.visibility = View.GONE
                return
            }

            val body = item.body

            with(bodyBinding) {
                when {
                    body.isJsonPrimitive -> {
                        imgExpand.visibility = View.GONE
                        rvSectionData.visibility = View.GONE
                        txtStartValue.text = body.asString.plus(",")
                    }

                    body.isJsonArray -> {
                        body.asJsonArray.showArrayObjects()
                    }

                    body.isJsonObject -> {
                        val obj = body.asJsonObject
                        val keys = obj.keySet()

                        if (keys.size == 0) return

                        // { "key" : "value" }
                        if (keys.size == 1) {
                            val key = obj.keySet().first()
                            val value: JsonElement = obj.get(key) ?: return
                            val keyText = "\"" + key + "\""

                            imgExpand.visibility = View.GONE
                            txtKey.text = keyText

                            when {
                                value.isJsonPrimitive -> {
                                    val text = "\"" + value.asString + "\","
                                    txtStartValue.text = text
                                    txtEndValue.visibility = View.GONE
                                }

                                value.isJsonObject -> {
                                    root.setClickForValue(element = value)
                                }

                                value.isJsonArray -> {
                                    root.setClickForValue(element = value)
                                }
                            }
                        } else {
                            // { "key1" : "value1", "key2" : "value2" }
                            imgExpand.visibility = View.GONE
                            txtKey.visibility = View.GONE
                            txtDivider.visibility = View.GONE
                            txtStartValue.visibility = View.GONE
                            txtEndValue.visibility = View.GONE
                            obj.showProperties()
                        }
                    }

                    else -> Unit
                }
            }
        }

        private fun JsonObject.showProperties() {
            val attrList = mutableListOf<PayloadItemSection>()

            for ((key, value) in entrySet()) {
                JsonObject().also {
                    it.add(key, value)
                    attrList.add(PayloadItemSection(body = it))
                }
            }

            bodyBinding.rvSectionData.visibility = View.VISIBLE
            bodyBinding.rvSectionData.adapter = TransactionBodyAdapter2().also { adapter ->
                adapter.setItems(attrList)
            }
        }

        private fun JsonArray.showArrayObjects() {
            map {
                PayloadItemSection(body = it.asJsonObject)
            }.also { list ->
                with(bodyBinding) {
                    imgExpand.visibility = View.GONE
                    txtKey.visibility = View.GONE
                    txtDivider.visibility = View.GONE
                    txtStartValue.visibility = View.GONE
                    txtEndValue.visibility = View.GONE
                    rvSectionData.visibility = View.VISIBLE
                    rvSectionData.adapter = TransactionBodyAdapter2().also { adapter ->
                        adapter.setItems(list)
                    }
                }
            }
        }

        private fun View.setClickForValue(element: JsonElement) = with(bodyBinding) {
            var isOpen = false

            imgExpand.visibility = View.VISIBLE
            txtStartValue.text = if (element.isJsonObject) "{...}" else "[...]"
            txtEndValue.visibility = View.GONE

            setOnClickListener {
                isOpen = isOpen.not()
                imgExpand.animate().rotationBy(180f * -1)

                if (isOpen) {
                    rvSectionData.visibility = View.VISIBLE
                    txtStartValue.text = if (element.isJsonObject) "{" else "["
                    txtEndValue.visibility = View.VISIBLE
                    txtEndValue.text = if (element.isJsonObject) "}" else "]"
                } else {
                    rvSectionData.visibility = View.GONE
                    txtStartValue.text = if (element.isJsonObject) "{...}" else "[...]"
                    txtEndValue.visibility = View.GONE
                }

                rvSectionData.adapter = TransactionBodyAdapter2().also { adapter ->
                    adapter.setItems(
                        listOf(PayloadItemSection(body = element))
                    )
                }
            }
        }
    }

    internal class ImageViewHolder(
        private val imageBinding: ChuckerTransactionItemImageBinding
    ) : TransactionPayloadViewHolder2(imageBinding.root) {

        override fun bind(item: PayloadItemSection) {
            item.imageItem?.let {
                imageBinding.binaryData.setImageBitmap(it.image)
                imageBinding.root.background = createContrastingBackground(it.luminance)
            }
        }

        private fun createContrastingBackground(luminance: Double?): Drawable? {
            if (luminance == null) return null

            return if (luminance < LUMINANCE_THRESHOLD) {
                ChessboardDrawable.createPattern(
                    itemView.context,
                    R.color.chucker_chessboard_even_square_light,
                    R.color.chucker_chessboard_odd_square_light,
                    R.dimen.chucker_half_grid
                )
            } else {
                ChessboardDrawable.createPattern(
                    itemView.context,
                    R.color.chucker_chessboard_even_square_dark,
                    R.color.chucker_chessboard_odd_square_dark,
                    R.dimen.chucker_half_grid
                )
            }
        }

        private companion object {
            const val LUMINANCE_THRESHOLD = 0.25
        }
    }
}

internal sealed class TransactionPayloadItem {
    internal class HeaderItem(val headers: Spanned) : TransactionPayloadItem()
    internal class BodyLineItem(var line: SpannableStringBuilder) : TransactionPayloadItem()
    internal class ImageItem(val image: Bitmap, val luminance: Double?) : TransactionPayloadItem()
}

internal data class PayloadItemSection(
    val headerItem: TransactionPayloadItem.HeaderItem? = null,
    val body: JsonElement? = null,
    val imageItem: TransactionPayloadItem.ImageItem? = null,
)
