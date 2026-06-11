package app.dku.embededapp.ui.compose

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import app.dku.embededapp.R
import app.dku.embededapp.ui.detection.DetectionOverlayView
import app.dku.embededapp.ui.detection.DetectionResultController

class RegisterInteropViews(context: Context) {
    val root: ConstraintLayout
    val cameraPreview: PreviewView
    val detectionOverlay: DetectionOverlayView
    val captureButton: MaterialButton
    val detectionResultViews: DetectionResultController.Views

    init {
        val builder = RegisterLayoutBuilder(context)
        root = builder.root
        cameraPreview = builder.cameraPreview
        detectionOverlay = builder.detectionOverlay
        captureButton = builder.captureButton
        detectionResultViews = DetectionResultController.Views(
            builder.frozenFrame,
            builder.cameraFlash,
            builder.detectionModalScrim,
            builder.detectionResultModal,
            builder.detectionResultImage,
            builder.detectionTransitionImage,
            builder.detectionCategoryDropdown,
            builder.detectionDetailGroup,
            builder.detectionDetailDropdown,
            builder.detectionColorDropdown,
            builder.detectionResultCancel,
            builder.detectionResultConfirm,
        )
    }
}

private class RegisterLayoutBuilder(private val context: Context) {
    val root = ConstraintLayout(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setPadding(dp(20), dp(8), dp(20), dp(22))
    }
    val cameraPreview = PreviewView(context).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }
    val frozenFrame = ImageView(context).apply {
        setBackgroundColor(color(android.R.color.black))
        scaleType = ImageView.ScaleType.CENTER_CROP
        visibility = View.GONE
    }
    val detectionOverlay = DetectionOverlayView(context, null).apply {
        visibility = View.GONE
    }
    val cameraFlash = View(context).apply {
        alpha = 0f
        setBackgroundColor(color(android.R.color.white))
        visibility = View.GONE
    }
    val captureButton = primaryButton(R.string.capture)
    val detectionModalScrim = View(context).apply {
        alpha = 0f
        setBackgroundColor(0x99000000.toInt())
        visibility = View.GONE
    }
    val detectionResultModal = MaterialCardView(context).apply {
        alpha = 0f
        clipToOutline = true
        radius = dp(24).toFloat()
        cardElevation = dp(14).toFloat()
        setCardBackgroundColor(color(R.color.laundry_surface))
        strokeColor = color(R.color.laundry_line)
        strokeWidth = dp(1)
        visibility = View.GONE
    }
    val detectionResultImage = ImageView(context).apply {
        setBackgroundColor(color(R.color.laundry_line))
        scaleType = ImageView.ScaleType.CENTER_CROP
    }
    val detectionTransitionImage = ImageView(context).apply {
        elevation = dp(20).toFloat()
        scaleType = ImageView.ScaleType.CENTER_CROP
        visibility = View.GONE
    }
    val detectionCategoryDropdown = dropdownButton()
    val detectionDetailGroup = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
    }
    val detectionDetailDropdown = dropdownButton()
    val detectionColorDropdown = dropdownButton()
    val detectionResultCancel = closeButton()
    val detectionResultConfirm = primaryButton(R.string.detected_laundry_confirm).apply {
        minHeight = 0
        insetTop = 0
        insetBottom = 0
    }

    init {
        build()
    }

    private fun build() {
        val frame = View(context).apply {
            id = View.generateViewId()
            setBackgroundResource(R.drawable.border_light_green)
        }
        val description = TextView(context).apply {
            id = View.generateViewId()
            gravity = Gravity.CENTER
            setText(R.string.scan_description)
            setTextColor(color(R.color.laundry_text_muted))
            textSize = 14f
        }

        cameraPreview.id = View.generateViewId()
        frozenFrame.id = View.generateViewId()
        detectionOverlay.id = View.generateViewId()
        cameraFlash.id = View.generateViewId()
        captureButton.id = View.generateViewId()
        detectionModalScrim.id = View.generateViewId()
        detectionResultModal.id = View.generateViewId()
        detectionTransitionImage.id = View.generateViewId()

        root.addView(frame)
        root.addView(cameraPreview)
        root.addView(frozenFrame)
        root.addView(detectionOverlay)
        root.addView(cameraFlash)
        root.addView(description)
        root.addView(captureButton)
        root.addView(detectionModalScrim)
        root.addView(detectionResultModal)
        root.addView(detectionTransitionImage)
        detectionResultModal.addView(createDetectionModalContent())

        ConstraintSet().apply {
            clone(root)

            constrainWidth(frame.id, ConstraintSet.MATCH_CONSTRAINT)
            constrainHeight(frame.id, ConstraintSet.MATCH_CONSTRAINT)
            constrainMaxWidth(frame.id, dp(312))
            setDimensionRatio(frame.id, "W,3:4")
            connect(frame.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(frame.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(frame.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            connect(frame.id, ConstraintSet.BOTTOM, description.id, ConstraintSet.TOP)
            setVerticalBias(frame.id, 0f)

            constrainToFrame(cameraPreview.id, frame.id)
            constrainToFrame(frozenFrame.id, frame.id)
            constrainToFrame(detectionOverlay.id, frame.id)
            constrainToFrame(cameraFlash.id, frame.id)

            constrainWidth(description.id, ConstraintSet.MATCH_CONSTRAINT)
            constrainHeight(description.id, ConstraintSet.WRAP_CONTENT)
            constrainMaxWidth(description.id, dp(280))
            connect(description.id, ConstraintSet.TOP, frame.id, ConstraintSet.BOTTOM, dp(20))
            connect(description.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(description.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            connect(description.id, ConstraintSet.BOTTOM, captureButton.id, ConstraintSet.TOP)

            constrainWidth(captureButton.id, ConstraintSet.MATCH_CONSTRAINT)
            constrainHeight(captureButton.id, ConstraintSet.WRAP_CONTENT)
            connect(captureButton.id, ConstraintSet.TOP, description.id, ConstraintSet.BOTTOM, dp(16))
            connect(captureButton.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, dp(18))
            connect(captureButton.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, dp(18))
            connect(captureButton.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

            constrainToParent(detectionModalScrim.id)

            constrainWidth(detectionResultModal.id, ConstraintSet.MATCH_CONSTRAINT)
            constrainHeight(detectionResultModal.id, ConstraintSet.WRAP_CONTENT)
            constrainMaxWidth(detectionResultModal.id, dp(320))
            constrainMaxHeight(detectionResultModal.id, dp(500))
            connect(detectionResultModal.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(detectionResultModal.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(detectionResultModal.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            connect(detectionResultModal.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

            constrainWidth(detectionTransitionImage.id, dp(1))
            constrainHeight(detectionTransitionImage.id, dp(1))
            connect(detectionTransitionImage.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(detectionTransitionImage.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)

            applyTo(root)
        }
    }

    private fun createDetectionModalContent(): View {
        return ScrollView(context).apply {
            clipToPadding = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(18), dp(20), dp(16))
                addView(createDetectionModalTitle(), linearParams(match = true))
                addView(detectionResultImage, linearParams(width = dp(112), height = dp(112), top = dp(14)))
                addView(labeledDropdown(R.string.laundry_category_label, detectionCategoryDropdown), linearParams(match = true, top = dp(14)))
                addView(labeledDropdown(R.string.laundry_detail_type_label, detectionDetailDropdown, detectionDetailGroup), linearParams(match = true, top = dp(8)))
                addView(labeledDropdown(R.string.laundry_color_label, detectionColorDropdown), linearParams(match = true, top = dp(8)))
                addView(detectionResultConfirm, linearParams(match = true, height = dp(46), top = dp(14)))
            }, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
    }

    private fun createDetectionModalTitle(): View {
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(context).apply {
                setText(R.string.detected_laundry_title)
                setTextColor(color(R.color.laundry_text))
                textSize = 22f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(detectionResultCancel, linearParams(width = dp(40), height = dp(40)))
        }
    }

    private fun labeledDropdown(
        labelResId: Int,
        dropdown: MaterialButton,
        container: LinearLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL },
    ): View {
        container.addView(TextView(context).apply {
            setText(labelResId)
            setTextColor(color(R.color.laundry_text_muted))
            textSize = 11f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, linearParams(match = true))
        container.addView(dropdown, linearParams(match = true, height = dp(44), top = dp(4)))
        return container
    }

    private fun ConstraintSet.constrainToFrame(viewId: Int, frameId: Int) {
        constrainWidth(viewId, ConstraintSet.MATCH_CONSTRAINT)
        constrainHeight(viewId, ConstraintSet.MATCH_CONSTRAINT)
        connect(viewId, ConstraintSet.TOP, frameId, ConstraintSet.TOP)
        connect(viewId, ConstraintSet.START, frameId, ConstraintSet.START)
        connect(viewId, ConstraintSet.END, frameId, ConstraintSet.END)
        connect(viewId, ConstraintSet.BOTTOM, frameId, ConstraintSet.BOTTOM)
    }

    private fun ConstraintSet.constrainToParent(viewId: Int) {
        constrainWidth(viewId, ConstraintSet.MATCH_CONSTRAINT)
        constrainHeight(viewId, ConstraintSet.MATCH_CONSTRAINT)
        connect(viewId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        connect(viewId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        connect(viewId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        connect(viewId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
    }

    private fun primaryButton(textResId: Int): MaterialButton {
        return MaterialButton(context).apply {
            setText(textResId)
            isAllCaps = false
            setTextColor(color(R.color.white))
            backgroundTintList = ColorStateList.valueOf(color(R.color.laundry_primary))
            cornerRadius = dp(16)
        }
    }

    private fun closeButton(): MaterialButton {
        return MaterialButton(context).apply {
            contentDescription = context.getString(R.string.detected_laundry_cancel)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_close)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = 0
            iconSize = dp(20)
            iconTint = ColorStateList.valueOf(color(R.color.laundry_text_muted))
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            insetTop = 0
            insetBottom = 0
            setPadding(0, 0, 0, 0)
            backgroundTintList = ColorStateList.valueOf(color(R.color.laundry_surface))
            cornerRadius = dp(20)
        }
    }

    private fun dropdownButton(): MaterialButton {
        return MaterialButton(context).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            isAllCaps = false
            minHeight = 0
            insetTop = 0
            insetBottom = 0
            setPadding(dp(16), 0, dp(14), 0)
            setTextColor(color(R.color.laundry_text))
            textSize = 14f
            backgroundTintList = ColorStateList.valueOf(color(R.color.laundry_surface))
            cornerRadius = dp(12)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_dropdown_arrow)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_END
            iconTint = ColorStateList.valueOf(color(R.color.laundry_text_muted))
            strokeColor = ColorStateList.valueOf(color(R.color.laundry_line))
            strokeWidth = dp(1)
        }
    }

    private fun linearParams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        match: Boolean = false,
        top: Int = 0,
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            if (match) ViewGroup.LayoutParams.MATCH_PARENT else width,
            height,
        ).apply {
            topMargin = top
        }
    }

    private fun color(colorResId: Int): Int = ContextCompat.getColor(context, colorResId)

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
