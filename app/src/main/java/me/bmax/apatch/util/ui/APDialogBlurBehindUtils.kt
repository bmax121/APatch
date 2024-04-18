package me.bmax.apatch.util.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.view.SurfaceControl
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import java.lang.reflect.Method

open class APDialogBlurBehindUtils {
    companion object {
        private val bIsBlurSupport =
            getSystemProperty("ro.surface_flinger.supports_background_blur") && !getSystemProperty("persist.sys.sf.disable_blurs")

        private fun getSystemProperty(key: String?): Boolean {
            var value = false
            try {
                val c = Class.forName("android.os.SystemProperties")
                val get = c.getMethod(
                    "getBoolean", String::class.java, Boolean::class.javaPrimitiveType
                )
                value = get.invoke(c, key, false) as Boolean
            } catch (e: Exception) {
                Log.e("APatchUI", "[APDialogBlurBehindUtils] Failed to getSystemProperty: ", e)
            }
            return value
        }

        private fun updateWindowForBlurs(window: Window, blursEnabled: Boolean) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.setDimAmount(0.27f)
                window.attributes.blurBehindRadius = 20
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
                if (blursEnabled) {
                    val view = window.decorView
                    val animator = ValueAnimator.ofInt(1, 53)
                    animator.duration = 667
                    animator.interpolator = DecelerateInterpolator()
                    try {
                        val viewRootImpl =
                            view.javaClass.getMethod("getViewRootImpl").invoke(view) ?: return
                        val surfaceControl = viewRootImpl.javaClass.getMethod("getSurfaceControl")
                            .invoke(viewRootImpl) as SurfaceControl
                        @SuppressLint("BlockedPrivateApi") val setBackgroundBlurRadius: Method =
                            SurfaceControl.Transaction::class.java.getDeclaredMethod(
                                "setBackgroundBlurRadius",
                                SurfaceControl::class.java,
                                Int::class.javaPrimitiveType
                            )
                        animator.addUpdateListener { animation: ValueAnimator ->
                            try {
                                val transaction = SurfaceControl.Transaction()
                                val animatedValue = animation.animatedValue
                                if (animatedValue != null) {
                                    setBackgroundBlurRadius.invoke(
                                        transaction, surfaceControl, animatedValue as Int
                                    )
                                }
                                transaction.apply()
                            } catch (t: Throwable) {
                                Log.e(
                                    "APatchUI",
                                    "[APDialogBlurBehindUtils] Blur behind dialog builder: " + t.toString()
                                )
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e(
                            "APatchUI",
                            "[APDialogBlurBehindUtils] Blur behind dialog builder: " + t.toString()
                        )
                    }
                    view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {}
                        override fun onViewDetachedFromWindow(v: View) {
                            animator.cancel()
                        }
                    })
                    animator.start()
                }
            }
        }

        fun setupWindowBlurListener(window: Window) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                )
                updateWindowForBlurs(window, true)
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
                updateWindowForBlurs(
                    window, bIsBlurSupport
                )
            }
        }

    }

}