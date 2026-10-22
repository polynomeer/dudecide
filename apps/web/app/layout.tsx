import type { Metadata } from "next";
import { Gothic_A1, IBM_Plex_Mono, IBM_Plex_Sans_KR } from "next/font/google";
import { AppProviders } from "@/components/app-providers";
import { themeBootstrapScript } from "@/components/theme/theme-script";
import "./globals.css";

const gothic = Gothic_A1({
  weight: ["700", "900"],
  subsets: ["latin"],
  variable: "--font-gothic",
  display: "swap",
  preload: false,
});

const plexKr = IBM_Plex_Sans_KR({
  weight: ["400", "500", "600", "700"],
  subsets: ["latin"],
  variable: "--font-plex-kr",
  display: "swap",
  preload: false,
});

const plexMono = IBM_Plex_Mono({
  weight: ["400", "500", "600"],
  subsets: ["latin"],
  variable: "--font-plex-mono",
  display: "swap",
});

export const metadata: Metadata = {
  title: "Dudecide",
  description: "흩어진 대화를 결정으로. 근거가 남는 모임 결정 워크스페이스.",
  icons: { icon: "/icon.svg" },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="ko"
      suppressHydrationWarning
      className={`${gothic.variable} ${plexKr.variable} ${plexMono.variable}`}
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeBootstrapScript }} />
      </head>
      <body className="font-body text-ink antialiased">
        <AppProviders>{children}</AppProviders>
      </body>
    </html>
  );
}
