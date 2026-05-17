import axios from 'axios';

const getBaseUrl = () => {
  let url = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';
  
  if (!url || typeof url !== 'string') {
    return 'http://localhost:8080/api';
  }

  // Remove trailing slash
  if (url.endsWith('/')) {
    url = url.slice(0, -1);
  }

  // Ensure it ends with /api for the gateway
  if (!url.endsWith('/api')) {
    url = url + '/api';
  }
  
  return url;
};

const api = axios.create({
  baseURL: getBaseUrl(),
});

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const isAuthEndpoint = error.config && error.config.url && error.config.url.includes('/auth/');
    
    if (!isAuthEndpoint && error.response && (error.response.status === 401 || error.response.status === 403)) {
      const url = error.config.url || '';
      console.warn(`Auth error (${error.response.status}) at ${url}`);
      
      // Stop the logout loop for all package-related endpoints during stabilization
      if (url.includes('/packages') || url.includes('/custom-packages') || url.includes('/bookings/leads')) {
        console.log('Safe-ignoring auth error for endpoint to prevent logout loop');
        return Promise.reject(error);
      }

      console.error('Forcing logout due to auth error at:', url);
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default api;
