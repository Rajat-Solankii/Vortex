package com.example.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.TransformOrigin

object ExpressiveSprings {
    const val FastSpatialDampingRatio = 0.6f
    const val FastSpatialStiffness = 800f
    const val DefaultSpatialDampingRatio = 0.84f
    const val DefaultSpatialStiffness = 380f
    const val FastEffectsDampingRatio = 1f
    const val FastEffectsStiffness = 3800f
}

val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

fun vortexEnterTransition(isReducedMotion: Boolean = false): EnterTransition =
    if (isReducedMotion) {
        fadeIn(animationSpec = tween(durationMillis = 150))
    } else {
        slideInVertically(
            animationSpec = spring(
                dampingRatio = ExpressiveSprings.DefaultSpatialDampingRatio,
                stiffness = ExpressiveSprings.DefaultSpatialStiffness
            ),
            initialOffsetY = { (it * 0.08f).toInt() }
        ) + scaleIn(
            animationSpec = spring(
                dampingRatio = ExpressiveSprings.DefaultSpatialDampingRatio,
                stiffness = ExpressiveSprings.DefaultSpatialStiffness
            ),
            initialScale = 0.92f,
            transformOrigin = TransformOrigin.Center
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = 300,
                delayMillis = 85,
                easing = EmphasizedDecelerateEasing
            )
        )
    }

fun vortexExitTransition(isReducedMotion: Boolean = false): ExitTransition =
    if (isReducedMotion) {
        fadeOut(animationSpec = tween(durationMillis = 100))
    } else {
        scaleOut(
            animationSpec = spring(
                dampingRatio = 1f,
                stiffness = ExpressiveSprings.DefaultSpatialStiffness
            ),
            targetScale = 0.92f,
            transformOrigin = TransformOrigin.Center
        ) + fadeOut(
            animationSpec = tween(durationMillis = 160, easing = EmphasizedAccelerateEasing)
        )
    }

fun vortexPopEnterTransition(isReducedMotion: Boolean = false): EnterTransition =
    if (isReducedMotion) {
        fadeIn(animationSpec = tween(durationMillis = 150))
    } else {
        scaleIn(
            animationSpec = spring(
                dampingRatio = ExpressiveSprings.DefaultSpatialDampingRatio,
                stiffness = ExpressiveSprings.DefaultSpatialStiffness
            ),
            initialScale = 0.92f,
            transformOrigin = TransformOrigin.Center
        ) + fadeIn(
            animationSpec = tween(durationMillis = 300, delayMillis = 85, easing = EmphasizedDecelerateEasing)
        )
    }

fun vortexPopExitTransition(isReducedMotion: Boolean = false): ExitTransition =
    if (isReducedMotion) {
        fadeOut(animationSpec = tween(durationMillis = 100))
    } else {
        slideOutVertically(
            animationSpec = spring(
                dampingRatio = 1f,
                stiffness = ExpressiveSprings.DefaultSpatialStiffness
            ),
            targetOffsetY = { (it * 0.08f).toInt() }
        ) + scaleOut(
            animationSpec = spring(
                dampingRatio = 1f,
                stiffness = ExpressiveSprings.DefaultSpatialStiffness
            ),
            targetScale = 0.92f,
            transformOrigin = TransformOrigin.Center
        ) + fadeOut(
            animationSpec = tween(durationMillis = 160, easing = EmphasizedAccelerateEasing)
        )
    }
