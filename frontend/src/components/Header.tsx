'use client';

import Link from 'next/link';
import { Ticket, User, LogOut } from 'lucide-react';
import { useAuthStore } from '@/store/useAuthStore';
import { useUIStore } from '@/store/useUIStore';
import { LoginModal } from '@/components/LoginModal';

export function Header() {
  const { accessToken, userId, userEmail, logout } = useAuthStore();
  const { isLoginModalOpen, openLoginModal, closeLoginModal } = useUIStore();

  return (
    <>
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
              {/* Simplified Navbar */}
            </nav>

            {/* Right side buttons */}
            <div className="flex items-center space-x-4">
              {accessToken ? (
                <div className="flex items-center gap-4">
                  <span className="flex items-center text-sm font-medium text-gray-700 bg-gray-100 px-3 py-1.5 rounded-full">
                    <User className="w-4 h-4 mr-2 text-blue-600" />
                    {userEmail || (userId ? userId.substring(0, 8) + '...' : 'User')}
                  </span>
                  <button 
                    onClick={logout}
                    className="flex items-center text-sm font-medium text-gray-500 hover:text-red-600 transition-colors"
                  >
                    <LogOut className="w-4 h-4 mr-1" />
                    Sign Out
                  </button>
                </div>
              ) : (
                <button
                  onClick={openLoginModal}
                  className="bg-blue-600 text-white font-medium px-5 py-2 rounded hover:bg-blue-700 transition-colors shadow-sm"
                >
                  Sign In
                </button>
              )}
            </div>
          </div>
        </div>
      </header>

      <LoginModal 
        isOpen={isLoginModalOpen} 
        onClose={closeLoginModal} 
      />
    </>
  );
}
