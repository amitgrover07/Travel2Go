import axios from 'axios';

let baseUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';
if (baseUrl.endsWith('/')) {
  baseUrl = baseUrl.slice(0, -1);
}
// Ensure it ends with /api
if (!baseUrl.endsWith('/api')) {
  baseUrl = baseUrl + '/api';
}

const api = axios.create({
  baseURL: baseUrl,
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
    // Do not trigger hard redirect for authentication endpoints
    const isAuthEndpoint = error.config && error.config.url && error.config.url.includes('/auth/');
    
    if (!isAuthEndpoint && error.response && (error.response.status === 401 || error.response.status === 403)) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default api;
