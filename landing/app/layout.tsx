import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: "AIO Remote — All-in-one Android remote",
  description:
    "Use your Android phone as a touchpad, keyboard, media controller, or remote for compatible computers and TVs.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
