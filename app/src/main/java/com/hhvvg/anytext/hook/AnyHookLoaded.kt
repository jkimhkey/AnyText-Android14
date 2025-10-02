package com.hhvvg.anytext.hook

import android.app.Activity
import android.app.AndroidAppHelper
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import com.hhvvg.anytext.utils.APP_HIGHLIGHT_FIELD_NAME
import com.hhvvg.anytext.utils.Android14Compat
import com.hhvvg.anytext.utils.DEFAULT_SHARED_PREFERENCES_FILE_NAME
import com.hhvvg.anytext.utils.KEY_SHOW_TEXT_BORDER
import com.hhvvg.anytext.utils.PACKAGE_NAME
import com.hhvvg.anytext.utils.appPropertyInject
import com.hhvvg.anytext.utils.getAppInjectedProperty
import com.hhvvg.anytext.utils.hookViewListener
import com.hhvvg.anytext.wrapper.IGNORE_HOOK
import com.hhvvg.anytext.wrapper.TextViewOnClickWrapper
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Class for package hook.
 * @author hhvvg
 */
class AnyHookLoaded : IXposedHookLoadPackage {
    override fun handleLoadPackage(p0: XC_LoadPackage.LoadPackageParam?) {
        if (p0 == null) {
            return
        }

        // Don't hook itself
        val packageName = p0.packageName
        if (packageName == PACKAGE_NAME) {
            return
        }

        Android14Compat.log("开始处理包: $packageName")

        try {
            // Hook onCreate method in Application
            val appClazz: Class<Application> = Application::class.java
            val methodHook = ApplicationOnCreateMethodHook()
            val method = XposedHelpers.findMethodBestMatch(appClazz, "onCreate", arrayOf(), arrayOf())
            XposedBridge.hookMethod(method, methodHook)

            // Enhanced hook for Android 14 - hook multiple view types
            hookEnhancedViewMethods(p0)

            Android14Compat.log("包 $packageName Hook 完成")

        } catch (e: Exception) {
            Android14Compat.log("处理包 $packageName 时出错: ${e.message}")
        }
    }

    /**
     * Enhanced hook method for Android 14 compatibility
     */
    private fun hookEnhancedViewMethods(lpparam: XC_LoadPackage.LoadPackageParam) {
        val hookTargets = Android14Compat.getEnhancedHookTargets()
        
        // Hook setOnClickListener for all target views
        val clickMethodHook = TextViewOnClickMethodHook()
        hookTargets.forEach { className ->
            try {
                XposedHelpers.findAndHookMethod(
                    className,
                    lpparam.classLoader,
                    "setOnClickListener",
                    View.OnClickListener::class.java,
                    clickMethodHook
                )
                Android14Compat.log("成功 Hook: $className.setOnClickListener")
            } catch (e: Throwable) {
                // Class or method not found, skip
            }
        }

        // Hook setText for all target views
        val setTextHook = TextViewSetTextMethodHook()
        hookTargets.forEach { className ->
            try {
                // Hook setText with 2 arguments
                val setTextMethod2Arg = XposedHelpers.findMethodBestMatch(
                    lpparam.classLoader.loadClass(className),
                    "setText",
                    CharSequence::class.java,
                    TextView.BufferType::class.java
                )
                XposedBridge.hookMethod(setTextMethod2Arg, setTextHook)
                
                // Hook setText with 1 argument
                val setTextMethod1Arg = XposedHelpers.findMethodBestMatch(
                    lpparam.classLoader.loadClass(className),
                    "setText",
                    CharSequence::class.java
                )
                XposedBridge.hookMethod(setTextMethod1Arg, setTextHook)
                
                Android14Compat.log("成功 Hook: $className.setText")
            } catch (e: Throwable) {
                // Class or method not found, skip
            }
        }
    }

    private class TextViewSetTextMethodHook : XC_MethodHook() {

