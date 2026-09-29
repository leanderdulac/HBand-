package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.example.R

@Composable
fun Next2uBrandLogo(
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(R.drawable.next2u_saude_logo),
        contentDescription = "next2u SAÚDE",
        modifier = modifier
            .height(48.dp)
            .testTag("next2u_brand_logo"),
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
    )
}

@Composable
fun HomeWelcomeHeader(
    fullName: String,
    patientId: String,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
    showGreeting: Boolean = true,
) {
    // Reserve room for the current task when rotation or a small window reduces height.
    // Keep the same text sizes and access to the complete profile.
    val compact = LocalConfiguration.current.screenHeightDp <= 400
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = if (compact) 4.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Next2uBrandLogo(modifier = Modifier.weight(1f))
            Button(
                onClick = onEditProfile,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0F2FE), contentColor = Color(0xFF004A77)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.heightIn(min = 56.dp).testTag("header_edit_profile_button"),
            ) { Text("Meu perfil", style = MaterialTheme.typography.labelLarge) }
        }
        if (showGreeting && !compact) {
            Text(
                text = "Olá!",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFF44474E)
            )
            if (fullName.isNotBlank()) Text(
                text = fullName,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xFF001D31)
            )
        }

    }
}
