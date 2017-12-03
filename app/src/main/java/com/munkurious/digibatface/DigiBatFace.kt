package com.munkurious.digibatface

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v4.content.ContextCompat
import android.support.v7.graphics.Palette
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.SurfaceHolder


import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.TimeZone
import android.graphics.Typeface
import android.util.Log
import android.widget.TextView



/**
 * Updates rate in milliseconds for interactive mode. We update once a second to advance the
 * second hand.
 */
private const val INTERACTIVE_UPDATE_RATE_MS = 1000

/**
 * Handler message id for updating the time periodically in interactive mode.
 */
private const val MSG_UPDATE_TIME = 0

private const val HOUR_STROKE_WIDTH = 5f
private const val MINUTE_STROKE_WIDTH = 3f
private const val SECOND_TICK_STROKE_WIDTH = 2f

private const val CENTER_GAP_AND_CIRCLE_RADIUS = 4f

private const val SHADOW_RADIUS = 6f

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 *
 *
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
class DigiBatFace : CanvasWatchFaceService()
{

	override fun onCreateEngine(): Engine
	{
		return Engine()
	}

	private class EngineHandler(reference: DigiBatFace.Engine) : Handler()
	{
		private val mWeakReference: WeakReference<DigiBatFace.Engine> = WeakReference(reference)

		override fun handleMessage(msg: Message)
		{
			val engine = mWeakReference.get()
			if (engine != null)
			{
				when (msg.what)
				{
					MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
				}
			}
		}
	}

