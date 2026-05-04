import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { MapPin, Clock } from 'lucide-react';
import api from '../services/api';

const Home = () => {
  const [packages, setPackages] = useState([]);
  const [loading, setLoading] = useState(true);

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
    window.location.reload();
  };

  useEffect(() => {
    const fetchPackages = async () => {
      try {
        const response = await api.get('/packages');
        setPackages(response.data);
      } catch (error) {
        console.error('Error fetching packages:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchPackages();
  }, []);

  if (loading) {
    return <div className="min-h-screen flex items-center justify-center">Loading...</div>;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16 items-center">
            <div className="flex-shrink-0 flex items-center">
              <span className="text-2xl font-bold text-blue-600">Travel2Go</span>
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

      <main className="max-w-7xl mx-auto py-12 px-4 sm:px-6 lg:px-8">
        <div className="text-center mb-16">
          <h1 className="text-4xl tracking-tight font-extrabold text-gray-900 sm:text-5xl md:text-6xl">
            <span className="block">Explore the World</span>
            <span className="block text-blue-600">With Premium Packages</span>
          </h1>
          <p className="mt-3 max-w-md mx-auto text-base text-gray-500 sm:text-lg md:mt-5 md:text-xl md:max-w-3xl">
            Discover our handpicked holiday packages for your next unforgettable adventure.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-y-10 gap-x-6 sm:grid-cols-2 lg:grid-cols-3 xl:gap-x-8">
          {packages.map((pkg) => (
            <Link key={pkg.id} to={`/packages/${pkg.id}`} className="group relative bg-white border border-gray-200 rounded-2xl shadow-sm hover:shadow-lg transition-shadow duration-300 overflow-hidden flex flex-col cursor-pointer block">
              <div className="aspect-w-3 aspect-h-2 bg-gray-200 relative">
                <img
                  src={pkg.media?.thumbnailUrl || 'https://images.unsplash.com/photo-1476514525535-07fb3b4ae5f1?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80'}
                  alt={pkg.media?.altText || pkg.title}
                  className="object-cover w-full h-48"
                />
              </div>
              <div className="p-6 flex-1 flex flex-col">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center text-sm text-gray-500">
                    <MapPin className="h-4 w-4 mr-1 text-blue-500" />
                    {pkg.destination}
                  </div>
                  {pkg.packageCode && <span className="text-xs bg-blue-100 text-blue-800 px-2 py-1 rounded-full">{pkg.packageCode}</span>}
                </div>
                <h3 className="text-xl font-bold text-gray-900 mb-2">{pkg.title}</h3>
                <p className="text-gray-600 text-sm mb-4 line-clamp-3 flex-1">{pkg.overview}</p>
                
                <div className="mt-auto border-t border-gray-100 pt-4 flex items-center justify-between">
                  <div className="flex items-center text-gray-700 font-medium">
                    <span className="text-xl font-semibold">{pkg.pricing?.currency || 'INR'} {pkg.pricing?.finalPrice}</span>
                  </div>
                  <div className="flex items-center text-gray-500 text-sm">
                    <Clock className="h-4 w-4 mr-1" />
                    {pkg.duration?.days}D / {pkg.duration?.nights}N
                  </div>
                </div>
              </div>
            </Link>
          ))}
        </div>
      </main>
    </div>
  );
};

export default Home;
