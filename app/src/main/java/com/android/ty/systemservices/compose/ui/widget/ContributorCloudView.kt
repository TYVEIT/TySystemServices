package com.android.ty.systemservices.compose.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.PathInterpolator
import com.android.ty.systemservices.compose.db.ContributorEntry
import kotlin.math.min

/**
 * 云端渲染结果：位图 + 每个人名在**位图坐标**里的视觉中心。
 *
 * 中心点在渲染时一并算好，避免定位时再遍历测量一遍文本。
 */
class CloudRenderResult(
    val bitmap: Bitmap,
    val centers: Map<Int, PointF>,
)

/**
 * 把贡献者列表画成一张「名字云」。
 *
 * 完全照搬 LineageParts 的绘制规则（`ContributorsCloudFragment.generateViewInfo`）：
 * - 所有坐标与字号按 `designSize → bitmapSize` 等比换算；
 * - `rotation = 0` 横排，`-1` 逆时针 90°，`1` 顺时针 90°；
 * - `id < 0` 的记录是分组标签，基线要再上移一个字高。
 *
 * 与原实现一样，选中某人时整张图重画一次并用强调色写名字——这比在 View 上叠加高亮更可靠，
 * 因为名字是斜着/竖着排的，叠加层很难对齐。
 */
object ContributorCloudRenderer {

    fun render(
        entries: List<ContributorEntry>,
        designSize: Int,
        selectedId: Int?,
        bitmapSize: Int,
        textColor: Int,
        selectedColor: Int,
        typeface: android.graphics.Typeface? = null,
    ): CloudRenderResult {
        val size = if (designSize > 0) designSize else bitmapSize
        val bitmap = Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            this.typeface = typeface
        }
        val centers = HashMap<Int, PointF>(entries.size)

        val bounds = Rect()
        for (entry in entries) {
            val x = entry.x * bitmapSize / size
            var y = entry.y * bitmapSize / size
            val fontSize = entry.fontSize * bitmapSize / size
            if (fontSize <= 0f) continue

            paint.textSize = fontSize
            if (entry.id < 0) y -= fontSize

            // 与 LineageParts 一致：只有选中项换成强调色，其余保持前景色
            paint.color = if (selectedId != null && entry.id == selectedId) selectedColor else textColor

            val name = entry.name
            if (name.isEmpty()) continue
            val textWidth = paint.measureText(name)
            paint.getTextBounds(name, 0, name.length, bounds)
            val textHeight = bounds.height().toFloat()

            when (entry.rotation) {
                0 -> {
                    canvas.drawText(name, x, y, paint)
                    centers[entry.id] = PointF(x + textWidth / 2f, y - textHeight / 2f)
                }

                -1 -> {
                    // 逆时针 90°：先平移再绕 (x, y) 旋转，包围盒是 [x, x+th] × [y-th, y+tw-th]
                    canvas.save()
                    canvas.translate(textHeight, textWidth - textHeight)
                    canvas.rotate(-90f, x, y)
                    canvas.drawText(name, x, y, paint)
                    canvas.restore()
                    centers[entry.id] = PointF(
                        x + textHeight / 2f,
                        y + textWidth / 2f - textHeight,
                    )
                }

                else -> {
                    // 顺时针 90°：包围盒是 [x+th/2, x+3th/2] × [y-th, y+tw-th]
                    canvas.save()
                    canvas.translate(textHeight / 2f, -textHeight)
                    canvas.rotate(90f, x, y)
                    canvas.drawText(name, x, y, paint)
                    canvas.restore()
                    centers[entry.id] = PointF(
                        x + textHeight,
                        y + textWidth / 2f - textHeight,
                    )
                }
            }
        }

        return CloudRenderResult(bitmap, centers)
    }
}

/**
 * 可缩放 / 拖动的名字云。
 *
 * 手势与 LineageParts 的 `ContributorsCloudViewController` 对齐：
 * 双指缩放（上限 8 倍）、单指拖动、双击在「适应屏幕 / 3 倍」之间切换。
 * 与 Compose 配合时放进 `AndroidView` 即可，自身不消费垂直滚动。
 */
class ContributorCloudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val matrix = Matrix()
    private val values = FloatArray(9)

    private var image: Bitmap? = null
    private var centers: Map<Int, PointF> = emptyMap()

    private var minScale = 1f
    private var maxScale = 1f
    private var animator: ValueAnimator? = null

    /** 云图之外的底色，缩放时露出的区域与页面同色 */
    var canvasColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            invalidate()
        }

    /** 用户是否手动缩放/拖动过（用于「重置视图」按钮的显隐） */
    var onTransformChanged: (() -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                cancelAnimator()
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val current = currentScale()
                if (current <= 0f || !detector.isInProgress) return false
                val target = (current * detector.scaleFactor).coerceIn(minScale, maxScale)
                val factor = target / current
                if (factor == 1f) return true
                matrix.postScale(factor, factor, detector.focusX, detector.focusY)
                fixBounds()
                invalidate()
                onTransformChanged?.invoke()
                return true
            }
        },
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (scaleDetector.isInProgress) return false
                cancelAnimator()
                matrix.postTranslate(-distanceX, -distanceY)
                fixBounds()
                invalidate()
                onTransformChanged?.invoke()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (image == null) return false
                val current = currentScale()
                if (current > minScale * 1.35f) {
                    resetView()
                } else {
                    // 以双击点为中心放大：先把屏幕坐标反算成位图坐标，再解出平移量
                    val bitmapPoint = screenToBitmap(e.x, e.y)
                    val target = minScale * DOUBLE_TAP_ZOOM
                    animateTo(target, e.x - bitmapPoint.x * target, e.y - bitmapPoint.y * target)
                }
                return true
            }
        },
    )

    init {
        isClickable = true
        isFocusable = true
    }

    /** 换图（首次加载或选中项变化后重画的结果） */
    fun setCloud(result: CloudRenderResult, keepTransform: Boolean) {
        val previous = image
        val hadImage = previous != null
        image = result.bitmap
        centers = result.centers
        if (!keepTransform || !hadImage) {
            fitToView()
        } else {
            // 尺寸不变时保留当前视口，只换像素
            fixBounds()
            invalidate()
        }
        // 旧图已经不在绘制链里，立刻释放 16MB 的位图内存
        if (previous != null && previous !== result.bitmap && !previous.isRecycled) {
            previous.recycle()
        }
    }

    fun clear() {
        val previous = image
        image = null
        centers = emptyMap()
        if (previous != null && !previous.isRecycled) previous.recycle()
        invalidate()
    }

    /** 把某个人名移到屏幕中心并放大 [factor] 倍（相对「适应屏幕」比例） */
    fun focusOn(id: Int, factor: Float = FOCUS_ZOOM, animated: Boolean = true) {
        val point = centers[id] ?: return
        val target = (minScale * factor).coerceIn(minScale, maxScale)
        val tx = width / 2f - point.x * target
        val ty = height / 2f - point.y * target
        if (animated) {
            animateTo(target, tx, ty)
        } else {
            cancelAnimator()
            matrix.setScale(target, target)
            matrix.postTranslate(tx, ty)
            fixBounds()
            invalidate()
        }
    }

    /** 回到「整张云铺满屏幕」的状态 */
    fun resetView(animated: Boolean = true) {
        if (image == null) return
        if (animated) {
            animateTo(minScale, (width - image!!.width * minScale) / 2f, (height - image!!.height * minScale) / 2f)
        } else {
            cancelAnimator()
            fitToView()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitToView()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(canvasColor)
        val bmp = image ?: return
        canvas.drawBitmap(bmp, matrix, bitmapPaint)
        // 用矩阵变换后的绘制区域之外不画东西；背景色已铺满
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (image == null) return false
        val scaled = scaleDetector.onTouchEvent(event)
        val gestured = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            performClick()
        }
        return scaled || gestured || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        cancelAnimator()
        super.onDetachedFromWindow()
    }

    private fun fitToView() {
        val bmp = image ?: return
        if (width == 0 || height == 0) return
        minScale = min(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        if (minScale <= 0f) return
        maxScale = minScale * MAX_ZOOM
        matrix.setScale(minScale, minScale)
        matrix.postTranslate(
            (width - bmp.width * minScale) / 2f,
            (height - bmp.height * minScale) / 2f,
        )
        invalidate()
    }

    private fun currentScale(): Float {
        matrix.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    /** 屏幕坐标 → 位图坐标 */
    private fun screenToBitmap(x: Float, y: Float): PointF {
        val inverse = Matrix()
        if (!matrix.invert(inverse)) return PointF(x, y)
        val points = floatArrayOf(x, y)
        inverse.mapPoints(points)
        return PointF(points[0], points[1])
    }

    /**
     * 约束平移：内容比视图小时居中，比视图大时不允许露出空白边。
     */
    private fun fixBounds() {
        val bmp = image ?: return
        matrix.getValues(values)
        val scale = values[Matrix.MSCALE_X]
        val drawWidth = bmp.width * scale
        val drawHeight = bmp.height * scale

        var tx = values[Matrix.MTRANS_X]
        var ty = values[Matrix.MTRANS_Y]

        tx = if (drawWidth <= width) {
            (width - drawWidth) / 2f
        } else {
            tx.coerceIn(width - drawWidth, 0f)
        }
        ty = if (drawHeight <= height) {
            (height - drawHeight) / 2f
        } else {
            ty.coerceIn(height - drawHeight, 0f)
        }

        values[Matrix.MTRANS_X] = tx
        values[Matrix.MTRANS_Y] = ty
        matrix.setValues(values)
    }

    private fun animateTo(targetScale: Float, targetX: Float, targetY: Float) {
        cancelAnimator()
        matrix.getValues(values)
        val startScale = values[Matrix.MSCALE_X]
        val startX = values[Matrix.MTRANS_X]
        val startY = values[Matrix.MTRANS_Y]

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = TRANSFORM_DURATION_MS
            interpolator = EMPHASIZED
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                val scale = startScale + (targetScale - startScale) * t
                val tx = startX + (targetX - startX) * t
                val ty = startY + (targetY - startY) * t
                matrix.setScale(scale, scale)
                matrix.postTranslate(tx, ty)
                invalidate()
            }
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                fixBounds()
                invalidate()
                onTransformChanged?.invoke()
            }
        })
        this.animator = animator
        animator.start()
    }

    private fun cancelAnimator() {
        animator?.cancel()
        animator = null
    }

    companion object {
        /** 双指最多放大到「适应屏幕」的 8 倍 */
        const val MAX_ZOOM = 8f

        /** 从卡片跳进来时用的聚焦倍率 */
        const val FOCUS_ZOOM = 4.5f

        /** 双击放大倍率 */
        const val DOUBLE_TAP_ZOOM = 3f

        private const val TRANSFORM_DURATION_MS = 520L
        private val EMPHASIZED = PathInterpolator(0.2f, 0f, 0f, 1f)
    }
}
