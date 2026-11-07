package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.hapticClick
import com.example.R
import com.example.ui.theme.SlateTheme
import kotlinx.coroutines.launch

@Composable
fun SlateSplashScreen(
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val accent = MaterialTheme.colorScheme.primary

  val onAccent = MaterialTheme.colorScheme.onPrimary
  val bg = MaterialTheme.colorScheme.background
  val wave1Color = SlateTheme.colors.surfaceVariantFallbackOrSurface()
  val wave2Color = SlateTheme.colors.elevatedSurface

  Surface(
    modifier = modifier
      .fillMaxSize()
      .clickable(onClick = hapticClick(onDismiss)),
    color = bg
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      // Subtle matte hardware silhouette waves
      Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val wave1 = Path().apply {
          moveTo(0f, h * 0.78f)
          cubicTo(w * 0.30f, h * 0.68f, w * 0.65f, h * 0.84f, w, h * 0.72f)
          lineTo(w, h)
          lineTo(0f, h)
          close()
        }
        drawPath(wave1, color = wave1Color)

        val wave2 = Path().apply {
          moveTo(0f, h * 0.86f)
          cubicTo(w * 0.38f, h * 0.94f, w * 0.70f, h * 0.75f, w, h * 0.82f)
          lineTo(w, h)
          lineTo(0f, h)
          close()
        }
        drawPath(wave2, color = wave2Color)
      }

      Column(
        modifier = Modifier
          .align(Alignment.Center)
          .offset(y = (-36).dp)
          .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Surface(
          modifier = Modifier.size(92.dp),
          shape = RoundedCornerShape(24.dp),
          color = accent,
          shadowElevation = 6.dp
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawArc(
              color = onAccent,
              startAngle = 215f,
              sweepAngle = 110f,
              useCenter = false,
              topLeft = Offset(w * 0.32f, h * 0.18f),
              size = Size(w * 0.36f, h * 0.32f),
              style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
              color = onAccent,
              startAngle = 220f,
              sweepAngle = 100f,
              useCenter = false,
              topLeft = Offset(w * 0.39f, h * 0.25f),
              size = Size(w * 0.22f, h * 0.20f),
              style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
            )
            drawRoundRect(
              color = onAccent,
              topLeft = Offset(w * 0.25f, h * 0.38f),
              size = Size(w * 0.50f, h * 0.32f),
              cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
              style = Stroke(width = 2.5.dp.toPx())
            )
            drawLine(
              color = onAccent,
              start = Offset(w * 0.18f, h * 0.74f),
              end = Offset(w * 0.82f, h * 0.74f),
              strokeWidth = 3.dp.toPx(),
              cap = StrokeCap.Round
            )
          }
        }

        Spacer(Modifier.height(22.dp))

        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))

        Text(
          text = stringResource(R.string.tagline),
          style = MaterialTheme.typography.bodyLarge,
          color = SlateTheme.colors.textSecondary,
          textAlign = TextAlign.Center
        )
      }
    }
  }
}

private data class OnboardingPageModel(
  val title: String,
  val subtitle: String,
  val kind: Int
)

@Composable
fun OnboardingScreen(
  onFinishOnboarding: () -> Unit,
  modifier: Modifier = Modifier
) {
  var showPager by remember { mutableStateOf(false) }

  Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 24.dp, vertical = 14.dp),
      contentAlignment = Alignment.Center
    ) {
      if (!showPager) {
        WelcomeStage(
          onGetStarted = { showPager = true },
          onSkip = onFinishOnboarding,
          modifier = Modifier.widthIn(max = 440.dp)
        )
      } else {
        FeaturePagesStage(
          onBackToWelcome = { showPager = false },
          onFinish = onFinishOnboarding,
          modifier = Modifier.widthIn(max = 440.dp)
        )
      }
    }
  }
}

