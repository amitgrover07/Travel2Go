import React, { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';

const OAuth2RedirectHandler = () => {
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    // Extract token from URL: /oauth2/redirect?token=...
    const urlParams = new URLSearchParams(location.search);
    const token = urlParams.get('token');

    if (token) {
      localStorage.setItem('token', token);
      
      const from = localStorage.getItem('oauth_redirect_from');
      const autoOpenBooking = localStorage.getItem('oauth_autoOpenBooking');
      
      localStorage.removeItem('oauth_redirect_from');
      localStorage.removeItem('oauth_autoOpenBooking');

      // Check role
      let isAdmin = false;
      try {
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const jsonPayload = decodeURIComponent(window.atob(base64).split('').map(function(c) {
            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
        }).join(''));
        const payload = JSON.parse(jsonPayload);
        isAdmin = payload.role === 'ADMIN';
      } catch (e) {}

      if (from) {
        navigate(from, { replace: true, state: { autoOpenBooking: autoOpenBooking === 'true' } });
      } else if (isAdmin) {
        navigate('/admin', { replace: true });
      } else {
        navigate('/', { replace: true });
      }
    } else {
      // Handle error or missing token
      navigate('/login', { replace: true });
    }
  }, [location, navigate]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="text-center">
        <h2 className="text-2xl font-semibold text-gray-900">Authenticating...</h2>
        <p className="mt-2 text-gray-500">Please wait while we log you in.</p>
      </div>
    </div>
  );
};

export default OAuth2RedirectHandler;
