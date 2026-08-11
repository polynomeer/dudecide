import type { Metadata } from "next";
import { AppProviders } from "@/components/app-providers";
import "./globals.css";

export const metadata: Metadata = {
  title: "Dudecide",
  description: "Evidence-linked planning workspace for date and location decisions.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body className="font-[var(--font-body)] text-ink antialiased">
        <AppProviders>{children}</AppProviders>
      </body>
    </html>
  );
}
