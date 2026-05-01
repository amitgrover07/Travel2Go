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
      navigate('/admin');
    } else {
      // Handle error or missing token
      navigate('/login');
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