        override fun beforeHookedMethod(param: MethodHookParam?) {
            if (param == null) {
                return
            }
            val textView = param.thisObject
            if (textView !is TextView || textView.tag == IGNORE_HOOK) {
                return
            }
            val textArg = (param.args[0] ?: return).toString()
            val showHighlight = getAppInjectedProperty<Boolean>(
                textView.context.applicationContext as Application,
                APP_HIGHLIGHT_FIELD_NAME
            )
            if (showHighlight) {
                val spanString = SpannableString(textArg)
                spanString.setSpan(
                    BackgroundColorSpan(Color.RED),
                    0,
                    textArg.length,
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE
                )
                param.args[0] = spanString
            }
        }
    }

    private class TextViewOnClickMethodHook : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam?) {
            if (param == null) {
                return
            }
            val view = param.thisObject
            if (view is TextView) {
                view.isClickable = true
                hookViewListener(view) { originListener ->
                    if (originListener is TextViewOnClickWrapper) {
                        originListener
                    } else {
                        TextViewOnClickWrapper(originListener, view)
                    }
                }
            }
        }
    }

    private class ApplicationOnCreateMethodHook : XC_MethodHook() {

        override fun afterHookedMethod(param: MethodHookParam?) {
            if (param == null) {
                return
            }
            val app = AndroidAppHelper.currentApplication()
            val appName = AndroidAppHelper.currentApplication()::class.java.name
            val packageName = AndroidAppHelper.currentApplicationInfo().packageName
            val sp = app.applicationContext.getSharedPreferences(
                DEFAULT_SHARED_PREFERENCES_FILE_NAME, Context.MODE_PRIVATE
            )
            // Update application class name
            updateApplicationClassName(sp, packageName, appName)
            hookLifecycleCallback(app, ActivityCallback())

            // Add global highlight property
            appPropertyInject(app, APP_HIGHLIGHT_FIELD_NAME, sp.getBoolean(KEY_SHOW_TEXT_BORDER, false))
            
            Android14Compat.log("应用 $packageName 初始化完成")
        }

        private fun hookLifecycleCallback(
            app: Application,
            callback: Application.ActivityLifecycleCallbacks
        ) {
            try {
                val clazz = app::class.java
                val callbackField = XposedHelpers.findField(clazz, "mActivityLifecycleCallbacks")
                val callbackArray =
                    callbackField.get(app) as ArrayList<Application.ActivityLifecycleCallbacks>
                callbackArray.add(callback)
            } catch (e: Exception) {
                Android14Compat.log("Hook 生命周期回调失败: ${e.message}")
            }
        }

        private fun updateApplicationClassName(
            sp: SharedPreferences,
            packageNameAsKey: String,
            name: String
        ) {
            val edit = sp.edit()
            edit.putString(packageNameAsKey, name)
            edit.apply()
        }
    }

    private class ActivityCallback : Application.ActivityLifecycleCallbacks {

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        }

        override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
            try {
                val contentView = activity.window.decorView as ViewGroup
                dfsHookTextView(contentView)
                contentView.viewTreeObserver.addOnGlobalLayoutListener {
                    dfsHookTextView(contentView)
                }
                Android14Compat.log("Activity ${activity::class.java.simpleName} 创建完成")
            } catch (e: Exception) {
                Android14Compat.log("Hook Activity 失败: ${e.message}")
            }
        }

        private fun dfsHookTextView(viewGroup: ViewGroup) {
            try {
                val children = viewGroup.children
                for (child in children) {
                    if (child is ViewGroup) {
                        dfsHookTextView(child)
                        continue
                    }
                    if (child is TextView) {
                        child.isClickable = true
                        hookViewListener(child) { originListener ->
                            if (originListener is TextViewOnClickWrapper) {
                                originListener
                            } else {
                                TextViewOnClickWrapper(originListener, child)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore errors during view traversal
            }
        }

        override fun onActivityStarted(activity: Activity) {
        }

        override fun onActivityResumed(activity: Activity) {
        }

        override fun onActivityPaused(activity: Activity) {
        }

        override fun onActivityStopped(activity: Activity) {
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        }

        override fun onActivityDestroyed(activity: Activity) {
        }
    }
}