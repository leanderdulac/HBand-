import SwiftUI

/// Same Android drawable (`next2u_saude_logo`) and 48 pt header height.
struct Next2uBrandLogo: View {
    var height: CGFloat = 48

    var body: some View {
        Image("Next2uLogo")
            .resizable()
            .scaledToFit()
            .frame(height: height)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityLabel("next2u SAÚDE")
            .accessibilityIdentifier("next2u_brand_logo")
    }
}

struct HomeWelcomeHeader: View {
    @Environment(\.patientPalette) private var palette
    let fullName: String
    var onEditProfile: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .center, spacing: 12) {
                Next2uBrandLogo()
                Button(action: onEditProfile) {
                    Text("Meu perfil")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(palette.onPrimaryContainer)
                        .padding(.horizontal, 12)
                        .frame(minHeight: PatientTheme.primaryButton)
                        .background(palette.primaryContainer)
                        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("header_edit_profile_button")
            }
            Text("Olá!")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(palette.onSurfaceVariant)
            if !fullName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                Text(fullName)
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(palette.onSurface)
                    .lineLimit(2)
            }
        }
    }
}
