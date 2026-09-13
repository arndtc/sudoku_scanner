package com.example.model

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Represents a quadrilateral defined by four normalized corner points in image space [0.0 .. 1.0].
 * Order of vertices:
 * - topLeft (index 0)
 * - topRight (index 1)
 * - bottomRight (index 2)
 * - bottomLeft (index 3)
 */
data class PerspectiveQuad(
    val topLeft: PointF = PointF(0.08f, 0.08f),
    val topRight: PointF = PointF(0.92f, 0.08f),
    val bottomRight: PointF = PointF(0.92f, 0.92f),
    val bottomLeft: PointF = PointF(0.08f, 0.92f)
) {

    fun toBoundingRect(): RectF {
        val minX = min(min(topLeft.x, topRight.x), min(bottomLeft.x, bottomRight.x))
        val maxX = max(max(topLeft.x, topRight.x), max(bottomLeft.x, bottomRight.x))
        val minY = min(min(topLeft.y, topRight.y), min(bottomLeft.y, bottomRight.y))
        val maxY = max(max(topLeft.y, topRight.y), max(bottomLeft.y, bottomRight.y))
        return RectF(minX, minY, maxX, maxY)
    }

    /**
     * Clamps all 4 points within normalized image coordinates [0f, 1f].
     */
    fun clamped(): PerspectiveQuad {
        return PerspectiveQuad(
            topLeft = PointF(topLeft.x.coerceIn(0f, 1f), topLeft.y.coerceIn(0f, 1f)),
            topRight = PointF(topRight.x.coerceIn(0f, 1f), topRight.y.coerceIn(0f, 1f)),
            bottomRight = PointF(bottomRight.x.coerceIn(0f, 1f), bottomRight.y.coerceIn(0f, 1f)),
            bottomLeft = PointF(bottomLeft.x.coerceIn(0f, 1f), bottomLeft.y.coerceIn(0f, 1f))
        )
    }

    /**
     * Moves a single corner handle by index:
     * 0 -> topLeft, 1 -> topRight, 2 -> bottomRight, 3 -> bottomLeft.
     */
    fun withCorner(cornerIndex: Int, newPoint: PointF): PerspectiveQuad {
        val clampedPoint = PointF(newPoint.x.coerceIn(0f, 1f), newPoint.y.coerceIn(0f, 1f))
        return when (cornerIndex) {
            0 -> copy(topLeft = clampedPoint)
            1 -> copy(topRight = clampedPoint)
            2 -> copy(bottomRight = clampedPoint)
            3 -> copy(bottomLeft = clampedPoint)
            else -> this
        }
    }

    /**
     * Shifts the entire quadrilateral by (dx, dy) keeping its shape and orientation.
     */
    fun nudge(dx: Float, dy: Float): PerspectiveQuad {
        val minX = min(min(topLeft.x, topRight.x), min(bottomLeft.x, bottomRight.x))
        val maxX = max(max(topLeft.x, topRight.x), max(bottomLeft.x, bottomRight.x))
        val minY = min(min(topLeft.y, topRight.y), min(bottomLeft.y, bottomRight.y))
        val maxY = max(max(topLeft.y, topRight.y), max(bottomLeft.y, bottomRight.y))

        val safeDx = when {
            minX + dx < 0f -> -minX
            maxX + dx > 1f -> 1f - maxX
            else -> dx
        }
        val safeDy = when {
            minY + dy < 0f -> -minY
            maxY + dy > 1f -> 1f - maxY
            else -> dy
        }

        return PerspectiveQuad(
            topLeft = PointF(topLeft.x + safeDx, topLeft.y + safeDy),
            topRight = PointF(topRight.x + safeDx, topRight.y + safeDy),
            bottomRight = PointF(bottomRight.x + safeDx, bottomRight.y + safeDy),
            bottomLeft = PointF(bottomLeft.x + safeDx, bottomLeft.y + safeDy)
        )
    }

    /**
     * Scales the quad around its center centroid.
     */
    fun scale(factor: Float): PerspectiveQuad {
        val cx = (topLeft.x + topRight.x + bottomRight.x + bottomLeft.x) / 4f
        val cy = (topLeft.y + topRight.y + bottomRight.y + bottomLeft.y) / 4f

        fun scalePoint(p: PointF): PointF {
            val nx = (cx + (p.x - cx) * (1f + factor)).coerceIn(0f, 1f)
            val ny = (cy + (p.y - cy) * (1f + factor)).coerceIn(0f, 1f)
            return PointF(nx, ny)
        }

        return PerspectiveQuad(
            topLeft = scalePoint(topLeft),
            topRight = scalePoint(topRight),
            bottomRight = scalePoint(bottomRight),
            bottomLeft = scalePoint(bottomLeft)
        )
    }

    /**
     * Interpolates point on top edge at fraction v in [0, 1].
     */
    fun topPoint(v: Float): PointF = PointF(
        topLeft.x + (topRight.x - topLeft.x) * v,
        topLeft.y + (topRight.y - topLeft.y) * v
    )

    /**
     * Interpolates point on bottom edge at fraction v in [0, 1].
     */
    fun bottomPoint(v: Float): PointF = PointF(
        bottomLeft.x + (bottomRight.x - bottomLeft.x) * v,
        bottomLeft.y + (bottomRight.y - bottomLeft.y) * v
    )

    /**
     * Interpolates point on left edge at fraction u in [0, 1].
     */
    fun leftPoint(u: Float): PointF = PointF(
        topLeft.x + (bottomLeft.x - topLeft.x) * u,
        topLeft.y + (bottomLeft.y - topLeft.y) * u
    )

    /**
     * Interpolates point on right edge at fraction u in [0, 1].
     */
    fun rightPoint(u: Float): PointF = PointF(
        topRight.x + (bottomRight.x - topRight.x) * u,
        topRight.y + (bottomRight.y - topRight.y) * u
    )

    /**
     * Bilinear surface interpolation inside the quad for coordinate (u, v) where u, v in [0, 1].
     */
    fun interiorPoint(u: Float, v: Float): PointF {
        val x = (1f - u) * (1f - v) * topLeft.x +
                u * (1f - v) * topRight.x +
                u * v * bottomRight.x +
                (1f - u) * v * bottomLeft.x
        val y = (1f - u) * (1f - v) * topLeft.y +
                u * (1f - v) * topRight.y +
                u * v * bottomRight.y +
                (1f - u) * v * bottomLeft.y
        return PointF(x, y)
    }

    companion object {
        fun fromRect(rect: RectF): PerspectiveQuad {
            return PerspectiveQuad(
                topLeft = PointF(rect.left, rect.top),
                topRight = PointF(rect.right, rect.top),
                bottomRight = PointF(rect.right, rect.bottom),
                bottomLeft = PointF(rect.left, rect.bottom)
            )
        }

        fun defaultQuad(padding: Float = 0.08f): PerspectiveQuad {
            return PerspectiveQuad(
                topLeft = PointF(padding, padding),
                topRight = PointF(1f - padding, padding),
                bottomRight = PointF(1f - padding, 1f - padding),
                bottomLeft = PointF(padding, 1f - padding)
            )
        }

        fun fullImage(): PerspectiveQuad {
            return PerspectiveQuad(
                topLeft = PointF(0f, 0f),
                topRight = PointF(1f, 0f),
                bottomRight = PointF(1f, 1f),
                bottomLeft = PointF(0f, 1f)
            )
        }
    }
}