@Composable
private fun WelcomeStage(
  onGetStarted: () -> Unit,
  onSkip: () -> Unit,
  modifier: Modifier = Modifier
) {
  val accent = MaterialTheme.colorScheme.primary

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val compact = maxHeight < 620.dp
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        TextButton(
          onClick = hapticClick(onSkip),
          modifier = Modifier.testTag("skip_onboarding_button")
        ) {
          Text(
            text = "Skip",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground
          )
        }
      }

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 22.dp)
      ) {
        // Welcome illustration: Monitor + Plant + Phone with Touch gesture
        WelcomeIllustration(compact = compact)

        Text(
          text = "Control your devices\nfrom your phone",
          style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center
        )

        Text(
          text = stringResource(R.string.onboarding_subtitle),
          style = MaterialTheme.typography.bodyLarge,
          color = SlateTheme.colors.textSecondary,
          textAlign = TextAlign.Center
        )
      }

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        OnboardingDots(total = 3, current = 0)

        Button(
          onClick = hapticClick(onGetStarted),
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("get_started_button"),
          shape = RoundedCornerShape(50),
          colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = MaterialTheme.colorScheme.onPrimary
          )
        ) {
          Text(
            text = stringResource(R.string.get_started),
            style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onPrimary)
          )
          Spacer(Modifier.width(8.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun FeaturePagesStage(
  onBackToWelcome: () -> Unit,
  onFinish: () -> Unit,
  modifier: Modifier = Modifier
) {
  val pages = remember {
    listOf(
      OnboardingPageModel(
        title = "Touchpad & Keyboard",
        subtitle = "Move, click, scroll and type\nwith ease.",
        kind = 0
      ),
      OnboardingPageModel(
        title = "Multiple Connections",
        subtitle = "Connect via Bluetooth or\nWi-Fi (with desktop receiver).",
        kind = 1
      ),
      OnboardingPageModel(
        title = "TV & Media Control",
        subtitle = "Control your Smart TV, manage\nmedia playback and\nrun presentations.",
        kind = 2
      )
    )
  }

  val pagerState = rememberPagerState(pageCount = { pages.size })
  val scope = rememberCoroutineScope()
  val accent = MaterialTheme.colorScheme.primary

  Column(
    modifier = modifier.fillMaxSize(),
    verticalArrangement = Arrangement.SpaceBetween,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.Start,
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(
        onClick = hapticClick {
          if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
          } else {
            onBackToWelcome()
          }
        }
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
          contentDescription = "Back",
          tint = MaterialTheme.colorScheme.onBackground
        )
      }
    }

    HorizontalPager(
      state = pagerState,
      modifier = Modifier.weight(1f)
    ) { idx ->
      val page = pages[idx]
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        when (page.kind) {
          0 -> Feature1Illustration()
          1 -> Feature2Illustration()
          else -> Feature3Illustration()
        }

        Spacer(Modifier.height(28.dp))

        Text(
          text = page.title,
          style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))

        Text(
          text = page.subtitle,
          style = MaterialTheme.typography.bodyLarge,
          color = SlateTheme.colors.textSecondary,
          textAlign = TextAlign.Center
        )
      }
    }

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(18.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      OnboardingDots(total = pages.size, current = pagerState.currentPage)

      val isLast = pagerState.currentPage == pages.lastIndex
      Button(
        onClick = hapticClick {
          if (!isLast) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
          } else {
            onFinish()
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("onboarding_continue_button"),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
          containerColor = accent,
          contentColor = MaterialTheme.colorScheme.onPrimary
        )
      ) {
        Text(
          text = if (isLast) "Get Started" else "Next",
          style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onPrimary)
        )
        Spacer(Modifier.width(8.dp))
        Icon(
          imageVector = if (isLast) Icons.Outlined.Check else Icons.AutoMirrored.Outlined.ArrowForward,
          contentDescription = null,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

@Composable
private fun OnboardingDots(total: Int, current: Int) {
  val accent = MaterialTheme.colorScheme.primary
  val inactiveDot = SlateTheme.colors.border
  Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    repeat(total) { idx ->
      val active = idx == current
      Box(
        modifier = Modifier
          .size(7.dp)
          .clip(CircleShape)
          .background(if (active) accent else inactiveDot)
      )
    }
  }
}

@Composable
private fun WelcomeIllustration(compact: Boolean) {
  val sizeDp = if (compact) 180.dp else 216.dp
  val circleBg = MaterialTheme.colorScheme.surface
  val frameColor = MaterialTheme.colorScheme.onBackground
  val screenColor = SlateTheme.colors.elevatedSurface
  val muted = SlateTheme.colors.textSecondary
  val accent = MaterialTheme.colorScheme.primary
  val onAccent = MaterialTheme.colorScheme.onPrimary

  Box(
    modifier = Modifier.size(sizeDp),
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height
      drawCircle(
        color = circleBg,
        radius = w * 0.44f,
        center = Offset(w * 0.50f, h * 0.50f)
      )
      drawLine(
        color = muted,
        start = Offset(w * 0.22f, h * 0.20f),
        end = Offset(w * 0.17f, h * 0.14f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
      )
      drawLine(
        color = muted,
        start = Offset(w * 0.28f, h * 0.17f),
        end = Offset(w * 0.26f, h * 0.10f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
      )
      drawRoundRect(
        color = frameColor,
        topLeft = Offset(w * 0.20f, h * 0.25f),
        size = Size(w * 0.54f, h * 0.38f),
        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
      )
      drawRoundRect(
        color = screenColor,
        topLeft = Offset(w * 0.22f, h * 0.27f),
        size = Size(w * 0.50f, h * 0.34f),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
      )
      drawRoundRect(
        color = muted,
        topLeft = Offset(w * 0.15f, h * 0.63f),
        size = Size(w * 0.60f, h * 0.04f),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
      )
      drawRoundRect(
        color = frameColor.copy(alpha = 0.22f),
        topLeft = Offset(w * 0.54f, h * 0.38f),
        size = Size(w * 0.30f, h * 0.46f),
        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
      )
      drawRoundRect(
        color = accent,
        topLeft = Offset(w * 0.56f, h * 0.40f),
        size = Size(w * 0.26f, h * 0.42f),
        cornerRadius = CornerRadius(11.dp.toPx(), 11.dp.toPx())
      )
      drawCircle(
        color = onAccent.copy(alpha = 0.25f),
        radius = w * 0.06f,
        center = Offset(w * 0.69f, h * 0.58f)
      )
    }
    Icon(
      imageVector = Icons.Outlined.TouchApp,
      contentDescription = null,
      tint = onAccent,
      modifier = Modifier
        .size(32.dp)
        .offset(x = 38.dp, y = 26.dp)
    )
  }
}

@Composable
private fun Feature1Illustration() {
  val circleBg = MaterialTheme.colorScheme.surface
  val deskLine = SlateTheme.colors.border
  val frameColor = MaterialTheme.colorScheme.onBackground
  val screenColor = SlateTheme.colors.elevatedSurface
  val muted = SlateTheme.colors.textSecondary

  Box(
    modifier = Modifier.size(200.dp),
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height
      drawCircle(
        color = circleBg,
        radius = w * 0.44f,
        center = Offset(w * 0.50f, h * 0.52f)
      )
      drawLine(
        color = deskLine,
        start = Offset(w * 0.12f, h * 0.72f),
        end = Offset(w * 0.88f, h * 0.72f),
        strokeWidth = 2.dp.toPx()
      )
      drawRoundRect(
        color = frameColor,
        topLeft = Offset(w * 0.26f, h * 0.36f),
        size = Size(w * 0.48f, h * 0.32f),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
      )
      drawRoundRect(
        color = screenColor,
        topLeft = Offset(w * 0.28f, h * 0.38f),
        size = Size(w * 0.44f, h * 0.28f),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
      )
      drawRoundRect(
        color = muted,
        topLeft = Offset(w * 0.21f, h * 0.69f),
        size = Size(w * 0.58f, h * 0.035f),
        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
      )
    }
  }
}

@Composable
private fun Feature2Illustration() {
  Box(
    modifier = Modifier.size(200.dp),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      modifier = Modifier
        .size(96.dp)
        .offset(x = (-34).dp, y = (-10).dp),
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = Icons.Outlined.Wifi,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(44.dp)
        )
      }
    }
    Surface(
      modifier = Modifier
        .size(96.dp)
        .offset(x = 34.dp, y = 14.dp),
      shape = RoundedCornerShape(24.dp),
      color = SlateTheme.colors.elevatedSurface
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = Icons.Outlined.Bluetooth,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(42.dp)
        )
      }
    }
  }
}

