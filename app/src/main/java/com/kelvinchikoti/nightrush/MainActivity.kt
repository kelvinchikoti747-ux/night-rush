package com.kelvinchikoti.nightrush

import android.app.Activity
import android.os.Bundle
import android.graphics.*
import android.view.*
import android.content.Context
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation =
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(NightRushGame(this))
    }
}

class NightRushGame(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cars = mutableListOf<Car>()

    private var playerX = 0.5f
    private var roadOffset = 0f
    private var speed = 0.4f
    private var distance = 0f
    private var bestDistance = 0f
    private var nitro = 1f

    private var left = false
    private var right = false
    private var accelerate = false
    private var brake = false
    private var nitroPressed = false

    private var paused = false
    private var gameOver = false
    private var lastTime = System.nanoTime()

    data class Car(
        var x: Float,
        var y: Float,
        var color: Int
    )

    init {
        bestDistance = context
            .getSharedPreferences("night_rush", Context.MODE_PRIVATE)
            .getFloat("best", 0f)

        resetGame()
    }

    private fun resetGame() {
        cars.clear()

        repeat(6) {
            cars.add(
                Car(
                    x = 0.2f + Random.nextFloat() * 0.6f,
                    y = -Random.nextFloat() * 1.5f,
                    color = COLORS.random()
                )
            )
        }

        playerX = 0.5f
        roadOffset = 0f
        speed = 0.4f
        distance = 0f
        nitro = 1f
        paused = false
        gameOver = false
        lastTime = System.nanoTime()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val now = System.nanoTime()
        val delta = min(
            0.05f,
            (now - lastTime) / 1_000_000_000f
        )

        lastTime = now

        if (!paused && !gameOver) {
            updateGame(delta)
        }

        drawGame(canvas)

        postInvalidateOnAnimation()
    }

    private fun updateGame(delta: Float) {
        val targetSpeed = when {
            nitroPressed && nitro > 0f -> 1.0f
            accelerate -> 0.72f
            brake -> 0.15f
            else -> 0.4f
        }

        speed += (targetSpeed - speed) * delta * 4f

        if (nitroPressed && nitro > 0f) {
            nitro = max(0f, nitro - delta * 0.25f)
        } else {
            nitro = min(1f, nitro + delta * 0.04f)
        }

        val direction =
            (if (right) 1f else 0f) -
            (if (left) 1f else 0f)

        playerX += direction * delta * (0.8f + speed)
        playerX = playerX.coerceIn(0.12f, 0.88f)

        roadOffset = (roadOffset + speed * delta) % 1f
        distance += speed * delta * 100f

        for (car in cars) {
            car.y += (speed - 0.1f) * delta * 0.7f

            if (car.y > 1.2f) {
                car.y = -0.2f
                car.x = 0.2f + Random.nextFloat() * 0.6f
                car.color = COLORS.random()
            }

            val hitX = abs(car.x - playerX) < 0.095f
            val hitY = abs(car.y - 0.78f) < 0.12f

            if (hitX && hitY) {
                crash()
            }
        }
    }

    private fun crash() {
        gameOver = true

        if (distance > bestDistance) {
            bestDistance = distance

            context.getSharedPreferences(
                "night_rush",
                Context.MODE_PRIVATE
            )
                .edit()
                .putFloat("best", bestDistance)
                .apply()
        }
    }

    private fun drawGame(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawColor(Color.rgb(8, 8, 25))

        val roadLeft = w * 0.13f
        val roadRight = w * 0.87f

        // City background
        paint.color = Color.rgb(10, 14, 40)
        canvas.drawRect(0f, 0f, w, h, paint)

        // Buildings
        paint.color = Color.rgb(18, 22, 55)

        for (i in 0..8) {
            val buildingWidth = w * 0.08f
            val x = i * buildingWidth

            canvas.drawRect(
                x,
                h * 0.25f,
                x + buildingWidth - 8f,
                h,
                paint
            )
        }

        // Road
        paint.color = Color.rgb(45, 45, 62)
        canvas.drawRect(roadLeft, 0f, roadRight, h, paint)

        // Neon borders
        paint.color = Color.CYAN
        canvas.drawRect(roadLeft - 5f, 0f, roadLeft, h, paint)
        canvas.drawRect(roadRight, 0f, roadRight + 5f, h, paint)

        // Road center lines
        paint.color = Color.WHITE
        paint.strokeWidth = 5f

        for (i in 0..10) {
            val y = ((i / 10f + roadOffset) % 1f) * h

            canvas.drawLine(
                w * 0.5f,
                y,
                w * 0.5f,
                y + h * 0.06f,
                paint
            )
        }

        // Traffic
        for (car in cars) {
            val x = roadLeft + car.x * (roadRight - roadLeft)
            drawCar(canvas, x, car.y * h, car.color)
        }

        // Player
        val playerScreenX =
            roadLeft + playerX * (roadRight - roadLeft)

        drawCar(
            canvas,
            playerScreenX,
            h * 0.78f,
            Color.rgb(255, 30, 130)
        )

        drawHud(canvas)
        drawControls(canvas)

        if (paused) {
            drawOverlay(canvas, "PAUSED", "Tap the pause button to continue")
        }

        if (gameOver) {
            drawOverlay(canvas, "CRASHED!", "Tap anywhere to restart")
        }
    }

    private fun drawCar(
        canvas: Canvas,
        x: Float,
        y: Float,
        color: Int
    ) {
        val carWidth = width * 0.075f
        val carHeight = height * 0.16f

        // Shadow
        paint.color = Color.argb(120, 0, 0, 0)

        canvas.drawRoundRect(
            x - carWidth / 2 + 6f,
            y - carHeight / 2 + 7f,
            x + carWidth / 2 + 6f,
            y + carHeight / 2 + 7f,
            12f,
            12f,
            paint
        )

        // Body
        paint.color = color

        canvas.drawRoundRect(
            x - carWidth / 2,
            y - carHeight / 2,
            x + carWidth / 2,
            y + carHeight / 2,
            12f,
            12f,
            paint
        )

        // Windows
        paint.color = Color.rgb(15, 25, 45)

        canvas.drawRoundRect(
            x - carWidth * 0.32f,
            y - carHeight * 0.33f,
            x + carWidth * 0.32f,
            y - carHeight * 0.02f,
            7f,
            7f,
            paint
        )

        // Headlights
        paint.color = Color.WHITE

        canvas.drawCircle(
            x - carWidth * 0.3f,
            y + carHeight * 0.37f,
            4f,
            paint
        )

        canvas.drawCircle(
            x + carWidth * 0.3f,
            y + carHeight * 0.37f,
            4f,
            paint
        )
    }

    private fun drawHud(canvas: Canvas) {
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.WHITE

        paint.textSize = height * 0.045f
        canvas.drawText("NIGHT RUSH", 28f, 45f, paint)

        paint.textSize = height * 0.03f

        canvas.drawText(
            "DISTANCE: ${distance.toInt()}m",
            30f,
            80f,
            paint
        )

        canvas.drawText(
            "BEST: ${bestDistance.toInt()}m",
            30f,
            110f,
            paint
        )

        // Nitro bar background
        paint.color = Color.DKGRAY

        canvas.drawRoundRect(
            width - 200f,
            28f,
            width - 30f,
            48f,
            10f,
            10f,
            paint
        )

        // Nitro bar
        paint.color = Color.CYAN

        canvas.drawRoundRect(
            width - 200f,
            28f,
            width - 200f + 170f * nitro,
            48f,
            10f,
            10f,
            paint
        )

        paint.color = Color.WHITE
        paint.textSize = 18f

        canvas.drawText(
            "NITRO",
            width - 192f,
            45f,
            paint
        )

        paint.textSize = 32f

        canvas.drawText(
            "Ⅱ",
            width - 65f,
            85f,
            paint
        )
    }

    private fun drawControls(canvas: Canvas) {
        val y = height * 0.82f
        val radius = height * 0.085f

        drawButton(canvas, width * 0.10f, y, radius, "◀", left)
        drawButton(canvas, width * 0.23f, y, radius, "▶", right)
        drawButton(canvas, width * 0.77f, y, radius, "GO", accelerate)
        drawButton(canvas, width * 0.90f, y, radius, "BRK", brake)

        drawButton(
            canvas,
            width * 0.90f,
            height * 0.61f,
            radius * 0.72f,
            "N2O",
            nitroPressed
        )
    }

    private fun drawButton(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        text: String,
        active: Boolean
    ) {
        paint.color =
            if (active) {
                Color.argb(235, 0, 220, 255)
            } else {
                Color.argb(180, 25, 28, 55)
            }

        canvas.drawCircle(x, y, radius, paint)

        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = radius * 0.42f

        canvas.drawText(
            text,
            x,
            y + paint.textSize / 3f,
            paint
        )

        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawOverlay(
        canvas: Canvas,
        title: String,
        subtitle: String
    ) {
        paint.color = Color.argb(210, 0, 0, 20)

        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            paint
        )

        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 48f

        canvas.drawText(
            title,
            width / 2f,
            height / 2f,
            paint
        )

        paint.textSize = 20f

        canvas.drawText(
            subtitle,
            width / 2f,
            height / 2f + 42f,
            paint
        )

        paint.textAlign = Paint.Align.LEFT
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                val x = event.x
                val y = event.y

                left = x < width * 0.17f

                right =
                    x >= width * 0.17f &&
                    x < width * 0.34f

                accelerate =
                    x > width * 0.68f &&
                    y > height * 0.70f

                brake =
                    x > width * 0.83f &&
                    y > height * 0.70f

                nitroPressed =
                    x > width * 0.82f &&
                    y > height * 0.50f &&
                    y < height * 0.72f
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                left = false
                right = false
                accelerate = false
                brake = false
                nitroPressed = false

                if (gameOver) {
                    resetGame()
                }

                if (
                    event.x > width - 110f &&
                    event.y < 130f
                ) {
                    paused = !paused
                }
            }
        }

        return true
    }

    companion object {
        private val COLORS = listOf(
            Color.rgb(255, 193, 7),
            Color.rgb(76, 175, 80),
            Color.rgb(156, 39, 176),
            Color.rgb(3, 169, 244),
            Color.rgb(255, 87, 34)
        )
    }
}
