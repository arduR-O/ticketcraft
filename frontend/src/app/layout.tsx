import type { Metadata } from "next";
import { Inter } from "next/font/google";
import Link from "next/link";
import "./globals.css";
import { Ticket } from "lucide-react";

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
        {/* Simple Ticketmaster-style Navbar */}
        <header className="border-b border-gray-200 bg-white sticky top-0 z-50">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex justify-between items-center h-16">
              {/* Logo */}
              <div className="flex-shrink-0 flex items-center">
                <Link href="/" className="flex items-center gap-2">
                  <Ticket className="h-8 w-8 text-blue-600" />
                  <span className="text-2xl font-bold tracking-tighter text-gray-900">
                    TicketCraft
                  </span>
                </Link>
              </div>

              {/* Navigation Links */}
              <nav className="hidden md:flex space-x-8">
                <Link href="/" className="text-gray-600 hover:text-blue-600 font-medium transition-colors">
                  Concerts
                </Link>
                <Link href="/" className="text-gray-600 hover:text-blue-600 font-medium transition-colors">
                  Sports
                </Link>
                <Link href="/" className="text-gray-600 hover:text-blue-600 font-medium transition-colors">
                  Arts & Theater
                </Link>
              </nav>

              {/* Right side buttons */}
              <div className="flex items-center space-x-4">
                <button className="text-gray-600 hover:text-blue-600 font-medium hidden sm:block">
                  Sell
                </button>
                <button className="text-gray-600 hover:text-blue-600 font-medium">
                  Sign In
                </button>
              </div>
            </div>
          </div>
        </header>

        <main>{children}</main>

        <footer className="bg-gray-900 text-white py-12 mt-auto">
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
