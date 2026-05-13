import React from 'react';
import { Link, useNavigate } from 'react-router-dom';

const Navbar = () => {
  const navigate = useNavigate();

  const getTokenPayload = () => {
    const token = localStorage.getItem('token');
    if (!token) return null;
    try {
      const base64Url = token.split('.')[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(window.atob(base64).split('').map(function(c) {
          return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
      }).join(''));
      return JSON.parse(jsonPayload);
    } catch (e) {
      return null;
    }
  };

  const userProfile = getTokenPayload();

  const handleLogout = () => {
    localStorage.removeItem('token');
    navigate('/login');
  };

  return (
    <nav className="bg-white shadow-sm">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16 items-center">
          <div className="flex-shrink-0 flex items-center">
            <Link to="/" className="flex items-center">
              <img src="/logo.png" alt="Travel2Go.in" className="h-10 w-auto" />
            </Link>
          </div>
          <div className="flex items-center space-x-4">
            {userProfile ? (
              <>
                <div className="flex items-center space-x-2">
                  {userProfile.picture && (
                    <img src={userProfile.picture} alt={userProfile.name} className="w-8 h-8 rounded-full border border-gray-300 object-cover" />
                  )}
                  <span className="text-sm font-medium text-gray-700 hidden md:block">
                    {userProfile.name || userProfile.sub}
                  </span>
                </div>
                {userProfile.role === 'ADMIN' && (
                  <Link to="/admin" className="text-blue-600 hover:text-blue-800 text-sm font-medium">
                    Dashboard
                  </Link>
                )}
                <button onClick={handleLogout} className="text-gray-500 hover:text-gray-900 text-sm font-medium">
                  Logout
                </button>
              </>
            ) : (
              <Link to="/login" className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-md text-sm font-medium shadow-sm transition-colors">
                Login
              </Link>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
