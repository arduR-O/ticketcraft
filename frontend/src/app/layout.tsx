import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import { Ticket } from "lucide-react";
import { Header } from "@/components/Header";

const inter = Inter({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: "TicketCraft",
  description: "Your ultimate destination for live events.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className={`${inter.className} bg-white text-gray-900`}>
        <Header />

        <main>{children}</main>

        <footer className="bg-gray-900 text-white py-6 mt-auto">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
            <Ticket className="h-8 w-8 text-white mx-auto mb-4 opacity-50" />
            <p className="text-gray-400 text-sm">
              &copy; {new Date().getFullYear()} TicketCraft. All rights reserved.
            </p>
          </div>
        </footer>
      </body>
    </html>
  );
}