@Composable
private fun Feature3Illustration() {
  val circleBg = MaterialTheme.colorScheme.surface
  val frameColor = MaterialTheme.colorScheme.onBackground
  val screenColor = MaterialTheme.colorScheme.primary
  val onAccent = MaterialTheme.colorScheme.onPrimary
  val standColor = SlateTheme.colors.textSecondary

  Box(
    modifier = Modifier.size(200.dp),
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height
      drawCircle(
        color = circleBg,
        radius = w * 0.44f,
        center = Offset(w * 0.50f, h * 0.50f)
      )
      drawRoundRect(
        color = frameColor,
        topLeft = Offset(w * 0.18f, h * 0.28f),
        size = Size(w * 0.64f, h * 0.40f),
        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
      )
      drawRoundRect(
        color = screenColor,
        topLeft = Offset(w * 0.20f, h * 0.30f),
        size = Size(w * 0.60f, h * 0.36f),
        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
      )
      drawRoundRect(
        color = standColor,
        topLeft = Offset(w * 0.47f, h * 0.68f),
        size = Size(w * 0.06f, h * 0.06f)
      )
      drawRoundRect(
        color = standColor,
        topLeft = Offset(w * 0.36f, h * 0.74f),
        size = Size(w * 0.28f, h * 0.03f),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
      )
    }
    Icon(
      imageVector = Icons.Outlined.PlayArrow,
      contentDescription = null,
      tint = onAccent,
      modifier = Modifier
        .size(40.dp)
        .offset(y = (-4).dp)
    )
  }
}

@Composable
private fun com.example.ui.theme.SlateExtendedColors.surfaceVariantFallbackOrSurface(): Color =
  MaterialTheme.colorScheme.surface