	inner class Engine : CanvasWatchFaceService.Engine()
	{

		private lateinit var mCalendar: Calendar

		private var mRegisteredTimeZoneReceiver = false
		private var mMuteMode: Boolean = false
		private var mCenterX: Float = 0F
		private var mCenterY: Float = 0F

		private lateinit var mBackgroundPaint: Paint
		private lateinit var mTextPaint: Paint
		private lateinit var mTextPaint2: Paint
		private lateinit var mTickAndCirclePaintBattery: Paint
		private var mWatchHandShadowColor: Int = 0


		private var mAmbient: Boolean = false
		private var mLowBitAmbient: Boolean = false
		private var mBurnInProtection: Boolean = false

		/* Handler to update the time once a second in interactive mode. */
		private val mUpdateTimeHandler = EngineHandler(this)

		private val mTimeZoneReceiver = object : BroadcastReceiver()
		{
			override fun onReceive(context: Context, intent: Intent)
			{
				mCalendar.timeZone = TimeZone.getDefault()
				invalidate()
			}
		}

		override fun onCreate(holder: SurfaceHolder)
		{
			super.onCreate(holder)

			setWatchFaceStyle(WatchFaceStyle.Builder(this@DigiBatFace)
					.build())

			mCalendar = Calendar.getInstance()


			initializeWatchFace()
		}

		private fun initializeWatchFace()
		{
			// Initializes background.
			mBackgroundPaint = Paint().apply {
				color = ContextCompat.getColor(applicationContext, R.color.black)
			}

			mWatchHandShadowColor = Color.BLACK

			//create the font i want
			val font = Typeface.createFromAsset(assets, "fonts/TitilliumWeb-Light.ttf")

			// Initializes Watch Face.
			mTextPaint = Paint().apply {
				typeface = font
				isAntiAlias = true
				color = ContextCompat.getColor(applicationContext, R.color.white)
				textAlign = Paint.Align.CENTER
				textSize = 145F
			}

			mTextPaint2 = Paint().apply {
				typeface = font
				isAntiAlias = true
				color = ContextCompat.getColor(applicationContext, R.color.green)
				textAlign = Paint.Align.CENTER
				textSize = 145F
			}

			mTickAndCirclePaintBattery = Paint().apply {
				color = Color.GREEN
				strokeWidth = SECOND_TICK_STROKE_WIDTH
				isAntiAlias = true
				style = Paint.Style.STROKE
				setShadowLayer(
						SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor)
			}
		}

		override fun onDestroy()
		{
			mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
			super.onDestroy()
		}

		override fun onPropertiesChanged(properties: Bundle)
		{
			super.onPropertiesChanged(properties)
			mLowBitAmbient = properties.getBoolean(
					WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
			mBurnInProtection = properties.getBoolean(
					WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false)
		}

		override fun onTimeTick()
		{
			super.onTimeTick()
			invalidate()
		}

		override fun onAmbientModeChanged(inAmbientMode: Boolean)
		{
			super.onAmbientModeChanged(inAmbientMode)
			mAmbient = inAmbientMode

			updateWatchHandStyle()

			// Check and trigger whether or not timer should be running (only
			// in active mode).
			updateTimer()
		}

		private fun updateWatchHandStyle()
		{
			if (mAmbient)
			{
				mTextPaint.color = Color.WHITE
				mTextPaint2.color = Color.WHITE
				mTickAndCirclePaintBattery.color = Color.BLACK

				mTextPaint.isAntiAlias = false
				mTextPaint2.isAntiAlias = false
				mTickAndCirclePaintBattery.isAntiAlias = false

			} else
			{
				mTextPaint.color = Color.GREEN
				mTextPaint2.color = Color.WHITE
				mTickAndCirclePaintBattery.color = Color.GREEN

				mTextPaint.isAntiAlias = true
				mTextPaint2.isAntiAlias = true
				mTickAndCirclePaintBattery.isAntiAlias = true

			}
		}

		override fun onInterruptionFilterChanged(interruptionFilter: Int)
		{
			super.onInterruptionFilterChanged(interruptionFilter)
			val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE

			/* Dim display in mute mode. */
			if (mMuteMode != inMuteMode)
			{
				mMuteMode = inMuteMode
				mTextPaint.alpha = if (inMuteMode) 100 else 255
				mTextPaint2.alpha = if (inMuteMode) 100 else 255
				invalidate()
			}
		}

		override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int)
		{
			super.onSurfaceChanged(holder, format, width, height)

			/*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
			mCenterX = width / 2f
			mCenterY = height / 2f

		}


		override fun onDraw(canvas: Canvas, bounds: Rect)
		{
			val now = System.currentTimeMillis()
			mCalendar.timeInMillis = now
			drawWatchFace(canvas)
		}


		private fun drawWatchFace(canvas: Canvas)
		{
			// background
			canvas.drawColor(Color.BLACK)

			// text
			val text = String.format("%02d", mCalendar.get(Calendar.HOUR))
			canvas.drawText(text, mCenterX, mCenterY - 20, mTextPaint2)


			val tex2 = String.format("%02d", mCalendar.get(Calendar.MINUTE))
			canvas.drawText(tex2, mCenterX, (mCenterY - 40) + mTextPaint.textSize, mTextPaint)

			/*
             * Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo.
             */
			if (!mAmbient)
			{
				val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
				val batteryStatus = registerReceiver(null, iFilter)
				val batt = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)

				var innerTickRadius = mCenterX - 10
				val outerTickRadius = mCenterX
				for (tickIndex in 0..127)
				{
					if (((tickIndex.toDouble() / 127) * 100) <= batt) // put code for checking percentage
					{
					val tickRot = (tickIndex.toDouble() * Math.PI * 2.0 / 128).toFloat()
					val innerX = Math.sin(tickRot.toDouble()).toFloat() * innerTickRadius
					val innerY = (-Math.cos(tickRot.toDouble())).toFloat() * innerTickRadius
					val outerX = Math.sin(tickRot.toDouble()).toFloat() * outerTickRadius
					val outerY = (-Math.cos(tickRot.toDouble())).toFloat() * outerTickRadius

						canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
								mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaintBattery)
					}
				}
			}


		}

		override fun onVisibilityChanged(visible: Boolean)
		{
			super.onVisibilityChanged(visible)

			if (visible)
			{
				registerReceiver()
				/* Update time zone in case it changed while we weren't visible. */
				mCalendar.timeZone = TimeZone.getDefault()
				invalidate()
			} else
			{
				unregisterReceiver()
			}

			/* Check and trigger whether or not timer should be running (only in active mode). */
			updateTimer()
		}

		private fun registerReceiver()
		{
			if (mRegisteredTimeZoneReceiver)
			{
				return
			}
			mRegisteredTimeZoneReceiver = true
			val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
			this@DigiBatFace.registerReceiver(mTimeZoneReceiver, filter)
		}

		private fun unregisterReceiver()
		{
			if (!mRegisteredTimeZoneReceiver)
			{
				return
			}
			mRegisteredTimeZoneReceiver = false
			this@DigiBatFace.unregisterReceiver(mTimeZoneReceiver)
		}

		/**
		 * Starts/stops the [.mUpdateTimeHandler] timer based on the state of the watch face.
		 */
		private fun updateTimer()
		{
			mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
			if (shouldTimerBeRunning())
			{
				mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
			}
		}

		/**
		 * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer
		 * should only run in active mode.
		 */
		private fun shouldTimerBeRunning(): Boolean
		{
			return isVisible && !mAmbient
		}

		/**
		 * Handle updating the time periodically in interactive mode.
		 */
		fun handleUpdateTimeMessage()
		{
			invalidate()
			if (shouldTimerBeRunning())
			{
				val timeMs = System.currentTimeMillis()
				val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
				mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
			}
		}
	}
}


